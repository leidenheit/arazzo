package de.leidenheit.core.execution.resolving;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;

public class ArazzoComponentRefResolver extends ContextExpressionResolver {

    private final JsonNode componentsNode;

    public ArazzoComponentRefResolver(final JsonNode componentsNode) {
        this.componentsNode = componentsNode;
    }

    public JsonNode resolveComponent(final String reference) {
        if (reference.startsWith("#/")) {
            return resolveJsonPointer(reference);
        } else if (reference.startsWith("$components.")) {
            return resolveRuntimeExpression(reference);
        }
        throw new RuntimeException("Unexpected");
    }

    private JsonNode resolveJsonPointer(final String jsonPointer) {
        // Convert JSON-Pointer to the right format
        String pointer = jsonPointer.replace("#/components", "");
        JsonNode result = componentsNode.at(pointer);
        if (result.isMissingNode()) {
            throw new RuntimeException("Unexpected");
        }
        return result;
    }

    private JsonNode resolveRuntimeExpression(final String runtimeExpression) {
        String[] keys = runtimeExpression.split("\\.");
        if (keys.length < 2 || !keys[0].equals("$components")) {
            throw new RuntimeException("Unexpected");
        }
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);
        return getNestedValue(componentsNode, String.join(".", targetFields));
    }
}
