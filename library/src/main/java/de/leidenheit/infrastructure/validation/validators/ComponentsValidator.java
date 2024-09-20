package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Map;
import java.util.Objects;

public class ComponentsValidator implements Validator<Components> {

    public static final String LOCATION = "components";

    @Override
    public <C> ArazzoValidationResult validate(
            final Components components,
            final C context,
            final ArazzoSpecification arazzo,
            final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (components.getParameters() != null) {
            for (Map.Entry<String, Parameter> entry : components.getParameters().entrySet()) {
                String name = entry.getKey();
                Parameter parameter = entry.getValue();

                if (Strings.isNullOrEmpty(name)) {
                    result.addError(LOCATION, "'parameter' name is mandatory");
                }

                var parameterValidator = new ParameterValidator();
                result.merge(parameterValidator.validate(parameter, components, arazzo, validationOptions));
            }
        }

        if (components.getSuccessActions() != null) {
            for (Map.Entry<String, SuccessAction> entry : components.getSuccessActions().entrySet()) {
                String name = entry.getKey();
                SuccessAction successAction = entry.getValue();

                if (Strings.isNullOrEmpty(name)) {
                    result.addError(LOCATION, "'successAction' name is mandatory");
                }

                var successActionValidator = new SuccessActionValidator();
                result.merge(successActionValidator.validate(successAction, components, arazzo, validationOptions));
            }
        }

        if (components.getFailureActions() != null) {
            for (Map.Entry<String, FailureAction> entry : components.getFailureActions().entrySet()) {
                String name = entry.getKey();
                FailureAction failureAction = entry.getValue();

                if (Strings.isNullOrEmpty(name)) {
                    result.addError(LOCATION, "'failureAction' name is mandatory");
                }

                var failureActionValidator = new FailureActionValidator();
                result.merge(failureActionValidator.validate(failureAction, components, arazzo, validationOptions));
            }
        }

        if (Objects.nonNull(components.getExtensions()) && !components.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(components.getExtensions(), components, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Components.class.isAssignableFrom(clazz);
    }
}
