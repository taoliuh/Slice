package me.sonaive.slice.utils

/**
 * Created by liutao on 30/09/2019.
 */
object MatrixUtils {
    fun getIdentityMatrix(): FloatArray {
        return floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )
    }
}