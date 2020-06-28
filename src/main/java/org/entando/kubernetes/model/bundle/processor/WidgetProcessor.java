package org.entando.kubernetes.model.bundle.processor;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.entando.kubernetes.client.core.EntandoCoreClient;
import org.entando.kubernetes.exception.EntandoComponentManagerException;
import org.entando.kubernetes.model.bundle.BundleReader;
import org.entando.kubernetes.model.bundle.descriptor.ComponentDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.ComponentSpecDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.WidgetDescriptor;
import org.entando.kubernetes.model.bundle.installable.Installable;
import org.entando.kubernetes.model.bundle.installable.WidgetInstallable;
import org.entando.kubernetes.model.digitalexchange.ComponentType;
import org.entando.kubernetes.model.digitalexchange.EntandoBundleComponentJob;
import org.entando.kubernetes.model.digitalexchange.EntandoBundleJob;
import org.springframework.stereotype.Service;

/**
 * Processor to create Widgets, can handle descriptors with custom UI embedded or a separate custom UI file.
 *
 * @author Sergio Marcelino
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetProcessor implements ComponentProcessor {

    private final EntandoCoreClient engineService;

    @Override
    public ComponentType getSupportedComponentType() {
        return ComponentType.WIDGET;
    }

    @Override
    public List<Installable> process(EntandoBundleJob job, BundleReader npr) {
        try {
            ComponentDescriptor descriptor = npr.readBundleDescriptor();

            final Optional<List<String>> widgetsDescriptor = ofNullable(descriptor.getComponents())
                    .map(ComponentSpecDescriptor::getWidgets);
            final List<Installable> installables = new LinkedList<>();

            if (widgetsDescriptor.isPresent()) {
                for (final String fileName : widgetsDescriptor.get()) {
                    final WidgetDescriptor widgetDescriptor = npr.readDescriptorFile(fileName, WidgetDescriptor.class);
                    if (widgetDescriptor.getCustomUiPath() != null) {
                        String widgetUiPath = getRelativePath(fileName, widgetDescriptor.getCustomUiPath());
                        widgetDescriptor.setCustomUi(npr.readFileAsString(widgetUiPath));
                    }
                    widgetDescriptor.setBundleId(descriptor.getCode());
                    installables.add(new WidgetInstallable(engineService, widgetDescriptor));
                }
            }

            return installables;
        } catch (IOException e) {
            throw new EntandoComponentManagerException("Error reading bundle", e);
        }
    }

    @Override
    public List<Installable> process(List<EntandoBundleComponentJob> components) {
        return components.stream()
                .filter(c -> c.getComponentType() == ComponentType.WIDGET)
                .map(c -> new WidgetInstallable(engineService, this.buildDescriptorFromComponentJob(c)))
                .collect(Collectors.toList());
    }

    @Override
    public WidgetDescriptor buildDescriptorFromComponentJob(EntandoBundleComponentJob component) {
        return WidgetDescriptor.builder()
                .code(component.getName())
                .build();
    }

}
