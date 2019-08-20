package org.entando.kubernetes.service.digitalexchange.installable.processors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.entando.kubernetes.model.digitalexchange.ComponentType;
import org.entando.kubernetes.model.digitalexchange.DigitalExchangeJob;
import org.entando.kubernetes.service.digitalexchange.entandocore.EntandoEngineService;
import org.entando.kubernetes.service.digitalexchange.installable.ComponentProcessor;
import org.entando.kubernetes.service.digitalexchange.installable.Installable;
import org.entando.kubernetes.service.digitalexchange.job.ZipReader;
import org.entando.kubernetes.service.digitalexchange.job.model.ComponentDescriptor;
import org.entando.kubernetes.service.digitalexchange.job.model.FileDescriptor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor to handle Static files to be stored by Entando.
 * Commonly used for js, images and css.
 *
 * This processor will also create the folders.
 *
 * @author Sergio Marcelino
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetProcessor implements ComponentProcessor {

    private final EntandoEngineService engineService;

    @Override
    public List<? extends Installable> process(final DigitalExchangeJob job, final ZipReader zipReader,
                                               final ComponentDescriptor descriptor) throws IOException {

        final List<Installable> installables = new LinkedList<>();

        if (zipReader.containsResourceFolder()) {
            final String componentFolder = "/" + job.getComponentId();
            installables.add(new DirectoryInstallable(componentFolder));

            final List<String> resourceFolders = zipReader.getResourceFolders();
            for (final String resourceFolder : resourceFolders) {
                installables.add(new DirectoryInstallable(componentFolder + "/" + resourceFolder));
            }

            final List<String> resourceFiles = zipReader.getResourceFiles();
            for (final String resourceFile : resourceFiles) {
                final FileDescriptor fileDescriptor = zipReader.readFileAsDescriptor(resourceFile);
                final String folder = StringUtils.isEmpty(fileDescriptor.getFolder())
                        ? componentFolder
                        : componentFolder + "/" + fileDescriptor.getFolder();
                fileDescriptor.setFolder(folder);
                installables.add(new AssetInstallable(fileDescriptor));
            }
        }

        return installables;
    }

    public class AssetInstallable extends Installable<FileDescriptor> {

        private AssetInstallable(final FileDescriptor fileDescriptor) {
            super(fileDescriptor);
        }

        @Override
        public CompletableFuture install() {
            return CompletableFuture.runAsync(() -> {
                log.info("Uploading file {}", representation.getFilename());
                engineService.uploadFile(representation);
            });
        }

        @Override
        public ComponentType getComponentType() {
            return ComponentType.RESOURCE;
        }

        @Override
        public String getName() {
            return representation.getFolder() + "/" + representation.getFilename();
        }

    }

    public class DirectoryInstallable extends Installable<String> {

        private DirectoryInstallable(final String directory) {
            super(directory);
        }

        @Override
        public CompletableFuture install() {
            return CompletableFuture.runAsync(() -> {
                log.info("Creating directory {}", representation);
                engineService.createFolder(representation);
            });
        }

        @Override
        public ComponentType getComponentType() {
            return ComponentType.RESOURCE;
        }

        @Override
        public String getName() {
            return representation;
        }

    }
}
