package me.sonaive.slice.camera

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.SurfaceHolder
import java.lang.ref.WeakReference

/**
 * Created by liutao on 12/10/2019.
 */

class CameraHelper private constructor() {
    companion object {
        private const val TAG = "CameraHelper"

        private const val MSG_OPEN_CAMERA = 1
        private const val MSG_INIT_CAMERA = 2
        private const val MSG_HANDLE_FOCUS = 3
        private const val MSG_HANDLE_SHRINK = 4
        private const val MSG_TAKE_PICTURE = 5
        private const val MSG_START_PREVIEW = 6
        private const val MSG_STOP_PREVIEW = 7
        private const val MSG_SET_PREVIEW_TEXTURE = 8
        private const val MSG_ENABLE_FLASH = 9
        private const val MSG_SWITCH_CAMERA = 10
        private const val MSG_QUIT = 11
        private const val MSG_SET_DISPLAY = 12

        val instance: CameraHelper by lazy {
            CameraHelper()
        }
    }

    private var mCamera: CameraThread? = null

    fun prepareCameraThread() {
        mCamera = CameraThread(this)
        mCamera?.start()
        // 调用方为OpenGL线程 里面有线程等待，会在这里等一会。如果此时主线程将mCamera置空，会触发空指针
        mCamera?.waitUntilReady()
    }


    fun openCamera(cameraId: Int) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_OPEN_CAMERA, cameraId, 0))
    }

    fun switchCamera(rotation: Int, cameraIndex: Int, ratio: Double) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_SWITCH_CAMERA, rotation, cameraIndex, ratio))
    }

    fun release() {
        val handler = mCamera?.getHandler() ?: return
        handler.sendEmptyMessage(MSG_QUIT)
        mCamera = null
    }

    fun initCamera(rotation: Int, ratio: Float) {
        val handler = mCamera?.getHandler() ?: return
        val msg = handler.obtainMessage()
        msg.what = MSG_INIT_CAMERA
        msg.arg1 = rotation
        msg.obj = ratio
        handler.sendMessage(msg)
    }

    fun takePicture(callback: TakePictureCallback, rotate: Int) {
        val handler = mCamera?.getHandler() ?: return
        val msg = handler.obtainMessage()
        msg.what = MSG_TAKE_PICTURE
        msg.obj = callback
        msg.arg1 = rotate
        handler.sendMessage(msg)
    }

    fun handleFocus(params: FocusParams) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_HANDLE_FOCUS, params))
    }

    fun handleShrink(shrink: Boolean) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_HANDLE_SHRINK, shrink))
    }

    fun setDisplay(holder: SurfaceHolder) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_SET_DISPLAY, holder))
    }

    fun startPreview() {
        val handler = mCamera?.getHandler() ?: return
        handler.sendEmptyMessage(MSG_START_PREVIEW)
    }

    fun stopPreview() {
        val handler = mCamera?.getHandler() ?: return
        handler.sendEmptyMessage(MSG_STOP_PREVIEW)
    }

    fun setPreviewTexture(surfaceTexture: SurfaceTexture?) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_SET_PREVIEW_TEXTURE, surfaceTexture))
    }

    fun enableFlash(enable: Boolean) {
        val handler = mCamera?.getHandler() ?: return
        handler.sendMessage(handler.obtainMessage(MSG_ENABLE_FLASH, enable))
    }

    class CameraHandler(camera: CameraThread) : Handler() {

        private var mWeakCamera: WeakReference<CameraThread>? = WeakReference(camera)

        override fun handleMessage(msg: Message) {
            val camera: CameraThread = mWeakCamera?.get() ?: return
            when (msg.what) {
                MSG_OPEN_CAMERA -> camera.createCamera(msg.arg1)
                MSG_INIT_CAMERA -> camera.initCamera(msg.arg1, msg.obj as Float)
                MSG_HANDLE_FOCUS -> camera.handleFocus(msg.obj as FocusParams)
                MSG_HANDLE_SHRINK -> camera.handleShrink(msg.obj as Boolean)
                MSG_TAKE_PICTURE -> camera.takePicture(msg.obj as TakePictureCallback, msg.arg1)
                MSG_START_PREVIEW -> camera.startPreview()
                MSG_STOP_PREVIEW -> camera.stopPreview()
                MSG_SET_PREVIEW_TEXTURE -> camera.setPreviewTexture(msg.obj as SurfaceTexture)
                MSG_ENABLE_FLASH -> camera.enableFlash(msg.obj as Boolean)
                MSG_SWITCH_CAMERA -> camera.switchCamera(msg.arg1, msg.arg2, msg.obj as Float)
                MSG_QUIT -> {
                    Log.d(TAG, "quit loop")
                    Looper.myLooper()!!.quit()
                    release()
                }
                MSG_SET_DISPLAY -> camera.setDisplay(msg.obj as SurfaceHolder)
            }
        }

        private fun release() {
            mWeakCamera?.clear()
            mWeakCamera = null
        }
    }
}