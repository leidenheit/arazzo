package de.leidenheit.infrastructure.validation.validators;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.ArazzoValidator;

import java.util.List;
import java.util.Map;

public class ExtensionsValidator implements ArazzoValidator<Map<String, Object>> {

    private static final String LOCATION = "extensions";

    @Override
    public ArazzoValidationResult validate(
            final Map<String, Object> extensions,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions) {

        var result = ArazzoValidationResult.builder().build();

        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!key.startsWith("x-")) {
                result.addError(LOCATION, "'%s' must begin with 'x-'.".formatted(key));
            }

            if (!isValidExtensionValue(value)) {
                result.addInvalidType(LOCATION, key, "must be String|Number|Boolean|Map|List|NULL");
            }
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return this.getClass().isAssignableFrom(clazz);
    }

    private boolean isValidExtensionValue(final Object value) {
        if (value == null) return true;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return true;
        if (value instanceof Map) return true;
        return value instanceof List;
    }
}
