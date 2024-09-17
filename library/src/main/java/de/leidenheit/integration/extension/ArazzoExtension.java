package de.leidenheit.integration.extension;

import de.leidenheit.core.execution.ArazzoWorkflowExecutor;
import de.leidenheit.core.execution.ExecutorParams;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.parsing.ArazzoParseOptions;
import de.leidenheit.infrastructure.parsing.ArazzoParser;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidatorRegistry;
import de.leidenheit.integration.annotation.WithWorkflowExecutor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ArazzoExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {

    private final Map<Class<?>, Object> supportedParameterTypes = new HashMap<>();
    private ArazzoSpecification arazzoSpecification;

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        var openApiPath = System.getProperty("openapi.file", "y");
        var arazzoPath = System.getProperty("arazzo.file", "x");

        // parse and validate
        OpenAPIV3Parser oasParser = new OpenAPIV3Parser();
        ArazzoParser arazzoParser = new ArazzoParser();
        ArazzoValidatorRegistry arazzoValidatorRegistry = new ArazzoValidatorRegistry();
        try {
            ParseOptions oasParseOptions = new ParseOptions();
            oasParseOptions.setResolveFully(true);
            OpenAPI openAPI = oasParser.read(openApiPath, Collections.emptyList(), oasParseOptions);

            var options = ArazzoParseOptions.builder()
                    .oaiAuthor(false)
                    .allowEmptyStrings(false)
                    .validateInternalRefs(true)
                    .resolve(true)
                    .build();
            var result = arazzoParser.readLocation(arazzoPath, options);
            if (result.isInvalid()) {
                throw new RuntimeException("Parsing result invalid; result=" + result.getMessages());
            }
            arazzoSpecification = result.getArazzo();
            // TODO validate itself
            var validationOptions = ArazzoValidationOptions.ofDefault();
            var validationResult = arazzoValidatorRegistry.validate(arazzoSpecification, validationOptions);
            if (validationResult.isInvalid()) {
                throw new RuntimeException("Validation of Arazzo failed: " + validationResult.getMessages());
            }
            // TODO validate against OAS;
            var validationResultAgainstOas = arazzoValidatorRegistry.validateAgainstOpenApi(arazzoSpecification, openAPI);
            if (validationResultAgainstOas.isInvalid()) {
                throw new RuntimeException("Validation against OAS failed: " + validationResultAgainstOas.getMessages());
            }
            arazzoSpecification.setOpenAPI(openAPI);

            supportedParameterTypes.put(ArazzoSpecification.class, arazzoSpecification);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        // TODO conditional if explicit workflow execution annotation is not present
        Method testMethod = context.getRequiredTestMethod();
        if (!testMethod.isAnnotationPresent(Test.class)) {
            throw new RuntimeException("Annotation @WithWorkflowExecution can only be applied to @Test annotated methods");
        }

        if (!testMethod.isAnnotationPresent(WithWorkflowExecutor.class)) {
            throw new RuntimeException("Annotation @WithWorkflowExecution missing");
        }

        WithWorkflowExecutor withWorkflowExecutor = testMethod.getAnnotation(WithWorkflowExecutor.class);
        var workflowId = withWorkflowExecutor.workflowId();
        var workflow = arazzoSpecification.getWorkflows().stream()
                .filter(w -> workflowId.equals(w.getWorkflowId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        // prepare executor
        var arazzoInputs = System.getProperty("arazzo-inputs.file", "z");
        var params = new ExecutorParams(arazzoInputs);
        ArazzoWorkflowExecutor arazzoWorkflowExecutor = new ArazzoWorkflowExecutor(arazzoSpecification, workflow, params);
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
