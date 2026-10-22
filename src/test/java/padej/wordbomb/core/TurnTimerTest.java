package padej.wordbomb.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnTimerTest {

    @Test
    void turnLengthFormula() {
        Rules rules = Rules.DEFAULT;
        assertEquals(20, rules.turnSeconds(0));
        assertEquals(15, rules.turnSeconds(5));
        assertEquals(5, rules.turnSeconds(15));
        assertEquals(5, rules.turnSeconds(40));
    }

    @Test
    void countsDownWithoutExtraSecond() {
        TurnTimer t = new TurnTimer(Rules.DEFAULT);
        assertEquals(5, t.start(5));
        assertTrue(t.running());
        int ticks = 0;
        while (t.running()) {
            t.tick();
            ticks++;
        }
        assertEquals(5, ticks);
        assertTrue(t.expired());
        assertEquals(0, t.tick());
    }

    @Test
    void startTurnUsesRound() {
        TurnTimer t = new TurnTimer(new Rules(2, 20, 10, 3, 2, 0.9, 20, 50));
        assertEquals(10, t.startTurn(0));
        assertEquals(3, t.startTurn(9));
        assertEquals(1f, t.progress(), 0.001);
        t.tick();
        assertEquals(2f / 3f, t.progress(), 0.001);
    }

    @Test
    void stopClearsState() {
        TurnTimer t = new TurnTimer(Rules.DEFAULT);
        t.start(4);
        t.stop();
        assertFalse(t.running());
        assertFalse(t.expired());
        assertEquals(0, t.secondsLeft());
    }
}
