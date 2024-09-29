package de.leidenheit.core.execution.resolving;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

public interface HttpExpressionResolver extends ExpressionResolver {

    String resolveHeader(final String headerName, final Headers headers);
    String resolvePathParam(final String paramName, final Map<String, String> pathParams);
    String resolveRequestBody(final RequestSpecification requestSpecification);
    String resolveResponseBody(final Response response);
}
