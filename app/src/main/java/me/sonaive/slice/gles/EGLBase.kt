package me.sonaive.slice.gles

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Created by liutao on 25/09/2019.
 */

class EGLBase(context: EGLContext?, withDepthBuffer: Boolean, isRecordable: Boolean) {

    companion object {
        private const val TAG = "EGLBase"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }

    var mEglConfig: EGLConfig? = null
    var mEglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    var mEglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    var mDefaultContext: EGLContext = EGL14.EGL_NO_CONTEXT

    init {
        Log.i(TAG, "EGLBase init")
        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            throw IllegalStateException("EGL already set up!")
        }
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed!")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed!")
        }
        if (mEglContext == EGL14.EGL_NO_CONTEXT) {
            mEglConfig = getConfig(withDepthBuffer, isRecordable)
            if (mEglConfig == null) {
                throw RuntimeException("chooseConfig failed!")
            }
            mEglContext = createContext(context)
        }
        val values = IntArray(1)
        EGL14.eglQueryContext(mEglDisplay, mEglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        makeDefault()
    }

    private fun getConfig(withDepthBuffer: Boolean, isRecordable: Boolean): EGLConfig? {
        val attributeList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_ALPHA_SIZE,
            8,
            EGL14.EGL_NONE,
            EGL14.EGL_NONE, //EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE,
            EGL14.EGL_NONE, //EGL_RECORDABLE_ANDROID, 1,	// this flag need to recording of MediaCodec
            EGL14.EGL_NONE,
            EGL14.EGL_NONE, //	with_depth_buffer ? EGL14.EGL_DEPTH_SIZE : EGL14.EGL_NONE,
            // with_depth_buffer ? 16 : 0,
            EGL14.EGL_NONE
        )
        var offset = 10
        if (withDepthBuffer) {
            attributeList[offset++] = EGL14.EGL_DEPTH_SIZE
            attributeList[offset++] = 16
        }
        if (isRecordable && Build.VERSION.SDK_INT >= 18) { // 配合MediaCodec InputSurface
            attributeList[offset++] = EGL_RECORDABLE_ANDROID
            attributeList[offset++] = 1
        }
        for (i in attributeList.size - 1 downTo offset) {
            attributeList[i] = EGL14.EGL_NONE
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(mEglDisplay, attributeList, 0, configs,
                0, configs.size, numConfigs, 0)) {
            // XXX it will be better to fallback to RGB565
            Log.w(TAG, "unable to find RGBA8888 EGLConfig")
            return null
        }
        return configs[0]
    }

    private fun createContext(context: EGLContext?): EGLContext {
        val attributeList = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        return EGL14.eglCreateContext(
            mEglDisplay, mEglConfig, context,
            attributeList, 0
        )
    }

    private fun makeDefault() {
        if (!EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            Log.w(TAG, "makeDefault" + EGL14.eglGetError())
        }
    }

    private fun destroyContext() {
        Log.i(TAG, "destroyContext")
        if (!EGL14.eglDestroyContext(mEglDisplay, mEglContext)) {
            Log.e(TAG, "eglDestroyContext, display: $mEglDisplay context: $mEglContext")
            Log.e(TAG, "eglDestroyContext: ${EGL14.eglGetError()}")
        }
        mEglContext = EGL14.EGL_NO_CONTEXT
        if (mDefaultContext !== EGL14.EGL_NO_CONTEXT) {
            if (!EGL14.eglDestroyContext(mEglDisplay, mDefaultContext)) {
                Log.e(TAG, "display: $mEglDisplay context: $mDefaultContext")
                Log.e(TAG, "eglDestroyContext:" + EGL14.eglGetError())
            }
            mDefaultContext = EGL14.EGL_NO_CONTEXT
        }
    }

    private fun createWindowSurface(nativeWindow: Any): EGLSurface? {
        val surfaceAttributes = intArrayOf(EGL14.EGL_NONE)
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, nativeWindow, surfaceAttributes, 0)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "eglCreateWindowSurface $e")
        }
        return result
    }

    private fun createOffscreenSurface(width: Int, height: Int): EGLSurface? {
        Log.v(TAG, "createOffscreenSurface:")
        val surfaceAttributes = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreatePbufferSurface(mEglDisplay, mEglConfig, surfaceAttributes, 0)
            if (result == null) {
                throw RuntimeException("surface was null")
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "createOffscreenSurface", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "createOffscreenSurface", e)
        }
        return result
    }

    private fun makeCurrent(surface: EGLSurface?): Boolean {
        if (mEglDisplay == null) {
            Log.w(TAG, "makeCurrent: eglDisplay not initialized")
        }
        if (surface == null || surface === EGL14.EGL_NO_SURFACE) {
            val error = EGL14.eglGetError()
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "makeCurrent:returned EGL_BAD_NATIVE_WINDOW.")
            }
            return false
        }
        // attach EGL rendering context to specific EGL window surface
        if (!EGL14.eglMakeCurrent(mEglDisplay, surface, surface, mEglContext)) {
            Log.w(TAG, "eglMakeCurrent:" + EGL14.eglGetError())
            return false
        }
        return true
    }

    private fun destroyWindowSurface(surface: EGLSurface?) {
        Log.v(TAG, "destroyWindowSurface")
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(mEglDisplay, surface)
        }
        Log.d(TAG, "destroyWindowSurface: finished")
    }

    private fun swap(surface: EGLSurface?): Int {
        if (!EGL14.eglSwapBuffers(mEglDisplay, surface)) {
            val err = EGL14.eglGetError()
            Log.w(TAG, "swap: error = $err")
            return err
        }
        return EGL14.EGL_SUCCESS
    }

    fun createFromSurface(surface: Any): EglSurface {
        val eglSurface = EglSurface(this, surface)
        eglSurface.makeCurrent()
        return eglSurface
    }

    fun createOffscreen(width: Int, height: Int): EglSurface {
        val eglSurface = EglSurface(this, width, height)
        eglSurface.makeCurrent()
        return eglSurface
    }

    fun getContext(): EGLContext {
        return mEglContext
    }

    fun querySurface(eglSurface: EGLSurface?, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(mEglDisplay, eglSurface, what, value, 0)
        return value[0]
    }

    fun release() {
        Log.d(TAG, "release")
        if (mEglDisplay !== EGL14.EGL_NO_DISPLAY) {
            destroyContext()
            EGL14.eglTerminate(mEglDisplay)
            EGL14.eglReleaseThread()
        }
        mEglDisplay = EGL14.EGL_NO_DISPLAY
        mEglContext = EGL14.EGL_NO_CONTEXT
    }

    class EglSurface() {
        lateinit var mEgl: EGLBase
        var mWidth: Int = 0
        var mHeight: Int = 0
        var mEglSurface: EGLSurface? = EGL14.EGL_NO_SURFACE

        constructor(egl: EGLBase, surface: Any): this() {
            if (!(surface is SurfaceView
                        || surface is Surface
                        || surface is SurfaceHolder
                        || surface is SurfaceTexture)) {
                throw java.lang.IllegalArgumentException("unsupported surface")
            }
            mEgl = egl
            mEglSurface = mEgl.createWindowSurface(surface)
            mWidth = mEgl.querySurface(mEglSurface, EGL14.EGL_WIDTH)
            mHeight = mEgl.querySurface(mEglSurface, EGL14.EGL_HEIGHT)
            Log.d(TAG, "EglSurface:size($mWidth, $mHeight)")
        }

        constructor(egl: EGLBase, width: Int, height: Int): this() {
            Log.d(TAG, "EglSurface, create")
            mEgl = egl
            mEglSurface = mEgl.createOffscreenSurface(width, height)
            mWidth = width
            mHeight = height
        }

        fun makeCurrent() {
            mEgl.makeCurrent(mEglSurface)
        }

        fun swap() {
            mEgl.swap(mEglSurface)
        }

        fun getContext(): EGLContext {
            return mEgl.getContext()
        }

        fun release() {
            Log.d(TAG, "EglSurface:release")
            mEgl.makeDefault()
            mEgl.destroyWindowSurface(mEglSurface)
            mEglSurface = EGL14.EGL_NO_SURFACE
        }

        fun getWidth(): Int {
            return mWidth
        }

        fun getHeight(): Int {
            return mHeight
        }
    }
}