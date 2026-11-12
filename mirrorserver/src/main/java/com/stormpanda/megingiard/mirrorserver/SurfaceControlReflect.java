package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection wrappers for hidden {@code android.view.SurfaceControl} (API 33) and
 * {@code com.android.server.display.DisplayControl} (API 34+) virtual display APIs.
 *
 * <p>Android 14+ (API 34–36+) removed {@code SurfaceControl.createDisplay} and
 * {@code SurfaceControl.destroyDisplay}, moving virtual display token creation to
 * {@code com.android.server.display.DisplayControl} inside {@code services.jar}
 * (backed by {@code libandroid_servers.so}), while keeping {@code SurfaceControl.Transaction}
 * for surface, layer-stack, and projection binding.</p>
 */
final class SurfaceControlReflect {

    private static final String SERVICES_JAR_PATH = "/system/framework/services.jar";
    private static final String SYSTEM_LIB_SEARCH_PATH = "/system/lib64:/system/lib";
    private static final String SYSTEM_LIB_ANDROID_SERVERS = "/system/lib64/libandroid_servers.so";
    private static final String DISPLAY_CONTROL_CLASS = "com.android.server.display.DisplayControl";

    private static final Class<?> SURFACE_CONTROL;
    private static final Method M_CREATE_DISPLAY;
    private static final Method M_DESTROY_DISPLAY;

    // SurfaceControl.Transaction — replaces openTransaction/closeTransaction on API 31+
    private static final Constructor<?> TRANSACTION_CTOR;
    private static final Method M_TX_SET_DISPLAY_SURFACE;
    private static final Method M_TX_SET_DISPLAY_LAYER_STACK;
    private static final Method M_TX_SET_DISPLAY_PROJECTION;
    private static final Method M_TX_APPLY;

    static {
        try {
            SURFACE_CONTROL = Class.forName("android.view.SurfaceControl");

            Method createMethod;
            Method destroyMethod;
            if (Build.VERSION.SDK_INT < 34) {
                try {
                    createMethod = SURFACE_CONTROL.getMethod(
                            "createDisplay", String.class, boolean.class);
                    destroyMethod = SURFACE_CONTROL.getMethod(
                            "destroyDisplay", IBinder.class);
                } catch (NoSuchMethodException fallbackEx) {
                    Class<?> displayControl = loadDisplayControlClass();
                    createMethod = displayControl.getMethod(
                            "createVirtualDisplay", String.class, boolean.class);
                    destroyMethod = displayControl.getMethod(
                            "destroyVirtualDisplay", IBinder.class);
                }
            } else {
                Class<?> displayControl = loadDisplayControlClass();
                createMethod = displayControl.getMethod(
                        "createVirtualDisplay", String.class, boolean.class);
                destroyMethod = displayControl.getMethod(
                        "destroyVirtualDisplay", IBinder.class);
            }
            M_CREATE_DISPLAY = createMethod;
            M_DESTROY_DISPLAY = destroyMethod;

            Class<?> txClass = Class.forName("android.view.SurfaceControl$Transaction");
            TRANSACTION_CTOR = txClass.getConstructor();
            M_TX_SET_DISPLAY_SURFACE = txClass.getMethod(
                    "setDisplaySurface", IBinder.class, Surface.class);
            M_TX_SET_DISPLAY_LAYER_STACK = txClass.getMethod(
                    "setDisplayLayerStack", IBinder.class, int.class);
            M_TX_SET_DISPLAY_PROJECTION = txClass.getMethod(
                    "setDisplayProjection",
                    IBinder.class, int.class, Rect.class, Rect.class);
            M_TX_APPLY = txClass.getMethod("apply");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("SurfaceControl/DisplayControl reflection setup failed", e);
        }
    }

    private SurfaceControlReflect() {}

    private static Class<?> loadDisplayControlClass() throws ReflectiveOperationException {
        PathClassLoader servicesLoader = new PathClassLoader(
                SERVICES_JAR_PATH,
                SYSTEM_LIB_SEARCH_PATH,
                ClassLoader.getSystemClassLoader());
        Class<?> displayControl = servicesLoader.loadClass(DISPLAY_CONTROL_CLASS);
        try {
            Method loadLibrary0 = Runtime.class.getDeclaredMethod(
                    "loadLibrary0", Class.class, String.class);
            loadLibrary0.setAccessible(true);
            loadLibrary0.invoke(Runtime.getRuntime(), displayControl, "android_servers");
        } catch (Throwable ignored) {
            System.load(SYSTEM_LIB_ANDROID_SERVERS);
        }
        return displayControl;
    }

    static IBinder createDisplay(String name, boolean secure) {
        try {
            return (IBinder) M_CREATE_DISPLAY.invoke(null, name, secure);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void destroyDisplay(IBinder token) {
        try {
            M_DESTROY_DISPLAY.invoke(null, token);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a {@link android.view.SurfaceControl.Transaction}, configures the
     * virtual display projection atomically, and applies it.
     *
     * @param layerStack   layer-stack id of the physical display to capture
     *                     (0 = main display)
     * @param layerStackRect source region on the display layer stack
     * @param displayRect  destination region on the virtual display surface
     */
    static void configureDisplay(IBinder displayToken, Surface surface,
                                 int layerStack, Rect layerStackRect, Rect displayRect) {
        Object tx = null;
        try {
            tx = TRANSACTION_CTOR.newInstance();
            M_TX_SET_DISPLAY_SURFACE.invoke(tx, displayToken, surface);
            configureDisplayTransaction(tx, displayToken, layerStack, 0, layerStackRect, displayRect);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            if (tx instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) tx).close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void configureDisplayTransaction(Object tx, IBinder displayToken,
                                                    int layerStack,
                                                    int orientation,
                                                    Rect layerStackRect,
                                                    Rect displayRect)
            throws ReflectiveOperationException {
        M_TX_SET_DISPLAY_LAYER_STACK.invoke(tx, displayToken, layerStack);
        M_TX_SET_DISPLAY_PROJECTION.invoke(tx, displayToken, orientation, layerStackRect, displayRect);
        M_TX_APPLY.invoke(tx);
    }
}
