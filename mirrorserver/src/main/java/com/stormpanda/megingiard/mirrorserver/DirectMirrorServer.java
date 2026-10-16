package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.net.LocalServerSocket;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Surface;

import java.lang.reflect.Method;
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
            System.err.println("DirectMirrorServer: readiness socket bound, waiting for surface");
            if (!surfaceDelivered.await(SURFACE_DELIVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.err.println("DirectMirrorServer: timed out waiting for app surface");
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
        private Surface currentSurface;
        private IBinder currentDisplay;

        SurfaceReceiverBinder(CountDownLatch surfaceDelivered) {
            this.surfaceDelivered = surfaceDelivered;
        }

        public synchronized void release() {
            if (currentDisplay != null) {
                System.err.println("DirectMirrorServer: releasing display");
                try { SurfaceControlReflect.destroyDisplay(currentDisplay); } catch (Throwable ignored) {}
                currentDisplay = null;
            }
            if (currentSurface != null) {
                try { currentSurface.release(); } catch (Throwable ignored) {}
                currentSurface = null;
            }
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
                release();

                int hasSurface = data.readInt();
                if (hasSurface == 0) {
                    System.err.println("DirectMirrorServer: app cleared surface");
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
                if (hasSurface != 1) {
                    System.err.println("[DirectMirror] Invalid hasSurface flag: " + hasSurface);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeInt(0);
                    }
                    return true;
                }

                int w = data.readInt();
                int h = data.readInt();
                Surface surface = Surface.CREATOR.createFromParcel(data);
                if (surface == null || !surface.isValid() || w <= 0 || h <= 0) {
                    System.err.println("DirectMirrorServer: invalid surface or dimensions: " + w + "x" + h);
                    if (surface != null) {
                        try { surface.release(); } catch (Throwable ignored) {}
                    }
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }

                IBinder displayToken = null;
                try {
                    displayToken = SurfaceControlReflect.createDisplay("megingiard-direct-0", false);
                    Rect rect = new Rect(0, 0, w, h);
                    SurfaceControlReflect.configureDisplay(displayToken, surface, 0, rect, rect);
                    currentDisplay = displayToken;
                    currentSurface = surface;
                } catch (Throwable t) {
                    System.err.println("DirectMirrorServer: failed to configure display: " + t);
                    if (displayToken != null) {
                        try { SurfaceControlReflect.destroyDisplay(displayToken); } catch (Throwable ignored) {}
                    }
                    try { surface.release(); } catch (Throwable ignored) {}
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }

                surfaceDelivered.countDown();
                System.err.println("DirectMirrorServer: surface parsed & configured");
                reply.writeNoException();
                reply.writeInt(1);
                return true;
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: setSurface failed: " + t);
                t.printStackTrace();
                reply.writeNoException();
                reply.writeInt(0);
                return true;
            }
        }
    }
}