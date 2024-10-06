package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.execution.context.RestAssuredContext;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.evaluation.Evaluator;
import de.leidenheit.infrastructure.io.ArazzoInputsReader;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;
import de.leidenheit.infrastructure.utils.IOUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ArazzoWorkflowExecutor {

    // TODO finalize and refactor
    public Map<String, Object> execute(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsFilePath) {
        System.out.printf("Executing workflow %s%n", workflow.getWorkflowId());

        var inputsSchema = workflow.getInputs();
        var inputs = ArazzoInputsReader.parseAndValidateInputs(arazzo, inputsFilePath, inputsSchema);
        var resolver = ArazzoExpressionResolver.getInstance(arazzo, inputs);
        System.out.printf("inputs: %s%n", inputs.toString());

        // TODO introduce a rest assured executor for source descriptions of type OAS
        var sourceDescriptionOAS = arazzo.getSourceDescriptions().stream()
                .filter(sourceDescription -> SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unexpected"));

        // TODO remove once validation is in place
        if (!IOUtils.isValidFileOrUrl(sourceDescriptionOAS.getUrl())) throw new RuntimeException("Unexpected");

        var resultWorkflowOutputs = new HashMap<String, Object>();

        var oasServers = sourceDescriptionOAS.getReferencedOpenAPI().getServers();
        oasServers.forEach(oasServer -> {
            // TODO only add port if missing
            var serverUrl = oasServer.getUrl();
            if (!serverUrl.matches(".*:\\d{1,5}")) {
                // TODO make fallback port configurable
                serverUrl = "%s:8080".formatted(oasServer.getUrl());
            }

            // execute
            for (Step step : workflow.getSteps()) {

                var pathOperationEntry = arazzo.getSourceDescriptions().get(0).getReferencedOpenAPI().getPaths().entrySet().stream()
                        .filter(entry ->
                                entry.getValue().readOperations().stream()
                                        .anyMatch(o ->
                                                o.getOperationId().equals(step.getOperationId()))
                                || step.getOperationPath().contains(entry.getKey()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No operation found for id " + step.getOperationId()));

                var pathAsString = pathOperationEntry.getKey();
                var isHttpMethodGet = Objects.nonNull(pathOperationEntry.getValue().getGet());
                var pathParameterMap = step.getParameters().stream()
                        .collect(Collectors.toMap(
                                Parameter::getName,
                                parameter -> resolver.resolveExpression(parameter.getValue().toString(), null)
                        ));

                Evaluator evaluator = new Evaluator(resolver);
                RestAssuredContext restAssuredContext = RestAssuredContext.builder().build();

                var requestSpecification = RestAssured
                        .given()
                        .filter((requestSpec, responseSpec, ctx) -> {
                            restAssuredContext.setLatestUrl(requestSpec.getBaseUri());
                            restAssuredContext.setLatestHttpMethod(requestSpec.getMethod());
                            restAssuredContext.setLatestRequest(requestSpec);

                            return ctx.next(requestSpec, responseSpec);
                        })
                        .baseUri(serverUrl)
                        .pathParams(pathParameterMap)
                        .when();

                Response response;
                // do the actual call
                if (isHttpMethodGet) {
                    response = requestSpecification.get(pathAsString);
                } else {
                    response = requestSpecification.post(pathAsString);
                }

                // set latest $statusCode, $response
                restAssuredContext.setLatestStatusCode(response.statusCode());
                restAssuredContext.setLastestResponse(response);

                // verify successCriteria
                if (Objects.nonNull(step.getSuccessCriteria())) {
                    boolean asExpected = step.getSuccessCriteria().stream()
                            .allMatch(c -> evaluator.evalCriterion(c, restAssuredContext));
                    if (asExpected) {
                        // TODO handle onSuccess
                        System.out.println("Successful SuccessCriteria");
                    } else {
                        // TODO handle onFailure
                        throw new RuntimeException("Failed SuccessCriteria");
                    }
                }

                // TODO resolve step outputs
                if (Objects.nonNull(step.getOutputs())) {
                    step.getOutputs().entrySet().forEach(output -> {
                        System.out.println("step iteration");
                        if (output.getValue() instanceof TextNode textNode) {
                            var resolvedOutput = resolver.resolveExpression(textNode.asText(), restAssuredContext);
                            System.out.println(resolvedOutput);
                            var key = String.format("$steps.%s.outputs.%s", step.getStepId(), output.getKey());
                            resolver.resolvedMap.put(key, resolvedOutput);
                        } else {
                            throw new RuntimeException("Unexpected");
                        }
                    });
                }
                System.out.println("step end");
            }

            // TODO resolve wf outputs
            if (Objects.nonNull(workflow.getOutputs())) {
                workflow.getOutputs().forEach((key, value) -> {
                    if (value instanceof TextNode textNode) {
                        var resolvedOutput = resolver.resolveString(textNode.asText());
                        resultWorkflowOutputs.put(key, resolvedOutput);
                    } else {
                        throw new RuntimeException("Unexpected");
                    }
                });
            }
            System.out.printf("wf end for oas-server '%s [%s]'%n", oasServer.getDescription(), serverUrl);
        });

        return resultWorkflowOutputs;
    }
}
