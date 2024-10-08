package de.leidenheit.core.execution;

import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Step;
import de.leidenheit.infrastructure.resolving.ArazzoExpressionResolver;

public interface StepExecutor {

    void executeStep(final ArazzoSpecification arazzo,
                     final Step step,
                     final ArazzoExpressionResolver resolver);
}
