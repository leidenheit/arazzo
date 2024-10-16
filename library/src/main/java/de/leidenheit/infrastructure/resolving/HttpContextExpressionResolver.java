package de.leidenheit.infrastructure.resolving;

import com.jayway.jsonpath.JsonPath;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.Objects;

public class HttpContextExpressionResolver implements HttpExpressionResolver {

    @Override
    public Object resolveExpression(final String expression, final ResolverContext context) {
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
                var responseBody = resolveResponseBodyPayload(httpContext.getLastestResponse());
                if (responseBody.isBlank()) {
                    return null;
                }
                if (expression.contains("$response.body.")) {
                    var subPath = expression.substring("$response.body.".length());
                    try {
                        var r = JsonPath.read(responseBody, "$.%s".formatted(subPath));
                        return r;
                    } catch (Exception e) {
                        throw new RuntimeException("Invalid JSON path: '%s'".formatted(expression));
                    }
                }
                return responseBody;
            }
            throw new RuntimeException("Not supported");
        } else if (expression.startsWith("$request.")) {
            if (expression.startsWith("$request.header")) {
                var header = expression.substring("$request.header.".length());
                return resolveHeader(header, httpContext.getLatestRequest().getHeaders());
            } else if (expression.startsWith("$request.body")) {
                var requestBody = resolveRequestBodyPayload(httpContext.getLatestRequest());
                if (requestBody.isBlank()) {
                    return null;
                }
                // TODO consider handle request bodies in a deeply manner
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
    public String resolveHeader(final String headerName, final Headers headers) {
        var header = headers.getValue(headerName);
        if (Objects.isNull(header)) throw new RuntimeException("Unexpected");
        return header;
    }

    @Override
    public String resolvePathParam(final String paramName, final Map<String, String> pathParams) {
        var pathParam = pathParams.get(paramName);
        if (Objects.isNull(pathParam)) throw new RuntimeException("Unexpected");
        return pathParam;
    }

    @Override
    public String resolveRequestBodyPayload(final RequestSpecification requestSpecification) {
        String body = ((FilterableRequestSpecification) requestSpecification).getBody();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body;
    }

    @Override
    public String resolveResponseBodyPayload(final Response response) {
        var body = response.body();
        if (Objects.isNull(body)) throw new RuntimeException("Unexpected");
        return body.asString();
    }
}
