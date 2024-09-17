package de.leidenheit.infrastructure.validation;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArazzoValidationResult {
    private boolean invalid;
    private List<String> messages;
}
