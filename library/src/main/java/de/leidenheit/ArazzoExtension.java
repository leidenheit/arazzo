package de.leidenheit;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ArazzoExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {


    private final Map<Class<?>, Object> supportedParameterTypes = new HashMap<>();
    private ArazzoSpecification arazzoSpecification;

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        var openApiPath = System.getProperty("openapi.file", "src/test/resources/openapi.yaml");
        var arazzoPath = System.getProperty("arazzo.file", "src/test/resources/arazzo.yaml");

        // parse and validate
        OpenAPIV3Parser oasParser = new OpenAPIV3Parser();
        ArazzoParser arazzoParser = new ArazzoParser();
        ArazzoValidator arazzoValidator = new ArazzoValidator();
        try {
            ParseOptions oasParseOptions = new ParseOptions();
            oasParseOptions.setResolveFully(true);
            OpenAPI openAPI = oasParser.read(openApiPath, Collections.emptyList(), oasParseOptions);

            arazzoSpecification = arazzoParser.parseYaml(new File(arazzoPath));
            var isValid = arazzoValidator.validateAgainstOpenApi(arazzoSpecification, openAPI);
            if (!isValid) {
                throw new RuntimeException("Validation failed");
            }

            supportedParameterTypes.put(ArazzoSpecification.class, arazzoSpecification);

        } catch (IOException ioException) {
            throw new RuntimeException("IO", ioException);
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
            Method testMethod = context.getRequiredTestMethod();
            if (!testMethod.isAnnotationPresent(Test.class)) {
                throw new RuntimeException("Annotation @WithWorkflowExecution can only be applied to @Test annotated methods");
            }

            if (!testMethod.isAnnotationPresent(WithWorkflowExecution.class)) {
                throw new RuntimeException("Annotation @WithWorkflowExecution missing");
            }

            WithWorkflowExecution withWorkflowExecution = testMethod.getAnnotation(WithWorkflowExecution.class);
            var workflowId = withWorkflowExecution.workflowId();
            var workflow = arazzoSpecification.getWorkflows().stream()
                    .filter(w -> workflowId.equals(w.getWorkflowId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Workflow not found"));

            ArazzoWorkflowExecutor arazzoWorkflowExecutor = new ArazzoWorkflowExecutor(workflow);

            supportedParameterTypes.put(ArazzoWorkflowExecutor.class, arazzoWorkflowExecutor);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return supportedParameterTypes.containsKey(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return supportedParameterTypes.get(parameterContext.getParameter().getType());
    }
}
