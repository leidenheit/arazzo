package de.leidenheit.core.execution.resolving;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.Objects;

public class HttpContextExpressionResolver implements HttpExpressionResolver {

    @Override
    public Object resolveExpression(String expression, ResolverContext context) {
        if (Objects.isNull(context)) return expression;
        if (!(HttpResolverContext.class.isAssignableFrom(context.getClass()))) throw new RuntimeException("Unexpected");

        var httpContext = (HttpResolverContext) context;
        if (expression.equals("$statusCode")) {
            return String.valueOf(httpContext.getLatestStatusCode());
        } else if (expression.startsWith("$response.")) {
            if (expression.startsWith("$response.header")) {
                var header = expression.substring("$response.header.".length());
                return resolveHeader(header, httpContext.getLastestResponse().getHeaders());
            } else if (expression.startsWith("$response.body")) {
                var responseBody = resolveResponseBody(httpContext.getLastestResponse());
                if (responseBody.isBlank()) {
                    return null;
                }
                return responseBody;
            }
            throw new RuntimeException("Not supported");
        } else if (expression.startsWith("$request.")) {
            if (expression.startsWith("$request.header")) {
                var header = expression.substring("$request.header.".length());
                return resolveHeader(header, httpContext.getLatestRequest().getHeaders());
            } else if (expression.startsWith("$request.body")) {
                var requestBody = resolveRequestBody(httpContext.getLatestRequest());
                if (requestBody.isBlank()) {
                    return null;
                }
                return requestBody;
            } else if (expression.startsWith("$request.path")) {
                var pathParam = expression.substring("$request.path.".length());
                return resolvePathParam(pathParam, httpContext.getLatestRequest().getPathParams());
            }
            throw new RuntimeException("Not supported");
        } else if (expression.startsWith("$url")) {
            return httpContext.getLatestUrl();
        } else if (expression.startsWith("$method")) {
            return httpContext.getLatestHttpMethod();
        } else if (expression.startsWith("$message")) {
            return httpContext.getLatestMessage();
        }

        return expression; // Return unchanged if no resolution is found
    }

    @Override
    public String resolveHeader(String headerName, Headers headers) {
        var header = headers.getValue(headerName);
        if (Objects.isNull(header)) throw new RuntimeException("Unexpected");
        return header;
    }

    @Override
    public String resolvePathParam(String paramName, Map<String, String> pathParams) {
        var pathParam = pathParams.get(paramName);
        if (Objects.isNull(pathParam)) throw new RuntimeException("Unexpected");
        return pathParam;
    }

    @Override
    public String resolveRequestBody(RequestSpecification requestSpecification) {
        String body = ((FilterableRequestSpecification) requestSpecification).getBody();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body;
    }

    @Override
    public String resolveResponseBody(Response response) {
        var body = response.body();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body.asString();
    }
}
