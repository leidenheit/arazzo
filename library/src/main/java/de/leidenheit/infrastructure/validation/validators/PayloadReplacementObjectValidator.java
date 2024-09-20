package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.PayloadReplacementObject;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class PayloadReplacementObjectValidator implements ArazzoValidator<PayloadReplacementObject> {

    @Override
    public <C> ArazzoValidationResult validate(final PayloadReplacementObject payloadReplacementObject,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return PayloadReplacementObject.class.isAssignableFrom(clazz);
    }
}
