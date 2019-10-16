package me.sonaive.slice.render

import android.opengl.GLSurfaceView

/**
 * Created by liutao on 12/10/2019.
 */
interface RenderCallback: GLSurfaceView.Renderer {
    fun onSurfaceDestroyed()
}