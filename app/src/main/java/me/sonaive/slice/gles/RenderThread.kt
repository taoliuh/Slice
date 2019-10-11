package me.sonaive.slice.gles

import android.graphics.SurfaceTexture
import android.os.HandlerThread
import android.view.SurfaceHolder
import java.lang.IllegalArgumentException

/**
 * Created by liutao on 30/09/2019.
 */
class RenderThread(name: String): HandlerThread(name) {
    var mRenderer: Renderer? = null
    var mEgl: EGLBase? = null
    var mEglSurface: EGLBase.EglSurface? = null

    fun surfaceCreate(surface: Any) {
        if (surface !is SurfaceTexture && surface !is SurfaceHolder) {
            throw IllegalArgumentException("must set a SurfaceTexture or SurfaceHolder!")
        }
        mEgl = EGLBase(null, false, false)
        mEglSurface = mEgl?.createFromSurface(surface)
        mEglSurface?.makeCurrent()
        mRenderer?.onSurfaceCreated()
    }

    fun surfaceChanged(width: Int, height: Int) {
        mRenderer?.onSurfaceChanged(width, height)
    }

    fun surfaceDestroyed() {
        mRenderer?.onSurfaceDestroyed()
    }

    fun drawFrame() {
        mEglSurface?.makeCurrent()
        mRenderer?.onDrawFrame()
        mEglSurface?.swap()
    }

    fun release() {
        mRenderer = null
        mEglSurface?.release()
        mEglSurface = null
        mEgl?.release()
        mEgl = null
        looper.quit()
    }
}