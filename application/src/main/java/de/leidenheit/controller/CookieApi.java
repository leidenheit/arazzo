package de.leidenheit.controller;

import de.leidenheit.model.CookieDto;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@OpenAPIDefinition(
        info = @Info(
                description = "An API to handle cookies.",
                title = "Cookie API"),
        servers = {
                @Server(description = "Production", url = "http://localhost:8080"),
                @Server(description = "Develop", url = "http://localhost:8080")})
@RestController
@RequestMapping("/cookies")
public class CookieApi {

    @Operation(
            operationId = "findCookie",
            summary = "Returns cookie by its id",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "Id of a cookie.",
                            in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Here is the cookie.",
                            content = @Content(
                                    schema = @Schema(implementation = CookieDto.class),
                                    mediaType = MediaType.APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Cookie not found."
                    )
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<CookieDto> findCookie(@PathVariable("id") final long id) {
        if (id == 4711) {
            return ResponseEntity.ok(CookieDto.builder()
                    .id(id)
                    .name("Chocolate")
                    .build());
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(
            operationId = "eatCookie",
            summary = "Eats a cookie",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "Id of a cookie.",
                            in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "That was a delicious cookie.",
                            headers = @Header(name = "location", description = "The location URI.", schema = @Schema(implementation = URI.class)),
                            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Cookie not found.",
                            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)
                    )
            }
    )
    @PostMapping("/{id}/eat")
    public ResponseEntity<Void> eatCookie(@PathVariable("id") final long id) {
        if (id == 4711) {
            var locationUri = UriComponentsBuilder
                    .fromPath("/cookies/{id}/eat")
                    .buildAndExpand(id)
                    .toUri();
            return ResponseEntity.accepted().location(locationUri).build();
        }
        return ResponseEntity.notFound().build();
    }
}
