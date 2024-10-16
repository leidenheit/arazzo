package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;

import java.util.*;

public class ArazzoWorkflowExecutor {

    private final SourceDescriptionInitializer sourceDescriptionInitializer = new SourceDescriptionInitializer();
    private final StepExecutor stepExecutor = new HttpStepExecutor();

    private final Map<String, Object> outputs = new HashMap<>();

    public Map<String, Object> execute(final ArazzoSpecification arazzo, final Workflow workflow, final Map<String, Object> inputs) {
        sourceDescriptionInitializer.initialize(arazzo);

        var resolver = ArazzoExpressionResolver.getInstance(arazzo, inputs);

        // execute
        Iterator<Step> stepIterator = workflow.getSteps().iterator();
        Step currentStep = stepIterator.hasNext() ? stepIterator.next() : null;
        Map<String, Integer> retryCounters = new HashMap<>();
        while (Objects.nonNull(currentStep)) {
            // referenced a workflow from another arazzo
            if (Objects.nonNull(currentStep.getWorkflowId())) {

                var sourceDescription = findRelevantSourceDescriptionByWorkflowId(arazzo, currentStep.getWorkflowId());
                var refWorkflow = findWorkflowByWorkflowId(sourceDescription.getReferencedArazzo(), currentStep.getWorkflowId());

                Objects.requireNonNull(sourceDescription);
                Objects.requireNonNull(refWorkflow);

                System.out.printf("Delegating execution of workflow '%s' referenced by step '%s' of workflow '%s'%n", refWorkflow.getWorkflowId(), currentStep.getStepId(), workflow.getWorkflowId());

                var refWorkflowOutputs = execute(sourceDescription.getReferencedArazzo(), refWorkflow, inputs);
                outputs.putAll(refWorkflowOutputs);

                if (stepIterator.hasNext()) {
                    currentStep = stepIterator.next();
                } else {
                    currentStep = null;
                }
                continue;
            }

            // within current arazzo
            System.out.printf("Running step '%s' of workflow '%s'%n", currentStep.getStepId(), workflow.getWorkflowId());
            var executionResult = stepExecutor.executeStep(arazzo, workflow, currentStep, resolver);
            Optional<Step> nextStep = Optional.empty();
            if (executionResult.isSuccessful()) {
                var actionList = new ArrayList<SuccessAction>();
                if (Objects.nonNull(workflow.getSuccessActions())) {
                    actionList.addAll(workflow.getSuccessActions());
                }
                if (Objects.nonNull(executionResult.getSuccessAction())) {
                    actionList.add(executionResult.getSuccessAction());
                }
                if (!actionList.isEmpty()) {
                    nextStep = handleSuccessActions(arazzo, actionList, currentStep, workflow, workflow.getSteps(), inputs, resolver);
                }
            } else {
                var actionList = new ArrayList<FailureAction>();
                if (Objects.nonNull(workflow.getFailureActions())) {
                    actionList.addAll(workflow.getFailureActions());
                }
                if (Objects.nonNull(executionResult.getFailureAction())) {
                    actionList.add(executionResult.getFailureAction());
                }

                // no failure actions defined
                assert !actionList.isEmpty() : "Aborting workflow '%s' due to an unsuccessful step '%s' with an empty set of failure actions"
                        .formatted(workflow.getWorkflowId(), currentStep.getStepId());

                nextStep = handleFailureActions(arazzo, actionList, currentStep, workflow, workflow.getSteps(), retryCounters, inputs, resolver);
            }

            if (nextStep.isPresent()) {
                currentStep = nextStep.get();
            } else if (stepIterator.hasNext()) {
                currentStep = stepIterator.next();
            } else {
                currentStep = null;
            }
        }

        // resolve and store outputs
        outputs.putAll(handleOutputs(workflow, resolver));

        return outputs;
    }

    private Optional<Step> handleSuccessActions(final ArazzoSpecification arazzo,
                                                final ArrayList<SuccessAction> actionList,
                                                final Step currentStep,
                                                final Workflow workflow,
                                                final List<Step> steps,
                                                final Map<String, Object> inputs,
                                                final ArazzoExpressionResolver resolver) {
        for (SuccessAction successAction : actionList) {
            switch (successAction.getType()) {
                case GOTO -> {
                    System.out.printf("Interrupting sequential step execution due to success action %s(type=%s)%n",
                            successAction.getName(), successAction.getType());
                    // find referenced step or workflow to execute
                    if (Objects.nonNull(successAction.getStepId())) {
                        var refStep = workflow.getSteps().stream()
                                .filter(step -> successAction.getStepId().contains(step.getStepId()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Unexpected"));
                        var executionResult = stepExecutor.executeStep(arazzo, workflow, refStep, resolver);
                        if (!executionResult.isSuccessful()) {
                            System.out.printf("Referenced step '%s' in success action was not successful%n", refStep.getStepId());
                            // TODO handle failure actions also in retry context
                        } else {
                            // TODO handle success actions also in retry context
                        }
                    } else if (Objects.nonNull(successAction.getWorkflowId())) {
                        var workflowToTransferTo = findWorkflowByWorkflowId(arazzo, successAction.getWorkflowId());
                        var refOutputs = execute(arazzo, workflowToTransferTo, inputs);
                        outputs.putAll(refOutputs);
                    }
                    return Optional.empty();
                }
                case END -> {
                    System.out.printf("Ending sequential step execution due to success action %s(type=%s)%n",
                            successAction.getName(), successAction.getType());
                    return Optional.empty();
                }
                default -> throw new RuntimeException("Unexpected");
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("java:S5960") // assertion in production code
    private Optional<Step> handleFailureActions(final ArazzoSpecification arazzo,
                                                final ArrayList<FailureAction> actionList,
                                                final Step currentStep,
                                                final Workflow workflow,
                                                final List<Step> steps,
                                                final Map<String, Integer> retryCounters,
                                                final Map<String, Object> inputs,
                                                final ArazzoExpressionResolver resolver) {
        for (FailureAction failureAction : actionList) {

            switch (failureAction.getType()) {
                case GOTO -> {
                    System.out.printf("Interrupting sequential step execution due to failure action %s(type=%s)%n",
                            failureAction.getName(), failureAction.getType());
                    // find referenced step or workflow to execute
                    if (Objects.nonNull(failureAction.getStepId())) {
                        var refStep = workflow.getSteps().stream()
                                .filter(step -> failureAction.getStepId().contains(step.getStepId()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Unexpected"));
                        var executionResult = stepExecutor.executeStep(arazzo, workflow, refStep, resolver);
                        if (!executionResult.isSuccessful()) {
                            System.out.printf("Referenced step '%s' in failure action was not successful%n", refStep.getStepId());
                            // TODO handle failure actions also in retry context
                        } else {
                            // TODO handle success actions also in retry context
                        }
                    } else if (Objects.nonNull(failureAction.getWorkflowId())) {
                        var workflowToTransferTo = findWorkflowByWorkflowId(arazzo, failureAction.getWorkflowId());
                        var refOutputs = execute(arazzo, workflowToTransferTo, inputs);
                        outputs.putAll(refOutputs);
                    }
                    // return empty in order to leave loop
                    return Optional.empty();
                }
                case END -> {
                    System.out.printf("Ending sequential step execution due to failure action %s(type=%s)%n",
                            failureAction.getName(), failureAction.getType());
                    return Optional.empty();
                }
                case RETRY -> {
                    int retryCount = retryCounters.getOrDefault(currentStep.getStepId(), 0);
                    retryCount++;

                    assert retryCount < failureAction.getRetryLimit() : "Reached retry limit for step failure action %s(type=%s)"
                            .formatted(failureAction.getName(), failureAction.getType());

                    if (retryCount < failureAction.getRetryLimit()) {
                        try {
                            System.out.printf("Waiting %s seconds before retry%n", failureAction.getRetryAfter());
                            Thread.sleep(failureAction.getRetryAfter().longValue() * 1000L);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        retryCounters.put(currentStep.getStepId(), retryCount);
                        System.out.printf("Retrying (%s/%s) step execution due to failure action %s(type=%s)%n",
                                retryCount, failureAction.getRetryLimit(), failureAction.getName(), failureAction.getType());

                        // find referenced step or workflow to execute before retrying current step
                        if (Objects.nonNull(failureAction.getStepId())) {
                            var refStep = workflow.getSteps().stream()
                                    .filter(step -> failureAction.getStepId().contains(step.getStepId()))
                                    .findFirst()
                                    .orElseThrow(() -> new RuntimeException("Unexpected"));
                            var executionResult = stepExecutor.executeStep(arazzo, workflow, refStep, resolver);
                            if (!executionResult.isSuccessful()) {
                                System.out.printf("Referenced step '%s' in failure action was not successful%n", refStep.getStepId());
                                // TODO handle failure actions also in retry context
                                var refActionList = new ArrayList<FailureAction>();
                                if (Objects.nonNull(workflow.getFailureActions())) {
                                    refActionList.addAll(workflow.getFailureActions());
                                }
                                if (Objects.nonNull(executionResult.getFailureAction())) {
                                    refActionList.add(executionResult.getFailureAction());
                                }
                                if (!refActionList.isEmpty()) {
                                    return handleFailureActions(arazzo, refActionList, refStep, workflow, workflow.getSteps(), retryCounters, inputs, resolver);
                                }
                            } else {
                                System.out.printf("Referenced step '%s' in success action was successful%n", refStep.getStepId());
                                // TODO handle success actions also in retry context
                                var refActionList = new ArrayList<SuccessAction>();
                                if (Objects.nonNull(workflow.getSuccessActions())) {
                                    refActionList.addAll(workflow.getSuccessActions());
                                }
                                if (Objects.nonNull(executionResult.getSuccessAction())) {
                                    refActionList.add(executionResult.getSuccessAction());
                                }
                                if (!refActionList.isEmpty()) {
                                    return handleSuccessActions(arazzo, refActionList, refStep, workflow, workflow.getSteps(), inputs, resolver);
                                }
                            }
                        } else if (Objects.nonNull(failureAction.getWorkflowId())) {
                            var workflowToTransferTo = findWorkflowByWorkflowId(arazzo, failureAction.getWorkflowId());
                            var refOutputs = execute(arazzo, workflowToTransferTo, inputs);
                            outputs.putAll(refOutputs);
                        }
                        return Optional.of(currentStep);
                    }
                }
                default -> throw new RuntimeException("Unexpected");
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> handleOutputs(final Workflow workflow, final ArazzoExpressionResolver resolver) {
        var res = new HashMap<String, Object>();
        if (Objects.nonNull(workflow.getOutputs())) {
            workflow.getOutputs().forEach((key, value) -> {
                Object resolvedOutput = null;
                if (value instanceof TextNode textNode) {
                    resolvedOutput = resolver.resolveString(textNode.asText());
                } else {
                    resolvedOutput = resolver.resolveString(value.toString());
                }
                if (Objects.nonNull(resolvedOutput)) {
                    res.put(key, resolvedOutput);
                } else {
                    throw new RuntimeException("Unexpected");
                }
            });
        }
        return res;
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
