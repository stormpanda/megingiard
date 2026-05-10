package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.net.LocalServerSocket;
import android.os.IBinder;

public final class DirectMirrorServer {
    private static final int PRIMARY_LAYER_STACK = 0;
    private static final int SECONDARY_PHYSICAL_DISPLAY_INDEX = 1;

    private DirectMirrorServer() {}

    public static void main(String[] args) {
        MirrorServer.bypassHiddenApiEnforcement();

        if (args.length < 5) {
            System.err.println("usage: DirectMirrorServer <socket> <srcW> <srcH> <targetW> <targetH>");
            System.exit(2);
            return;
        }

        String socketName = args[0];
        int sourceWidth = Integer.parseInt(args[1]);
        int sourceHeight = Integer.parseInt(args[2]);
        int targetWidth = Integer.parseInt(args[3]);
        int targetHeight = Integer.parseInt(args[4]);
        IBinder targetDisplayToken = null;
        LocalServerSocket readinessSocket = null;
        try {
            targetDisplayToken = SurfaceControlReflect.physicalDisplayTokenForIndex(
                    SECONDARY_PHYSICAL_DISPLAY_INDEX);
            if (targetDisplayToken == null) {
                System.err.println("secondary physical display unavailable");
                System.exit(1);
                return;
            }
                IBinder restoreToken = targetDisplayToken;
                Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> restoreDisplay(restoreToken, targetWidth, targetHeight),
                    "DirectMirrorRestoreDisplay"));

            Rect sourceRect = new Rect(0, 0, sourceWidth, sourceHeight);
            Rect targetRect = new Rect(0, 0, targetWidth, targetHeight);
            SurfaceControlReflect.configurePhysicalDisplay(
                    targetDisplayToken, PRIMARY_LAYER_STACK, sourceRect, targetRect);

            readinessSocket = new LocalServerSocket(socketName);
            synchronized (DirectMirrorServer.class) {
                DirectMirrorServer.class.wait();
            }
        } catch (Throwable t) {
            System.err.println("direct mirror ended: " + t);
            System.exit(1);
        } finally {
            if (targetDisplayToken != null) {
                restoreDisplay(targetDisplayToken, targetWidth, targetHeight);
            }
            if (readinessSocket != null) {
                try { readinessSocket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void restoreDisplay(IBinder targetDisplayToken, int targetWidth, int targetHeight) {
        try {
            Rect rect = new Rect(0, 0, targetWidth, targetHeight);
            SurfaceControlReflect.configurePhysicalDisplay(
                    targetDisplayToken, SECONDARY_PHYSICAL_DISPLAY_INDEX, rect, rect);
        } catch (Throwable ignored) {}
    }
}