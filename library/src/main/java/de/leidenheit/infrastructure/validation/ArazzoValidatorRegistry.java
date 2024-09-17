package de.leidenheit.infrastructure.validation;


import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.validation.validators.*;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.Map;
import java.util.Objects;

public class ArazzoValidatorRegistry {

    private final Map<Class<?>, ArazzoValidator<?>> validators = Map.ofEntries(
            Map.entry(Info.class, new InfoValidator()),
            Map.entry(SourceDescription.class, new SourceDescriptionValidator()),
            Map.entry(Workflow.class, new WorkflowValidator()),
            Map.entry(Step.class, new StepValidator()),
            Map.entry(Parameter.class, new ParameterValidator()),
            Map.entry(SuccessAction.class, new SuccessActionValidator()),
            Map.entry(FailureAction.class, new FailureActionValidator()),
            Map.entry(Criterion.class, new CriterionValidator()),
            Map.entry(CriterionExpressionTypeObject.class, new CriterionExpressionTypeObjectValidator()),
            Map.entry(RequestBody.class, new RequestBodyValidator()),
            Map.entry(PayloadReplacementObject.class, new PayloadReplacementObjectValidator()),
            Map.entry(ReusableObject.class, new ReusableObjectValidator()),
            Map.entry(Components.class, new ComponentsValidator())
    );

    public ArazzoValidationResult validateAgainstOpenApi(final ArazzoSpecification arazzo, final OpenAPI openAPI) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }

    public ArazzoValidationResult validate(final ArazzoSpecification arazzo, final ArazzoValidationOptions options) {
        // TODO finalize implementation
        //  forEach element do validate
        return ArazzoValidationResult.builder().build();
    }

    @SuppressWarnings("unchecked")
    private <T> ArazzoValidationResult validate(
            final T partOfArazzo,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions options) {
        ArazzoValidator<T> validator = (ArazzoValidator<T>) validators.get(partOfArazzo.getClass());
        if (Objects.isNull(validator)) throw new RuntimeException("Unexpected");
        return validator.validate(partOfArazzo, arazzo, options);
    }
}
