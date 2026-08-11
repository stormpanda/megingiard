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
import com.stormpanda.megingiard.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
 * 100% GPU-accelerated temporal frame blender for screen mirroring motion smoothing.
 *
 * Runs an OpenGL ES 2.0 ping-pong FBO pipeline on a dedicated GL thread. Blends incoming OES
 * frames from MediaProjection/DirectMirrorServer with previous frame textures entirely inside
 * GPU VRAM — eliminating TextureView.getBitmap() CPU readbacks, frame truncation, and brightness darkening.
 */
class GpuMotionSmoother(
    private val outputSurface: Surface,
    private val width: Int,
    private val height: Int,
    private var strength: Int,
) {
    private val glThread = HandlerThread("GpuMotionSmootherGL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var blendProgram = 0
    private var drawProgram = 0
    private var passthroughProgram = 0

    private var oesTextureId = 0
    private val fboTextureIds = IntArray(2)
    private val fboFramebuffers = IntArray(2)

    private var inputSurfaceTexture: SurfaceTexture? = null
    var inputSurface: Surface? = null
        private set

    private val stMatrix = FloatArray(16)
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private var readIndex = 0
    private var writeIndex = 1
    private var isFirstFrame = true

    @Volatile
    private var released = false

    init {
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

    fun updateStrength(newStrength: Int) {
        if (released) return
        glHandler.post {
            this.strength = newStrength
            isFirstFrame = true
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

            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            setupShaders()
            setupTexturesAndFbos()

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
    }

    private fun setupTexturesAndFbos() {
        val texs = IntArray(1)
        GLES20.glGenTextures(1, texs, 0)
        oesTextureId = texs[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glGenTextures(2, fboTextureIds, 0)
        GLES20.glGenFramebuffers(2, fboFramebuffers, 0)

        for (i in 0..1) {
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
    }

    private fun renderFrame() {
        val st = inputSurfaceTexture ?: return
        if (released) return
        try {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)

            GLES20.glViewport(0, 0, width, height)

            if (strength <= 0) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                GLES20.glUseProgram(passthroughProgram)

                val aPos = GLES20.glGetAttribLocation(passthroughProgram, "aPosition")
                val aTex = GLES20.glGetAttribLocation(passthroughProgram, "aTextureCoord")
                val uMat = GLES20.glGetUniformLocation(passthroughProgram, "uSTMatrix")
                val uOes = GLES20.glGetUniformLocation(passthroughProgram, "uOesTexture")

                GLES20.glEnableVertexAttribArray(aPos)
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

                GLES20.glEnableVertexAttribArray(aTex)
                GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

                GLES20.glUniformMatrix4fv(uMat, 1, false, stMatrix, 0)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES20.glUniform1i(uOes, 0)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                isFirstFrame = true
                return
            }

            // Pass 1: Render OES input frame + Previous FBO frame into Target FBO
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboFramebuffers[writeIndex])
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(blendProgram)

            val aPos = GLES20.glGetAttribLocation(blendProgram, "aPosition")
            val aTex = GLES20.glGetAttribLocation(blendProgram, "aTextureCoord")
            val uMat = GLES20.glGetUniformLocation(blendProgram, "uSTMatrix")
            val uAlpha = GLES20.glGetUniformLocation(blendProgram, "uAlpha")
            val uFirst = GLES20.glGetUniformLocation(blendProgram, "uFirstFrame")
            val uOes = GLES20.glGetUniformLocation(blendProgram, "uOesTexture")
            val uPrev = GLES20.glGetUniformLocation(blendProgram, "uPreviousTexture")

            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glUniformMatrix4fv(uMat, 1, false, stMatrix, 0)

            val alphaPercent = (100 - strength).coerceIn(1, 99) / 100.0f
            GLES20.glUniform1f(uAlpha, alphaPercent)
            GLES20.glUniform1i(uFirst, if (isFirstFrame) 1 else 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(uOes, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureIds[readIndex])
            GLES20.glUniform1i(uPrev, 1)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Pass 2: Draw smoothed FBO texture onto Output Window Surface
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(drawProgram)

            val aPosDraw = GLES20.glGetAttribLocation(drawProgram, "aPosition")
            val aTexDraw = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord")
            val uTexDraw = GLES20.glGetUniformLocation(drawProgram, "uTexture")

            GLES20.glEnableVertexAttribArray(aPosDraw)
            GLES20.glVertexAttribPointer(aPosDraw, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexDraw)
            GLES20.glVertexAttribPointer(aTexDraw, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureIds[writeIndex])
            GLES20.glUniform1i(uTexDraw, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            // Swap FBO indices
            readIndex = writeIndex
            writeIndex = 1 - writeIndex
            isFirstFrame = false
        } catch (e: Exception) {
            AppLog.e(TAG, "Error rendering frame in GpuMotionSmoother", e)
        }
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
                if (oesTextureId != 0) GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                GLES20.glDeleteTextures(2, fboTextureIds, 0)
                GLES20.glDeleteFramebuffers(2, fboFramebuffers, 0)

                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
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
