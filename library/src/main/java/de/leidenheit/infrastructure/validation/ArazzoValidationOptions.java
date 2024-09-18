package de.leidenheit.infrastructure.validation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
// TODO introduce parameterized handling from POM or equivalent
public class ArazzoValidationOptions {
    private boolean failFast;
    private boolean validateReferences;

    public static ArazzoValidationOptions ofDefault() {
        return ArazzoValidationOptions.builder()
                .failFast(false)
                .validateReferences(false)
                .build();
    }
}
