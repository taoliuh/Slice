package me.sonaive.slice.render

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Message
import android.view.Surface
import android.view.SurfaceHolder
import me.sonaive.slice.render.filters.GLFilter
import java.lang.ref.WeakReference

/**
 * Created by liutao on 2019-10-18.
 */

class RenderHandler(thread: RenderThread) : Handler(thread.looper) {
    companion object {
        // Surface创建
        const val MSG_SURFACE_CREATED = 1
        // Surface改变
        const val MSG_SURFACE_CHANGED = 2
        // Surface销毁
        const val MSG_SURFACE_DESTROYED = 3
        // 打开相机
        const val MSG_OPEN_CAMERA = 4
        // 切换相机
        const val MSG_SWITCH_CAMERA = 5
        // 切换滤镜
        const val MSG_CHANGE_FILTER = 6
        // 添加滤镜
        const val MSG_ADD_FILTER = 7
    }

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
                }
            }
            MSG_SURFACE_CHANGED -> thread?.surfaceChanged(msg.arg1, msg.arg2)
            MSG_SURFACE_DESTROYED -> thread?.surfaceDestroyed()
            MSG_OPEN_CAMERA -> thread?.openCamera()
            MSG_SWITCH_CAMERA -> thread?.switchCamera()
            MSG_CHANGE_FILTER -> thread?.replaceFilter(msg.obj as GLFilter)
            MSG_ADD_FILTER -> thread?.addFilter(msg.obj as GLFilter)
        }

    }

}