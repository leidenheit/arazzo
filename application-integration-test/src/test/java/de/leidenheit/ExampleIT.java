package de.leidenheit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ArazzoExtension.class)
class ExampleIT {

    @Test
    @WithWorkflowExecution(workflowId = "retrieveCookieAndEatCookie")
    void exampleTestCase(final ArazzoSpecification arazzo,
                         final ArazzoWorkflowExecutor executor) {
        // given
        System.out.printf("Arazzo Version: %s%n", arazzo.getArazzo());

        // when & then
        Assertions.assertDoesNotThrow(executor::execute);
    }
}
