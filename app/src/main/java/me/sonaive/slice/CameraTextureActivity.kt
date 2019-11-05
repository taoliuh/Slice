package me.sonaive.slice

import android.Manifest
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.TextureView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.bt_record
import kotlinx.android.synthetic.main.activity_texture_camera.*
import me.sonaive.slice.recorder.HardwareEncoder
import me.sonaive.slice.recorder.MediaEncoder
import me.sonaive.slice.render.RenderHelper
import me.sonaive.slice.render.filters.GrayFilter
import me.sonaive.slice.utils.PermissionUtils

class CameraTextureActivity : FragmentActivity(), TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQ_PERMISSION_SET = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_texture_camera)
        surface_view.surfaceTextureListener = this
        RenderHelper.instance.prepareRenderThread(application, windowManager.defaultDisplay.rotation)
        bt_record.setOnClickListener {
            if (bt_record.text == "START") {
                HardwareEncoder.instance.prepareRecorder()
                    .setOutputPath("/mnt/sdcard/a.mp4")
                    .enableAudioRecord(true)
                    .enableHDMode(true)
                    .initApplication(application)
                    .initRecorder(1920, 1080, object: MediaEncoder.MediaEncoderListener {
                        override fun onPrepared(encoder: MediaEncoder) {

                        }

                        override fun onStarted(encoder: MediaEncoder) {

                        }

                        override fun onStopped(encoder: MediaEncoder) {

                        }

                        override fun onReleased(encoder: MediaEncoder) {
                            HardwareEncoder.instance.destroyRecorder()
                        }

                        override fun onError(error: Int) {

                        }

                    })
                HardwareEncoder.instance.startRecording(RenderHelper.instance.getSharedEGLContext())
                RenderHelper.instance.startRecording()
                bt_record.text = "STOP"
            } else {
                HardwareEncoder.instance.stopRecording()
                RenderHelper.instance.stopRecording()
                bt_record.text = "START"
            }
        }

        PermissionUtils.askPermission(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            REQ_PERMISSION_SET,
            Runnable {
                // do nothing
            }
        )
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged, width = $width , height = $height")
        RenderHelper.instance.surfaceChanged(width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        Log.d(TAG, "onSurfaceTextureUpdated")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed")
        RenderHelper.instance.surfaceDestroyed()
        RenderHelper.instance.release()
        return false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable, width = $width , height = $height")
        RenderHelper.instance.surfaceCreated(surface)
        RenderHelper.instance.surfaceChanged(width, height)
        RenderHelper.instance.addFilter(GrayFilter(application))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionUtils.onRequestPermissionsResult(requestCode == REQ_PERMISSION_SET, grantResults,
            Runnable {
                RenderHelper.instance.openCamera()
            },
            Runnable {
                Toast.makeText(
                    application, R.string.request_permission_failed,
                    Toast.LENGTH_SHORT
                ).show()
            })
    }
}
