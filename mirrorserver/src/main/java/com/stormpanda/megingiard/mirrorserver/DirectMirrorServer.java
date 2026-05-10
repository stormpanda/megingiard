package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.net.LocalServerSocket;
import android.os.IBinder;

public final class DirectMirrorServer {
    private static final int DISPLAY_ROTATION_90 = 1;
    private static final int PRIMARY_LAYER_STACK = 0;
    private static final int SECONDARY_PHYSICAL_DISPLAY_INDEX = 1;

    private DirectMirrorServer() {}

    public static void main(String[] args) {
        MirrorServer.bypassHiddenApiEnforcement();

        if (args.length < 6) {
            System.err.println("usage: DirectMirrorServer <socket> <srcW> <srcH> <targetW> <targetH> <targetLayerStack>");
            System.exit(2);
            return;
        }

        String socketName = args[0];
        int sourceWidth = Integer.parseInt(args[1]);
        int sourceHeight = Integer.parseInt(args[2]);
        int targetWidth = Integer.parseInt(args[3]);
        int targetHeight = Integer.parseInt(args[4]);
        int targetLayerStack = Integer.parseInt(args[5]);
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
                    () -> restoreDisplay(restoreToken, targetWidth, targetHeight, targetLayerStack),
                    "DirectMirrorRestoreDisplay"));

            Rect sourceRect = new Rect(0, 0, sourceWidth, sourceHeight);
                Rect targetRect = fitCenter(sourceWidth, sourceHeight, targetWidth, targetHeight);
            SurfaceControlReflect.configurePhysicalDisplay(
                    targetDisplayToken, PRIMARY_LAYER_STACK, DISPLAY_ROTATION_90, sourceRect, targetRect);

            readinessSocket = new LocalServerSocket(socketName);
            synchronized (DirectMirrorServer.class) {
                DirectMirrorServer.class.wait();
            }
        } catch (Throwable t) {
            System.err.println("direct mirror ended: " + t);
            System.exit(1);
        } finally {
            if (targetDisplayToken != null) {
                restoreDisplay(targetDisplayToken, targetWidth, targetHeight, targetLayerStack);
            }
            if (readinessSocket != null) {
                try { readinessSocket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static Rect fitCenter(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        double sourceAspect = (double) sourceWidth / (double) sourceHeight;
        double targetAspect = (double) targetWidth / (double) targetHeight;
        if (targetAspect > sourceAspect) {
            int fittedWidth = (int) Math.round(targetHeight * sourceAspect);
            int left = (targetWidth - fittedWidth) / 2;
            return new Rect(left, 0, left + fittedWidth, targetHeight);
        }
        int fittedHeight = (int) Math.round(targetWidth / sourceAspect);
        int top = (targetHeight - fittedHeight) / 2;
        return new Rect(0, top, targetWidth, top + fittedHeight);
    }

    private static void restoreDisplay(IBinder targetDisplayToken, int targetWidth, int targetHeight,
                                       int targetLayerStack) {
        try {
            Rect rect = new Rect(0, 0, targetWidth, targetHeight);
            SurfaceControlReflect.configurePhysicalDisplay(
                    targetDisplayToken, targetLayerStack, DISPLAY_ROTATION_90, rect, rect);
        } catch (Throwable ignored) {}
    }
}