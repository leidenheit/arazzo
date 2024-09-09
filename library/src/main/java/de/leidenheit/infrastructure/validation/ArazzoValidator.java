package de.leidenheit.infrastructure.validation;

import de.leidenheit.core.model.ArazzoSpecification;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArazzoValidator {

    public boolean validateAgainstOpenApi(final ArazzoSpecification arazzo, final OpenAPI openAPI) {
        // TODO finalize implementation
        return true;
    }
}
