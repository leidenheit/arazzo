package de.leidenheit.core.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Parameter {
    private String name;
    private ParameterEnum in;
    private Object value;
    private String reference;
    private Map<String, Object> extensions;

    @Getter
    @AllArgsConstructor
    public enum ParameterEnum {
        PATH("path"),
        HEADER("header"),
        QUERY("query"),
        BODY("body");

        private final String value;
    }
}
