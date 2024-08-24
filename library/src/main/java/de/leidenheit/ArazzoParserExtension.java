package de.leidenheit;

public interface ArazzoParserExtension {

    ArazzoParseResult readLocation(final String arazzoUrl, final ArazzoParseOptions options);
    ArazzoParseResult readContents(final String arazzoAsString, final ArazzoParseOptions options);
}
