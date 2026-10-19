import android.app.Instrumentation;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;

/** Sends one continuous long-press-and-drag gesture from the FBD macro tile. */
public class CraftDragGesture extends InstrumentationTestCase {
    public void testMacroBlockDrag() {
        Instrumentation instrumentation = getInstrumentation();
        int sourceX = 1629;
        int sourceY = 359;
        int dropX = Integer.parseInt(instrumentation.getArguments().getString("dropX", "1150"));
        int dropY = Integer.parseInt(instrumentation.getArguments().getString("dropY", "850"));
        long downTime = SystemClock.uptimeMillis();
        send(instrumentation, downTime, downTime, MotionEvent.ACTION_DOWN, sourceX, sourceY, 1f);
        SystemClock.sleep(800);
        for (int step = 1; step <= 8; step++) {
            long eventTime = SystemClock.uptimeMillis();
            float fraction = step / 8f;
            float x = sourceX + (dropX - sourceX) * fraction;
            float y = sourceY + (dropY - sourceY) * fraction;
            send(instrumentation, downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 1f);
            SystemClock.sleep(35);
        }
        long eventTime = SystemClock.uptimeMillis();
        send(instrumentation, downTime, eventTime, MotionEvent.ACTION_UP, dropX, dropY, 0f);
    }

    private void send(Instrumentation instrumentation, long downTime, long eventTime,
                      int action, float x, float y, float pressure) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, pressure);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        instrumentation.sendPointerSync(event);
        event.recycle();
    }
}
