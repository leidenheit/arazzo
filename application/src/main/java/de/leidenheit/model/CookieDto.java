package de.leidenheit.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "A cookie.")
public class CookieDto {

    @Schema(description = "Id of the cookie.")
    private long id;
    @Schema(description = "Name of the cookie.")
    private String name;
}
