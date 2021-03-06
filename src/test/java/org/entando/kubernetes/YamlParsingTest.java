package org.entando.kubernetes;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
public class YamlParsingTest {

    @Test
    public void test_yaml_parsing_without_quotes() throws IOException {
        String yaml = "id: ID\nvalue: 1";

        YAMLMapper mapper = new YAMLMapper();
        TempObject obj = mapper.readValue(yaml, TempObject.class);
        assertEquals("ID", obj.getId());
        assertEquals(Integer.valueOf(1), obj.getValue());
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TempObject {

        String id;
        Integer value;

    }

}
