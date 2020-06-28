package org.entando.kubernetes.model.bundle.processor;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.entando.kubernetes.model.bundle.BundleReader;
import org.entando.kubernetes.model.bundle.descriptor.Descriptor;
import org.entando.kubernetes.model.bundle.installable.Installable;
import org.entando.kubernetes.model.digitalexchange.ComponentType;
import org.entando.kubernetes.model.digitalexchange.EntandoBundleComponentJob;
import org.entando.kubernetes.model.digitalexchange.EntandoBundleJob;

/**
 * Any classes that is called a Component Processor will be found automatically on the context to process the Zip File
 * with the Component Descriptor
 *
 * @author Sergio Marcelino
 */
public interface ComponentProcessor {

    /**
     * This method will process the component descriptor and should return an empty list or a list of all components
     * that should be installed
     *
     *
     * @param job
     * @param bundleReader npm package reader zip file being processed
     * @return Should return a list of Installables
     * @throws IOException in case of any error while reading any the file from the Zip package
     */
    List<Installable> process(EntandoBundleJob job, BundleReader bundleReader);

    /**
     * This method extracts a list of installable components from a list of {@class EntandoBundleComponentJob}
     * @param components
     * @return List of installable components
     */
    List<Installable> process(List<EntandoBundleComponentJob> components);

    default Installable process(EntandoBundleComponentJob componentJob) {
        if (supportComponent(componentJob.getComponentType())) {
            return process(Collections.singletonList(componentJob)).get(0);
        }
        return null;
    }

    Object buildDescriptorFromComponentJob(EntandoBundleComponentJob component);

    ComponentType getSupportedComponentType();

    default boolean supportComponent(ComponentType ct) {
        return this.getSupportedComponentType().equals(ct);
    }

    default String getRelativePath(String referenceFile, String fileName) {
        return Paths.get(referenceFile).resolveSibling(fileName).toString();
    }

}
