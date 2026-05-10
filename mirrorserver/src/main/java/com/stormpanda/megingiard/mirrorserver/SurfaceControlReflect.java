package com.stormpanda.megingiard.mirrorserver;

import android.os.IBinder;
import android.view.Surface;

import java.lang.reflect.Method;

/**
 * Reflection wrappers for hidden {@code android.view.SurfaceControl} APIs.
 *
 * <p>API 33 (Android 13) only — Megingiard targets a single device (AYN
 * Thor) so we do not branch on SDK versions or fall back to alternate
 * signatures.</p>
 *
 * <p>The signatures used here mirror Android 13 AOSP source:
 * <pre>
 *   public static IBinder createDisplay(String name, boolean secure);
 *   public static void destroyDisplay(IBinder displayToken);
 *   public static void openTransaction();
 *   public static void closeTransaction();
 *   public static void setDisplaySurface(IBinder displayToken, Surface surface);
 *   public static void setDisplayLayerStack(IBinder displayToken, int layerStack);
 *   public static void setDisplayProjection(IBinder displayToken, int orientation,
 *       Rect layerStackRect, Rect displayRect);
 *   public static void setDisplaySize(IBinder displayToken, int width, int height);
 * </pre>
 * </p>
 */
final class SurfaceControlReflect {

    private static final Class<?> SURFACE_CONTROL;
    private static final Method M_CREATE_DISPLAY;
    private static final Method M_DESTROY_DISPLAY;
    private static final Method M_OPEN_TRANSACTION;
    private static final Method M_CLOSE_TRANSACTION;
    private static final Method M_SET_DISPLAY_SURFACE;
    private static final Method M_SET_DISPLAY_LAYER_STACK;
    private static final Method M_SET_DISPLAY_PROJECTION;
    private static final Method M_SET_DISPLAY_SIZE;

    static {
        try {
            SURFACE_CONTROL = Class.forName("android.view.SurfaceControl");
            M_CREATE_DISPLAY = SURFACE_CONTROL.getMethod("createDisplay", String.class, boolean.class);
            M_DESTROY_DISPLAY = SURFACE_CONTROL.getMethod("destroyDisplay", IBinder.class);
            M_OPEN_TRANSACTION = SURFACE_CONTROL.getMethod("openTransaction");
            M_CLOSE_TRANSACTION = SURFACE_CONTROL.getMethod("closeTransaction");
            M_SET_DISPLAY_SURFACE = SURFACE_CONTROL.getMethod("setDisplaySurface", IBinder.class, Surface.class);
            M_SET_DISPLAY_LAYER_STACK = SURFACE_CONTROL.getMethod("setDisplayLayerStack", IBinder.class, int.class);
            M_SET_DISPLAY_PROJECTION = SURFACE_CONTROL.getMethod(
                "setDisplayProjection", IBinder.class, int.class,
                android.graphics.Rect.class, android.graphics.Rect.class);
            M_SET_DISPLAY_SIZE = SURFACE_CONTROL.getMethod(
                "setDisplaySize", IBinder.class, int.class, int.class);
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

    static void openTransaction() {
        try {
            M_OPEN_TRANSACTION.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void closeTransaction() {
        try {
            M_CLOSE_TRANSACTION.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void setDisplaySurface(IBinder token, Surface surface) {
        try {
            M_SET_DISPLAY_SURFACE.invoke(null, token, surface);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void setDisplayLayerStack(IBinder token, int layerStack) {
        try {
            M_SET_DISPLAY_LAYER_STACK.invoke(null, token, layerStack);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void setDisplayProjection(IBinder token, int orientation,
                                     android.graphics.Rect layerStack, android.graphics.Rect display) {
        try {
            M_SET_DISPLAY_PROJECTION.invoke(null, token, orientation, layerStack, display);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void setDisplaySize(IBinder token, int width, int height) {
        try {
            M_SET_DISPLAY_SIZE.invoke(null, token, width, height);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
