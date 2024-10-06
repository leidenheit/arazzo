package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.execution.context.RestAssuredContext;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.evaluation.Evaluator;
import de.leidenheit.infrastructure.io.ArazzoInputsReader;
import de.leidenheit.infrastructure.parsing.ArazzoParseOptions;
import de.leidenheit.infrastructure.parsing.ArazzoParser;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;
import de.leidenheit.infrastructure.utils.JsonPointerUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.util.Collections;
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

        var resultWorkflowOutputs = new HashMap<String, Object>();

        // TODO initialize referenced source description content
        arazzo.getSourceDescriptions().forEach(sourceDescriptionX -> {
                    if (SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescriptionX.getType())) {
                        OpenAPIV3Parser oasParser = new OpenAPIV3Parser();
                        ParseOptions oasParseOptions = new ParseOptions();
                        oasParseOptions.setResolveFully(true);
                        OpenAPI refOAS = oasParser.read(sourceDescriptionX.getUrl(), Collections.emptyList(), oasParseOptions);
                        sourceDescriptionX.setReferencedOpenAPI(refOAS);
                    } else if (SourceDescription.SourceDescriptionType.ARAZZO.equals(sourceDescriptionX.getType())) {
                        ArazzoParser arazzoParser = new ArazzoParser();
                        var options = ArazzoParseOptions.builder()
                                .oaiAuthor(false)
                                .allowEmptyStrings(false)
                                .mustValidate(true)
                                .resolve(true)
                                .build();
                        var refArazzo = arazzoParser.readLocation(sourceDescriptionX.getUrl(), options);
                        if (refArazzo.isInvalid()) throw new RuntimeException("Unexpected");
                        sourceDescriptionX.setReferencedArazzo(refArazzo.getArazzo());
                    } else {
                        throw new RuntimeException("Unsupported");
                    }
                });
        System.out.println("Hello world");

        // TODO introduce a rest assured executor for source descriptions of type OAS
//        var sourceDescriptionOAS = arazzo.getSourceDescriptions().stream()
//                .filter(sourceDescription -> SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType()))
//                .findFirst()
//                .orElseThrow(() -> new RuntimeException("Unexpected"));
//
//        // TODO remove once validation is in place
//        if (!IOUtils.isValidFileOrUrl(sourceDescriptionOAS.getUrl())) throw new RuntimeException("Unexpected");
//
//
//        var oasServers = sourceDescriptionOAS.getReferencedOpenAPI().getServers();
//        oasServers.forEach(oasServer -> {
//            // TODO only add port if missing
//            var serverUrl = oasServer.getUrl();
//            if (!serverUrl.matches(".*:\\d{1,5}")) {
//                // TODO make fallback port configurable
//                serverUrl = "%s:8080".formatted(oasServer.getUrl());
//            }

            // execute
            for (Step step : workflow.getSteps()) {


                // TODO find server from source description
                String serverUrl = null;
                Map.Entry<String, PathItem> pathOperationEntry = null;
                if (Objects.nonNull(step.getOperationId())) {
                    var sourceDescription = arazzo.getSourceDescriptions().get(0);
                    if (arazzo.getSourceDescriptions().size() > 1) {
                        sourceDescription = arazzo.getSourceDescriptions().stream()
                                .filter(s -> step.getOperationId().contains(s.getName()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Unexpected"));
                    }
                    serverUrl = sourceDescription.getReferencedOpenAPI().getServers().get(0).getUrl();
                    if (serverUrl.contains("localhost") && !serverUrl.matches(".*:\\d{1,5}")) {
                        // TODO make fallback port configurable
                        serverUrl = "%s:8080".formatted(sourceDescription.getReferencedOpenAPI().getServers().get(0).getUrl());
                    }
                    pathOperationEntry = sourceDescription.getReferencedOpenAPI().getPaths().entrySet().stream()
                            .filter(entry ->
                                    entry.getValue().readOperations().stream()
                                            .anyMatch(o -> step.getOperationId().contains(o.getOperationId())))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No operation found for id " + step.getOperationId()));
                } else if (Objects.nonNull(step.getOperationPath())) {
                    var sourceDescription = arazzo.getSourceDescriptions().get(0);
                    if (arazzo.getSourceDescriptions().size() > 1) {
                        sourceDescription = arazzo.getSourceDescriptions().stream()
                                .filter(s -> step.getOperationPath().contains(s.getName()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Unexpected"));
                    }
                    serverUrl = sourceDescription.getReferencedOpenAPI().getServers().get(0).getUrl();
                    if (serverUrl.contains("localhost") && !serverUrl.matches(".*:\\d{1,5}")) {
                        // TODO make fallback port configurable
                        serverUrl = "%s:8080".formatted(sourceDescription.getReferencedOpenAPI().getServers().get(0).getUrl());
                    }
                    var unescapedOperationPath = JsonPointerUtils.unescapeJsonPointer(step.getOperationPath());
                    pathOperationEntry = sourceDescription.getReferencedOpenAPI().getPaths().entrySet().stream()
                            .filter(entry ->
                                    entry.getValue().readOperations().stream()
                                            .anyMatch(o -> unescapedOperationPath.contains(entry.getKey())))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No operation found for id " + step.getOperationId()));
                } else if (Objects.nonNull(step.getWorkflowId())) {
                    var sourceDescription = arazzo.getSourceDescriptions().get(0);
                    if (arazzo.getSourceDescriptions().size() > 1) {
                        sourceDescription = arazzo.getSourceDescriptions().stream()
                                .filter(s -> step.getWorkflowId().contains(s.getName()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Unexpected"));
                    }
                    var refWorkflow = sourceDescription.getReferencedArazzo().getWorkflows().stream()
                            .filter(wf -> step.getWorkflowId().contains(wf.getWorkflowId()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Unexpected"));
                    var refworkflowOutputs = execute(sourceDescription.getReferencedArazzo(), refWorkflow, inputsFilePath);
                    resultWorkflowOutputs.putAll(refworkflowOutputs);
                    continue;
                } else {
                    throw new RuntimeException("Unexpected");
                }


//                var pathOperationEntry = arazzo.getSourceDescriptions().get(0).getReferencedOpenAPI().getPaths().entrySet().stream()
//                        .filter(entry ->
//                                entry.getValue().readOperations().stream()
//                                        .anyMatch(o ->
//                                                o.getOperationId().equals(step.getOperationId()))
//                                || step.getOperationPath().contains(entry.getKey()))
//                        .findFirst()
//                        .orElseThrow(() -> new RuntimeException("No operation found for id " + step.getOperationId()));

                var pathAsString = pathOperationEntry.getKey();
                var isHttpMethodGet = Objects.nonNull(pathOperationEntry.getValue().getGet());
                Map<String, Object> pathParameterMap = Collections.emptyMap();
                if (Objects.nonNull(step.getParameters())) {
                    pathParameterMap = step.getParameters().stream()
                            .collect(Collectors.toMap(
                                    Parameter::getName,
                                    parameter -> resolver.resolveExpression(parameter.getValue().toString(), null)
                            ));
             }

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
                            .allMatch(c -> {
                                var satisfied = evaluator.evalCriterion(c, restAssuredContext);
                                return satisfied;
                            });
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
//            System.out.printf("wf end for oas-server '%s [%s]'%n", oasServer.getDescription(), serverUrl);
//        });

        return resultWorkflowOutputs;
    }
}
