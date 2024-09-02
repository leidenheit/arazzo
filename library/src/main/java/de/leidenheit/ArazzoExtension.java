package de.leidenheit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
        ArazzoValidator arazzoValidator = new ArazzoValidator();
        try {
            ParseOptions oasParseOptions = new ParseOptions();
            oasParseOptions.setResolveFully(true);
            OpenAPI openAPI = oasParser.read(openApiPath, Collections.emptyList(), oasParseOptions);

            /*
            TODO arazzoSpecification = arazzoParser.parseYamlRaw(new File(arazzoPath));
            var isValid = arazzoValidator.validateAgainstOpenApi(arazzoSpecification, openAPI);
            if (!isValid) {
                throw new RuntimeException("Validation failed");
            }
             */
            var options = ArazzoParseOptions.builder()
                    .oaiAuthor(false)
                    .allowEmptyStrings(false)
                    .inferSchemaType(true)
                    .validateInternalRefs(true)
                    .resolve(true)
                    .build();
            var result = arazzoParser.readLocation(arazzoPath, options);
            if (result.isInvalid()) {
                throw new RuntimeException("Parsing result invalid; result=" + result.getMessages());
            }
            arazzoSpecification = result.getArazzo();
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
