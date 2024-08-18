package de.leidenheit;

import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ArazzoValidator {

    public boolean validateAgainstOpenApi(final ArazzoSpecification arazzo, final OpenAPI openAPI) {
        // TODO finalize implementation
        log.debug("Validating Arazzo against OpenApi");
        return true;
    }
}
