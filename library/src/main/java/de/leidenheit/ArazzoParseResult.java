package de.leidenheit;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class ArazzoParseResult {
    private final List<String> messages;
    private final ArazzoSpecification arazzo;
    private final boolean invalid;

    public static ArazzoParseResult ofError(final String errorMessage) {
        return ArazzoParseResult.builder()
                .arazzo(null)
                .messages(Collections.singletonList(errorMessage))
                .invalid(true)
                .build();
    }
}
