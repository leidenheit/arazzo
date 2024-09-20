package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SuccessAction;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class SuccessActionValidator implements ArazzoValidator<SuccessAction> {

    @Override
    public <C> ArazzoValidationResult validate(final SuccessAction successAction,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return SuccessAction.class.isAssignableFrom(clazz);
    }
}
