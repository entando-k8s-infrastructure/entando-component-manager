package org.entando.kubernetes.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.entando.kubernetes.client.k8ssvc.K8SServiceClient;
import org.entando.kubernetes.exception.k8ssvc.PluginNotFoundException;
import org.entando.kubernetes.exception.k8ssvc.PluginNotReadyException;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KubernetesService {

    public static final String ENTANDOPLUGIN_CRD_NAME = "entandoplugins.entando.org";
    public static final String KUBERNETES_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    private final ConditionFactory waitingConditionFactory;
    private final K8SServiceClient k8sServiceClient;
    private final String entandoAppName;
    private final String entandoAppNamespace;

    public KubernetesService(@Value("${entando.app.name}") String entandoAppName,
            @Value("${entando.app.namespace}") String entandoAppNamespace,
            K8SServiceClient k8SServiceClient,
            ConditionFactory waitingConditionFactory) {
        this.waitingConditionFactory = waitingConditionFactory;
        this.k8sServiceClient = k8SServiceClient;
        this.entandoAppName = entandoAppName;
        this.entandoAppNamespace = getCurrentKubernetesNamespace().orElse(entandoAppNamespace);
    }

    public List<EntandoPlugin> getLinkedPlugins() {
        return getCurrentAppLinkedPlugins()
                .stream().map(k8sServiceClient::getPluginForLink)
                .collect(Collectors.toList());
    }

    public EntandoPlugin getLinkedPlugin(String pluginId) {
        return getCurrentAppLinkedPlugin(pluginId)
                .map(k8sServiceClient::getPluginForLink)
                .orElseThrow(PluginNotFoundException::new);
    }

    public boolean isLinkedPlugin(String pluginId) {
        return getCurrentAppLinkedPlugin(pluginId).isPresent();
    }

    public boolean isPluginReady(EntandoPlugin plugin) {
        return k8sServiceClient.isPluginReadyToServeApp(plugin, entandoAppName);
    }

    private List<EntandoAppPluginLink> getCurrentAppLinkedPlugins() {
        return k8sServiceClient.getAppLinks(entandoAppName);
    }

    private Optional<EntandoAppPluginLink> getCurrentAppLinkedPlugin(String pluginId) {
        return getCurrentAppLinkedPlugins()
                .stream()
                .filter(el -> el.getSpec().getEntandoPluginName().equals(pluginId))
                .findFirst();
    }

    public void unlinkPlugin(String pluginId) {
        getCurrentAppLinkedPlugin(pluginId).ifPresent(k8sServiceClient::unlink);
    }

    public void linkPlugin(EntandoPlugin plugin) {
        EntandoPlugin newPlugin = new EntandoPluginBuilder()
                .withMetadata(plugin.getMetadata())
                .withSpec(plugin.getSpec())
                .build();

        newPlugin.getMetadata().setNamespace(null);

        k8sServiceClient.linkAppWithPlugin(entandoAppName, entandoAppNamespace, newPlugin);
    }

    public void linkAndWaitForPlugin(EntandoPlugin plugin) {
        this.linkPlugin(plugin);
        try {
            this.waitingConditionFactory.until(() -> this.isPluginReady(plugin));
        } catch (ConditionTimeoutException e) {
            throw new PluginNotReadyException(plugin.getMetadata().getName());
        }

    }

    public List<EntandoDeBundle> getBundlesInDefaultNamespace() {
        return k8sServiceClient.getBundlesInObservedNamespaces();
    }

    public Optional<EntandoDeBundle> getBundleByName(String name) {
        return k8sServiceClient.getBundleWithName(name);
    }

    public Optional<EntandoDeBundle> getBundleByNameAndDigitalExchange(String name, String deId) {
        return k8sServiceClient.getBundleWithNameAndNamespace(name, deId);
    }

    private Optional<String> getCurrentKubernetesNamespace() {
        Path namespacePath = Paths.get(KUBERNETES_NAMESPACE_PATH);
        String namespace = null;
        if (namespacePath.toFile().exists()) {
            try {
                namespace = new String(Files.readAllBytes(namespacePath));
            } catch (IOException e) {
                log.error(String.format("An error occurred while reading the namespace from file %s",
                        namespacePath.toString()), e);
            }
        }
        return Optional.ofNullable(namespace);
    }

}
