package de.leidenheit.infrastructure.validation;


import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.validation.validators.*;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ArazzoValidatorRegistry {

    private final List<ArazzoValidator<?>> validators = new ArrayList<>(
            // register default validators
            List.of(
                    new InfoValidator(),
                    new SourceDescriptionValidator(),
                    new WorkflowValidator(),
                    new StepValidator(),
                    new ParameterValidator(),
                    new SuccessActionValidator(),
                    new FailureActionValidator(),
                    new CriterionValidator(),
                    new CriterionExpressionTypeObjectValidator(),
                    new RequestBodyValidator(),
                    new PayloadReplacementObjectValidator(),
                    new ReusableObjectValidator(),
                    new ComponentsValidator()
            ));

    public void register(final ArazzoValidator<?> validator) {
        validators.add(validator);
    }

    public ArazzoValidationResult validateAgainstOpenApi(final ArazzoSpecification arazzo, final OpenAPI openAPI) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }

    public ArazzoValidationResult validate(final ArazzoSpecification arazzo, final ArazzoValidationOptions options) {
        var result = ArazzoValidationResult.builder().build();

        // info
        result.merge(validateObject(arazzo.getInfo(), arazzo, options));

        // sourceDescriptions
        arazzo.getSourceDescriptions().forEach(sourceDescription ->
            result.merge(validateObject(sourceDescription, arazzo, options))
        );

        // TODO finalize implementation
        //  forEach element do validate

        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> ArazzoValidationResult validateObject(
            final T partOfArazzo,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions options) {
        ArazzoValidator<T> validator = (ArazzoValidator<T>) findValidatorForObject(partOfArazzo);
        if (Objects.isNull(validator)) throw new RuntimeException("Unexpected");
        return validator.validate(partOfArazzo, arazzo, options);
    }

    private <T> ArazzoValidator<?> findValidatorForObject(final T partOfArazzo) {
        for (ArazzoValidator<?> validator : validators) {
            if (validator.supports(partOfArazzo.getClass())) {
                return validator;
            }
        }
        return null;
    }
}
