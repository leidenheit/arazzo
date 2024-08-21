package de.leidenheit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FailureAction {
    private String name;
    private FailureActionType type;
    private String workflowId;
    private String stepId;
    private Float retryAfter; // non negative
    private Integer retryLimit; // non negative
    private List<ArazzoSpecification.Workflow.Step.Criterion> criteria;

    @Getter
    @AllArgsConstructor
    public enum FailureActionType {
        END("end"),
        RETRY("retry"),
        GOTO("goto");

        private final String value;
    }
}
