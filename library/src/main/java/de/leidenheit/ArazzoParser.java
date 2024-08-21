package de.leidenheit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class ArazzoParser {

    private final ArazzoValidator validator;

    public ArazzoParser(final ArazzoValidator validator) {
        this.validator = validator;
    }

    public ArazzoSpecification parseYaml(final File workflowSpecificationFile, final Configuration configuration) throws IOException {
        log.debug("Parsing arazzo specification (YAML) from {}", workflowSpecificationFile);
        var yamlMapper = new ObjectMapper(new YAMLFactory());
        var arazzo = yamlMapper.readValue(workflowSpecificationFile, ArazzoSpecification.class);

        if (configuration.validation) {
            var validationResult = validator.validate(arazzo);
            if (!validationResult.isValid()) {
                throw new RuntimeException("Validation failed");
            }
        }

        // resolve dynamic properties and reference oas operations
        // TODO

        return arazzo;
    }

    @Data
    @Builder
    public static class Configuration {
        private boolean validation;
    }
}
