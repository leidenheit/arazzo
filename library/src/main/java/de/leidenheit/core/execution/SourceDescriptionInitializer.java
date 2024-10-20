package de.leidenheit.core.execution;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.infrastructure.parsing.ArazzoParseOptions;
import de.leidenheit.infrastructure.parsing.ArazzoParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.util.Collections;

public class SourceDescriptionInitializer {

    public static void initialize(final ArazzoSpecification arazzo) {
        arazzo.getSourceDescriptions().forEach(sourceDescription -> {
            switch (sourceDescription.getType()) {
                case OPENAPI:
                    initializeAsOpenAPI(sourceDescription);
                    break;
                case ARAZZO:
                    initializeAsArazzo(sourceDescription);
                    break;
                default:
                    throw new RuntimeException("Unexpected");
            }
        });
    }

    private static void initializeAsOpenAPI(final SourceDescription sourceDescription) {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        ParseOptions options = new ParseOptions();
        options.setResolveFully(true);
        options.setOaiAuthor(false);
        OpenAPI openAPI = parser.read(sourceDescription.getUrl(), Collections.emptyList(), options);
        sourceDescription.setReferencedOpenAPI(openAPI);
    }

    private static void initializeAsArazzo(final SourceDescription sourceDescription) {
        ArazzoParser parser = new ArazzoParser();
        var options = ArazzoParseOptions.builder()
                .oaiAuthor(false)
                .allowEmptyStrings(false)
                .mustValidate(true)
                .resolve(true)
                .build();
        var refArazzo = parser.readLocation(sourceDescription.getUrl(), options);
        if (refArazzo.isInvalid()) {
            throw new RuntimeException("Unexpected");
        }
        sourceDescription.setReferencedArazzo(refArazzo.getArazzo());
    }
}
