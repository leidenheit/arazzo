package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class StepValidator implements Validator<Step> {

    private static final String LOCATION = "step";

    @Override
    public <C> ArazzoValidationResult validate(final Step step,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(step.getStepId())) {
            result.addError(LOCATION, "'stepId' is mandatory");
        } else if (!isRecommendedStepIdFormat(step.getStepId())) {
            result.addWarning(LOCATION, "stepId '%s' does not comply to [A-Za-z0-9_\\-]+.".formatted(step.getStepId()));
        }
        Workflow parentWorkflow = findParentWorkflow(step, arazzo);
        if (Objects.nonNull(parentWorkflow)) {
            boolean uniqueStepId = parentWorkflow.getSteps().stream()
                    .filter(s -> s.getStepId().equals(step.getStepId()))
                    .count() == 1;
            if (!uniqueStepId)
                result.addError(LOCATION, "'stepId' must be unique within workflow '%s'".formatted(parentWorkflow.getWorkflowId()));
        } else {
            result.addWarning(LOCATION, "parent workflow for step '%s' not found".formatted(step.getStepId()));
        }

        int countSet = 0;
        if (!Strings.isNullOrEmpty(step.getOperationId())) countSet++;
        if (!Strings.isNullOrEmpty(step.getOperationPath())) countSet++;
        if (!Strings.isNullOrEmpty(step.getWorkflowId())) countSet++;
        if (countSet == 0) {
            result.addError(LOCATION, "'operationId', 'operationPath' or 'workflowId' must be defined");
        } else if (countSet > 1) {
            result.addError(LOCATION, "'operationId', 'operationPath' and 'workflowId' are mutually exclusive");
        }

        if (!Strings.isNullOrEmpty(step.getOperationId())) {
            boolean operationExists = validateOperationId(step.getOperationId(), arazzo, validationOptions);
            if (!operationExists) {
                result.addError(LOCATION, "'operationId' in step '%s' was not found".formatted(step.getOperationId()));
            }
        }
        if (!Strings.isNullOrEmpty(step.getOperationPath())) {
            boolean validOperationPath = validateOperationPath(step.getOperationPath(), arazzo, validationOptions);
            if (!validOperationPath) {
                result.addError(LOCATION, "'operationPath' in step '%s' was not found".formatted(step.getOperationPath()));
            }
        }
        if (!Strings.isNullOrEmpty(step.getWorkflowId())) {
            boolean workflowExists = validateWorkflowId(step.getWorkflowId(), arazzo, validationOptions);
            if (!workflowExists) {
                result.addError(LOCATION, "'workflowId' in step '%s' was not found".formatted(step.getWorkflowId()));
            }
        }

        if (Objects.nonNull(step.getRequestBody())) {
            var requestBodyValidator = new RequestBodyValidator();
            result.merge(requestBodyValidator.validate(step.getRequestBody(), step, arazzo, validationOptions));
        }

        if (Objects.nonNull(step.getSuccessCriteria())) {
            step.getSuccessCriteria().forEach(criterion -> {
                var criterionValidator = new CriterionValidator();
                result.merge(criterionValidator.validate(criterion, step, arazzo, validationOptions));
            });
        }

        if (Objects.nonNull(step.getOnSuccess())) {
            var containsDuplicates = step.getOnSuccess().stream().distinct().count() != step.getOnSuccess().size();
            if (containsDuplicates) result.addError(LOCATION, "'onSuccess' must not contain duplicates");

            var successActionValidator = new SuccessActionValidator();
            step.getOnSuccess().forEach(successAction ->
                    result.merge(successActionValidator.validate(successAction, step, arazzo, validationOptions)));
        }

        if (Objects.nonNull(step.getOnFailure())) {
            var containsDuplicates = step.getOnFailure().stream().distinct().count() != step.getOnFailure().size();
            if (containsDuplicates) result.addError(LOCATION, "'onFailure' must not contain duplicates");

            var failureActionValidator = new FailureActionValidator();
            step.getOnFailure().forEach(failureAction ->
                    result.merge(failureActionValidator.validate(failureAction, step, arazzo, validationOptions)));
        }

        if (Objects.nonNull(step.getOutputs())) {
            var validKeyFormat = step.getOutputs().keySet().stream().allMatch(this::isValidKeyFormat);
            if (!validKeyFormat) result.addError(LOCATION, "keys of 'outputs' must comply to ^[a-zA-Z0-9\\.\\-_]+$");

            // TODO consider introducing outputs validator
        }

        if (Objects.nonNull(step.getParameters())) {
            var containsDuplicates = step.getParameters().stream().distinct().count() != step.getParameters().size();
            if (containsDuplicates) result.addError(LOCATION, "'parameters' must not contain duplicates");

            var parameterValidator = new ParameterValidator();
            step.getParameters().forEach(parameter -> result.merge(
                    parameterValidator.validate(parameter, step, arazzo, validationOptions)));
        }

        if (Objects.nonNull(step.getExtensions()) && !step.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(step.getExtensions(), step, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Step.class.isAssignableFrom(clazz);
    }

    private boolean isRecommendedStepIdFormat(final String workflowId) {
        return workflowId.matches("^[A-Za-z0-9_\\\\-]+$");
    }

    private boolean isValidKeyFormat(final String key) {
        return key.matches("^[a-zA-Z0-9.\\-_]+$");
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

    private boolean validateOperationId(final String operationId,
                                        final ArazzoSpecification arazzo,
                                        final ArazzoValidationOptions validationOptions) {
        arazzo.getSourceDescriptions().forEach(sourceDescription -> {
            // TODO load OAS and validate operationId
        });
        return true;
    }

    private boolean validateOperationPath(final String operationPath,
                                          final ArazzoSpecification arazzo,
                                          final ArazzoValidationOptions validationOptions) {
        // TODO load OAS and validate operationPath
        return true;
    }

    private boolean validateWorkflowId(final String workflowId,
                                       final ArazzoSpecification arazzo,
                                       final ArazzoValidationOptions validationOptions) {
        return arazzo.getWorkflows().stream()
                .anyMatch(wf -> wf.getWorkflowId().equals(workflowId));
    }
}
