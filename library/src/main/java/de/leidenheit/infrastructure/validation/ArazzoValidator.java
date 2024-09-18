package de.leidenheit.infrastructure.validation;

import de.leidenheit.core.model.ArazzoSpecification;

public interface ArazzoValidator<T> {

    ArazzoValidationResult validate(
            final T partOfArazzo,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions);

    boolean supports(final Class<?> clazz);
}
