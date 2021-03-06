/**
 * request to install a bundle using an install plans previously fetched.
 */

package org.entando.kubernetes.controller.digitalexchange.job.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.entando.kubernetes.service.digitalexchange.BundleUtilities;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class InstallWithPlansRequest extends InstallPlan {

    private String version = BundleUtilities.LATEST_VERSION;
}
