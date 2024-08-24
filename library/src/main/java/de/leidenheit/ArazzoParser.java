package de.leidenheit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.util.ObjectMapperFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@RequiredArgsConstructor
public class ArazzoParser implements ArazzoParserExtension {

    private static final Charset ENCODING = StandardCharsets.UTF_8;

    private static final ObjectMapper JSON_MAPPER;
    private static final ObjectMapper YAML_MAPPER;

    static {
        JSON_MAPPER = ObjectMapperFactory.createJson();
        YAML_MAPPER = ObjectMapperFactory.createYaml();
    }

    @Deprecated(since = "ArazzoParserExtension", forRemoval = true)
    public ArazzoSpecification parseYamlRaw(final File workflowSpecificationFile) throws IOException {
        var yamlMapper = new ObjectMapper(new YAMLFactory());
        return yamlMapper.readValue(workflowSpecificationFile, ArazzoSpecification.class);
    }

    @Override
    public ArazzoParseResult readLocation(final String arazzoUrl, final ArazzoParseOptions options) {
        try {
            var content = readContentFromLocation(arazzoUrl);
            return readContents(content, options, arazzoUrl);
        } catch (Exception e) {
            return ArazzoParseResult.ofError(e.getMessage());
        }
    }

    private String readContentFromLocation(final String location) {
        final String adjustedLocation = location.replace("\\\\", "/");
        try {
            final String fileScheme = "file:";
            final Path path = adjustedLocation.toLowerCase().startsWith(fileScheme) ?
                    Paths.get(URI.create(adjustedLocation)) : Paths.get(adjustedLocation);
            if (Files.exists(path)) {
                return FileUtils.readFileToString(path.toFile(), ENCODING);
            } else {
                try (var is = getClass().getClassLoader().getResourceAsStream(location)) {
                    return new String(Objects.requireNonNull(is).readAllBytes(), ENCODING);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public ArazzoParseResult readContents(final String arazzoAsString, final ArazzoParseOptions options) {
        throw new RuntimeException("Not yet implemented.");
    }

    private ArazzoParseResult readContents(final String arazzoAsString,
                                           final ArazzoParseOptions options,
                                           final String location) {
        if (Objects.isNull(arazzoAsString) || arazzoAsString.trim().isEmpty()) {
            return ArazzoParseResult.ofError("Null or empty definition");
        }

        try {
            ArazzoParseResult arazzoParseResult;

            final var mapper = getMapper(arazzoAsString);
            JsonNode rootNode = mapper.readTree(arazzoAsString);

            if (Objects.nonNull(options)) {
                arazzoParseResult = parseJsonNode(location, rootNode, options);
            } else {
                arazzoParseResult = parseJsonNode(location, rootNode);
            }
            if (Objects.nonNull(arazzoParseResult.getArazzo())) {
                arazzoParseResult = resolve(arazzoParseResult, options, location);
            }
            return arazzoParseResult;
        } catch (Exception e) {
            var msg = String.format("location:%s; msg=%s", location, e.getMessage());
            return ArazzoParseResult.ofError(msg);
        }
    }

    private ArazzoParseResult resolve(final ArazzoParseResult arazzoParseResult,
                                      final ArazzoParseOptions options,
                                      final String location) {
        return ArazzoParseResult.ofError("Not yet implemented");
    }

    private ArazzoParseResult parseJsonNode(final String path, final JsonNode node, final ArazzoParseOptions options) {
        return new ArazzoDeserializer().deserialize(node, path, options);
    }

    private ArazzoParseResult parseJsonNode(final String path, final JsonNode node) {
        var options = ArazzoParseOptions.builder().build();
        return new ArazzoDeserializer().deserialize(node, path, options);
    }



    private ObjectMapper getMapper(final String data) {
        if (data.trim().startsWith("{")) {
            return JSON_MAPPER;
        }
        return YAML_MAPPER;
    }
}
