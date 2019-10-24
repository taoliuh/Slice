package me.sonaive.slice.render.filters

import android.app.Application
import android.opengl.GLES20
import me.sonaive.slice.utils.GLUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayList

/**
 * Created by liutao on 09/10/2019.
 */

class GroupFilter(application: Application): GLFilter(application) {

    companion object {
        private const val TAG = "GroupFilter"
    }

    private var mFilterQueue: Queue<GLFilter> = ConcurrentLinkedQueue()
    private var mFilters: MutableList<GLFilter> = ArrayList()

    private var mWidth = 0
    private var mHeight = 0
    private var mSize = 0
    private var mFrameBufId = -1
    private var mTextures = IntArray(2)
    private var mTextureIndex = 0

    override fun onCreate() {
        // do nothing
    }

    override fun onSizeChanged(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        updateFilter()
        mFrameBufId = GLUtils.createFrameBuffer()
        createTextures()
    }

    override fun getOutputTextureId(): Int {
        return if (mSize == 0) getTextureId() else mTextures[(mTextureIndex - 1) % 2]
    }

    override fun draw() {
        updateFilter()
        mTextureIndex = 0
        if (mSize > 0) {
            for (filter in mFilters) {
                GLUtils.bindFrameBuffer(mFrameBufId, mTextures[mTextureIndex % 2])
                GLES20.glViewport(0, 0, mWidth, mHeight)
                if (mTextureIndex == 0) {
                    filter.setTextureId(getTextureId())
                } else {
                    filter.setTextureId(mTextures[(mTextureIndex - 1) % 2])
                }
                filter.draw()
                GLUtils.unBindFrameBuffer()
                ++mTextureIndex
            }
        }
    }

    fun addFilter(filter: GLFilter) {
        mFilterQueue.add(filter)
    }

    fun clearAll() {
        mFilterQueue.clear()
        mFilters.clear()
        mSize = 0
    }

    override fun release() {
        GLES20.glDeleteFramebuffers(1, intArrayOf(mFrameBufId), 0)
        GLES20.glDeleteTextures(mTextures.size, mTextures, 0)
        super.release()
    }

    private fun updateFilter() {
        var filter: GLFilter?
        while ((mFilterQueue.poll().also { filter = it }) != null) {
            filter!!.create()
            filter!!.setSize(mWidth, mHeight)
            mFilters.add(filter!!)
            ++mSize
        }
    }

    private fun createTextures() {
        for (index in 0 until mTextures.size) {
            mTextures[index] = GLUtils.createTexture(mWidth, mHeight)
        }
    }
}