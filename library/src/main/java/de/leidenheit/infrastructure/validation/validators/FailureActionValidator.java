package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.FailureAction;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class FailureActionValidator implements ArazzoValidator<FailureAction> {

    @Override
    public <C> ArazzoValidationResult validate(final FailureAction failureAction,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return FailureAction.class.isAssignableFrom(clazz);
    }
}
