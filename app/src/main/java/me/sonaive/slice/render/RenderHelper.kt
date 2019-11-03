package me.sonaive.slice.render

import android.app.Application
import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import me.sonaive.slice.render.filters.GLFilter
import java.lang.ref.WeakReference

/**
 * Created by liutao on 2019-10-24.
 */

class RenderHelper {
    companion object {
        private const val TAG = "RenderHelper"
        // Surface创建
        private const val MSG_SURFACE_CREATED = 1
        // Surface改变
        private const val MSG_SURFACE_CHANGED = 2
        // Surface销毁
        private const val MSG_SURFACE_DESTROYED = 3
        // 打开相机
        private const val MSG_OPEN_CAMERA = 4
        // 切换相机
        private const val MSG_SWITCH_CAMERA = 5
        // 切换滤镜
        private const val MSG_CHANGE_FILTER = 6
        // 添加滤镜
        private const val MSG_ADD_FILTER = 7
        // 退出
        private const val MSG_QUIT = 8

        val instance: RenderHelper by lazy {
            RenderHelper()
        }
    }

    private lateinit var mRender: RenderThread
    private lateinit var mRenderHandler: RenderHandler

    fun prepareRenderThread(application: Application, rotation: Int) {
        mRender = RenderThread(application, rotation, "RenderThread")
        mRender.start()
        mRenderHandler = RenderHandler(mRender)
    }

    fun startRecording() {
        mRender.enableRecording(true)
    }

    fun stopRecording() {
        mRender.enableRecording(false)
    }

    fun getSharedEGLContext(): EGLContext? {
        return mRender.getSharedEGL()
    }

    fun surfaceCreated(surface: Any?) {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_SURFACE_CREATED, surface))
    }

    fun surfaceChanged(width: Int, height: Int) {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_SURFACE_CHANGED, width, height))
    }

    fun surfaceDestroyed() {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_SURFACE_DESTROYED))
    }

    fun openCamera() {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_OPEN_CAMERA))
    }

    fun switchCamera() {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_SWITCH_CAMERA))
    }

    fun changeFilter(filter: GLFilter) {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_CHANGE_FILTER, filter))
    }

    fun addFilter(filter: GLFilter) {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_ADD_FILTER, filter))
    }

    fun release() {
        mRenderHandler.sendMessage(mRenderHandler.obtainMessage(MSG_QUIT))
    }

    private class RenderHandler(thread: RenderThread): Handler(thread.looper) {

        private var mRenderThreadRef: WeakReference<RenderThread> = WeakReference(thread)

        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            val thread: RenderThread? = mRenderThreadRef.get()
            when(msg?.what) {
                MSG_SURFACE_CREATED -> {
                    when {
                        msg.obj is SurfaceHolder -> thread?.surfaceCreate(msg.obj as SurfaceHolder)
                        msg.obj is Surface -> thread?.surfaceCreate(msg.obj as Surface)
                        msg.obj is SurfaceTexture -> thread?.surfaceCreate(msg.obj as SurfaceTexture)
                        else -> throw IllegalArgumentException("surface is null!")
                    }
                }
                MSG_SURFACE_CHANGED -> thread?.surfaceChanged(msg.arg1, msg.arg2)
                MSG_SURFACE_DESTROYED -> thread?.surfaceDestroyed()
                MSG_OPEN_CAMERA -> thread?.openCamera()
                MSG_SWITCH_CAMERA -> thread?.switchCamera()
                MSG_CHANGE_FILTER -> thread?.replaceFilter(msg.obj as GLFilter)
                MSG_ADD_FILTER -> thread?.addFilter(msg.obj as GLFilter)
                MSG_QUIT -> {
                    Log.d(TAG, "current thread name = ${Thread.currentThread().name}")
                    Log.d(TAG, "quit loop")
                    thread?.quit()
                    mRenderThreadRef.clear()
                }
            }

        }
    }
}