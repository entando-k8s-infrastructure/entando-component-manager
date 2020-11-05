/**
 * functional interface used to abstract the remote service request function.
 */

package org.entando.kubernetes.model.bundle.reportable;

import java.util.List;
import org.entando.kubernetes.controller.digitalexchange.job.model.AnalysisReport;

@FunctionalInterface
public interface AnalysisReportFunction {

    AnalysisReport getAnalysisReport(List<Reportable> reportableList);
}
