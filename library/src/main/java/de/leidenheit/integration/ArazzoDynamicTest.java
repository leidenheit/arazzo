package de.leidenheit.integration;

import de.leidenheit.core.execution.ArazzoWorkflowExecutor;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Workflow;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ArazzoDynamicTest {

    private final Map<String, Map<String, Object>> workflowOutputs = new LinkedHashMap<>();

    @TestFactory
    public Stream<DynamicTest> generateWorkflowTests(final ArazzoSpecification arazzo, final String inputsPath) {
        return arazzo.getWorkflows().stream()
                .map(workflow -> createDynamicTestForWorkflow(arazzo, workflow, inputsPath));
    }

    private DynamicTest createDynamicTestForWorkflow(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsPath) {
        return DynamicTest.dynamicTest("Test of Workflow %s".formatted(workflow.getWorkflowId()), () -> executeWorkflow(arazzo, workflow, inputsPath));
    }

    private void executeWorkflow(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsPath) {
        var executor = new ArazzoWorkflowExecutor();
        var outputs = executor.execute(arazzo, workflow, inputsPath);
        if (Objects.nonNull(outputs)) {
            // TODO key must be unique but is not yet
            workflowOutputs.put(workflow.getWorkflowId(), outputs);
        }


        System.out.printf("%n%n%n====================%n%s%n====================%n%n%n".formatted(workflowOutputs));
    }
}
