package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.execution.context.ExecutionResult;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.FailureAction;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.SuccessAction;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ArazzoWorkflowExecutor {

    private final SourceDescriptionInitializer sourceDescriptionInitializer = new SourceDescriptionInitializer();
    private final StepExecutor stepExecutor = new HttpStepExecutor();
    private final Map<String, Object> outputs = new HashMap<>();

    public Map<String, Object> execute(final ArazzoSpecification arazzo, final Workflow workflow, final Map<String, Object> inputs) {
        sourceDescriptionInitializer.initialize(arazzo);
        var resolver = ArazzoExpressionResolver.getInstance(arazzo, inputs);

        Map<String, Integer> retryCounters = new HashMap<>();

        int currentStepIndex = 0;
        while (currentStepIndex < workflow.getSteps().size()) {
            Step currentStep = workflow.getSteps().get(currentStepIndex);

            // TODO refactor
            // execute referenced workflow as content of this step
            if (Objects.nonNull(currentStep.getWorkflowId())) {
                executeToReferencedWorkflow(arazzo, workflow, currentStep, inputs);
                currentStepIndex++;
                continue;
            }

            // execute step content
             System.out.printf("%nRunning step '%s' of workflow '%s'%n", currentStep.getStepId(), workflow.getWorkflowId());
            var executionResult = stepExecutor.executeStep(arazzo, workflow, currentStep, resolver);
            ExecutionDecision executionDecision = handleExecutionResultActions(arazzo, workflow, currentStep, executionResult, inputs, retryCounters, resolver);

            if (executionDecision.isEnd()) {
                // workflow has found its end
                break;
            }

            if (Objects.isNull(executionDecision.nextStepIndex)) {
                // no specific reference, so choose sequentially the next step
                currentStepIndex++;
            } else {
                // we got a reference, so apply it
                currentStepIndex = executionDecision.getNextStepIndex();
            }
        }

        outputs.putAll(handleOutputs(workflow, resolver));
        return outputs;
    }

    private ExecutionDecision handleExecutionResultActions(final ArazzoSpecification arazzo,
                                                        final Workflow workflow,
                                                        final Step currentStep,
                                                        final ExecutionResult executionResult,
                                                        final Map<String, Object> inputs,
                                                        final Map<String, Integer> retryCounters,
                                                        final ArazzoExpressionResolver resolver) {
        List<SuccessAction> successActions;
        List<FailureAction> failureActions;

        if (executionResult.isSuccessful()) {
            successActions = collectSuccessActions(workflow, executionResult);
            return handleSuccessActions(arazzo, successActions, workflow, currentStep, inputs, resolver);
        } else {
            failureActions = collectFailureActions(workflow, executionResult);

            assert !failureActions.isEmpty() : "Aborting workflow '%s' due to an unsuccessful step '%s' with an empty set of failure actions"
                    .formatted(workflow.getWorkflowId(), currentStep.getStepId());

            return handleFailureActions(arazzo, failureActions, currentStep, workflow, retryCounters, inputs, resolver);
        }
    }

    private ExecutionDecision handleSuccessActions(final ArazzoSpecification arazzo,
                                                  final List<SuccessAction> actionList,
                                                  final Workflow workflow,
                                                  final Step currentStep,
                                                  final Map<String, Object> inputs,
                                                  final ArazzoExpressionResolver resolver) {
        for (SuccessAction successAction : actionList) {
            switch (successAction.getType()) {
                case GOTO -> {
                    // find referenced step or workflow to execute
                    if (Objects.nonNull(successAction.getStepId())) {
//                        var x = handleStepIdExecutionReference(arazzo, workflow, successAction.getStepId(), inputs, resolver);
                        System.out.printf("=> SuccessAction ['%s' as %s]: interrupts sequential execution and moves to step '%s'%n",
                                successAction.getName(), successAction.getType(), successAction.getStepId());
                        var x2 = findStepIndexById(workflow, successAction.getStepId());
                        return ExecutionDecision.builder()
                                .nextStepIndex(x2)
                                .isEnd(false)
                                .build();
                    } else if (Objects.nonNull(successAction.getWorkflowId())) {
                        System.out.printf("=> SuccessAction ['%s' as %s]: interrupts sequential execution and moves to workflow '%s'%n",
                                successAction.getName(), successAction.getType(), successAction.getWorkflowId());
                        var refOutputs = handleWorkflowIdExecutionReference(arazzo, successAction.getWorkflowId(), inputs);
                        outputs.putAll(refOutputs);
                        return ExecutionDecision.builder()
                                .isEnd(true)
                                .build();
                    }
                }
                case END -> {
                    System.out.printf("=> SuccessAction ['%s' as %s]: ends workflow%n", successAction.getName(), successAction.getType());

                    return ExecutionDecision.builder()
                            .isEnd(true)
                            .build();
                }
                default -> throw new RuntimeException("Unexpected");
            }
        }
//        return Optional.empty();
        return ExecutionDecision.builder()
                // .nextStepIndex(findStepIndexById(workflow, currentStep.getStepId()))
                .isEnd(false)
                .build();
    }

    private ExecutionDecision handleFailureActions(final ArazzoSpecification arazzo,
                                                final List<FailureAction> actionList,
                                                final Step currentStep,
                                                final Workflow workflow,
                                                final Map<String, Integer> retryCounters,
                                                final Map<String, Object> inputs,
                                                final ArazzoExpressionResolver resolver) {
        for (FailureAction failureAction : actionList) {

            switch (failureAction.getType()) {
                case GOTO -> {
                    // find referenced step or workflow to execute
                    if (Objects.nonNull(failureAction.getStepId())) {
//                        var x = handleStepIdExecutionReference(arazzo, workflow, failureAction.getStepId(), inputs, resolver);
                        var x2 = findStepIndexById(workflow, failureAction.getStepId());
                        System.out.printf("=> FailureAction ['%s' as %s]: interrupts sequential execution and moves to step '%s'%n",
                                failureAction.getName(), failureAction.getType(), failureAction.getStepId());
                        return ExecutionDecision.builder()
                                .nextStepIndex(x2)
                                .isEnd(false)
                                .build();
                    } else if (Objects.nonNull(failureAction.getWorkflowId())) {
                        System.out.printf("=> FailureAction ['%s' as %s]: interrupts sequential execution and moves to workflow '%s'%n",
                                failureAction.getName(), failureAction.getType(), failureAction.getWorkflowId());
                        var refOutputs = handleWorkflowIdExecutionReference(arazzo, failureAction.getWorkflowId(), inputs);
                        outputs.putAll(refOutputs);

                        return ExecutionDecision.builder()
//                                .step(currentStep)
                                .isEnd(true)
                                .build();
                    }
                }
                case END -> {
                    System.out.printf("=> FailureAction ['%s' as %s]: ends workflow%n", failureAction.getName(), failureAction.getType());
                    // return Optional.empty();
                    return ExecutionDecision.builder()
                            .isEnd(true)
                            .build();
                }
                case RETRY -> {
                    int retryCount = retryCounters.getOrDefault(currentStep.getStepId(), 0);
                    retryCount++;
                    assert retryCount < failureAction.getRetryLimit() : "Reached retry limit for step failure action %s(type=%s)"
                            .formatted(failureAction.getName(), failureAction.getType());
                    retryCounters.put(currentStep.getStepId(), retryCount);
                    System.out.printf("=> FailureAction ['%s as %s']: Retrying %s/%s after waiting %s seconds%n",
                            failureAction.getName(),
                            failureAction.getType(),
                            retryCount + 1,
                            failureAction.getRetryLimit(),
                            failureAction.getRetryAfter().longValue());

                    // find referenced step or workflow to execute before retrying current step
                    if (Objects.nonNull(failureAction.getStepId())) {
                        handleStepIdExecutionReference(arazzo, workflow, failureAction.getStepId(), inputs, resolver);
                    } else if (Objects.nonNull(failureAction.getWorkflowId())) {
                        var refOutputs = handleWorkflowIdExecutionReference(arazzo, failureAction.getWorkflowId(), inputs);
                        outputs.putAll(refOutputs);
                    }


                    doWait(failureAction.getRetryAfter().longValue());

                    return ExecutionDecision.builder()
                            .nextStepIndex(findStepIndexById(workflow, currentStep.getStepId()))
                            .isEnd(false)
                            .build();
                }
                default -> throw new RuntimeException("Unexpected");
            }
        }
        // no actions defined; stick to sequential execution
        return ExecutionDecision.builder()
                .isEnd(false)
                .build();
    }

    private Map<String, Object> handleOutputs(final Workflow workflow, final ArazzoExpressionResolver resolver) {
        var resolvedOutputs = new HashMap<String, Object>();
        if (Objects.isNull(workflow.getOutputs())) return resolvedOutputs;

        workflow.getOutputs().forEach((key, value) -> {
            Object resolvedOutput;
            if (value instanceof TextNode textNode) {
                resolvedOutput = resolver.resolveString(textNode.asText());
            } else {
                resolvedOutput = resolver.resolveString(value.toString());
            }
            if (Objects.isNull(resolvedOutput)) throw new RuntimeException("Unexpected");

            resolvedOutputs.put(key, resolvedOutput);
        });

        return resolvedOutputs;
    }

    private ExecutionDecision handleStepIdExecutionReference(final ArazzoSpecification arazzo,
                                                final Workflow workflow,
                                                final String referencedStepId,
                                                final Map<String, Object> inputs,
                                                final ArazzoExpressionResolver resolver) {
        var refStep = workflow.getSteps().stream()
                .filter(step -> referencedStepId.contains(step.getStepId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unexpected"));
        var executionResult = stepExecutor.executeStep(arazzo, workflow, refStep, resolver);
        var decision = handleExecutionResultActions(arazzo, workflow, refStep, executionResult, inputs, null, resolver);
        return decision;
//        if (Objects.nonNull(executionDecision.getStep())) {
//            stepExecutor.executeStep(arazzo, workflow, executionDecision.getStep(), resolver);
//        }
    }

    private Map<String, Object> handleWorkflowIdExecutionReference(final ArazzoSpecification arazzo,
                                                                   final String referencedWorkflowId,
                                                                   final Map<String, Object> inputs) {
        var workflowToTransferTo = findWorkflowByWorkflowId(arazzo, referencedWorkflowId);
        return execute(arazzo, workflowToTransferTo, inputs);
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

    private Workflow findWorkflowByWorkflowId(final ArazzoSpecification arazzo, final String workflowId) {
        return arazzo.getWorkflows().stream()
                .filter(wf -> workflowId.contains(wf.getWorkflowId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unexpected"));
    }

    private List<SuccessAction> collectSuccessActions(final Workflow workflow, final ExecutionResult executionResult) {
        List<SuccessAction> actions = new ArrayList<>();
        if (workflow.getSuccessActions() != null) {
            actions.addAll(workflow.getSuccessActions());
        }
        if (executionResult.getSuccessAction() != null) {
            actions.add(executionResult.getSuccessAction());
        }
        return actions;
    }

    private List<FailureAction> collectFailureActions(final Workflow workflow, final ExecutionResult executionResult) {
        List<FailureAction> actions = new ArrayList<>();
        if (workflow.getFailureActions() != null) {
            actions.addAll(workflow.getFailureActions());
        }
        if (executionResult.getFailureAction() != null) {
            actions.add(executionResult.getFailureAction());
        }
        return actions;
    }

    private void executeToReferencedWorkflow(final ArazzoSpecification arazzo,
                                             final Workflow workflow,
                                             final Step currentStep,
                                             final Map<String, Object> inputs) {
        var sourceDescription = findRelevantSourceDescriptionByWorkflowId(arazzo, currentStep.getWorkflowId());
        var refWorkflow = findWorkflowByWorkflowId(sourceDescription.getReferencedArazzo(), currentStep.getWorkflowId());

        Objects.requireNonNull(sourceDescription);
        Objects.requireNonNull(refWorkflow);

        System.out.printf("Step ['%s']: delegates to workflow '%s' by reference%n", currentStep.getStepId(), refWorkflow.getWorkflowId());
        var refWorkflowOutputs = execute(sourceDescription.getReferencedArazzo(), refWorkflow, inputs);
        outputs.putAll(refWorkflowOutputs);
    }

    private void doWait(final Long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private int findStepIndexById(final Workflow workflow, final String stepId) {
        for (int i = 0; i < workflow.getSteps().size(); i++) {
            if (workflow.getSteps().get(i).getStepId().equals(stepId)) {
                return i;
            }
        }
        throw new RuntimeException("Step not found: " + stepId);
    }

    @Data
    @Builder
    private static class ExecutionDecision {

        private Integer nextStepIndex;
        private boolean isEnd;
    }
}
