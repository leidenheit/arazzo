package de.leidenheit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import lombok.*;

import java.util.*;

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
    private Components components;
    private OpenAPI openAPI;

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
        private Map<String, Object> extensions;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceDescription {
        private String name;
        private String url;
        // TODO @JsonDeserialize(using = SourceDescriptionTypeDeserializer.class)
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

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Workflow {
        private String workflowId;
        private String summary;
        private String description;
        private Schema<?> inputs;
        private List<Step> steps;
        private Map<String, Object> outputs;
        private Map<String, Object> extensions;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Step {
            private String stepId;
            private String operationId;
            private String operationRef;
            private Operation operation;
            private String description;
            private String dependsOn;
            @JsonIgnore
            private Workflow workflow;
            private List<Parameter> parameters;
            private List<Criterion> successCriteria;
            private Map<String, String> outputs;
            private List<SuccessAction> onSuccess;
            private List<FailureAction> onFailure;

            @Data
            @Builder
            @AllArgsConstructor
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Parameter {
                private String name;
                @JsonDeserialize(using = ParameterInDeserializer.class)
                private ParameterEnum in;
                private Object value;
                private String reference;
                private Map<String, Object> extensions;

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
            public static class Criterion {
                private String condition;
                private String context;
                private CriterionType type;
                private Map<String, Object> extensions;

                @Getter
                @AllArgsConstructor
                public enum CriterionType {
                    SIMPLE("simple"),
                    REGEX("regex"),
                    JSON_PATH("jsonpath"),
                    XPATH("xpath");

                    private final String value;
                }
            }

        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Components {
        private Map<String, Schema<?>> inputs;
        private Map<String, Workflow.Step.Parameter> parameters;
        private Map<String, SuccessAction> successActions;
        private Map<String, FailureAction> failureActions;
        private Map<String, Object> extensions;

        public void addInput(final String key, final Schema<?> input) {
            this.inputs.put(key, input);
        }

        public void addParameter(final String key, final Workflow.Step.Parameter parameter) {
            this.parameters.put(key, parameter);
        }
    }
}
