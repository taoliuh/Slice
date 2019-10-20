package me.sonaive.slice.render

import android.app.Activity
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import me.sonaive.slice.camera.CameraHelper
import me.sonaive.slice.render.filters.*
import java.lang.IllegalArgumentException

/**
 * Created by liutao on 30/09/2019.
 */
class RenderThread(activity: Activity, name: String): HandlerThread(name),
    SurfaceTexture.OnFrameAvailableListener {

    companion object {
        const val TAG = "RenderThread"
    }

    private var mEgl: EGLBase? = null
    private var mEglSurface: EGLBase.EglSurface? = null

    private var mCameraFilter: CameraFilter? = null
    private var mGroupFilter: GroupFilter? = null
    private var mNoFilter: NoFilter? = null

    private var mActivity: Activity = activity

    private var mCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK

    fun surfaceCreate(surface: Any) {
        Log.d(TAG, "surfaceCreate")
        if ((surface !is SurfaceTexture) && (surface !is SurfaceHolder) && (surface !is Surface)) {
            throw IllegalArgumentException("must set a SurfaceTexture or SurfaceHolder!")
        }
        mEgl = EGLBase(null, false, false)
        mEglSurface = mEgl?.createFromSurface(surface)
        mEglSurface?.makeCurrent()

        mCameraFilter = CameraFilter(mActivity.application)
        mGroupFilter = GroupFilter(mActivity.application)
        mNoFilter = NoFilter(mActivity.application)

        mCameraFilter!!.create()
        mGroupFilter!!.create()
        mNoFilter!!.create()

        mCameraFilter!!.getSurfaceTexture()?.setOnFrameAvailableListener(this)

        openCamera()
    }

    fun surfaceChanged(width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged, width = $width, height = $height")
        mCameraFilter?.setSize(width, height)
        mGroupFilter?.setSize(width, height)
        mNoFilter?.setSize(width, height)
    }

    fun surfaceDestroyed() {
        Log.d(TAG, "surfaceDestroyed")
        release()
    }

    fun openCamera() {
        releaseCamera()
        CameraHelper.instance.prepareCameraThread()
        CameraHelper.instance.openCamera(mCameraIndex)
        val rotation = mActivity.windowManager.defaultDisplay.rotation
        CameraHelper.instance.initCamera(rotation, 9f / 16f)
        CameraHelper.instance.setPreviewTexture(mCameraFilter!!.getSurfaceTexture())
        CameraHelper.instance.startPreview()
    }

    fun switchCamera() {
        mCameraIndex = if (mCameraIndex == Camera.CameraInfo.CAMERA_FACING_BACK) {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            Camera.CameraInfo.CAMERA_FACING_BACK
        }
        openCamera()
    }

    fun addFilter(filter: GLFilter) {
        mGroupFilter?.addFilter(filter)
    }

    fun replaceFilter(filter: GLFilter) {
        mGroupFilter?.clearAll()
        mGroupFilter?.addFilter(filter)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        drawFrame()
    }

    private fun drawFrame() {
        mEglSurface?.makeCurrent()
        mCameraFilter?.draw()
        mGroupFilter?.setTextureId(mCameraFilter?.getOutputTextureId() ?: -1)
        mGroupFilter?.draw()
        mNoFilter?.setTextureId(mGroupFilter?.getOutputTextureId() ?: -1)
        mNoFilter?.draw()
        mEglSurface?.swap()
    }

    private fun release() {
        releaseCamera()
        mCameraFilter?.release()
        mEglSurface?.makeCurrent()
        mEglSurface?.release()
        mEglSurface = null
        mEgl?.release()
        mEgl = null
    }

    private fun releaseCamera() {
        CameraHelper.instance.stopPreview()
        CameraHelper.instance.release()
    }
}