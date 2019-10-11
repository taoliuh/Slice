package me.sonaive.slice.gles.filters

import android.app.Application
import android.opengl.GLES11Ext
import android.opengl.GLES20

/**
 * Created by liutao on 09/10/2019.
 */

class OESFilter(application: Application): GLFilter(application) {

    var mStMatrixLoc = -1
    var mStMatrix = IM.copyOf(16)

    override fun onCreate() {
        createProgramFromAssetFile("camera/camera_vertex.glsl", "camera/camera_fragment.glsl")
        mStMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uStMatrix")
    }

    override fun onSizeChanged(width: Int, height: Int) {

    }

    override fun onSetExtraData() {
        super.onSetExtraData()
        GLES20.glUniformMatrix4fv(mStMatrixLoc, 1, false, mStMatrix, 0)
    }

    override fun onBindTexture() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getTextureId())
        GLES20.glUniform1i(mTextureLoc, 0)
    }

}