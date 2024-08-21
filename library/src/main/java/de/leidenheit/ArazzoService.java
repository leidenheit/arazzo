package de.leidenheit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

@Slf4j
@Service
public class ArazzoService {

    private final ArazzoParser arazzoParser;
    private final ArazzoExecutor arazzoExecutor;

    public ArazzoService(
            final ArazzoParser arazzoParser,
            final ArazzoExecutor arazzoExecutor) {
        this.arazzoParser = arazzoParser;
        this.arazzoExecutor = arazzoExecutor;
    }

    public void arazzoMe(final String arazzoYamlFilePath, final String openapiYamlFilePath)
            throws URISyntaxException, IOException {
        URL arazzoUrl = getClass().getClassLoader().getResource(arazzoYamlFilePath);
        if (Objects.isNull(arazzoUrl)) {
            log.error("Resource not found: {}", arazzoYamlFilePath);
            return;
        }
        URL openApiUrl = getClass().getClassLoader().getResource(openapiYamlFilePath);
        if (Objects.isNull(openApiUrl)) {
            log.error("Resource not found: {}", openapiYamlFilePath);
            return;
        }

        // parse specifications
        var arazzoSpecification = parseArazzoSpec(arazzoUrl.toURI());
//        var openApiSpecification = parseOpenApiSpec(openApiUrl.toURI());

        // execute workflows
        executeArazzo(arazzoSpecification);
    }

    private ArazzoSpecification parseArazzoSpec(final URI arazzoYaml) throws IOException {
        var parsed = arazzoParser.parseYaml(
                new File(arazzoYaml),
                ArazzoParser.Configuration.builder().build());
        log.debug("Arazzo: {}", parsed);
        return parsed;
    }

//    private OpenAPI parseOpenApiSpec(final URI openapiYaml) throws IOException {
//        var om = new ObjectMapper(new YAMLFactory());
//        var parsed = om.readValue(new File(openapiYaml), OpenAPI.class);
//        log.debug("OpenAPI: {}", parsed);
//        return parsed;
//    }

    private void executeArazzo(final ArazzoSpecification arazzoSpecification) {
        arazzoExecutor.executeWorkflows(arazzoSpecification);
    }
}
