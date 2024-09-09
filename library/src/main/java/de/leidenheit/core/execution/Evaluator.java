package de.leidenheit.core.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import de.leidenheit.core.execution.resolving.ArazzoExpressionResolver;
import de.leidenheit.core.model.Criterion;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Evaluator {

    private final ArazzoExpressionResolver resolver;
    private final ObjectMapper mapper = new ObjectMapper();

    protected Evaluator(final ArazzoExpressionResolver resolver) {
        this.resolver = resolver;
    }

    public boolean evalCriterion(
            final Criterion criterion,
            final EvaluatorParams params) {
        if (criterion.getType() != null) {
            return switch (criterion.getType()) {
                case REGEX -> evaluateRegex(criterion, params);
                case JSONPATH -> evaluateJsonPath(criterion, params);
                case XPATH -> evaluateXPath(criterion, params);
                case SIMPLE -> evaluateSimpleCondition(criterion, params);
            };
        } else {
            return evaluateSimpleCondition(criterion, params);
        }
    }

    private String resolveContext(final String context, final EvaluatorParams params) {
        var resolved = resolver.resolveExpression(context, params);
        if (resolved instanceof String resolvedAsString) {
            return resolvedAsString;
        }
        throw new RuntimeException("Unexpected");
    }

    private boolean evaluateSimpleCondition(
            final Criterion criterion,
            final EvaluatorParams params) {
        // e.g. $statusCode == 200
        return evaluateLogicalExpression(criterion.getCondition(), params);
    }

    private boolean evaluateRegex(
            final Criterion criterion,
            final EvaluatorParams params) {
        String contextValue = resolveContext(criterion.getContext(), params);
        if (Objects.isNull(contextValue)) throw new RuntimeException("Unexpected");
        // e.g. $response.body.fieldHugo -> ^FieldHugoValue$
        return contextValue.matches(criterion.getCondition());
    }

    private boolean evaluateJsonPath(
            final Criterion criterion,
            final EvaluatorParams params) {
        // Resolve the context value (e.g., response body)
        String contextValue = resolveContext(criterion.getContext(), params);
        if (Objects.isNull(contextValue)) {
            throw new RuntimeException("Unexpected");
        }

        // Parse the contextValue into a JSON Node
        JsonNode jsonNode = null;
        try {
            jsonNode = mapper.readTree(contextValue);

            // Check if the criterion uses a JSON Pointer (starts with #/)
            if (criterion.getCondition().startsWith("#/")) {
                // Extract JSON pointer, operator and expected value
                Pattern pattern = Pattern.compile("#(?<ptr>/[^ ]+)\\s*(?<operator>==|!=|<=|>=|<|>)\\s*(?<expected>.+)");
                Matcher matcher = pattern.matcher(criterion.getCondition());

                if (matcher.find()) {
                    String ptr = matcher.group("ptr");         // JSON Pointer
                    String operator = matcher.group("operator");  // Operator
                    String expected = matcher.group("expected");  // Erwarteter Wert


                    // Use JSON Pointer to resolve the node
                    JsonNode nodeAtPointer = jsonNode.at(ptr);
                    if (Objects.isNull(nodeAtPointer)) throw new RuntimeException("Unexpected");

                    // Resolve expected if it is an expression
                    expected = resolver.resolveString(expected);

                    System.out.println("Pointer: " + ptr);
                    System.out.println("Operator: " + operator);
                    System.out.println("Expected: " + expected);

                    // Evaluate condition based on the extracted node (simple condition)
                    var resolvedCriterion = Criterion.builder()
                            .type(Criterion.CriterionType.SIMPLE)
                            .condition(String.format("%s %s %s", nodeAtPointer.asText(), operator, expected))
                            .context(criterion.getContext())
                            .build();
                    return evaluateSimpleCondition(resolvedCriterion, params);
                } else {
                    throw new RuntimeException("Unexpected");
                }
            } else {
                // Extract query, operator and expected value
                Pattern pattern = Pattern.compile("(?<query>[$][^ ]+)\\s*(?<operator>==|!=|<=|>=|<|>)\\s*(?<expected>.+)");
                Matcher matcher = pattern.matcher(criterion.getCondition());

                if (matcher.find()) {
                    String query = matcher.group("query");         // JSON Pointer
                    String operator = matcher.group("operator");  // Operator
                    String expected = matcher.group("expected");  // Erwarteter Wert

                    // Resolve expected if it is an expression
                    expected = resolver.resolveString(expected);

                    System.out.println("Query: " + query);
                    System.out.println("Operator: " + operator);
                    System.out.println("Expected: " + expected);

                    var jsonNodeValue = JsonPath.parse(jsonNode.toString()).read(query);
                    if (Objects.isNull(jsonNodeValue)) throw new RuntimeException("Unexpected");
                    // Evaluate condition based on the extracted node (simple condition)
                    var resolvedCriterion = Criterion.builder()
                            .type(Criterion.CriterionType.SIMPLE)
                            .condition(String.format("%s %s %s", jsonNodeValue, operator, expected))
                            .context(criterion.getContext())
                            .build();
                    return evaluateSimpleCondition(resolvedCriterion, params);
                } else {
                    throw new RuntimeException("Unexpected");
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean evaluateXPath(
            final Criterion criterion,
            final EvaluatorParams params) {
        String contextValue = resolveContext(criterion.getContext(), params);
        if (Objects.isNull(contextValue)) throw new RuntimeException("Unexpected");
        return evaluateXPathExpression(contextValue, criterion.getCondition());
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
        } else if (Objects.isNull(leftValue) && "null".equals(rightValue)) {
            return 0;
        } else {
            throw new IllegalArgumentException("Incomparable types: " + leftValue + " and " + rightValue);
        }
    }

    private boolean evaluateXPathExpression(final String contextValue, final String condition) {
        // TODO implementation
        throw new RuntimeException("not implemented yet");
    }

    @Data
    @Builder
    public static class EvaluatorParams {
        private int latestStatusCode;
        private String latestUrl;
        private String latestHttpMethod;
        private Response lastestResponse;
        private FilterableRequestSpecification latestRequest;
    }
}
