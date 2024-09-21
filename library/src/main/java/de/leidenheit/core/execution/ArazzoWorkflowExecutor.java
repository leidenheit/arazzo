package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.execution.resolving.ArazzoComponentRefResolver;
import de.leidenheit.core.execution.resolving.ArazzoExpressionResolver;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Parameter;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.io.ArazzoInputsReader;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ArazzoWorkflowExecutor {

    private final ArazzoSpecification arazzoSpecification;
    private final Workflow workflow;
    private final ExecutorParams params;

    public void execute() {
        // TODO finalize implementation
        System.out.printf("Executing workflow %s%n", workflow.getWorkflowId());

        // TODO rest assured
        var serverUrl = arazzoSpecification.getSourceDescriptions().get(0).getReferencedOpenAPI().getServers().get(0).getUrl();
        serverUrl = serverUrl + ":8080";

        // TODO components
        var mapper = new ObjectMapper();
        var node = mapper.convertValue(arazzoSpecification.getComponents(), JsonNode.class);
        var componentsResolver = new ArazzoComponentRefResolver(node);

        var inputSchema = workflow.getInputs();
        if (inputSchema.has("$ref")) {
            inputSchema = componentsResolver.resolveComponent(inputSchema.get("$ref").asText());
        }
        var inputs = ArazzoInputsReader.parseAndValidateInputs(params.inputsFilePath(), inputSchema);
        System.out.printf("inputs: %s%n", inputs.toString());

        ArazzoExpressionResolver resolver = new ArazzoExpressionResolver(arazzoSpecification, inputs);

        // execute
        for (Workflow wf : arazzoSpecification.getWorkflows()) {
            for (Step step : wf.getSteps()) {
                var pathOperationEntry = arazzoSpecification.getSourceDescriptions().get(0).getReferencedOpenAPI().getPaths().entrySet().stream()
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
            if (Objects.nonNull(wf.getOutputs())) {
                wf.getOutputs().entrySet().forEach(output -> {
                    System.out.println("wf iteration");
                    if (output.getValue() instanceof TextNode textNode) {
                        var x = resolver.resolveString(textNode.asText());
                        // TODO do something with that
                        System.out.println("wf output: " + x);
                    } else {
                        throw new RuntimeException("Unexpected");
                    }
                });
            }
            System.out.println("wf end");
        }
    }
}
