package de.leidenheit.infrastructure.parsing;

import de.leidenheit.core.model.ArazzoSpecification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArazzoParseResult {
    private boolean valid = true;
    private boolean invalid;
    private List<String> messages;
    private ArazzoSpecification arazzo;

    public static ArazzoParseResult ofError(final String errorMessage) {
        return ArazzoParseResult.builder()
                .arazzo(null)
                .messages(Collections.singletonList(errorMessage))
                .invalid(true)
                .build();
    }
}
