package de.leidenheit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;

@Slf4j
@Component
public class ArazzoValidator {

    public ValidationResult validate(final ArazzoSpecification arazzo) {
        log.debug("Validating Arazzo itself and against OpenApi");
        // TODO finalize implementation
        //  - load source oas and validate arazzo them
        return ValidationResult.builder()
                .isValid(true)
                .build();
    }

    private URL retrieveOpenApiUri(final String oasSource) {
        //TODO resolve http or relative path
        return null;
    }

    private OpenAPI parseOpenApiSpec(final URI openapiYaml) throws IOException {
        var om = new ObjectMapper(new YAMLFactory());
        var parsed = om.readValue(new File(openapiYaml), OpenAPI.class);
        log.debug("OpenAPI: {}", parsed);
        return parsed;
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean isValid;
        private List<ValidationError> errors;

        @Data
        @Builder
        private static class ValidationError {
            private String key;
            private String detail;
        }
    }
}
