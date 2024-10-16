package de.leidenheit.infrastructure.validation;

import de.leidenheit.core.model.ArazzoSpecification;

public interface Validator<T> {

    <C> ArazzoValidationResult validate(
            final T partOfArazzo,
            final C context,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions);

    boolean supports(final Class<?> clazz);
}
