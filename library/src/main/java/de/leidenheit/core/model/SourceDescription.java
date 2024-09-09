package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceDescription {
    private String name;
    private String url;
    private SourceDescriptionType type;
    private Map<String, Object> extensions;

    @Getter
    @AllArgsConstructor
    public enum SourceDescriptionType {
        OPENAPI("openapi"),
        ARAZZO("arazzo");

        private final String value;
    }
}
