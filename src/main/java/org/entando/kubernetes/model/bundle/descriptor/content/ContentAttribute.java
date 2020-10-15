package org.entando.kubernetes.model.bundle.descriptor.content;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAttribute {

    private String code;
    private Object value;
    private Map<String, Object> values;
    private List<ContentAttribute> elements;
    private List<ContentAttribute> compositeelements;
    private Map<String, List<ContentAttribute>> listelements;

}