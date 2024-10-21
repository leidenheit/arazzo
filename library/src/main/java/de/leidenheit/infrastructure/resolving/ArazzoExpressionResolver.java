package de.leidenheit.infrastructure.resolving;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.utils.ResolverUtils;

import java.util.*;

public class ArazzoExpressionResolver extends HttpContextExpressionResolver {

    private static ArazzoExpressionResolver instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs = new HashMap<>();
    private final ArrayNode steps = mapper.createArrayNode();
    private final ArrayNode workflows = mapper.createArrayNode();
    private final ArrayNode sourceDescriptions = mapper.createArrayNode();

    // hold resolved values to easily reference them later
    private final Map<String, Object> resolvedMap = new LinkedHashMap<>();

    public static synchronized ArazzoExpressionResolver getInstance(final ArazzoSpecification arazzo,
                                                                    final Map<String, Object> inputs) {
        if (Objects.isNull(instance)) {
            instance = new ArazzoExpressionResolver(arazzo, inputs);
        }
        return instance;
    }

    private ArazzoExpressionResolver(final ArazzoSpecification arazzo, final Map<String, Object> inputs) {
        this.inputs = inputs;
        this.sourceDescriptions.addAll(Objects.requireNonNull(
                mapper.convertValue(arazzo.getSourceDescriptions(), ArrayNode.class)));
        this.workflows.addAll(Objects.requireNonNull(
                mapper.convertValue(arazzo.getWorkflows(), ArrayNode.class)));
        arazzo.getWorkflows().forEach(workflow ->
                this.steps.addAll(Objects.requireNonNull(
                        mapper.convertValue(workflow.getSteps(), ArrayNode.class))));
    }

    @Override
    public Object resolveExpression(final String expression, final ResolverContext context) {
        // re-use already resolved expressions
        Object resolved = resolvedMap.get(expression);

        if (Objects.isNull(resolved)) {
            if (expression.startsWith("$inputs.")) {
                resolved = ResolverUtils.getNestedValue(inputs, expression.substring("$inputs.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$outputs.")) {
                resolved = ResolverUtils.getNestedValue(outputs, expression.substring("$outputs.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$sourceDescriptions.")) {
                resolved = resolveSourceDescription(sourceDescriptions, expression.substring("$sourceDescriptions.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$workflows.")) {
                resolved = resolveWorkflows(workflows, expression.substring("$workflows.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$steps.")) {
                resolved = resolveSteps(steps, expression.substring("$steps.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$components.") || expression.startsWith("#/components")) {
                // TODO replace with exception
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
                if (Objects.nonNull(resolved)) {
                    if (resolved instanceof TextNode textNode) {
                        result.append(textNode.asText());
                    } else {
                        result.append(resolved);
                    }
                } else {
                    // TODO replace with exception
                    throw new RuntimeException("Unexpected");
                }
                start = closeIndex + 1;
            }
        } else {
            result.append(resolveExpression(expression, null));
        }
        return result.toString();
    }

    public void addResolved(final String key, final Object resolved) {
        this.resolvedMap.put(key, resolved);
    }

    private JsonNode resolveSourceDescription(final ArrayNode sourceDescriptionsArray, final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : sourceDescriptionsArray) {
            if (sourceNode.has("name") && sourceNode.get("name").asText().equals(targetName)) {
                return ResolverUtils.getNestedValue(sourceNode, String.join(".", targetFields));
            }
        }
        return null;
    }


    private JsonNode resolveSteps(final ArrayNode stepsArray, final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : stepsArray) {
            if (sourceNode.has("stepId") && sourceNode.get("stepId").asText().equals(targetName)) {
                var resolved = ResolverUtils.getNestedValue(sourceNode, String.join(".", targetFields));
                if (Objects.nonNull(resolved) && resolved.isTextual()) {
                    resolved = new TextNode(resolveString(resolved.asText()));
                    return resolved;
                }
                // TODO replace with exception
                throw new RuntimeException("Unexpected");
            }
        }
        return null;
    }

    private JsonNode resolveWorkflows(final ArrayNode stepsArray, final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : stepsArray) {
            if (sourceNode.has("workflowId") && sourceNode.get("workflowId").asText().equals(targetName)) {
                var resolved = ResolverUtils.getNestedValue(sourceNode, String.join(".", targetFields));
                if (Objects.nonNull(resolved) && resolved.isTextual()) {
                    resolved = new TextNode(resolveString(resolved.asText()));
                    return resolved;
                }
                // TODO replace with exception
                throw new RuntimeException("Unexpected");
            }
        }
        return null;
    }

    private void resolveJsonObject(final ObjectNode node) {
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

    private void resolveJsonArray(final ArrayNode arrayNode) {
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
