package de.leidenheit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RuntimeExpressionResolver {

    private final Map<String, Object> inputs;
    private final ArazzoSpecification arazzoSpecification;
    private final ObjectMapper mapper = new ObjectMapper();

    private ArrayNode sourceDescriptions;
    private ArrayNode steps;
    // TODO others

    // hold resolved values to easily reference them later
    public final Map<String, Object> resolvedMap = new LinkedHashMap<>();

    public RuntimeExpressionResolver(final ArazzoSpecification arazzoSpecification, final Map<String, Object> inputs) {
        this.inputs = inputs;
        this.arazzoSpecification = arazzoSpecification;

        this.sourceDescriptions = this.mapper.convertValue(
                arazzoSpecification.getSourceDescriptions(), ArrayNode.class);

        for (ArazzoSpecification.Workflow wf : arazzoSpecification.getWorkflows()) {
            var stepsX = this.mapper.convertValue(wf.getSteps(), ArrayNode.class);
            if (Objects.isNull(this.steps)) {
                this.steps = stepsX;
            } else {
                this.steps.addAll(stepsX);
            }
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

    private String resolveHeader(final String headerName, final Headers headers) {
        var header = headers.getValue(headerName);
        if (Objects.isNull(header)) throw new RuntimeException("Unexpected");
        return header;
    }

    private String resolveResponseBody(final Response response) {
        var body = response.body();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body.asString();
    }

    private String resolveRequestBody(final RequestSpecification requestSpecification) {
        String body = ((FilterableRequestSpecification) requestSpecification).getBody();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body;
    }

    public Object resolveExpression(final String expression, Evaluator.EvaluatorParams params) {
        if (expression.equals("$statusCode")) {
            return String.valueOf(params.getLatestStatusCode());
        } else if (expression.startsWith("$response.")) {
            if (expression.startsWith("$response.header")) {
                var header = expression.substring("$response.header.".length());
                return resolveHeader(header, params.getLastestResponse().getHeaders());
            } else if (expression.startsWith("$response.body")) {
                var responseBody = resolveResponseBody(params.getLastestResponse());
                if (responseBody.isBlank()) {
                    return null;
                }
                return responseBody;
            }
            throw new RuntimeException("Not supported");
        } else if (expression.startsWith("$request.")) {
            if (expression.startsWith("$request.header")) {
                var header = expression.substring("$request.header.".length());
                return resolveHeader(header, params.getLatestRequest().getHeaders());
            } else if (expression.startsWith("$request.body")) {
                var requestBody = resolveRequestBody(params.getLatestRequest());
                if (requestBody.isBlank()) {
                    return null;
                }
                return requestBody;
            }
            throw new RuntimeException("Not supported");
        } else if (expression.startsWith("$url")) {
            return params.getLatestUrl();
        } else if (expression.startsWith("$method")) {
            return params.getLatestHttpMethod();
        }
        // TODO others
        return expression; // Return unchanged if no resolution is found
    }

    private Object getNestedValue(
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

    private JsonNode getNestedValue(
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

    private JsonNode resolveSourceDescription(
            ArrayNode sourceDescriptionsArray,
            String keyPath) {
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
