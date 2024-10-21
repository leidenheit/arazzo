package de.leidenheit.integration.extension;

import com.google.common.base.Strings;
import de.leidenheit.infrastructure.parsing.SourceDescriptionInitializer;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.parsing.ArazzoParseOptions;
import de.leidenheit.infrastructure.parsing.ArazzoParser;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidatorRegistry;
import org.junit.jupiter.api.extension.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ArazzoExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {

    private final String PROPERTY_ARAZZO_FILE = "arazzo.file";
    private final String PROPERTY_ARAZZO_INPUTS_FILE = "arazzo-inputs.file";
    private final Map<Class<?>, Object> supportedParameterTypes = new HashMap<>();

    @Override
    public void beforeAll(final ExtensionContext context) {
        var arazzoPath = readFromSystemProperties(PROPERTY_ARAZZO_FILE)
                // TODO replace with exception
                .orElseThrow(() -> new RuntimeException("Unexpected"));
        var arazzo = loadArazzoFromPath(arazzoPath);
        supportedParameterTypes.put(ArazzoSpecification.class, arazzo);
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        var arazzoInputs = readFromSystemProperties(PROPERTY_ARAZZO_INPUTS_FILE)
                // TODO replace with exception
                .orElseThrow(() -> new RuntimeException("Unexpected"));
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

    private Optional<String> readFromSystemProperties(final String property) {
        var propertyValue = System.getProperty(property);
        if (Strings.isNullOrEmpty(propertyValue)) return Optional.empty();

        System.out.printf("Reading system property '%s': %s%n", property, propertyValue);
        return Optional.of(propertyValue);
    }

    private ArazzoSpecification loadArazzoFromPath(final String pathOfArazzo) {
        ArazzoParser parser = new ArazzoParser();
        ArazzoParseOptions parseOptions = ArazzoParseOptions.ofDefault();
        var parseResult = parser.readLocation(pathOfArazzo, parseOptions);
        if (parseResult.isInvalid()) {
            // TODO replace with exception
            throw new RuntimeException("Parsing result is invalid: %s".formatted(parseResult.getMessages()));
        }

        // initializes arazzo/oas referenced through source descriptions
        SourceDescriptionInitializer.initialize(parseResult.getArazzo());

        ArazzoValidatorRegistry validatorRegistry = new ArazzoValidatorRegistry();
        ArazzoValidationOptions validationOptions = ArazzoValidationOptions.ofDefault();
        var validationResult = validatorRegistry.validate(parseResult.getArazzo(), validationOptions);
        if (validationResult.isInvalid()) {
            // TODO replace with exception
            throw new RuntimeException("Validation result is invalid: %s".formatted(validationResult.getMessages()));
        }

        return validationResult.getArazzo();
    }
}
