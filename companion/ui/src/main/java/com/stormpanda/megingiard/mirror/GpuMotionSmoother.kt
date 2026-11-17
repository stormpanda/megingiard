package com.stormpanda.megingiard.mirror

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "GpuMotionSmoother"
private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
private const val ANCHOR_FBO_WIDTH = 256
private const val BYTES_PER_RGBA_PIXEL = 4
private const val FLOATS_PER_VEC2 = 2
private const val BYTES_PER_FLOAT = 4

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

private const val ANCHOR_POINT_OES_VERTEX_SHADER_CODE = """
    attribute vec2 aPosition;
    attribute vec2 aTextureCoord;
    varying vec2 vOesTexCoord;
    uniform mat4 uSTMatrix;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        gl_PointSize = 1.0;
        vOesTexCoord = (uSTMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
    }
"""

private const val ANCHOR_POINT_2D_VERTEX_SHADER_CODE = """
    attribute vec2 aPosition;
    attribute vec2 aTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        gl_PointSize = 1.0;
        vTextureCoord = aTextureCoord;
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

    private var anchorPointOesProgram = 0
    private var anchorOesPosLoc = -1
    private var anchorOesTexLoc = -1
    private var anchorOesStMatLoc = -1
    private var anchorOesSamplerLoc = -1

    private var anchorPoint2dProgram = 0
    private var anchor2dPosLoc = -1
    private var anchor2dTexLoc = -1
    private var anchor2dSamplerLoc = -1

    private var isGles30 = false
    private var anchorSampleTexId = 0
    private var anchorSampleFboId = 0
    private val anchorPboIds = IntArray(2)
    private var anchorPboIndex = 0
    private val anchorPboPrimed = BooleanArray(2)
    private val anchorPboLayoutIds = arrayOf("", "")
    private val anchorPboPoints = arrayOf<List<AnchorPoint>>(emptyList(), emptyList())
    private val anchorPointPosBuffer: FloatBuffer =
        ByteBuffer
            .allocateDirect(ANCHOR_FBO_WIDTH * FLOATS_PER_VEC2 * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    private val anchorPointTexBuffer: FloatBuffer =
        ByteBuffer
            .allocateDirect(ANCHOR_FBO_WIDTH * FLOATS_PER_VEC2 * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    private val framePending = AtomicBoolean(false)
    private val renderFrameRunnable =
        Runnable {
            framePending.set(false)
            renderFrame()
        }

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

    private fun requiresFboPipeline(): Boolean = strength > 0 || streamDelayFrames > 0

    private fun targetFboCount(): Int = (streamDelayFrames + 1).coerceIn(2, MAX_LAYOUT_STREAM_DELAY_FRAMES + 2)

    private fun syncFboLifecycle() {
        if (requiresFboPipeline()) {
            ensureFbosAllocated()
        } else {
            releaseFbosIfAllocated()
        }
        if (hasActiveCalibratedAnchor()) {
            ensureAnchorSampleFboAllocated()
        } else {
            releaseAnchorSampleFboIfAllocated()
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

    private fun ensureAnchorSampleFboAllocated() {
        if (!initGl || oesTextureId == 0 || anchorSampleFboId != 0) return
        val tex = IntArray(1)
        val fbo = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glGenFramebuffers(1, fbo, 0)
        anchorSampleTexId = tex[0]
        anchorSampleFboId = fbo[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, anchorSampleTexId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            ANCHOR_FBO_WIDTH,
            1,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, anchorSampleFboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            anchorSampleTexId,
            0,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        if (isGles30) {
            GLES30.glGenBuffers(2, anchorPboIds, 0)
            val byteSize = ANCHOR_FBO_WIDTH * BYTES_PER_RGBA_PIXEL
            for (i in 0..1) {
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, anchorPboIds[i])
                GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, byteSize, null, GLES30.GL_STREAM_READ)
                anchorPboPrimed[i] = false
            }
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
            anchorPboIndex = 0
        }
    }

    private fun releaseAnchorSampleFboIfAllocated() {
        if (!initGl || anchorSampleFboId == 0) return
        if (isGles30 && anchorPboIds[0] != 0) {
            GLES30.glDeleteBuffers(2, anchorPboIds, 0)
            anchorPboIds[0] = 0
            anchorPboIds[1] = 0
        }
        anchorPboPrimed[0] = false
        anchorPboPrimed[1] = false
        anchorPboPoints[0] = emptyList()
        anchorPboPoints[1] = emptyList()
        GLES20.glDeleteTextures(1, intArrayOf(anchorSampleTexId), 0)
        GLES20.glDeleteFramebuffers(1, intArrayOf(anchorSampleFboId), 0)
        anchorSampleTexId = 0
        anchorSampleFboId = 0
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

            val es3Attribs =
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
                    EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            var chosenConfig: EGLConfig? = null
            if (EGL14.eglChooseConfig(eglDisplay, es3Attribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
                val es3ContextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
                val ctx = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, es3ContextAttribs, 0)
                if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT) {
                    eglContext = ctx
                    chosenConfig = configs[0]
                    isGles30 = true
                }
            }
            if (chosenConfig == null) {
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
                EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                chosenConfig = configs[0] ?: throw RuntimeException("EGL config not found")
                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                isGles30 = false
            }
            val config = chosenConfig

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
            syncFboLifecycle()

            val st =
                SurfaceTexture(oesTextureId).apply {
                    setDefaultBufferSize(width, height)
                    setOnFrameAvailableListener {
                        if (framePending.compareAndSet(false, true)) {
                            glHandler.post(renderFrameRunnable)
                        }
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

        val anchorOesVertShader = loadShader(GLES20.GL_VERTEX_SHADER, ANCHOR_POINT_OES_VERTEX_SHADER_CODE)
        anchorPointOesProgram =
            GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, anchorOesVertShader)
                GLES20.glAttachShader(it, passthroughFragShader)
                GLES20.glLinkProgram(it)
            }

        val anchor2dVertShader = loadShader(GLES20.GL_VERTEX_SHADER, ANCHOR_POINT_2D_VERTEX_SHADER_CODE)
        anchorPoint2dProgram =
            GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, anchor2dVertShader)
                GLES20.glAttachShader(it, drawFragShader)
                GLES20.glLinkProgram(it)
            }

        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(blendFragShader)
        GLES20.glDeleteShader(passthroughFragShader)
        GLES20.glDeleteShader(quadVertShader)
        GLES20.glDeleteShader(drawFragShader)
        GLES20.glDeleteShader(anchorOesVertShader)
        GLES20.glDeleteShader(anchor2dVertShader)

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

        anchorOesPosLoc = GLES20.glGetAttribLocation(anchorPointOesProgram, "aPosition")
        anchorOesTexLoc = GLES20.glGetAttribLocation(anchorPointOesProgram, "aTextureCoord")
        anchorOesStMatLoc = GLES20.glGetUniformLocation(anchorPointOesProgram, "uSTMatrix")
        anchorOesSamplerLoc = GLES20.glGetUniformLocation(anchorPointOesProgram, "uOesTexture")

        anchor2dPosLoc = GLES20.glGetAttribLocation(anchorPoint2dProgram, "aPosition")
        anchor2dTexLoc = GLES20.glGetAttribLocation(anchorPoint2dProgram, "aTextureCoord")
        anchor2dSamplerLoc = GLES20.glGetUniformLocation(anchorPoint2dProgram, "uTexture")
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
                if (hasActiveCalibratedAnchor()) {
                    evaluateAnchorOnGlThread(useOesDirect = true, sourceTexId = oesTextureId)
                    GLES20.glViewport(0, 0, width, height)
                }
                // Fast path: single-pass direct OES-to-window render without intermediate 1080p FBO copies
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

            // Pass 2: Evaluate Visual Anchor via dedicated 256x1 point-sample FBO before presenting delayed frame
            if (hasActiveCalibratedAnchor()) {
                evaluateAnchorOnGlThread(useOesDirect = false, sourceTexId = fboTextureIds[writeIndex])
                GLES20.glViewport(0, 0, width, height)
            }

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

    private fun evaluateAnchorOnGlThread(
        useOesDirect: Boolean,
        sourceTexId: Int,
    ) {
        val anchor = activeAnchor ?: return
        val listener = onPresenceEvaluated ?: return
        if (!anchor.enabled || !anchor.isCalibrated) return
        val signature = anchor.signature ?: return
        val points = signature.points
        if (points.isEmpty()) return

        ensureAnchorSampleFboAllocated()
        if (anchorSampleFboId == 0) return

        val sampleCount = min(points.size, ANCHOR_FBO_WIDTH)
        val cropX = (anchor.srcX * width).roundToInt().coerceIn(0, width - 1)
        val cropY = (anchor.srcY * height).roundToInt().coerceIn(0, height - 1)
        val cropW = (anchor.srcWidth * width).roundToInt().coerceIn(1, width - cropX)
        val cropH = (anchor.srcHeight * height).roundToInt().coerceIn(1, height - cropY)
        val glBaseY = height - (cropY + cropH)
        val invW = 1.0f / width.toFloat()
        val invH = 1.0f / height.toFloat()

        anchorPointPosBuffer.clear()
        anchorPointTexBuffer.clear()
        for (i in 0 until sampleCount) {
            val pt = points[i]
            val px = cropX + (pt.u * cropW).toInt().coerceIn(0, cropW - 1)
            val py = glBaseY + ((1f - pt.v) * cropH).toInt().coerceIn(0, cropH - 1)
            val ndcX = ((i + 0.5f) / ANCHOR_FBO_WIDTH.toFloat()) * 2.0f - 1.0f
            anchorPointPosBuffer.put(ndcX)
            anchorPointPosBuffer.put(0.0f)
            anchorPointTexBuffer.put((px + 0.5f) * invW)
            anchorPointTexBuffer.put((py + 0.5f) * invH)
        }
        anchorPointPosBuffer.position(0)
        anchorPointTexBuffer.position(0)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, anchorSampleFboId)
        GLES20.glViewport(0, 0, ANCHOR_FBO_WIDTH, 1)

        if (useOesDirect) {
            GLES20.glUseProgram(anchorPointOesProgram)
            GLES20.glEnableVertexAttribArray(anchorOesPosLoc)
            GLES20.glVertexAttribPointer(anchorOesPosLoc, 2, GLES20.GL_FLOAT, false, 0, anchorPointPosBuffer)
            GLES20.glEnableVertexAttribArray(anchorOesTexLoc)
            GLES20.glVertexAttribPointer(anchorOesTexLoc, 2, GLES20.GL_FLOAT, false, 0, anchorPointTexBuffer)
            GLES20.glUniformMatrix4fv(anchorOesStMatLoc, 1, false, stMatrix, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, sourceTexId)
            GLES20.glUniform1i(anchorOesSamplerLoc, 0)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, sampleCount)
        } else {
            GLES20.glUseProgram(anchorPoint2dProgram)
            GLES20.glEnableVertexAttribArray(anchor2dPosLoc)
            GLES20.glVertexAttribPointer(anchor2dPosLoc, 2, GLES20.GL_FLOAT, false, 0, anchorPointPosBuffer)
            GLES20.glEnableVertexAttribArray(anchor2dTexLoc)
            GLES20.glVertexAttribPointer(anchor2dTexLoc, 2, GLES20.GL_FLOAT, false, 0, anchorPointTexBuffer)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexId)
            GLES20.glUniform1i(anchor2dSamplerLoc, 0)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, sampleCount)
        }

        val byteCount = sampleCount * BYTES_PER_RGBA_PIXEL

        if (isGles30 && streamDelayFrames == 0 && anchorPboIds[0] != 0) {
            val writeSlot = anchorPboIndex
            val readSlot = 1 - anchorPboIndex
            if (anchorPboPrimed[readSlot]) {
                val prevPoints = anchorPboPoints[readSlot]
                val prevCount = min(prevPoints.size, ANCHOR_FBO_WIDTH)
                val prevLayoutId = anchorPboLayoutIds[readSlot]
                if (prevCount > 0 && prevLayoutId == activeTrackingLayoutId) {
                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, anchorPboIds[readSlot])
                    val mapped =
                        GLES30.glMapBufferRange(
                            GLES30.GL_PIXEL_PACK_BUFFER,
                            0,
                            prevCount * BYTES_PER_RGBA_PIXEL,
                            GLES30.GL_MAP_READ_BIT,
                        ) as? ByteBuffer
                    if (mapped != null) {
                        val ratio = computeSampledMatchRatio(mapped, prevPoints, prevCount)
                        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
                        listener.invoke(prevLayoutId, ratio)
                    }
                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
                }
            }
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, anchorPboIds[writeSlot])
            GLES30.glReadPixels(0, 0, sampleCount, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
            anchorPboPrimed[writeSlot] = true
            anchorPboLayoutIds[writeSlot] = activeTrackingLayoutId
            anchorPboPoints[writeSlot] = points
            anchorPboIndex = readSlot
            return
        }

        var buf = anchorByteBuffer
        if (buf == null || buf.capacity() < byteCount) {
            buf = ByteBuffer.allocateDirect(ANCHOR_FBO_WIDTH * BYTES_PER_RGBA_PIXEL).apply { order(ByteOrder.nativeOrder()) }
            anchorByteBuffer = buf
        }
        buf.rewind()
        GLES20.glReadPixels(0, 0, sampleCount, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val matchRatio = computeSampledMatchRatio(buf, points, sampleCount)
        listener.invoke(activeTrackingLayoutId, matchRatio)
    }

    private fun computeSampledMatchRatio(
        buf: ByteBuffer,
        points: List<AnchorPoint>,
        sampleCount: Int,
    ): Float {
        var matchCount = 0
        for (i in 0 until sampleCount) {
            val pt = points[i]
            val offset = i * BYTES_PER_RGBA_PIXEL
            val r = buf.get(offset).toInt() and 0xFF
            val g = buf.get(offset + 1).toInt() and 0xFF
            val b = buf.get(offset + 2).toInt() and 0xFF
            val diff = abs(r - pt.r) + abs(g - pt.g) + abs(b - pt.b)
            if (diff <= AnchorPresenceEvaluator.ANCHOR_DIFF_TOLERANCE) {
                matchCount++
            }
        }
        return matchCount.toFloat() / sampleCount.toFloat()
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
                if (anchorPointOesProgram != 0) GLES20.glDeleteProgram(anchorPointOesProgram)
                if (anchorPoint2dProgram != 0) GLES20.glDeleteProgram(anchorPoint2dProgram)
                releaseAnchorSampleFboIfAllocated()
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
