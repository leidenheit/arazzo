package de.leidenheit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArazzoParseOptions {

    private final boolean oaiAuthor;
    private final boolean inferSchemaType;
    private final boolean allowEmptyStrings;
    private final boolean validateInternalRefs;
    private final boolean resolve;
}
