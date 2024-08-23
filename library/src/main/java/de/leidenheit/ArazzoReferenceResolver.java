package de.leidenheit;

import java.util.HashMap;
import java.util.Map;

public class ArazzoReferenceResolver {
    private Map<String, Object> context = new HashMap<>();

    // Fügt eine Referenz zum Kontext hinzu
    public void addReference(String key, Object value) {
        context.put(key, value);
    }

    // Rekursive Auflösung der Referenz
    public Object resolve(String reference) {
        if (reference.startsWith("$")) {
            String key = reference.substring(1);
            Object resolvedValue = context.get(key);
            if (resolvedValue instanceof String && ((String) resolvedValue).startsWith("$")) {
                // Rekursive Auflösung, falls das aufgelöste Objekt selbst eine Referenz ist
                return resolve((String) resolvedValue);
            }
            if (resolvedValue == null) {
                throw new IllegalArgumentException("Reference " + reference + " could not be resolved.");
            }
            return resolvedValue;
        }
        return reference;
    }

    // Validiert, dass alle Referenzen aufgelöst werden können
    public void validateReferences() {
        for (String key : context.keySet()) {
            Object value = context.get(key);
            if (value instanceof String && ((String) value).startsWith("$")) {
                resolve((String) value); // Rekursive Validierung
            }
        }
    }
}
