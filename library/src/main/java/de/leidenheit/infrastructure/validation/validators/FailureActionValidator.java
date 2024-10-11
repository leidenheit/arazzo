package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class FailureActionValidator implements Validator<FailureAction> {

    private static final String LOCATION = "failureObject";

    @Override
    public <C> ArazzoValidationResult validate(final FailureAction failureAction,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(failureAction.getName())) result.addError(LOCATION, "'name' is mandatory");
        if (Objects.isNull(failureAction.getType())) result.addError(LOCATION, "'type' is mandatory");

        if (FailureAction.FailureActionType.GOTO.equals(failureAction.getType())
                || FailureAction.FailureActionType.RETRY.equals(failureAction.getType())
        ) {
            int countSet = 0;
            if (failureAction.getWorkflowId() != null) countSet++;
            if (failureAction.getStepId() != null) countSet++;

            if (countSet == 0 && FailureAction.FailureActionType.GOTO.equals(failureAction.getType())) {
                result.addError(LOCATION, "'goto' requires one of 'workflowId' or 'stepId'");
            } else if (countSet > 1) {
                result.addError(LOCATION, "'goto' mutually excludes 'workflowId' and 'stepId'");
            }

            if (Objects.nonNull(failureAction.getWorkflowId())) {
                var workflowExists = arazzo.getWorkflows().stream()
                        .anyMatch(wf -> wf.getWorkflowId().equals(failureAction.getWorkflowId()));
                if (!workflowExists) {
                    result.addError(LOCATION, "workflow '%s' not found".formatted(failureAction.getWorkflowId()));
                }
            }

            if (Objects.nonNull(failureAction.getStepId()) && (context instanceof Step stepContext)) {
                var parentWorkflow = findParentWorkflow(stepContext, arazzo);
                if (Objects.nonNull(parentWorkflow)) {
                    var stepExists = parentWorkflow.getSteps().stream()
                            .anyMatch(step -> step.getStepId().equals(failureAction.getStepId()));
                    if (!stepExists) {
                        result.addError(LOCATION, "step '%s' not found".formatted(failureAction.getStepId()));
                    }
                } else {
                    result.addWarning(LOCATION, "workflow not found for step in context %s".formatted(failureAction.getStepId()));
                }
            }

            if (FailureAction.FailureActionType.RETRY.equals(failureAction.getType())) {
                if (Objects.nonNull(failureAction.getRetryAfter())) {
                    if (failureAction.getRetryAfter().doubleValue() < 0) {
                        result.addError(LOCATION, "'retryAfter' must be non-negative");
                    }
                }

                if (Objects.nonNull(failureAction.getRetryLimit())) {
                    if (failureAction.getRetryLimit() < 0) {
                        result.addError(LOCATION, "'retryLimit' must be non-negative integer");
                    }
                }
            } else {
                if (Objects.nonNull(failureAction.getRetryAfter()) || Objects.nonNull(failureAction.getRetryLimit())) {
                    result.addError(LOCATION, "'retryAfter' and 'retryLimit' allowed on 'retry' only");
                }
            }
        } else if (FailureAction.FailureActionType.END.equals(failureAction.getType())) {
            if (Objects.nonNull(failureAction.getWorkflowId()) || Objects.nonNull(failureAction.getStepId())) {
                result.addError(LOCATION, "'end' must not have 'workflowId' nor 'stepId'");
            }
            if (Objects.nonNull(failureAction.getRetryAfter()) || Objects.nonNull(failureAction.getRetryLimit())) {
                result.addError(LOCATION, "'retryAfter' and 'retryLimit' allowed on 'retry' only");
            }
        }

        if (Objects.nonNull(failureAction.getCriteria())) {
            failureAction.getCriteria().forEach(criterion -> {
                var criterionValidator = new CriterionValidator();
                result.merge(criterionValidator.validate(criterion, failureAction, arazzo, validationOptions));
            });
        }

        if (Objects.nonNull(failureAction.getExtensions()) && !failureAction.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(failureAction.getExtensions(), failureAction, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return FailureAction.class.isAssignableFrom(clazz);
    }

    private Workflow findParentWorkflow(final Step step,
                                        final ArazzoSpecification arazzo) {
        for (Workflow workflow : arazzo.getWorkflows()) {
            if (workflow.getSteps().contains(step)) {
                return workflow;
            }
        }
        return null;
    }
}
