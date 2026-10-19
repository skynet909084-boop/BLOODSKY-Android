import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

/** Sends a long-press drag from Craft's FBD macro tile onto the canvas. */
public class CraftDragGesture extends UiAutomatorTestCase {
    public void testMacroBlockDrag() throws Exception {
        UiObject macroTile = new UiObject(new UiSelector()
                .resourceId("ru.kascada.modbus:id/tab_fb_macro"));
        macroTile.dragTo(1150, 850, 40);
    }
}
