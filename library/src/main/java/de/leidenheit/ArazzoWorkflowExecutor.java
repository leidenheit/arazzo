package de.leidenheit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArazzoWorkflowExecutor {

    private final ArazzoSpecification.Workflow workflow;

    public void execute() {
        // TODO finalize implementation
        System.out.printf("Executing workflow %s%n", workflow.getWorkflowId());
    }
}
