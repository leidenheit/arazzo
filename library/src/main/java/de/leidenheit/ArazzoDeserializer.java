package de.leidenheit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class ArazzoDeserializer {

    protected static Set<String> JSON_SCHEMA_2020_12_TYPES = new LinkedHashSet<>(List.of(
            "null", "boolean", "object", "array", "number", "string", "integer"));

    protected static Set<String> RESERVED_KEYWORDS = new LinkedHashSet<>(List.of());
    protected static Set<String> ROOT_KEYS = new LinkedHashSet<>(List.of());
    protected static Set<String> INFO_KEYS = new LinkedHashSet<>(List.of(
            "title", "summary", "description", "version"
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
        // TODO others

        KEYS.put("arazzo10", keys10);
    }

    private JsonNode rootNode;
    private Map<String, Object> rootMap;
    private String basePath;
    private final Set<String> workflowIds = new HashSet<>();
    // TODO components
    // TODO localSchemaRefs

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
        OpenAPIDeserializer openAPIDeserializer;

        String location = "";
        ArazzoSpecification arazzo = new ArazzoSpecification();
        if (node.getNodeType().equals(JsonNodeType.OBJECT)){
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

            // source description object (https://spec.openapis.org/arazzo/latest.html#source-description-object)
            // TODO finalise implementation

            // workflows (https://spec.openapis.org/arazzo/latest.html#workflow-object)
            // TODO finalise implementation

            // components (https://spec.openapis.org/arazzo/latest.html#components-object)
            // TODO finalise implementation

            // reference handling
            // TODO finalise implementation
        } else {
            parseResult.invalidType(location, "arazzo", "object", node);
            parseResult.invalid();
            return null;
        }
        return arazzo;
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
        if(!result.isOaiAuthor() && specKeys.get("RESERVED_KEYWORDS").stream()
                .filter(key::startsWith)
                .findAny()
                .orElse(null) != null){
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
        if (node == null || v == null) {
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
            if (uniqueValues != null && !uniqueValues.add(value)) {
                parseResult.unique(location, "workflowId");
                parseResult.invalid();
            }
        }
        return value;
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
}
