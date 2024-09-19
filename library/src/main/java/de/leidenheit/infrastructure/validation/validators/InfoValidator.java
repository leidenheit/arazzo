package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Info;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class InfoValidator implements ArazzoValidator<Info> {

    private static final String LOCATION = "info";

    @Override
    public ArazzoValidationResult validate(
            final Info info,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions) {

        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(info.getTitle())) {
            result.addMissing(LOCATION, "title");
            if (validationOptions.isFailFast()) {
                return result;
            }
        }
        if (Strings.isNullOrEmpty(info.getVersion())) {
            result.addMissing(LOCATION, "version");
            if (validationOptions.isFailFast()) {
                return result;
            }
        } else if (!isSemanticVersioningFormat(info.getVersion())) {
            result.addWarning(LOCATION, "'version' does not adhere to semantic versioning");
        }

        if (!info.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionValidator();
            result.merge(extensionValidator.validate(info.getExtensions(), arazzo, validationOptions));
        }
        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Info.class.isAssignableFrom(clazz);
    }

    private boolean isSemanticVersioningFormat(final String version) {
        return version.matches("\\d+\\.\\d+\\.\\d+");
    }
}
