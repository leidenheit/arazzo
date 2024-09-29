package de.leidenheit.infrastructure.utils;

import io.swagger.v3.oas.models.Paths;

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
            if (!matcher.find()) throw new RuntimeException("Unexpected");

            var validJsonPointerOfOperationPath = matcher.group();

            String[] extracted = JsonPointerUtils.extractPathAndOperationFromJsonPointer(validJsonPointerOfOperationPath);
            String extractedPath = extracted[0];
            String extractedOperation = extracted[1];

            var stringPathItemEntry = paths.entrySet().stream().filter(e -> extractedPath.equals(e.getKey())).findFirst().orElseThrow(() -> new RuntimeException("Unexpected"));

            var openApiPath = stringPathItemEntry.getKey();
            var oasOperationEntry = stringPathItemEntry.getValue().readOperationsMap().entrySet().stream().findFirst().orElseThrow(() -> new RuntimeException("unexpected"));

            return extractedPath.equals(openApiPath) && extractedOperation.equals(oasOperationEntry.getKey().toString().toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
