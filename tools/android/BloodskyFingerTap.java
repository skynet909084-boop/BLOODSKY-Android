import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Shell-only emulator test driver: API30 input tap leaves the pointer tool type UNKNOWN. */
public final class BloodskyFingerTap {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected x y");
        float x = Float.parseFloat(args[0]), y = Float.parseFloat(args[1]);
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0)
            throw new IllegalArgumentException("Invalid tap coordinates");
        // These are platform APIs used by Android's own shell input command.
        // Reflection permits compilation against the ordinary public SDK stubs.
        Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        Method inject = managerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        pointer.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x; coords.y = y; coords.size = 1.0f; coords.pressure = 1.0f;
        long down = SystemClock.uptimeMillis();
        send(manager, inject, MotionEvent.ACTION_DOWN, down, pointer, coords);
        SystemClock.sleep(80);
        coords.pressure = 0.0f;
        send(manager, inject, MotionEvent.ACTION_UP, down, pointer, coords);
    }
    private static void send(Object manager, Method inject, int action, long down,
                             MotionEvent.PointerProperties pointer, MotionEvent.PointerCoords coords)
            throws Exception {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            if (!Boolean.TRUE.equals(inject.invoke(manager, event, 2)))
                throw new IllegalStateException("Shell finger injection rejected");
        } finally { event.recycle(); }
    }
}
