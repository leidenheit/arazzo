package de.leidenheit.yaml;

import com.fasterxml.jackson.core.type.TypeReference;
import de.leidenheit.util.yaml.YamlUtil;
import de.leidenheit.yaml.model.TestBean;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class YamlUtilTest {

    @Test
    void testLoadYaml() throws IOException {
        // given
        try (final InputStream resourceAsStream = getClass().getResourceAsStream("/dataset/testbean1.yml")) {

            // when
            final String content = new String(Objects.requireNonNull(resourceAsStream).readAllBytes(), UTF_8);
            final TestBean testBean = YamlUtil.load(content, TestBean.class);

            // then
            assertThat(testBean).isNotNull();
            assertThat(testBean.getAttribute()).isEqualTo("attribute1");
            assertThat(testBean.getMessage()).isEqualTo("Hello");
        }
    }

    @Test
    void testLoadYamlTolerantReader() throws IOException {
        // given
        try (final InputStream resourceAsStream = getClass().getResourceAsStream("/dataset/testbean2.yml")) {
            // when
            final String content = new String(Objects.requireNonNull(resourceAsStream).readAllBytes(), UTF_8);
            final TestBean testBean2 = YamlUtil.load(content, TestBean.class);

            // then
            assertThat(testBean2).isNotNull();
            assertThat(testBean2.getAttribute()).isEqualTo("attribute1");
            assertThat(testBean2.getMessage()).isEqualTo("Hello");
        }
    }

    @Test
    void testLoadYamlWithMemberUsingString() {
        // given
        final String resource = "---\nmessage: \"Hello\"\nattribute: \"attribute1\"\n";

        // when
        final TestBean testBean = YamlUtil.load(resource, TestBean.class);

        // then
        assertThat(testBean).isNotNull();
        assertThat(testBean.getAttribute()).isEqualTo("attribute1");
        assertThat(testBean.getMessage()).isEqualTo("Hello");
    }


    @Test
    void testWriteValueAsString() {
        // given
        final TestBean testBean = new TestBean();
        testBean.setAttribute("attribute1");
        testBean.setMessage("Hello");

        // when
        String actual = YamlUtil.writeValueAsString(testBean);

        // then
        assertThat(actual).isEqualTo("---\nmessage: \"Hello\"\nattribute: \"attribute1\"\nid: null\n");
    }

    @Test
    void testReadListOfBean2FromStringContent() throws IOException {
        // given
        try (final InputStream resourceAsStream = getClass().getResourceAsStream("/dataset/test-list.yml")) {

            // when
            final String content = new String(Objects.requireNonNull(resourceAsStream).readAllBytes(), UTF_8);
            final List<TestBean> actual = YamlUtil.load(content, new TypeReference<>() {
            });

            // then
            assertThat(actual)
                    .extracting(TestBean::getId, TestBean::getMessage, TestBean::getAttribute)
                    .containsExactly(
                            tuple("123", "Hello World!", "Foo"),
                            tuple("456", "Hello Universe!", "Bar"));
        }
    }

    @Test
    void testLoadYamlWithMultipleFilesStringClass() throws IOException {
        // given
        try (final InputStream resourceAsStream = getClass()
                .getResourceAsStream("/dataset/test-multiple-files.yml")) {

            // when
            final String content = new String(Objects.requireNonNull(resourceAsStream).readAllBytes(), UTF_8);
            final List<TestBean> actual = YamlUtil.loadList(content, TestBean.class);

            // then
            assertThat(actual)
                    .extracting(TestBean::getId, TestBean::getMessage, TestBean::getAttribute)
                    .containsExactly(
                            tuple("123", "Hello World!", "Foo"),
                            tuple("456", "Hello Universe!", "Bar"));
        }
    }

    @Test
    void testLoadYamlWithMultipleFilesStringTypeReference() throws IOException {
        // given
        try (final InputStream resourceAsStream =
                     getClass().getResourceAsStream("/dataset/test-multiple-files.yml")) {

            // when
            final String content = new String(Objects.requireNonNull(resourceAsStream).readAllBytes(), UTF_8);
            final List<TestBean> actual = YamlUtil.loadList(content, new TypeReference<>() {
            });

            // then
            assertThat(actual)
                    .extracting(TestBean::getId, TestBean::getMessage, TestBean::getAttribute)
                    .containsExactly(
                            tuple("123", "Hello World!", "Foo"), tuple("456", "Hello Universe!", "Bar"));
        }
    }
}
