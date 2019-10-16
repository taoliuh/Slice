package me.sonaive.slice.render

/**
 * Created by liutao on 30/09/2019.
 */

interface Renderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onDrawFrame()
    fun onSurfaceDestroyed()
}