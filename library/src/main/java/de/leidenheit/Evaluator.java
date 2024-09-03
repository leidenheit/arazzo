package de.leidenheit;

import com.jayway.jsonpath.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;

public class Evaluator {

    private final RuntimeExpressionResolver resolver;

    protected Evaluator(final RuntimeExpressionResolver resolver) {
        this.resolver = resolver;
    }

    public boolean evalCriterion(
            final ArazzoSpecification.Workflow.Step.Criterion criterion,
            final EvaluatorParams params) {
        if (criterion.getType() != null) {
            return switch (criterion.getType()) {
                case REGEX -> evaluateRegex(criterion, params);
                case JSON_PATH -> evaluateJsonPath(criterion, params);
                case XPATH -> evaluateXPath(criterion, params);
                case SIMPLE -> evaluateSimpleCondition(criterion, params);
            };
        } else {
            return evaluateSimpleCondition(criterion, params);
        }
    }

    private boolean evaluateSimpleCondition(
            final ArazzoSpecification.Workflow.Step.Criterion criterion,
            final EvaluatorParams params) {
        // e.g. $statusCode == 200
        return evaluateLogicalExpression(criterion.getCondition(), params);
    }

    private boolean evaluateRegex(
            final ArazzoSpecification.Workflow.Step.Criterion criterion,
            final EvaluatorParams params) {
        String contextValue = resolveContext(criterion.getContext(), params);
        if (Objects.isNull(contextValue)) throw new RuntimeException("Unexpected");
        return contextValue.matches(criterion.getCondition());
    }

    private boolean evaluateJsonPath(
            final ArazzoSpecification.Workflow.Step.Criterion criterion,
            final EvaluatorParams params) {
        String contextValue = resolveContext(criterion.getContext(), params);
        if (Objects.isNull(contextValue)) throw new RuntimeException("Unexpected");
        // e.g. $response.body.pets[?(@.age > 4)]
        return JsonPath.parse(contextValue).read(criterion.getCondition(), Boolean.class);
    }

    private boolean evaluateXPath(
            final ArazzoSpecification.Workflow.Step.Criterion criterion,
            final EvaluatorParams params) {
        String contextValue = resolveContext(criterion.getContext(), params);
        if (Objects.isNull(contextValue)) throw new RuntimeException("Unexpected");
        return evaluateXPathExpression(contextValue, criterion.getCondition());
    }

    private String resolveContext(final String context, final EvaluatorParams params) {
        if (context.equals("$statusCode")) {
            return String.valueOf(params.latestStatusCode);
        } else if (context.startsWith("$response.")) {
            // e.g. $response.body
            return extractResponseBody();
        } else if (context.startsWith("$inputs.")) {
            return resolver.resolveString(context);
        } else if (context.startsWith("$sourceDescriptions.")) {
            return resolver.resolveString(context);
        }
        // TODO others
        return null;
    }

    private boolean evaluateLogicalExpression(final String condition, final EvaluatorParams params) {
        // split into left, right and operator
        String[] parts = condition.split("==|!=|<=|>=|<|>");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid simple condition format: " + condition);
        }

        String leftPart = parts[0].trim();
        String rightPart = parts[1].trim();

        // resolved left and right, e.g. $statusCode
        Object leftValue = resolver.resolveExpression(leftPart, params);
        Object rightValue = resolver.resolveExpression(rightPart, params);

        // determine operator
        if (condition.contains("==")) {
            return compareValues(leftValue, rightValue) == 0;
        } else if (condition.contains("!=")) {
            return compareValues(leftValue, rightValue) != 0;
        } else if (condition.contains("<=")) {
            return compareValues(leftValue, rightValue) <= 0;
        } else if (condition.contains(">=")) {
            return compareValues(leftValue, rightValue) >= 0;
        } else if (condition.contains("<")) {
            return compareValues(leftValue, rightValue) < 0;
        } else if (condition.contains(">")) {
            return compareValues(leftValue, rightValue) > 0;
        } else {
            throw new UnsupportedOperationException("Unsupported operator in condition: " + condition);
        }
    }

    private int compareValues(final Object leftValue, final Object rightValue) {
        if (leftValue instanceof Number leftNumberValue && rightValue instanceof Number rightNumberValue) {
            return Double.compare(leftNumberValue.doubleValue(), rightNumberValue.doubleValue());
        } else if (leftValue instanceof String leftStringValue && rightValue instanceof String rightStringValue) {
            return (leftStringValue).compareToIgnoreCase(rightStringValue);
        } else {
            throw new IllegalArgumentException("Incomparable types: " + leftValue + " and " + rightValue);
        }
    }

    private boolean evaluateXPathExpression(final String contextValue, final String condition) {
        // TODO implementation
        return false;
    }

    private String extractResponseBody() {
        // TODO implementation
        return null;
    }

    @Data
    @Builder
    public static class EvaluatorParams {
        private int latestStatusCode;
        private String latestUrl;
        private String latestHttpMethod;
        private Response lastestResponse;
        private RequestSpecification latestRequestSpecification;
    }
}
