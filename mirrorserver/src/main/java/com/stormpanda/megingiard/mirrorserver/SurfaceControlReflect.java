package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.os.IBinder;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection wrappers for hidden {@code android.view.SurfaceControl} APIs.
 *
 * <p>Targets API 31+ (Android 12+): uses {@code SurfaceControl.Transaction}
 * for display configuration, replacing the removed static
 * {@code openTransaction}/{@code closeTransaction}/{@code setDisplay*} methods.</p>
 *
 * <p>The {@code setDisplayLayerStack} signature changed in Android 12 from
 * {@code (IBinder, int)} to {@code (IBinder, LayerStack)}. Both variants are
 * probed at static-init time so the code works on either API level.</p>
 */
final class SurfaceControlReflect {

    private static final Class<?> SURFACE_CONTROL;
    private static final Method M_CREATE_DISPLAY;
    private static final Method M_DESTROY_DISPLAY;

    // SurfaceControl.Transaction — replaces openTransaction/closeTransaction on API 31+
    private static final Constructor<?> TRANSACTION_CTOR;
    private static final Method M_TX_SET_DISPLAY_SURFACE;
    private static final Method M_TX_SET_DISPLAY_LAYER_STACK;
    private static final Method M_TX_SET_DISPLAY_PROJECTION;
    private static final Method M_TX_APPLY;

    /**
     * {@code LayerStack.from(int)} — non-null only on Android 12+ where
     * {@code setDisplayLayerStack} takes a {@code LayerStack} object rather than
     * a plain {@code int}.
     */
    private static final Method M_LAYER_STACK_FROM;

    static {
        try {
            SURFACE_CONTROL = Class.forName("android.view.SurfaceControl");
            M_CREATE_DISPLAY = SURFACE_CONTROL.getMethod(
                    "createDisplay", String.class, boolean.class);
            M_DESTROY_DISPLAY = SURFACE_CONTROL.getMethod(
                    "destroyDisplay", IBinder.class);

            Class<?> txClass = Class.forName("android.view.SurfaceControl$Transaction");
            TRANSACTION_CTOR = txClass.getConstructor();
            M_TX_SET_DISPLAY_SURFACE = txClass.getMethod(
                    "setDisplaySurface", IBinder.class, Surface.class);
            M_TX_SET_DISPLAY_PROJECTION = txClass.getMethod(
                    "setDisplayProjection",
                    IBinder.class, int.class, Rect.class, Rect.class);
            M_TX_APPLY = txClass.getMethod("apply");

            // setDisplayLayerStack signature changed in Android 12 from int to LayerStack.
            // Try the int form first (Android ≤11); fall back to LayerStack (Android 12+).
            Method setLayerStack;
            Method layerStackFrom = null;
            try {
                setLayerStack = txClass.getMethod(
                        "setDisplayLayerStack", IBinder.class, int.class);
            } catch (NoSuchMethodException ignored) {
                Class<?> lsClass = Class.forName("android.view.SurfaceControl$LayerStack");
                layerStackFrom = lsClass.getMethod("from", int.class);
                setLayerStack = txClass.getMethod(
                        "setDisplayLayerStack", IBinder.class, lsClass);
            }
            M_TX_SET_DISPLAY_LAYER_STACK = setLayerStack;
            M_LAYER_STACK_FROM = layerStackFrom;

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("SurfaceControl reflection setup failed", e);
        }
    }

    private SurfaceControlReflect() {}

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
     * <p>Replaces the former {@code openTransaction / setDisplay* / closeTransaction}
     * sequence that was removed in Android 12 (API 31).</p>
     *
     * @param layerStack   layer-stack id of the physical display to capture
     *                     (0 = main display)
     * @param layerStackRect source region on the display layer stack
     * @param displayRect  destination region on the virtual display surface
     */
    static void configureDisplay(IBinder displayToken, Surface surface,
                                 int layerStack, Rect layerStackRect, Rect displayRect) {
        try {
            Object tx = TRANSACTION_CTOR.newInstance();
            M_TX_SET_DISPLAY_SURFACE.invoke(tx, displayToken, surface);
            configureDisplayTransaction(tx, displayToken, layerStack, 0, layerStackRect, displayRect);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void configureDisplayTransaction(Object tx, IBinder displayToken,
                                                    int layerStack,
                                                    int orientation,
                                                    Rect layerStackRect,
                                                    Rect displayRect)
            throws ReflectiveOperationException {
        if (M_LAYER_STACK_FROM != null) {
            Object ls = M_LAYER_STACK_FROM.invoke(null, layerStack);
            M_TX_SET_DISPLAY_LAYER_STACK.invoke(tx, displayToken, ls);
        } else {
            M_TX_SET_DISPLAY_LAYER_STACK.invoke(tx, displayToken, layerStack);
        }
        M_TX_SET_DISPLAY_PROJECTION.invoke(tx, displayToken, orientation, layerStackRect, displayRect);
        M_TX_APPLY.invoke(tx);
    }
}
