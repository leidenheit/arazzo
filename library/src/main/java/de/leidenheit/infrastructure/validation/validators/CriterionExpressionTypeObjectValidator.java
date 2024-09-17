package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.CriterionExpressionTypeObject;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class CriterionExpressionTypeObjectValidator implements ArazzoValidator<CriterionExpressionTypeObject> {

    @Override
    public ArazzoValidationResult validate(
            final CriterionExpressionTypeObject partOfArazzo,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }
}
