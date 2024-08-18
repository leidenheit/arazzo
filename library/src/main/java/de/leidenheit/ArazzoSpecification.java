package de.leidenheit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArazzoSpecification {

    private String arazzo;
    private Info info;
    private List<SourceDescription> sourceDescriptions;
    private List<Workflow> workflows;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        private String title;
        private String summary;
        private String description;
        private String version;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceDescription {
        private String name;
        private String url;
        @JsonDeserialize(using = SourceDescriptionTypeDeserializer.class)
        private SourceDescriptionType type;

        @Getter
        @AllArgsConstructor
        public enum SourceDescriptionType {
            OPENAPI("openapi");

            private final String value;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Workflow {
        private String workflowId;
        private String summary;
        private String description;
        private Inputs inputs;
        private List<Step> steps;
        private Map<String, Object> outputs;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Inputs {
            private String type;
            private Map<String, Property> properties;

            @Data
            @Builder
            @AllArgsConstructor
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Property {
                private String type;
            }
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Step {

            private String stepId;
            private String description;
            private String operationId;
            private List<Parameter> parameters;
            private List<SuccessCriterion> successCriteria;

            @Data
            @Builder
            @AllArgsConstructor
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Parameter {
                private String name;
                @JsonDeserialize(using = ParameterInDeserializer.class)
                private ParameterEnum in;
                private String value;

                @Getter
                @AllArgsConstructor
                public enum ParameterEnum {
                    PATH("path"),
                    HEADER("header"),
                    QUERY("query");

                    private final String value;
                }
            }

            @Data
            @Builder
            @AllArgsConstructor
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class SuccessCriterion {
                private String condition;
            }

        }
    }
}
