package org.entando.kubernetes.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.utils.SleepStubber.doSleep;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.UniformDistribution;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.compress.utils.Sets;
import org.apache.commons.io.IOUtils;
import org.entando.kubernetes.client.K8SServiceClientTestDouble;
import org.entando.kubernetes.client.core.EntandoCoreClient;
import org.entando.kubernetes.client.k8ssvc.K8SServiceClient;
import org.entando.kubernetes.model.bundle.ComponentType;
import org.entando.kubernetes.model.bundle.descriptor.FileDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.PageDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.plugin.PluginDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.plugin.PluginDescriptorV1Role;
import org.entando.kubernetes.model.bundle.descriptor.plugin.PluginDescriptorV1Spec;
import org.entando.kubernetes.model.bundle.downloader.BundleDownloaderFactory;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.model.debundle.EntandoDeBundleBuilder;
import org.entando.kubernetes.model.debundle.EntandoDeBundleSpec;
import org.entando.kubernetes.model.debundle.EntandoDeBundleSpecBuilder;
import org.entando.kubernetes.model.entandocore.EntandoCoreComponentUsage.NoUsageComponent;
import org.entando.kubernetes.model.job.EntandoBundleJobEntity;
import org.entando.kubernetes.model.job.JobStatus;
import org.entando.kubernetes.model.job.JobType;
import org.entando.kubernetes.model.web.response.PagedMetadata;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

public class TestInstallUtils {

    private static final Duration MAX_WAITING_TIME_FOR_JOB_STATUS = Duration.ofSeconds(30);
    private static final Duration AWAITILY_DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    public static final UriBuilder ANALYSIS_REPORT_ENDPOINT = UriComponentsBuilder.newInstance()
            .pathSegment("components", "todomvc", "analysis");
    public static final UriBuilder ALL_COMPONENTS_ENDPOINT = UriComponentsBuilder.newInstance()
            .pathSegment("components");
    public static final UriBuilder SINGLE_COMPONENT_ENDPOINT = UriComponentsBuilder.newInstance()
            .pathSegment("components", "todomvc");
    public static final UriBuilder INSTALL_COMPONENT_ENDPOINT = UriComponentsBuilder.newInstance()
            .pathSegment("components", "todomvc", "install");
    public static final UriBuilder UNINSTALL_COMPONENT_ENDPOINT = UriComponentsBuilder.newInstance()
            .pathSegment("components", "todomvc", "uninstall");

    public static final String JOBS_ENDPOINT = "/jobs";

    public static final String EXPECTED_PLUGIN_NAME = "entando-the-lucas-0-0-1-snapshot";
    public static final String EXPECTED_INGRESS_PATH = "/entando/the-lucas/0-0-1-snapshot";
    public static final String TEST_DESCRIPTOR_IMAGE = "entando/the-lucas:0.0.1-SNAPSHOT";
    public static final String TEST_DESCRIPTOR_ADMIN_ROLE = "thelucas-admin";
    public static final String TEST_DESCRIPTOR_USER_ROLE = "thelucas-user";
    public static final String TEST_DESCRIPTOR_HEALTH_PATH = "/management/health";
    public static final String TEST_DESCRIPTOR_DBMS = "postgresql";

    @SneakyThrows
    public static String simulateSuccessfullyCompletedInstall(MockMvc mockMvc, EntandoCoreClient coreClient,
            K8SServiceClient k8sServiceClient, String bundleName) {
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalFixedDelay(0);

        K8SServiceClientTestDouble k8SServiceClientTestDouble = (K8SServiceClientTestDouble) k8sServiceClient;
        k8SServiceClientTestDouble.addInMemoryBundle(getTestBundle());

        stubFor(WireMock.get("/repository/npm-internal/test_bundle/-/test_bundle-0.0.1.tgz")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream")
                        .withBody(readFromDEPackage(bundleName))));

        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));
        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));
        //        stubFor(WireMock.post(urlMatching("/entando-app/api/.*")).willReturn(aResponse().withStatus(200)));

        MvcResult result = mockMvc.perform(post(INSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForInstallStatus(mockMvc, JobStatus.INSTALL_COMPLETED);

        return jobId;
    }

    public static EntandoDeBundle getTestBundle() {
        return new EntandoDeBundleBuilder()
                .withNewMetadata()
                .withName("todomvc")
                .withNamespace("entando-de-bundles")
                .endMetadata()
                .withSpec(getTestEntandoDeBundleSpec()).build();

    }

    public static EntandoDeBundleSpec getTestEntandoDeBundleSpec() {
        return new EntandoDeBundleSpecBuilder()
                .withNewDetails()
                .withDescription("A bundle containing some demo components for Entando6")
                .withName("todomvc")
                .addNewVersion("0.0.1")
                .addNewKeyword("entando6")
                .addNewKeyword("digital-exchange")
                .addNewDistTag("latest", "0.0.1")
                .and()
                .addNewTag()
                .withVersion("0.0.1")
                .withIntegrity(
                        "sha512-n4TEroSqg/sZlEGg2xj6RKNtl/t3ZROYdNd99/dl3UrzCUHvBrBxZ1rxQg/sl3kmIYgn3+ogbIFmUZYKWxG3Ag==")
                .withShasum("4d80130d7d651176953b5ce470c3a6f297a70815")
                .withTarball("http://localhost:8099/repository/npm-internal/test_bundle/-/test_bundle-0.0.1.tgz")
                .endTag()
                .build();
    }

    @SneakyThrows
    public static byte[] readFromDEPackage(String bundleName) {
        try (final InputStream inputStream = TestInstallUtils.class.getClassLoader().getResourceAsStream(bundleName)) {
            try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                Assertions.assertNotNull(inputStream);
                IOUtils.copy(inputStream, outputStream);
                return outputStream.toByteArray();
            }
        }
    }

    public static void waitForInstallStatus(MockMvc mockMvc, JobStatus expected) {
        waitForInstallStatus(mockMvc, new JobStatus[]{expected});
    }

    public static void waitForInstallStatus(MockMvc mockMvc, JobStatus... expected) {
        waitForJobStatus(() -> getComponentLastJobStatusOfType(mockMvc, "todomvc", JobType.INSTALL.getStatuses()),
                expected);
    }

    public static void waitForUninstallStatus(MockMvc mockMvc, JobStatus expected) {
        waitForJobStatus(() -> getComponentLastJobStatusOfType(mockMvc, "todomvc", JobType.UNINSTALL.getStatuses()),
                expected);
    }

    public static void waitForJobStatus(Supplier<JobStatus> jobStatus, JobStatus... expected) {
        waitForJobStatus(jobStatus, Sets.newHashSet(expected));
    }

    public static void waitForJobStatus(Supplier<JobStatus> jobStatus, Set<JobStatus> expected) {
        await().atMost(MAX_WAITING_TIME_FOR_JOB_STATUS)
                .pollInterval(AWAITILY_DEFAULT_POLL_INTERVAL)
                .until(() -> expected.contains(jobStatus.get()));
    }

    public static void waitForJobStatus(Supplier<JobStatus> jobStatus, JobStatus expected) {
        waitForJobStatus(jobStatus, new JobStatus[]{expected});
    }

    @SneakyThrows
    public static JobStatus getJobStatus(MockMvc mockMvc, String jobId) {
        MockHttpServletResponse response = mockMvc.perform(get("/jobs/" + jobId)
                .with(user("user")))
                .andReturn().getResponse();
        return JobStatus.valueOf(JsonPath.read(response.getContentAsString(), "$.payload.status"));
    }

    @SneakyThrows
    public static JobStatus getComponentLastJobStatusOfType(MockMvc mockMvc, String component,
            Set<JobStatus> possibleStatues) {
        List<String> allowedValues = possibleStatues.stream().map(JobStatus::name).collect(Collectors.toList());
        MockHttpServletResponse response = mockMvc.perform(get("/jobs"
                + "?sort=startedAt"
                + "&direction=DESC"
                + "&pageSize=1"
                + "&filters[0].attribute=status&filters[0].operator=eq&filters[0].allowedValues=" + String
                .join(",", allowedValues)
                + "&filters[1].attribute=componentId&filters[1].operator=eq&filters[1].value=" + component)
                .with(user("user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").value(hasSize(1)))
                .andExpect(jsonPath("$.payload.[0].componentId").value(component))
                .andReturn().getResponse();
        return JobStatus.valueOf(JsonPath.read(response.getContentAsString(), "$.payload.[0].status"));
    }

    public static EntandoBundleJobEntity getJob(MockMvc mockMvc, String jobId) throws Exception {
        String responseContent = mockMvc.perform(get(JOBS_ENDPOINT + "/{id}", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String status = JsonPath.read(responseContent, "$.payload.status");
        double progress = JsonPath.read(responseContent, "$.payload.progress");
        return EntandoBundleJobEntity.builder()
                .status(JobStatus.valueOf(status))
                .progress(progress)
                .build();
    }

    public static void verifyJobHasComponentAndStatus(MockMvc mockMvc, String jobId, JobStatus expectedStatus)
            throws Exception {
        mockMvc.perform(get(JOBS_ENDPOINT + "/{id}", jobId))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(jsonPath("payload.componentId").value("todomvc"))
                .andExpect(jsonPath("payload.status").value(expectedStatus.toString()));
    }

    @SneakyThrows
    public static String simulateBundleDownloadError(MockMvc mockMvc, EntandoCoreClient coreClient,
            K8SServiceClient k8sServiceClient,
            BundleDownloaderFactory factory) {
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalFixedDelay(0);

        factory.setDefaultSupplier(() -> null);
        K8SServiceClientTestDouble k8SServiceClientTestDouble = (K8SServiceClientTestDouble) k8sServiceClient;
        k8SServiceClientTestDouble.addInMemoryBundle(getTestBundle());

        stubFor(WireMock.get("/repository/npm-internal/test_bundle/-/test_bundle-0.0.1.tgz")
                .willReturn(aResponse().withStatus(500)));

        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));
        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));
        //        stubFor(WireMock.post(urlMatching("/entando-app/api/.*")).willReturn(aResponse().withStatus(200)));

        MvcResult result = mockMvc.perform(post(INSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForInstallStatus(mockMvc, JobStatus.INSTALL_ERROR);
        return jobId;
    }

    @SneakyThrows
    public static String simulateSuccessfullyCompletedUninstall(MockMvc mockMvc, EntandoCoreClient coreClient) {
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalFixedDelay(0);

        setupComponentUsageToAllowUninstall(coreClient);
        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));
        //        stubFor(WireMock.delete(urlMatching("/entando-app/api/.*")).willReturn(aResponse().withStatus(200)));
        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));

        MvcResult result = mockMvc.perform(post(UNINSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForUninstallStatus(mockMvc, JobStatus.UNINSTALL_COMPLETED);

        return jobId;
    }

    @SneakyThrows
    public static String simulateFailingInstall(MockMvc mockMvc, EntandoCoreClient coreClient,
            K8SServiceClient k8sServiceClient,
            String bundleName) {
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalFixedDelay(0);

        K8SServiceClientTestDouble k8SServiceClientTestDouble = (K8SServiceClientTestDouble) k8sServiceClient;

        k8SServiceClientTestDouble.addInMemoryBundle(getTestBundle());

        stubFor(WireMock.get("/repository/npm-internal/test_bundle/-/test_bundle-0.0.1.tgz")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream")
                        .withBody(readFromDEPackage(bundleName))));

        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));

        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));
        doThrow(new RestClientResponseException("error", 500, "Error", null, null, null))
                .when(coreClient).createPage(any(PageDescriptor.class));

        MvcResult result = mockMvc.perform(post(INSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForInstallStatus(mockMvc, JobStatus.INSTALL_ROLLBACK, JobStatus.INSTALL_ROLLBACK_ERROR,
                JobStatus.INSTALL_ERROR);

        return JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
    }

    @SneakyThrows
    public static String simulateHugeAssetFailingInstall(MockMvc mockMvc, EntandoCoreClient coreClient,
            K8SServiceClient k8sServiceClient, String bundleName) {
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalFixedDelay(0);

        K8SServiceClientTestDouble k8SServiceClientTestDouble = (K8SServiceClientTestDouble) k8sServiceClient;

        k8SServiceClientTestDouble.addInMemoryBundle(getTestBundle());

        stubFor(WireMock.get("/repository/npm-internal/test_bundle/-/test_bundle-0.0.1.tgz")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream")
                        .withBody(readFromDEPackage(bundleName))));

        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));

        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));
        doThrow(new RestClientResponseException("error", 413, "Error", null, null, null))
                .when(coreClient).createFile(any(FileDescriptor.class));

        MvcResult result = mockMvc.perform(post(INSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForInstallStatus(mockMvc, JobStatus.INSTALL_ROLLBACK, JobStatus.INSTALL_ROLLBACK_ERROR,
                JobStatus.INSTALL_ERROR);

        return JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
    }

    @SneakyThrows
    public static String simulateFailingUninstall(MockMvc mockMvc, EntandoCoreClient coreClient) {
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalFixedDelay(0);

        setupComponentUsageToAllowUninstall(coreClient);
        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));

        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deleteFolder(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deleteContentModel(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deleteContentType(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deleteFragment(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deleteWidget(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deletePage(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deletePageModel(any());
        doThrow(new RestClientResponseException("error", 500, "error", null, null, null)).when(coreClient)
                .deleteLabel(any());

        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));

        MvcResult result = mockMvc.perform(post(UNINSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForUninstallStatus(mockMvc, JobStatus.UNINSTALL_ERROR);

        return JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");

    }

    @SneakyThrows
    public static String simulateInProgressInstall(MockMvc mockMvc, EntandoCoreClient coreClient,
            K8SServiceClient k8sServiceClient,
            String bundleName) {
        Mockito.reset(coreClient);
        WireMock.reset();
        UniformDistribution delayDistribution = new UniformDistribution(200, 500);
        WireMock.setGlobalRandomDelay(delayDistribution);

        K8SServiceClientTestDouble k8SServiceClientTestDouble = (K8SServiceClientTestDouble) k8sServiceClient;
        k8SServiceClientTestDouble.addInMemoryBundle(getTestBundle());

        stubFor(WireMock.get("/repository/npm-internal/test_bundle/-/test_bundle-0.0.1.tgz")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream")
                        .withBody(readFromDEPackage(bundleName))));

        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));

        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createPage(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createPageTemplate(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createWidget(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createFragment(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createContentType(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createContentTemplate(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createLabel(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createFolder(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).createFile(any());

        stubFor(WireMock.get(urlMatching("/k8s/.*")).willReturn(aResponse().withStatus(200)));

        MvcResult result = mockMvc.perform(post(INSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);

        waitForInstallStatus(mockMvc, JobStatus.INSTALL_IN_PROGRESS);

        return JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
    }

    @SneakyThrows
    public static String simulateInProgressUninstall(MockMvc mockMvc, EntandoCoreClient coreClient) {
        UniformDistribution delayDistribution = new UniformDistribution(200, 500);
        Mockito.reset(coreClient);
        WireMock.reset();
        WireMock.setGlobalRandomDelay(delayDistribution);

        setupComponentUsageToAllowUninstall(coreClient);
        stubFor(WireMock.post(urlEqualTo("/auth/protocol/openid-connect/auth"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{ \"access_token\": \"iddqd\" }")));

        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deletePage(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deletePageModel(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deleteWidget(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deleteFragment(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deleteContentType(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deleteContentModel(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deleteLabel(any());
        doSleep(Duration.ofMillis(delayDistribution.sampleMillis())).when(coreClient).deleteFolder(any());

        MvcResult result = mockMvc.perform(post(UNINSTALL_COMPONENT_ENDPOINT.build()))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
        assertThat(result.getResponse().containsHeader("Location")).isTrue();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/jobs/" + jobId);
        waitForUninstallStatus(mockMvc, JobStatus.UNINSTALL_IN_PROGRESS);

        return JsonPath.read(result.getResponse().getContentAsString(), "$.payload.id");
    }

    private static void setupComponentUsageToAllowUninstall(EntandoCoreClient coreClient) {
        when(coreClient.getGroupUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.GROUP));
        when(coreClient.getWidgetUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.WIDGET));
        when(coreClient.getPageUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.PAGE));
        when(coreClient.getContentModelUsage(anyString()))
                .thenReturn(new NoUsageComponent(ComponentType.CONTENT_TEMPLATE));
        when(coreClient.getPageModelUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.PAGE_TEMPLATE));
        when(coreClient.getFragmentUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.FRAGMENT));
        when(coreClient.getContentTypeUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.CONTENT_TYPE));
        when(coreClient.getCategoryUsage(anyString())).thenReturn(new NoUsageComponent(ComponentType.CATEGORY));
    }

    public static PagedMetadata<EntandoBundleJobEntity> getInstallJob(MockMvc mockMvc) throws Exception {
        return new ObjectMapper().readValue(mockMvc.perform(get(JOBS_ENDPOINT + "?component=todomvcV1&type=INSTALL"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<PagedMetadata<EntandoBundleJobEntity>>() {
                });
    }

    public static PagedMetadata<EntandoBundleJobEntity> getUninstallJob(MockMvc mockMvc) throws Exception {
        List<String> allowedValues = JobType.UNINSTALL.getStatuses().stream().map(JobStatus::name)
                .collect(Collectors.toList());
        return new ObjectMapper().readValue(mockMvc.perform(get("/jobs"
                        + "?sort=startedAt"
                        + "&direction=DESC"
                        + "&filters[0].attribute=status&filters[0].operator=eq&filters[0].allowedValues=" + String
                        .join(",", allowedValues)
                        + "&filters[1].attribute=componentId&filters[1].operator=eq&filters[1].value=todomvc"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<PagedMetadata<EntandoBundleJobEntity>>() {
                });
    }

    public static String requestProperty(final LoggedRequest request, final String property) {
        return JsonPath.read(new String(request.getBody()), property);
    }

    public static String requestCode(final LoggedRequest request) {
        return requestProperty(request, "code");
    }

    public static String requestPath(final LoggedRequest request) {
        return requestProperty(request, "path");
    }



    public static PluginDescriptor getTestDescriptor() {
        return PluginDescriptor.builder()
                .image(TEST_DESCRIPTOR_IMAGE)
                .roles(Arrays.asList(TEST_DESCRIPTOR_ADMIN_ROLE, TEST_DESCRIPTOR_USER_ROLE))
                .healthCheckPath(TEST_DESCRIPTOR_HEALTH_PATH)
                .dbms(TEST_DESCRIPTOR_DBMS)
                .deploymentBaseName(TEST_DESCRIPTOR_IMAGE)
                .build();
    }

    public static PluginDescriptor getTestDescriptorVersion1() {
        return PluginDescriptor.builder()
                .spec(getTestDescriptorV1Spec())
                .build();
    }

    public static PluginDescriptorV1Spec getTestDescriptorV1Spec() {

        List<PluginDescriptorV1Role> roleList = Arrays.asList(TEST_DESCRIPTOR_ADMIN_ROLE, TEST_DESCRIPTOR_USER_ROLE)
                .stream()
                .map(role -> new PluginDescriptorV1Role(role, role))
                .collect(Collectors.toList());

        return new PluginDescriptorV1Spec()
                .setImage(TEST_DESCRIPTOR_IMAGE)
                .setRoles(roleList)
                .setHealthCheckPath(TEST_DESCRIPTOR_HEALTH_PATH)
                .setDbms(TEST_DESCRIPTOR_DBMS);
    }
}
