package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class SourceDescriptionValidator implements ArazzoValidator<SourceDescription> {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]+$");
    private static final String LOCATION = "sourceDescription";

    @Override
    public ArazzoValidationResult validate(
            final SourceDescription sourceDescription,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions) {

        var result = ArazzoValidationResult.builder().build();

        if (sourceDescription.getName() == null || sourceDescription.getName().isEmpty()) {
            result.addMissing(LOCATION, "name");
        } else if (!isValidPattern(sourceDescription.getName(), NAME_PATTERN)) {
            result.addWarning(LOCATION, "'name' field should match the regular expression %s.".formatted(NAME_PATTERN.toString()));
        }

        if (sourceDescription.getUrl() == null || sourceDescription.getUrl().isEmpty()) {
            result.addMissing(LOCATION, "url");
        } else {
            try {
                URI uri = new URI(sourceDescription.getUrl());
                if (!uri.isAbsolute() && !uri.isOpaque()) {
                    throw new URISyntaxException(sourceDescription.getUrl(), "unexpected format");
                }
            } catch (URISyntaxException e) {
                result.addInvalidType(LOCATION, "'url' error: %s".formatted(e.getMessage()), "must be a valid URI reference as per RFC3986");
            }
        }

        if (!sourceDescription.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionValidator();
            result.merge(extensionValidator.validate(sourceDescription.getExtensions(), arazzo, validationOptions));
        }
        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return SourceDescription.class.isAssignableFrom(clazz);
    }

    private boolean isValidPattern(final String input, final Pattern pattern) {
        return pattern.matcher(input).matches();
    }
}
