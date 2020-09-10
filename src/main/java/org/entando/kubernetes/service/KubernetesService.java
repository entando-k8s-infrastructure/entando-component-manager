package org.entando.kubernetes.service;

import static org.entando.kubernetes.model.EntandoDeploymentPhase.FAILED;
import static org.entando.kubernetes.model.EntandoDeploymentPhase.SUCCESSFUL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.entando.kubernetes.client.k8ssvc.K8SServiceClient;
import org.entando.kubernetes.exception.k8ssvc.EntandoAppPluginLinkingProcessException;
import org.entando.kubernetes.exception.k8ssvc.PluginNotFoundException;
import org.entando.kubernetes.exception.k8ssvc.PluginNotReadyException;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.bundle.descriptor.DockerImage;
import org.entando.kubernetes.model.bundle.descriptor.PluginDescriptor;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.ExpectedRole;
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
        return getCurrentAppLinks()
                .stream().map(k8sServiceClient::getPluginForLink)
                .collect(Collectors.toList());
    }

    public EntandoPlugin getLinkedPlugin(String pluginId) {
        return getCurrentAppLinkToPlugin(pluginId)
                .map(k8sServiceClient::getPluginForLink)
                .orElseThrow(PluginNotFoundException::new);
    }

    public boolean isLinkedPlugin(String pluginId) {
        return getCurrentAppLinkToPlugin(pluginId).isPresent();
    }

    public boolean isPluginReady(EntandoPlugin plugin) {
        return k8sServiceClient.isPluginReadyToServeApp(plugin, entandoAppName);
    }

    private List<EntandoAppPluginLink> getCurrentAppLinks() {
        return k8sServiceClient.getAppLinks(entandoAppName);
    }

    private Optional<EntandoAppPluginLink> getCurrentAppLinkToPlugin(String pluginId) {
        return getCurrentAppLinks()
                .stream()
                .filter(el -> el.getSpec().getEntandoPluginName().equals(pluginId))
                .findFirst();
    }

    public void unlinkPlugin(String pluginId) {
        getCurrentAppLinkToPlugin(pluginId).ifPresent(k8sServiceClient::unlink);
    }

    public EntandoPlugin generatePluginFromDescriptor(PluginDescriptor descriptor) {
        return new EntandoPluginBuilder()
                .withNewMetadata()
                .withName(extractNameFromDescriptor(descriptor))
                .withLabels(extractLabelsFromDescriptor(descriptor))
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.valueOf(descriptor.getDbms().toUpperCase()))
                .withImage(descriptor.getImage())
                .withIngressPath(extractIngressPathFromDescriptor(descriptor))
                .withRoles(extractRolesFromDescriptor(descriptor))
                .withHealthCheckPath(descriptor.getHealthCheckPath())
                .endSpec()
                .build();
    }

    public List<ExpectedRole> extractRolesFromDescriptor(PluginDescriptor descriptor) {
        return descriptor.getRoles().stream()
                .distinct()
                .map(ExpectedRole::new)
                .collect(Collectors.toList());
    }

    public String extractNameFromDescriptor(PluginDescriptor descriptor) {
        return composeNameFromDockerImage(descriptor.getDockerImage());
    }

    public String extractIngressPathFromDescriptor(PluginDescriptor descriptor) {
        return composeIngressPathFromDockerImage(descriptor.getDockerImage());
    }

    public Map<String, String> extractLabelsFromDescriptor(PluginDescriptor descriptor) {
        DockerImage dockerImage = descriptor.getDockerImage();
        return getLabelsFromImage(dockerImage);
    }

    private String composeNameFromDockerImage(DockerImage image) {
        return String.join(".",
                makeKubernetesCompatible(image.getOrganization()),
                makeKubernetesCompatible(image.getName()),
                makeKubernetesCompatible(image.getVersion()));
    }

    private String composeIngressPathFromDockerImage(DockerImage image) {
        return "/" + String.join("/",
                makeKubernetesCompatible(image.getOrganization()),
                makeKubernetesCompatible(image.getName()),
                makeKubernetesCompatible(image.getVersion()));
    }

    public Map<String, String> getLabelsFromImage(DockerImage dockerImage) {
        Map<String, String> labels = new HashMap<>();
        labels.put("organization", dockerImage.getOrganization());
        labels.put("name", dockerImage.getName());
        labels.put("version", dockerImage.getVersion());
        return labels;
    }

    private String makeKubernetesCompatible(String value) {
        value = value.toLowerCase();
        value = value.replace("_", ".");
        return value;
    }

    public EntandoAppPluginLink linkPlugin(EntandoPlugin plugin) {
        EntandoPlugin newPlugin = new EntandoPluginBuilder()
                .withMetadata(plugin.getMetadata())
                .withSpec(plugin.getSpec())
                .build();

        newPlugin.getMetadata().setNamespace(null);

        return k8sServiceClient.linkAppWithPlugin(entandoAppName, entandoAppNamespace, newPlugin);
    }

    public void linkPluginAndWaitForSuccess(EntandoPlugin plugin) {
        EntandoAppPluginLink createdLink = this.linkPlugin(plugin);
        try {
            this.waitingConditionFactory.until(() -> this.hasLinkingProcessCompletedSuccessfully(createdLink, plugin));
        } catch (ConditionTimeoutException e) {
            throw new PluginNotReadyException(plugin.getMetadata().getName());
        }

    }

    public boolean hasLinkingProcessCompletedSuccessfully(EntandoAppPluginLink link, EntandoPlugin plugin) {
        boolean result = false;
        Optional<EntandoAppPluginLink> linkByName = k8sServiceClient.getLinkByName(link.getMetadata().getName());
        if (linkByName.isPresent()) {
            if (linkByName.get().getStatus().getEntandoDeploymentPhase().equals(FAILED)) {
                String msg = String.format("Linking procedure between app %s and plugin %s failed", entandoAppName,
                        plugin.getMetadata().getName());
                throw new EntandoAppPluginLinkingProcessException(msg);
            }
            result = linkByName.get().getStatus().getEntandoDeploymentPhase().equals(SUCCESSFUL)
                    && isPluginReady(plugin);
        }
        return result;
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
