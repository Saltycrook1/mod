package dev.stormward.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkBudgetTest {
    @Test void workStopsAtSharedLimitAndResetsExplicitly() {
        WorkBudget budget = new WorkBudget(3);
        for (int storm = 0; storm < 8; storm++) {
            for (int attempt = 0; attempt < 100; attempt++) budget.trySpend();
        }
        assertEquals(3, budget.spent());
        assertEquals(0, budget.remaining());
        assertFalse(budget.trySpend());
        budget.reset();
        assertEquals(3, budget.remaining());
        assertEquals(0, budget.spent());
    }
    @Test void zeroLimitDisablesWork() {
        assertFalse(new WorkBudget(0).trySpend());
        assertThrows(IllegalArgumentException.class, () -> new WorkBudget(-1));
    }
}
