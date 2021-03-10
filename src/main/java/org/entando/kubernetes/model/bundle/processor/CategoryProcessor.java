package org.entando.kubernetes.model.bundle.processor;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.entando.kubernetes.client.core.EntandoCoreClient;
import org.entando.kubernetes.controller.digitalexchange.job.model.AnalysisReport;
import org.entando.kubernetes.controller.digitalexchange.job.model.InstallActionsByComponentType;
import org.entando.kubernetes.controller.digitalexchange.job.model.InstallRequest.InstallAction;
import org.entando.kubernetes.exception.EntandoComponentManagerException;
import org.entando.kubernetes.model.bundle.ComponentType;
import org.entando.kubernetes.model.bundle.descriptor.CategoryDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.ComponentSpecDescriptor;
import org.entando.kubernetes.model.bundle.installable.CategoryInstallable;
import org.entando.kubernetes.model.bundle.installable.Installable;
import org.entando.kubernetes.model.bundle.reader.BundleReader;
import org.entando.kubernetes.model.bundle.reportable.EntandoEngineReportableProcessor;
import org.entando.kubernetes.model.job.EntandoBundleComponentJobEntity;
import org.springframework.stereotype.Service;

/**
 * Processor to create Groups.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryProcessor extends BaseComponentProcessor<CategoryDescriptor>
        implements EntandoEngineReportableProcessor {

    private final EntandoCoreClient engineService;

    @Override
    public ComponentType getSupportedComponentType() {
        return ComponentType.CATEGORY;
    }

    @Override
    public Class<CategoryDescriptor> getDescriptorClass() {
        return CategoryDescriptor.class;
    }

    @Override
    public Optional<Function<ComponentSpecDescriptor, List<String>>> getComponentSelectionFn() {
        return Optional.of(ComponentSpecDescriptor::getCategories);
    }

    @Override
    public boolean doesComponentDscriptorContainMoreThanOneSingleEntity() {
        return true;
    }

    @Override
    public List<Installable<CategoryDescriptor>> process(BundleReader bundleReader) {
        return this.process(bundleReader, InstallAction.CREATE, new InstallActionsByComponentType(),
                new AnalysisReport());
    }

    @Override
    public List<Installable<CategoryDescriptor>> process(BundleReader bundleReader, InstallAction conflictStrategy,
            InstallActionsByComponentType actions, AnalysisReport report) {
        try {
            final List<String> descriptorList = getDescriptorList(bundleReader);

            List<Installable<CategoryDescriptor>> installables = new LinkedList<>();

            for (String fileName : descriptorList) {
                List<CategoryDescriptor> categoryDescriptorList = bundleReader
                        .readListOfDescriptorFile(fileName, CategoryDescriptor.class);
                for (CategoryDescriptor cd : categoryDescriptorList) {
                    InstallAction action = extractInstallAction(cd.getCode(), actions, conflictStrategy, report);
                    installables.add(new CategoryInstallable(engineService, cd, action));
                }
            }

            return installables;
        } catch (IOException e) {
            throw new EntandoComponentManagerException(
                    String.format("Error processing %s components", getSupportedComponentType().getTypeName()), e);
        }
    }

    @Override
    public List<Installable<CategoryDescriptor>> process(List<EntandoBundleComponentJobEntity> components) {
        return components.stream()
                .filter(c -> c.getComponentType() == getSupportedComponentType())
                .map(c -> new CategoryInstallable(engineService, this.buildDescriptorFromComponentJob(c),
                        c.getAction()))
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDescriptor buildDescriptorFromComponentJob(EntandoBundleComponentJobEntity component) {
        return CategoryDescriptor.builder()
                .code(component.getComponentId())
                .build();
    }
}
