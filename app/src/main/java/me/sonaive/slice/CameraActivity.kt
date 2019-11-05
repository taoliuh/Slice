package me.sonaive.slice

import android.Manifest
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.sonaive.slice.recorder.HardwareEncoder
import me.sonaive.slice.recorder.MediaEncoder
import me.sonaive.slice.render.RenderHelper
import me.sonaive.slice.render.filters.GrayFilter
import me.sonaive.slice.utils.PermissionUtils

class CameraActivity : FragmentActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQ_PERMISSION_SET = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surface_view.holder.addCallback(this)
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

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated")
        RenderHelper.instance.surfaceCreated(holder)
        RenderHelper.instance.addFilter(GrayFilter(application))
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged, width = $width , height = $height")
        RenderHelper.instance.surfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceDestroyed")
        RenderHelper.instance.surfaceDestroyed()
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

    override fun onDestroy() {
        RenderHelper.instance.release()
        super.onDestroy()
    }
}
