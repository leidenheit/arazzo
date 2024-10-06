package de.leidenheit.integration.extension;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
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
        var arazzoPath = System.getProperty("arazzo.file");

        System.out.printf("Properties: arazzo.file=%s%n".formatted(arazzoPath));

        // parse and validate
        ArazzoParser arazzoParser = new ArazzoParser();
        ArazzoValidatorRegistry arazzoValidatorRegistry = new ArazzoValidatorRegistry();
        try {
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
            // TODO initialize referenced source description content
            arazzoSpecification.getSourceDescriptions().forEach(sourceDescription -> {
               if (SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType())) {
                   OpenAPIV3Parser oasParser = new OpenAPIV3Parser();
                   ParseOptions oasParseOptions = new ParseOptions();
                   oasParseOptions.setResolveFully(true);
                   OpenAPI refOAS = oasParser.read(sourceDescription.getUrl(), Collections.emptyList(), oasParseOptions);
                   sourceDescription.setReferencedOpenAPI(refOAS);
               } else if (SourceDescription.SourceDescriptionType.ARAZZO.equals(sourceDescription.getType())) {
                   var refArazzo = arazzoParser.readLocation(sourceDescription.getUrl(), options);
                    if (refArazzo.isInvalid()) throw new RuntimeException("Unexpected");
                   sourceDescription.setReferencedArazzo(refArazzo.getArazzo());
               } else {
                   throw new RuntimeException("Unsupported");
               }
            });

            // TODO temp validation
            var validateOptions = ArazzoValidationOptions.ofDefault();
            var validationResult = arazzoValidatorRegistry.validate(arazzoSpecification, validateOptions);
            if (validationResult.isInvalid()) {
                System.out.printf("Arazzo validation failed : %n%s%n", validationResult.getMessages().toString());
                throw new RuntimeException("Arazzo must be valid");
            }

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

        var arazzoInputs = System.getProperty("arazzo-inputs.file");
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
