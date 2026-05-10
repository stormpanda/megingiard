package com.stormpanda.megingiard.mirrorserver;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.OutputStream;

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
 *   <li>Bind a {@link LocalServerSocket} on the abstract namespace name
 *       provided by the daemon.</li>
 *   <li>Print exactly {@code "READY\n"} on stdout so the daemon can confirm
 *       the bind happened before it sends {@code MIRROR_READY} to the app.</li>
 *   <li>Block on {@code accept()} for the app to connect.</li>
 *   <li>Run the {@link ScreenEncoder} loop, writing length-prefixed H.264
 *       NAL units into the socket's output stream.</li>
 *   <li>On any I/O error or stream close, exit with status 0.</li>
 * </ol>
 * </p>
 */
public final class MirrorServer {

    private MirrorServer() {}

    public static void main(String[] args) {
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

        // Signal readiness on stdout BEFORE blocking on accept().
        System.out.println("READY");
        System.out.flush();

        try (LocalSocket client = server.accept();
             OutputStream out = client.getOutputStream()) {
            new ScreenEncoder(width, height, bitrate, maxFps).run(out);
        } catch (Throwable t) {
            System.err.println("encoder ended: " + t);
        } finally {
            try { server.close(); } catch (Throwable ignored) {}
        }
    }
}
