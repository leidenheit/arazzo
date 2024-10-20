package de.leidenheit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import de.leidenheit.core.execution.ArazzoWorkflowExecutor;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.io.ArazzoInputsReader;
import org.junit.jupiter.api.DynamicTest;

import java.util.*;
import java.util.stream.Stream;

public class ArazzoDynamicTest {

    private Map<String, Map<String, Object>> outputsOfWorkflows = new LinkedHashMap<>();

    public Stream<DynamicTest> generateWorkflowTests(final ArazzoSpecification arazzo, final String inputsPath) {
        // sort workflows
        var sortedWorkflows = sortWorkflowsByDependencies(arazzo);

        // build and execute
        return sortedWorkflows.stream()
                .map(workflow -> createDynamicTestForWorkflow(arazzo, workflow, inputsPath));
    }

    private DynamicTest createDynamicTestForWorkflow(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsPath) {
        var inputs = readInputs(arazzo, workflow.getInputs(), inputsPath);
        return DynamicTest.dynamicTest("Workflow '%s'".formatted(workflow.getWorkflowId()), () ->
                executeWorkflow(arazzo, workflow, inputs, outputsOfWorkflows));
    }

    private void executeWorkflow(final ArazzoSpecification arazzo, final Workflow workflow, final Map<String, Object> inputs, final Map<String, Map<String, Object>> outputs) {
        var executor = new ArazzoWorkflowExecutor(arazzo, inputs, outputsOfWorkflows);

        System.out.printf("%n=============%nExecuting Workflow '%s'%n", workflow.getWorkflowId());
        outputsOfWorkflows  = executor.execute(workflow);
        System.out.printf("%nOutputs of all workflows: %s%n", outputsOfWorkflows);
        System.out.printf("=============%n%n");
    }

    private List<Workflow> sortWorkflowsByDependencies(final ArazzoSpecification arazzo) {
        List<Workflow> sortedWorkflowList = new ArrayList<>(arazzo.getWorkflows());

        sortedWorkflowList.sort(
                Comparator.comparing(workflow -> Optional
                                .ofNullable(workflow.getDependsOn())
                                .orElse(Collections.emptyList()),
                        Comparator.nullsFirst(
                                Comparator.comparing((List<String> dependsOnList) -> dependsOnList,
                                        (dependsOn1, dependsOn2) -> {
                                            if (dependsOn1.size() != dependsOn2.size()) {
                                                return Integer.compare(dependsOn1.size(), dependsOn2.size());
                                            }
                                            for (int i = 0; i < dependsOn1.size(); i++) {
                                                int compared = dependsOn1.get(i).compareTo(dependsOn2.get(i));
                                                if (compared != 0) {
                                                    return compared;
                                                }
                                            }
                                            return 0;
                                        })
                        )
                )
        );

        System.out.println("Workflows has been sorted to execution order:");
        sortedWorkflowList.forEach(workflow -> System.out.printf(">> '%s' depends on: '%s'%n",
                workflow.getWorkflowId(), workflow.getDependsOn()));
        return sortedWorkflowList;
    }

    private Map<String, Object> readInputs(final ArazzoSpecification arazzo,
                                           final JsonNode inputsSchemaNode,
                                           final String inputsFilePath) {
        if (Objects.isNull(inputsSchemaNode)) return null;
        var inputs = ArazzoInputsReader.parseAndValidateInputs(arazzo, inputsFilePath, inputsSchemaNode);
        System.out.printf("Provided inputs for arazzo: %s%n", inputs.toString());
        return inputs;
    }
}
