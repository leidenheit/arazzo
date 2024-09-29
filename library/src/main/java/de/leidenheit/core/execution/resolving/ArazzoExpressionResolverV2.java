package de.leidenheit.core.execution.resolving;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.*;

public class ArazzoExpressionResolverV2 extends HttpContextExpressionResolver {

    private static ArazzoExpressionResolverV2 instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, Object> inputs = new HashMap<>();
    private final Map<String, Object> outputs = new HashMap<>();
    private JsonNode componentsNode;
    private final ArrayNode steps = mapper.createArrayNode();
    private final ArrayNode workflows = mapper.createArrayNode();
    private final ArrayNode sourceDescriptions = mapper.createArrayNode();

    // hold resolved values to easily reference them later
    public final Map<String, Object> resolvedMap = new LinkedHashMap<>();

    private ArazzoExpressionResolverV2(final JsonNode arazzoRootNode, final Map<String, Object> inputs) {
        this.inputs = inputs;

        var componentsNode = arazzoRootNode.get("components");
        this.componentsNode = componentsNode;

        var sourceDescriptionsNode = arazzoRootNode.get("sourceDescriptions");

        if (sourceDescriptionsNode instanceof ArrayNode arrayNodeSourceDescriptions) {
            this.sourceDescriptions.addAll(arrayNodeSourceDescriptions);
        }

        var workflowsNode = arazzoRootNode.get("workflows");
        if (workflowsNode instanceof ArrayNode arrayNodeWorkflows) {
            this.workflows.addAll(arrayNodeWorkflows);
            arrayNodeWorkflows.forEach(wfNode -> {
                var stepsNode = wfNode.get("steps");
                if (stepsNode instanceof ArrayNode arrayNodeSteps)
                this.steps.addAll(arrayNodeSteps);
            });
        }
    }

    public static synchronized ArazzoExpressionResolverV2 getInstance(final JsonNode arazzoRootNode, final Map<String, Object> inputs) {
        if (Objects.isNull(instance)) {
            instance = new ArazzoExpressionResolverV2(arazzoRootNode, inputs);
        }
        return instance;
    }

    @Override
    public Object resolveExpression(final String expression, final ResolverContext context) {
        // re-use already resolved expressions
        Object resolved = resolvedMap.get(expression);

        if (Objects.isNull(resolved)) {
            if (expression.startsWith("$inputs.")) {
                resolved = getNestedValue(inputs, expression.substring("$inputs.".length()));
            } else if (expression.startsWith("$outputs.")) {
                resolved = getNestedValue(outputs, expression.substring("$outputs.".length()));
            } else if (expression.startsWith("$sourceDescriptions.")) {
                resolved = resolveSourceDescription(sourceDescriptions, expression.substring("$sourceDescriptions.".length()));
            } else if (expression.startsWith("$workflows.")) {
                resolved = resolveWorkflows(workflows, expression.substring("$workflows.".length()));
            } else if (expression.startsWith("$steps.")) {
                resolved = resolveSteps(steps, expression.substring("$steps.".length()));
            } else if (expression.startsWith("$components.") || expression.startsWith("#/components")) {
                // TODO
                throw new RuntimeException("Expected to be handled by ArazzoComponentRefResolver but was not: %s".formatted(expression));
            } else {
                return super.resolveExpression(expression, context);
            }

            if (Objects.nonNull(resolved) && !expression.equalsIgnoreCase(resolved.toString())) {
                // add resolved expression to reference map
                resolvedMap.put(expression, resolved);
            }
        }
        return resolved;
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
                Object resolved = resolveExpression(expr, null);
                if (Objects.nonNull(resolved) && resolved instanceof TextNode textNode) {
                    result.append(textNode.asText());
                } else {
                    throw new RuntimeException("Unexpected");
                }
                start = closeIndex + 1;
            }
        } else {
            result.append(resolveExpression(expression, null));
        }
        return result.toString();
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

    private JsonNode resolveWorkflows(
            ArrayNode stepsArray,
            String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : stepsArray) {
            if (sourceNode.has("workflowId") && sourceNode.get("workflowId").asText().equals(targetName)) {
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

    protected JsonNode getNestedValue(
            JsonNode resolveNode,
            String keyPath) {
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

    protected Object getNestedValue(
            Map<String, Object> resolveMap,
            String keyPath) {
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

    public JsonNode resolveComponent(final String reference) {
        if (reference.startsWith("#/components")) {
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
