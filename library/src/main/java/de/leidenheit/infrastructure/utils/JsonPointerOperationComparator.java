package de.leidenheit.infrastructure.utils;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonPointerOperationComparator {

    private JsonPointerOperationComparator() {}

    // e.g '{$sourceDescriptions.cookieApi.url}#/paths/~1cookies~1{id}/get'
    public static boolean compareJsonPointerToPathAndOperation(final String operationPathJsonPointer,
                                                               final Paths paths) {
        try {
            Pattern pattern = Pattern.compile("#/[^']+");
            Matcher matcher = pattern.matcher(operationPathJsonPointer);
            // TODO replace with exception
            if (!matcher.find()) throw new RuntimeException("Unexpected");

            var validJsonPointerOfOperationPath = matcher.group();

            String[] extracted = JsonPointerUtils.extractPathAndOperationFromJsonPointer(validJsonPointerOfOperationPath);
            String extractedPath = extracted[0];
            String extractedOperation = extracted[1];

            var res = paths.entrySet().stream()
                    .filter(entry -> entry.getValue().readOperations().stream()
                            .anyMatch(o -> {
                                var pathItemMethod = PathItem.HttpMethod.valueOf(extractedOperation.toUpperCase());
                                return extractedPath.equals(entry.getKey()) &&
                                        Objects.nonNull(entry.getValue().readOperationsMap().getOrDefault(pathItemMethod, null));
                            })
                    )
                    .findFirst()
                    // TODO replace with exception
                    .orElseThrow(() -> new RuntimeException("No operation found for path " + extractedPath));
            return Objects.nonNull(res);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
