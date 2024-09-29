package de.leidenheit.infrastructure.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.parsing.ArazzoComponentsReferenceResolver;
import io.swagger.util.ObjectMapperFactory;
import org.apache.commons.io.FileUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ArazzoInputsReader {

    private static final ObjectMapper JSON_MAPPER;
    private static final ObjectMapper YAML_MAPPER;

    static {
        JSON_MAPPER = ObjectMapperFactory.createJson();
        YAML_MAPPER = ObjectMapperFactory.createYaml();
    }

    public static Map<String, Object> parseAndValidateInputs(final ArazzoSpecification arazzo, final String inputsFilePath, final JsonNode schemaNode) {
        try {
            var inputs = readInputs(inputsFilePath);
            var mapper = getMapper(inputs.toString());

            JSONObject jsonObject = new JSONObject(mapper.writeValueAsString(inputs));

            // validate against schema
            Schema schema;
            if (schemaNode.has("$ref")) {
                var componentsNode = mapper.convertValue(arazzo.getComponents(), JsonNode.class);
                var resolver = ArazzoComponentsReferenceResolver.getInstance(componentsNode);
                schema = loadSchema(resolver.resolveComponent(schemaNode.get("$ref").asText()));
            } else {
                schema = loadSchema(schemaNode);
            }

            schema.validate(jsonObject);

            return mapper.convertValue(inputs, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Schema loadSchema(final JsonNode schemaNode) {
        JSONObject rawSchema = new JSONObject(new JSONTokener(schemaNode.toString()));
        return SchemaLoader.load(rawSchema);
    }

    private static JsonNode readInputs(final String inputsFilePath) throws IOException {
        var file = new File(inputsFilePath);
        if (file.exists()) {
            var contentAsString = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            return new ObjectMapper().readTree(contentAsString);
        }
        throw new RuntimeException("Unexpected");
    }

    private static ObjectMapper getMapper(final String data) {
        if (data.trim().startsWith("{")) {
            return JSON_MAPPER;
        }
        return YAML_MAPPER;
    }

    private ArazzoInputsReader() {}
}
