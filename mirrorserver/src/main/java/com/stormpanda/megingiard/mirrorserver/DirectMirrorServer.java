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
import java.util.concurrent.atomic.AtomicReference;

public final class DirectMirrorServer {
    private static final String DIRECT_SURFACE_SERVICE_NAME = "megingiard.direct.surface";
    private static final String DIRECT_SURFACE_DESCRIPTOR = "com.stormpanda.megingiard.mirrorserver.IDirectSurfaceReceiver";
    private static final int TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION;
    private static final long SURFACE_DELIVERY_TIMEOUT_MS = 5_000L;

    private DirectMirrorServer() {}

    public static void main(String[] args) {
        MirrorServer.bypassHiddenApiEnforcement();

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
        AtomicReference<Surface> surfaceRef = new AtomicReference<>();
        AtomicReference<IBinder> displayTokenRef = new AtomicReference<>();
        LocalServerSocket readinessSocket = null;
        try {
            addService(
                    DIRECT_SURFACE_SERVICE_NAME,
                    new SurfaceReceiverBinder(width, height, surfaceDelivered, surfaceRef, displayTokenRef));
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
            IBinder displayToken = displayTokenRef.get();
            if (displayToken != null) {
                try { SurfaceControlReflect.destroyDisplay(displayToken); } catch (Throwable ignored) {}
            }
            Surface surface = surfaceRef.get();
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) {}
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

    private static final class SurfaceReceiverBinder extends Binder {
        private final int width;
        private final int height;
        private final CountDownLatch surfaceDelivered;
        private final AtomicReference<Surface> surfaceRef;
        private final AtomicReference<IBinder> displayTokenRef;

        SurfaceReceiverBinder(
                int width,
                int height,
                CountDownLatch surfaceDelivered,
                AtomicReference<Surface> surfaceRef,
                AtomicReference<IBinder> displayTokenRef) {
            this.width = width;
            this.height = height;
            this.surfaceDelivered = surfaceDelivered;
            this.surfaceRef = surfaceRef;
            this.displayTokenRef = displayTokenRef;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DIRECT_SURFACE_DESCRIPTOR);
                return true;
            }
            if (code != TRANSACTION_SET_SURFACE) return false;
            try {
                data.enforceInterface(DIRECT_SURFACE_DESCRIPTOR);
                if (data.readInt() == 0) {
                    System.err.println("DirectMirrorServer: app sent no surface");
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }

                Surface surface = Surface.CREATOR.createFromParcel(data);
                if (surface == null || !surface.isValid()) {
                    System.err.println("DirectMirrorServer: app surface invalid");
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }

                IBinder displayToken = SurfaceControlReflect.createDisplay("megingiard-direct-mirror", false);
                Rect rect = new Rect(0, 0, width, height);
                SurfaceControlReflect.configureDisplay(displayToken, surface, 0, rect, rect);
                surfaceRef.set(surface);
                displayTokenRef.set(displayToken);
                surfaceDelivered.countDown();
                System.err.println("DirectMirrorServer: app surface accepted and display configured");
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