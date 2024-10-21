package de.leidenheit.infrastructure.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import de.leidenheit.infrastructure.utils.ResolverUtils;

import java.util.Arrays;
import java.util.Objects;

public class ArazzoComponentsReferenceResolver {

    private static ArazzoComponentsReferenceResolver instance;

    private final JsonNode componentsNode;

    private ArazzoComponentsReferenceResolver(final JsonNode componentsNode) {
        this.componentsNode = componentsNode;
    }

    public static synchronized ArazzoComponentsReferenceResolver getInstance(final JsonNode componentsNode) {
        if (Objects.isNull(instance)) {
            instance = new ArazzoComponentsReferenceResolver(componentsNode);
        }
        return instance;
    }

    public JsonNode resolveComponent(final String reference) {
        if (reference.startsWith("#/components")) {
            return resolveJsonPointer(reference);
        } else if (reference.startsWith("$components.")) {
            return resolveRuntimeExpression(reference);
        }
        // TODO replace with exception
        throw new RuntimeException("Unexpected");
    }

    private JsonNode resolveJsonPointer(final String jsonPointer) {
        // convert JSON-Pointer to the right format
        String pointer = jsonPointer.replace("#/components", "");
        JsonNode result = componentsNode.at(pointer);
        if (result.isMissingNode()) {
            // TODO replace with exception
            throw new RuntimeException("Unexpected");
        }
        return result;
    }

    private JsonNode resolveRuntimeExpression(final String runtimeExpression) {
        String[] keys = runtimeExpression.split("\\.");
        if (keys.length < 2 || !keys[0].equals("$components")) {
            // TODO replace with exception
            throw new RuntimeException("Unexpected");
        }
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);
        return ResolverUtils.getNestedValue(componentsNode, String.join(".", targetFields));
    }
}
