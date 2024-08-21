package de.leidenheit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ArazzoExecutor {

    private final Map<String, Object> context = new HashMap<>();

    public void executeWorkflows(final ArazzoSpecification arazzo) {
        // TODO finalize implementation

        Map<String, Object> parameters = new HashMap<>();
        var wf = arazzo.getWorkflows().get(0);
        for (final ArazzoSpecification.Workflow.Step step: wf.getSteps()) {
            for (final ArazzoSpecification.Workflow.Step.Parameter parameter : step.getParameters()) {
                String resolvedParameter = new PlaceholderResolver(context).resolve(parameter.getValue());
                parameters.put(parameter.getName(), resolvedParameter);
            }
        }
        log.debug("Resolved parameters: {}", parameters);


        return;
    }
}
