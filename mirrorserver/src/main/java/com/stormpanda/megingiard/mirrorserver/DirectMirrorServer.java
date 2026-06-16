package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.net.LocalServerSocket;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
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

        SurfaceReceiverBinder(CountDownLatch surfaceDelivered) {
            this.surfaceDelivered = surfaceDelivered;
        }

        public synchronized void release() {
            System.err.println("DirectMirrorServer: releasing " + activeDisplayTokens.size() + " displays");
            for (IBinder token : activeDisplayTokens) {
                try { SurfaceControlReflect.destroyDisplay(token); } catch (Throwable ignored) {}
            }
            activeDisplayTokens.clear();
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
            if (code != TRANSACTION_SET_SURFACE) return false;
            try {
                data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR);
                
                // Release all existing resources first before applying the new configuration
                release();

                int numSurfaces = data.readInt();
                System.err.println("DirectMirrorServer: receiving " + numSurfaces + " surfaces");
                
                if (numSurfaces == 0) {
                    System.err.println("DirectMirrorServer: app sent 0 surfaces");
                    reply.writeNoException();
                    reply.writeInt(1);
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
                        continue;
                    }

                    IBinder displayToken = SurfaceControlReflect.createDisplay("megingiard-direct-" + i, false);
                    Rect rect = new Rect(0, 0, w, h);
                    SurfaceControlReflect.configureDisplay(displayToken, surface, 0, rect, rect);
                    
                    activeDisplayTokens.add(displayToken);
                    activeSurfaces.add(surface);
                }

                surfaceDelivered.countDown();
                System.err.println("DirectMirrorServer: " + activeDisplayTokens.size() + " surfaces panned & configured");
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
    }
}