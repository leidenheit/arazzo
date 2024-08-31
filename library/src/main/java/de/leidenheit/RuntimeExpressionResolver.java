package de.leidenheit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class RuntimeExpressionResolver {

    private final Map<String, Object> inputs;
    private final ArrayNode sourceDescriptions;

    // TODO others

    public RuntimeExpressionResolver(
            final Map<String, Object> inputs,
            final ArrayNode sourceDescriptions) {
        this.inputs = inputs;
        this.sourceDescriptions = sourceDescriptions;
    }

    public Object resolve(final JsonNode node) {
        if (node.isTextual()) {
            return resolveString(node.asText());
        } else if (node.isObject()) {
            return resolveJsonObject(node);
        } else if (node.isArray()) {
            return resolveJsonArray(node);
        }
        return node;
    }

    private String resolveString(final String expression) {
        StringBuilder result = new StringBuilder();
        if (expression.contains("{$")) {
            int start = 0;
            while (start < expression.length()) {
                int openIndex = expression.indexOf("{$", start);
                if (openIndex == -1) {
                    result.append(expression.substring(start));
                    break;
                }
                result.append(expression.substring(start, openIndex));
                int closeIndex = expression.indexOf('}', openIndex);
                if (closeIndex == -1) {
                    throw new IllegalArgumentException("Unmatched '{$' in expression: " + expression);
                }
                String expr = expression.substring(openIndex + 1, closeIndex);
                Object resolved = resolveExpression(expr);
                if (Objects.nonNull(resolved)) {
                    result.append(resolved);
                }
                start = closeIndex + 1;
            }
        } else {
            result.append(resolveExpression(expression));
        }
        return result.toString();
    }

    private Object resolveExpression(final String expression) {
        if (expression.startsWith("$inputs.")) {
            return getNestedValue(inputs, expression.substring("$inputs.".length()));
        } else if (expression.startsWith("$sourceDescriptions.")) {
            return resolveSourceDescription(sourceDescriptions, expression.substring("$sourceDescriptions.".length()));
        } else if (expression.equals("$statusCode")) {
            // TODO not implemented
        } else if (expression.startsWith("$response.")) {
            // TODO not implemented
        } else if (expression.startsWith("$steps.")) {
            // TODO not implemented
        }
        // TODO others
        return expression; // Return unchanged if no resolution is found
    }

    private Object getNestedValue(
            final Map<String, Object> resolveMap,
            final String keyPath) {
        String[] keys = keyPath.split("\\.");
        Object current = resolveMap;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    private JsonNode getNestedValue(
            final JsonNode resolveNode,
            final String keyPath) {
        String[] keys = keyPath.split("\\.");
        JsonNode currentNode = resolveNode;
        for (String key : keys) {
            if (currentNode.has(key)) {
                currentNode = currentNode.get(key);
            } else {
                return null;
            }
        }
        return currentNode;
    }

    private JsonNode resolveSourceDescription(
            final ArrayNode sourceDescriptionsArray,
            final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length); // Alle weiteren Felder, z.B. ["url"] oder ["x-hugo"]

        for (JsonNode sourceNode : sourceDescriptionsArray) {
            if (sourceNode.has("name") && sourceNode.get("name").asText().equals(targetName)) {
                return getNestedValue(sourceNode, String.join(".", targetFields));
            }
        }
        return null;
    }


    private JsonNode resolveJsonObject(final JsonNode node) {
        ObjectNode resolvedNode = node.deepCopy();
        node.fields().forEachRemaining(entry -> {
            Object resolvedValue = resolve(entry.getValue());
            resolvedNode.set(entry.getKey(), resolvedValue instanceof JsonNode jsonNode
                    ? jsonNode
                    : new TextNode(resolvedValue.toString()));
        });
        return resolvedNode;
    }

    private JsonNode resolveJsonArray(final JsonNode arrayNode) {
        ArrayNode resolvedArray = arrayNode.deepCopy();
        for (int i = 0; i < arrayNode.size(); i++) {
            Object resolvedValue = resolve(arrayNode.get(i));
            resolvedArray.set(i, resolvedValue instanceof JsonNode jsonNode
                    ? jsonNode
                    : new TextNode(resolvedValue.toString()));
        }
        return resolvedArray;
    }
}
