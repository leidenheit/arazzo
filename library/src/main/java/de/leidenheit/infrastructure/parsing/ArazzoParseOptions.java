package de.leidenheit.infrastructure.parsing;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArazzoParseOptions {

    private final boolean oaiAuthor;
    private final boolean allowEmptyStrings;
    private final boolean mustValidate; // TODO implementation
    private final boolean resolve; // TODO implementation
}
