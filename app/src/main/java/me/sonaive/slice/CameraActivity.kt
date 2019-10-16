package me.sonaive.slice

import android.Manifest
import android.graphics.PixelFormat
import android.hardware.Camera
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.view.SurfaceHolder
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.sonaive.slice.camera.CameraHelper
import me.sonaive.slice.render.GLController
import me.sonaive.slice.render.RenderCallback
import me.sonaive.slice.render.filters.GrayFilter
import me.sonaive.slice.utils.PermissionUtils
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraActivity : FragmentActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQ_PERMISSION_SET = 1
    }

    private var mCamera: Camera? = null
    private var mGLController: GLController? = null
    private var mCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK

    private val mRenderCallback: RenderCallback = object : RenderCallback {
        override fun onDrawFrame(gl: GL10?) {

        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {

        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            initCamera()
        }

        override fun onSurfaceDestroyed() {
            releaseCamerea()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        surface_view.holder.setFormat(PixelFormat.RGBA_8888)
        surface_view.holder.addCallback(this)
        mGLController = GLController(application)
        PermissionUtils.askPermission(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            REQ_PERMISSION_SET,
            Runnable {
                // do nothing
            }
        )
    }

    override fun onResume() {
        super.onResume()
        mGLController?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mGLController?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mGLController?.release()
        mGLController = null
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
//        mCamera = Camera.open()
//        try {
//            mCamera?.setPreviewDisplay(holder)
//            mCamera?.startPreview()
//        } catch (e: IOException) {
//            Log.e(TAG, "camera preview failed, cause: " + e.message)
//        }
        mGLController?.surfaceCreated(holder as Any)
        mGLController?.setRenderCallback(mRenderCallback)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        mGLController?.surfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
//        mCamera?.stopPreview()
//        mCamera?.release()
        mRenderCallback.onSurfaceDestroyed()
        mGLController?.surfaceDestroyed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionUtils.onRequestPermissionsResult(requestCode == REQ_PERMISSION_SET, grantResults,
            Runnable {
                // do nothing
            },
            Runnable {
                Toast.makeText(
                    application, R.string.request_permission_failed,
                    Toast.LENGTH_SHORT
                ).show()
            })
    }

    private fun initCamera() {
        CameraHelper.instance.prepareCameraThread()
        CameraHelper.instance.openCamera(mCameraIndex)
        val rotation = windowManager.defaultDisplay.rotation
        CameraHelper.instance.initCamera(rotation, 9f / 16f)
        val surfaceTexture = mGLController?.getSurfaceTexture() ?: return
        CameraHelper.instance.setPreviewTexture(surfaceTexture)
        onFilterSet()
        surfaceTexture.setOnFrameAvailableListener { mGLController?.requestRender() }
        CameraHelper.instance.startPreview()
    }

    private fun onFilterSet() {
        val filter = GrayFilter(application)
        mGLController?.addFilter(filter)
    }

    private fun releaseCamerea() {
        CameraHelper.instance.stopPreview()
        CameraHelper.instance.release()
    }
}
