package com.bloodsky.hmi;
import org.junit.Test;
import static org.junit.Assert.*;

public class OperatorGestureTest {
    @Test public void shortTapActivatesExactlyItsOriginalTargetOnce() {
        OperatorGesture g = new OperatorGesture();
        g.begin(2, 10, 20, 100);
        assertEquals(2, g.end(2, 11, 21, 200, 5));
        assertEquals(-1, g.end(2, 11, 21, 210, 5));
    }
    @Test public void crossingTargetsOrMissingAnElementCannotActivate() {
        OperatorGesture g = new OperatorGesture();
        g.begin(2, 10, 20, 100);
        assertEquals(-1, g.end(3, 10, 20, 200, 5));
        g.begin(-1, 10, 20, 100);
        assertEquals(-1, g.end(2, 10, 20, 200, 5));
    }
    @Test public void draggingAwayAndBackStillCancelsActivation() {
        OperatorGesture g = new OperatorGesture();
        g.begin(2, 10, 20, 100); g.move(50, 20, 5); g.move(10, 20, 5);
        assertEquals(-1, g.end(2, 10, 20, 200, 5));
    }
    @Test public void longPressAndCancellationCannotActivate() {
        OperatorGesture g = new OperatorGesture();
        g.begin(2, 10, 20, 100);
        assertEquals(-1, g.end(2, 10, 20, 650, 5));
        g.begin(2, 10, 20, 100); g.cancel();
        assertEquals(-1, g.end(2, 10, 20, 200, 5));
    }
}
