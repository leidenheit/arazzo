package de.leidenheit;

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

public class ArazzoDeserializer {

    protected static Set<String> JSON_SCHEMA_2020_12_TYPES = new LinkedHashSet<>(List.of(
            "null", "boolean", "object", "array", "number", "string", "integer"));

    protected static Set<String> RESERVED_KEYWORDS = new LinkedHashSet<>(List.of());

    protected static Set<String> ROOT_KEYS = new LinkedHashSet<>(List.of(
            "arazzo", "info", "sourceDescriptions", "workflows", "components", "extensions"
    ));
    protected static Set<String> INFO_KEYS = new LinkedHashSet<>(List.of(
            "title", "summary", "description", "version", "extensions"
    ));
    protected static Set<String> SOURCE_DESCRIPTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "url", "type", "extensions"
    ));
    protected static Set<String> WORKFLOW_KEYS = new LinkedHashSet<>(List.of(
            "workflowId", "summary", "description", "inputs",
            "dependsOn", "steps", "successActions" , "failureActions" ,
            "outputs", "parameters", "extensions"
    ));
    protected static Set<String> STEP_KEYS = new LinkedHashSet<>(List.of(
            "description", "stepId", "operationId", "operationPath",
            "workflowId", "parameters", "requestBody", "successCriteria",
            "onSuccess", "onFailure", "outputs", "extensions"
    ));
    protected static Set<String> SCHEMA_KEYS = new LinkedHashSet<>(List.of(
            "$ref", "title", "multipleOf", "maximum", "format", "exclusiveMaximum",
            "minimum", "exclusiveMinimum", "maxLength", "minLength", "pattern", "maxItems",
            "minItems", "uniqueItems", "maxProperties", "minProperties", "required", "enum",
            "type", "allOf", "oneOf", "anyOf", "not", "items", "properties", "additionalProperties",
            "description", "default", "nullable", "discriminator", "readOnly", "writeOnly", "xml",
            "externalDocs", "example", "deprecated"
    ));
    protected static Set<String> XML_KEYS = new LinkedHashSet<>(List.of(
            "attribute", "wrapped"
    ));
    protected static Set<String> COMPONENTS_KEYS = new LinkedHashSet<>(List.of(
            "inputs", "parameters", "successActions", "failureActions", "extensions"
    ));
    protected static Set<String> PARAMETER_KEYS = new LinkedHashSet<>(List.of(
            "name", "in", "value", "extensions"
    ));
    protected static Set<String> SUCCESS_ACTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "type", "workflowId", "stepId", "criteria", "extensions"
    ));
    protected static Set<String> FAILURE_ACTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "type", "workflowId", "stepId", "criteria", "retryAfter", "retryLimit", "extensions"
    ));
    protected static Set<String> CRITERION_KEYS = new LinkedHashSet<>(List.of(
            "context", "condition", "type", "extensions"
    ));
    protected static Set<String> REQUEST_BODY_KEYS = new LinkedHashSet<>(List.of(
            "contentType", "payload", "replacements", "extensions"
    ));
    protected static Set<String> PAYLOAD_REPLACEMENT_OBJECT_KEYS = new LinkedHashSet<>(List.of(
            "target", "value", "extensions"
    ));
    protected static Set<String> REUSABLE_OBJECT_KEYS = new LinkedHashSet<>(List.of(
            "reference", "value"
    ));
    // TODO others

    protected static Map<String, Map<String, Set<String>>> KEYS = new LinkedHashMap<>();

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
        keys10.put("XML_KEYS", XML_KEYS);
        keys10.put("COMPONENTS_KEYS", COMPONENTS_KEYS);
        keys10.put("PARAMETER_KEYS", PARAMETER_KEYS);
        keys10.put("SUCCESS_ACTION_KEYS", SUCCESS_ACTION_KEYS);
        keys10.put("FAILURE_ACTION_KEYS", FAILURE_ACTION_KEYS);
        keys10.put("CRITERION_KEYS", CRITERION_KEYS);
        keys10.put("REQUEST_BODY_KEYS", REQUEST_BODY_KEYS);
        keys10.put("PAYLOAD_REPLACEMENT_OBJECT_KEYS", PAYLOAD_REPLACEMENT_OBJECT_KEYS);
        keys10.put("REUSABLE_OBJECT_KEYS", REUSABLE_OBJECT_KEYS);
        // TODO others

        KEYS.put("arazzo10", keys10);
    }

    private static final Pattern RFC3339_DATE_TIME_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):" +
            "(\\d{2}):(\\d{2})(\\.\\d+)?((Z)|([+-]\\d{2}:\\d{2}))$");
    private static final Pattern RFC3339_DATE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final String REFERENCE_SEPARATOR = "#/";

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
    private Map<String, String> localSchemaRefs = new HashMap<>();

    public ArazzoParseResult deserialize(final JsonNode node, final String path, final ArazzoParseOptions options) {
        basePath = path;
        rootNode = node;
        rootMap = new ObjectMapper().convertValue(rootNode, Map.class);
        ArazzoParseResult result = new ArazzoParseResult();
        try {
            ParseResult rootParseResult = new ParseResult();
            // TODO introduce those options to Arazzo Parse Options
            rootParseResult.setOaiAuthor(false);
            rootParseResult.setInferSchemaType(false);
            rootParseResult.setAllowEmptyStrings(false);
            rootParseResult.setValidateInternalRefs(false);

            ArazzoSpecification arazzo = parseRoot(rootNode, rootParseResult, path);
            result.setArazzo(arazzo);
            result.setMessages(rootParseResult.getMessages());
        } catch (Exception e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                result.setMessages(List.of(e.getMessage()));
            } else {
                result.setMessages(List.of("Unexpected error deserialising arazzo specification"));
            }
        }
        return result;
    }

    public ArazzoSpecification parseRoot(final JsonNode node, final ParseResult parseResult, final String path) {

        // TODO remove: its just for lookup
        // OpenAPIDeserializer openAPIDeserializer;

        String location = "";
        ArazzoSpecification arazzo = new ArazzoSpecification();
        if (node.getNodeType().equals(JsonNodeType.OBJECT)) {
            ObjectNode rootNode = (ObjectNode) node;

            // arazzo version
            String value = getString("arazzo", rootNode, true, location, parseResult);
            if (Objects.isNull(value) || !value.startsWith("1.0")) {
                return null;
            }
            arazzo.setArazzo(value);

            // info object (https://spec.openapis.org/arazzo/latest.html#info-object)
            ObjectNode infoObjNode = getObject("info", rootNode, true, location, parseResult);
            if (Objects.nonNull(infoObjNode)) {
                ArazzoSpecification.Info info = getInfo(infoObjNode, "info", parseResult);
                arazzo.setInfo(info);
            }

            // list of source descriptions (https://spec.openapis.org/arazzo/latest.html#source-description-object)
            ArrayNode sourceDescriptionsArray = getArray("sourceDescriptions", rootNode, true, location, parseResult);
            if (Objects.nonNull(sourceDescriptionsArray) && !sourceDescriptionsArray.isEmpty()) {
                arazzo.setSourceDescriptions(getSourceDescriptionList(sourceDescriptionsArray, String.format("%s.%s", location, "sourceDescriptions"), parseResult, path));
            }

            // workflows (https://spec.openapis.org/arazzo/latest.html#workflow-object)
            ArrayNode workflowsArray = getArray("workflows", rootNode, true, location, parseResult);
            if (Objects.nonNull(workflowsArray) && !workflowsArray.isEmpty()) {
                arazzo.setWorkflows(getWorkflowList(workflowsArray, String.format("%s.%s", location, "workflows"), parseResult, path));
            }

            // components (https://spec.openapis.org/arazzo/latest.html#components-object)
            ObjectNode componentsObj = getObject("components", rootNode, false, location, parseResult);
            if (Objects.nonNull(componentsObj)) {
                this.components = getComponents(componentsObj, "components", parseResult, path);
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

            Set<String> keys = getKeys(rootNode);
            Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
            for (String key : keys) {
                if (!specKeys.get("ROOT_KEYS").contains(key) && !key.startsWith("x-")) {
                    parseResult.extra(location, key, node.get(key));
                }
                validateReservedKeywords(specKeys, key, location, parseResult);
            }

            // TODO reference handling
        } else {
            parseResult.invalidType(location, "arazzo", "object", node);
            parseResult.invalid();
            return null;
        }
        return arazzo;
    }

    private ArazzoSpecification.Components getComponents(final ObjectNode rootNode,
                                                         final String location,
                                                         final ParseResult parseResult,
                                                         final String path) {
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

        // TODO successActions
        // TODO failureActions
        // TODO outputs
        // TODO parameters

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

    public JsonNode getObjectOrBoolean(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        JsonNode value = node.get(key);

        if (value == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
            return null;
        }
        Boolean boolValue = null;
        if (value.getNodeType().equals(JsonNodeType.BOOLEAN)) {
            boolValue = value.asBoolean();
        } else if (value.getNodeType().equals(JsonNodeType.STRING)) {
            String stringValue = value.textValue();
            if ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue)) {
                boolValue = Boolean.parseBoolean(stringValue);
            } else {
                result.invalidType(location, key, "object", value);
                return null;
            }
        }
        if (boolValue != null) {
            return value;
        }
        if (!value.isObject()) {
            result.invalidType(location, key, "object", value);
            return null;
        }

        return value;
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

//    public Schema getJsonSchema(JsonNode jsonNode, String location, ParseResult result) {
//        if (jsonNode == null) {
//            return null;
//        }
//        Schema schema = null;
//        Boolean boolValue = null;
//        if (jsonNode.getNodeType().equals(JsonNodeType.BOOLEAN)) {
//            boolValue = jsonNode.asBoolean();
//        } else if (jsonNode.getNodeType().equals(JsonNodeType.STRING)) {
//            String stringValue = jsonNode.textValue();
//            if ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue)) {
//                boolValue = Boolean.parseBoolean(stringValue);
//            } else {
//                result.invalidType(location, "", "object", jsonNode);
//                return null;
//            }
//        }
//        if (boolValue != null) {
//            return new JsonSchema().booleanSchemaValue(boolValue);
//        }
//        ObjectNode node = null;
//        if (jsonNode.isObject()) {
//            node = (ObjectNode) jsonNode;
//        } else {
//            result.invalidType(location, "", "object", jsonNode);
//            return null;
//        }
//        ArrayNode oneOfArray = getArray("oneOf", node, false, location, result);
//        ArrayNode allOfArray = getArray("allOf", node, false, location, result);
//        ArrayNode anyOfArray = getArray("anyOf", node, false, location, result);
//        JsonNode itemsNode = getObjectOrBoolean("items", node, false, location, result);
//
//        if ((allOfArray != null) || (anyOfArray != null) || (oneOfArray != null)) {
//            JsonSchema composedSchema = new JsonSchema();
//
//            if (allOfArray != null) {
//
//                for (JsonNode n : allOfArray) {
//                    schema = getJsonSchema(n, location, result);
//                    composedSchema.addAllOfItem(schema);
//                }
//                schema = composedSchema;
//            }
//            if (anyOfArray != null) {
//
//                for (JsonNode n : anyOfArray) {
//                    schema = getJsonSchema(n, location, result);
//                    composedSchema.addAnyOfItem(schema);
//                }
//                schema = composedSchema;
//            }
//            if (oneOfArray != null) {
//
//                for (JsonNode n : oneOfArray) {
//                    schema = getJsonSchema(n, location, result);
//                    composedSchema.addOneOfItem(schema);
//                }
//                schema = composedSchema;
//            }
//        }
//        if (itemsNode != null) {
//            Schema items = new JsonSchema();
//            items.setItems(getJsonSchema(itemsNode, location, result));
//            schema = items;
//        }
//        JsonNode additionalProperties = getObjectOrBoolean("additionalProperties", node, false, location, result);
//        if (additionalProperties != null) {
//            Schema additionalPropertiesSchema = getJsonSchema(additionalProperties, location, result);
//            if (schema == null) {
//                schema = new JsonSchema();
//            }
//            schema.setAdditionalProperties(additionalPropertiesSchema);
//        }
//
//        JsonNode unevaluatedProperties = getObjectOrBoolean("unevaluatedProperties", node, false, location, result);
//        if (unevaluatedProperties != null) {
//            Schema unevaluatedPropertiesSchema = getJsonSchema(unevaluatedProperties, location, result);
//            if (schema == null) {
//                schema = new JsonSchema();
//            }
//            schema.setUnevaluatedProperties(unevaluatedPropertiesSchema);
//        }
//
//        if (schema == null) {
//            schema = new JsonSchema();
//        }
//
//        JsonNode ref = node.get("$ref");
//        if (ref != null) {
//            if (ref.getNodeType().equals(JsonNodeType.STRING)) {
//
//                if (location.startsWith("paths")) {
//                    try {
//                        String components[] = ref.asText().split("#/components");
//                        if ((ref.asText().startsWith("#/components")) && (components.length > 1)) {
//                            String[] childComponents = components[1].split("/");
//                            String[] newChildComponents = Arrays.copyOfRange(childComponents, 1,
//                                    childComponents.length);
//                            boolean isValidComponent = ReferenceValidator.valueOf(newChildComponents[0])
//                                    .validateComponent(this.components,
//                                            newChildComponents[1]);
//                            if (!isValidComponent) {
//                                result.missing(location, ref.asText());
//                            }
//                        }
//                    } catch (Exception e) {
//                        result.missing(location, ref.asText());
//                    }
//                }
//
//                String mungedRef = mungedRef(ref.textValue());
//                if (mungedRef != null) {
//                    schema.set$ref(mungedRef);
//                } else {
//                    schema.set$ref(ref.asText());
//                }
//                if(ref.textValue().startsWith("#/components") && !(ref.textValue().startsWith("#/components/schemas"))) {
//                    result.warning(location, "$ref target "+ref.textValue() +" is not of expected type Schema");
//                }
//            } else {
//                result.invalidType(location, "$ref", "string", node);
//            }
//        }
//
//        getCommonSchemaFields(node, location, result, schema);
//        String value;
//        Boolean bool;
//        Integer integer;
//
//        if (node.get("default") != null) {
//            schema.setDefault(getAnyType("default", node, location, result));
//        }
//
//        BigDecimal bigDecimal = getBigDecimal("exclusiveMaximum", node, false, location, result);
//        if (bigDecimal != null) {
//            schema.setExclusiveMaximumValue(bigDecimal);
//        }
//
//        bigDecimal = getBigDecimal("exclusiveMinimum", node, false, location, result);
//        if (bigDecimal != null) {
//            schema.setExclusiveMinimumValue(bigDecimal);
//        }
//
//        integer = getInteger("minContains", node, false, location, result);
//        if (integer != null) {
//            schema.setMinContains(integer);
//        }
//
//        integer = getInteger("maxContains", node, false, location, result);
//        if (integer != null) {
//            schema.setMaxContains(integer);
//        }
//
//        String typeString = getString("type", node, false, location, result, null, true);
//        ArrayNode typeArray = getArray("type", node, false, location, result, true);
//
//        if ((result.isAllowEmptyStrings() && typeString != null) || (!result.isAllowEmptyStrings() && !StringUtils.isBlank(typeString))) {
//            schema.addType(typeString);
//        } else if (typeArray != null) {
//            for (JsonNode n : typeArray) {
//                if (n.isValueNode()) {
//                    if (!JSON_SCHEMA_2020_12_TYPES.contains(n.asText())) {
//                        result.warning(location, " invalid type " + n.asText());
//                    }
//                    if (!schema.addType(n.asText())) {
//                        result.warning(location, " duplicated type " + n.asText());
//                    }
//                } else {
//                    result.invalidType(location, "type", "value", n);
//                }
//            }
//
//        } else {
//            // may have an enum where type can be inferred
//            JsonNode enumNode = node.get("enum");
//            if (enumNode != null && enumNode.isArray()) {
//                String type = inferTypeFromArray((ArrayNode) enumNode);
//                schema.addType(type);
//            }
//        }
//
//        ArrayNode enumArray = getArray("enum", node, false, location, result);
//        if (enumArray != null) {
//            for (JsonNode n : enumArray) {
//                if (n.isNumber()) {
//                    schema.addEnumItemObject(n.numberValue());
//                } else if (n.isBoolean()) {
//                    schema.addEnumItemObject(n.booleanValue());
//                } else if (n.isValueNode()) {
//                    try {
//                        schema.addEnumItemObject(getDecodedObject(schema, n.asText(null)));
//                    } catch (ParseException e) {
//                        result.invalidType(location, String.format("enum=`%s`", e.getMessage()),
//                                schema.getFormat(), n);
//                    }
//                } else if (n.isContainerNode()) {
//                    schema.addEnumItemObject(n.isNull() ? null : n);
//                } else {
//                    result.invalidType(location, "enum", "value", n);
//                }
//            }
//        }
//
//        JsonNode notObj = getObjectOrBoolean("not", node, false, location, result);
//        if (notObj != null) {
//            Schema not = getJsonSchema(notObj, location, result);
//            if (not != null) {
//                schema.setNot(not);
//            }
//        }
//
//        JsonNode contentSchemaObj = getObjectOrBoolean("contentSchema", node, false, location, result);
//        if (contentSchemaObj != null) {
//            Schema contentSchema = getJsonSchema(contentSchemaObj, location, result);
//            if (contentSchema != null) {
//                schema.setContentSchema(contentSchema);
//            }
//        }
//
//        JsonNode propertyNamesObj = getObjectOrBoolean("propertyNames", node, false, location, result);
//        if (propertyNamesObj != null) {
//            Schema propertyNames = getJsonSchema(propertyNamesObj, location, result);
//            if (propertyNames != null) {
//                schema.setPropertyNames(propertyNames);
//            }
//        }
//
//        JsonNode ifObj = getObjectOrBoolean("if", node, false, location, result);
//        if (ifObj != null) {
//            Schema _if = getJsonSchema(ifObj, location, result);
//            if (_if != null) {
//                schema.setIf(_if);
//            }
//        }
//
//        JsonNode thenObj = getObjectOrBoolean("then", node, false, location, result);
//        if (thenObj != null) {
//            Schema _then = getJsonSchema(thenObj, location, result);
//            if (_then != null) {
//                schema.setThen(_then);
//            }
//        }
//
//        JsonNode elseObj = getObjectOrBoolean("else", node, false, location, result);
//        if (elseObj != null) {
//            Schema _else = getJsonSchema(elseObj, location, result);
//            if (_else != null) {
//                schema.setElse(_else);
//            }
//        }
//
//        JsonNode unevaluatedItems = getObjectOrBoolean("unevaluatedItems", node, false, location, result);
//        if (unevaluatedItems != null) {
//            Schema unevaluatedItemsSchema = getJsonSchema(unevaluatedItems, location, result);
//            if (unevaluatedItemsSchema != null) {
//                schema.setUnevaluatedItems(unevaluatedItemsSchema);
//            }
//        }
//
//
//        Map<String, List<String>> dependentRequiredList = new LinkedHashMap<>();
//        ObjectNode dependentRequiredObj = getObject("dependentRequired", node, false, location, result);
//        List<String> dependentRequired = new ArrayList<>();
//
//        Set<String> dependentRequiredKeys = getKeys(dependentRequiredObj);
//        for (String name : dependentRequiredKeys) {
//            JsonNode dependentRequiredValue = dependentRequiredObj.get(name);
//            if (!dependentRequiredValue.getNodeType().equals(JsonNodeType.ARRAY)) {
//                result.invalidType(location, "dependentRequired", "object", dependentRequiredValue);
//            } else {
//                if (dependentRequiredObj != null) {
//                    for (JsonNode n : dependentRequiredValue){
//                        if (n.getNodeType().equals(JsonNodeType.STRING)) {
//                            dependentRequired.add(n.textValue());
//                        }
//                    }
//                    if (dependentRequired != null) {
//                        dependentRequiredList.put(name, dependentRequired);
//                    }
//                }
//            }
//        }
//        if (dependentRequiredObj != null) {
//            schema.setDependentRequired(dependentRequiredList);
//        }
//
//        Map<String, Schema> dependentSchemasList = new LinkedHashMap<>();
//        ObjectNode dependentSchemasObj = getObject("dependentSchemas", node, false, location, result);
//        if (dependentSchemasObj != null) {
//            Schema dependentSchemas = null;
//
//            Set<String> dependentSchemasKeys = getKeys(dependentSchemasObj);
//            for (String name : dependentSchemasKeys) {
//                JsonNode dependentSchemasValue = dependentSchemasObj.get(name);
//                dependentSchemas = getJsonSchema(dependentSchemasValue, location, result);
//                if (dependentSchemas != null) {
//                    dependentSchemasList.put(name, dependentSchemas);
//                }
//            }
//            if (dependentSchemasObj != null) {
//                schema.setDependentSchemas(dependentSchemasList);
//            }
//        }
//
//        //prefixItems
//        ArrayNode prefixItemsArray = getArray("prefixItems", node, false, location, result);
//        if(prefixItemsArray != null) {
//            Schema prefixItems = new JsonSchema();
//
//            List<Schema> prefixItemsList = new ArrayList<>();
//            for (JsonNode n : prefixItemsArray) {
//                prefixItems = getJsonSchema(n, location, result);
//                if (prefixItems != null) {
//                    prefixItemsList.add(prefixItems);
//                }
//            }
//            if (prefixItemsList.size() > 0) {
//                schema.setPrefixItems(prefixItemsList);
//            }
//        }
//
//        JsonNode containsObj = getObjectOrBoolean("contains", node, false, location, result);
//        if (containsObj != null) {
//            Schema contains = getJsonSchema(containsObj, location, result);
//            if (contains != null) {
//                schema.setContains(contains);
//            }
//        }
//
//        Map<String, Schema> properties = new LinkedHashMap<>();
//        ObjectNode propertiesObj = getObject("properties", node, false, location, result);
//        Schema property = null;
//
//        Set<String> keys = getKeys(propertiesObj);
//        for (String name : keys) {
//            JsonNode propertyValue = propertiesObj.get(name);
//            if (propertiesObj != null) {
//                property = getJsonSchema(propertyValue, location, result);
//                if (property != null) {
//                    properties.put(name, property);
//                }
//            }
//        }
//        if (propertiesObj != null) {
//            schema.setProperties(properties);
//        }
//
//        Map<String, Schema> patternProperties = new LinkedHashMap<>();
//        ObjectNode patternPropertiesObj = getObject("patternProperties", node, false, location, result);
//        Schema patternProperty = null;
//
//        Set<String> patternKeys = getKeys(patternPropertiesObj);
//        for (String name : patternKeys) {
//            JsonNode propertyValue = patternPropertiesObj.get(name);
//            if (patternPropertiesObj != null) {
//                patternProperty = getJsonSchema(propertyValue, location, result);
//                if (patternProperty != null) {
//                    patternProperties.put(name, patternProperty);
//                }
//            }
//        }
//        if (patternPropertiesObj != null) {
//            schema.setPatternProperties(patternProperties);
//        }
//
//        Object constValue = getAnyType("const", node, location, result);
//        if (constValue != null) {
//            schema.setConst(constValue);
//        }
//
//        value = getString("contentEncoding", node, false, location, result);
//        if (value != null) {
//            schema.setContentEncoding(value);
//        }
//
//        value = getString("contentMediaType", node, false, location, result);
//        if (value != null) {
//            schema.setContentMediaType(value);
//        }
//
//        ArrayNode examples = getArray("examples", node,false, location, result);
//        List<Object> exampleList = new ArrayList<>();
//        if (examples != null) {
//            for (JsonNode item : examples) {
//                exampleList.add(item);
//            }
//        }
//        if(exampleList.size() > 0){
//            schema.setExamples(exampleList);
//        }
//
//        value = getString("$anchor", node, false, location, result);
//        if (value != null) {
//            schema.set$anchor(value);
//        }
//
//        value = getString("$vocabulary", node, false, location, result);
//        if (value != null) {
//            schema.set$vocabulary(value);
//        }
//
//        value = getString("$dynamicAnchor", node, false, location, result);
//        if (value != null) {
//            schema.set$dynamicAnchor(value);
//        }
//
//        value = getString("$id", node, false, location, result);
//        if (value != null) {
//            schema.set$id(value);
//        }
//
//        value = getString("$schema", node, false, location, result);
//        if (value != null) {
//            schema.set$schema(value);
//        }
//
//        value = getString("$comment", node, false, location, result);
//        if (value != null) {
//            schema.set$comment(value);
//        }
//
//        Map<String, Object> extensions = getExtensions(node);
//        if (extensions != null && extensions.size() > 0) {
//            schema.setExtensions(extensions);
//        }
//
//        Set<String> schemaKeys = getKeys(node);
//        Map<String, Set<String>> specKeys = KEYS.get("openapi31");
//        for (String key : schemaKeys) {
//            validateReservedKeywords(specKeys, key, location, result);
//            if (!specKeys.get("SCHEMA_KEYS").contains(key) && !key.startsWith("x-")) {
//                extensions.put(key, new ObjectMapper().convertValue(node.get(key), Object.class));
//                schema.setExtensions(extensions);
//            }
//        }
//        return schema;
//    }


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

		/* TODO!! solve this
		at the moment path passed as string (basePath) from upper components can be both an absolute url or a relative one
		when it's relative, e.g. currently when parsing a file passing the location as relative ref
		 */

        for (JsonSchemaParserExtension jsonschemaExtension : jsonschemaExtensions) {
            schema = jsonschemaExtension.getSchema(jsonNode, location, result, rootMap, basePath);
            if (schema != null) {
                return schema;
            }
        }

        // TODO required? return getJsonSchema(jsonNode, location, result);

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

//    public class Schema<T> {
//
//        public static final String BIND_TYPE_AND_TYPES = "bind-type";
//
//        protected T _default;
//
//        private String name;
//        private String title = null;
//        private BigDecimal multipleOf = null;
//        private BigDecimal maximum = null;
//        private Boolean exclusiveMaximum = null;
//        private BigDecimal minimum = null;
//        private Boolean exclusiveMinimum = null;
//        private Integer maxLength = null;
//        private Integer minLength = null;
//        private String pattern = null;
//        private Integer maxItems = null;
//        private Integer minItems = null;
//        private Boolean uniqueItems = null;
//        private Integer maxProperties = null;
//        private Integer minProperties = null;
//        private List<String> required = null;
//        private String type = null;
//        private Schema not = null;
//        private Map<String, Schema> properties = null;
//        private Object additionalProperties = null;
//        private String description = null;
//        private String format = null;
//        private String $ref = null;
//        private Boolean nullable = null;
//        private Boolean readOnly = null;
//        private Boolean writeOnly = null;
//        protected T example = null;
//        private ExternalDocumentation externalDocs = null;
//        private Boolean deprecated = null;
//        private XML xml = null;
//        private Map<String, Object> extensions = null;
//        protected List<T> _enum = null;
//        private Discriminator discriminator = null;
//
//        private boolean exampleSetFlag;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private List<Schema> prefixItems = null;
//        private List<Schema> allOf = null;
//        private List<Schema> anyOf = null;
//        private List<Schema> oneOf = null;
//
//        private Schema<?> items = null;
//
//        protected T _const;
//
//        private SpecVersion specVersion = SpecVersion.V30;
//
//        @JsonIgnore
//        public SpecVersion getSpecVersion() {
//            return this.specVersion;
//        }
//
//        public void setSpecVersion(SpecVersion specVersion) {
//            this.specVersion = specVersion;
//        }
//
//        public Schema specVersion(SpecVersion specVersion) {
//            this.setSpecVersion(specVersion);
//            return this;
//        }
//
//
//        private Set<String> types;
//
//        private Map<String, Schema> patternProperties = null;
//
//        private BigDecimal exclusiveMaximumValue = null;
//
//        private BigDecimal exclusiveMinimumValue = null;
//
//        private Schema contains = null;
//
//        private String $id;
//
//        private String $schema;
//
//        private String $anchor;
//
//        private String $vocabulary;
//
//        private String $dynamicAnchor;
//
//        private String contentEncoding;
//
//        private String contentMediaType;
//
//        private Schema contentSchema;
//
//        private Schema propertyNames;
//
//        private Schema unevaluatedProperties;
//
//        private Integer maxContains;
//
//        private Integer minContains;
//
//        private Schema additionalItems;
//
//        private Schema unevaluatedItems;
//
//        private Schema _if;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private io.swagger.v3.oas.models.media.Schema _else;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private io.swagger.v3.oas.models.media.Schema then;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private Map<String, io.swagger.v3.oas.models.media.Schema> dependentSchemas;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private Map<String, List<String>> dependentRequired;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private String $comment;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        private List<T> examples;
//
//        /**
//         * @since 2.2.2 (OpenAPI 3.1.0)
//         *
//         * when set, this represents a boolean schema value
//         */
//        @OpenAPI31
//        private Boolean booleanSchemaValue;
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getContains() {
//            return contains;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setContains(io.swagger.v3.oas.models.media.Schema contains) {
//            this.contains = contains;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String get$id() {
//            return $id;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void set$id(String $id) {
//            this.$id = $id;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String get$schema() {
//            return $schema;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void set$schema(String $schema) {
//            this.$schema = $schema;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String get$anchor() {
//            return $anchor;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void set$anchor(String $anchor) {
//            this.$anchor = $anchor;
//        }
//
//        /**
//         * returns the exclusiveMaximumValue property from a Schema instance for OpenAPI 3.1.x
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         * @return BigDecimal exclusiveMaximumValue
//         *
//         **/
//        @OpenAPI31
//        public BigDecimal getExclusiveMaximumValue() {
//            return exclusiveMaximumValue;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setExclusiveMaximumValue(BigDecimal exclusiveMaximumValue) {
//            this.exclusiveMaximumValue = exclusiveMaximumValue;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema exclusiveMaximumValue(BigDecimal exclusiveMaximumValue) {
//            this.exclusiveMaximumValue = exclusiveMaximumValue;
//            return this;
//        }
//
//        /**
//         * returns the exclusiveMinimumValue property from a Schema instance for OpenAPI 3.1.x
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         * @return BigDecimal exclusiveMinimumValue
//         *
//         **/
//        @OpenAPI31
//        public BigDecimal getExclusiveMinimumValue() {
//            return exclusiveMinimumValue;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setExclusiveMinimumValue(BigDecimal exclusiveMinimumValue) {
//            this.exclusiveMinimumValue = exclusiveMinimumValue;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema exclusiveMinimumValue(BigDecimal exclusiveMinimumValue) {
//            this.exclusiveMinimumValue = exclusiveMinimumValue;
//            return this;
//        }
//
//        /**
//         * returns the patternProperties property from a Schema instance.
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         * @return Map&lt;String, Schema&gt; patternProperties
//         **/
//        @OpenAPI31
//        public Map<String, io.swagger.v3.oas.models.media.Schema> getPatternProperties() {
//            return patternProperties;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setPatternProperties(Map<String, io.swagger.v3.oas.models.media.Schema> patternProperties) {
//            this.patternProperties = patternProperties;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema patternProperties(Map<String, io.swagger.v3.oas.models.media.Schema> patternProperties) {
//            this.patternProperties = patternProperties;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema addPatternProperty(String key, io.swagger.v3.oas.models.media.Schema patternPropertiesItem) {
//            if (this.patternProperties == null) {
//                this.patternProperties = new LinkedHashMap<>();
//            }
//            this.patternProperties.put(key, patternPropertiesItem);
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema contains(io.swagger.v3.oas.models.media.Schema contains) {
//            this.contains = contains;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema $id(String $id) {
//            this.$id = $id;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Set<String> getTypes() {
//            return types;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setTypes(Set<String> types) {
//            this.types = types;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public boolean addType(String type) {
//            if (types == null) {
//                types = new LinkedHashSet<>();
//            }
//            return types.add(type);
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema $schema(String $schema) {
//            this.$schema = $schema;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.8 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String get$vocabulary() {
//            return $vocabulary;
//        }
//
//        /**
//         *
//         * @since 2.2.8 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void set$vocabulary(String $vocabulary) {
//            this.$vocabulary = $vocabulary;
//        }
//
//        /**
//         *
//         * @since 2.2.8 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema $vocabulary(String $vocabulary) {
//            this.$vocabulary = $vocabulary;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.8 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String get$dynamicAnchor() {
//            return $dynamicAnchor;
//        }
//
//        /**
//         *
//         * @since 2.2.8 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void set$dynamicAnchor(String $dynamicAnchor) {
//            this.$dynamicAnchor = $dynamicAnchor;
//        }
//
//        /**
//         *
//         * @since 2.2.8 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema $dynamicAnchor(String $dynamicAnchor) {
//            this.$dynamicAnchor = $dynamicAnchor;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema $anchor(String $anchor) {
//            this.$anchor = $anchor;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema types(Set<String> types) {
//            this.types = types;
//            return this;
//        }
//
//    /*
//    INTERNAL MEMBERS @OpenAPI31
//     */
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        protected Map<String, Object> jsonSchema = null;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Map<String, Object> getJsonSchema() {
//            return jsonSchema;
//        }
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setJsonSchema(Map<String, Object> jsonSchema) {
//            this.jsonSchema = jsonSchema;
//        }
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema jsonSchema(Map<String, Object> jsonSchema) {
//            this.jsonSchema = jsonSchema;
//            return this;
//        }
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        protected transient Object jsonSchemaImpl = null;
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Object getJsonSchemaImpl() {
//            return jsonSchemaImpl;
//        }
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setJsonSchemaImpl(Object jsonSchemaImpl) {
//            this.jsonSchemaImpl = jsonSchemaImpl;
//        }
//
//        /**
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema jsonSchemaImpl(Object jsonSchemaImpl) {
//            setJsonSchemaImpl(jsonSchemaImpl);
//            return this;
//        }
//
//        /*
//    CONSTRUCTORS
//     */
//
//
//        public Schema() {
//        }
//
//        protected Schema(String type, String format) {
//            this.type = type;
//            this.addType(type);
//            this.format = format;
//        }
//
//        public Schema(SpecVersion specVersion) {
//            this.specVersion = specVersion;
//        }
//
//        protected Schema(String type, String format, SpecVersion specVersion) {
//            this.type = type;
//            this.addType(type);
//            this.format = format;
//            this.specVersion = specVersion;
//        }
//
//    /*
//    ACCESSORS
//     */
//
//        /**
//         * returns the allOf property from a ComposedSchema instance.
//         *
//         * @return List&lt;Schema&gt; allOf
//         **/
//
//        public List<io.swagger.v3.oas.models.media.Schema> getAllOf() {
//            return allOf;
//        }
//
//        public void setAllOf(List<io.swagger.v3.oas.models.media.Schema> allOf) {
//            this.allOf = allOf;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema allOf(List<io.swagger.v3.oas.models.media.Schema> allOf) {
//            this.allOf = allOf;
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema addAllOfItem(io.swagger.v3.oas.models.media.Schema allOfItem) {
//            if (this.allOf == null) {
//                this.allOf = new ArrayList<>();
//            }
//            this.allOf.add(allOfItem);
//            return this;
//        }
//
//        /**
//         * returns the anyOf property from a ComposedSchema instance.
//         *
//         * @return List&lt;Schema&gt; anyOf
//         **/
//
//        public List<io.swagger.v3.oas.models.media.Schema> getAnyOf() {
//            return anyOf;
//        }
//
//        public void setAnyOf(List<io.swagger.v3.oas.models.media.Schema> anyOf) {
//            this.anyOf = anyOf;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema anyOf(List<io.swagger.v3.oas.models.media.Schema> anyOf) {
//            this.anyOf = anyOf;
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema addAnyOfItem(io.swagger.v3.oas.models.media.Schema anyOfItem) {
//            if (this.anyOf == null) {
//                this.anyOf = new ArrayList<>();
//            }
//            this.anyOf.add(anyOfItem);
//            return this;
//        }
//
//        /**
//         * returns the oneOf property from a ComposedSchema instance.
//         *
//         * @return List&lt;Schema&gt; oneOf
//         **/
//
//        public List<io.swagger.v3.oas.models.media.Schema> getOneOf() {
//            return oneOf;
//        }
//
//        public void setOneOf(List<io.swagger.v3.oas.models.media.Schema> oneOf) {
//            this.oneOf = oneOf;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema oneOf(List<io.swagger.v3.oas.models.media.Schema> oneOf) {
//            this.oneOf = oneOf;
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema addOneOfItem(io.swagger.v3.oas.models.media.Schema oneOfItem) {
//            if (this.oneOf == null) {
//                this.oneOf = new ArrayList<>();
//            }
//            this.oneOf.add(oneOfItem);
//            return this;
//        }
//
//
//        /**
//         * returns the items property from a ArraySchema instance.
//         *
//         * @return Schema items
//         **/
//
//        public io.swagger.v3.oas.models.media.Schema<?> getItems() {
//            return items;
//        }
//
//        public void setItems(io.swagger.v3.oas.models.media.Schema<?> items) {
//            this.items = items;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema items(io.swagger.v3.oas.models.media.Schema<?> items) {
//            this.items = items;
//            return this;
//        }
//
//
//        /**
//         * returns the name property from a from a Schema instance. Ignored in serialization.
//         *
//         * @return String name
//         **/
//        @JsonIgnore
//        public String getName() {
//            return this.name;
//        }
//
//        public void setName(String name) {
//            this.name = name;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema name(String name) {
//            this.setName(name);
//            return this;
//        }
//
//        /**
//         * returns the discriminator property from a AllOfSchema instance.
//         *
//         * @return Discriminator discriminator
//         **/
//
//        public Discriminator getDiscriminator() {
//            return discriminator;
//        }
//
//        public void setDiscriminator(Discriminator discriminator) {
//            this.discriminator = discriminator;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema discriminator(Discriminator discriminator) {
//            this.discriminator = discriminator;
//            return this;
//        }
//
//        /**
//         * returns the title property from a Schema instance.
//         *
//         * @return String title
//         **/
//
//        public String getTitle() {
//            return title;
//        }
//
//        public void setTitle(String title) {
//            this.title = title;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema title(String title) {
//            this.title = title;
//            return this;
//        }
//
//        /**
//         * returns the _default property from a Schema instance.
//         *
//         * @return String _default
//         **/
//
//        public T getDefault() {
//            return _default;
//        }
//
//        public void setDefault(Object _default) {
//            this._default = cast(_default);
//        }
//
//        @SuppressWarnings("unchecked")
//        protected T cast(Object value) {
//            return (T) value;
//        }
//
//        public List<T> getEnum() {
//            return _enum;
//        }
//
//        public void setEnum(List<T> _enum) {
//            this._enum = _enum;
//        }
//
//        public void addEnumItemObject(T _enumItem) {
//            if (this._enum == null) {
//                this._enum = new ArrayList<>();
//            }
//            this._enum.add(cast(_enumItem));
//        }
//
//        /**
//         * returns the multipleOf property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return BigDecimal multipleOf
//         **/
//
//        public BigDecimal getMultipleOf() {
//            return multipleOf;
//        }
//
//        public void setMultipleOf(BigDecimal multipleOf) {
//            this.multipleOf = multipleOf;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema multipleOf(BigDecimal multipleOf) {
//            this.multipleOf = multipleOf;
//            return this;
//        }
//
//        /**
//         * returns the maximum property from a Schema instance.
//         *
//         * @return BigDecimal maximum
//         **/
//
//        public BigDecimal getMaximum() {
//            return maximum;
//        }
//
//        public void setMaximum(BigDecimal maximum) {
//            this.maximum = maximum;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema maximum(BigDecimal maximum) {
//            this.maximum = maximum;
//            return this;
//        }
//
//        /**
//         * returns the exclusiveMaximum property from a Schema instance for OpenAPI 3.0.x
//         *
//         * @return Boolean exclusiveMaximum
//         **/
//        @OpenAPI30
//        public Boolean getExclusiveMaximum() {
//            return exclusiveMaximum;
//        }
//
//        @OpenAPI30
//        public void setExclusiveMaximum(Boolean exclusiveMaximum) {
//            this.exclusiveMaximum = exclusiveMaximum;
//        }
//
//        @OpenAPI30
//        public io.swagger.v3.oas.models.media.Schema exclusiveMaximum(Boolean exclusiveMaximum) {
//            this.exclusiveMaximum = exclusiveMaximum;
//            return this;
//        }
//
//        /**
//         * returns the minimum property from a Schema instance.
//         *
//         * @return BigDecimal minimum
//         **/
//
//        public BigDecimal getMinimum() {
//            return minimum;
//        }
//
//        public void setMinimum(BigDecimal minimum) {
//            this.minimum = minimum;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema minimum(BigDecimal minimum) {
//            this.minimum = minimum;
//            return this;
//        }
//
//
//        /**
//         * returns the exclusiveMinimum property from a Schema instance for OpenAPI 3.0.x
//         *
//         * @return Boolean exclusiveMinimum
//         **/
//
//        public Boolean getExclusiveMinimum() {
//            return exclusiveMinimum;
//        }
//
//        public void setExclusiveMinimum(Boolean exclusiveMinimum) {
//            this.exclusiveMinimum = exclusiveMinimum;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema exclusiveMinimum(Boolean exclusiveMinimum) {
//            this.exclusiveMinimum = exclusiveMinimum;
//            return this;
//        }
//
//
//        /**
//         * returns the maxLength property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return Integer maxLength
//         **/
//
//        public Integer getMaxLength() {
//            return maxLength;
//        }
//
//        public void setMaxLength(Integer maxLength) {
//            this.maxLength = maxLength;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema maxLength(Integer maxLength) {
//            this.maxLength = maxLength;
//            return this;
//        }
//
//        /**
//         * returns the minLength property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return Integer minLength
//         **/
//
//        public Integer getMinLength() {
//            return minLength;
//        }
//
//        public void setMinLength(Integer minLength) {
//            this.minLength = minLength;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema minLength(Integer minLength) {
//            this.minLength = minLength;
//            return this;
//        }
//
//        /**
//         * returns the pattern property from a Schema instance.
//         *
//         * @return String pattern
//         **/
//
//        public String getPattern() {
//            return pattern;
//        }
//
//        public void setPattern(String pattern) {
//            this.pattern = pattern;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema pattern(String pattern) {
//            this.pattern = pattern;
//            return this;
//        }
//
//        /**
//         * returns the maxItems property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return Integer maxItems
//         **/
//
//        public Integer getMaxItems() {
//            return maxItems;
//        }
//
//        public void setMaxItems(Integer maxItems) {
//            this.maxItems = maxItems;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema maxItems(Integer maxItems) {
//            this.maxItems = maxItems;
//            return this;
//        }
//
//        /**
//         * returns the minItems property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return Integer minItems
//         **/
//
//        public Integer getMinItems() {
//            return minItems;
//        }
//
//        public void setMinItems(Integer minItems) {
//            this.minItems = minItems;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema minItems(Integer minItems) {
//            this.minItems = minItems;
//            return this;
//        }
//
//        /**
//         * returns the uniqueItems property from a Schema instance.
//         *
//         * @return Boolean uniqueItems
//         **/
//
//        public Boolean getUniqueItems() {
//            return uniqueItems;
//        }
//
//        public void setUniqueItems(Boolean uniqueItems) {
//            this.uniqueItems = uniqueItems;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema uniqueItems(Boolean uniqueItems) {
//            this.uniqueItems = uniqueItems;
//            return this;
//        }
//
//        /**
//         * returns the maxProperties property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return Integer maxProperties
//         **/
//
//        public Integer getMaxProperties() {
//            return maxProperties;
//        }
//
//        public void setMaxProperties(Integer maxProperties) {
//            this.maxProperties = maxProperties;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema maxProperties(Integer maxProperties) {
//            this.maxProperties = maxProperties;
//            return this;
//        }
//
//        /**
//         * returns the minProperties property from a Schema instance.
//         * <p>
//         * minimum: 0
//         *
//         * @return Integer minProperties
//         **/
//
//        public Integer getMinProperties() {
//            return minProperties;
//        }
//
//        public void setMinProperties(Integer minProperties) {
//            this.minProperties = minProperties;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema minProperties(Integer minProperties) {
//            this.minProperties = minProperties;
//            return this;
//        }
//
//        /**
//         * returns the required property from a Schema instance.
//         *
//         * @return List&lt;String&gt; required
//         **/
//
//        public List<String> getRequired() {
//            return required;
//        }
//
//        public void setRequired(List<String> required) {
//            List<String> list = new ArrayList<>();
//            if (required != null) {
//                for (String req : required) {
//                    if (this.properties == null || this.properties.containsKey(req)) {
//                        list.add(req);
//                    }
//                }
//            }
//            Collections.sort(list);
//            if (list.isEmpty()) {
//                list = null;
//            }
//            this.required = list;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema required(List<String> required) {
//            this.required = required;
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema addRequiredItem(String requiredItem) {
//            if (this.required == null) {
//                this.required = new ArrayList<>();
//            }
//            this.required.add(requiredItem);
//            Collections.sort(required);
//            return this;
//        }
//
//        /**
//         * returns the type property from a Schema instance.
//         *
//         * @return String type
//         **/
//
//        public String getType() {
//            boolean bindTypes = Boolean.valueOf(System.getProperty(BIND_TYPE_AND_TYPES, "false"));
//            if (bindTypes && type == null && types != null && types.size() == 1) {
//                return types.iterator().next();
//            }
//            return type;
//        }
//
//        public void setType(String type) {
//            this.type = type;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema type(String type) {
//            this.type = type;
//            return this;
//        }
//
//        /**
//         * returns the not property from a Schema instance.
//         *
//         * @return Schema not
//         **/
//
//        public io.swagger.v3.oas.models.media.Schema getNot() {
//            return not;
//        }
//
//        public void setNot(io.swagger.v3.oas.models.media.Schema not) {
//            this.not = not;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema not(io.swagger.v3.oas.models.media.Schema not) {
//            this.not = not;
//            return this;
//        }
//
//        /**
//         * returns the properties property from a Schema instance.
//         *
//         * @return Map&lt;String, Schema&gt; properties
//         **/
//
//        public Map<String, io.swagger.v3.oas.models.media.Schema> getProperties() {
//            return properties;
//        }
//
//        public void setProperties(Map<String, io.swagger.v3.oas.models.media.Schema> properties) {
//            this.properties = properties;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema properties(Map<String, io.swagger.v3.oas.models.media.Schema> properties) {
//            this.properties = properties;
//            return this;
//        }
//
//        @Deprecated
//        public io.swagger.v3.oas.models.media.Schema addProperties(String key, io.swagger.v3.oas.models.media.Schema property) {
//            return addProperty(key, property);
//        }
//
//        /**
//         *
//         * @since 2.2.0
//         */
//        public io.swagger.v3.oas.models.media.Schema addProperty(String key, io.swagger.v3.oas.models.media.Schema property) {
//            if (this.properties == null) {
//                this.properties = new LinkedHashMap<>();
//            }
//            this.properties.put(key, property);
//            return this;
//        }
//
//        /**
//         * returns the additionalProperties property from a Schema instance. Can be either a Boolean or a Schema
//         *
//         * @return Object additionalProperties
//         **/
//
//        public Object getAdditionalProperties() {
//            return additionalProperties;
//        }
//
//        public void setAdditionalProperties(Object additionalProperties) {
//            if (additionalProperties != null && !(additionalProperties instanceof Boolean) && !(additionalProperties instanceof io.swagger.v3.oas.models.media.Schema)) {
//                throw new IllegalArgumentException("additionalProperties must be either a Boolean or a Schema instance");
//            }
//            this.additionalProperties = additionalProperties;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema additionalProperties(Object additionalProperties) {
//            setAdditionalProperties(additionalProperties);
//            return this;
//        }
//
//        /**
//         * returns the description property from a Schema instance.
//         *
//         * @return String description
//         **/
//
//        public String getDescription() {
//            return description;
//        }
//
//        public void setDescription(String description) {
//            this.description = description;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema description(String description) {
//            this.description = description;
//            return this;
//        }
//
//        /**
//         * returns the format property from a Schema instance.
//         *
//         * @return String format
//         **/
//
//        public String getFormat() {
//            return format;
//        }
//
//        public void setFormat(String format) {
//            this.format = format;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema format(String format) {
//            this.format = format;
//            return this;
//        }
//
//        /**
//         * returns the $ref property from a Schema instance.
//         *
//         * @return String $ref
//         **/
//        public String get$ref() {
//            return $ref;
//        }
//
//        public void set$ref(String $ref) {
//            if ($ref != null && !$ref.startsWith("#") && ($ref.indexOf('.') == -1 && $ref.indexOf('/') == -1)) {
//                $ref = Components.COMPONENTS_SCHEMAS_REF + $ref;
//            }
//            this.$ref = $ref;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema $ref(String $ref) {
//
//            set$ref($ref);
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema raw$ref(String $ref) {
//            this.$ref = $ref;
//            return this;
//        }
//
//        /**
//         * returns the nullable property from a Schema instance.
//         *
//         * @return Boolean nullable
//         **/
//        @OpenAPI30
//        public Boolean getNullable() {
//            return nullable;
//        }
//
//        @OpenAPI30
//        public void setNullable(Boolean nullable) {
//            this.nullable = nullable;
//        }
//
//        @OpenAPI30
//        public io.swagger.v3.oas.models.media.Schema nullable(Boolean nullable) {
//            this.nullable = nullable;
//            return this;
//        }
//
//        /**
//         * returns the readOnly property from a Schema instance.
//         *
//         * @return Boolean readOnly
//         **/
//
//        public Boolean getReadOnly() {
//            return readOnly;
//        }
//
//        public void setReadOnly(Boolean readOnly) {
//            this.readOnly = readOnly;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema readOnly(Boolean readOnly) {
//            this.readOnly = readOnly;
//            return this;
//        }
//
//        /**
//         * returns the writeOnly property from a Schema instance.
//         *
//         * @return Boolean writeOnly
//         **/
//
//        public Boolean getWriteOnly() {
//            return writeOnly;
//        }
//
//        public void setWriteOnly(Boolean writeOnly) {
//            this.writeOnly = writeOnly;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema writeOnly(Boolean writeOnly) {
//            this.writeOnly = writeOnly;
//            return this;
//        }
//
//        /**
//         * returns the example property from a Schema instance.
//         *
//         * @return String example
//         **/
//
//        public Object getExample() {
//            return example;
//        }
//
//        public void setExample(Object example) {
//            this.example = cast(example);
//            if (!(example != null && this.example == null)) {
//                exampleSetFlag = true;
//            }
//        }
//
//        public io.swagger.v3.oas.models.media.Schema example(Object example) {
//            setExample(example);
//            return this;
//        }
//
//        /**
//         * returns the externalDocs property from a Schema instance.
//         *
//         * @return ExternalDocumentation externalDocs
//         **/
//
//        public ExternalDocumentation getExternalDocs() {
//            return externalDocs;
//        }
//
//        public void setExternalDocs(ExternalDocumentation externalDocs) {
//            this.externalDocs = externalDocs;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema externalDocs(ExternalDocumentation externalDocs) {
//            this.externalDocs = externalDocs;
//            return this;
//        }
//
//        /**
//         * returns the deprecated property from a Schema instance.
//         *
//         * @return Boolean deprecated
//         **/
//
//        public Boolean getDeprecated() {
//            return deprecated;
//        }
//
//        public void setDeprecated(Boolean deprecated) {
//            this.deprecated = deprecated;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema deprecated(Boolean deprecated) {
//            this.deprecated = deprecated;
//            return this;
//        }
//
//        /**
//         * returns the xml property from a Schema instance.
//         *
//         * @return XML xml
//         **/
//
//        public XML getXml() {
//            return xml;
//        }
//
//        public void setXml(XML xml) {
//            this.xml = xml;
//        }
//
//        public Schema xml(XML xml) {
//            this.xml = xml;
//            return this;
//        }
//
//        /**
//         * returns true if example setter has been invoked
//         * Used to flag explicit setting to null of example (vs missing field) while deserializing from json/yaml string
//         *
//         * @return boolean exampleSetFlag
//         **/
//
//        public boolean getExampleSetFlag() {
//            return exampleSetFlag;
//        }
//
//        public void setExampleSetFlag(boolean exampleSetFlag) {
//            this.exampleSetFlag = exampleSetFlag;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public List<Schema> getPrefixItems() {
//            return prefixItems;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setPrefixItems(List<io.swagger.v3.oas.models.media.Schema> prefixItems) {
//            this.prefixItems = prefixItems;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema prefixItems(List<io.swagger.v3.oas.models.media.Schema> prefixItems) {
//            this.prefixItems = prefixItems;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.12 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema addPrefixItem(io.swagger.v3.oas.models.media.Schema prefixItem) {
//            if (this.prefixItems == null) {
//                this.prefixItems = new ArrayList<>();
//            }
//            this.prefixItems.add(prefixItem);
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String getContentEncoding() {
//            return contentEncoding;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setContentEncoding(String contentEncoding) {
//            this.contentEncoding = contentEncoding;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema contentEncoding(String contentEncoding) {
//            this.contentEncoding = contentEncoding;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String getContentMediaType() {
//            return contentMediaType;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setContentMediaType(String contentMediaType) {
//            this.contentMediaType = contentMediaType;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema contentMediaType(String contentMediaType) {
//            this.contentMediaType = contentMediaType;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getContentSchema() {
//            return contentSchema;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setContentSchema(io.swagger.v3.oas.models.media.Schema contentSchema) {
//            this.contentSchema = contentSchema;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema contentSchema(io.swagger.v3.oas.models.media.Schema contentSchema) {
//            this.contentSchema = contentSchema;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getPropertyNames() {
//            return propertyNames;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setPropertyNames(io.swagger.v3.oas.models.media.Schema propertyNames) {
//            this.propertyNames = propertyNames;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema propertyNames(io.swagger.v3.oas.models.media.Schema propertyNames) {
//            this.propertyNames = propertyNames;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getUnevaluatedProperties() {
//            return unevaluatedProperties;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setUnevaluatedProperties(io.swagger.v3.oas.models.media.Schema unevaluatedProperties) {
//            this.unevaluatedProperties = unevaluatedProperties;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema unevaluatedProperties(io.swagger.v3.oas.models.media.Schema unevaluatedProperties) {
//            this.unevaluatedProperties = unevaluatedProperties;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Integer getMaxContains() {
//            return maxContains;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setMaxContains(Integer maxContains) {
//            this.maxContains = maxContains;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema maxContains(Integer maxContains) {
//            this.maxContains = maxContains;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Integer getMinContains() {
//            return minContains;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setMinContains(Integer minContains) {
//            this.minContains = minContains;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema minContains(Integer minContains) {
//            this.minContains = minContains;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getAdditionalItems() {
//            return additionalItems;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setAdditionalItems(io.swagger.v3.oas.models.media.Schema additionalItems) {
//            this.additionalItems = additionalItems;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema additionalItems(io.swagger.v3.oas.models.media.Schema additionalItems) {
//            this.additionalItems = additionalItems;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getUnevaluatedItems() {
//            return unevaluatedItems;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setUnevaluatedItems(io.swagger.v3.oas.models.media.Schema unevaluatedItems) {
//            this.unevaluatedItems = unevaluatedItems;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema unevaluatedItems(io.swagger.v3.oas.models.media.Schema unevaluatedItems) {
//            this.unevaluatedItems = unevaluatedItems;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getIf() {
//            return _if;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setIf(io.swagger.v3.oas.models.media.Schema _if) {
//            this._if = _if;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema _if(io.swagger.v3.oas.models.media.Schema _if) {
//            this._if = _if;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getElse() {
//            return _else;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setElse(io.swagger.v3.oas.models.media.Schema _else) {
//            this._else = _else;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema _else(io.swagger.v3.oas.models.media.Schema _else) {
//            this._else = _else;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema getThen() {
//            return then;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setThen(io.swagger.v3.oas.models.media.Schema then) {
//            this.then = then;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema then(io.swagger.v3.oas.models.media.Schema then) {
//            this.then = then;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Map<String, io.swagger.v3.oas.models.media.Schema> getDependentSchemas() {
//            return dependentSchemas;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setDependentSchemas(Map<String, io.swagger.v3.oas.models.media.Schema> dependentSchemas) {
//            this.dependentSchemas = dependentSchemas;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema dependentSchemas(Map<String, io.swagger.v3.oas.models.media.Schema> dependentSchemas) {
//            this.dependentSchemas = dependentSchemas;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Map<String, List<String>> getDependentRequired() {
//            return dependentRequired;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setDependentRequired(Map<String, List<String>> dependentRequired) {
//            this.dependentRequired = dependentRequired;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema dependentRequired(Map<String, List<String>> dependentRequired) {
//            this.dependentRequired = dependentRequired;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public String get$comment() {
//            return $comment;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void set$comment(String $comment) {
//            this.$comment = $comment;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema $comment(String $comment) {
//            this.$comment = $comment;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public List<T> getExamples() {
//            return examples;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setExamples(List<T> examples) {
//            this.examples = examples;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema<T> examples(List<T> examples) {
//            this.examples = examples;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void addExample(T example) {
//            if (this.examples == null) {
//                this.examples = new ArrayList<>();
//            }
//            this.examples.add(example);
//        }
//
//        @Override
//        public boolean equals(java.lang.Object o) {
//            if (this == o) {
//                return true;
//            }
//            if (o == null || getClass() != o.getClass()) {
//                return false;
//            }
//            io.swagger.v3.oas.models.media.Schema schema = (io.swagger.v3.oas.models.media.Schema) o;
//            return Objects.equals(this.title, schema.title) &&
//                    Objects.equals(this.multipleOf, schema.multipleOf) &&
//                    Objects.equals(this.maximum, schema.maximum) &&
//                    Objects.equals(this.exclusiveMaximum, schema.exclusiveMaximum) &&
//                    Objects.equals(this.exclusiveMaximumValue, schema.exclusiveMaximumValue) &&
//                    Objects.equals(this.minimum, schema.minimum) &&
//                    Objects.equals(this.exclusiveMinimum, schema.exclusiveMinimum) &&
//                    Objects.equals(this.exclusiveMinimumValue, schema.exclusiveMinimumValue) &&
//                    Objects.equals(this.maxLength, schema.maxLength) &&
//                    Objects.equals(this.minLength, schema.minLength) &&
//                    Objects.equals(this.pattern, schema.pattern) &&
//                    Objects.equals(this.maxItems, schema.maxItems) &&
//                    Objects.equals(this.minItems, schema.minItems) &&
//                    Objects.equals(this.uniqueItems, schema.uniqueItems) &&
//                    Objects.equals(this.maxProperties, schema.maxProperties) &&
//                    Objects.equals(this.minProperties, schema.minProperties) &&
//                    Objects.equals(this.required, schema.required) &&
//                    Objects.equals(this.type, schema.type) &&
//                    Objects.equals(this.not, schema.not) &&
//                    Objects.equals(this.properties, schema.properties) &&
//                    Objects.equals(this.additionalProperties, schema.additionalProperties) &&
//                    Objects.equals(this.description, schema.description) &&
//                    Objects.equals(this.format, schema.format) &&
//                    Objects.equals(this.$ref, schema.$ref) &&
//                    Objects.equals(this.nullable, schema.nullable) &&
//                    Objects.equals(this.readOnly, schema.readOnly) &&
//                    Objects.equals(this.writeOnly, schema.writeOnly) &&
//                    Objects.equals(this.example, schema.example) &&
//                    Objects.equals(this.externalDocs, schema.externalDocs) &&
//                    Objects.equals(this.deprecated, schema.deprecated) &&
//                    Objects.equals(this.xml, schema.xml) &&
//                    Objects.equals(this.extensions, schema.extensions) &&
//                    Objects.equals(this.discriminator, schema.discriminator) &&
//                    Objects.equals(this._enum, schema._enum) &&
//                    Objects.equals(this.contains, schema.contains) &&
//                    Objects.equals(this.patternProperties, schema.patternProperties) &&
//                    Objects.equals(this.$id, schema.$id) &&
//                    Objects.equals(this.$anchor, schema.$anchor) &&
//                    Objects.equals(this.$schema, schema.$schema) &&
//                    Objects.equals(this.$vocabulary, schema.$vocabulary) &&
//                    Objects.equals(this.$dynamicAnchor, schema.$dynamicAnchor) &&
//                    Objects.equals(this.types, schema.types) &&
//                    Objects.equals(this.allOf, schema.allOf) &&
//                    Objects.equals(this.anyOf, schema.anyOf) &&
//                    Objects.equals(this.oneOf, schema.oneOf) &&
//                    Objects.equals(this._const, schema._const) &&
//                    Objects.equals(this._default, schema._default) &&
//                    Objects.equals(this.contentEncoding, schema.contentEncoding) &&
//                    Objects.equals(this.contentMediaType, schema.contentMediaType) &&
//                    Objects.equals(this.contentSchema, schema.contentSchema) &&
//                    Objects.equals(this.propertyNames, schema.propertyNames) &&
//                    Objects.equals(this.unevaluatedProperties, schema.unevaluatedProperties) &&
//                    Objects.equals(this.maxContains, schema.maxContains) &&
//                    Objects.equals(this.minContains, schema.minContains) &&
//                    Objects.equals(this.additionalItems, schema.additionalItems) &&
//                    Objects.equals(this.unevaluatedItems, schema.unevaluatedItems) &&
//                    Objects.equals(this._if, schema._if) &&
//                    Objects.equals(this._else, schema._else) &&
//                    Objects.equals(this.then, schema.then) &&
//                    Objects.equals(this.dependentRequired, schema.dependentRequired) &&
//                    Objects.equals(this.dependentSchemas, schema.dependentSchemas) &&
//                    Objects.equals(this.$comment, schema.$comment) &&
//                    Objects.equals(this.examples, schema.examples) &&
//                    Objects.equals(this.prefixItems, schema.prefixItems) &&
//                    Objects.equals(this.items, schema.items)
//
//                    ;
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(title, multipleOf, maximum, exclusiveMaximum, exclusiveMaximumValue, minimum,
//                    exclusiveMinimum, exclusiveMinimumValue, maxLength, minLength, pattern, maxItems, minItems, uniqueItems,
//                    maxProperties, minProperties, required, type, not, properties, additionalProperties, description,
//                    format, $ref, nullable, readOnly, writeOnly, example, externalDocs, deprecated, xml, extensions,
//                    discriminator, _enum, _default, patternProperties, $id, $anchor, $schema, $vocabulary, $dynamicAnchor, types, allOf, anyOf, oneOf, _const,
//                    contentEncoding, contentMediaType, contentSchema, propertyNames, unevaluatedProperties, maxContains,
//                    minContains, additionalItems, unevaluatedItems, _if, _else, then, dependentRequired, dependentSchemas,
//                    $comment, examples, prefixItems, items);
//        }
//
//        public java.util.Map<String, Object> getExtensions() {
//            return extensions;
//        }
//
//        public void addExtension(String name, Object value) {
//            if (name == null || name.isEmpty() || (specVersion == SpecVersion.V30 && !name.startsWith("x-"))) {
//                return;
//            }
//            if (this.extensions == null) {
//                this.extensions = new java.util.LinkedHashMap<>();
//            }
//            this.extensions.put(name, value);
//        }
//
//        public void setExtensions(java.util.Map<String, Object> extensions) {
//            this.extensions = extensions;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema extensions(java.util.Map<String, Object> extensions) {
//            this.extensions = extensions;
//            return this;
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class Schema {\n");
//            Object typeStr = specVersion == SpecVersion.V30 ? type : types;
//            sb.append("    type: ").append(toIndentedString(typeStr)).append("\n");
//            sb.append("    format: ").append(toIndentedString(format)).append("\n");
//            sb.append("    $ref: ").append(toIndentedString($ref)).append("\n");
//            sb.append("    description: ").append(toIndentedString(description)).append("\n");
//            sb.append("    title: ").append(toIndentedString(title)).append("\n");
//            sb.append("    multipleOf: ").append(toIndentedString(multipleOf)).append("\n");
//            sb.append("    maximum: ").append(toIndentedString(maximum)).append("\n");
//            Object exclusiveMaximumStr = specVersion == SpecVersion.V30 ? exclusiveMaximum : exclusiveMaximumValue;
//            sb.append("    exclusiveMaximum: ").append(toIndentedString(exclusiveMaximumStr)).append("\n");
//            sb.append("    minimum: ").append(toIndentedString(minimum)).append("\n");
//            Object exclusiveMinimumStr = specVersion == SpecVersion.V30 ? exclusiveMinimum : exclusiveMinimumValue;
//            sb.append("    exclusiveMinimum: ").append(toIndentedString(exclusiveMinimumStr)).append("\n");
//            sb.append("    maxLength: ").append(toIndentedString(maxLength)).append("\n");
//            sb.append("    minLength: ").append(toIndentedString(minLength)).append("\n");
//            sb.append("    pattern: ").append(toIndentedString(pattern)).append("\n");
//            sb.append("    maxItems: ").append(toIndentedString(maxItems)).append("\n");
//            sb.append("    minItems: ").append(toIndentedString(minItems)).append("\n");
//            sb.append("    uniqueItems: ").append(toIndentedString(uniqueItems)).append("\n");
//            sb.append("    maxProperties: ").append(toIndentedString(maxProperties)).append("\n");
//            sb.append("    minProperties: ").append(toIndentedString(minProperties)).append("\n");
//            sb.append("    required: ").append(toIndentedString(required)).append("\n");
//            sb.append("    not: ").append(toIndentedString(not)).append("\n");
//            sb.append("    properties: ").append(toIndentedString(properties)).append("\n");
//            sb.append("    additionalProperties: ").append(toIndentedString(additionalProperties)).append("\n");
//            sb.append("    nullable: ").append(toIndentedString(nullable)).append("\n");
//            sb.append("    readOnly: ").append(toIndentedString(readOnly)).append("\n");
//            sb.append("    writeOnly: ").append(toIndentedString(writeOnly)).append("\n");
//            sb.append("    example: ").append(toIndentedString(example)).append("\n");
//            sb.append("    externalDocs: ").append(toIndentedString(externalDocs)).append("\n");
//            sb.append("    deprecated: ").append(toIndentedString(deprecated)).append("\n");
//            sb.append("    discriminator: ").append(toIndentedString(discriminator)).append("\n");
//            sb.append("    xml: ").append(toIndentedString(xml)).append("\n");
//            if (specVersion == SpecVersion.V31) {
//                sb.append("    patternProperties: ").append(toIndentedString(patternProperties)).append("\n");
//                sb.append("    contains: ").append(toIndentedString(contains)).append("\n");
//                sb.append("    $id: ").append(toIndentedString($id)).append("\n");
//                sb.append("    $anchor: ").append(toIndentedString($anchor)).append("\n");
//                sb.append("    $schema: ").append(toIndentedString($schema)).append("\n");
//                sb.append("    $vocabulary: ").append(toIndentedString($vocabulary)).append("\n");
//                sb.append("    $dynamicAnchor: ").append(toIndentedString($dynamicAnchor)).append("\n");
//                sb.append("    const: ").append(toIndentedString(_const)).append("\n");
//                sb.append("    contentEncoding: ").append(toIndentedString(contentEncoding)).append("\n");
//                sb.append("    contentMediaType: ").append(toIndentedString(contentMediaType)).append("\n");
//                sb.append("    contentSchema: ").append(toIndentedString(contentSchema)).append("\n");
//                sb.append("    propertyNames: ").append(toIndentedString(propertyNames)).append("\n");
//                sb.append("    unevaluatedProperties: ").append(toIndentedString(unevaluatedProperties)).append("\n");
//                sb.append("    maxContains: ").append(toIndentedString(maxContains)).append("\n");
//                sb.append("    minContains: ").append(toIndentedString(minContains)).append("\n");
//                sb.append("    additionalItems: ").append(toIndentedString(additionalItems)).append("\n");
//                sb.append("    unevaluatedItems: ").append(toIndentedString(unevaluatedItems)).append("\n");
//                sb.append("    _if: ").append(toIndentedString(_if)).append("\n");
//                sb.append("    _else: ").append(toIndentedString(_else)).append("\n");
//                sb.append("    then: ").append(toIndentedString(then)).append("\n");
//                sb.append("    dependentRequired: ").append(toIndentedString(dependentRequired)).append("\n");
//                sb.append("    dependentSchemas: ").append(toIndentedString(dependentSchemas)).append("\n");
//                sb.append("    $comment: ").append(toIndentedString($comment)).append("\n");
//                sb.append("    prefixItems: ").append(toIndentedString(prefixItems)).append("\n");
//            }
//            sb.append("}");
//            return sb.toString();
//        }
//
//        /**
//         * Convert the given object to string with each line indented by 4 spaces
//         * (except the first line).
//         */
//        protected String toIndentedString(java.lang.Object o) {
//            if (o == null) {
//                return "null";
//            }
//            return o.toString().replace("\n", "\n    ");
//        }
//
//        public io.swagger.v3.oas.models.media.Schema _default(T _default) {
//            this._default = _default;
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema _enum(List<T> _enum) {
//            this._enum = _enum;
//            return this;
//        }
//
//        public io.swagger.v3.oas.models.media.Schema exampleSetFlag(boolean exampleSetFlag) {
//            this.exampleSetFlag = exampleSetFlag;
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public T getConst() {
//            return _const;
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setConst(Object _const) {
//            this._const = cast(_const);
//        }
//
//        /**
//         *
//         * @since 2.2.0 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema _const(Object _const) {
//            this._const = cast(_const);
//            return this;
//        }
//
//        /**
//         *
//         * @since 2.2.2 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public Boolean getBooleanSchemaValue() {
//            return booleanSchemaValue;
//        }
//
//        /**
//         *
//         * @since 2.2.2 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public void setBooleanSchemaValue(Boolean booleanSchemaValue) {
//            this.booleanSchemaValue = booleanSchemaValue;
//        }
//
//        /**
//         *
//         * @since 2.2.2 (OpenAPI 3.1.0)
//         */
//        @OpenAPI31
//        public io.swagger.v3.oas.models.media.Schema booleanSchemaValue(Boolean booleanSchemaValue) {
//            this.booleanSchemaValue = booleanSchemaValue;
//            return this;
//        }
//    }

//    public class ArraySchema extends Schema<Object> {
//
//        public ArraySchema() {
//            super("array", null);
//        }
//
//        @Override
//        public ArraySchema type(String type) {
//            super.setType(type);
//            return this;
//        }
//
//        @Override
//        public ArraySchema items(Schema items) {
//            super.setItems(items);
//            return this;
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class ArraySchema {\n");
//            sb.append("    ").append(toIndentedString(super.toString())).append("\n");
//            sb.append("}");
//            return sb.toString();
//        }
//    }
//
//    public class ComposedSchema extends Schema<Object> {
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class ComposedSchema {\n");
//            sb.append("    ").append(toIndentedString(super.toString())).append("\n");
//            sb.append("}");
//            return sb.toString();
//        }
//    }
//
//    public class JsonSchema extends Schema<Object> {
//
//        public JsonSchema (){
//            specVersion(SpecVersion.V31);
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class JsonSchema {\n");
//            sb.append("    ").append(toIndentedString(super.toString())).append("\n");
//            sb.append("}");
//            return sb.toString();
//        }
//    }
//
//    public class MapSchema extends Schema<Object> {
//
//        public MapSchema() {
//            super("object", null);
//        }
//
//        @Override
//        public MapSchema type(String type) {
//            super.setType(type);
//            return this;
//        }
//
//        @Override
//        public boolean equals(java.lang.Object o) {
//            if (this == o) {
//                return true;
//            }
//            if (o == null || getClass() != o.getClass()) {
//                return false;
//            }
//            return super.equals(o);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(super.hashCode());
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class MapSchema {\n");
//            sb.append("    ").append(toIndentedString(super.toString())).append("\n");
//            sb.append("}");
//            return sb.toString();
//        }
//    }
//
//    public class ObjectSchema extends Schema<Object> {
//
//        public ObjectSchema() {
//            super("object", null);
//        }
//
//        @Override
//        public ObjectSchema type(String type) {
//            super.setType(type);
//            return this;
//        }
//
//        @Override
//        public ObjectSchema example(Object example) {
//            if (example != null) {
//                super.setExample(example.toString());
//            } else {
//                super.setExample(example);
//            }
//            return this;
//        }
//
//        @Override
//        protected Object cast(Object value) {
//            return value;
//        }
//
//        @Override
//        public boolean equals(java.lang.Object o) {
//            if (this == o) {
//                return true;
//            }
//            if (o == null || getClass() != o.getClass()) {
//                return false;
//            }
//            return super.equals(o);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(super.hashCode());
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class ObjectSchema {\n");
//            sb.append("    ").append(toIndentedString(super.toString())).append("\n");
//            sb.append("}");
//            return sb.toString();
//        }
//    }
//
//    public class XML {
//        private String name = null;
//        private String namespace = null;
//        private String prefix = null;
//        private Boolean attribute = null;
//        private Boolean wrapped = null;
//        private java.util.Map<String, Object> extensions = null;
//
//        /**
//         * returns the name property from a XML instance.
//         *
//         * @return String name
//         **/
//
//        public String getName() {
//            return name;
//        }
//
//        public void setName(String name) {
//            this.name = name;
//        }
//
//        public XML name(String name) {
//            this.name = name;
//            return this;
//        }
//
//        /**
//         * returns the namespace property from a XML instance.
//         *
//         * @return String namespace
//         **/
//
//        public String getNamespace() {
//            return namespace;
//        }
//
//        public void setNamespace(String namespace) {
//            this.namespace = namespace;
//        }
//
//        public XML namespace(String namespace) {
//            this.namespace = namespace;
//            return this;
//        }
//
//        /**
//         * returns the prefix property from a XML instance.
//         *
//         * @return String prefix
//         **/
//
//        public String getPrefix() {
//            return prefix;
//        }
//
//        public void setPrefix(String prefix) {
//            this.prefix = prefix;
//        }
//
//        public XML prefix(String prefix) {
//            this.prefix = prefix;
//            return this;
//        }
//
//        /**
//         * returns the attribute property from a XML instance.
//         *
//         * @return Boolean attribute
//         **/
//
//        public Boolean getAttribute() {
//            return attribute;
//        }
//
//        public void setAttribute(Boolean attribute) {
//            this.attribute = attribute;
//        }
//
//        public XML attribute(Boolean attribute) {
//            this.attribute = attribute;
//            return this;
//        }
//
//        /**
//         * returns the wrapped property from a XML instance.
//         *
//         * @return Boolean wrapped
//         **/
//
//        public Boolean getWrapped() {
//            return wrapped;
//        }
//
//        public void setWrapped(Boolean wrapped) {
//            this.wrapped = wrapped;
//        }
//
//        public XML wrapped(Boolean wrapped) {
//            this.wrapped = wrapped;
//            return this;
//        }
//
//        @Override
//        public boolean equals(java.lang.Object o) {
//            if (this == o) {
//                return true;
//            }
//            if (o == null || getClass() != o.getClass()) {
//                return false;
//            }
//            XML XML = (XML) o;
//            return Objects.equals(this.name, XML.name) &&
//                    Objects.equals(this.namespace, XML.namespace) &&
//                    Objects.equals(this.prefix, XML.prefix) &&
//                    Objects.equals(this.attribute, XML.attribute) &&
//                    Objects.equals(this.wrapped, XML.wrapped) &&
//                    Objects.equals(this.extensions, XML.extensions);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(name, namespace, prefix, attribute, wrapped, extensions);
//        }
//
//        public java.util.Map<String, Object> getExtensions() {
//            return extensions;
//        }
//
//        public void addExtension(String name, Object value) {
//            if (name == null || name.isEmpty() || !name.startsWith("x-")) {
//                return;
//            }
//            if (this.extensions == null) {
//                this.extensions = new java.util.LinkedHashMap<>();
//            }
//            this.extensions.put(name, value);
//        }
//
//        public void setExtensions(java.util.Map<String, Object> extensions) {
//            this.extensions = extensions;
//        }
//
//        public XML extensions(java.util.Map<String, Object> extensions) {
//            this.extensions = extensions;
//            return this;
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("class XML {\n");
//
//            sb.append("    name: ").append(toIndentedString(name)).append("\n");
//            sb.append("    namespace: ").append(toIndentedString(namespace)).append("\n");
//            sb.append("    prefix: ").append(toIndentedString(prefix)).append("\n");
//            sb.append("    attribute: ").append(toIndentedString(attribute)).append("\n");
//            sb.append("    wrapped: ").append(toIndentedString(wrapped)).append("\n");
//            sb.append("}");
//            return sb.toString();
//        }
//
//        /**
//         * Convert the given object to string with each line indented by 4 spaces
//         * (except the first line).
//         */
//        private String toIndentedString(java.lang.Object o) {
//            if (o == null) {
//                return "null";
//            }
//            return o.toString().replace("\n", "\n    ");
//        }
//
//    }
}
