package me.sonaive.slice.render.filters

import android.app.Application
import android.opengl.GLES20
import me.sonaive.slice.utils.GLUtils
import me.sonaive.slice.utils.MatrixUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by liutao on 30/09/2019.
 */

abstract class GLFilter(application: Application) {
    companion object {
        const val TAG = "GLFilter"

        val IM = MatrixUtils.getIdentityMatrix()

        val VERTEX = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
        )

        val TEXTURE_COORD = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        fun readResShader(application: Application, path: String): String? {
            val sb = StringBuilder()
            try {
                val inputStream = application.assets.open(path)
                var len: Int
                val buffer = ByteArray(1024)
                while ((inputStream.read(buffer).also { len = it }) != -1) {
                    sb.append(String(buffer, 0, len))
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
            return sb.toString().replace("\\r\\n".toRegex(), "\n")
        }
    }

    protected var mProgram = -1
    protected var mPositionLoc = -1
    protected var mTextureCoordLoc = -1
    protected var mMvpMatrixLoc = -1
    protected var mTextureLoc = -1
    protected var mVertexBuf: FloatBuffer
    protected var mTextureCoordBuf: FloatBuffer
    protected val mApplication: Application = application

    private var mMvpMatrix = IM.copyOf(16)
    private var mTextureId = -1

    init {
        mVertexBuf = ByteBuffer.allocateDirect(VERTEX.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(VERTEX)
        mVertexBuf.position(0)
        mTextureCoordBuf = ByteBuffer.allocateDirect(TEXTURE_COORD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(TEXTURE_COORD)
        mTextureCoordBuf.position(0)
    }

    /*******************************************************************************************************************
     *
     *    abstract methods
     *
     ******************************************************************************************************************/

    protected abstract fun onCreate()

    protected abstract fun onSizeChanged(width: Int, height: Int)

    /*******************************************************************************************************************
     *
     *    override methods
     *
     ******************************************************************************************************************/
    open fun onClear() {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    }

    open fun onUserProgram() {
        GLES20.glUseProgram(mProgram)
    }

    open fun onSetExtraData() {
        GLES20.glUniformMatrix4fv(mMvpMatrixLoc, 1, false, mMvpMatrix, 0)
    }

    open fun onBindTexture() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTextureId())
        GLES20.glUniform1i(mTextureLoc, 0)
    }

    open fun onDraw() {
        GLES20.glEnableVertexAttribArray(mPositionLoc)
        GLES20.glVertexAttribPointer(mPositionLoc, 2, GLES20.GL_FLOAT, false, 0, mVertexBuf)
        GLES20.glEnableVertexAttribArray(mTextureCoordLoc)
        GLES20.glVertexAttribPointer(mTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0,
            mTextureCoordBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(mPositionLoc)
        GLES20.glDisableVertexAttribArray(mTextureCoordLoc)
    }

    open fun draw() {
        onClear()
        onUserProgram()
        onSetExtraData()
        onBindTexture()
        onDraw()
    }

    open fun getOutputTextureId(): Int {
        return -1
    }

    open fun release() {
        if (mProgram >= 0) {
            GLES20.glDeleteProgram(mProgram)
        }
        mProgram = -1
    }

    /*******************************************************************************************************************
     *
     *    protected methods
     *
     ******************************************************************************************************************/

    protected fun createProgramFromAssetFile(vertexPath: String, fragmentPath: String) {
        createProgram(readResShader(mApplication, vertexPath), readResShader(mApplication, fragmentPath))
    }

    protected fun createProgram(vertex: String?, fragment: String?) {
        mProgram = GLUtils.createProgram(vertex, fragment)
        mPositionLoc = GLES20.glGetAttribLocation(mProgram, "aPosition")
        mTextureCoordLoc = GLES20.glGetAttribLocation(mProgram, "aTextureCoord")
        mMvpMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uMvpMatrix")
        mTextureLoc = GLES20.glGetUniformLocation(mProgram, "uTexture")
    }

    /*******************************************************************************************************************
     *
     *    public methods
     *
     ******************************************************************************************************************/

    fun create() {
        onCreate()
    }

    fun setSize(width: Int, height: Int) {
        onSizeChanged(width, height)
    }

    fun setTextureId(textureId: Int) {
        mTextureId = textureId
    }

    fun getTextureId(): Int {
        return mTextureId
    }
}