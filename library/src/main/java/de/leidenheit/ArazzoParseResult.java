package de.leidenheit;

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
    private List<String> messages;
    private ArazzoSpecification arazzo;
    private boolean invalid;

    public static ArazzoParseResult ofError(final String errorMessage) {
        return ArazzoParseResult.builder()
                .arazzo(null)
                .messages(Collections.singletonList(errorMessage))
                .invalid(true)
                .build();
    }
}
