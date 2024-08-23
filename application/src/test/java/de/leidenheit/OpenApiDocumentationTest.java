package de.leidenheit;

import com.fasterxml.jackson.core.type.TypeReference;
import de.leidenheit.util.yaml.YamlUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    private static final String URL_API_DOCS = "/openapi.yaml";
    private static final String TEST_DESCRIPTION = """
            The openapi.yaml file in the repository must be equal with the one currently generated.
            It has to be recreated using this test locally.
            This test must be executed with JUnit.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHaveSameOpenApiInRepository() throws IOException {
        // given
        final Path openApiYamlPath = Path.of("src/test/resources/openapi.yaml");
        Map<String, Object> existingOpenApiMap;
        try {
            existingOpenApiMap = readOpenApiYaml(Files.readString(openApiYamlPath, StandardCharsets.UTF_8));
        } catch (IOException e) {
            existingOpenApiMap = null;
        }

        // when
        final var request = MockMvcRequestBuilders
                .get(URL_API_DOCS)
                .characterEncoding(StandardCharsets.UTF_8);
        final var resultActions = assertDoesNotThrow(() -> mockMvc.perform(request));
        final String newOpenApiFile = resultActions
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        final Map<String, Object> newOpenApiMap = readOpenApiYaml(newOpenApiFile);
        Files.writeString(
                openApiYamlPath,
                YamlUtil.writeValueAsString(readOpenApiYaml(newOpenApiFile)),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        // then
        assertThat(newOpenApiMap)
                .describedAs(TEST_DESCRIPTION)
                .overridingErrorMessage("OpenAPI changed: Please check your modifications")
                .usingRecursiveComparison()
                .isEqualTo(existingOpenApiMap);
    }

    private Map<String, Object> readOpenApiYaml(String yaml) {
        return YamlUtil.load(yaml, new TypeReference<>() {
        });
    }
}
