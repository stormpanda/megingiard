package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.net.LocalServerSocket;
import android.os.IBinder;
import android.view.Surface;

public final class DirectMirrorServer {
    private static final long SURFACE_BIND_TIMEOUT_MS = 5_000L;

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
        Surface surface = null;
        IBinder displayToken = null;
        LocalServerSocket readinessSocket = null;
        try {
            surface = DirectSurfaceClient.acquireSurface(SURFACE_BIND_TIMEOUT_MS);
            if (surface == null || !surface.isValid()) {
                System.err.println("direct surface unavailable");
                System.exit(1);
                return;
            }

            displayToken = SurfaceControlReflect.createDisplay("megingiard-direct-mirror", false);
            Rect rect = new Rect(0, 0, width, height);
            SurfaceControlReflect.configureDisplay(displayToken, surface, 0, rect, rect);

            readinessSocket = new LocalServerSocket(socketName);
            synchronized (DirectMirrorServer.class) {
                DirectMirrorServer.class.wait();
            }
        } catch (Throwable t) {
            System.err.println("direct mirror ended: " + t);
            System.exit(1);
        } finally {
            if (displayToken != null) {
                try { SurfaceControlReflect.destroyDisplay(displayToken); } catch (Throwable ignored) {}
            }
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) {}
            }
            if (readinessSocket != null) {
                try { readinessSocket.close(); } catch (Throwable ignored) {}
            }
        }
    }
}