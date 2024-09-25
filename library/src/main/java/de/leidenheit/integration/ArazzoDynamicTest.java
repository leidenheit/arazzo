package de.leidenheit.integration;

import de.leidenheit.core.execution.ArazzoWorkflowExecutor;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Workflow;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ArazzoDynamicTest {

    private final Map<String, Map<String, Object>> workflowOutputs = new LinkedHashMap<>();

    @TestFactory
    public Stream<DynamicTest> generateWorkflowTests(final ArazzoSpecification arazzo, final String inputsPath) {
        // TODO implement support for multiple OAS source descriptions and referencing arazzo specifications
        var countOfSourceDescriptionOfTypeOAS = arazzo.getSourceDescriptions().stream()
                .filter(sourceDescription ->
                        SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType()))
                .count();
        if (countOfSourceDescriptionOfTypeOAS == 0)
            throw new RuntimeException("Only supports source description of type 'openapi' yet");
        if (countOfSourceDescriptionOfTypeOAS > 1)
            throw new RuntimeException("Multiple source descriptions of type 'openapi' is not yet supported");

        // sort workflows
        var sortedWorkflows = sortWorkflowsByDependencies(arazzo);

        // build and execute
        return sortedWorkflows.stream()
                .map(workflow -> createDynamicTestForWorkflow(arazzo, workflow, inputsPath));
    }

    private DynamicTest createDynamicTestForWorkflow(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsPath) {
        return DynamicTest.dynamicTest("Workflow-Test '%s'".formatted(workflow.getWorkflowId()), () ->
                executeWorkflow(arazzo, workflow, inputsPath, workflowOutputs));
    }

    private void executeWorkflow(final ArazzoSpecification arazzo, final Workflow workflow, final String inputsPath, final Map<String, Map<String, Object>> outputs) {
        var executor = new ArazzoWorkflowExecutor();
        var currentOutputs = executor.execute(arazzo, workflow, inputsPath);
        if (Objects.nonNull(currentOutputs)) {
            workflowOutputs.put(workflow.getWorkflowId(), currentOutputs);
        }
        System.out.printf("%n%nOutputs after workflow '%s'%n====================%n%s%n====================%n%n%n".formatted(workflow.getWorkflowId(), workflowOutputs));
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

        System.out.printf("==================%nWorkflow Execution Order: %n");
        sortedWorkflowList.forEach(workflow -> {
            System.out.printf("-> Workflow '%s' which depends on '%s'%n".formatted(workflow.getWorkflowId(), workflow.getDependsOn()));
        });
        System.out.printf("==================%n");

        return sortedWorkflowList;
    }
}
