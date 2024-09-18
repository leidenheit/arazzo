package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Info;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

public class InfoValidator implements ArazzoValidator<Info> {

    @Override
    public ArazzoValidationResult validate(
            final Info info,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(info.getTitle())) {
            result.addMissing("info", "title");
            if (validationOptions.isFailFast()) {
                return result;
            }
        }
        if (Strings.isNullOrEmpty(info.getVersion())) {
            result.addMissing("info", "version");
            if (validationOptions.isFailFast()) {
                return result;
            }
        } else if (!isSemanticVersioningFormat(info.getVersion())) {
            result.addWarning("info", "\"version\" does not adhere to semantic versioning");
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
