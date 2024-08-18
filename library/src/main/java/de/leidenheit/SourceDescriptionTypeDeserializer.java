package de.leidenheit;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class SourceDescriptionTypeDeserializer
        extends JsonDeserializer<ArazzoSpecification.SourceDescription.SourceDescriptionType> {

    @Override
    public ArazzoSpecification.SourceDescription.SourceDescriptionType deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        var value = jsonParser.getText();
        return ArazzoSpecification.SourceDescription.SourceDescriptionType.valueOf(value.toUpperCase());
    }
}
