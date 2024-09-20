package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class SourceDescriptionValidator implements ArazzoValidator<SourceDescription> {

    private static final String LOCATION = "sourceDescription";

    @Override
    public <C> ArazzoValidationResult validate(final SourceDescription sourceDescription,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(sourceDescription.getName())) {
            result.addError(LOCATION, "'name' is mandatory");
        } else if (!isRecommendedNameFormat(sourceDescription.getName())) {
            result.addWarning(LOCATION, "name '%s' does not comply to [A-Za-z0-9_\\-]+.".formatted(sourceDescription.getName()));
        }

        if (!Strings.isNullOrEmpty(sourceDescription.getUrl())) {
            try {
                URI uri = new URI(sourceDescription.getUrl());
                if (!uri.isAbsolute() && !uri.isOpaque()) {
                    throw new URISyntaxException(sourceDescription.getUrl(), "unexpected format");
                }
            } catch (URISyntaxException e) {
                result.addInvalidType(LOCATION, "'url' error: %s".formatted(e.getMessage()), "must be a valid URI reference as per RFC3986");
            }
        } else {
            result.addError(LOCATION, "'url' is mandatory");
        }

        if (Objects.nonNull(sourceDescription.getExtensions()) && !sourceDescription.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(sourceDescription.getExtensions(), sourceDescription, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return SourceDescription.class.isAssignableFrom(clazz);
    }

    private boolean isRecommendedNameFormat(final String name) {
        return name.matches("^[A-Za-z0-9_\\-]+$");
    }
}
