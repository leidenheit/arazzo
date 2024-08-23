package de.leidenheit.util.yaml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import de.leidenheit.util.yaml.exception.YamlLoadException;
import de.leidenheit.util.yaml.exception.YamlWriteException;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

public class YamlUtil {

    private static final YAMLMapper mapper = configuredMapper();
    private static final YAMLFactory yamlFactory = new YAMLFactory();

    private YamlUtil() {
    }

    public static <T> T load(final String content, final Class<T> clazz) {
        try {
            return mapper.readValue(content, clazz);
        } catch (IOException ioe) {
            throw new YamlLoadException(ioe);
        }
    }

    public static <T> T load(final String content, final TypeReference<T> typeReference) {
        try {
            return mapper.readValue(content, typeReference);
        } catch (IOException ioe) {
            throw new YamlLoadException(ioe);
        }
    }

    public static <T> List<T> loadList(final String content, final Class<T> clazz) {
        try {
            YAMLParser parser = yamlFactory.createParser(content);
            return mapper.readValues(parser, clazz).readAll();
        } catch (IOException ioe) {
            throw new YamlLoadException(ioe);
        }
    }

    public static <T> List<T> loadList(final String content, final TypeReference<T> typeReference) {
        try {
            YAMLParser parser = yamlFactory.createParser(content);
            return mapper.<T>readValues(parser, typeReference).readAll();
        } catch (IOException ioe) {
            throw new YamlLoadException(ioe);
        }
    }

    public static String writeValueAsString(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException jpe) {
            throw new YamlWriteException(jpe);
        }
    }

    private static YAMLMapper configuredMapper() {
        YAMLMapper mapper = new YAMLMapper();

        mapper
                // serialization
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                // deserialization
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                // time zone handling
                .setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC))
                // YAML specific
                .enable(JsonParser.Feature.ALLOW_COMMENTS)
                .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                .enable(JsonParser.Feature.ALLOW_YAML_COMMENTS)
                // modules
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());

        return mapper;
    }
}
