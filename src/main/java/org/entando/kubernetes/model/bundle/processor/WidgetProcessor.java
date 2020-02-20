package org.entando.kubernetes.model.bundle.processor;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.entando.kubernetes.model.bundle.NpmBundleReader;
import org.entando.kubernetes.model.bundle.descriptor.ComponentDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.ComponentSpecDescriptor;
import org.entando.kubernetes.model.bundle.descriptor.WidgetDescriptor;
import org.entando.kubernetes.model.bundle.installable.Installable;
import org.entando.kubernetes.model.digitalexchange.ComponentType;
import org.entando.kubernetes.model.digitalexchange.DigitalExchangeJob;
import org.entando.kubernetes.model.digitalexchange.DigitalExchangeJobComponent;
import org.entando.kubernetes.service.digitalexchange.entandocore.EntandoCoreService;
import org.springframework.stereotype.Service;

/**
 * Processor to create Widgets, can handle descriptors
 * with custom UI embedded or a separate custom UI file.
 *
 * @author Sergio Marcelino
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetProcessor implements ComponentProcessor {

    private final EntandoCoreService engineService;

    @Override
    public List<Installable> process(final DigitalExchangeJob job, final NpmBundleReader npr,
                                               final ComponentDescriptor descriptor) throws IOException {

        final Optional<List<String>> widgetsDescriptor = ofNullable(descriptor.getComponents()).map(ComponentSpecDescriptor::getWidgets);
        final List<Installable> installables = new LinkedList<>();

        if (widgetsDescriptor.isPresent()) {
            for (final String fileName : widgetsDescriptor.get()) {
                final WidgetDescriptor widgetDescriptor = npr.readDescriptorFile(fileName, WidgetDescriptor.class);
                if (widgetDescriptor.getCustomUiPath() != null) {
                    String widgetUiPath = getRelativePath(fileName, widgetDescriptor.getCustomUiPath());
                    widgetDescriptor.setCustomUi(npr.readFileAsString(widgetUiPath));
                }
                installables.add(new WidgetInstallable(widgetDescriptor));
            }
        }

        return installables;
    }

    @Override
    public boolean shouldProcess(final ComponentType componentType) {
        return componentType == ComponentType.WIDGET;
    }

    @Override
    public void uninstall(final DigitalExchangeJobComponent component) {
        log.info("Removing Widget {}", component.getName());
        engineService.deleteWidget(component.getName());
    }

    public class WidgetInstallable extends Installable<WidgetDescriptor> {

        private WidgetInstallable(final WidgetDescriptor widgetDescriptor) {
            super(widgetDescriptor);
        }

        @Override
        public CompletableFuture install() {
            return CompletableFuture.runAsync(() -> {
                log.info("Registering Widget {}", representation.getCode());
                engineService.registerWidget(representation);
            });
        }

        @Override
        public ComponentType getComponentType() {
            return ComponentType.WIDGET;
        }

        @Override
        public String getName() {
            return representation.getCode();
        }

    }
}