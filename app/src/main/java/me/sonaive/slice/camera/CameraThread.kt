package me.sonaive.slice.camera

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.os.Looper
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList

/**
 * Created by liutao on 12/10/2019.
 */

class CameraThread(name: String): Thread(name) {

    companion object {
        private const val TAG = "CameraThread"
        private var ORIENTATIONS = SparseIntArray()
    }

    private var mCamera: Camera? = null
    private var mFacing = -1
    private var isReady = false
    private var mHandler: CameraHelper.CameraHandler? = null
    private var mReadyFence = Object()

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    /*******************************************************************************************************************
     *
     *    public methods
     *
     ******************************************************************************************************************/

    fun waitUntilReady() {
        synchronized(mReadyFence) {
            while (!isReady) {
                try {
                    mReadyFence.wait()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getHandler(): CameraHelper.CameraHandler? {
        return mHandler
    }

    fun createCamera(cameraId: Int) {
        synchronized(mReadyFence) {
            openCamera(cameraId)
        }
    }

    fun initCamera(rotation: Int, ratio: Float) {
        synchronized(mReadyFence) {
            setupCamera(rotation, ratio)
        }
    }

    fun switchCamera(rotation: Int, cameraIndex: Int, ration: Float) {
        synchronized(mReadyFence) {
            releaseCamera()
            openCamera(cameraIndex)
            setupCamera(rotation, ration)
        }
    }

    fun enableFlash(enable: Boolean) {
        synchronized(mReadyFence) {
            setFlash(enable)
        }
    }

    fun handleFocus(focusParams: FocusParams) {
        synchronized(mReadyFence) {
            if (mCamera == null) return
            val params = mCamera!!.parameters
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(mFacing, info)
            val focusRect = focusParams.getFocusRect()
            val meteringRect = focusParams.getMeteringRect()
            mCamera!!.cancelAutoFocus()
            if (mFacing != Camera.CameraInfo.CAMERA_FACING_FRONT && params.maxNumFocusAreas > 0) {
                val focusAreas = ArrayList<Camera.Area>()
                focusAreas.add(Camera.Area(focusRect, FocusParams.FOCUS_SIDE))
                params.focusAreas = focusAreas
            }
            if (params.maxNumMeteringAreas > 0) {
                val meteringAreas = ArrayList<Camera.Area>()
                meteringAreas.add(Camera.Area(meteringRect, FocusParams.FOCUS_SIDE))
                params.meteringAreas = meteringAreas
            }
            params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            mCamera!!.parameters = params
            mCamera!!.autoFocus(null)
        }
    }

    fun takePicture(callback: TakePictureCallback?, rotate: Int) {
        synchronized(mReadyFence) {
            if (mCamera == null) return
            val params = mCamera!!.parameters
            var rotation = rotate
            if (isFront()) {
                rotation = Math.abs(360 - rotate) % 360
            }
            params.setRotation(rotation)
            mCamera!!.parameters = params
            mCamera!!.takePicture(null, null, Camera.PictureCallback { data, camera ->
                callback?.onTakePicture(data)
                try {
                    camera.startPreview()
                } catch (e: Exception) {
                    Log.w(TAG, "take picture failed! cause: " + e.message)
                }
            })
        }
    }

    fun handleShrink(shrink: Boolean) {
        synchronized(mReadyFence) {
            if (mCamera == null) return
            val params = mCamera!!.parameters ?: return
            if (params.isZoomSupported) {
                val maxZoom = params.maxZoom
                var zoom = params.zoom
                if (!shrink and (zoom < maxZoom)) {
                    ++zoom
                } else if (zoom > 0) {
                    --zoom
                }
                params.zoom = zoom
                mCamera!!.parameters = params
            }
        }
    }

    fun startPreview() {
        synchronized(mReadyFence) {
            if (mCamera == null) return
            try {
                mCamera!!.startPreview()
            } catch (e: Exception) {
                Log.w(TAG, "startPreview failed! cause: " + e.message)
            }
        }
    }

    fun stopPreview() {
        synchronized(mReadyFence) {
            releaseCamera()
        }
    }

    fun setPreviewTexture(surfaceTexture: SurfaceTexture) {
        synchronized(mReadyFence) {
            try {
                mCamera?.setPreviewTexture(surfaceTexture)
            } catch (e: Exception) {
                Log.w(TAG, "setPreviewTexture failed! cause: " + e.message)
            }
        }
    }

    fun setDisplay(holder: SurfaceHolder) {
        synchronized(mReadyFence) {
            try {
                mCamera?.setPreviewDisplay(holder)
            } catch (e: Exception) {
                Log.w(TAG, "setDisplay failed! cause: " + e.message)
            }
        }
    }

    override fun run() {
        Looper.prepare()
        synchronized(mReadyFence) {
            mHandler = CameraHelper.CameraHandler(this)
            isReady = true
            mReadyFence.notify()
        }
        Looper.loop()
        synchronized(mReadyFence) {
            isReady = false
            mHandler = null
        }
    }

    /*******************************************************************************************************************
     *
     *    private methods
     *
     ******************************************************************************************************************/

    private fun openCamera(cameraId: Int) {
        if ((cameraId != Camera.CameraInfo.CAMERA_FACING_FRONT)
            and (cameraId != Camera.CameraInfo.CAMERA_FACING_BACK)) {
            throw IllegalArgumentException("illegal camera id")
        }
        Log.d(TAG, "openCamera")
        try {
            mCamera = Camera.open(cameraId)
            var info = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            mFacing = info.facing
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) and info.canDisableShutterSound) {
                mCamera?.enableShutterSound(false) // 关闭快门声音
            }
        } catch (e: Exception) {
            Log.e(TAG, "get camera info failed! cause: " + e.message)
        }
    }

    private fun setupCamera(rotation: Int, ratio: Float) {
        if (mCamera == null) {
            Log.e(TAG, "camera is null")
            return
        }
        try {
            val camera = mCamera
            setFlash(false)
            setAF(camera)
            setCAF(camera)
            val params = camera!!.parameters
            val previewSize = calculateMaxSize(params.supportedPreviewSizes, ratio)
            params.setPreviewSize(previewSize.width, previewSize.height)
            val pictureSize = calculateMaxSize(params.supportedPictureSizes, ratio)
            params.setPictureSize(pictureSize.width, pictureSize.height)
            // 适配nexus 6系列拍摄预览倒置的问题
            if (Build.MODEL.equals("Nexus 6") && isFront()) {
                camera.setDisplayOrientation(0)
            } else if (Build.MODEL.equals("Nexus 6P") && isFront()) {
                camera.setDisplayOrientation(270)
            } else {
                camera.setDisplayOrientation(ORIENTATIONS.get(rotation))
            }
            camera.parameters = params
        } catch (e: Exception) {
            Log.e(TAG, "setupCamera failed! cause: " + e.message)
        }
    }

    private fun setFlash(enable: Boolean) {
        if ((mCamera == null) or (mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT)) {
            return
        }
        val params = mCamera?.parameters
        params?.flashMode = if (enable) Camera.Parameters.FLASH_MODE_ON else Camera.Parameters.FLASH_MODE_OFF
        applyCameraParams(mCamera, params)
    }

    private fun setAF(camera: Camera?) {
        val params = camera?.parameters
        if (params?.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_AUTO) == true) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            applyCameraParams(camera, params)
        }
    }

    private fun setCAF(camera: Camera?) {
        val params = camera?.parameters
        if (params?.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            applyCameraParams(camera, params)
        } else if (params?.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            applyCameraParams(camera, params)
        }
    }

    private fun applyCameraParams(camera: Camera?, params: Camera.Parameters?) {
        try {
            camera?.parameters = params
        } catch (e: Exception) {
            Log.w(TAG, "set camera parameters failed! cause: " + e.message)
        }
    }

    private fun calculateMaxSize(supportedSize: List<Camera.Size>, ratio: Float): Camera.Size {
        val targetRatioSize = ArrayList<Camera.Size>()
        val sizesString = StringBuilder()
        for (size in supportedSize) {
            val r = size.height / size.width.toFloat()
            if (r == ratio) {
                sizesString.append(size.width).append('x')
                    .append(size.height).append(' ')
                targetRatioSize.add(size)
            }
        }
        Log.d(TAG, sizesString.toString())
        return Collections.max<Camera.Size>(targetRatioSize, CameraSizeComparator())
    }

    private fun isFront(): Boolean {
        return mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT
    }

    private fun releaseCamera() {
        if (mCamera == null) return
        try {
            Log.d(TAG, "release camera")
            mCamera?.stopPreview()
            mCamera?.release()
            mCamera = null
        } catch (e: Exception) {
            Log.w(TAG, "release camera failed! cause: " + e.message)
        }

    }

    private inner class CameraSizeComparator: Comparator<Camera.Size> {
        override fun compare(o1: Camera.Size, o2: Camera.Size): Int {
            return when {
                o1.width == o2.width -> 0
                o1.width > o2.width -> 1
                else -> -1
            }
        }
    }
}