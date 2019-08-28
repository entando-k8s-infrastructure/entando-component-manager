package org.entando.kubernetes.service.digitalexchange.job.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComponentDescriptor extends Descriptor {

    private String code;
    private String description;

    private ComponentSpecDescriptor components;

}