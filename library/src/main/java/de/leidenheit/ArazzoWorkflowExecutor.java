package de.leidenheit;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ArazzoWorkflowExecutor {

    private final ArazzoSpecification arazzoSpecification;
    private final ArazzoSpecification.Workflow workflow;
    private final ExecutorParams params;

    public void execute() {
        // TODO finalize implementation
        System.out.printf("Executing workflow %s%n", workflow.getWorkflowId());

        // TODO rest assured
        var serverUrl = arazzoSpecification.getOpenAPI().getServers().get(0).getUrl();
        serverUrl = serverUrl + ":8080";

        var inputs = ArazzoInputsReader.parseAndValidateInputs(params.inputsFilePath(), workflow.getInputs());
        System.out.printf("inputs: %s%n", inputs.toString());

        RuntimeExpressionResolver resolver = new RuntimeExpressionResolver(
                arazzoSpecification, inputs);
        // FIXME: do not resolve beforehand; seems to be better to do within the iteration
        ArazzoSpecification resolvedSpec = resolver.resolve();
        System.out.println("Resolved Node: " + resolvedSpec.toString());

        // execute
        for (ArazzoSpecification.Workflow wf : resolvedSpec.getWorkflows()) {
            for (ArazzoSpecification.Workflow.Step step : wf.getSteps()) {
                var pathOperationEntry = arazzoSpecification.getOpenAPI().getPaths().entrySet().stream()
                        .filter(entry ->
                                entry.getValue().readOperations().stream()
                                        .anyMatch(o -> o.getOperationId().equals(step.getOperationId())))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No operation found for id " + step.getOperationId()));

                var pathAsString = pathOperationEntry.getKey();
                var isHttpMethodGet = Objects.nonNull(pathOperationEntry.getValue().getGet());
                var pathParameterMap = step.getParameters().stream()
                        .collect(Collectors.toMap(
                                ArazzoSpecification.Workflow.Step.Parameter::getName,
                                ArazzoSpecification.Workflow.Step.Parameter::getValue));

                Evaluator evaluator = new Evaluator(resolver);
                Evaluator.EvaluatorParams evaluatorParams = Evaluator.EvaluatorParams.builder().build();

                var requestSpecification = RestAssured
                        .given()
                        .filter((requestSpec, responseSpec, ctx) -> {
                            // TODO use filter to inspect request and extract necessary data: $url, $method, $request,
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

                // TODO set latest $statusCode, $response
                evaluatorParams.setLatestStatusCode(response.statusCode());
                evaluatorParams.setLastestResponse(response);

                // TODO verify successCriteria
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
                        var resolvedOutput = resolver.resolveExpression(output.getValue().toString(), evaluatorParams);
                        output.setValue(resolvedOutput);
                    });
                }
                System.out.println("step end");
            }

            // TODO resolve wf outputs
            if (Objects.nonNull(wf.getOutputs())) {
                wf.getOutputs().entrySet().forEach(output -> {
                    var resolvedOutput = resolver.resolveExpression(output.getValue().toString());
                    output.setValue(resolvedOutput);
                });
            }
            System.out.println("wf end");
        }
    }
}
