package de.leidenheit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class ArazzoParser {

    public ArazzoSpecification parseYaml(final File workflowSpecificationFile) throws IOException {
        log.debug("Parsing arazzo specification (YAML) from {}", workflowSpecificationFile);
        var yamlMapper = new ObjectMapper(new YAMLFactory());
        return yamlMapper.readValue(workflowSpecificationFile, ArazzoSpecification.class);
    }
}
