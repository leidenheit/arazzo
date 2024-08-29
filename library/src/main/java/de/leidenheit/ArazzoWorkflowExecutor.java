package de.leidenheit;

import io.restassured.RestAssured;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.notNullValue;

@RequiredArgsConstructor
public class ArazzoWorkflowExecutor {

    private final ArazzoSpecification arazzoSpecification;
    private final ArazzoSpecification.Workflow workflow;
    private final ArazzoInputsResolver inputsResolver;

    public void execute() {
        // TODO finalize implementation
        System.out.printf("Executing workflow %s%n", workflow.getWorkflowId());

        // TODO remove this mock and use the provided arazzo
        var mockParameter = ArazzoSpecification.Workflow.Step.Parameter.builder()
                .name("id")
                .value("4711")
                .in(ArazzoSpecification.Workflow.Step.Parameter.ParameterEnum.PATH)
                .reference(null)
                .build();
        var mockStep1 = ArazzoSpecification.Workflow.Step.builder()
                .stepId("retrieveCookieStep")
                .description("")
                .successCriteria(Collections.emptyList())
                .outputs(null)
                .operationId("findCookie")
                .parameters(List.of(mockParameter))
                .onFailure(Collections.emptyList())
                .onSuccess(Collections.emptyList())
                .build();
        var mockStep2 = ArazzoSpecification.Workflow.Step.builder()
                .stepId("eatCookieStep")
                .description("")
                .successCriteria(Collections.emptyList())
                .outputs(null)
                .operationId("eatCookie")
                .parameters(List.of(mockParameter))
                .onFailure(Collections.emptyList())
                .onSuccess(Collections.emptyList())
                .build();
        var mockWorkflow = ArazzoSpecification.Workflow.builder()
                .workflowId("retrieveCookieAndEatCookie")
                .summary("")
                .description("")
                .inputs(null)
                .outputs(null)
                .steps(List.of(mockStep1, mockStep2))
                .build();
        var mockArazzo = ArazzoSpecification.builder()
                .arazzo("1.0.0")
                .info(arazzoSpecification.getInfo())
                .sourceDescriptions(arazzoSpecification.getSourceDescriptions())
                .workflows(List.of(mockWorkflow))
                .openAPI(arazzoSpecification.getOpenAPI())
                .build();

        // TODO rest assured
        var serverUrl = arazzoSpecification.getOpenAPI().getServers().get(0).getUrl();
        serverUrl = serverUrl + ":8080";

        for (ArazzoSpecification.Workflow wf : mockArazzo.getWorkflows()) {
            for (ArazzoSpecification.Workflow.Step step : wf.getSteps()) {
                var pathOperationEntry = mockArazzo.getOpenAPI().getPaths().entrySet().stream()
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

                if (isHttpMethodGet) {
                    RestAssured
                            .given()
                            .baseUri(serverUrl)
                            .pathParam(mockParameter.getName(), mockParameter.getValue())
                            .when()
                            .get(pathAsString)
                            .then()
                            .statusCode(200)
                            .body(notNullValue());
                } else {
                    RestAssured
                            .given()
                            .baseUri(serverUrl)
                            .pathParams(pathParameterMap)
                            .when()
                            .post(pathAsString)
                            .then()
                            .statusCode(202)
                            .body(notNullValue());

                }
            }
        }
    }
}
