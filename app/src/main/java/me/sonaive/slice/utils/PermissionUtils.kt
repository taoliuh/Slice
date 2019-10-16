package me.sonaive.slice.utils

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment

/**
 * Created by liutao on 12/10/2019.
 */
object PermissionUtils {

    fun askPermission(
        fragment: Fragment, permissions: Array<String>, req: Int,
        runnable: Runnable
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val result = ActivityCompat.checkSelfPermission(fragment.context!!, permissions[0])
            if (result == PackageManager.PERMISSION_GRANTED) {
                runnable.run()
            } else {
                fragment.requestPermissions(permissions, req)
            }
        } else {
            runnable.run()
        }
    }

    fun askPermission(context: Activity, permissions: Array<String>, req: Int, runnable: Runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val result = ActivityCompat.checkSelfPermission(context, permissions[0])
            if (result == PackageManager.PERMISSION_GRANTED) {
                runnable.run()
            } else {
                ActivityCompat.requestPermissions(context, permissions, req)
            }
        } else {
            runnable.run()
        }
    }

    fun onRequestPermissionsResult(isReq: Boolean, grantResults: IntArray, okRun: Runnable, denyRun: Runnable) {
        if (!isReq) {
            return
        }
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            okRun.run()
        } else {
            denyRun.run()
        }
    }
}