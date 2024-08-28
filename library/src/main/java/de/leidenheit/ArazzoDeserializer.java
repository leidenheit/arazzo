package de.leidenheit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.parser.ResolverCache;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("java:S1192") // magic strings
public class ArazzoDeserializer {

    protected static final Set<String> RESERVED_KEYWORDS = new LinkedHashSet<>(List.of(
            "x-oai-","x-oas-", "arazzo"
    ));
    protected static final Set<String> ROOT_KEYS = new LinkedHashSet<>(List.of(
            "arazzo", "info", "sourceDescriptions", "workflows", "components", "extensions"
    ));
    protected static final Set<String> INFO_KEYS = new LinkedHashSet<>(List.of(
            "title", "summary", "description", "version", "extensions"
    ));
    protected static final Set<String> SOURCE_DESCRIPTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "url", "type", "extensions"
    ));
    protected static final Set<String> WORKFLOW_KEYS = new LinkedHashSet<>(List.of(
            "workflowId", "summary", "description", "inputs",
            "dependsOn", "steps", "successActions" , "failureActions" ,
            "outputs", "parameters", "extensions"
    ));
    protected static final Set<String> STEP_KEYS = new LinkedHashSet<>(List.of(
            "description", "stepId", "operationId", "operationPath",
            "workflowId", "parameters", "requestBody", "successCriteria",
            "onSuccess", "onFailure", "outputs", "extensions"
    ));
    protected static final Set<String> COMPONENTS_KEYS = new LinkedHashSet<>(List.of(
            "inputs", "parameters", "successActions", "failureActions", "extensions"
    ));
    protected static final Set<String> PARAMETER_KEYS = new LinkedHashSet<>(List.of(
            "name", "in", "value", "extensions"
    ));
    protected static final Set<String> SUCCESS_ACTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "type", "workflowId", "stepId", "criteria", "extensions"
    ));
    protected static final Set<String> FAILURE_ACTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "type", "workflowId", "stepId", "criteria", "retryAfter", "retryLimit", "extensions"
    ));
    protected static final Set<String> CRITERION_KEYS = new LinkedHashSet<>(List.of(
            "context", "condition", "type", "extensions"
    ));
    protected static final Set<String> REQUEST_BODY_KEYS = new LinkedHashSet<>(List.of(
            "contentType", "payload", "replacements", "extensions"
    ));
    protected static final Set<String> PAYLOAD_REPLACEMENT_OBJECT_KEYS = new LinkedHashSet<>(List.of(
            "target", "value", "extensions"
    ));
    protected static final Set<String> REUSABLE_OBJECT_KEYS = new LinkedHashSet<>(List.of(
            "reference", "value"
    ));
    protected static final Set<String> SCHEMA_KEYS = new LinkedHashSet<>(List.of(
            "format"
    ));
    protected static Map<String, Map<String, Set<String>>> KEYS = new LinkedHashMap<>();
    // TODO introduce check for valid node types
    protected static Set<JsonNodeType> validNodeTypes = new LinkedHashSet<>(List.of(
            JsonNodeType.OBJECT, JsonNodeType.STRING
    ));

    static {
        Map<String, Set<String>> keys10 = new LinkedHashMap<>();

        keys10.put("RESERVED_KEYWORDS", RESERVED_KEYWORDS);
        keys10.put("ROOT_KEYS", ROOT_KEYS);
        keys10.put("INFO_KEYS", INFO_KEYS);
        keys10.put("SOURCE_DESCRIPTION_KEYS", SOURCE_DESCRIPTION_KEYS);
        keys10.put("WORKFLOW_KEYS", WORKFLOW_KEYS);
        keys10.put("STEP_KEYS", STEP_KEYS);
        keys10.put("SCHEMA_KEYS", SCHEMA_KEYS);
        keys10.put("COMPONENTS_KEYS", COMPONENTS_KEYS);
        keys10.put("PARAMETER_KEYS", PARAMETER_KEYS);
        keys10.put("SUCCESS_ACTION_KEYS", SUCCESS_ACTION_KEYS);
        keys10.put("FAILURE_ACTION_KEYS", FAILURE_ACTION_KEYS);
        keys10.put("CRITERION_KEYS", CRITERION_KEYS);
        keys10.put("REQUEST_BODY_KEYS", REQUEST_BODY_KEYS);
        keys10.put("PAYLOAD_REPLACEMENT_OBJECT_KEYS", PAYLOAD_REPLACEMENT_OBJECT_KEYS);
        keys10.put("REUSABLE_OBJECT_KEYS", REUSABLE_OBJECT_KEYS);

        KEYS.put("arazzo10", keys10);
    }

    private static final Pattern RFC3339_DATE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final int MAX_EXTENSION_ENTRIES = 20;

    // Holds extensions to a given classloader. Implemented as a least-recently used cache
    private static final Map<ClassLoader, List<JsonSchemaParserExtension>> jsonSchemaParserExtensionMap = new LinkedHashMap<ClassLoader, List<JsonSchemaParserExtension>>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ClassLoader, List<JsonSchemaParserExtension>> eldest) {
            return size() > MAX_EXTENSION_ENTRIES;
        }
    };

    private ArazzoSpecification.Components components;
    private JsonNode rootNode;
    private Map<String, Object> rootMap;
    private String basePath;
    private final Set<String> workflowIds = new HashSet<>();
    private Map<String, String> localSchemaRefs = new HashMap<>(); // TODO remove since no use case

    public ArazzoParseResult deserialize(final JsonNode node, final String path, final ArazzoParseOptions options) {
        basePath = path;
        rootNode = node;
        rootMap = new ObjectMapper().convertValue(rootNode, new TypeReference<>() {});
        ArazzoParseResult result = new ArazzoParseResult();
        try {
            ParseResult rootParseResult = new ParseResult();
            rootParseResult.setOaiAuthor(options.isOaiAuthor());
            rootParseResult.setInferSchemaType(options.isInferSchemaType());
            rootParseResult.setAllowEmptyStrings(options.isAllowEmptyStrings());
            rootParseResult.setValidateInternalRefs(options.isValidateInternalRefs());

            ArazzoSpecification arazzo = parseRoot(rootNode, rootParseResult, path);
            result.setArazzo(arazzo);
            result.setMessages(rootParseResult.getMessages());
        } catch (Exception e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                result.setMessages(List.of(e.getMessage()));
            } else {
                result.setMessages(List.of("Unexpected error de-serialising arazzo specification"));
            }
        }
        return result;
    }

    public ArazzoSpecification parseRoot(final JsonNode rootNode, final ParseResult parseResult, final String path) {
        String location = "";
        ArazzoSpecification arazzo = new ArazzoSpecification();
        if (JsonNodeType.OBJECT.equals(rootNode.getNodeType())) {
            ObjectNode node = (ObjectNode) rootNode;

            // arazzo version
            String value = getString("arazzo", node, true, location, parseResult);
            if (Objects.isNull(value) || !value.startsWith("1.0")) {
                return null;
            }
            arazzo.setArazzo(value);

            // info object (https://spec.openapis.org/arazzo/latest.html#info-object)
            ObjectNode infoObjNode = getObject("info", node, true, location, parseResult);
            if (Objects.nonNull(infoObjNode)) {
                ArazzoSpecification.Info info = getInfo(infoObjNode, "info", parseResult);
                arazzo.setInfo(info);
            }

            // list of source descriptions (https://spec.openapis.org/arazzo/latest.html#source-description-object)
            ArrayNode sourceDescriptionsArray = getArray("sourceDescriptions", node, true, location, parseResult);
            if (Objects.nonNull(sourceDescriptionsArray) && !sourceDescriptionsArray.isEmpty()) {
                arazzo.setSourceDescriptions(getSourceDescriptionList(sourceDescriptionsArray, String.format("%s.%s", location, "sourceDescriptions"), parseResult, path));
            }

            // workflows (https://spec.openapis.org/arazzo/latest.html#workflow-object)
            ArrayNode workflowsArray = getArray("workflows", node, true, location, parseResult);
            if (Objects.nonNull(workflowsArray) && !workflowsArray.isEmpty()) {
                arazzo.setWorkflows(getWorkflowList(workflowsArray, String.format("%s.%s", location, "workflows"), parseResult, path));
            }

            // components (https://spec.openapis.org/arazzo/latest.html#components-object)
            ObjectNode componentsObj = getObject("components", node, false, location, parseResult);
            if (Objects.nonNull(componentsObj)) {
                this.components = getComponents(componentsObj, "components", parseResult);
                arazzo.setComponents(components);
//                if(parseResult.validateInternalRefs) {
//                    /* TODO currently only capable of validating if ref is to root schema withing #/components/schemas
//                     * need to evaluate json pointer instead to also allow validation of nested schemas
//                     * e.g. #/components/schemas/foo/properties/bar
//                     */
//                    for (String schema : localSchemaRefs.keySet()) {
//                        if (components.getSchemas() == null){
//                            parseResult.missing(localSchemaRefs.get(schema), schema);
//                        } else if (components.getSchemas().get(schema) == null) {
//                            parseResult.invalidType(localSchemaRefs.get(schema), schema, "schema", rootNode);
//                        }
//                    }
//                }
            }

            Set<String> keys = getKeys(node);
            Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
            for (String key : keys) {
                if (!specKeys.get("ROOT_KEYS").contains(key) && !key.startsWith("x-")) {
                    parseResult.extra(location, key, node.get(key));
                }
                validateReservedKeywords(specKeys, key, location, parseResult);
            }

            // TODO reference handling
        } else {
            parseResult.invalidType(location, "arazzo", "object", rootNode);
            parseResult.invalid();
            return null;
        }
        return arazzo;
    }

    private ArazzoSpecification.Components getComponents(final ObjectNode rootNode,
                                                         final String location,
                                                         final ParseResult parseResult) {
        if (rootNode == null) {
            return null;
        }
        ArazzoSpecification.Components components = new ArazzoSpecification.Components();

        ObjectNode inputsObj = getObject("inputs", rootNode, false, location, parseResult);
        if (inputsObj != null) {
            components.setInputs(getSchemas(inputsObj, String.format("%s.%s", location, "inputs"), parseResult, true));
        }

        ObjectNode parametersObj = getObject("parameters", rootNode, false, location, parseResult);
        if (Objects.nonNull(parametersObj)) {
            components.setParameters(getParameters(parametersObj, location, parseResult, true));
        }

        ObjectNode successActionsObj = getObject("successActions", rootNode, false, location, parseResult);
        if (Objects.nonNull(successActionsObj)) {
            components.setSuccessActions(getSuccessActions(successActionsObj, location, parseResult, true));
        }

        ObjectNode failureActionsObj = getObject("failureActions", rootNode, false, location, parseResult);
        if (Objects.nonNull(failureActionsObj)) {
            components.setFailureActions(getFailureActions(failureActionsObj, location, parseResult, true));
        }

        Map<String, Object> extensions = getExtensions(rootNode);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            components.setExtensions(extensions);
        }

        Set<String> keys = getKeys(rootNode);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("COMPONENTS_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, rootNode.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return components;
    }

    private Map<String, FailureAction> getFailureActions(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult,
            boolean underComponents) {
        if (Objects.isNull(node)) {
            return null;
        }

        Map<String, FailureAction> failureActions = new LinkedHashMap<>();
        Set<String> filter = new HashSet<>();
        FailureAction failureAction = null;

        Set<String> failureActionKeys = getKeys(node);
        for (String failureActionKey : failureActionKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9\\.\\-_]+$",
                        failureActionKey)) {
                    parseResult.warning(location, "FailureAction name " + failureActionKey + " doesn't adhere to regular " +
                            "expression ^[a-zA-Z0-9\\.\\-_]+$");
                }
            }

            JsonNode failureActionValue = node.get(failureActionKey);
            if (JsonNodeType.OBJECT.equals(failureActionValue.getNodeType())) {
                ObjectNode failureActionObj = (ObjectNode) failureActionValue;
                if (Objects.nonNull(failureActionObj)) {
                    failureAction = getFailureAction(failureActionObj, String.format("%s.%s", location, failureActionValue), parseResult);
                    if (Objects.nonNull(failureAction)) {
                        failureActions.put(failureActionKey, failureAction);
                    }
                }
            }
        }
        return failureActions;
    }

    private FailureAction getFailureAction(final ObjectNode node, final String location, final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }

        FailureAction failureAction = FailureAction.builder().build();

        String name = getString("name", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(name))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name))) {
            failureAction.setName(name);
        }

        String type = getString("type", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(type))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(type))) {
            failureAction.setType(FailureAction.FailureActionType.valueOf(type.toUpperCase()));
        }

        // TODO multiple source description handling
        boolean workflowIdRequired = FailureAction.FailureActionType.GOTO.getValue().equalsIgnoreCase(type);
        String workflowId = getString("workflowId", node, workflowIdRequired, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId))) {
            failureAction.setWorkflowId(workflowId);
        }

        // TODO XOR constraints
        boolean stepIdRequired = FailureAction.FailureActionType.GOTO.getValue().equalsIgnoreCase(type);
        String stepId = getString("stepId", node, stepIdRequired, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(stepId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(stepId))) {
            failureAction.setStepId(stepId);
        }

        BigDecimal retryAfter = getBigDecimal("retryAfter", node, false, location, parseResult);
        if (Objects.nonNull(retryAfter)) {
            failureAction.setRetryAfter(retryAfter);
        }

        Integer retryLimit = getInteger("retryLimit", node, false, location, parseResult);
        if (Objects.nonNull(retryLimit)) {
            failureAction.setRetryLimit(retryLimit);
        }

        ArrayNode criteriaArray = getArray("criteria", node, false, location, parseResult);
        if (Objects.nonNull(criteriaArray) && !criteriaArray.isEmpty()) {
            failureAction.setCriteria(getCriteriaList(criteriaArray, location, parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            failureAction.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("FAILURE_ACTION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return failureAction;
    }

    private Map<String, SuccessAction> getSuccessActions(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult,
            boolean underComponents) {
        if (Objects.isNull(node)) {
            return null;
        }

        Map<String, SuccessAction> successActions = new LinkedHashMap<>();
        Set<String> filter = new HashSet<>();
        SuccessAction successAction = null;

        Set<String> successActionKeys = getKeys(node);
        for (String successActionKey : successActionKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9\\.\\-_]+$",
                        successActionKey)) {
                    parseResult.warning(location, "SuccessAction name " + successActionKey + " doesn't adhere to regular " +
                            "expression ^[a-zA-Z0-9\\.\\-_]+$");
                }
            }

            JsonNode successActionValue = node.get(successActionKey);
            if (JsonNodeType.OBJECT.equals(successActionValue.getNodeType())) {
                ObjectNode successActionObj = (ObjectNode) successActionValue;
                if (Objects.nonNull(successActionObj)) {
                    successAction = getSuccessAction(successActionObj, String.format("%s.%s", location, successActionValue), parseResult);
                    if (Objects.nonNull(successAction)) {
                        successActions.put(successActionKey, successAction);
                    }
                }
            }
        }
        return successActions;
    }

    private SuccessAction getSuccessAction(final ObjectNode node,
                                           final String location,
                                           final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }

        SuccessAction successAction = SuccessAction.builder().build();

        String name = getString("name", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(name))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name))) {
            successAction.setName(name);
        }

        String type = getString("type", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(type))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(type))) {
            successAction.setType(SuccessAction.SuccessActionType.valueOf(type.toUpperCase()));
        }

        // TODO multiple source description handling
        boolean workflowIdRequired = SuccessAction.SuccessActionType.GOTO.getValue().equalsIgnoreCase(type);
        String workflowId = getString("workflowId", node, workflowIdRequired, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId))) {
            successAction.setWorkflowId(workflowId);
        }

        // TODO XOR constraints
        boolean stepIdRequired = SuccessAction.SuccessActionType.GOTO.getValue().equalsIgnoreCase(type);
        String stepId = getString("stepId", node, stepIdRequired, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(stepId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(stepId))) {
            successAction.setStepId(stepId);
        }

        ArrayNode criteriaArray = getArray("criteria", node, false, location, parseResult);
        if (Objects.nonNull(criteriaArray) && !criteriaArray.isEmpty()) {
            successAction.setCriteria(getCriteriaList(criteriaArray, location, parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            successAction.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("SUCCESS_ACTION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return successAction;
    }

    private List<ArazzoSpecification.Workflow.Step.Criterion> getCriteriaList(
            final ArrayNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }

        List<ArazzoSpecification.Workflow.Step.Criterion> criteria = new ArrayList<>();
        ArazzoSpecification.Workflow.Step.Criterion criterion = null;

        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                criterion = getCriterion((ObjectNode) item, location, parseResult);
                if (Objects.nonNull(criterion)) {
                    criteria.add(criterion);
                }
            }
        }

        return criteria;
    }

    private ArazzoSpecification.Workflow.Step.Criterion getCriterion(final ObjectNode node,
                                                                     final String location,
                                                                     final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }

        ArazzoSpecification.Workflow.Step.Criterion criterion =
                ArazzoSpecification.Workflow.Step.Criterion.builder().build();

        String condition = getString("condition", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(condition)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(condition)) {
            criterion.setCondition(condition);
        }

        String context = getString("context", node, false, location, parseResult);
        if ( parseResult.isAllowEmptyStrings() && Objects.nonNull(context)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(context)) {
            criterion.setContext(context);
        }
        String typeAsString = getString("type", node, false, location, parseResult);
        if ( parseResult.isAllowEmptyStrings() && Objects.nonNull(context)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(context)) {
            ArazzoSpecification.Workflow.Step.Criterion.CriterionType type =
                    ArazzoSpecification.Workflow.Step.Criterion.CriterionType.valueOf(typeAsString.toUpperCase());
            criterion.setType(type);
        } else {
            criterion.setType(ArazzoSpecification.Workflow.Step.Criterion.CriterionType.SIMPLE);
        }

        // TODO operator validation
        // TODO context expression handling based on type

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            criterion.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("CRITERION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return criterion;
    }

    private Map<String, ArazzoSpecification.Workflow.Step.Parameter> getParameters(final ObjectNode obj, final String location, final ParseResult parseResult, final boolean underComponents) {
        if (Objects.isNull(obj)) {
            return null;
        }

        Map<String, ArazzoSpecification.Workflow.Step.Parameter> parameters = new LinkedHashMap<>();
        Set<String> filter = new HashSet<>();
        ArazzoSpecification.Workflow.Step.Parameter parameter = null;

        // TODO this check should be applied to other places where regex validation is required
        Set<String> parameterKeys = getKeys(obj);
        for (String parameterName : parameterKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9\\.\\-_]+$",
                        parameterName)) {
                    parseResult.warning(location, "Parameter name " + parameterName + " doesn't adhere to regular " +
                            "expression ^[a-zA-Z0-9\\.\\-_]+$");
                }
            }

            JsonNode parameterValue = obj.get(parameterName);
            if (JsonNodeType.OBJECT.equals(parameterValue.getNodeType())) {
                ObjectNode parameterObj = (ObjectNode) parameterValue;
                if (Objects.nonNull(parameterObj)) {
                    parameter = getParameter(parameterObj, String.format("%s.%s", location, parameterName), parseResult);
                    if (Objects.nonNull(parameter)) {
                        parameters.put(parameterName, parameter);
                    }
                }
            }
        }
        return parameters;
    }

    private ArazzoSpecification.Workflow.Step.Parameter getParameter(final ObjectNode obj, final String location, final ParseResult parseResult) {
        if (Objects.isNull(obj)) {
            return null;
        }

        ArazzoSpecification.Workflow.Step.Parameter parameter =
                ArazzoSpecification.Workflow.Step.Parameter.builder().build();

        String name = getString("name", obj, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(name)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name)) {
            // TODO add check for regex pattern and add a warning if not
            parameter.setName(name);
        }

        String in = getString("in", obj, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(in))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(in))) {
            // TODO add check for unsupported values and XOR constraints
            parameter.setIn(ArazzoSpecification.Workflow.Step.Parameter.ParameterEnum.valueOf(in.toUpperCase()));
        }

        // TODO this field is required and there is not check yet
        Object value = getAnyType("value", obj, location, parseResult);
        if (Objects.isNull(value)) {
            parseResult.missing(location, "value");
        } else {
            parameter.setValue(value);
        }

        Map<String, Object> extensions = getExtensions(obj);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            parameter.setExtensions(extensions);
        }

        Set<String> keys = getKeys(obj);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("PARAMETER_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, obj.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return parameter;
    }

    private List<ArazzoSpecification.Workflow> getWorkflowList(
            final ArrayNode node,
            final String location,
            final ParseResult parseResult,
            final String path) {
        List<ArazzoSpecification.Workflow> workflows = new ArrayList<>();
        if (Objects.isNull(node)) {
            return Collections.emptyList();
        }
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                ArazzoSpecification.Workflow workflow = getWorkflow((ObjectNode) item, location, parseResult, path);
                if (Objects.nonNull(workflow)) {
                    workflows.add(workflow);
                }
            }
        }
        return workflows;
    }

    private List<ArazzoSpecification.SourceDescription> getSourceDescriptionList(
            final ArrayNode node,
            final String location,
            final ParseResult parseResult,
            final String path) {
        if (Objects.isNull(node)) {
            return Collections.emptyList();
        }
        List<ArazzoSpecification.SourceDescription> sourceDescriptions = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                ArazzoSpecification.SourceDescription sourceDescription = getSourceDescription((ObjectNode) item, location, parseResult, path);
                if (Objects.nonNull(sourceDescription)) {
                    sourceDescriptions.add(sourceDescription);
                }
            }
        }
        return sourceDescriptions;
    }

    private ArazzoSpecification.Workflow getWorkflow(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult,
            final String path) {
        if (Objects.isNull(node)) {
            return null;
        }

        ArazzoSpecification.Workflow workflow = new ArazzoSpecification.Workflow();

        String workflowId = getString("workflowId", node, true, location, parseResult, workflowIds);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId)) {
            // TODO add check for regex pattern and add a warning if not
            workflow.setWorkflowId(workflowId);
        }

        String summary = getString("summary", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(summary)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(summary)) {
            workflow.setSummary(summary);
        }

        String description = getString("description", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(description)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(description)) {
            workflow.setDescription(description);
        }

        ObjectNode inputsObj = getObject("inputs", node, false, location, parseResult);
        if (inputsObj != null) {
            workflow.setInputs(getSchema(inputsObj, String.format("%s.%s", location, "inputs"), parseResult));
        }

        ArrayNode dependsOnArray = getArray("dependsOn", node, false, location, parseResult);
        if (Objects.nonNull(dependsOnArray) && !dependsOnArray.isEmpty()) {
            workflow.setDependsOn(getDependsOnList(dependsOnArray, String.format("%s.%s", location, "dependsOn"), parseResult, path));
        }

        ArrayNode stepsArray = getArray("steps", node, true, location, parseResult);
        if (Objects.nonNull(stepsArray) && !stepsArray.isEmpty()) {
            workflow.setSteps(getStepsList(stepsArray, String.format("%s.%s", location, "steps"), parseResult));
        }

        ArrayNode successActionArray = getArray("successActions", node, false, location, parseResult);
        if (Objects.nonNull(successActionArray) && !successActionArray.isEmpty()) {
            workflow.setSuccessActions(getSuccessActionList(successActionArray, String.format("%s.%s", location, "successActions"), parseResult));
        }

        ArrayNode failureActionArray = getArray("failureActions", node, false, location, parseResult);
        if (Objects.nonNull(failureActionArray) && !failureActionArray.isEmpty()) {
            workflow.setFailureActions(getFailureActionList(failureActionArray, String.format("%s.%s", location, "failureActions"), parseResult));
        }

        ObjectNode outputsObj = getObject("outputs", node, false, location, parseResult);
        if (Objects.nonNull(outputsObj)) {
            workflow.setOutputs(getOutputs(outputsObj, String.format("%s.%s", location, "outputs"), parseResult));
        }

        ArrayNode parameterArray = getArray("parameters", node, false, location, parseResult);
        if (Objects.nonNull(parameterArray) && !parameterArray.isEmpty()) {
            workflow.setParameters(getParameterList(parameterArray, String.format("%s.%s" , location, "parameters"), parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            workflow.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("WORKFLOW_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return workflow;
    }

    private List<ArazzoSpecification.Workflow.Step> getStepsList(final ArrayNode node,
                                                                 final String location,
                                                                 final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return Collections.emptyList();
        }

        ArrayList<ArazzoSpecification.Workflow.Step> steps = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                ArazzoSpecification.Workflow.Step step = getStep((ObjectNode) item, location, parseResult);
                if (Objects.nonNull(step)) {
                    steps.add(step);
                }
            }
        }

        return steps;
    }

    private ArazzoSpecification.Workflow.Step getStep(final ObjectNode node,
                                                      final String location,
                                                      final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }
        ArazzoSpecification.Workflow.Step step = ArazzoSpecification.Workflow.Step.builder().build();

        String description = getString("description", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(description)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(description)) {
            step.setDescription(description);
        }

        String stepId = getString("stepId", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(stepId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(stepId)) {
            // TODO regex validation
            step.setStepId(stepId);
        }

        String operationId = getString("operationId", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(operationId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(operationId)) {
            step.setOperationId(operationId);
        }

        String operationPath = getString("operationPath", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(operationPath)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(operationPath)) {
            step.setOperationPath(operationPath);
        }

        String workflowId = getString("workflowId", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId)) {
            step.setOperationPath(workflowId);
        }

        ArrayNode parameterArray = getArray("parameters", node, false, location, parseResult);
        if (Objects.nonNull(parameterArray) && !parameterArray.isEmpty()) {
            step.setParameters(getParameterList(parameterArray, String.format("%s.%s", location, "parameters"), parseResult));
        }

        ObjectNode requestBodyObj = getObject("requestBody", node, false, location, parseResult);
        if (Objects.nonNull(requestBodyObj)) {
            step.setRequestBody(getRequestBody(requestBodyObj, String.format("%s.%s", location, "requestBody"), parseResult));
        }

        ArrayNode successCriteriaArray = getArray("successCriteria", node, false, location, parseResult);
        if (Objects.nonNull(successCriteriaArray) && !successCriteriaArray.isEmpty()) {
            step.setSuccessCriteria(getCriteriaList(successCriteriaArray, location, parseResult));
        }

        ArrayNode onSuccessArray = getArray("onSuccess", node, false, location, parseResult);
        if (Objects.nonNull(onSuccessArray) && !onSuccessArray.isEmpty()) {
            step.setOnSuccess(getSuccessActionList(onSuccessArray, location, parseResult));
        }

        ArrayNode onFailureArray = getArray("onFailure", node, false, location, parseResult);
        if (Objects.nonNull(onFailureArray) && !onFailureArray.isEmpty()) {
            step.setOnFailure(getFailureActionList(onFailureArray, location, parseResult));
        }

        ObjectNode outputsObj = getObject("outputs", node, false, location, parseResult);
        if (Objects.nonNull(outputsObj)) {
            step.setOutputs(getOutputs(outputsObj, String.format("%s.%s", location, "outputs"), parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            step.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("STEP_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return step;
    }

    private Map<String, String> getOutputs(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult) {

        if (Objects.isNull(node)) {
            return Collections.emptyMap();
        }

        Map<String, String> outputs = new HashMap<>();

        if (JsonNodeType.OBJECT.equals(node.getNodeType())) {
            Set<String> keys = getKeys(node);
            for (String key : keys) {
                String expression = node.asText();
                outputs.put(key, expression);
            }
        } else {
            parseResult.invalidType(location, "outputs", "object", node);
            parseResult.invalid();
        }

        return outputs;
    }

    private List<FailureAction> getFailureActionList(
            final ArrayNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return Collections.emptyList();
        }

        List<FailureAction> failureActionList = new ArrayList<>();

        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                // TODO use this reusable object handling to other places where reusable objects are possible values
                if (item.has("reference")) {
                    ReusableObject reusableObject = getReusableObject((ObjectNode) item, location, parseResult);
                    // TODO resolve and add to result
                    parseResult.warning(String.format("%s.%s", location, "reusableObject"), "resolver not implemented");
                } else {
                    FailureAction failureAction = getFailureAction((ObjectNode) item, location, parseResult);
                    if (Objects.nonNull(failureAction)) {
                        failureActionList.add(failureAction);
                    }
                }
            }
        }

        return failureActionList;
    }

    private List<SuccessAction> getSuccessActionList(
            final ArrayNode node, final String location, final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return Collections.emptyList();
        }

        List<SuccessAction> successActionList = new ArrayList<>();

        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                // TODO use this reusable object handling to other places where reusable objects are possible values
                if (item.has("reference")) {
                    ReusableObject reusableObject = getReusableObject((ObjectNode) item, location, parseResult);
                    // TODO resolve and add to result
                    parseResult.warning(String.format("%s.%s", location, "reusableObject"), "resolver not implemented");
                } else {
                    SuccessAction successAction = getSuccessAction((ObjectNode) item, location, parseResult);
                    if (Objects.nonNull(successAction)) {
                        successActionList.add(successAction);
                    }
                }
            }
        }

        return successActionList;
    }

    private ReusableObject getReusableObject(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }
        ReusableObject reusableObject = ReusableObject.builder().build();

        String reference = getString("reference", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(reference)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(reference)) {
            reusableObject.setReference(reference);
        }

        String value = getString("value", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(value)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(value)) {
            reusableObject.setValue(value);
        }

        return reusableObject;
    }

    private ArazzoSpecification.Workflow.Step.RequestBody getRequestBody(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }
        ArazzoSpecification.Workflow.Step.RequestBody requestBody = ArazzoSpecification.Workflow.Step.RequestBody.builder().build();

        String contentType = getString("contentType", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(contentType)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(contentType)) {
            requestBody.setContentType(contentType);
        }

        JsonNode payloadNode = node.get("playload");
        if (Objects.nonNull(payloadNode)) {
            requestBody.setPayload(getPlayload(payloadNode, location, parseResult));
        }

        ArrayNode payloadReplacementObjectArray = getArray("replacements", node, false, location, parseResult);
        if (Objects.nonNull(payloadReplacementObjectArray)) {
            requestBody.setReplacements(getPayloadReplacementObjectList(payloadReplacementObjectArray, String.format("%s.%s", location, "payloadReplacementObject"), parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            requestBody.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("REQUEST_BODY_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return requestBody;
    }

    private Object getPlayload(
            final JsonNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isObject()) {
            return node;
        }
        parseResult.invalidType(location, "payload", "string|object", node);
        parseResult.invalid();
        return null;
    }

    private List<ArazzoSpecification.Workflow.Step.RequestBody.PayloadReplacementObject> getPayloadReplacementObjectList(
            final ArrayNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node) || node.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArazzoSpecification.Workflow.Step.RequestBody.PayloadReplacementObject> payloadReplacementObjects = new ArrayList<>();

        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                ArazzoSpecification.Workflow.Step.RequestBody.PayloadReplacementObject payloadReplacementObject = getPayloadReplacementObject((ObjectNode) item, location, parseResult);
                if (Objects.nonNull(payloadReplacementObject)) {
                    payloadReplacementObjects.add(payloadReplacementObject);
                }
            }
        }

        return payloadReplacementObjects;
    }

    private ArazzoSpecification.Workflow.Step.RequestBody.PayloadReplacementObject getPayloadReplacementObject(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult) {

        ArazzoSpecification.Workflow.Step.RequestBody.PayloadReplacementObject payloadReplacementObject = ArazzoSpecification.Workflow.Step.RequestBody.PayloadReplacementObject.builder().build();

        String target = getString("target", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(target)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(target)) {
            payloadReplacementObject.setTarget(target);
        }

        JsonNode valueNode = node.get("value");
        if (Objects.nonNull(valueNode)) {
            payloadReplacementObject.setValue(valueNode);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            payloadReplacementObject.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("PAYLOAD_REPLACEMENT_OBJECT_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return payloadReplacementObject;
    }

    private List<ArazzoSpecification.Workflow.Step.Parameter> getParameterList(
            final ArrayNode node,
            final String location,
            final ParseResult parseResult) {
        if (Objects.isNull(node)) {
            return Collections.emptyList();
        }
        List<ArazzoSpecification.Workflow.Step.Parameter> parameters = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                ArazzoSpecification.Workflow.Step.Parameter parameter = getParameter((ObjectNode) item, location, parseResult);
                if (Objects.nonNull(parameters)) {
                    parameters.add(parameter);
                }
            }
        }
        return parameters;
    }

    private List<String> getDependsOnList(final ArrayNode node, final String location, final ParseResult parseResult, final String path) {
        if (Objects.isNull(node)) {
            return null;
        }

        List<String> dependsOn = new ArrayList<>();

        for (JsonNode item : node) {
            if (JsonNodeType.STRING.equals(item.getNodeType())) {
                dependsOn.add(item.asText());
                // TODO validation that this value is a known workflowId
            }
        }
        return dependsOn;
    }

    private ArazzoSpecification.SourceDescription getSourceDescription(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult,
            final String path) {
        if (Objects.isNull(node)) {
            return null;
        }

        ArazzoSpecification.SourceDescription sourceDescription = new ArazzoSpecification.SourceDescription();

        String name = getString("name", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(name)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name)) {
            // TODO add check for regex pattern and add a warning if not
            sourceDescription.setName(name);
        }

        String typeAsString = getString("type", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(typeAsString))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(typeAsString))) {
            // TODO add check for unsupported values
            var type = ArazzoSpecification.SourceDescription.SourceDescriptionType.valueOf(typeAsString.toUpperCase());
            sourceDescription.setType(type);
        }

        String urlAsString = getString("url", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(urlAsString)) || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(urlAsString))) {
            if (!isValidURL(urlAsString) && Objects.nonNull(path)) {
                try {
                    final URI absURI = new URI(path.replaceAll("\\\\", "/"));
                    if ("http".equals(absURI.getScheme()) || "https".equals(absURI.getScheme())) {
                        urlAsString = absURI.resolve(new URI(urlAsString)).toString();
                    }
                } catch (URISyntaxException e) {
                    parseResult.warning(location, "invalid url: " + urlAsString);
                }
            }
            sourceDescription.setUrl(urlAsString);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            sourceDescription.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("SOURCE_DESCRIPTION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return sourceDescription;
    }

    private Map<String, Object> getExtensions(final ObjectNode node) {
        // It seems that the expanded content under the JSON format node is not parsed here,
        // Result in the expanded content of all nodes that are not resolved
        // So the expansion node is added here and the content under this node is parsed.
        Map<String, Object> extensions = tryDirectExtensions(node);
        if (extensions.isEmpty()) {
            extensions = tryUnwrapLookupExtensions(node);
        }
        return extensions;
    }

    private Map<String, Object> tryUnwrapLookupExtensions(final ObjectNode node) {
        Map<String, Object> extensions = new LinkedHashMap<>();

        JsonNode extensionsNode = node.get("extensions");
        if (Objects.nonNull(extensionsNode) && JsonNodeType.OBJECT.equals(node.getNodeType())) {
            ObjectNode extensionsObjectNode = (ObjectNode) extensionsNode;
            extensions.putAll(tryDirectExtensions(extensionsObjectNode));
        }

        return extensions;
    }

    private Map<String, Object> tryDirectExtensions(final ObjectNode node) {
        Map<String, Object> extensions = new LinkedHashMap<>();

        Set<String> keys = getKeys(node);
        for (String key : keys) {
            if (key.startsWith("x-")) {
                extensions.put(key, new ObjectMapper().convertValue(node.get(key), Object.class));
            }
        }

        return extensions;
    }

    private boolean isValidURL(final String urlAsString) {
        try {
            URL url = new URL(urlAsString);
            url.toURI();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public ArazzoSpecification.Info getInfo(
            final ObjectNode node,
            final String location,
            final ParseResult parseResult) {

        if (Objects.isNull(node)) {
            return null;
        }

        ArazzoSpecification.Info info = new ArazzoSpecification.Info();

        String value = getString("title", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && value != null) || (!parseResult.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            info.setTitle(value);
        }

        value = getString("description", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && value != null) || (!parseResult.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            info.setDescription(value);
        }

        value = getString("summary", node, false, location, parseResult);
        if (StringUtils.isNotBlank(value)) {
            info.setSummary(value);
        }

        value = getString("version", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && value != null) || (!parseResult.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            info.setVersion(value);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (Objects.nonNull(extensions) && !extensions.isEmpty()) {
            info.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("INFO_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return info;
    }

    public ArrayNode getArray(final String key,
                              final ObjectNode node,
                              final boolean required,
                              final String location,
                              final ParseResult result) {
        return getArray(key, node, required, location, result, false);
    }

    public ArrayNode getArray(
            final String key,
            final ObjectNode node,
            final boolean required,
            final String location,
            final ParseResult result,
            final boolean noInvalidError) {
        JsonNode value = node.get(key);
        ArrayNode arrayNode = null;
        if (value == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!value.getNodeType().equals(JsonNodeType.ARRAY)) {
            if (!noInvalidError) {
                result.invalidType(location, key, "array", value);
            }
        } else {
            arrayNode = (ArrayNode) value;
        }
        return arrayNode;
    }

    public Set<String> getKeys(final ObjectNode node) {
        Set<String> keys = new LinkedHashSet<>();
        if (node == null) {
            return keys;
        }

        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            keys.add(it.next());
        }

        return keys;
    }

    private void validateReservedKeywords(Map<String, Set<String>> specKeys, String key, String location, ParseResult result) {
        if (!result.isOaiAuthor() && specKeys.get("RESERVED_KEYWORDS").stream()
                .filter(key::startsWith)
                .findAny()
                .orElse(null) != null) {
            result.reserved(location, key);
        }
    }

    public ObjectNode getObject(final String key,
                                final ObjectNode node,
                                final boolean required,
                                final String location,
                                final ParseResult result) {
        JsonNode value = node.get(key);
        ObjectNode object = null;
        if (value == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!value.getNodeType().equals(JsonNodeType.OBJECT)) {
            result.invalidType(location, key, "object", value);
            if (required) {
                result.invalid();
            }
        } else {
            object = (ObjectNode) value;
        }
        return object;
    }

    private String getString(final String key,
                             final ObjectNode node,
                             final boolean required,
                             final String location,
                             final ParseResult parseResult) {
        return getString(key, node, required, location, parseResult, null);
    }

    private String getString(final String key,
                             final ObjectNode node,
                             final boolean required,
                             final String location,
                             final ParseResult parseResult,
                             final Set<String> uniqueValues) {
        return getString(key, node, required, location, parseResult, uniqueValues, false);
    }

    private String getString(final String key,
                             final ObjectNode node,
                             final boolean required,
                             final String location,
                             final ParseResult parseResult,
                             final Set<String> uniqueValues,
                             final boolean noInvalidError) {
        String value = null;
        JsonNode v = node.get(key);
        if (Objects.isNull(node) || Objects.isNull(v)) {
            if (required) {
                parseResult.missing(location, key);
                parseResult.invalid();
            }
        } else if (!v.isValueNode()) {
            if (!noInvalidError) {
                parseResult.invalidType(location, key, "string", node);
            }
        } else if (!v.isNull()) {
            value = v.asText();
            if (Objects.nonNull(uniqueValues) && !uniqueValues.add(value)) {
                parseResult.unique(location, "workflowId");
                parseResult.invalid();
            }
        }
        return value;
    }

    public static List<JsonSchemaParserExtension> getJsonSchemaParserExtensions() {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final List<JsonSchemaParserExtension> extensions = getJsonSchemaParserExtensions(tccl);
        final ClassLoader cl = JsonSchemaParserExtension.class.getClassLoader();
        if (cl != tccl) {
            extensions.addAll(getJsonSchemaParserExtensions(cl));
        }
        return extensions;
    }

    protected static List<JsonSchemaParserExtension> getJsonSchemaParserExtensions(ClassLoader cl) {
        if (jsonSchemaParserExtensionMap.containsKey(cl)) {
            return jsonSchemaParserExtensionMap.get(cl);
        }

        final List<JsonSchemaParserExtension> extensions = new ArrayList<>();
        final ServiceLoader<JsonSchemaParserExtension> loader = ServiceLoader.load(JsonSchemaParserExtension.class, cl);
        for (JsonSchemaParserExtension extension : loader) {
            extensions.add(extension);
        }

        // don't cache null-Value classLoader (e.g. Bootstrap Classloader)
        if (cl != null) {
            jsonSchemaParserExtensionMap.put(cl, extensions);
        }
        return extensions;
    }

    public String mungedRef(String refString) {
        // Ref: IETF RFC 3966, Section 5.2.2
        if (!refString.contains(":") &&   // No scheme
                !refString.startsWith("#") && // Path is not empty
                !refString.startsWith("/") && // Path is not absolute
                refString.indexOf(".") > 0) { // Path does not start with dot but contains "." (file extension)
            return "./" + refString;
        }
        return null;
    }

    public String inferTypeFromArray(ArrayNode an) {
        if (an.size() == 0) {
            return "string";
        }
        String type = null;
        for (int i = 0; i < an.size(); i++) {
            JsonNode element = an.get(0);
            if (element.isBoolean()) {
                if (type == null) {
                    type = "boolean";
                } else if (!"boolean".equals(type)) {
                    type = "string";
                }
            } else if (element.isNumber()) {
                if (type == null) {
                    type = "number";
                } else if (!"number".equals(type)) {
                    type = "string";
                }
            } else {
                type = "string";
            }
        }

        return type;
    }

    public Boolean getBoolean(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        Boolean value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else {
            if (v.getNodeType().equals(JsonNodeType.BOOLEAN)) {
                value = v.asBoolean();
            } else if (v.getNodeType().equals(JsonNodeType.STRING)) {
                String stringValue = v.textValue();
                return Boolean.parseBoolean(stringValue);
            }
        }
        return value;
    }

    public BigDecimal getBigDecimal(String key, ObjectNode node, boolean required, String location, ParseResult
            result) {
        BigDecimal value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (v.getNodeType().equals(JsonNodeType.NUMBER)) {
            value = new BigDecimal(v.asText());
        } else if (!v.isValueNode()) {
            result.invalidType(location, key, "double", node);
        }
        return value;
    }


    public Integer getInteger(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        Integer value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (v.getNodeType().equals(JsonNodeType.NUMBER)) {
            if (v.isInt()) {
                value = v.intValue();
            }
        } else if (!v.isValueNode()) {
            result.invalidType(location, key, "integer", node);
        }
        return value;
    }

    public Object getAnyType(String nodeKey, ObjectNode node, String location, ParseResult result) {
        JsonNode example = node.get(nodeKey);
        if (example != null) {
            if (example.getNodeType().equals(JsonNodeType.STRING)) {
                return getString(nodeKey, node, false, location, result);
            }
            if (example.getNodeType().equals(JsonNodeType.NUMBER)) {
                Integer integerExample = getInteger(nodeKey, node, false, location, result);
                if (integerExample != null) {
                    return integerExample;
                } else {
                    BigDecimal bigDecimalExample = getBigDecimal(nodeKey, node, false, location, result);
                    if (bigDecimalExample != null) {
                        return bigDecimalExample;
                    }
                }
            } else if (example.getNodeType().equals(JsonNodeType.OBJECT)) {
                ObjectNode objectValue = getObject(nodeKey, node, false, location, result);
                if (objectValue != null) {
                    return objectValue;
                }
            } else if (example.getNodeType().equals(JsonNodeType.ARRAY)) {
                ArrayNode arrayValue = getArray(nodeKey, node, false, location, result);
                if (arrayValue != null) {
                    return arrayValue;
                }
            } else if (example.getNodeType().equals(JsonNodeType.BOOLEAN)) {
                Boolean bool = getBoolean(nodeKey, node, false, location, result);
                if (bool != null) {
                    return bool;
                }
            } else if (example.getNodeType().equals(JsonNodeType.NULL)) {
                return example;
            }
        }
        return null;
    }


    public XML getXml(ObjectNode node, String location, ParseResult result) {
        if (node == null) {
            return null;
        }
        XML xml = new XML();

        String value = getString("name", node, false, String.format("%s.%s", location, "name"), result);
        if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            xml.setName(value);
        }

        value = getString("namespace", node, false, String.format("%s.%s", location, "namespace"), result);
        if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            xml.setNamespace(value);
        }

        value = getString("prefix", node, false, String.format("%s.%s", location, "prefix"), result);
        if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            xml.setPrefix(value);
        }

        Boolean attribute = getBoolean("attribute", node, false, location, result);
        if (attribute != null) {
            xml.setAttribute(attribute);
        }

        Boolean wrapped = getBoolean("wrapped", node, false, location, result);
        if (wrapped != null) {
            xml.setWrapped(wrapped);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (extensions != null && extensions.size() > 0) {
            xml.setExtensions(extensions);
        }


        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("XML_KEYS").contains(key) && !key.startsWith("x-")) {
                result.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, result);
        }


        return xml;

    }


    protected void getCommonSchemaFields(ObjectNode node, String location, ParseResult result, Schema schema) {
        String value = getString("title", node, false, location, result);
        if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            schema.setTitle(value);
        }

//        ObjectNode discriminatorNode = getObject("discriminator", node, false, location, result);
//        if (discriminatorNode != null) {
//            schema.setDiscriminator(getDiscriminator(discriminatorNode, location, result));
//        }

        BigDecimal bigDecimal = getBigDecimal("multipleOf", node, false, location, result);
        if (bigDecimal != null) {
            if (bigDecimal.compareTo(BigDecimal.ZERO) > 0) {
                schema.setMultipleOf(bigDecimal);
            } else {
                result.warning(location, "multipleOf value must be > 0");
            }
        }

        bigDecimal = getBigDecimal("maximum", node, false, location, result);
        if (bigDecimal != null) {
            schema.setMaximum(bigDecimal);
        }

        bigDecimal = getBigDecimal("minimum", node, false, location, result);
        if (bigDecimal != null) {
            schema.setMinimum(bigDecimal);
        }

        Integer integer = getInteger("minLength", node, false, location, result);
        if (integer != null) {
            schema.setMinLength(integer);
        }

        integer = getInteger("maxLength", node, false, location, result);
        if (integer != null) {
            schema.setMaxLength(integer);
        }

        String pattern = getString("pattern", node, false, location, result);
        if (result.isAllowEmptyStrings() && pattern != null) {
            schema.setPattern(pattern);
        }

        integer = getInteger("maxItems", node, false, location, result);
        if (integer != null) {
            schema.setMaxItems(integer);
        }
        integer = getInteger("minItems", node, false, location, result);
        if (integer != null) {
            schema.setMinItems(integer);
        }

        Boolean bool = getBoolean("uniqueItems", node, false, location, result);
        if (bool != null) {
            schema.setUniqueItems(bool);
        }

        integer = getInteger("maxProperties", node, false, location, result);
        if (integer != null) {
            schema.setMaxProperties(integer);
        }

        integer = getInteger("minProperties", node, false, location, result);
        if (integer != null) {
            schema.setMinProperties(integer);
        }

        ArrayNode required = getArray("required", node, false, location, result);
        if (required != null) {
            List<String> requiredList = new ArrayList<>();
            for (JsonNode n : required) {
                if (n.getNodeType().equals(JsonNodeType.STRING)) {
                    requiredList.add(((TextNode) n).textValue());
                } else {
                    result.invalidType(location, "required", "string", n);
                }
            }
            if (requiredList.size() > 0) {
                schema.setRequired(requiredList);
            }
        }

        value = getString("description", node, false, location, result);
        if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            schema.setDescription(value);
        }

        value = getString("format", node, false, location, result);
        if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            schema.setFormat(value);
        }

        bool = getBoolean("readOnly", node, false, location, result);
        if (bool != null) {
            schema.setReadOnly(bool);
        }

        bool = getBoolean("writeOnly", node, false, location, result);
        if (bool != null) {
            schema.setWriteOnly(bool);
        }

        bool =
                Optional.ofNullable(getBoolean("writeOnly", node, false, location, result)).orElse(false) && Optional.ofNullable(getBoolean("readOnly", node, false, location, result)).orElse(false);
        if (bool == true) {
            result.warning(location, " writeOnly and readOnly are both present");

        }

        ObjectNode xmlNode = getObject("xml", node, false, location, result);
        if (xmlNode != null) {
            XML xml = getXml(xmlNode, location, result);
            if (xml != null) {
                schema.setXml(xml);
            }
        }

//        ObjectNode externalDocs = getObject("externalDocs", node, false, location, result);
//        if (externalDocs != null) {
//            ExternalDocumentation docs = getExternalDocs(externalDocs, location, result);
//            if (docs != null) {
//                schema.setExternalDocs(docs);
//            }
//        }

        Object example = getAnyType("example", node, location, result);
        if (example != null) {
            schema.setExample(example instanceof NullNode ? null : example);
        }

        bool = getBoolean("deprecated", node, false, location, result);
        if (bool != null) {
            schema.setDeprecated(bool);
        }

    }

    private Object getDecodedObject(Schema schema, String objectString) throws ParseException {
        Object object =
                objectString == null ?
                        null :

                        "string".equals(schema.getType()) && "date".equals(schema.getFormat()) ?
                                toDate(objectString) :

                                "string".equals(schema.getType()) && "date-time".equals(schema.getFormat()) ?
                                        toDateTime(objectString) :

                                        "string".equals(schema.getType()) && "byte".equals(schema.getFormat()) ?
                                                toBytes(objectString) :

                                                objectString;

        if (object == null && objectString != null) {
            throw new ParseException(objectString, 0);
        }

        return object;
    }

    private OffsetDateTime toDateTime(String dateString) {

        OffsetDateTime dateTime = null;
        try {
            dateTime = OffsetDateTime.parse(dateString);
        } catch (Exception ignore) {
        }

        return dateTime;
    }

    /**
     * Returns the Date represented by the given RFC3339 full-date string.
     * Returns null if this string can't be parsed as Date.
     */
    private Date toDate(String dateString) {
        Matcher matcher = RFC3339_DATE_PATTERN.matcher(dateString);

        Date date = null;
        if (matcher.matches()) {
            String year = matcher.group(1);
            String month = matcher.group(2);
            String day = matcher.group(3);

            try {
                date =
                        new Calendar.Builder()
                                .setDate(Integer.parseInt(year), Integer.parseInt(month) - 1,
                                        Integer.parseInt(day))
                                .build()
                                .getTime();
            } catch (Exception ignore) {
            }
        }

        return date;
    }

    /**
     * Returns the byte array represented by the given base64-encoded string.
     * Returns null if this string is not a valid base64 encoding.
     */
    private byte[] toBytes(String byteString) {
        byte[] bytes;

        try {
            bytes = Base64.getDecoder().decode(byteString);
        } catch (Exception e) {
            bytes = null;
        }

        return bytes;
    }

    public Schema getSchema(JsonNode jsonNode, String location, ParseResult result) {
        if (jsonNode == null) {
            return null;
        }
        //Added to handle NPE from ResolverCache when Trying to dereference a schema
        if (result == null) {
            result = new ParseResult();
            result.setAllowEmptyStrings(true);
        }

        Schema schema = null;
        List<JsonSchemaParserExtension> jsonschemaExtensions = getJsonSchemaParserExtensions();

        for (JsonSchemaParserExtension jsonschemaExtension : jsonschemaExtensions) {
            schema = jsonschemaExtension.getSchema(jsonNode, location, result, rootMap, basePath);
            if (schema != null) {
                return schema;
            }
        }

        ObjectNode node = null;
        if (jsonNode.isObject()) {
            node = (ObjectNode) jsonNode;
        } else {
            result.invalidType(location, "", "object", jsonNode);
            return null;
        }
        ArrayNode oneOfArray = getArray("oneOf", node, false, location, result);
        ArrayNode allOfArray = getArray("allOf", node, false, location, result);
        ArrayNode anyOfArray = getArray("anyOf", node, false, location, result);
        ObjectNode itemsNode = getObject("items", node, false, location, result);

        if ((allOfArray != null) || (anyOfArray != null) || (oneOfArray != null)) {
            ComposedSchema composedSchema = new ComposedSchema();

            if (allOfArray != null) {

                for (JsonNode n : allOfArray) {
                    if (n.isObject()) {
                        schema = getSchema((ObjectNode) n, location, result);
                        composedSchema.addAllOfItem(schema);
                    }
                }
                schema = composedSchema;
            }
            if (anyOfArray != null) {

                for (JsonNode n : anyOfArray) {
                    if (n.isObject()) {
                        schema = getSchema((ObjectNode) n, location, result);
                        composedSchema.addAnyOfItem(schema);
                    }
                }
                schema = composedSchema;
            }
            if (oneOfArray != null) {

                for (JsonNode n : oneOfArray) {
                    if (n.isObject()) {
                        schema = getSchema((ObjectNode) n, location, result);
                        composedSchema.addOneOfItem(schema);
                    }
                }
                schema = composedSchema;
            }
        }

        if (itemsNode != null && result.isInferSchemaType()) {
            ArraySchema items = new ArraySchema();
            if (itemsNode.getNodeType().equals(JsonNodeType.OBJECT)) {
                items.setItems(getSchema(itemsNode, location, result));
            } else if (itemsNode.getNodeType().equals(JsonNodeType.ARRAY)) {
                for (JsonNode n : itemsNode) {
                    if (n.isValueNode()) {
                        items.setItems(getSchema(itemsNode, location, result));
                    }
                }
            }
            schema = items;
        } else if (itemsNode != null) {
            Schema items = new Schema();
            if (itemsNode.getNodeType().equals(JsonNodeType.OBJECT)) {
                items.setItems(getSchema(itemsNode, location, result));
            } else if (itemsNode.getNodeType().equals(JsonNodeType.ARRAY)) {
                for (JsonNode n : itemsNode) {
                    if (n.isValueNode()) {
                        items.setItems(getSchema(itemsNode, location, result));
                    }
                }
            }
            schema = items;
        }

        Boolean additionalPropertiesBoolean = getBoolean("additionalProperties", node, false, location, result);

        ObjectNode additionalPropertiesObject =
                additionalPropertiesBoolean == null
                        ? getObject("additionalProperties", node, false, location, result)
                        : null;

        Object additionalProperties =
                additionalPropertiesObject != null
                        ? getSchema(additionalPropertiesObject, location, result)
                        : additionalPropertiesBoolean;

        if (additionalProperties != null && result.isInferSchemaType()) {
            if (schema == null) {
                schema =
                        additionalProperties.equals(Boolean.FALSE)
                                ? new ObjectSchema()
                                : new MapSchema();
            }
            schema.setAdditionalProperties(additionalProperties);
        } else if (additionalProperties != null) {
            if (schema == null) {
                schema = new Schema();
            }
            schema.setAdditionalProperties(additionalProperties);
        }

        if (schema == null) {
            schema = SchemaTypeUtil.createSchemaByType(node);
        }

        JsonNode ref = node.get("$ref");
        if (ref != null) {
            if (ref.getNodeType().equals(JsonNodeType.STRING)) {

                if (location.startsWith("paths")) {
                    try {
                        String components[] = ref.asText().split("#/components");
                        if ((ref.asText().startsWith("#/components")) && (components.length > 1)) {
                            String[] childComponents = components[1].split("/");
                            String[] newChildComponents = Arrays.copyOfRange(childComponents, 1,
                                    childComponents.length);
                            boolean isValidComponent = ReferenceValidator.valueOf(newChildComponents[0])
                                    .validateComponent(this.components,
                                            newChildComponents[1]);
                            if (!isValidComponent) {
                                result.missing(location, ref.asText());
                            }
                        }
                    } catch (Exception e) {
                        result.missing(location, ref.asText());
                    }
                }

                String mungedRef = mungedRef(ref.textValue());
                if (mungedRef != null) {
                    schema.set$ref(mungedRef);
                } else {
                    schema.set$ref(ref.asText());
                }
                /* TODO currently only capable of validating if ref is to root schema withing #/components/schemas
                 * need to evaluate json pointer instead to also allow validation of nested schemas
                 * e.g. #/components/schemas/foo/properties/bar
                 */
                if (schema.get$ref().startsWith("#/components/schemas") && StringUtils.countMatches(schema.get$ref(), "/") == 3) {
                    String refName = schema.get$ref().substring(schema.get$ref().lastIndexOf("/") + 1);
                    localSchemaRefs.put(refName, location);
                }
                if (ref.textValue().startsWith("#/components") && !(ref.textValue().startsWith("#/components/schemas"))) {
                    result.warning(location, "$ref target " + ref.textValue() + " is not of expected type Schema");
                }
                return schema;
            } else {
                result.invalidType(location, "$ref", "string", node);
                return null;
            }
        }


        getCommonSchemaFields(node, location, result, schema);
        String value;
        Boolean bool;

        bool = getBoolean("exclusiveMaximum", node, false, location, result);
        if (bool != null) {
            schema.setExclusiveMaximum(bool);
        }

        bool = getBoolean("exclusiveMinimum", node, false, location, result);
        if (bool != null) {
            schema.setExclusiveMinimum(bool);
        }


        ArrayNode enumArray = getArray("enum", node, false, location, result);
        if (enumArray != null) {
            for (JsonNode n : enumArray) {
                if (n.isNumber()) {
                    schema.addEnumItemObject(n.numberValue());
                } else if (n.isBoolean()) {
                    schema.addEnumItemObject(n.booleanValue());
                } else if (n.isValueNode()) {
                    try {
                        schema.addEnumItemObject(getDecodedObject(schema, n.asText(null)));
                    } catch (ParseException e) {
                        result.invalidType(location, String.format("enum=`%s`", e.getMessage()),
                                schema.getFormat(), n);
                    }
                } else if (n.isContainerNode()) {
                    schema.addEnumItemObject(n.isNull() ? null : n);
                } else {
                    result.invalidType(location, "enum", "value", n);
                }
            }
        }

        value = getString("type", node, false, location, result);
        if (StringUtils.isBlank(schema.getType())) {
            if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
                schema.setType(value);
            } else if (result.isInferSchemaType()) {
                // may have an enum where type can be inferred
                JsonNode enumNode = node.get("enum");
                if (enumNode != null && enumNode.isArray()) {
                    String type = inferTypeFromArray((ArrayNode) enumNode);
                    schema.setType(type);
                }
            }
            if ("array".equals(schema.getType()) && schema.getItems() == null) {
                result.missing(location, "items");
            }
        }

        ObjectNode notObj = getObject("not", node, false, location, result);
        if (notObj != null) {
            Schema not = getSchema(notObj, location, result);
            if (not != null) {
                schema.setNot(not);
            }
        }


        Map<String, Schema> properties = new LinkedHashMap<>();
        ObjectNode propertiesObj = getObject("properties", node, false, location, result);
        Schema property = null;

        Set<String> keys = getKeys(propertiesObj);
        for (String name : keys) {
            JsonNode propertyValue = propertiesObj.get(name);
            if (!propertyValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, "properties", "object", propertyValue);
            } else {
                if (propertiesObj != null) {
                    property = getSchema((ObjectNode) propertyValue, location, result);
                    if (property != null) {
                        properties.put(name, property);
                    }
                }
            }
        }
        if (propertiesObj != null) {
            schema.setProperties(properties);
        }

        //sets default value according to the schema type
        if (node.get("default") != null && result.isInferSchemaType()) {
            if (!StringUtils.isBlank(schema.getType())) {
                if (schema.getType().equals("array")) {
                    ArrayNode array = getArray("default", node, false, location, result);
                    if (array != null) {
                        schema.setDefault(array);
                    }
                } else if (schema.getType().equals("string")) {
                    value = getString("default", node, false, location, result);
                    if ((result.isAllowEmptyStrings() && value != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
                        try {
                            schema.setDefault(getDecodedObject(schema, value));
                        } catch (ParseException e) {
                            result.invalidType(location, String.format("default=`%s`", e.getMessage()),
                                    schema.getFormat(), node);
                        }
                    }
                } else if (schema.getType().equals("boolean")) {
                    bool = getBoolean("default", node, false, location, result);
                    if (bool != null) {
                        schema.setDefault(bool);
                    }
                } else if (schema.getType().equals("object")) {
                    Object object = getObject("default", node, false, location, result);
                    if (object != null) {
                        schema.setDefault(object);
                    }
                } else if (schema.getType().equals("integer")) {
                    Integer number = getInteger("default", node, false, location, result);
                    if (number != null) {
                        schema.setDefault(number);
                    }
                } else if (schema.getType().equals("number")) {
                    BigDecimal number = getBigDecimal("default", node, false, location, result);
                    if (number != null) {
                        schema.setDefault(number);
                    }
                }
            } else {
                Object defaultObject = getAnyType("default", node, location, result);
                if (defaultObject != null) {
                    schema.setDefault(defaultObject);
                }
            }
        } else if (node.get("default") != null) {
            Object defaultObject = getAnyType("default", node, location, result);
            if (defaultObject != null) {
                schema.setDefault(defaultObject);
            }
        } else {
            schema.setDefault(null);
        }

        bool = getBoolean("nullable", node, false, location, result);
        if (bool != null) {
            schema.setNullable(bool);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (extensions != null && extensions.size() > 0) {
            schema.setExtensions(extensions);
        }

        Set<String> schemaKeys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : schemaKeys) {
            if (!specKeys.get("SCHEMA_KEYS").contains(key) && !key.startsWith("x-")) {
                result.extra(location, key, node.get(key));
            }
        }
        return schema;
    }

    public Map<String, Schema<?>> getSchemas(ObjectNode obj, String location, ParseResult result,
                                             boolean underComponents) {
        if (obj == null) {
            return null;
        }
        Map<String, Schema<?>> schemas = new LinkedHashMap<>();

        Set<String> schemaKeys = getKeys(obj);
        for (String schemaName : schemaKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9\\.\\-_]+$",
                        schemaName)) {
                    result.warning(location, "Schema name " + schemaName + " doesn't adhere to regular expression " +
                            "^[a-zA-Z0-9\\.\\-_]+$");
                }
            }
            JsonNode schemaValue = obj.get(schemaName);
            if (!schemaValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, schemaName, "object", schemaValue);
            } else {
                ObjectNode schema = (ObjectNode) schemaValue;
                Schema schemaObj = getSchema(schema, String.format("%s.%s", location, schemaName), result);
                if (schemaObj != null) {
                    schemas.put(schemaName, schemaObj);
                }
            }
        }

        return schemas;
    }

    public static class ParseResult {
        private boolean valid = true;
        private Map<Location, JsonNode> extra = new LinkedHashMap<>();
        private Map<Location, JsonNode> unsupported = new LinkedHashMap<>();
        private Map<Location, String> invalidType = new LinkedHashMap<>();
        private List<Location> missing = new ArrayList<>();
        private List<Location> warnings = new ArrayList<>();
        private List<Location> unique = new ArrayList<>();
        private List<Location> uniqueTags = new ArrayList<>();
        private boolean allowEmptyStrings = true;
        private List<Location> reserved = new ArrayList<>();
        private boolean validateInternalRefs;

        private boolean inferSchemaType = true;
        private boolean oaiAuthor = false;

        public boolean isInferSchemaType() {
            return inferSchemaType;
        }

        public void setInferSchemaType(boolean inferSchemaType) {
            this.inferSchemaType = inferSchemaType;
        }

        public ParseResult inferSchemaType(boolean inferSchemaType) {
            this.inferSchemaType = inferSchemaType;
            return this;
        }

        public ParseResult() {
        }

        public boolean isAllowEmptyStrings() {
            return this.allowEmptyStrings;
        }

        public void setAllowEmptyStrings(boolean allowEmptyStrings) {
            this.allowEmptyStrings = allowEmptyStrings;
        }

        public ParseResult allowEmptyStrings(boolean allowEmptyStrings) {
            this.allowEmptyStrings = allowEmptyStrings;
            return this;
        }

        public void unsupported(String location, String key, JsonNode value) {
            unsupported.put(new Location(location, key), value);
        }

        public void reserved(String location, String key) {
            reserved.add(new Location(location, key));
        }

        public void extra(String location, String key, JsonNode value) {
            extra.put(new Location(location, key), value);
        }

        public void missing(String location, String key) {
            missing.add(new Location(location, key));
        }

        public void warning(String location, String key) {
            warnings.add(new Location(location, key));
        }

        public void unique(String location, String key) {
            unique.add(new Location(location, key));
        }

        public void uniqueTags(String location, String key) {
            uniqueTags.add(new Location(location, key));
        }

        public void invalidType(String location, String key, String expectedType, JsonNode value) {
            invalidType.put(new Location(location, key), expectedType);
        }

        public void invalid() {
            this.valid = false;
        }

        public boolean isValid() {
            return this.valid;
        }

        public boolean isOaiAuthor() {
            return this.oaiAuthor;
        }

        public void setOaiAuthor(boolean oaiAuthor) {
            this.oaiAuthor = oaiAuthor;
        }

        public ParseResult oaiAuthor(boolean oaiAuthor) {
            this.oaiAuthor = oaiAuthor;
            return this;
        }

        public List<String> getMessages() {
            List<String> messages = new ArrayList<String>();
            for (Location l : extra.keySet()) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is unexpected";
                messages.add(message);
            }
            for (Location l : invalidType.keySet()) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is not of type `" + invalidType.get(l) + "`";
                messages.add(message);
            }
            for (Location l : missing) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is missing";
                messages.add(message);
            }
            for (Location l : warnings) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = location + l.key;
                messages.add(message);
            }
            for (Location l : unsupported.keySet()) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is unsupported";
                messages.add(message);
            }
            for (Location l : unique) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is repeated";
                messages.add(message);
            }
            for (Location l : uniqueTags) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is repeated";
                messages.add(message);
            }
            for (Location l : reserved) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is reserved by The OpenAPI Initiative";
                messages.add(message);
            }
            return messages;
        }

        public void setValidateInternalRefs(boolean validateInternalRefs) {
            this.validateInternalRefs = validateInternalRefs;
        }

        public boolean isValidateInternalRefs() {
            return validateInternalRefs;
        }
    }

    protected static class Location {
        private String location;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Location)) return false;

            Location location1 = (Location) o;

            if (location != null ? !location.equals(location1.location) : location1.location != null) return false;
            return !(key != null ? !key.equals(location1.key) : location1.key != null);

        }

        @Override
        public int hashCode() {
            int result = location != null ? location.hashCode() : 0;
            result = 31 * result + (key != null ? key.hashCode() : 0);
            return result;
        }

        private String key;

        public Location(String location, String key) {
            this.location = location;
            this.key = key;
        }
    }

    public interface JsonSchemaParserExtension {
        Schema getSchema(JsonNode node, String location, ParseResult result, Map<String, Object> rootMap, String basePath);

        boolean resolveSchema(Schema schema, ResolverCache cache, ArazzoSpecification arazzo);
    }

    public enum ReferenceValidator {
        inputs {
            @Override
            public boolean validateComponent(ArazzoSpecification.Components components, String reference) {
                return components.getInputs().containsKey(reference);
            }
        },
        parameters {
            @Override
            public boolean validateComponent(ArazzoSpecification.Components components, String reference) {
                return components.getParameters().containsKey(reference);
            }
        },
        failureActions {
            @Override
            public boolean validateComponent(ArazzoSpecification.Components components, String reference) {
                return components.getFailureActions().containsKey(reference);
            }
        },
        successActions {
            @Override
            public boolean validateComponent(ArazzoSpecification.Components components, String reference) {
                return components.getSuccessActions().containsKey(reference);
            }
        };
        public abstract boolean validateComponent(ArazzoSpecification.Components components, String reference);
    }
}
