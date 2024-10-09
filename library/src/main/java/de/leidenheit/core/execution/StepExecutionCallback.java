package de.leidenheit.core.execution;

import de.leidenheit.core.model.FailureAction;
import de.leidenheit.core.model.SuccessAction;

public interface StepExecutionCallback {

    void onStepSuccess(final SuccessAction successAction);

    void onStepFailure(final FailureAction failureAction);
}
