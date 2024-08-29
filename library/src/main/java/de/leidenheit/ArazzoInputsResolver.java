package de.leidenheit;

import java.util.LinkedHashMap;
import java.util.Map;

public class ArazzoInputsResolver {

    private Map<String, Object> inputs = new LinkedHashMap<>();

    public Object resolveInput(final String keyPath) {
        return null; // TODO
    }

    private Object getNestedValue(final String key) {
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = this.inputs;
        Object value = null;
        for (int i = 0; i < keys.length; i++) {
            value = currentMap.get(keys[i]);
            if (i < keys.length - 1 && value instanceof Map) {
                currentMap = (Map<String, Object>) value;
            } else {
                break;
            }
        }
        return value;
    }

}
