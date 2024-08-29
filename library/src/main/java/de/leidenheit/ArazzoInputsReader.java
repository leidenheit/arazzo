package de.leidenheit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.util.ObjectMapperFactory;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Map;

public class ArazzoInputsReader {

    private static final ObjectMapper JSON_MAPPER;
    private static final ObjectMapper YAML_MAPPER;

    static {
        JSON_MAPPER = ObjectMapperFactory.createJson();
        YAML_MAPPER = ObjectMapperFactory.createYaml();
    }

    public static Map<String, Object> validateAndParseInputs(final JsonNode schemaNode, final JsonNode valueNode) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JSONObject jsonObject = new JSONObject(mapper.writeValueAsString(valueNode));

        // validate against schema
        Schema schema = loadSchema(schemaNode);
        schema.validate(jsonObject);

        return mapper.convertValue(valueNode, new TypeReference<>(){});
    }

    private static Schema loadSchema(final JsonNode schemaNode) {
        JSONObject rawSchema = new JSONObject(new JSONTokener(schemaNode.toString()));
        return SchemaLoader.load(rawSchema);
    }

    private static ObjectMapper getMapper(final String data) {
        if (data.trim().startsWith("{")) {
            return JSON_MAPPER;
        }
        return YAML_MAPPER;
    }
}
