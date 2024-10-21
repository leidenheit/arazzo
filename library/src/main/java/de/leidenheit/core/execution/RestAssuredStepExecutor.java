package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Strings;
import com.jayway.jsonpath.JsonPath;
import de.leidenheit.core.execution.context.ExecutionResultContext;
import de.leidenheit.core.execution.context.RestAssuredContext;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Criterion;
import de.leidenheit.core.model.FailureAction;
import de.leidenheit.core.model.Parameter;
import de.leidenheit.core.model.PayloadReplacementObject;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.SuccessAction;
import de.leidenheit.core.model.Workflow;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RestAssuredStepExecutor implements StepExecutor {

    // TODO designated servers support
    //  public static final String SERVER_MARKER = "x-itarazzo-designated-server"; // move to global constants

    private final ArazzoSpecification arazzo;
    private final CriterionEvaluator criterionEvaluator;
    private final ArazzoExpressionResolver resolver;

    public RestAssuredStepExecutor(final ArazzoSpecification arazzo,
                                   final CriterionEvaluator criterionEvaluator,
                                   final ArazzoExpressionResolver resolver) {
        this.arazzo = arazzo;
        this.resolver = resolver;
        this.criterionEvaluator = criterionEvaluator;
    }

    @Override
    public ExecutionResultContext executeStep(final Workflow workflow, final Step step) {
        RestAssuredContext restAssuredContext = RestAssuredContext.builder().build();

        SourceDescription sourceDescription = null;
        Map.Entry<String, Method> pathMethodEntry = null;
        if (Objects.nonNull(step.getOperationId())) {
            sourceDescription = findRelevantSourceDescriptionByIdentifier(arazzo, step.getOperationId());
            pathMethodEntry = findPathAndMethodByOperationId(sourceDescription, step.getOperationId());
        } else if (Objects.nonNull(step.getOperationPath())) {
            sourceDescription = findRelevantSourceDescriptionByIdentifier(arazzo, step.getOperationPath());
            pathMethodEntry = extractPathAndMethodByOperationPath(step.getOperationPath());
        }

        var requestSpecification = buildRequest(sourceDescription, step, restAssuredContext, resolver);
        var response = makeRequest(requestSpecification, pathMethodEntry);

        return handleResponseAndOutputs(step, response, restAssuredContext);
    }

    private RequestSpecification buildRequest(final SourceDescription sourceDescription,
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
        Map<String, Object> pathParameterMap;
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
        if (Objects.nonNull(step.getRequestBody())) {
            requestSpecification.contentType(step.getRequestBody().getContentType());
            String resolvedPayload = resolver.resolveString(step.getRequestBody().getPayload().toString());

            if (Objects.nonNull(step.getRequestBody().getReplacements())) {
                var replacements = step.getRequestBody().getReplacements();
                for (PayloadReplacementObject replacementObject : replacements) {
                    if (replacementObject.getTarget().startsWith("$")) {
                        // JSONPointer
                        resolvedPayload = (applyPayloadFromJsonPath(resolvedPayload, replacementObject, resolver));
                    } else {
                        // XPATH
                        resolvedPayload = (applyPayloadFromXPath(resolvedPayload, replacementObject));
                    }
                }
            }
            requestSpecification.body(resolvedPayload);
        }

        return requestSpecification;
    }

    private Response makeRequest(final RequestSpecification requestSpecification,
                                 final Map.Entry<String, Method> pathMethodEntry) {
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
            // TODO replace with exception
            default -> throw new RuntimeException("Unsupported by RestAssured");
        };
    }

    private SourceDescription findRelevantSourceDescriptionByIdentifier(final ArazzoSpecification arazzo,
                                                                        final String identifier) {
        var sourceDescription = arazzo.getSourceDescriptions().get(0);
        if (arazzo.getSourceDescriptions().size() > 1) {
            sourceDescription = arazzo.getSourceDescriptions().stream()
                    .filter(s -> identifier.contains(s.getName()))
                    .findFirst()
                    // TODO replace with exception
                    .orElseThrow(() -> new RuntimeException("Unexpected"));
        }
        return sourceDescription;
    }

    private Map.Entry<String, Method> findPathAndMethodByOperationId(final SourceDescription sourceDescription,
                                                                     final String operationId) {
        return sourceDescription.getReferencedOpenAPI().getPaths().entrySet().stream()
                .flatMap(pathsEntry -> pathsEntry.getValue().readOperationsMap().entrySet().stream()
                        .filter(operationEntry -> operationId.contains(operationEntry.getValue().getOperationId()))
                        .map(matchingOperationEntry -> Map.entry(
                                pathsEntry.getKey(),
                                Method.valueOf(matchingOperationEntry.getKey().name().toUpperCase()))
                        )
                ).findFirst()
                // TODO replace with exception
                .orElseThrow(() -> new RuntimeException("No operation found for id " + operationId));
    }

    private Map.Entry<String, Method> extractPathAndMethodByOperationPath(final String operationPath) {
        var unescapedOperationPath = JsonPointerUtils.unescapeJsonPointer(operationPath);
        var pattern = Pattern.compile("paths/(?<oasPath>.+)/(?<httpMethod>[a-zA-Z]+)$");
        var matcher = pattern.matcher(unescapedOperationPath);
        if (matcher.find()) {
            var oasOperationPath = matcher.group("oasPath");
            var httpMethod = matcher.group("httpMethod");
            if (Strings.isNullOrEmpty(oasOperationPath) || Strings.isNullOrEmpty(httpMethod))
                // TODO replace with exception
                throw new RuntimeException("Unexpected");
            return Map.entry(oasOperationPath, Method.valueOf(httpMethod.toUpperCase()));
        } else {
            // TODO replace with exception
            throw new RuntimeException("Invalid format: '%s'".formatted(unescapedOperationPath));
        }
    }

    private SuccessAction findFittingSuccessAction(final Step step,
                                                   final CriterionEvaluator criterionEvaluator,
                                                   final RestAssuredContext restAssuredContext) {
        if (Objects.nonNull(step.getOnSuccess())) {
            // return the first success action object that fulfills its criteria
            var fittingSuccessAction = step.getOnSuccess().stream()
                    .filter(f -> shouldExecuteAction(f.getCriteria(), criterionEvaluator, restAssuredContext))
                    .findFirst()
                    .orElse(null);

            // TODO replace with exception
            assert Objects.nonNull(fittingSuccessAction) : "@@@===> Success action criteria are not fully satisfied for step '%s'%n====%n%n".formatted(step.getStepId());

            return fittingSuccessAction;
        }
        return null;
    }

    private FailureAction findFittingFailureAction(final Step step,
                                                   final CriterionEvaluator criterionEvaluator,
                                                   final RestAssuredContext restAssuredContext) {
        if (Objects.nonNull(step.getOnFailure())) {
            // return the first failure action object that fulfills its criteria
            var fittingFailureAction = step.getOnFailure().stream()
                    .filter(f -> shouldExecuteAction(f.getCriteria(), criterionEvaluator, restAssuredContext))
                    .findFirst()
                    .orElse(null);

            // TODO replace with exception
            assert Objects.nonNull(fittingFailureAction) : "@@@===> Failure action criteria are not fully satisfied for step '%s'%n====%n%n".formatted(step.getStepId());

            if (FailureAction.FailureActionType.RETRY.equals(fittingFailureAction.getType())) {
                // apply provided retry-after header value to the action
                var retryAfter = restAssuredContext.getLastestResponse().getHeader("Retry-After");
                if (Objects.nonNull(retryAfter)) {
                    System.out.printf("=> FailureAction ['%s' as '%s']: applying header 'Retry-After' with a value of '%s'%n", fittingFailureAction.getName(), fittingFailureAction.getType(), retryAfter);
                    fittingFailureAction.setRetryAfter(new BigDecimal(retryAfter));
                }
            }
            return fittingFailureAction;
        }
        return null;
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

    private void handleResponse(final RestAssuredContext restAssuredContext, final Response response) {
        restAssuredContext.setLastestResponse(response);
        restAssuredContext.setLatestStatusCode(response.statusCode());
    }

    private ExecutionResultContext handleResponseAndOutputs(final Step step,
                                                            final Response response,
                                                            final RestAssuredContext restAssuredContext) {
        var stepExecutionResultBuilder = ExecutionResultContext.builder();

        // Handle response
        handleResponse(restAssuredContext, response);

        // Evaluate success criteria
        var success = evaluateSuccessCriteria(step, restAssuredContext);
        System.out.printf("=> Step ['%s']: successful: '%s'%n", step.getStepId(), success);
        if (!success) {
            stepExecutionResultBuilder.failureAction(
                    findFittingFailureAction(step, criterionEvaluator, restAssuredContext));
        } else {
            stepExecutionResultBuilder.successAction(
                    findFittingSuccessAction(step, criterionEvaluator, restAssuredContext));

            // Resolve outputs
            applyOutputs(step, restAssuredContext);
        }
        return stepExecutionResultBuilder.successful(success).build();
    }

    private String applyPayloadFromXPath(final String payload, final PayloadReplacementObject replacement) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(payload)));

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
            return writer.getBuffer().toString();
        } catch (SAXException | IOException | ParserConfigurationException | XPathExpressionException |
                 TransformerException e) {
            // TODO replace with exception
            throw new RuntimeException("Unexpected", e);
        }
    }

    private String applyPayloadFromJsonPath(final String payload,
                                            final PayloadReplacementObject replacement,
                                            final ArazzoExpressionResolver resolver) {
        var resolvedValue = resolver.resolveString(replacement.getValue().toString());
        return JsonPath.parse(payload).set(replacement.getTarget(), resolvedValue).jsonString();
    }

    private void applyOutputs(final Step step, final RestAssuredContext restAssuredContext) {
        if (Objects.nonNull(step.getOutputs())) {
            step.getOutputs().forEach((key, value) -> {
                Object resolvedOutput;
                if (value instanceof TextNode textNode) {
                    resolvedOutput = resolver.resolveExpression(textNode.asText(), restAssuredContext);
                } else {
                    resolvedOutput = resolver.resolveExpression(value.toString(), restAssuredContext);
                }
                if (Objects.nonNull(resolvedOutput)) {
                    var resolvedEntryKey = String.format("$steps.%s.outputs.%s", step.getStepId(), key);
                    resolver.addResolved(resolvedEntryKey, resolvedOutput);
                } else {
                    // TODO replace with exception
                    throw new RuntimeException("Unexpected");
                }
            });
        }
    }

    private boolean evaluateSuccessCriteria(final Step step, final RestAssuredContext restAssuredContext) {
        return step.getSuccessCriteria().stream()
                .allMatch(c -> {
                    var isSatisfied = criterionEvaluator.evalCriterion(c, restAssuredContext);
                    if (!isSatisfied) {
                        System.out.printf("@@@===> Unsatisfied success criterion condition '%s' in step '%s'('%s %s -> %s')%n",
                                c.getCondition(),
                                step.getStepId(),
                                restAssuredContext.getLatestHttpMethod(),
                                restAssuredContext.getLatestUrl(),
                                restAssuredContext.getLatestStatusCode());
                    }
                    return isSatisfied;
                });
    }

    private boolean shouldExecuteAction(final List<Criterion> actionCriteria,
                                        final CriterionEvaluator criterionEvaluator,
                                        final RestAssuredContext restAssuredContext) {
        return actionCriteria.stream()
                .allMatch(criterion -> criterionEvaluator.evalCriterion(criterion, restAssuredContext));
    }
}
