package me.sonaive.slice.gles.filters

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import me.sonaive.slice.utils.GLUtils

/**
 * Created by liutao on 09/10/2019.
 */
class CameraFilter(application: Application): GLFilter(application) {

    private var mOESFilter: OESFilter = OESFilter(application)
    private var mStMatrix = FloatArray(16)
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mOESTextureId = -1
    private var mFrameBufId = -1
    private var mOutputTextureId = -1

    @SuppressLint("Recycle")
    override fun onCreate() {
        mOESFilter.create()
        mOESTextureId = GLUtils.createFrameBuffer()
        mSurfaceTexture = SurfaceTexture(mOESTextureId)
    }

    override fun onSizeChanged(width: Int, height: Int) {
        mOESFilter.setSize(width, height)
        mFrameBufId = GLUtils.createFrameBuffer()
        mOutputTextureId = GLUtils.createTexture(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun draw() {
        mSurfaceTexture?.updateTexImage()
        mSurfaceTexture?.getTransformMatrix(mStMatrix)
        mOESFilter.mStMatrix = mStMatrix
        mOESFilter.setTextureId(mOESTextureId)
        GLUtils.bindFrameBuffer(mFrameBufId, mOutputTextureId)
        mOESFilter.draw()
        GLUtils.unBindFrameBuffer()
    }

    override fun getOutputTextureId(): Int {
        return mOutputTextureId
    }

    fun release() {
        GLES20.glDeleteFramebuffers(1, intArrayOf(mFrameBufId), 0)
        GLES20.glDeleteTextures(2, intArrayOf(mOutputTextureId, mOESTextureId), 0)
        mSurfaceTexture?.release()
    }

}