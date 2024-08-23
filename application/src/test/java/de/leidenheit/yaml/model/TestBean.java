package de.leidenheit.yaml.model;

import lombok.Data;

@Data
public class TestBean {
    static final String TEST_STRING = "TEST-BEAN";

    String message = TEST_STRING;
    String attribute;
    String id;
}
