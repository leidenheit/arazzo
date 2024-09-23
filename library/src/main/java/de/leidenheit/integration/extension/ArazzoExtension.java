package de.leidenheit.integration.extension;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.parsing.ArazzoParseOptions;
import de.leidenheit.infrastructure.parsing.ArazzoParser;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidatorRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.extension.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ArazzoExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {

    private final Map<Class<?>, Object> supportedParameterTypes = new HashMap<>();
    private ArazzoSpecification arazzoSpecification;

    @Override
    public void beforeAll(final ExtensionContext context) {
        var openApiPath = System.getenv("openapi.file");
        var arazzoPath = System.getenv("arazzo.file");

        System.out.printf("oas=%s%narazzo=%s%n".formatted(openApiPath, arazzoPath));

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
                    .mustValidate(true)
                    .resolve(true)
                    .build();
            var result = arazzoParser.readLocation(arazzoPath, options);
            if (result.isInvalid()) {
                throw new RuntimeException("Parsing result invalid; result=" + result.getMessages());
            }
            arazzoSpecification = result.getArazzo();
            arazzoSpecification.getSourceDescriptions().get(0).setReferencedOpenAPI(openAPI);

            // TODO temp validation
            var validateOptions = ArazzoValidationOptions.ofDefault();
            var validationResult = arazzoValidatorRegistry.validate(arazzoSpecification, validateOptions);

            supportedParameterTypes.put(ArazzoSpecification.class, arazzoSpecification);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) {

//        Method testMethod = context.getRequiredTestMethod();

//        if (testMethod.isAnnotationPresent(WithWorkflowExecutor.class)) {
//            // TODO conditional if explicit workflow execution annotation is not present
//            if (!testMethod.isAnnotationPresent(Test.class)) {
//                throw new RuntimeException("Annotation @WithWorkflowExecution can only be applied to @Test annotated methods");
//            }

//            WithWorkflowExecutor withWorkflowExecutor = testMethod.getAnnotation(WithWorkflowExecutor.class);
//            var workflowId = withWorkflowExecutor.workflowId();
//            var workflow = arazzoSpecification.getWorkflows().stream()
//                    .filter(w -> workflowId.equals(w.getWorkflowId()))
//                    .findFirst()
//                    .orElseThrow(() -> new RuntimeException("Workflow not found"));

//            // prepare executor
//            var arazzoInputs = System.getenv("arazzo-inputs.file");
//            var params = new ExecutorParams(arazzoInputs);
//            ArazzoWorkflowExecutor arazzoWorkflowExecutor = new ArazzoWorkflowExecutor(arazzoSpecification, workflow, params);
//            supportedParameterTypes.put(ArazzoWorkflowExecutor.class, arazzoWorkflowExecutor);
//            return;
//        }

//        // prepare executor
//
//        var params = new ExecutorParams(arazzoInputs);
//        ArazzoWorkflowExecutor arazzoWorkflowExecutor = new ArazzoWorkflowExecutor(arazzoSpecification, null, params);
//        supportedParameterTypes.put(ArazzoWorkflowExecutor.class, arazzoWorkflowExecutor);

        var arazzoInputs = System.getenv("arazzo-inputs.file");
        supportedParameterTypes.put(String.class, arazzoInputs);
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
