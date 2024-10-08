package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ArazzoWorkflowExecutor {

    private final SourceDescriptionInitializer sourceDescriptionInitializer = new SourceDescriptionInitializer();
    private final StepExecutor stepExecutor = new HttpStepExecutor();

    public Map<String, Object> execute(final ArazzoSpecification arazzo, final Workflow workflow, final Map<String, Object> inputs) {
        sourceDescriptionInitializer.initialize(arazzo);

        var resolver = ArazzoExpressionResolver.getInstance(arazzo, inputs);
        var resultWorkflowOutputs = new HashMap<String, Object>();

        // execute
        for (Step step : workflow.getSteps()) {

            // referenced a workflow from another arazzo
            if (Objects.nonNull(step.getWorkflowId())) {

                var sourceDescription = findRelevantSourceDescriptionByWorkflowId(arazzo, step.getWorkflowId());
                var refWorkflow = findWorkflowByWorkflowId(sourceDescription.getReferencedArazzo(), step.getWorkflowId());

                Objects.requireNonNull(sourceDescription);
                Objects.requireNonNull(refWorkflow);

                System.out.printf("Delegating execution of workflow '%s' referenced by step '%s' of workflow '%s'%n", refWorkflow.getWorkflowId(), step.getStepId(), workflow.getWorkflowId());

                var refWorkflowOutputs = execute(sourceDescription.getReferencedArazzo(), refWorkflow, inputs);
                resultWorkflowOutputs.putAll(refWorkflowOutputs);

                continue;
            }

            // within current arazzo
            System.out.printf("Running step '%s' of workflow '%s'%n", step.getStepId(), workflow.getWorkflowId());
            stepExecutor.executeStep(arazzo, step, resolver);
        }

        // resolve and store outputs
        if (Objects.nonNull(workflow.getOutputs())) {
            workflow.getOutputs().forEach((key, value) -> {
                if (value instanceof TextNode textNode) {
                    var resolvedOutput = resolver.resolveString(textNode.asText());
                    resultWorkflowOutputs.put(key, resolvedOutput);
                } else {
                    throw new RuntimeException("Unexpected");
                }
            });
        }

        return resultWorkflowOutputs;
    }

    private Workflow findWorkflowByWorkflowId(final ArazzoSpecification arazzo, final String workflowId) {
        return arazzo.getWorkflows().stream()
                .filter(wf -> workflowId.contains(wf.getWorkflowId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unexpected"));
    }

    private SourceDescription findRelevantSourceDescriptionByWorkflowId(final ArazzoSpecification arazzo, final String workflowId) {
        var sourceDescription = arazzo.getSourceDescriptions().get(0);
        if (arazzo.getSourceDescriptions().size() > 1) {
            sourceDescription = arazzo.getSourceDescriptions().stream()
                    .filter(s -> workflowId.contains(s.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Unexpected"));
        }
        return sourceDescription;
    }
}
