package de.leidenheit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.*;

import java.util.List;
import java.util.Map;

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
        private JsonNode inputs;
        private List<String> dependsOn;
        private List<Step> steps;
        private List<SuccessAction> successActions;
        private List<FailureAction> failureActions;
        private Map<String, String> outputs;
        private List<Step.Parameter> parameters;
        private Map<String, Object> extensions;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Step {
            private String description;
            private String stepId;
            private String operationId;
            private String operationPath;
            private String workflowId;
            private List<Parameter> parameters;
            private RequestBody requestBody;
            private List<Criterion> successCriteria;
            private List<SuccessAction> onSuccess;
            private List<FailureAction> onFailure;
            private Map<String, String> outputs;
            private Map<String, Object> extensions;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class RequestBody {
                private String contentType;
                private Object payload;
                private List<PayloadReplacementObject> replacements;
                private Map<String, Object> extensions;

                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class PayloadReplacementObject {
                    private String target;
                    private Object value;
                    private Map<String, Object> extensions;
                }
            }

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
                    QUERY("query"),
                    BODY("body");

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
                private CriterionExpressionTypeObject expressionTypeObject;
                private Map<String, Object> extensions;

                public void setType(final CriterionType type) {
                    this.type = type;

                    // handle jsonpath and xpath
                    // see: https://spec.openapis.org/arazzo/latest.html#criterion-expression-type-object
                    if (CriterionType.JSONPATH.equals(type)) {
                        this.expressionTypeObject = CriterionExpressionTypeObject.builder()
                                .type(CriterionExpressionTypeObject.CriterionExpressionType.JSONPATH)
                                .build();
                    } else if (CriterionType.XPATH.equals(type)) {
                        this.expressionTypeObject = CriterionExpressionTypeObject.builder()
                                .type(CriterionExpressionTypeObject.CriterionExpressionType.XPATH)
                                .build();
                    }
                }

                @Getter
                @AllArgsConstructor
                public enum CriterionType {
                    SIMPLE("simple"),
                    REGEX("regex"),
                    JSONPATH("jsonpath"),
                    XPATH("xpath");

                    private final String value;
                }

                @Data
                @Builder
                @AllArgsConstructor
                @NoArgsConstructor
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class CriterionExpressionTypeObject {
                    private CriterionExpressionType type;
                    private String version;
                    private Map<String, Object> extensions;

                    @Getter
                    @AllArgsConstructor
                    public enum CriterionExpressionType {
                        JSONPATH("jsonpath"),
                        XPATH("xpath");

                        private final String value;
                    }
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
        private Map<String, JsonNode> inputs;
        private Map<String, Workflow.Step.Parameter> parameters;
        private Map<String, SuccessAction> successActions;
        private Map<String, FailureAction> failureActions;
        private Map<String, Object> extensions;

        public void addInput(final String key, final JsonNode input) {
            this.inputs.put(key, input);
        }

        public void addParameter(final String key, final Workflow.Step.Parameter parameter) {
            this.parameters.put(key, parameter);
        }
    }
}
