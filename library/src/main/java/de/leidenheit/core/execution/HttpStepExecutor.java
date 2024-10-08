package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.execution.context.RestAssuredContext;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Parameter;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Step;
import de.leidenheit.infrastructure.evaluation.CriterionEvaluator;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;
import de.leidenheit.infrastructure.utils.JsonPointerUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.swagger.v3.oas.models.PathItem;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class HttpStepExecutor implements StepExecutor {

    @Override
    public void executeStep(final ArazzoSpecification arazzo,
                            final Step step,
                            final ArazzoExpressionResolver resolver) {
        RestAssuredContext restAssuredContext = RestAssuredContext.builder().build();
        CriterionEvaluator criterionEvaluator = new CriterionEvaluator(resolver);

        SourceDescription sourceDescription = null;
        Map.Entry<String, PathItem> pathOperationEntry = null;
        if (Objects.nonNull(step.getOperationId())) {
            sourceDescription = findRelevantSourceDescriptionByOperationId(arazzo, step.getOperationId());
            pathOperationEntry = findOperationByOperationId(sourceDescription, step.getOperationId());
        } else if (Objects.nonNull(step.getOperationPath())) {
            sourceDescription = findRelevantSourceDescriptionByOperationPath(arazzo, step.getOperationPath());
            pathOperationEntry = findOperationByOperationPath(sourceDescription, step.getOperationPath());
        } else {
            throw new RuntimeException("Unexpected");
        }

        Objects.requireNonNull(sourceDescription);
        Objects.requireNonNull(pathOperationEntry);

        var requestSpecification = buildRestAssuredRequest(sourceDescription, step, restAssuredContext, resolver);
        var response = makeRequest(requestSpecification, pathOperationEntry);

        handleResponseAndOutputs(step, resolver, criterionEvaluator, response, restAssuredContext);
    }

    private SourceDescription findRelevantSourceDescriptionByOperationId(final ArazzoSpecification arazzo, final String operationId) {
        var sourceDescription = arazzo.getSourceDescriptions().get(0);
        if (arazzo.getSourceDescriptions().size() > 1) {
            sourceDescription = arazzo.getSourceDescriptions().stream()
                    .filter(s -> operationId.contains(s.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Unexpected"));
        }
        return sourceDescription;
    }

    private SourceDescription findRelevantSourceDescriptionByOperationPath(final ArazzoSpecification arazzo, final String operationPath) {
        var sourceDescription = arazzo.getSourceDescriptions().get(0);
        if (arazzo.getSourceDescriptions().size() > 1) {
            sourceDescription = arazzo.getSourceDescriptions().stream()
                    .filter(s -> operationPath.contains(s.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Unexpected"));
        }
        return sourceDescription;
    }

    private Map.Entry<String, PathItem> findOperationByOperationId(final SourceDescription sourceDescription, final String operationId) {
        return sourceDescription.getReferencedOpenAPI().getPaths().entrySet().stream()
                .filter(entry ->
                        entry.getValue().readOperations().stream()
                                .anyMatch(o -> operationId.contains(o.getOperationId())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No operation found for id " + operationId));
    }

    private Map.Entry<String, PathItem> findOperationByOperationPath(final SourceDescription sourceDescription,
                                                                     final String operationPath) {
        var unescapedOperationPath = JsonPointerUtils.unescapeJsonPointer(operationPath);
        return sourceDescription.getReferencedOpenAPI().getPaths().entrySet().stream()
                .filter(entry ->
                        entry.getValue().readOperations().stream()
                                .anyMatch(o -> unescapedOperationPath.contains(entry.getKey())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No operation found for path " + operationPath));
    }

    private RequestSpecification buildRestAssuredRequest(final SourceDescription sourceDescription,
                                                         final Step step,
                                                         final RestAssuredContext restAssuredContext,
                                                         final ArazzoExpressionResolver resolver) {
        String serverUrl = findServerUrl(sourceDescription);

        Map<String, Object> pathParameterMap = Collections.emptyMap();
        if (Objects.nonNull(step.getParameters())) {
            pathParameterMap = step.getParameters().stream()
                    .collect(Collectors.toMap(
                            Parameter::getName,
                            parameter -> resolver.resolveExpression(parameter.getValue().toString(), null)
                    ));
        }

        return RestAssured
                .given()
                .filter((requestSpec, responseSpec, ctx) -> {
                    restAssuredContext.setLatestUrl(requestSpec.getBaseUri());
                    restAssuredContext.setLatestHttpMethod(requestSpec.getMethod());
                    restAssuredContext.setLatestRequest(requestSpec);

                    return ctx.next(requestSpec, responseSpec);
                })
                .baseUri(serverUrl)
                .pathParams(pathParameterMap);
    }

    private Response makeRequest(final RequestSpecification requestSpecification, final Map.Entry<String, PathItem> pathOperationEntry) {
        var pathAsString = pathOperationEntry.getKey();
        var pathItem = pathOperationEntry.getValue();

        if (Objects.nonNull(pathItem.getGet())) {
            return requestSpecification.get(pathAsString);

        } else if (Objects.nonNull(pathItem.getPost())) {
            return requestSpecification.post(pathAsString);

        } else if (Objects.nonNull(pathItem.getPut())) {
            return requestSpecification.put(pathAsString);

        } else if (Objects.nonNull(pathItem.getDelete())) {
            return requestSpecification.delete(pathAsString);

        } else if (Objects.nonNull(pathItem.getOptions())) {
            return requestSpecification.options(pathAsString);

        } else if (Objects.nonNull(pathItem.getPatch())) {
            return requestSpecification.patch(pathAsString);

        } else if (Objects.nonNull(pathItem.getHead())) {
            return requestSpecification.head(pathAsString);

        } else {
            throw new RuntimeException("Unsupported by RestAssured");
        }
    }

    private void handleResponseAndOutputs(final Step step,
                                          final ArazzoExpressionResolver resolver,
                                          final CriterionEvaluator criterionEvaluator,
                                          final Response response,
                                          final RestAssuredContext restAssuredContext) {
        // Handle response
        restAssuredContext.setLastestResponse(response);
        restAssuredContext.setLatestStatusCode(response.statusCode());

        // Evaluate success criteria
        if (Objects.nonNull(step.getSuccessCriteria())) {
            boolean success = step.getSuccessCriteria().stream()
                    .allMatch(c -> {
                        var isSatisfied = criterionEvaluator.evalCriterion(c, restAssuredContext);
                        if (!isSatisfied) {
                            System.out.printf("Unsatisfied success criterion: %s%n", c);
                        }
                        return isSatisfied;
                    });
            if (!success) {
                throw new RuntimeException("Failed SuccessCriteria");
                // TODO do onFailureActions
            } else {
                // TODO do onSuccessActions
            }
        }

        // Resolve outputs
        if (Objects.nonNull(step.getOutputs())) {
            step.getOutputs().entrySet().forEach(output -> {
                if (output.getValue() instanceof TextNode textNode) {
                    var resolvedOutput = resolver.resolveExpression(textNode.asText(), restAssuredContext);
                    System.out.println(resolvedOutput);
                    var key = String.format("$steps.%s.outputs.%s", step.getStepId(), output.getKey());
                    resolver.addResolved(key, resolvedOutput);
                } else {
                    throw new RuntimeException("Unexpected");
                }
            });
        }
    }

    private String findServerUrl(final SourceDescription sourceDescription) {
        // TODO support multiple servers
        var serverUrl = sourceDescription.getReferencedOpenAPI().getServers().get(0).getUrl();

        if (serverUrl.contains("localhost") && !serverUrl.matches(".*:\\d{1,5}")) {
            // TODO make fallback port configurable
            serverUrl = "%s:8080".formatted(sourceDescription.getReferencedOpenAPI().getServers().get(0).getUrl());
        }
        return serverUrl;
    }
}
