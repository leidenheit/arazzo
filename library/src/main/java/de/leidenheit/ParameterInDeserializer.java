package de.leidenheit;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class ParameterInDeserializer
        extends JsonDeserializer<ArazzoSpecification.Workflow.Step.Parameter.ParameterEnum> {

    @Override
    public ArazzoSpecification.Workflow.Step.Parameter.ParameterEnum deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        var value = jsonParser.getText();
        return ArazzoSpecification.Workflow.Step.Parameter.ParameterEnum.valueOf(value.toUpperCase());
    }
}
