package me.sonaive.slice.camera

import android.graphics.Rect

/**
 * Created by liutao on 13/10/2019.
 */

class FocusParams(x: Float, y: Float, width: Int, height: Int) {
    companion object {
        private const val FOCUS_COEF = 1.0f
        private const val METER_COEF = 1.5f
        const val FOCUS_SIDE = 1000
    }

    private var mX = x
    private var mY = y
    private var mWidth = width
    private var mHeight = height

    fun getFocusRect(): Rect? {
        return calculateTapArea(mX, mY, FOCUS_COEF, mWidth, mHeight)
    }

    fun getMeteringRect(): Rect? {
        return calculateTapArea(mX, mY, METER_COEF, mWidth, mHeight)
    }

    private fun calculateTapArea(x: Float, y: Float, coefficient: Float, width: Int, height: Int): Rect {
        val focusAreaSize = 300
        val areaSize = (focusAreaSize * coefficient).toInt()
        val halfW = width / 2f
        val halfH = height / 2f
        val centerX = ((y - halfH) / halfH * FOCUS_SIDE).toInt()
        val centerY = ((x - halfW) / halfW * FOCUS_SIDE).toInt()
        val left = clamp(centerX - areaSize / 2, -FOCUS_SIDE, FOCUS_SIDE)
        val top = clamp(centerY - areaSize / 2, -FOCUS_SIDE, FOCUS_SIDE)
        val right = left + if (areaSize > FOCUS_SIDE) FOCUS_SIDE else left + areaSize
        val bottom = top + if (areaSize > FOCUS_SIDE) FOCUS_SIDE else top + areaSize
        return Rect(left, top, right, bottom)
    }

    private fun clamp(x: Int, min: Int, max: Int): Int {
        return when {
            x > max -> max
            x < min -> min
            else -> x
        }
    }
}