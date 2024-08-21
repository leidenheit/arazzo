package de.leidenheit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class ArazzoReferenceDeserializer extends JsonDeserializer<Object> {

    private final ArazzoReferenceResolver resolver;
    public ArazzoReferenceDeserializer(final ArazzoReferenceResolver arazzoReferenceResolver) {
        this.resolver = arazzoReferenceResolver;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isTextual()) {
            String value = node.asText();
            return resolver.resolve(value);
        } else if (node.isObject()) {
            return deserializeObject(node);
        } else if (node.isArray()) {
            return deserializeArray(node);
        }
        return node;
    }

    private Map<String, Object> deserializeObject(JsonNode node) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.convertValue(node, Map.class);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String && ((String) entry.getValue()).startsWith("$")) {
                entry.setValue(resolver.resolve((String) entry.getValue()));
            }
        }
        return map;
    }

    private Object[] deserializeArray(JsonNode node) throws IOException {
        Object[] array = new Object[node.size()];
        int i = 0;
        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); i++) {
            JsonNode elem = it.next();
            if (elem.isTextual() && elem.asText().startsWith("$")) {
                array[i] = resolver.resolve(elem.asText());
            } else if (elem.isObject()) {
                array[i] = deserializeObject(elem);
            } else if (elem.isArray()) {
                array[i] = deserializeArray(elem);
            } else {
                array[i] = elem;
            }
        }
        return array;
    }
}
