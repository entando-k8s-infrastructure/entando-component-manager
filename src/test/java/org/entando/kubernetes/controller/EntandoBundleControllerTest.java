package org.entando.kubernetes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.entando.kubernetes.client.core.EntandoCoreClient;
import org.entando.kubernetes.model.bundle.EntandoBundle;
import org.entando.kubernetes.controller.digitalexchange.component.EntandoBundleResourceController;
import org.entando.kubernetes.exception.EntandoComponentManagerException;
import org.entando.kubernetes.exception.digitalexchange.BundleNotInstalledException;
import org.entando.kubernetes.model.digitalexchange.ComponentType;
import org.entando.kubernetes.model.digitalexchange.EntandoBundleEntity;
import org.entando.kubernetes.model.entandocore.EntandoCoreComponentUsage;
import org.entando.kubernetes.model.job.EntandoBundleComponentJob;
import org.entando.kubernetes.model.job.EntandoBundleJob;
import org.entando.kubernetes.model.job.JobStatus;
import org.entando.kubernetes.model.web.response.SimpleRestResponse;
import org.entando.kubernetes.service.ModelConverter;
import org.entando.kubernetes.service.digitalexchange.component.EntandoBundleComponentUsageService;
import org.entando.kubernetes.service.digitalexchange.component.EntandoBundleService;
import org.entando.kubernetes.service.digitalexchange.component.EntandoBundleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

@Tag("in-process")
public class EntandoBundleControllerTest {

    private EntandoBundleResourceController controller;
    private EntandoBundleComponentUsageService usageService;
    private EntandoBundleService componentsService;
    private EntandoCoreClient coreClient;

    @BeforeEach
    public void setup() {
        componentsService = Mockito.mock(EntandoBundleServiceImpl.class);
        coreClient = Mockito.mock(EntandoCoreClient.class);
        usageService = new EntandoBundleComponentUsageService(coreClient);
        controller = new EntandoBundleResourceController(componentsService, usageService);
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenGettingSummaryOfNotInstalledComponent() {
        when(componentsService.getInstalledComponent(any())).thenReturn(Optional.empty());
        when(componentsService.getBundleInstalledComponents(any())).thenCallRealMethod();
        assertThrows(BundleNotInstalledException.class, () -> controller.getBundleUsageSummary("any"));
    }

    @Test
    public void shouldThrowIfInstalledBundleDoesntHaveAssociatedCompletedJob() {
        EntandoBundle component = ModelConverter.fromEntity(getTestComponent());
        when(componentsService.getBundleInstalledComponents(any())).thenCallRealMethod();
        when(componentsService.getInstalledComponent(any())).thenReturn(Optional.of(component));

        assertThrows(EntandoComponentManagerException.class, () -> controller.getBundleUsageSummary("any"));

    }

    @Test
    public void shouldReturnEmptySummaryIfBundleHasNoComponents() {
        EntandoBundle component = ModelConverter.fromEntity(getTestComponent());
        component.setLastJob(getTestJob());
        when(componentsService.getInstalledComponent(any())).thenReturn(Optional.of(component));

        ResponseEntity<SimpleRestResponse<List<EntandoCoreComponentUsage>>> resp = controller.getBundleUsageSummary("my-component");
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(Objects.requireNonNull(resp.getBody()).getPayload().size()).isZero();
    }

    @Test
    public void shouldReturnEmptySummaryIfBundleHasIrrelevantComponents() {
        EntandoBundleJob job = getTestJob();

        EntandoBundleComponentJob componentJob = new EntandoBundleComponentJob();
        componentJob.setStatus(JobStatus.INSTALL_COMPLETED);
        componentJob.setParentJob(job);
        componentJob.setComponentId("my-magic-resource");
        componentJob.setComponentType(ComponentType.ASSET);

        EntandoBundle component = ModelConverter.fromEntity(getTestComponent());
        component.setLastJob(job);

        when(componentsService.getInstalledComponent(any())).thenReturn(Optional.of(component));
        when(componentsService.getBundleInstalledComponents(any())).thenReturn(Collections.singletonList(componentJob));

        ResponseEntity<SimpleRestResponse<List<EntandoCoreComponentUsage>>> resp = controller.getBundleUsageSummary("my-component");
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(Objects.requireNonNull(resp.getBody()).getPayload().size()).isZero();
    }

    @Test
    public void shouldReturnSummaryForRelevantComponents() {
        EntandoBundleJob job = getTestJob();

        EntandoBundleComponentJob cjA = new EntandoBundleComponentJob();
        cjA.setStatus(JobStatus.INSTALL_COMPLETED);
        cjA.setParentJob(job);
        cjA.setComponentId("my-magic-resource");
        cjA.setComponentType(ComponentType.ASSET);

        EntandoBundleComponentJob cjB = new EntandoBundleComponentJob();
        cjB.setStatus(JobStatus.INSTALL_COMPLETED);
        cjB.setParentJob(job);
        cjB.setComponentId("my-magic-widget");
        cjB.setComponentType(ComponentType.WIDGET);

        EntandoBundleComponentJob cjC = new EntandoBundleComponentJob();
        cjC.setStatus(JobStatus.INSTALL_COMPLETED);
        cjC.setParentJob(job);
        cjC.setComponentId("my-magic-page");
        cjC.setComponentType(ComponentType.PAGE);

        EntandoBundle component = ModelConverter.fromEntity(getTestComponent());
        component.setLastJob(job);

        when(componentsService.getInstalledComponent(any())).thenReturn(Optional.of(component));
        when(componentsService.getBundleInstalledComponents(any())).thenReturn(Arrays.asList(cjA, cjB, cjC));
        when(coreClient.getWidgetUsage(eq("my-magic-widget"))).thenReturn(
                new EntandoCoreComponentUsage(ComponentType.WIDGET.getTypeName(), "my-magic-widget", 11));
        when(coreClient.getPageUsage(eq("my-magic-page"))).thenReturn(
                new EntandoCoreComponentUsage(ComponentType.PAGE.getTypeName(), "my-magic-page", 5));

        ResponseEntity<SimpleRestResponse<List<EntandoCoreComponentUsage>>> resp = controller.getBundleUsageSummary("my-component");
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        List<EntandoCoreComponentUsage> usageList = resp.getBody().getPayload();

        assertThat(usageList.size()).isEqualTo(2);
        assertThat(usageList.stream()
                .filter(usc -> usc.getType().equals(ComponentType.WIDGET.getTypeName()) && usc.getCode().equals("my-magic-widget"))
                .findFirst().get().getUsage()).isEqualTo(11);
        assertThat(usageList.stream()
                .filter(usc -> usc.getType().equals(ComponentType.PAGE.getTypeName()) && usc.getCode().equals("my-magic-page"))
                .findFirst().get().getUsage()).isEqualTo(5);
        assertThat(usageList.stream().map(EntandoCoreComponentUsage::getType).distinct().count())
                .isEqualTo(2);
        assertThat(usageList.stream().map(EntandoCoreComponentUsage::getUsage).reduce(0, Integer::sum))
                .isEqualTo(16);
    }

    @Test
    public void shouldProvideSummaryForDifferentWidgets() {
        EntandoBundleJob job = getTestJob();

        EntandoBundleComponentJob cjA = new EntandoBundleComponentJob();
        cjA.setStatus(JobStatus.INSTALL_COMPLETED);
        cjA.setParentJob(job);
        cjA.setComponentId("my-magic-resource");
        cjA.setComponentType(ComponentType.ASSET);

        EntandoBundleComponentJob cjB = new EntandoBundleComponentJob();
        cjB.setStatus(JobStatus.INSTALL_COMPLETED);
        cjB.setParentJob(job);
        cjB.setComponentId("my-magic-widget");
        cjB.setComponentType(ComponentType.WIDGET);

        EntandoBundleComponentJob cjC = new EntandoBundleComponentJob();
        cjC.setStatus(JobStatus.INSTALL_COMPLETED);
        cjC.setParentJob(job);
        cjC.setComponentId("my-other-widget");
        cjC.setComponentType(ComponentType.WIDGET);

        EntandoBundle component = ModelConverter.fromEntity(getTestComponent());
        component.setLastJob(job);

        when(componentsService.getInstalledComponent(any())).thenReturn(Optional.of(component));
        when(componentsService.getBundleInstalledComponents(any())).thenReturn(Arrays.asList(cjA, cjB, cjC));
        when(coreClient.getWidgetUsage(eq("my-magic-widget"))).thenReturn(
                new EntandoCoreComponentUsage(ComponentType.WIDGET.getTypeName(), "my-magic-widget", 11));
        when(coreClient.getWidgetUsage(eq("my-other-widget"))).thenReturn(
                new EntandoCoreComponentUsage(ComponentType.WIDGET.getTypeName(), "my-other-widget", 5));

        ResponseEntity<SimpleRestResponse<List<EntandoCoreComponentUsage>>> resp = controller.getBundleUsageSummary("my-component");
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        List<EntandoCoreComponentUsage> usageList = resp.getBody().getPayload();

        assertThat(usageList.size()).isEqualTo(2);
        assertThat(usageList.stream()
                .filter(usc -> usc.getType().equals(ComponentType.WIDGET.getTypeName()) && usc.getCode().equals("my-magic-widget"))
                .findFirst()
                .get().getUsage()).isEqualTo(11);
        assertThat(usageList.stream()
                .filter(usc -> usc.getType().equals(ComponentType.WIDGET.getTypeName()) && usc.getCode().equals("my-other-widget"))
                .findFirst()
                .get()
                .getUsage())
                .isEqualTo(5);

    }

    private EntandoBundleEntity getTestComponent() {

        EntandoBundleJob installJob = new EntandoBundleJob();
        installJob.setComponentId("my-component");
        installJob.setStatus(JobStatus.INSTALL_COMPLETED);
        installJob.setComponentName("my-component-name");
        installJob.setStartedAt(LocalDateTime.of(2020, 7, 15, 12,23,30));
        installJob.setFinishedAt(LocalDateTime.of(2020, 7, 15, 12,24,30));

        EntandoBundleEntity component = new EntandoBundleEntity();
        component.setId(UUID.randomUUID());
        component.setCode("my-component");
        component.setTitle("my-component-name");
        component.setInstalledJob(installJob);
        component.setLastJob(installJob);
        return component;
    }

    private EntandoBundleJob getTestJob() {
        EntandoBundleJob job = new EntandoBundleJob();
        job.setId(UUID.randomUUID());
        job.setComponentId("my-component");
        job.setComponentName("my-component-name");
        job.setStatus(JobStatus.INSTALL_COMPLETED);
        return job;
    }

}
