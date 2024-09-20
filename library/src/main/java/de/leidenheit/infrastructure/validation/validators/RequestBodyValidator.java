package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.RequestBody;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class RequestBodyValidator implements ArazzoValidator<RequestBody> {

    @Override
    public <C> ArazzoValidationResult validate(final RequestBody requestBody,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        // TODO finalize implementation
        return ArazzoValidationResult.builder().build();
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return RequestBody.class.isAssignableFrom(clazz);
    }
}
