package de.leidenheit.core.execution.context;

import de.leidenheit.core.model.FailureAction;
import de.leidenheit.core.model.SuccessAction;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExecutionResult {

    final boolean successful;
    final List<SuccessAction> successActions;
    final List<FailureAction> failureActions;
}
