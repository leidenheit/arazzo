package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.execution.resolving.ArazzoComponentRefResolver;
import de.leidenheit.core.execution.resolving.ArazzoExpressionResolver;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.io.ArazzoInputsReader;
import de.leidenheit.infrastructure.validation.utils.FileUrlResolver;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ArazzoWorkflowExecutor {

    // TODO finalize and refactor
    public Map<String, Object> execute(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsFilePath) {
        System.out.printf("Executing workflow %s%n", workflow.getWorkflowId());

        // TODO rest assured
        var sourceDescriptionOAS = arazzo.getSourceDescriptions().stream()
                .filter(sourceDescription -> SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unexpected"));
        // TODO remove once validation is in place
        if (!FileUrlResolver.isValidFileOrUrl(sourceDescriptionOAS.getUrl())) throw new RuntimeException("Unexpected");
        var oasServers = sourceDescriptionOAS.getReferencedOpenAPI().getServers();
        oasServers.forEach(oasServer -> {
            // TODO only add port if missing
            var serverUrl = oasServer.getUrl();
            if (!serverUrl.matches(".*:\\d{1,5}")) {
                serverUrl = "%s:8080".formatted(oasServer.getUrl()); // TODO make fallback port configurable
            }

            // TODO components
            var mapper = new ObjectMapper();
            var node = mapper.convertValue(arazzo.getComponents(), JsonNode.class);
            var componentsResolver = new ArazzoComponentRefResolver(node);

            var inputSchema = workflow.getInputs();
            if (inputSchema.has("$ref")) {
                inputSchema = componentsResolver.resolveComponent(inputSchema.get("$ref").asText());
            }
            var inputs = ArazzoInputsReader.parseAndValidateInputs(inputsFilePath, inputSchema);
            System.out.printf("inputs: %s%n", inputs.toString());

            ArazzoExpressionResolver resolver = new ArazzoExpressionResolver(arazzo, inputs);

            // execute
            for (Step step : workflow.getSteps()) {
                var pathOperationEntry = arazzo.getSourceDescriptions().get(0).getReferencedOpenAPI().getPaths().entrySet().stream()
                        .filter(entry ->
                                entry.getValue().readOperations().stream()
                                        .anyMatch(o -> o.getOperationId().equals(step.getOperationId())))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No operation found for id " + step.getOperationId()));

                var pathAsString = pathOperationEntry.getKey();
                var isHttpMethodGet = Objects.nonNull(pathOperationEntry.getValue().getGet());
                var pathParameterMap = step.getParameters().stream()
                        .collect(Collectors.toMap(
                                Parameter::getName,
                                parameter -> resolver.resolveExpression(parameter.getValue().toString())
                        ));

                Evaluator evaluator = new Evaluator(resolver);
                Evaluator.EvaluatorParams evaluatorParams = Evaluator.EvaluatorParams.builder().build();

                var requestSpecification = RestAssured
                        .given()
                        .filter((requestSpec, responseSpec, ctx) -> {
                            evaluatorParams.setLatestUrl(requestSpec.getBaseUri());
                            evaluatorParams.setLatestHttpMethod(requestSpec.getMethod());
                            evaluatorParams.setLatestRequest(requestSpec);

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
                evaluatorParams.setLatestStatusCode(response.statusCode());
                evaluatorParams.setLastestResponse(response);

                // verify successCriteria
                if (Objects.nonNull(step.getSuccessCriteria())) {
                    boolean asExpected = step.getSuccessCriteria().stream()
                            .allMatch(c -> evaluator.evalCriterion(c, evaluatorParams));
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
                            var resolvedOutput = resolver.resolveExpression(textNode.asText(), evaluatorParams);
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
                workflow.getOutputs().entrySet().forEach(output -> {
                    if (output.getValue() instanceof TextNode textNode) {
                        var x = resolver.resolveString(textNode.asText());
                        // TODO do something with that
                        System.out.printf("wf output: %s%n", x);
                        workflow.getOutputs().put(output.getKey(), x);
                    } else if (output.getValue() instanceof String outputValueAsString) {
                        // already resolved and placed in map; no need to do anything
                    }
                    else {
                        throw new RuntimeException("Unexpected");
                    }
                });
            }
            System.out.printf("wf end for oas-server '%s [%s]'%n", oasServer.getDescription(), serverUrl);
        });

        return workflow.getOutputs();
    }
}
