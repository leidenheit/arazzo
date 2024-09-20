package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.RequestBody;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class RequestBodyValidator implements Validator<RequestBody> {

    public static final String LOCATION = "requestBody";

    @Override
    public <C> ArazzoValidationResult validate(final RequestBody requestBody,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(requestBody.getContentType())) result.addWarning(LOCATION, "'contentType' is not defined");
        if (Objects.isNull(requestBody.getPayload())) result.addError(LOCATION, "'payload' is mandatory");

        if (!requestBody.getReplacements().isEmpty()) {
            var payloadReplacementObjectValidator = new PayloadReplacementObjectValidator();
            requestBody.getReplacements().forEach(payloadReplacementObject ->
                    result.merge(payloadReplacementObjectValidator.validate(
                            payloadReplacementObject, requestBody, arazzo, validationOptions)));
        }

        if (Objects.nonNull(requestBody.getExtensions()) && !requestBody.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(requestBody.getExtensions(), requestBody, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return RequestBody.class.isAssignableFrom(clazz);
    }
}
