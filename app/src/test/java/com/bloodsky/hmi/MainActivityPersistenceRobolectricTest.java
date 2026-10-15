package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Checks the actual Activity and SharedPreferences boundary, not only JSON codecs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityPersistenceRobolectricTest {
    @Test public void editedTwoScreenProjectSurvivesActivityRecreation() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("bloodsky_projects", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        JSONObject first = new JSONObject()
                .put("name", "Logic").put("backgroundColor", 123)
                .put("widgets", new JSONArray().put(new JSONObject().put("type", "FBD Вход"))
                        .put(new JSONObject().put("type", "FBD Выход")))
                .put("connections", new JSONArray().put(
                        new JSONObject().put("source", 0).put("target", 1)
                                .put("vendorWire", "preserve")))
                .put("vendorScreen", "keep");
        JSONObject second = new JSONObject()
                .put("name", "Panel").put("backgroundColor", 456)
                .put("widgets", new JSONArray().put(new JSONObject()
                        .put("type", "Индикатор").put("text", "Old")
                        .put("x", 10).put("y", 20).put("w", 160).put("h", 58)
                        .put("connectionInfo", "device/temperature")
                        .put("buttonState", true)
                        .put("vendorWidget", new JSONObject().put("scale", 10))))
                .put("connections", new JSONArray());
        JSONObject project = new JSONObject().put("projectName", "Test")
                .put("currentScreen", 1).put("screens", new JSONArray().put(first).put(second))
                .put("vendorProject", 99);
        assertTrue(prefs.edit().putString("active_project", project.toString()).commit());
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            call(activity, "loadProject");
            call(activity, "showEditor");
            List<?> widgets = (List<?>) field(activity, "widgets");
            Object widget = widgets.get(0);
            set(widget, "text", "New");
            set(widget, "x", 90);
            // No explicit save: lifecycle onPause must persist the edited canvas.
            controller.recreate();
            activity = controller.get();
            call(activity, "loadProject");
            assertEquals(1, field(activity, "currentScreenIndex"));
            assertEquals("New", field(((List<?>) field(activity, "widgets")).get(0), "text"));
            JSONObject restored = new JSONObject(prefs.getString("active_project", ""));
            assertEquals(99, restored.getInt("vendorProject"));
            JSONArray screens = restored.getJSONArray("screens");
            assertEquals(2, screens.length());
            assertEquals(123, screens.getJSONObject(0).getInt("backgroundColor"));
            assertEquals("keep", screens.getJSONObject(0).getString("vendorScreen"));
            assertEquals("preserve", screens.getJSONObject(0).getJSONArray("connections")
                    .getJSONObject(0).getString("vendorWire"));
            JSONObject saved = screens.getJSONObject(1).getJSONArray("widgets").getJSONObject(0);
            assertEquals(90, saved.getInt("x"));
            assertEquals("device/temperature", saved.getString("connectionInfo"));
            assertTrue(saved.getBoolean("buttonState"));
            assertEquals(10, saved.getJSONObject("vendorWidget").getInt("scale"));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test public void corruptedStoredProjectFallsBackWithoutOverwritingPreferences() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("bloodsky_projects", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        String invalid = "{invalid project JSON";
        assertTrue(prefs.edit().putString("active_project", invalid).commit());
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            call(activity, "loadProject");
            assertEquals(1, ((List<?>) field(activity, "screens")).size());
            assertEquals(0, ((List<?>) field(activity, "widgets")).size());
            assertEquals(invalid, prefs.getString("active_project", null));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    private static void call(Object object, String name) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(object);
    }
}
