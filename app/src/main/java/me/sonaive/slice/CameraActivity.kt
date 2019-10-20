package me.sonaive.slice

import android.Manifest
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.view.SurfaceHolder
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.sonaive.slice.render.RenderHandler
import me.sonaive.slice.render.RenderThread
import me.sonaive.slice.render.filters.GrayFilter
import me.sonaive.slice.utils.PermissionUtils

class CameraActivity : FragmentActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQ_PERMISSION_SET = 1
    }

    private var mRenderHandler: RenderHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surface_view.holder.addCallback(this)
        val render = RenderThread(this, "RenderThread")
        render.start()
        mRenderHandler = RenderHandler(render)
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

    override fun surfaceCreated(holder: SurfaceHolder?) {
        mRenderHandler?.sendMessage(
            mRenderHandler?.obtainMessage(RenderHandler.MSG_SURFACE_CREATED, holder)
        )
        mRenderHandler!!.sendMessage(
            mRenderHandler!!.obtainMessage(RenderHandler.MSG_ADD_FILTER, GrayFilter(application))
        )
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        mRenderHandler?.sendMessage(
            mRenderHandler?.obtainMessage(RenderHandler.MSG_SURFACE_CHANGED, width, height)
        )
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        mRenderHandler?.sendMessage(
            mRenderHandler?.obtainMessage(RenderHandler.MSG_SURFACE_DESTROYED)
        )
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
}
