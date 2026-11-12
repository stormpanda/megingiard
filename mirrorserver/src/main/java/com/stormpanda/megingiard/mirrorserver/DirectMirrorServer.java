package com.stormpanda.megingiard.mirrorserver;

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
    private static final String DIRECT_SURFACE_DESCRIPTOR =
            "com.stormpanda.megingiard.mirrorserver.IDirectSurfaceReceiver";
    private static final String MIRROR_DISPLAY_NAME = "megingiard-direct-0";
    private static final int PRIMARY_DISPLAY_ID = 0;
    private static final int MIN_ARGS_COUNT = 3;
    private static final int EXIT_CODE_USAGE = 2;
    private static final int EXIT_CODE_FAILURE = 1;
    private static final int HAS_SURFACE_CLEAR = 0;
    private static final int HAS_SURFACE_PRESENT = 1;
    private static final int REPLY_FAILURE = 0;
    private static final int REPLY_SUCCESS = 1;
    private static final int TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION;
    private static final long SURFACE_DELIVERY_TIMEOUT_MS = 5_000L;

    interface SurfaceHandle {
        boolean isValid();

        void release();
    }

    interface SessionOpener<S extends SurfaceHandle> {
        SurfaceControlReflect.MirrorSession open(
                String name,
                int width,
                int height,
                int displayIdToMirror,
                S surface);
    }

    static final class SessionCoordinator<S extends SurfaceHandle> {
        private final SessionOpener<S> sessionOpener;
        private SurfaceControlReflect.MirrorSession currentSession;
        private S currentSurface;

        SessionCoordinator(SessionOpener<S> sessionOpener) {
            this.sessionOpener = sessionOpener;
        }

        synchronized void release() {
            if (currentSession != null) {
                try {
                    currentSession.close();
                } catch (Throwable ignored) {
                }
                currentSession = null;
            }
            if (currentSurface != null) {
                try {
                    currentSurface.release();
                } catch (Throwable ignored) {
                }
                currentSurface = null;
            }
        }

        synchronized boolean applySurface(S surface, int width, int height) {
            release();
            if (surface == null || !surface.isValid() || width <= 0 || height <= 0) {
                System.err.println(
                        "DirectMirrorServer: invalid surface or dimensions: " + width + "x" + height);
                if (surface != null) {
                    try {
                        surface.release();
                    } catch (Throwable ignored) {
                    }
                }
                return false;
            }

            SurfaceControlReflect.MirrorSession session = null;
            try {
                session = sessionOpener.open(
                        MIRROR_DISPLAY_NAME, width, height, PRIMARY_DISPLAY_ID, surface);
                currentSession = session;
                currentSurface = surface;
                return true;
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: failed to configure display: " + t);
                if (session != null) {
                    try {
                        session.close();
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    surface.release();
                } catch (Throwable ignored) {
                }
                return false;
            }
        }
    }

    private DirectMirrorServer() {}

    public static void main(String[] args) {
        bypassHiddenApiEnforcement();

        if (args.length < MIN_ARGS_COUNT) {
            System.err.println("usage: DirectMirrorServer <socket> <w> <h>");
            System.exit(EXIT_CODE_USAGE);
            return;
        }

        String socketName = args[0];
        int width = Integer.parseInt(args[1]);
        int height = Integer.parseInt(args[2]);
        System.err.println(
                "DirectMirrorServer: starting socket=" + socketName + " size=" + width + "x" + height);

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
                System.exit(EXIT_CODE_FAILURE);
                return;
            }
            System.err.println("DirectMirrorServer: surface configured, entering wait loop");

            synchronized (DirectMirrorServer.class) {
                DirectMirrorServer.class.wait();
            }
        } catch (Throwable t) {
            System.err.println("direct mirror ended: " + t);
            t.printStackTrace();
            System.exit(EXIT_CODE_FAILURE);
        } finally {
            if (binder != null) {
                binder.release();
            }
            if (readinessSocket != null) {
                try {
                    readinessSocket.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void addService(String name, IBinder binder) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method addService =
                serviceManager.getDeclaredMethod("addService", String.class, IBinder.class);
        addService.setAccessible(true);
        addService.invoke(null, name, binder);
    }

    private static void bypassHiddenApiEnforcement() {
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object vmRuntime = getRuntime.invoke(null);
            Method setHiddenApiExemptions =
                    vmRuntimeClass.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setHiddenApiExemptions.setAccessible(true);
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{""}});
        } catch (Exception e) {
            System.err.println("Warning: hidden-API bypass failed: " + e);
        }
    }

    private static final class AndroidSurfaceHandle implements SurfaceHandle {
        final Surface surface;

        AndroidSurfaceHandle(Surface surface) {
            this.surface = surface;
        }

        @Override
        public boolean isValid() {
            return surface != null && surface.isValid();
        }

        @Override
        public void release() {
            if (surface != null) {
                surface.release();
            }
        }
    }

    private static final class SurfaceReceiverBinder extends Binder {
        private final CountDownLatch surfaceDelivered;
        private final SessionCoordinator<AndroidSurfaceHandle> coordinator =
                new SessionCoordinator<>(
                        (name, w, h, displayId, handle) ->
                                SurfaceControlReflect.openMirrorSession(
                                        name, w, h, displayId, handle.surface));

        SurfaceReceiverBinder(CountDownLatch surfaceDelivered) {
            this.surfaceDelivered = surfaceDelivered;
        }

        public synchronized void release() {
            System.err.println("DirectMirrorServer: releasing display session");
            coordinator.release();
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

                int hasSurface = data.readInt();
                if (hasSurface == HAS_SURFACE_CLEAR) {
                    coordinator.release();
                    System.err.println("DirectMirrorServer: app cleared surface");
                    reply.writeNoException();
                    reply.writeInt(REPLY_FAILURE);
                    return true;
                }
                if (hasSurface != HAS_SURFACE_PRESENT) {
                    coordinator.release();
                    System.err.println("[DirectMirror] Invalid hasSurface flag: " + hasSurface);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeInt(REPLY_FAILURE);
                    }
                    return true;
                }

                int w = data.readInt();
                int h = data.readInt();
                Surface surface = Surface.CREATOR.createFromParcel(data);
                AndroidSurfaceHandle handle = surface != null ? new AndroidSurfaceHandle(surface) : null;
                if (!coordinator.applySurface(handle, w, h)) {
                    reply.writeNoException();
                    reply.writeInt(REPLY_FAILURE);
                    return true;
                }

                surfaceDelivered.countDown();
                System.err.println("DirectMirrorServer: surface parsed & configured");
                reply.writeNoException();
                reply.writeInt(REPLY_SUCCESS);
                return true;
            } catch (Throwable t) {
                System.err.println("DirectMirrorServer: setSurface failed: " + t);
                t.printStackTrace();
                reply.writeNoException();
                reply.writeInt(REPLY_FAILURE);
                return true;
            }
        }
    }
}
