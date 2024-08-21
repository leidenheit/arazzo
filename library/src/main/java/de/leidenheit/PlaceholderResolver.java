package de.leidenheit;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderResolver {

    private final Map<String, Object> context;

    public PlaceholderResolver(final Map<String, Object> context) {
        this.context = context;
    }

    public String resolve(final String value) {
        Pattern pattern = Pattern.compile("\\$\\S+");
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(0);
            String resolvedValue = resolvePlaceholder(placeholder);
            matcher.appendReplacement(result, resolvedValue);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolvePlaceholder(final String placeholder) {
        String[] parts = placeholder.split("\\.");
        Object currentValue = context;

        for (String part : parts) {
            if (currentValue instanceof Map) {
                currentValue = ((Map<?, ?>) currentValue).get(part);
            }
//            else if (currentValue instanceof PlaceholderResolvable) {
//                currentValue = ((PlaceholderResolvable) currentValue).resolvePlaceholder(part);
//            }
        }

        return currentValue != null ? currentValue.toString() : "";
    }
}
