package de.leidenheit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuccessAction {
    private String name;
    private SuccessActionType type;
    private String workflowId;
    private String stepId;
    private List<ArazzoSpecification.Workflow.Step.Criterion> criteria;

    @Getter
    @AllArgsConstructor
    public enum SuccessActionType {
        END("end"),
        GOTO("goto");

        private final String value;
    }
}
