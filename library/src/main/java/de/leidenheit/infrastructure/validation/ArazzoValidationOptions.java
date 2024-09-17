package de.leidenheit.infrastructure.validation;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArazzoValidationOptions {
    private boolean strictMode;
    private boolean validateReferences;

    public static ArazzoValidationOptions ofDefault() {
        return ArazzoValidationOptions.builder()
                .strictMode(true)
                .validateReferences(false)
                .build();
    }
}
