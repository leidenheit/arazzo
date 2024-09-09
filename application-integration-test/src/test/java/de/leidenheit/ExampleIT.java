package de.leidenheit;

import de.leidenheit.core.execution.ArazzoWorkflowExecutor;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.integration.annotation.WithWorkflowExecutor;
import de.leidenheit.integration.extension.ArazzoExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ArazzoExtension.class)
class ExampleIT {

    @Test
    @WithWorkflowExecutor(workflowId = "retrieveCookieAndEatCookie")
    void exampleTestCase(final ArazzoSpecification arazzo,
                         final ArazzoWorkflowExecutor executor) {
        // given
        System.out.printf("Arazzo Version: %s%n", arazzo.getArazzo());

        // when & then
        Assertions.assertDoesNotThrow(executor::execute);
    }
}
