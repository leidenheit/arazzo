package de.leidenheit.core.execution;

import de.leidenheit.core.execution.context.ExecutionResult;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;

public interface StepExecutor {

    ExecutionResult executeStep(final ArazzoSpecification arazzo,
                                final Workflow workflow,
                                final Step step,
                                final ArazzoExpressionResolver resolver);
}
