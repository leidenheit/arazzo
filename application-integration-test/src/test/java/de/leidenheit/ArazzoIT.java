package de.leidenheit;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.integration.ArazzoDynamicTest;
import de.leidenheit.integration.extension.ArazzoExtension;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.stream.Stream;

@ExtendWith(ArazzoExtension.class)
class ArazzoIT {

    @TestFactory
    Stream<DynamicTest> runArazzoTest(final ArazzoSpecification arazzo,
                                      final String inputsFilePath) {
        ArazzoDynamicTest dynamicTest = new ArazzoDynamicTest();
        return dynamicTest.generateWorkflowTests(arazzo, inputsFilePath);
    }
}
