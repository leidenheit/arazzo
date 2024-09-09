package de.leidenheit.core.execution.resolving;

import com.fasterxml.jackson.databind.JsonNode;
import de.leidenheit.core.execution.Evaluator;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.Objects;

public class ContextExpressionResolver {

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
            } else if (expression.startsWith("$request.path")) {
                var pathParam = expression.substring("$request.path.".length());
                return resolvePathParam(pathParam, params.getLatestRequest().getPathParams());
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


    protected String resolveHeader(final String headerName, final Headers headers) {
        var header = headers.getValue(headerName);
        if (Objects.isNull(header)) throw new RuntimeException("Unexpected");
        return header;
    }

    protected String resolvePathParam(final String paramName, final Map<String, String> pathParams) {
        var pathParam = pathParams.get(paramName);
        if (Objects.isNull(pathParam)) throw new RuntimeException("Unexpected");
        return pathParam;
    }

    protected String resolveResponseBody(final Response response) {
        var body = response.body();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body.asString();
    }

    protected String resolveRequestBody(final RequestSpecification requestSpecification) {
        String body = ((FilterableRequestSpecification) requestSpecification).getBody();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body;
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
}
