package me.sonaive.slice.render

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.view.View
import android.view.ViewGroup
import me.sonaive.slice.render.filters.CameraFilter
import me.sonaive.slice.render.filters.GLFilter
import me.sonaive.slice.render.filters.GroupFilter
import me.sonaive.slice.render.filters.NoFilter
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import javax.microedition.khronos.opengles.GL10

/**
 * Created by liutao on 12/10/2019.
 */

class GLController(application: Application): GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "GLController"
        private const val OPENGL_ES_VERSION = 2
    }

    private var mGLView: GLView? = null
    private var mSurface: Any? = null
    private var mRenderCallback: RenderCallback? = null
    private var mCameraFilter: CameraFilter? = null
    private var mGroupFilter: GroupFilter? = null
    private var mNoFilter: NoFilter? = null
    private var mApplication: Application = application

    init {
        mGLView = GLView(application)
        val vg = object : ViewGroup(application) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        }
        vg.addView(mGLView)
        vg.visibility = View.GONE

    }

    fun onResume() {
        mGLView?.onResume()
    }

    fun onPause() {
        mGLView?.queueEvent {
            mCameraFilter?.release()
            mGroupFilter?.release()
            mCameraFilter = null
            mGroupFilter = null
        }
        mGLView?.onPause()
    }

    fun requestRender() {
        mGLView?.requestRender()
    }

    fun commitTask(runnable: Runnable) {
        mGLView?.queueEvent(runnable)
    }

    fun getSurfaceTexture(): SurfaceTexture? {
        return mCameraFilter?.getSurfaceTexture()
    }

    fun setRenderCallback(callback: RenderCallback) {
        mRenderCallback = callback
    }

    fun addFilter(filter: GLFilter) {
        mGroupFilter?.addFilter(filter)
    }

    fun replaceFilter(filter: GLFilter) {
        mGroupFilter?.clearAll()
        mGroupFilter?.addFilter(filter)
    }

    fun surfaceCreated(nativeWindow: Any) {
        mSurface = nativeWindow
        mGLView?.surfaceCreated(null)
    }

    fun surfaceChanged(width: Int, height: Int) {
        mGLView?.surfaceChanged(null, 0, width, height)
    }

    fun surfaceDestroyed() {
        mGLView?.surfaceDestroyed(null)
    }

    fun release() {
        if (mRenderCallback != null) {
            mRenderCallback = null
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        mCameraFilter?.draw()
        mGroupFilter?.setTextureId(mCameraFilter?.getOutputTextureId() ?: -1)
        mGroupFilter?.draw()
        mNoFilter?.setTextureId(mGroupFilter?.getOutputTextureId() ?: -1)
        mNoFilter?.draw()
        mRenderCallback?.onDrawFrame(gl)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        mCameraFilter?.setSize(width, height)
        mGroupFilter?.setSize(width, height)
        mNoFilter?.setSize(width, height)
        mRenderCallback?.onSurfaceChanged(gl, width, height)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        mCameraFilter = CameraFilter(mApplication)
        mGroupFilter = GroupFilter(mApplication)
        mNoFilter = NoFilter(mApplication)
        mCameraFilter?.create()
        mGroupFilter?.create()
        mNoFilter?.create()
        mRenderCallback?.onSurfaceCreated(gl, config)
    }

    private inner class GLView(context: Context) : GLSurfaceView(context) {

        init {
            holder.addCallback(null)
            setEGLWindowSurfaceFactory(object : GLSurfaceView.EGLWindowSurfaceFactory {
                override fun createWindowSurface(
                    egl: EGL10, display: EGLDisplay,
                    config: EGLConfig, nativeWindow: Any
                ): EGLSurface {
                    return egl.eglCreateWindowSurface(display, config, mSurface, null)
                }

                override fun destroySurface(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
                    egl.eglDestroySurface(display, surface)
                }
            })
            setEGLContextClientVersion(OPENGL_ES_VERSION)
            setRenderer(this@GLController)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

}