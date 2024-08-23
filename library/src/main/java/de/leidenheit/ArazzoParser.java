package de.leidenheit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;

@RequiredArgsConstructor
public class ArazzoParser {

    public ArazzoSpecification parseYaml(final File workflowSpecificationFile) throws IOException {
        var yamlMapper = new ObjectMapper(new YAMLFactory());
        return yamlMapper.readValue(workflowSpecificationFile, ArazzoSpecification.class);
    }
}
