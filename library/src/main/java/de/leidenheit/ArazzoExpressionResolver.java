package de.leidenheit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ArazzoExpressionResolver extends ContextExpressionResolver {

    private final Map<String, Object> inputs;
    private final ArazzoSpecification arazzoSpecification;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ArrayNode sourceDescriptions = mapper.createArrayNode();
    private final ArrayNode steps = mapper.createArrayNode();
    // TODO others

    // hold resolved values to easily reference them later
    public final Map<String, Object> resolvedMap = new LinkedHashMap<>();

    public ArazzoExpressionResolver(final ArazzoSpecification arazzoSpecification, final Map<String, Object> inputs) {
        this.inputs = inputs;
        this.arazzoSpecification = arazzoSpecification;

        this.sourceDescriptions.addAll(this.mapper.convertValue(arazzoSpecification.getSourceDescriptions(), ArrayNode.class));

        for (ArazzoSpecification.Workflow wf : arazzoSpecification.getWorkflows()) {
            this.steps.addAll(this.mapper.convertValue(wf.getSteps(), ArrayNode.class));
        }
    }

    public ArazzoSpecification resolve() {
        var arazzoSpecAsJsonNode =  this.mapper.convertValue(this.arazzoSpecification, JsonNode.class);
        resolveJsonObject((ObjectNode) arazzoSpecAsJsonNode);
        return this.mapper.convertValue(arazzoSpecAsJsonNode, ArazzoSpecification.class);
    }

    public String resolveString(final String expression) {
        StringBuilder result = new StringBuilder();
        if (expression.contains("{$")) {
            int start = 0;
            while (start < expression.length()) {
                int openIndex = expression.indexOf("{$", start);
                if (openIndex == -1) {
                    result.append(expression.substring(start));
                    break;
                }
                result.append(expression, start, openIndex);
                int closeIndex = expression.indexOf('}', openIndex);
                if (closeIndex == -1) {
                    throw new IllegalArgumentException("Unmatched '{$' in expression: " + expression);
                }
                String expr = expression.substring(openIndex + 1, closeIndex);
                Object resolved = resolveExpression(expr);
                if (Objects.nonNull(resolved) && resolved instanceof TextNode textNode) {
                    result.append(textNode.asText());
                } else {
                    throw new RuntimeException("Unexpected");
                }
                start = closeIndex + 1;
            }
        } else {
            result.append(resolveExpression(expression));
        }
        return result.toString();
    }

    public Object resolveExpression(final String expression) {
        // re-use already resolved expressions
        Object resolved = resolvedMap.get(expression);
        if (Objects.isNull(resolved)) {
            if (expression.startsWith("$inputs.")) {
                resolved = getNestedValue(inputs, expression.substring("$inputs.".length()));
            } else if (expression.startsWith("$sourceDescriptions.")) {
                resolved = resolveSourceDescription(sourceDescriptions, expression.substring("$sourceDescriptions.".length()));
            } else if (expression.startsWith("$steps.")) {
                resolved = resolveSteps(steps, expression.substring("$steps.".length()));
            } else {
                // Return unchanged if no resolution is found
                return expression;
            }
            // TODO others

            // add resolved expression to reference map
            resolvedMap.put(expression, resolved);
        }
        return resolved;
    }

    private JsonNode resolveSourceDescription(
            ArrayNode sourceDescriptionsArray,
            String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : sourceDescriptionsArray) {
            if (sourceNode.has("name") && sourceNode.get("name").asText().equals(targetName)) {
                return getNestedValue(sourceNode, String.join(".", targetFields));
            }
        }
        return null;
    }

    private JsonNode resolveSteps(
            ArrayNode stepsArray,
            String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : stepsArray) {
            if (sourceNode.has("stepId") && sourceNode.get("stepId").asText().equals(targetName)) {
                var resolved = getNestedValue(sourceNode, String.join(".", targetFields));
                if (Objects.nonNull(resolved) && resolved.isTextual()) {
                    resolved = new TextNode(resolveString(resolved.asText()));
                    return resolved;
                }
                throw new RuntimeException("Unexpected");
            }
        }
        return null;
    }

    private void resolveJsonObject(ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                var resolved = resolveString(value.asText());
                node.put(entry.getKey(), resolved);
            } else if (value.isObject()) {
                resolveJsonObject((ObjectNode) value);
            } else if (value.isArray()) {
                resolveJsonArray((ArrayNode) value);
            }
        });
    }

    private void resolveJsonArray(ArrayNode arrayNode) {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode value = arrayNode.get(i);
            if (value.isTextual()) {
                arrayNode.set(i, new TextNode(resolveString(value.asText())));
            } else if (value.isObject()) {
                resolveJsonObject((ObjectNode) value);
            } else if (value.isArray()) {
                resolveJsonArray((ArrayNode) value);
            }
        }
    }
}
