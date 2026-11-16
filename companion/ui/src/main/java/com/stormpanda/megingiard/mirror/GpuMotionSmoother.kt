package com.stormpanda.megingiard.mirror

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.LayoutVisualAnchor
import com.stormpanda.megingiard.macropad.MAX_LAYOUT_STREAM_DELAY_FRAMES
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "GpuMotionSmoother"

private const val VERTEX_SHADER_CODE = """
    attribute vec4 aPosition;
    attribute vec4 aTextureCoord;
    varying vec2 vOesTexCoord;
    varying vec2 vFboTexCoord;
    uniform mat4 uSTMatrix;
    void main() {
        gl_Position = aPosition;
        vOesTexCoord = (uSTMatrix * aTextureCoord).xy;
        vFboTexCoord = aTextureCoord.xy;
    }
"""

private const val BLEND_FRAGMENT_SHADER_CODE = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vOesTexCoord;
    varying vec2 vFboTexCoord;
    uniform samplerExternalOES uOesTexture;
    uniform sampler2D uPreviousTexture;
    uniform float uAlpha;
    uniform bool uFirstFrame;
    void main() {
        vec4 current = texture2D(uOesTexture, vOesTexCoord);
        if (uFirstFrame) {
            gl_FragColor = current;
        } else {
            vec4 previous = texture2D(uPreviousTexture, vFboTexCoord);
            gl_FragColor = mix(previous, current, uAlpha);
        }
    }
"""

private const val PASSTHROUGH_FRAGMENT_SHADER_CODE = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vOesTexCoord;
    uniform samplerExternalOES uOesTexture;
    void main() {
        gl_FragColor = texture2D(uOesTexture, vOesTexCoord);
    }
"""

private const val QUAD_VERTEX_SHADER_CODE = """
    attribute vec4 aPosition;
    attribute vec2 aTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = aPosition;
        vTextureCoord = aTextureCoord;
    }
"""

private const val DRAW_FRAGMENT_SHADER_CODE = """
    precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTextureCoord);
    }
"""

private val FULL_QUAD_VERTICES =
    floatArrayOf(
        -1.0f,
        -1.0f,
        1.0f,
        -1.0f,
        -1.0f,
        1.0f,
        1.0f,
        1.0f,
    )

private val FULL_QUAD_TEX_COORDS =
    floatArrayOf(
        0.0f,
        0.0f,
        1.0f,
        0.0f,
        0.0f,
        1.0f,
        1.0f,
        1.0f,
    )

/**
 * 100% GPU-accelerated temporal frame blender and FBO stream delay queue for screen mirroring.
 *
 * Runs an OpenGL ES 2.0 FBO circular buffer pipeline on a dedicated GL thread.
 * Supports hardware stream delay (up to 10 frames in GPU VRAM) and live anchor evaluation
 * directly on the GL thread — eliminating TextureView.getBitmap() CPU readbacks and Main UI stalls.
 */
class GpuMotionSmoother(
    private val outputSurface: Surface,
    private val width: Int,
    private val height: Int,
    private var strength: Int,
    private var streamDelayFrames: Int = 0,
    private val initGl: Boolean = true,
) {
    private val glThread = HandlerThread("GpuMotionSmootherGL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglDisplay: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext? = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface? = EGL14.EGL_NO_SURFACE
    private var blendProgram = 0
    private var drawProgram = 0
    private var passthroughProgram = 0

    private var blendPosLoc = -1
    private var blendTexLoc = -1
    private var blendStMatLoc = -1
    private var blendAlphaLoc = -1
    private var blendFirstLoc = -1
    private var blendOesLoc = -1
    private var blendPrevLoc = -1

    private var passPosLoc = -1
    private var passTexLoc = -1
    private var passStMatLoc = -1
    private var passOesLoc = -1

    private var drawPosLoc = -1
    private var drawTexLoc = -1
    private var drawSamplerLoc = -1

    private var oesTextureId = 0
    private var fboCount = 0
    private var fboTextureIds = IntArray(0)
    private var fboFramebuffers = IntArray(0)

    private var inputSurfaceTexture: SurfaceTexture? = null
    var inputSurface: Surface? = null
        private set

    private val stMatrix = FloatArray(16)
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private var writeIndex = 0
    private var frameCount = 0L
    private var isFirstFrame = true

    @Volatile
    private var requestedStrength = strength

    @Volatile
    private var requestedStreamDelay = streamDelayFrames.coerceIn(0, MAX_LAYOUT_STREAM_DELAY_FRAMES)

    @Volatile
    private var isFrozen = false

    private var activeTrackingLayoutId = ""
    private var activeAnchor: LayoutVisualAnchor? = null
    private var onPresenceEvaluated: ((layoutId: String, matchRatio: Float) -> Unit)? = null
    private var anchorByteBuffer: ByteBuffer? = null

    @Volatile
    private var released = false

    init {
        this.streamDelayFrames = streamDelayFrames.coerceIn(0, MAX_LAYOUT_STREAM_DELAY_FRAMES)
        val vertBb = ByteBuffer.allocateDirect(FULL_QUAD_VERTICES.size * 4).apply { order(ByteOrder.nativeOrder()) }
        vertexBuffer =
            vertBb.asFloatBuffer().apply {
                put(FULL_QUAD_VERTICES)
                position(0)
            }

        val texBb = ByteBuffer.allocateDirect(FULL_QUAD_TEX_COORDS.size * 4).apply { order(ByteOrder.nativeOrder()) }
        texCoordBuffer =
            texBb.asFloatBuffer().apply {
                put(FULL_QUAD_TEX_COORDS)
                position(0)
            }

        if (initGl) {
            val latch = CountDownLatch(1)
            glHandler.post {
                try {
                    initGL()
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(2000, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                AppLog.e(TAG, "Timed out waiting for GL initialization", e)
            }
        }
    }

    fun updateStrength(newStrength: Int) {
        if (released || requestedStrength == newStrength) return
        requestedStrength = newStrength
        glHandler.post {
            if (this.strength != newStrength) {
                this.strength = newStrength
                isFirstFrame = true
                syncFboLifecycle()
            }
        }
    }

    fun updateStreamDelay(newDelay: Int) {
        val clampedDelay = newDelay.coerceIn(0, MAX_LAYOUT_STREAM_DELAY_FRAMES)
        if (released || requestedStreamDelay == clampedDelay) return
        requestedStreamDelay = clampedDelay
        glHandler.post {
            if (this.streamDelayFrames != clampedDelay) {
                AppLog.i(TAG, "Updating streamDelayFrames from $streamDelayFrames to $clampedDelay")
                this.streamDelayFrames = clampedDelay
                syncFboLifecycle()
            }
        }
    }

    fun setAnchorTracking(
        layoutId: String,
        anchor: LayoutVisualAnchor?,
        listener: ((layoutId: String, matchRatio: Float) -> Unit)? = null,
    ) {
        if (released) return
        glHandler.post {
            this.activeTrackingLayoutId = layoutId
            this.activeAnchor = anchor
            this.onPresenceEvaluated = listener
            syncFboLifecycle()
        }
    }

    private fun hasActiveCalibratedAnchor(): Boolean {
        val anchor = activeAnchor ?: return false
        if (!anchor.enabled || !anchor.isCalibrated || onPresenceEvaluated == null) return false
        return anchor.signature?.points?.isNotEmpty() == true
    }

    private fun requiresFboPipeline(): Boolean = strength > 0 || streamDelayFrames > 0 || hasActiveCalibratedAnchor()

    private fun targetFboCount(): Int = (streamDelayFrames + 1).coerceIn(2, MAX_LAYOUT_STREAM_DELAY_FRAMES + 2)

    private fun syncFboLifecycle() {
        if (requiresFboPipeline()) {
            ensureFbosAllocated()
        } else {
            releaseFbosIfAllocated()
        }
    }

    private fun ensureFbosAllocated() {
        if (!initGl || oesTextureId == 0) return
        val target = targetFboCount()
        if (fboCount != target || fboFramebuffers.isEmpty() || fboFramebuffers[0] == 0) {
            setupTexturesAndFbos(target)
        }
    }

    private fun releaseFbosIfAllocated() {
        if (!initGl || oesTextureId == 0) return
        if (fboTextureIds.isNotEmpty() && fboTextureIds[0] != 0) {
            GLES20.glDeleteTextures(fboTextureIds.size, fboTextureIds, 0)
            GLES20.glDeleteFramebuffers(fboFramebuffers.size, fboFramebuffers, 0)
            fboCount = 0
            fboTextureIds = IntArray(0)
            fboFramebuffers = IntArray(0)
            writeIndex = 0
            frameCount = 0L
            isFirstFrame = true
            AppLog.d(TAG, "Reclaimed FBO ring buffer VRAM on transition to zero-FBO passthrough")
        }
    }

    @VisibleForTesting
    internal fun isFrozenForTesting(): Boolean = isFrozen

    fun setFrozen(frozen: Boolean) {
        if (released) return
        if (this.isFrozen != frozen) {
            AppLog.d(TAG, "GpuMotionSmoother frozen state changed to $frozen")
            this.isFrozen = frozen
        }
    }

    private fun initGL() {
        if (released) return
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("eglGetDisplay failed")

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) throw RuntimeException("eglInitialize failed")

            val attribList =
                intArrayOf(
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_ALPHA_SIZE,
                    8,
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: throw RuntimeException("EGL config not found")

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, outputSurface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                val error = EGL14.eglGetError()
                throw RuntimeException("eglCreateWindowSurface failed with error $error")
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                val error = EGL14.eglGetError()
                throw RuntimeException("eglMakeCurrent failed with error $error")
            }

            val oesTex = IntArray(1)
            GLES20.glGenTextures(1, oesTex, 0)
            oesTextureId = oesTex[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            setupShaders()
            if (requiresFboPipeline()) {
                ensureFbosAllocated()
            }

            val st =
                SurfaceTexture(oesTextureId).apply {
                    setDefaultBufferSize(width, height)
                    setOnFrameAvailableListener {
                        glHandler.post { renderFrame() }
                    }
                }
            inputSurfaceTexture = st
            inputSurface = Surface(st)
            AppLog.i(TAG, "GpuMotionSmoother initialized cleanly for ${width}x$height")
        } catch (e: Exception) {
            AppLog.e(TAG, "Error initializing GpuMotionSmoother GL context", e)
        }
    }

    private fun setupShaders() {
        val vertShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val blendFragShader = loadShader(GLES20.GL_FRAGMENT_SHADER, BLEND_FRAGMENT_SHADER_CODE)
        blendProgram =
            GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertShader)
                GLES20.glAttachShader(it, blendFragShader)
                GLES20.glLinkProgram(it)
            }

        val passthroughFragShader = loadShader(GLES20.GL_FRAGMENT_SHADER, PASSTHROUGH_FRAGMENT_SHADER_CODE)
        passthroughProgram =
            GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertShader)
                GLES20.glAttachShader(it, passthroughFragShader)
                GLES20.glLinkProgram(it)
            }

        val quadVertShader = loadShader(GLES20.GL_VERTEX_SHADER, QUAD_VERTEX_SHADER_CODE)
        val drawFragShader = loadShader(GLES20.GL_FRAGMENT_SHADER, DRAW_FRAGMENT_SHADER_CODE)
        drawProgram =
            GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, quadVertShader)
                GLES20.glAttachShader(it, drawFragShader)
                GLES20.glLinkProgram(it)
            }

        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(blendFragShader)
        GLES20.glDeleteShader(passthroughFragShader)
        GLES20.glDeleteShader(quadVertShader)
        GLES20.glDeleteShader(drawFragShader)

        blendPosLoc = GLES20.glGetAttribLocation(blendProgram, "aPosition")
        blendTexLoc = GLES20.glGetAttribLocation(blendProgram, "aTextureCoord")
        blendStMatLoc = GLES20.glGetUniformLocation(blendProgram, "uSTMatrix")
        blendAlphaLoc = GLES20.glGetUniformLocation(blendProgram, "uAlpha")
        blendFirstLoc = GLES20.glGetUniformLocation(blendProgram, "uFirstFrame")
        blendOesLoc = GLES20.glGetUniformLocation(blendProgram, "uOesTexture")
        blendPrevLoc = GLES20.glGetUniformLocation(blendProgram, "uPreviousTexture")

        passPosLoc = GLES20.glGetAttribLocation(passthroughProgram, "aPosition")
        passTexLoc = GLES20.glGetAttribLocation(passthroughProgram, "aTextureCoord")
        passStMatLoc = GLES20.glGetUniformLocation(passthroughProgram, "uSTMatrix")
        passOesLoc = GLES20.glGetUniformLocation(passthroughProgram, "uOesTexture")

        drawPosLoc = GLES20.glGetAttribLocation(drawProgram, "aPosition")
        drawTexLoc = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord")
        drawSamplerLoc = GLES20.glGetUniformLocation(drawProgram, "uTexture")
    }

    private fun setupTexturesAndFbos(targetCount: Int = fboCount) {
        if (fboTextureIds.isNotEmpty() && fboTextureIds[0] != 0) {
            GLES20.glDeleteTextures(fboTextureIds.size, fboTextureIds, 0)
            GLES20.glDeleteFramebuffers(fboFramebuffers.size, fboFramebuffers, 0)
        }

        fboCount = targetCount
        fboTextureIds = IntArray(targetCount)
        fboFramebuffers = IntArray(targetCount)

        GLES20.glGenTextures(targetCount, fboTextureIds, 0)
        GLES20.glGenFramebuffers(targetCount, fboFramebuffers, 0)

        for (i in 0 until targetCount) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureIds[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null,
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboFramebuffers[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                fboTextureIds[i],
                0,
            )
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        writeIndex = 0
        frameCount = 0L
        isFirstFrame = true
    }

    private fun renderFrame() {
        val st = inputSurfaceTexture ?: return
        if (released) return
        try {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)

            GLES20.glViewport(0, 0, width, height)

            if (!requiresFboPipeline()) {
                // Fast path: single-pass direct OES-to-window render without intermediate FBO copies or glClear
                if (!isFrozen) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    drawPassthroughQuad()
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                }
                isFirstFrame = true
                return
            }

            ensureFbosAllocated()

            // Pass 1: Render incoming OES frame into current FBO at writeIndex
            val targetFbo = fboFramebuffers[writeIndex]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFbo)

            if (strength > 0 && !isFirstFrame) {
                GLES20.glUseProgram(blendProgram)

                GLES20.glEnableVertexAttribArray(blendPosLoc)
                GLES20.glVertexAttribPointer(blendPosLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                GLES20.glEnableVertexAttribArray(blendTexLoc)
                GLES20.glVertexAttribPointer(blendTexLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

                GLES20.glUniformMatrix4fv(blendStMatLoc, 1, false, stMatrix, 0)
                val alphaPercent = (100 - strength).coerceIn(1, 99) / 100.0f
                GLES20.glUniform1f(blendAlphaLoc, alphaPercent)
                GLES20.glUniform1i(blendFirstLoc, 0)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES20.glUniform1i(blendOesLoc, 0)

                val prevIndex = (writeIndex - 1 + fboCount) % fboCount
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureIds[prevIndex])
                GLES20.glUniform1i(blendPrevLoc, 1)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } else {
                drawPassthroughQuad()
            }

            // Pass 2: Evaluate Visual Anchor directly on live FBO (writeIndex) before delay
            evaluateAnchorOnGlThread(writeIndex)

            // Pass 3: Draw delayed FBO texture onto Output Window Surface
            if (!isFrozen) {
                val delayedIndex =
                    if (streamDelayFrames > 0 && frameCount >= streamDelayFrames) {
                        (writeIndex - streamDelayFrames + fboCount) % fboCount
                    } else {
                        writeIndex
                    }

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glUseProgram(drawProgram)

                GLES20.glEnableVertexAttribArray(drawPosLoc)
                GLES20.glVertexAttribPointer(drawPosLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                GLES20.glEnableVertexAttribArray(drawTexLoc)
                GLES20.glVertexAttribPointer(drawTexLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureIds[delayedIndex])
                GLES20.glUniform1i(drawSamplerLoc, 0)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }

            // Advance ring buffer index
            writeIndex = (writeIndex + 1) % fboCount
            frameCount++
            isFirstFrame = false
        } catch (e: Exception) {
            AppLog.e(TAG, "Error rendering frame in GpuMotionSmoother", e)
        }
    }

    private fun drawPassthroughQuad() {
        GLES20.glUseProgram(passthroughProgram)

        GLES20.glEnableVertexAttribArray(passPosLoc)
        GLES20.glVertexAttribPointer(passPosLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(passTexLoc)
        GLES20.glVertexAttribPointer(passTexLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glUniformMatrix4fv(passStMatLoc, 1, false, stMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(passOesLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun evaluateAnchorOnGlThread(targetIndex: Int) {
        val anchor = activeAnchor ?: return
        val listener = onPresenceEvaluated ?: return
        if (!anchor.enabled || !anchor.isCalibrated) return
        val signature = anchor.signature ?: return
        val points = signature.points
        if (points.isEmpty()) return

        val cropX = (anchor.srcX * width).roundToInt().coerceIn(0, width - 1)
        val cropY = (anchor.srcY * height).roundToInt().coerceIn(0, height - 1)
        val cropW = (anchor.srcWidth * width).roundToInt().coerceIn(1, width - cropX)
        val cropH = (anchor.srcHeight * height).roundToInt().coerceIn(1, height - cropY)

        var minPx = cropW - 1
        var maxPx = 0
        var minPy = cropH - 1
        var maxPy = 0
        for (i in points.indices) {
            val pt = points[i]
            val px = (pt.u * cropW).toInt().coerceIn(0, cropW - 1)
            val py = ((1f - pt.v) * cropH).toInt().coerceIn(0, cropH - 1)
            if (px < minPx) minPx = px
            if (px > maxPx) maxPx = px
            if (py < minPy) minPy = py
            if (py > maxPy) maxPy = py
        }
        val readW = (maxPx - minPx + 1).coerceAtLeast(1)
        val readH = (maxPy - minPy + 1).coerceAtLeast(1)

        val neededCapacity = readW * readH * 4
        var buf = anchorByteBuffer
        if (buf == null || buf.capacity() < neededCapacity) {
            buf = ByteBuffer.allocateDirect(neededCapacity).apply { order(ByteOrder.nativeOrder()) }
            anchorByteBuffer = buf
        }
        buf.rewind()

        val glY = height - (cropY + cropH)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboFramebuffers[targetIndex])
        GLES20.glReadPixels(cropX + minPx, glY + minPy, readW, readH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

        var matchCount = 0
        for (i in points.indices) {
            val pt = points[i]
            val localX = (pt.u * cropW).toInt().coerceIn(0, cropW - 1) - minPx
            val localY = ((1f - pt.v) * cropH).toInt().coerceIn(0, cropH - 1) - minPy
            val offset = (localY * readW + localX) * 4
            if (offset + 2 < neededCapacity) {
                val r = buf.get(offset).toInt() and 0xFF
                val g = buf.get(offset + 1).toInt() and 0xFF
                val b = buf.get(offset + 2).toInt() and 0xFF
                val diff = abs(r - pt.r) + abs(g - pt.g) + abs(b - pt.b)
                if (diff <= AnchorPresenceEvaluator.ANCHOR_DIFF_TOLERANCE) {
                    matchCount++
                }
            }
        }

        val matchRatio = matchCount.toFloat() / points.size.toFloat()
        listener.invoke(activeTrackingLayoutId, matchRatio)
    }

    private fun loadShader(
        type: Int,
        shaderCode: String,
    ): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }

    fun release() {
        if (released) return
        released = true
        glHandler.post {
            try {
                inputSurface?.release()
                inputSurfaceTexture?.release()
                if (blendProgram != 0) GLES20.glDeleteProgram(blendProgram)
                if (drawProgram != 0) GLES20.glDeleteProgram(drawProgram)
                if (passthroughProgram != 0) GLES20.glDeleteProgram(passthroughProgram)
                if (oesTextureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                    oesTextureId = 0
                }
                if (fboTextureIds.isNotEmpty() && fboTextureIds[0] != 0) {
                    GLES20.glDeleteTextures(fboTextureIds.size, fboTextureIds, 0)
                    GLES20.glDeleteFramebuffers(fboFramebuffers.size, fboFramebuffers, 0)
                }

                if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
                AppLog.i(TAG, "GpuMotionSmoother resources released cleanly")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error releasing GpuMotionSmoother GL resources", e)
            } finally {
                glThread.quitSafely()
            }
        }
    }
}
