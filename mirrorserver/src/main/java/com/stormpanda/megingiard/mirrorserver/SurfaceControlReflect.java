package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection wrappers for privileged direct-to-Surface display mirroring across Android 13–16+.
 *
 * <ul>
 *   <li><b>API 33 (Android 13, e.g. AYN Thor):</b> Uses hidden
 *       {@code SurfaceControl.createDisplay(name, secure=false)} +
 *       {@code SurfaceControl.Transaction} ({@code setDisplaySurface},
 *       {@code setDisplayLayerStack(0)}, {@code setDisplayProjection}).</li>
 *   <li><b>API 34+ (Android 14–16+, e.g. Retroid Pocket Duo on SDK 36):</b>
 *       Android 14 removed {@code SurfaceControl.createDisplay}, and Android 15 QPR / 16
 *       enabled {@code reject_dupe_layerstacks: true} in SurfaceFlinger (filtering internal
 *       Display 0 layers with {@code toInternalDisplay=true} so raw {@code DisplayControl}
 *       virtual displays on {@code layerStack=0} render {@code 0 Layers}). Uses the framework
 *       {@code @TestApi} helper
 *       {@code DisplayManager.createVirtualDisplay(name, width, height, displayIdToMirror, surface)},
 *       which allocates a private {@code VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR} virtual display on its
 *       own dedicated {@code layerStack} and mirrors Display 0 via {@code WindowManagerService}'s
 *       {@code ContentRecorder} ({@code SurfaceControl.mirrorSurface}).</li>
 * </ul>
 */
final class SurfaceControlReflect {

    static final int SDK_ANDROID_14 = 34;
    private static final int DISPLAY_ORIENTATION_0 = 0;

    enum BackendKind {
        SURFACE_CONTROL_LEGACY,
        DISPLAY_MANAGER_MIRROR,
    }

    interface MirrorSession extends AutoCloseable {
        @Override
        void close();
    }

    private static Method sDisplayManagerCreateVirtualDisplay;
    private static LegacySurfaceControlBindings sLegacyBindings;

    private SurfaceControlReflect() {}

    static BackendKind selectBackendKind(int sdkInt) {
        return sdkInt >= SDK_ANDROID_14
                ? BackendKind.DISPLAY_MANAGER_MIRROR
                : BackendKind.SURFACE_CONTROL_LEGACY;
    }

    static <T> T requireNonNullVirtualDisplay(T virtualDisplay, String name) {
        if (virtualDisplay == null) {
            throw new IllegalStateException(
                    "DisplayManager.createVirtualDisplay returned null for " + name);
        }
        return virtualDisplay;
    }

    static MirrorSession openMirrorSession(
            String name,
            int width,
            int height,
            int displayIdToMirror,
            Surface surface) {
        if (selectBackendKind(Build.VERSION.SDK_INT) == BackendKind.DISPLAY_MANAGER_MIRROR) {
            return openDisplayManagerSession(name, width, height, displayIdToMirror, surface);
        }
        return openLegacyOrFallbackSession(name, width, height, displayIdToMirror, surface);
    }

    private static MirrorSession openDisplayManagerSession(
            String name,
            int width,
            int height,
            int displayIdToMirror,
            Surface surface) {
        try {
            Method createMethod = getDisplayManagerCreateMethod();
            VirtualDisplay rawDisplay = (VirtualDisplay) createMethod.invoke(
                    null, name, width, height, displayIdToMirror, surface);
            final VirtualDisplay virtualDisplay = requireNonNullVirtualDisplay(rawDisplay, name);
            return new MirrorSession() {
                private boolean closed;

                @Override
                public synchronized void close() {
                    if (closed) return;
                    closed = true;
                    virtualDisplay.release();
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("DisplayManager.createVirtualDisplay failed", e);
        }
    }

    private static MirrorSession openLegacyOrFallbackSession(
            String name,
            int width,
            int height,
            int displayIdToMirror,
            Surface surface) {
        LegacySurfaceControlBindings bindings;
        try {
            bindings = getLegacyBindings();
        } catch (NoSuchMethodException missingLegacyApi) {
            return openDisplayManagerSession(name, width, height, displayIdToMirror, surface);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("SurfaceControl reflection setup failed", e);
        }

        IBinder token = null;
        try {
            token = (IBinder) bindings.createDisplay.invoke(null, name, false);
            if (token == null) {
                throw new IllegalStateException(
                        "SurfaceControl.createDisplay returned null for " + name);
            }
            Rect rect = new Rect(0, 0, width, height);
            configureLegacyDisplay(bindings, token, surface, displayIdToMirror, rect, rect);
            final IBinder activeToken = token;
            final Method destroyMethod = bindings.destroyDisplay;
            return new MirrorSession() {
                private boolean closed;

                @Override
                public synchronized void close() {
                    if (closed) return;
                    closed = true;
                    try {
                        destroyMethod.invoke(null, activeToken);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } catch (Throwable t) {
            if (token != null) {
                try {
                    bindings.destroyDisplay.invoke(null, token);
                } catch (Throwable ignored) {
                }
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException("Failed to configure legacy SurfaceControl display", t);
        }
    }

    private static void configureLegacyDisplay(
            LegacySurfaceControlBindings bindings,
            IBinder displayToken,
            Surface surface,
            int layerStack,
            Rect layerStackRect,
            Rect displayRect) throws ReflectiveOperationException {
        Object tx = null;
        try {
            tx = bindings.transactionCtor.newInstance();
            bindings.setDisplaySurface.invoke(tx, displayToken, surface);
            bindings.setDisplayLayerStack.invoke(tx, displayToken, layerStack);
            bindings.setDisplayProjection.invoke(
                    tx, displayToken, DISPLAY_ORIENTATION_0, layerStackRect, displayRect);
            bindings.apply.invoke(tx);
        } finally {
            if (tx instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) tx).close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static synchronized Method getDisplayManagerCreateMethod()
            throws ReflectiveOperationException {
        if (sDisplayManagerCreateVirtualDisplay == null) {
            sDisplayManagerCreateVirtualDisplay = DisplayManager.class.getMethod(
                    "createVirtualDisplay",
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    Surface.class);
        }
        return sDisplayManagerCreateVirtualDisplay;
    }

    private static synchronized LegacySurfaceControlBindings getLegacyBindings()
            throws ReflectiveOperationException {
        if (sLegacyBindings == null) {
            Class<?> scClass = Class.forName("android.view.SurfaceControl");
            Method createDisplay = scClass.getMethod(
                    "createDisplay", String.class, boolean.class);
            Method destroyDisplay = scClass.getMethod(
                    "destroyDisplay", IBinder.class);

            Class<?> txClass = Class.forName("android.view.SurfaceControl$Transaction");
            Constructor<?> txCtor = txClass.getConstructor();
            Method setDisplaySurface = txClass.getMethod(
                    "setDisplaySurface", IBinder.class, Surface.class);
            Method setDisplayLayerStack = txClass.getMethod(
                    "setDisplayLayerStack", IBinder.class, int.class);
            Method setDisplayProjection = txClass.getMethod(
                    "setDisplayProjection",
                    IBinder.class, int.class, Rect.class, Rect.class);
            Method apply = txClass.getMethod("apply");

            sLegacyBindings = new LegacySurfaceControlBindings(
                    createDisplay,
                    destroyDisplay,
                    txCtor,
                    setDisplaySurface,
                    setDisplayLayerStack,
                    setDisplayProjection,
                    apply);
        }
        return sLegacyBindings;
    }

    private static final class LegacySurfaceControlBindings {
        final Method createDisplay;
        final Method destroyDisplay;
        final Constructor<?> transactionCtor;
        final Method setDisplaySurface;
        final Method setDisplayLayerStack;
        final Method setDisplayProjection;
        final Method apply;

        LegacySurfaceControlBindings(
                Method createDisplay,
                Method destroyDisplay,
                Constructor<?> transactionCtor,
                Method setDisplaySurface,
                Method setDisplayLayerStack,
                Method setDisplayProjection,
                Method apply) {
            this.createDisplay = createDisplay;
            this.destroyDisplay = destroyDisplay;
            this.transactionCtor = transactionCtor;
            this.setDisplaySurface = setDisplaySurface;
            this.setDisplayLayerStack = setDisplayLayerStack;
            this.setDisplayProjection = setDisplayProjection;
            this.apply = apply;
        }
    }
}
