package org.entando.kubernetes.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.hateoas.MediaTypes.HAL_JSON;
import static org.springframework.hateoas.MediaTypes.HAL_JSON_VALUE;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.assertionhelper.AnalysisReportAssertionHelper;
import org.entando.kubernetes.client.k8ssvc.DefaultK8SServiceClient;
import org.entando.kubernetes.client.model.AnalysisReport;
import org.entando.kubernetes.controller.digitalexchange.job.model.Status;
import org.entando.kubernetes.exception.EntandoComponentManagerException;
import org.entando.kubernetes.model.bundle.reportable.Reportable;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.link.EntandoAppPluginLinkBuilder;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.stubhelper.ReportableStubHelper;
import org.entando.kubernetes.utils.EntandoK8SServiceMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.client.Traverson;
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Tag("unit")
public class K8SServiceClientTest {

    private static final String SERVICE_ACCOUNT_TOKEN_FILEPATH = "src/test/resources/token";
    private static EntandoK8SServiceMockServer mockServer;
    private DefaultK8SServiceClient client;

    private static Optional<Integer> findFreePort() {
        Integer port = null;
        try {
            // Get a free port
            ServerSocket s = new ServerSocket(0);
            port = s.getLocalPort();
            s.close();

        } catch (IOException e) {
            // No OPS
        }
        return Optional.ofNullable(port);
    }

    @BeforeEach
    public void setup() {
        mockServer = new EntandoK8SServiceMockServer();
        client = new DefaultK8SServiceClient(mockServer.getApiRoot(), SERVICE_ACCOUNT_TOKEN_FILEPATH);
        client.setRestTemplate(noOAuthRestTemplate());
        client.setNoAuthRestTemplate(noOAuthRestTemplate());
    }

    @AfterEach
    public void reset() {
        mockServer.tearDown();
    }

    @Test
    void shouldThrowExceptionIfServiceAccountTokenDoesNotExist() {

        Assertions.assertThrows(EntandoComponentManagerException.class, () ->
                new DefaultK8SServiceClient(mockServer.getApiRoot(), "not_existing"));
    }

    @Test
    public void testTraversonWithWiremock() {
        Traverson t = client.newTraverson();
        Link l = t.follow("app-plugin-links").asLink();
        assertThat(l).isNotNull();
        assertThat(l.getRel().value()).isEqualTo("app-plugin-links");
        assertThat(l.getHref()).isEqualTo(mockServer.getApiRoot() + "/app-plugin-links");

    }

    @Test
    public void shouldReturnLinkByName() {
        Optional<EntandoAppPluginLink> link = client.getLinkByName("my-app-to-plugin-link");
        assertThat(link.isPresent()).isTrue();
        assertThat(link.get().getMetadata().getName()).isEqualTo("my-app-to-plugin-link");
        mockServer.getInnerServer().verify(1, getRequestedFor(urlMatching("/?")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlMatching("/app-plugin-links/?")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/app-plugin-links/my-app-to-plugin-link")));
    }

    @Test
    public void shouldReturnLinksToApp() {
        List<EntandoAppPluginLink> returnedLink = client.getAppLinks("my-app");
        mockServer.getInnerServer().verify(1, getRequestedFor(urlMatching("/?")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlMatching("/app-plugin-links/?")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/app-plugin-links?app=my-app")));
        assertThat(returnedLink.size()).isEqualTo(1);
        assertThat(returnedLink.get(0).getSpec().getEntandoAppName()).isEqualTo("my-app");
        assertThat(returnedLink.get(0).getSpec().getEntandoAppNamespace().get()).isEqualTo("my-namespace");
    }

    @Test
    public void shouldLinkAnAppWithAPlugin() {
        EntandoPlugin testPlugin = getTestEntandoPlugin();
        client.linkAppWithPlugin("my-app", "my-namespace", testPlugin);
        mockServer.getInnerServer().verify(1, postRequestedFor(urlEqualTo("/apps/my-app/links")));
        List<LoggedRequest> loggedRequests = mockServer.getInnerServer()
                .findAll(postRequestedFor(urlEqualTo("/apps/my-app/links")));
        loggedRequests.get(0).getBodyAsString().contains("name: " + testPlugin.getMetadata().getName());
        loggedRequests.get(0).getBodyAsString().contains("namespace: " + testPlugin.getMetadata().getNamespace());
    }

    @Test
    public void shouldNotGetAppIngressIfPluginHasNoIngressPath() {
        EntandoPlugin testPlugin = getTestEntandoPlugin();
        boolean pluginReady = client.isPluginReadyToServeApp(testPlugin, "my-app");
        assertThat(pluginReady).isFalse();
        mockServer.getInnerServer().verify(0, getRequestedFor(urlEqualTo("/apps/my-app/ingress")));
    }

    @Test
    public void shouldReturnTrueIfPluginIsReadyToServeApp() {
        EntandoPlugin testPlugin = getTestEntandoPluginWithIngressPath();
        mockServer.addStub(get(urlMatching("/my-plugin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", HAL_JSON_VALUE)));
        boolean pluginReady = client.isPluginReadyToServeApp(testPlugin, "my-app");
        assertThat(pluginReady).isTrue();
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/apps/my-app/ingress")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/my-plugin")));
    }

    @Test
    public void shouldUnlinkThePlugin() {
        EntandoAppPluginLink testLink = getTestEntandoAppPluginLink();
        client.unlink(getTestEntandoAppPluginLink());
        String name = testLink.getMetadata().getName();
        mockServer.getInnerServer().verify(1, deleteRequestedFor(urlEqualTo("/app-plugin-links/" + name)));

    }

    @Test
    public void shouldGetPluginByName() {
        EntandoPlugin testPlugin = getTestEntandoPluginWithIngressPath();
        String pluginName = testPlugin.getMetadata().getName();

        Optional<EntandoPlugin> foundPlugin = client.getPluginByName(pluginName);
        assertThat(foundPlugin.isPresent()).isTrue();
        assertThat(foundPlugin.get().getMetadata().getName()).isEqualTo(pluginName);
        mockServer.getInnerServer().verify(1, getRequestedFor(urlMatching("/?")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlMatching("/plugins/?")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/plugins/" + pluginName)));

    }

    @Test
    public void shouldThrowExceptionWhenResponseStatusIsError() {
        Assertions.assertThrows(KubernetesClientException.class, () -> {
            client.getAppLinks("not-existing-app");
        });
    }

    @Test
    public void shouldThrowExceptionWhenUnlinkError() {
        EntandoAppPluginLink link = getTestEntandoAppPluginLink();
        mockServer
                .addStub(delete(anyUrl()).willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        Assertions.assertThrows(KubernetesClientException.class, () -> {
            client.unlink(link);
        });
    }

    @Test
    public void shouldParseEntandoAppPluginCorrectly() {

        List<EntandoAppPluginLink> links = client.getAppLinks("my-app");
        assertThat(links).hasSize(1);
        EntandoAppPluginLink appPluginLink = links.get(0);
        assertThat(appPluginLink.getMetadata().getNamespace()).isEqualTo("my-namespace");
        assertThat(appPluginLink.getMetadata().getName()).isEqualTo("my-app-to-plugin-link");
        assertThat(appPluginLink.getSpec().getEntandoAppNamespace().get()).isEqualTo("my-namespace");
        assertThat(appPluginLink.getSpec().getEntandoAppName()).isEqualTo("my-app");
        assertThat(appPluginLink.getSpec().getEntandoPluginName()).isEqualTo("plugin");
        assertThat(appPluginLink.getSpec().getEntandoPluginNamespace().get()).isEqualTo("plugin-namespace");

    }

    @Test
    public void shouldReadPluginFromLink() {
        EntandoAppPluginLink eapl = new EntandoAppPluginLinkBuilder()
                .withNewMetadata()
                .withName("my-link")
                .endMetadata()
                .withNewSpec()
                .withEntandoPlugin("plugin-namespace", "plugin")
                .withEntandoApp("dummy", "dummy")
                .endSpec()
                .build();

        EntandoPlugin plugin = client.getPluginForLink(eapl);
        assertThat(plugin.getMetadata().getName()).isEqualTo("plugin");
        assertThat(plugin.getMetadata().getNamespace()).isEqualTo("plugin-namespace");
        assertThat(plugin.getSpec().getImage()).isEqualTo("entando/some-image:6.0.0");

    }

    @Test
    public void shouldGetBundlesFromAllObservedNamespaces() {
        List<EntandoDeBundle> bundles = client.getBundlesInObservedNamespaces();
        assertThat(bundles).hasSize(1);
        assertThat(bundles.get(0).getMetadata().getName()).isEqualTo("my-bundle");
        assertThat(bundles.get(0).getSpec().getDetails().getName()).isEqualTo("@entando/my-bundle");
    }

    @Test
    public void shouldGetBundlesFromSingleNamespace() {
        List<EntandoDeBundle> bundles = client.getBundlesInNamespace("entando-de-bundles");
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/bundles?namespace=entando-de-bundles")));
        assertThat(bundles).hasSize(1);
    }

    @Test
    public void shouldGetBundlesFromMultipleNamespaces() {
        String stubResponse = mockServer.readResourceAsString("/payloads/k8s-svc/bundles/bundles-empty-list.json");
        mockServer.addStub(get(urlMatching("/bundles?namespaces=first"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", HAL_JSON_VALUE)
                        .withBody(stubResponse)));
        mockServer.addStub(get(urlMatching("/bundles?namespaces=second"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", HAL_JSON_VALUE)
                        .withBody(stubResponse)));
        mockServer.addStub(get(urlMatching("/bundles?namespaces=third"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", HAL_JSON_VALUE)
                        .withBody(stubResponse)));
        List<EntandoDeBundle> bundles = client.getBundlesInNamespaces(Arrays.asList("first", "second", "third"));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/bundles?namespace=first")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/bundles?namespace=second")));
        mockServer.getInnerServer().verify(1, getRequestedFor(urlEqualTo("/bundles?namespace=third")));
        assertThat(bundles).isEmpty();
    }

    @Test
    public void shouldGetBundleWithName() {
        Optional<EntandoDeBundle> bundle = client.getBundleWithName("my-bundle");
        assertThat(bundle.isPresent()).isTrue();
        assertThat(bundle.get().getSpec().getDetails().getName()).isEqualTo("@entando/my-bundle");
    }

    @Test
    public void shouldNotFindBundleWithName() {
        Optional<EntandoDeBundle> bundle = client.getBundleWithName("not-existent-bundle");
        assertThat(bundle.isPresent()).isFalse();
    }

    @Test
    public void shouldNotFindBundleWithNameInNamespace() {
        String stubResponse = mockServer.readResourceAsString("/payloads/k8s-svc/bundles/bundles-empty-list.json");
        mockServer.addStub(get(urlEqualTo("/bundles?namespace=my-namespace"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(stubResponse)
                        .withHeader("Content-Type", HAL_JSON_VALUE)));
        Optional<EntandoDeBundle> bundle = client.getBundleWithNameAndNamespace("my-bundle", "my-namespace");
        assertThat(bundle.isPresent()).isFalse();
    }

    @Test
    public void shouldGetBundleWithNameAndNamespace() {
        Optional<EntandoDeBundle> bundle = client.getBundleWithNameAndNamespace("my-bundle", "entando-de-bundles");
        assertThat(bundle.isPresent()).isTrue();
        assertThat(bundle.get().getMetadata().getName()).isEqualTo("my-bundle");
    }

    @Test
    void shouldGetAnalysisReport() {

        Map<String, Status> pluginMap = Map.of(
                ReportableStubHelper.PLUGIN_CODE_1, Status.NEW,
                ReportableStubHelper.PLUGIN_CODE_2, Status.NEW);

        AnalysisReport expected = new AnalysisReport().setPlugins(pluginMap);

        List<Reportable> reportableList = ReportableStubHelper.stubAllReportableList();
        AnalysisReport analysisReport = client.getAnalysisReport(reportableList);

        AnalysisReportAssertionHelper.assertOnAnalysisReports(expected, analysisReport);
    }

    private RestTemplate noOAuthRestTemplate() {
        RestTemplate template = new RestTemplate();
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        messageConverters.add(0, getJsonConverter());
        messageConverters.addAll(Traverson.getDefaultMessageConverters(HAL_JSON));
        template.setMessageConverters(messageConverters);
        return template;
    }

    private HttpMessageConverter<?> getJsonConverter() {
        final List<MediaType> supportedMediaTypes = Collections.singletonList(MediaType.APPLICATION_JSON);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jackson2HalModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        converter.setObjectMapper(mapper);
        converter.setSupportedMediaTypes(supportedMediaTypes);

        return converter;
    }

    private EntandoAppPluginLink getTestEntandoAppPluginLink() {
        return new EntandoAppPluginLinkBuilder()
                .withNewMetadata()
                .withName("my-app-to-plugin-link")
                .withNamespace("my-namespace")
                .endMetadata()
                .withNewSpec()
                .withEntandoApp("my-namespace", "my-app")
                .withEntandoPlugin("plugin-namespace", "plugin")
                .endSpec()
                .build();
    }

    private EntandoPlugin getTestEntandoPlugin() {
        return new EntandoPluginBuilder()
                .withNewMetadata()
                .withName("plugin")
                .withNamespace("plugin-namespace")
                .endMetadata()
                .build();
    }

    private EntandoPlugin getTestEntandoPluginWithIngressPath() {
        return new EntandoPluginBuilder()
                .withNewMetadata()
                .withName("plugin")
                .withNamespace("plugin-namespace")
                .endMetadata()
                .withNewSpec()
                .withIngressPath("/my-plugin")
                .endSpec()
                .build();

    }

    private String readResourceAsString(String resourcePath) {

        try {
            Path rp = Paths.get(this.getClass().getResource(resourcePath).toURI());
            return new String(Files.readAllBytes(rp));
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
