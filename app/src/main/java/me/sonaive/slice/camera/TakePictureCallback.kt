package me.sonaive.slice.camera

/**
 * Created by liutao on 13/10/2019.
 */

interface TakePictureCallback {
    fun onTakePicture(data: ByteArray)
}