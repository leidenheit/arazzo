package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Strings;
import com.jayway.jsonpath.JsonPath;
import de.leidenheit.core.execution.context.ExecutionResult;
import de.leidenheit.core.execution.context.RestAssuredContext;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.evaluation.CriterionEvaluator;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;
import de.leidenheit.infrastructure.utils.JsonPointerUtils;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HttpStepExecutor implements StepExecutor {

    @Override
    public ExecutionResult executeStep(final ArazzoSpecification arazzo,
                                       final Workflow workflow,
                                       final Step step,
                                       final ArazzoExpressionResolver resolver) {
        RestAssuredContext restAssuredContext = RestAssuredContext.builder().build();
        CriterionEvaluator criterionEvaluator = new CriterionEvaluator(resolver);

        SourceDescription sourceDescription = null;
        Map.Entry<String, Method> pathMethodEntry = null;
        if (Objects.nonNull(step.getOperationId())) {
            sourceDescription = findRelevantSourceDescriptionByOperationId(arazzo, step.getOperationId());
            pathMethodEntry = findOperationByOperationId(sourceDescription, step.getOperationId());
        } else if (Objects.nonNull(step.getOperationPath())) {
            sourceDescription = findRelevantSourceDescriptionByOperationPath(arazzo, step.getOperationPath());
            pathMethodEntry = findOperationByOperationPath(sourceDescription, step.getOperationPath());
        } else {
            throw new RuntimeException("Unexpected");
        }

        Objects.requireNonNull(sourceDescription);
        Objects.requireNonNull(pathMethodEntry);

        var requestSpecification = buildRestAssuredRequest(sourceDescription, step, restAssuredContext, resolver);
        var response = makeRequest(requestSpecification, pathMethodEntry);

        return handleResponseAndOutputs(workflow, step, resolver, criterionEvaluator, response, restAssuredContext);
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

    private Map.Entry<String, Method> findOperationByOperationId(final SourceDescription sourceDescription, final String operationId) {
        return sourceDescription.getReferencedOpenAPI().getPaths().entrySet().stream()
                .flatMap(pathsEntry -> pathsEntry.getValue().readOperationsMap().entrySet().stream()
                        .filter(operationEntry -> operationId.contains(operationEntry.getValue().getOperationId()))
                        .map(matchingOperationEntry -> Map.entry(
                                pathsEntry.getKey(),
                                Method.valueOf(matchingOperationEntry.getKey().name().toUpperCase()))
                        )
                ).findFirst()
                .orElseThrow(() -> new RuntimeException("No operation found for id " + operationId));
    }

    private Map.Entry<String, Method> findOperationByOperationPath(final SourceDescription sourceDescription,
                                                                   final String operationPath) {
        var unescapedOperationPath = JsonPointerUtils.unescapeJsonPointer(operationPath);
        var pattern = Pattern.compile("paths/(?<oasPath>.+)/(?<httpMethod>[a-zA-Z]+)$");
        var matcher = pattern.matcher(unescapedOperationPath);
        if (matcher.find()) {
            var oasOperationPath = matcher.group("oasPath");
            var httpMethod = matcher.group("httpMethod");
            if (Strings.isNullOrEmpty(oasOperationPath) || Strings.isNullOrEmpty(httpMethod)) throw new RuntimeException("Unexpected");
            return Map.entry(oasOperationPath, Method.valueOf(httpMethod.toUpperCase()));
        } else {
            throw new RuntimeException("Invalid format: '%s'".formatted(unescapedOperationPath));
        }
    }

    private RequestSpecification buildRestAssuredRequest(final SourceDescription sourceDescription,
                                                         final Step step,
                                                         final RestAssuredContext restAssuredContext,
                                                         final ArazzoExpressionResolver resolver) {
        var requestSpecification = RestAssured
                .given()
                .filter((requestSpec, responseSpec, ctx) -> {
                    restAssuredContext.setLatestUrl(requestSpec.getURI());
                    restAssuredContext.setLatestHttpMethod(requestSpec.getMethod());
                    restAssuredContext.setLatestRequest(requestSpec);

                    return ctx.next(requestSpec, responseSpec);
                });

        // apply uri
        String serverUrl = findServerUrl(sourceDescription);
        requestSpecification.baseUri(serverUrl);

        // apply path params
        Map<String, Object> pathParameterMap = Collections.emptyMap();
        if (Objects.nonNull(step.getParameters())) {
            pathParameterMap = step.getParameters().stream()
                    .collect(Collectors.toMap(
                            Parameter::getName,
                            parameter -> resolver.resolveExpression(parameter.getValue().toString(), null)
                    ));

            requestSpecification.pathParams(pathParameterMap);
        }

        // apply default content type
        requestSpecification.contentType(ContentType.JSON);

        // apply body
        // TODO refactor
        if (Objects.nonNull(step.getRequestBody())) {
            requestSpecification.contentType(step.getRequestBody().getContentType());
            AtomicReference<String> resolvedPayload = new AtomicReference<>(resolver.resolveString(step.getRequestBody().getPayload().toString()));

            if (Objects.nonNull(step.getRequestBody().getReplacements())) {
                var replacements = step.getRequestBody().getReplacements();
                replacements.forEach(replacement -> {
                    if (replacement.getTarget().startsWith("$")) {
                        // JSONPointer
                        var resolvedValue = resolver.resolveString(replacement.getValue().toString());
                        resolvedPayload.set(JsonPath.parse(resolvedPayload.get())
                                .set(replacement.getTarget(), resolvedValue).jsonString());
                    } else {
                        // XPATH
                        try {
                            Document document = DocumentBuilderFactory.newInstance()
                                    .newDocumentBuilder()
                                    .parse(new InputSource(new StringReader(resolvedPayload.get())));

                            XPath xpath = XPathFactory.newInstance().newXPath();
                            Node node = (Node) xpath.evaluate(
                                    replacement.getTarget(),
                                    document,
                                    XPathConstants.NODE);
                            if (node != null) {
                                node.setTextContent(replacement.getValue().toString());
                            }
                            TransformerFactory tf = TransformerFactory.newInstance();
                            Transformer transformer = tf.newTransformer();
                            StringWriter writer = new StringWriter();
                            transformer.transform(new DOMSource(document), new StreamResult(writer));
                            resolvedPayload.set(writer.getBuffer().toString());
                        } catch (SAXException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } catch (ParserConfigurationException e) {
                            throw new RuntimeException(e);
                        } catch (XPathExpressionException e) {
                            throw new RuntimeException(e);
                        } catch (TransformerConfigurationException e) {
                            throw new RuntimeException(e);
                        } catch (TransformerException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            requestSpecification.body(resolvedPayload.get());
        }

        return requestSpecification;
    }

    private Response makeRequest(final RequestSpecification requestSpecification, final Map.Entry<String, Method> pathMethodEntry) {
        var pathAsString = pathMethodEntry.getKey();
        var method = pathMethodEntry.getValue();

        return switch (method) {
            case GET -> requestSpecification.get(pathAsString);
            case POST -> requestSpecification.post(pathAsString);
            case PUT -> requestSpecification.put(pathAsString);
            case DELETE -> requestSpecification.delete(pathAsString);
            case OPTIONS -> requestSpecification.options(pathAsString);
            case PATCH -> requestSpecification.patch(pathAsString);
            case HEAD -> requestSpecification.head(pathAsString);
            default -> throw new RuntimeException("Unsupported by RestAssured");
        };
    }

    // TODO refactor
    private ExecutionResult handleResponseAndOutputs(final Workflow workflow,
                                                     final Step step,
                                                     final ArazzoExpressionResolver resolver,
                                                     final CriterionEvaluator criterionEvaluator,
                                                     final Response response,
                                                     final RestAssuredContext restAssuredContext) {

        var stepExecutionResultBuilder = ExecutionResult.builder();

        // Handle response
        restAssuredContext.setLastestResponse(response);
        restAssuredContext.setLatestStatusCode(response.statusCode());

        // Evaluate success criteria
        if (Objects.nonNull(step.getSuccessCriteria())) {
            boolean success = step.getSuccessCriteria().stream()
                    .allMatch(c -> {
                        var isSatisfied = criterionEvaluator.evalCriterion(c, restAssuredContext);
                        if (!isSatisfied) {
                            System.out.printf("Unsatisfied success criterion condition '%s' in step '%s'('%s %s -> %s')%n",
                                    c.getCondition(),
                                    step.getStepId(),
                                    restAssuredContext.getLatestHttpMethod(),
                                    restAssuredContext.getLatestUrl(),
                                    restAssuredContext.getLatestStatusCode());
                        }
                        return isSatisfied;
                    });

            stepExecutionResultBuilder.successful(success);

            System.out.printf("Execution of step '%s' successful: '%s'%n", step.getStepId(), success);
            if (!success) {
                if (Objects.nonNull(step.getOnFailure())) {
                    // return the first failure action object that fulfills its criteria
                    var fittingFailureAction = step.getOnFailure().stream()
                            .filter(f -> shouldExecuteAction(f.getCriteria(), criterionEvaluator, restAssuredContext))
                            .findFirst()
                            .orElse(null);

                    assert Objects.nonNull(fittingFailureAction) : "Failure action criteria are not fully satisfied for step '%s'%n====%n%n".formatted(step.getStepId());

                    System.out.printf("Running failure action '%s' for step '%s'%n", fittingFailureAction.getName(), step.getStepId());
                    if (FailureAction.FailureActionType.RETRY.equals(fittingFailureAction.getType())) {
                        // apply provided retry-after header value to the action
                        var retryAfter = restAssuredContext.getLastestResponse().getHeader("Retry-After");
                        if (Objects.nonNull(retryAfter)) {
                            System.out.printf("Applying header value of 'Retry-After' to failure action '%s': retryAfter=%s%n", fittingFailureAction.getName(), retryAfter);
                            fittingFailureAction.setRetryAfter(new BigDecimal(retryAfter));
                        }
                    }
                    stepExecutionResultBuilder.failureAction(fittingFailureAction);
                }
            } else {
                if (Objects.nonNull(step.getOnSuccess())) {
                    // return the first success action object that fulfills its criteria
                    var fittingSuccessAction = step.getOnSuccess().stream()
                            .filter(f -> shouldExecuteAction(f.getCriteria(), criterionEvaluator, restAssuredContext))
                            .findFirst()
                            .orElse(null);

                    assert Objects.nonNull(fittingSuccessAction) : "Success action criteria are not fully satisfied for step '%s'%n====%n%n".formatted(step.getStepId());

                    System.out.printf("Running success action '%s' for step '%s'%n", fittingSuccessAction.getName(), step.getStepId());
                    stepExecutionResultBuilder.successAction(fittingSuccessAction);
                }
            }
        }

        // Resolve outputs
        if (Objects.nonNull(step.getOutputs())) {
            step.getOutputs().entrySet().forEach(output -> {
                Object resolvedOutput = null;
                if (output.getValue() instanceof TextNode textNode) {
                    resolvedOutput = resolver.resolveExpression(textNode.asText(), restAssuredContext);
                } else {
                    resolvedOutput = resolver.resolveExpression(output.getValue().toString(), restAssuredContext);
                }
                if (Objects.nonNull(resolvedOutput)) {
                    var key = String.format("$steps.%s.outputs.%s", step.getStepId(), output.getKey());
                    resolver.addResolved(key, resolvedOutput);
                } else {
                    throw new RuntimeException("Unexpected");
                }
            });
        }

        return stepExecutionResultBuilder.build();
    }

    private boolean shouldExecuteAction(final List<Criterion> actionCriteria,
                                        final CriterionEvaluator criterionEvaluator,
                                        final RestAssuredContext restAssuredContext) {
        return actionCriteria.stream()
                .allMatch(criterion -> criterionEvaluator.evalCriterion(criterion, restAssuredContext));
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
