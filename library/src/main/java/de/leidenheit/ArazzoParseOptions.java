package de.leidenheit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArazzoParseOptions {
    private final boolean resolve;
}
