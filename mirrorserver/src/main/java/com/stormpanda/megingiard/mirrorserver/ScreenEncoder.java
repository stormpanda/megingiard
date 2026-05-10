package com.stormpanda.megingiard.mirrorserver;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.IBinder;
import android.view.Surface;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Continuously encodes the primary display into Annex-B H.264 NAL units and
 * writes them to {@code out}.
 *
 * <p>Output framing: each NAL unit is preceded by its 4-byte big-endian
 * length prefix (length-prefixed framing — easier and faster to demux on
 * the app side than scanning for {@code 00 00 00 01} start codes).</p>
 */
final class ScreenEncoder {

    private static final String MIME = "video/avc"; // H.264
    private static final int IFRAME_INTERVAL_SEC = 10;
    private static final int CODEC_TIMEOUT_US = 100_000; // 100 ms

    private final int width;
    private final int height;
    private final int bitrate;
    private final int maxFps;

    ScreenEncoder(int width, int height, int bitrate, int maxFps) {
        this.width = width;
        this.height = height;
        this.bitrate = bitrate;
        this.maxFps = maxFps;
    }

    /**
     * Blocks until the output stream is closed or an unrecoverable error
     * occurs. Cleans up MediaCodec, virtual display, and Surface before
     * returning.
     */
    void run(OutputStream out) throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL_SEC);
        // Realtime priority — encoder runs as fast as possible.
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000); // 100 ms

        MediaCodec codec = MediaCodec.createEncoderByType(MIME);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = codec.createInputSurface();

        IBinder displayToken = SurfaceControlReflect.createDisplay("megingiard-mirror", false);
        Rect rect = new Rect(0, 0, width, height);
        SurfaceControlReflect.openTransaction();
        try {
            SurfaceControlReflect.setDisplaySurface(displayToken, inputSurface);
            SurfaceControlReflect.setDisplayProjection(displayToken, 0, rect, rect);
            SurfaceControlReflect.setDisplayLayerStack(displayToken, 0);
            SurfaceControlReflect.setDisplaySize(displayToken, width, height);
        } finally {
            SurfaceControlReflect.closeTransaction();
        }

        codec.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] lenBuf = new byte[4];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int idx = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    continue;
                }
                if (idx < 0) {
                    continue;
                }
                ByteBuffer buf = codec.getOutputBuffer(idx);
                if (buf != null && info.size > 0) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    int len = info.size;
                    lenBuf[0] = (byte) ((len >>> 24) & 0xff);
                    lenBuf[1] = (byte) ((len >>> 16) & 0xff);
                    lenBuf[2] = (byte) ((len >>> 8) & 0xff);
                    lenBuf[3] = (byte) (len & 0xff);
                    out.write(lenBuf);
                    byte[] payload = new byte[len];
                    buf.get(payload);
                    out.write(payload);
                    out.flush();
                }
                codec.releaseOutputBuffer(idx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        } finally {
            try { codec.stop(); } catch (Throwable ignored) {}
            try { codec.release(); } catch (Throwable ignored) {}
            try { inputSurface.release(); } catch (Throwable ignored) {}
            try { SurfaceControlReflect.destroyDisplay(displayToken); } catch (Throwable ignored) {}
        }
    }
}
