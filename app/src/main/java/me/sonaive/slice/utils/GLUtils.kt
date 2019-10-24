package me.sonaive.slice.utils

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log

/**
 * Created by liutao on 25/09/2019.
 */

object GLUtils {

    private const val TAG = "GLUtils"

    fun createProgram(vertexSrc: String?, fragmentSrc: String?): Int {
        val vertex = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fragment = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        var program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ")
            Log.e(TAG, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    fun loadShader(type: Int, src: String?): Int {
        var shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compileStatus = intArrayOf(0)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "load shader failed, type: $type, info: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    fun createFrameBuffer(): Int {
        val frameBuffers = intArrayOf(0)
        GLES20.glGenFramebuffers(1, frameBuffers, 0)
        return frameBuffers[0]
    }

    fun createYuvTexture(width: Int, height: Int): Int {
        val textures = intArrayOf(0)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0,
            GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null
        )
        setDefaultTexParams()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textures[0]
    }

    fun createTexture(width: Int, height: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        setDefaultTexParams()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textures[0]
    }

    fun genTexturesWithParameter(size: Int, textures: IntArray, start: Int, format: Int, width: Int, height: Int) {
        GLES20.glGenTextures(size, textures, start)
        for (i in 0 until size) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, format, width, height,
                0, format, GLES20.GL_UNSIGNED_BYTE, null
            )
            setDefaultTexParams()
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun bindFrameBuffer(frameBuffer: Int, textureId: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0
        )
    }

    fun unBindFrameBuffer() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun generateTexture(target: Int): Int {
        val textureArray = IntArray(1)
        GLES20.glGenTextures(1, textureArray, 0)
        val textureId = textureArray[0]
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        checkGLError("generateTexture")
        return textureId
    }

    fun createOESTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        // 缩小使用最近邻过滤
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat()
        )
        // 放大使用双线性过滤
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat()
        )
        // GL_CLAMP_TO_EDGE 表示OpenGL只画图片一次，剩下的部分将使用图片最后一行像素重复
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        return textures[0]
    }

    private fun checkGLError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op : glError 0x ${Integer.toHexString(error)}")
        }
    }

    private fun setDefaultTexParams() {
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
    }
}