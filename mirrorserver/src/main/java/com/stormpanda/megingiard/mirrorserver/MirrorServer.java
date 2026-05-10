package com.stormpanda.megingiard.mirrorserver;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * Entrypoint for the mirror server, launched by {@code megingiard_privd} via:
 * <pre>
 *   CLASSPATH=/data/local/tmp/megingiard_mirror.dex \
 *     /system/bin/app_process /data/local/tmp \
 *     com.stormpanda.megingiard.mirrorserver.MirrorServer \
 *     &lt;socketName&gt; &lt;width&gt; &lt;height&gt; &lt;bitrate&gt; &lt;maxFps&gt;
 * </pre>
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Bypass hidden-API enforcement so that {@code SurfaceControl.Transaction}
 *       reflection works on API 31+.</li>
 *   <li>Bind a {@link LocalServerSocket} on the abstract namespace name
 *       provided by the daemon.</li>
 *   <li>Block on {@code accept()} for the app to connect.  The daemon detects
 *       readiness by polling {@code /proc/net/unix} for the abstract socket
 *       name rather than reading from a stdout pipe (which ART silently
 *       redirects to logcat via {@code RuntimeInit.redirectLogStreams()}).</li>
 *   <li>Run the {@link ScreenEncoder} loop, writing length-prefixed H.264
 *       NAL units into the socket's output stream.</li>
 *   <li>On any I/O error or stream close, exit with status 0.</li>
 * </ol>
 * </p>
 */
public final class MirrorServer {

    private MirrorServer() {}

    public static void main(String[] args) {
        // Unlock hidden-API enforcement before any SurfaceControl reflection.
        // Required on API 31+ where SurfaceControl.Transaction methods are @hide.
        bypassHiddenApiEnforcement();

        if (args.length < 5) {
            System.err.println("usage: MirrorServer <socket> <w> <h> <bitrate> <maxfps>");
            System.exit(2);
            return;
        }
        String socketName = args[0];
        int width = Integer.parseInt(args[1]);
        int height = Integer.parseInt(args[2]);
        int bitrate = Integer.parseInt(args[3]);
        int maxFps = Integer.parseInt(args[4]);

        LocalServerSocket server;
        try {
            server = new LocalServerSocket(socketName);
        } catch (Throwable t) {
            System.err.println("bind failed: " + t);
            System.exit(1);
            return;
        }

        // The daemon detects readiness via /proc/net/unix (socket binding above).
        // System.out.println("READY") would go to logcat rather than the pipe
        // because RuntimeInit.redirectLogStreams() runs before main().

        try (LocalSocket client = server.accept();
             OutputStream out = client.getOutputStream()) {
            new ScreenEncoder(width, height, bitrate, maxFps).run(out);
        } catch (Throwable t) {
            System.err.println("encoder ended: " + t);
        } finally {
            try { server.close(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Exempts all hidden APIs from enforcement for this process.
     *
     * <p>On Android 9+ (API 28+) ART enforces restrictions on reflection access
     * to {@code @hide} methods even when called by privileged processes.
     * Calling {@code VMRuntime.setHiddenApiExemptions([""])} before any hidden-API
     * access disables that enforcement for the lifetime of the process.</p>
     *
     * <p>This is the same technique used by Shizuku, scrcpy, and similar
     * ADB-level tools.</p>
     */
    static void bypassHiddenApiEnforcement() {
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object vmRuntime = getRuntime.invoke(null);
            Method setHiddenApiExemptions = vmRuntimeClass.getDeclaredMethod(
                    "setHiddenApiExemptions", String[].class);
            setHiddenApiExemptions.setAccessible(true);
            // An empty-string prefix matches every hidden API signature.
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{""}});
        } catch (Exception e) {
            System.err.println("Warning: hidden-API bypass failed: " + e);
        }
    }
}
