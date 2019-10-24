package me.sonaive.slice.recorder

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import java.lang.Exception
import kotlin.collections.ArrayList

/**
 * Created by liutao on 2019-10-21.
 */

class MediaVideoEncoder(muxer: MediaMuxerWrapper, listener: MediaEncoderListener, width: Int, height: Int):
    MediaEncoder(muxer, listener) {

    companion object {
        const val TAG = "MediaVideoEncoder"
        private const val MIME_TYPE = "video/avc"
        /**
         * 帧率
         */
        private const val FRAME_RATE = 24
        /**
         * 码率计算因子
         */
        private const val BPP = 0.25f
        /**
         * 高清录制码率倍数
         */
        private const val HD_VALUE = 2
        /**
         * I帧间隔 单位为秒
         */
        private const val I_FRAME_INTERVAL = 1

        private val recognizedFormats = intArrayOf(
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
//        	MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

        /**
         * select the first codec that match a specific MIME type
         * @param mimeType
         * @return null if no codec matched
         */
        fun selectVideoCodec(mimeType: String): MediaCodecInfo? {
            Log.d(TAG, "selectVideoCodec")
            // get the list of available codecs
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    continue
                }
                // select first codec that match a specific MIME type and color format
                val types = codecInfo.supportedTypes
                for (type in types) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        Log.d(TAG, "codec = ${codecInfo.name}, mime = $type")
                        val format = selectColorFormat(codecInfo, mimeType)
                        if (format > 0) {
                            return codecInfo
                        }
                    }
                }
            }
            return null
        }

        /**
         * select color format available on specific codec and we can use.
         * @return 0 if no colorFormat is matched
         */
        private fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
            Log.d(TAG, "selectColorFormat")
            var result = 0
            val caps: MediaCodecInfo.CodecCapabilities
            try {
                Thread.currentThread().priority = Thread.MAX_PRIORITY
                caps = codecInfo.getCapabilitiesForType(mimeType)
            } finally {
                Thread.currentThread().priority = Thread.NORM_PRIORITY
            }
            for (colorFormat in caps.colorFormats) {
                if (isRecognizedVideoFormat(colorFormat)) {
                    if (result == 0) {
                        result = colorFormat
                    }
                    break
                }
            }
            if (result == 0) {
                Log.e(TAG, "couldn't find a good color format for ${codecInfo.name}/$mimeType")
            }
            return result
        }

        private fun isRecognizedVideoFormat(colorFormat: Int): Boolean {
            Log.d(TAG, "isRecognizedVideoFormat:colorFormat = $colorFormat")
            for (color in recognizedFormats) {
                if (colorFormat == color) {
                    return true
                }
            }
            return false
        }
    }

    private val mWidth = width
    private val mHeight = height
    private var mSurface: Surface? = null
    private var mBitRate = 0
    private var isEnableHD = false

    override fun prepare() {
        Log.d(TAG, "prepare")
        mTrackIndex = -1
        mMuxerStarted = false
        isEOS = false
        try {
            val videoCodecInfo = selectVideoCodec(MIME_TYPE)
            if (videoCodecInfo == null) {
                Log.e(TAG, "Unable to find an appropriate codec for $MIME_TYPE")
                mListener?.onError(-1)
                return
            }
            Log.d(TAG, "selected codec: ${videoCodecInfo.name}")
            val format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight)
            // API >= 18
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            // 测出vivo X9 可以设置profile为high但是输出视频帧紊乱
            if (Build.MODEL != "vivo X9") {
                setupHDParams(format)
            }
            if (mBitRate > 0) {
                format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate)
            } else {
                format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate())
            }
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            Log.d(TAG, "format: $format")
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mMediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // get Surface for encoder input
            // this method only can call between #configure and #start
            mSurface = mMediaCodec!!.createInputSurface()
            mMediaCodec!!.start()
            Log.d(TAG, "prepare finishing")
            mListener?.onPrepared(this)
        } catch (e: Exception) {
            Log.e(TAG, "prepare failed, error ${e.message}")
            e.printStackTrace()
        }
    }

    override fun startRecording() {
        super.startRecording()
        mListener?.onStarted(this)
    }

    override fun signalEndOfInputStream() {
        Log.d(TAG, "sending EOS to encoder")
        mMediaCodec!!.signalEndOfInputStream()    // API >= 18
        isEOS = true
    }

    override fun release() {
        mSurface?.release()
        mSurface = null
        super.release()
    }

    fun getInputSurface(): Surface? {
        return mSurface
    }

    /**
     * 是否允许录制高清视频
     * @param enable
     */
    fun enableHighDefinition(enable: Boolean) {
        isEnableHD = enable
    }

    private fun setupHDParams(format: MediaFormat) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {
                continue
            }
            for (mimeType: String in codecInfo.supportedTypes) {
                if (mimeType.equals(MIME_TYPE, ignoreCase = true)) {
                    if (setProfileLevel(format, codecInfo)) {
                        return
                    }
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun setProfileLevel(format: MediaFormat, codecInfo: MediaCodecInfo): Boolean {
        var result = false
        val caps = codecInfo.getCapabilitiesForType(MIME_TYPE)
        val profileLevels = caps.profileLevels
        var isSupportedHighProfile = false
        var isSupportedMainProfile = false
        var profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        var level = 1
        val highLevels = ArrayList<Int>()
        val mainLevels = ArrayList<Int>()
        for (i in profileLevels.indices) {
            val c = MediaCodecInfo.CodecCapabilities.createFromProfileLevel(
                MIME_TYPE, profileLevels[i].profile, profileLevels[i].level
            ) ?: continue
            for (j in c.profileLevels.indices) {
                if (c.profileLevels[j].profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh) {
                    isSupportedHighProfile = true
                    highLevels.add(c.profileLevels[j].level)
                } else if (c.profileLevels[j].profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain) {
                    isSupportedMainProfile = true
                    mainLevels.add(c.profileLevels[j].level)
                }
                Log.i(TAG, "profile: " + c.profileLevels[j].profile + ", level: " + c.profileLevels[j].level)
            }
        }
        if (isSupportedHighProfile) {
            profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            highLevels.sort()
            level = highLevels.last()
            result = true
        } else if (isSupportedMainProfile) {
            profile = MediaCodecInfo.CodecProfileLevel.AVCProfileMain
            mainLevels.sort()
            level = mainLevels.last()
            result = true
        }
        format.setInteger(MediaFormat.KEY_PROFILE, profile)
        format.setInteger(MediaFormat.KEY_LEVEL, level)
        return result
    }

    private fun calcBitRate(): Int {
        var bitRate = (BPP * FRAME_RATE * mWidth * mHeight).toInt()
        if (isEnableHD) {
            bitRate *= HD_VALUE
        }
        Log.d(TAG, "bit rate = ${String.format("%5.2f", bitRate / 1024f / 1024f)}Mbps")
        return bitRate
    }

}