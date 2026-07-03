package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.net.LocalServerSocket;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DirectMirrorServer {
    private static final String DIRECT_SURFACE_SERVICE_NAME = "megingiard.direct.surface";
    private static final String DIRECT_SURFACE_DESCRIPTOR = "com.stormpanda.megingiard.mirrorserver.IDirectSurfaceReceiver";
    private static final int TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_CREATE_SPLIT_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_LAUNCH_GAME = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int TRANSACTION_INJECT_TOUCH = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final long SURFACE_DELIVERY_TIMEOUT_MS = 5_000L;

    private DirectMirrorServer() {}

    public static void main(String[] args) {
        bypassHiddenApiEnforcement();

        if (args.length < 3) {
            System.err.println("usage: DirectMirrorServer <socket> <w> <h>");
            System.exit(2);
            return;
        }

        String socketName = args[0];
        int width = Integer.parseInt(args[1]);
        int height = Integer.parseInt(args[2]);
        System.err.println("DirectMirrorServer: starting socket=" + socketName + " size=" + width + "x" + height);

        CountDownLatch surfaceDelivered = new CountDownLatch(1);
        LocalServerSocket readinessSocket = null;
        SurfaceReceiverBinder binder = null;
        try {
            binder = new SurfaceReceiverBinder(surfaceDelivered);
            addService(DIRECT_SURFACE_SERVICE_NAME, binder);
            System.err.println("DirectMirrorServer: registered " + DIRECT_SURFACE_SERVICE_NAME);

            readinessSocket = new LocalServerSocket(socketName);
            System.err.println("DirectMirrorServer: readiness socket bound, waiting for surfaces");
            if (!surfaceDelivered.await(SURFACE_DELIVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.err.println("DirectMirrorServer: timed out waiting for app surfaces");
                System.exit(1);
                return;
            }
            System.err.println("DirectMirrorServer: surface configured, entering wait loop");

            synchronized (DirectMirrorServer.class) {
                DirectMirrorServer.class.wait();
            }
        } catch (Throwable t) {
            System.err.println("direct mirror ended: " + t);
            t.printStackTrace();
            System.exit(1);
        } finally {
            if (binder != null) {
                binder.release();
            }
            if (readinessSocket != null) {
                try { readinessSocket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void addService(String name, IBinder binder) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method addService = serviceManager.getDeclaredMethod("addService", String.class, IBinder.class);
        addService.setAccessible(true);
        addService.invoke(null, name, binder);
    }

    private static void bypassHiddenApiEnforcement() {
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object vmRuntime = getRuntime.invoke(null);
            Method setHiddenApiExemptions = vmRuntimeClass.getDeclaredMethod(
                    "setHiddenApiExemptions", String[].class);
            setHiddenApiExemptions.setAccessible(true);
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{""}});
        } catch (Exception e) {
            System.err.println("Warning: hidden-API bypass failed: " + e);
        }
    }

    private static final class SurfaceReceiverBinder extends Binder {
        private final CountDownLatch surfaceDelivered;
        private final List<Surface> activeSurfaces = new ArrayList<>();
        private final List<IBinder> activeDisplayTokens = new ArrayList<>();
        private final List<IBinder> activeCallbacks = new ArrayList<>();

        // Multi-touch state tracking variables
        private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[10];
        private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[10];
        private final int[] activeSlots = new int[10]; // stores displayId or -1 if inactive
        private long downTime = 0;

        SurfaceReceiverBinder(CountDownLatch surfaceDelivered) {
            this.surfaceDelivered = surfaceDelivered;
            for (int i = 0; i < 10; i++) {
                pointerProperties[i] = new MotionEvent.PointerProperties();
                pointerProperties[i].id = i;
                pointerProperties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                pointerCoords[i] = new MotionEvent.PointerCoords();
                activeSlots[i] = -1;
            }
        }

        public synchronized void release() {
            System.err.println("DirectMirrorServer: releasing " + activeDisplayTokens.size() + " displays and " + activeCallbacks.size() + " split-plays");
            for (IBinder token : activeDisplayTokens) {
                try { SurfaceControlReflect.destroyDisplay(token); } catch (Throwable ignored) {}
            }
            activeDisplayTokens.clear();
            for (IBinder cb : activeCallbacks) {
                try { releaseVirtualDisplayReflection(cb); } catch (Throwable ignored) {}
            }
            activeCallbacks.clear();
            for (Surface surface : activeSurfaces) {
                try { surface.release(); } catch (Throwable ignored) {}
            }
            activeSurfaces.clear();
        }

        @Override
        protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DIRECT_SURFACE_DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_SET_SURFACE) {
                try {
                    data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR);
                    
                    // Release all existing resources first before applying the new configuration
                    release();

                    int numSurfaces = data.readInt();
                    System.err.println("DirectMirrorServer: receiving " + numSurfaces + " surfaces");
                    
                    if (numSurfaces == 0) {
                        System.err.println("DirectMirrorServer: app sent 0 surfaces");
                        reply.writeNoException();
                        reply.writeInt(0);
                        return true;
                    }

                    for (int i = 0; i < numSurfaces; i++) {
                        int w = data.readInt();
                        int h = data.readInt();
                        if (data.readInt() == 0) {
                            System.err.println("DirectMirrorServer: surface at index " + i + " is null");
                            continue;
                        }
                        Surface surface = Surface.CREATOR.createFromParcel(data);
                        if (surface == null || !surface.isValid()) {
                            System.err.println("DirectMirrorServer: surface at index " + i + " is invalid");
                            if (surface != null) {
                                try { surface.release(); } catch (Throwable ignored) {}
                            }
                            continue;
                        }

                        if (w <= 0 || h <= 0) {
                            System.err.println("DirectMirrorServer: surface at index " + i + " has invalid dimensions: " + w + "x" + h);
                            try { surface.release(); } catch (Throwable ignored) {}
                            continue;
                        }

                        IBinder displayToken = null;
                        try {
                            displayToken = SurfaceControlReflect.createDisplay("megingiard-direct-" + i, false);
                            Rect rect = new Rect(0, 0, w, h);
                            SurfaceControlReflect.configureDisplay(displayToken, surface, 0, rect, rect);
                            activeDisplayTokens.add(displayToken);
                            activeSurfaces.add(surface);
                        } catch (Throwable t) {
                            System.err.println("DirectMirrorServer: failed to configure display for surface index " + i + ": " + t);
                            if (displayToken != null) {
                                try { SurfaceControlReflect.destroyDisplay(displayToken); } catch (Throwable ignored) {}
                            }
                            try { surface.release(); } catch (Throwable ignored) {}
                        }
                    }

                    if (activeDisplayTokens.isEmpty()) {
                        System.err.println("DirectMirrorServer: no valid surfaces accepted");
                        reply.writeNoException();
                        reply.writeInt(0);
                        return true;
                    }

                    surfaceDelivered.countDown();
                    System.err.println("DirectMirrorServer: " + activeDisplayTokens.size() + " surfaces parsed & configured");
                    reply.writeNoException();
                    reply.writeInt(1);
                    return true;
                } catch (Throwable t) {
                    System.err.println("DirectMirrorServer: setSurfaces failed: " + t);
                    t.printStackTrace();
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
            }

            if (code == TRANSACTION_CREATE_SPLIT_DISPLAY) {
                try {
                    data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR);
                    int w = data.readInt();
                    int h = data.readInt();
                    int dpi = data.readInt();
                    int displayFlags = data.readInt();
                    String name = data.readString();
                    Surface surface = null;
                    if (data.readInt() != 0) {
                        surface = Surface.CREATOR.createFromParcel(data);
                    }
                    if (surface == null || !surface.isValid()) {
                        System.err.println("DirectMirrorServer: invalid surface for split play");
                        reply.writeNoException();
                        reply.writeInt(-1);
                        return true;
                    }
                    
                    int displayId = createVirtualDisplayReflection(name, w, h, dpi, surface, displayFlags);
                    IBinder callback = new Binder();
                    if (displayId >= 0) {
                        activeCallbacks.add(callback);
                        activeSurfaces.add(surface);
                        surfaceDelivered.countDown();
                    } else {
                        try { surface.release(); } catch (Throwable ignored) {}
                    }
                    reply.writeNoException();
                    reply.writeInt(displayId);
                    reply.writeStrongBinder(callback);
                    return true;
                } catch (Throwable t) {
                    System.err.println("DirectMirrorServer: CREATE_SPLIT_DISPLAY failed: " + t);
                    t.printStackTrace();
                    reply.writeNoException();
                    reply.writeInt(-1);
                    return true;
                }
            }

            if (code == TRANSACTION_LAUNCH_GAME) {
                try {
                    data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR);
                    String comp = data.readString();
                    int displayId = data.readInt();
                    boolean ok = launchGameReflection(comp, displayId);
                    reply.writeNoException();
                    reply.writeInt(ok ? 1 : 0);
                    return true;
                } catch (Throwable t) {
                    System.err.println("DirectMirrorServer: LAUNCH_GAME failed: " + t);
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
            }

            if (code == TRANSACTION_INJECT_TOUCH) {
                try {
                    data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR);
                    int displayId = data.readInt();
                    int slot = data.readInt();
                    int action = data.readInt();
                    float x = data.readFloat();
                    float y = data.readFloat();
                    boolean ok = injectTouchReflection(displayId, slot, action, x, y);
                    reply.writeNoException();
                    reply.writeInt(ok ? 1 : 0);
                    return true;
                } catch (Throwable t) {
                    System.err.println("DirectMirrorServer: INJECT_TOUCH failed: " + t);
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
            }

            return false;
        }

        private int createVirtualDisplayReflection(String name, int width, int height, int dpi, Surface surface, int flags) {
            try {
                IBinder displayBinder = (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class)
                        .invoke(null, "display");
                Object iDisplayManager = Class.forName("android.hardware.display.IDisplayManager$Stub")
                        .getMethod("asInterface", IBinder.class)
                        .invoke(null, displayBinder);

                Method createVirtualDisplayMethod = null;
                for (Method m : iDisplayManager.getClass().getMethods()) {
                    if (m.getName().equals("createVirtualDisplay")) {
                        createVirtualDisplayMethod = m;
                        break;
                    }
                }

                if (createVirtualDisplayMethod == null) {
                    System.err.println("DirectMirrorServer: createVirtualDisplay method not found on IDisplayManager");
                    return -1;
                }

                IBinder callback = new Binder();
                Object result = createVirtualDisplayMethod.invoke(
                        iDisplayManager,
                        callback,
                        null, // IMediaProjection
                        "com.stormpanda.megingiard", // packageName
                        name,
                        width,
                        height,
                        dpi,
                        surface,
                        flags,
                        null // uniqueId
                );
                
                if (result instanceof Integer) {
                    return (Integer) result;
                } else {
                    System.err.println("DirectMirrorServer: createVirtualDisplay returned non-integer: " + result);
                    return -1;
                }
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: failed to create virtual display via reflection: " + t);
                t.printStackTrace();
                return -1;
            }
        }

        private void releaseVirtualDisplayReflection(IBinder callback) {
            try {
                IBinder displayBinder = (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class)
                        .invoke(null, "display");
                Object iDisplayManager = Class.forName("android.hardware.display.IDisplayManager$Stub")
                        .getMethod("asInterface", IBinder.class)
                        .invoke(null, displayBinder);

                Method releaseMethod = null;
                for (Method m : iDisplayManager.getClass().getMethods()) {
                    if (m.getName().equals("releaseVirtualDisplay")) {
                        releaseMethod = m;
                        break;
                    }
                }
                if (releaseMethod != null) {
                    releaseMethod.invoke(iDisplayManager, callback);
                    System.err.println("DirectMirrorServer: released virtual display");
                }
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: failed to release virtual display: " + t);
            }
        }

        private boolean launchGameReflection(String componentName, int displayId) {
            try {
                String cmd = "am start -n " + componentName + " --display " + displayId;
                System.err.println("DirectMirrorServer: running command: " + cmd);
                Process process = Runtime.getRuntime().exec(cmd);
                process.waitFor();
                int exitVal = process.exitValue();
                System.err.println("DirectMirrorServer: am start exit value: " + exitVal);
                return exitVal == 0;
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: failed to launch game: " + t);
                return false;
            }
        }

        private synchronized boolean injectTouchReflection(int displayId, int slot, int action, float x, float y) {
            try {
                if (slot < 0 || slot >= 10) return false;
                
                long now = SystemClock.uptimeMillis();
                
                IBinder inputBinder = (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class)
                        .invoke(null, "input");
                Object iInputManager = Class.forName("com.android.server.input.IInputManager$Stub")
                        .getMethod("asInterface", IBinder.class)
                        .invoke(null, inputBinder);
                Method injectInputEventMethod = iInputManager.getClass().getMethod("injectInputEvent", 
                        Class.forName("android.view.InputEvent"), int.class);

                if (action == 0) { // DOWN
                    if (downTime == 0) {
                        downTime = now;
                    }
                    activeSlots[slot] = displayId;
                    pointerCoords[slot].x = x;
                    pointerCoords[slot].y = y;
                } else if (action == 1) { // MOVE
                    pointerCoords[slot].x = x;
                    pointerCoords[slot].y = y;
                } else if (action == 2) { // UP
                    activeSlots[slot] = -1;
                    pointerCoords[slot].x = x;
                    pointerCoords[slot].y = y;
                }

                int pointerCount = 0;
                for (int i = 0; i < 10; i++) {
                    if (activeSlots[i] == displayId || (action == 2 && i == slot)) {
                        pointerCount++;
                    }
                }

                if (pointerCount == 0) {
                    downTime = 0;
                    return true;
                }

                MotionEvent.PointerProperties[] activeProps = new MotionEvent.PointerProperties[pointerCount];
                MotionEvent.PointerCoords[] activeCoords = new MotionEvent.PointerCoords[pointerCount];
                
                int index = 0;
                int actionIndex = -1;
                for (int i = 0; i < 10; i++) {
                    if (activeSlots[i] == displayId || (action == 2 && i == slot)) {
                        activeProps[index] = pointerProperties[i];
                        activeCoords[index] = pointerCoords[i];
                        if (i == slot) {
                            actionIndex = index;
                        }
                        index++;
                    }
                }

                int eventAction = 0;
                if (action == 0) { // DOWN
                    if (pointerCount == 1) {
                        eventAction = MotionEvent.ACTION_DOWN;
                    } else {
                        eventAction = MotionEvent.ACTION_POINTER_DOWN | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
                    }
                } else if (action == 1) { // MOVE
                    eventAction = MotionEvent.ACTION_MOVE;
                } else if (action == 2) { // UP
                    if (pointerCount == 1) {
                        eventAction = MotionEvent.ACTION_UP;
                    } else {
                        eventAction = MotionEvent.ACTION_POINTER_UP | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
                    }
                }

                MotionEvent event = MotionEvent.obtain(
                        downTime,
                        now,
                        eventAction,
                        pointerCount,
                        activeProps,
                        activeCoords,
                        0, // metaState
                        0, // buttonState
                        1.0f, 1.0f, // precision
                        0, // deviceId
                        0, // edgeFlags
                        0x00001002, // source (SOURCE_TOUCHSCREEN = 0x00001002)
                        0 // flags
                );

                Method setDisplayIdMethod = MotionEvent.class.getMethod("setDisplayId", int.class);
                setDisplayIdMethod.invoke(event, displayId);

                boolean ok = (Boolean) injectInputEventMethod.invoke(iInputManager, event, 2);
                event.recycle();
                
                if (action == 2 && pointerCount == 1) {
                    downTime = 0;
                }
                
                return ok;
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: failed to inject touch event: " + t);
                t.printStackTrace();
                return false;
            }
        }
    }
}