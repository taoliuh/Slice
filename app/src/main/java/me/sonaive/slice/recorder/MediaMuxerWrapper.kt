package me.sonaive.slice.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

/**
 * Created by liutao on 2019-10-21.
 */

class MediaMuxerWrapper(path: String) {

    companion object {
        const val TAG = "MediaMuxerWrapper"
    }

    private var mMediaMuxer: MediaMuxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) // API >= 18
    private var mEncoderCount = 0
    private var mStartedCount = 0
    private var isStarted = false
    // 视频编码器
    private var mVideoEncoder: MediaEncoder? = null
    // 音频编码器
    private var mAudioEncoder: MediaEncoder? = null

    val lock = Object()

    init {
        mMediaMuxer.setOrientationHint(0)
    }

    /*******************************************************************************************************************
     *
     *    public methods
     *
     ******************************************************************************************************************/

    /**
     * 编码器录制开始前的准备
     */
    fun prepare() {
        mVideoEncoder?.prepare()
        mAudioEncoder?.prepare()
    }

    fun addEncoder(encoder: MediaEncoder) {
        when (encoder) {
            is MediaVideoEncoder -> {
                if (mVideoEncoder != null) {
                    throw IllegalArgumentException("video encoder already added.")
                }
                mVideoEncoder = encoder
            }
            is MediaAudioEncoder -> {
                if (mAudioEncoder != null) {
                    throw IllegalArgumentException("audio encoder already added.")
                }
                mAudioEncoder = encoder
            }
            else -> {
                throw IllegalArgumentException("unsupported encoder.")
            }
        }
        mEncoderCount = (if (mVideoEncoder != null) 1 else 0) + (if (mAudioEncoder != null) 1 else 0)
    }

    /**
     * 开始录制
     */
    fun startRecording() {
        mVideoEncoder?.startRecording()
        mAudioEncoder?.startRecording()
    }

    /**
     * 暂停录制
     */
    fun pauseRecording() {
        mVideoEncoder?.pauseRecording(true)
        mAudioEncoder?.pauseRecording(true)
    }

    /**
     * 继续录制
     */
    fun resumeRecording() {
        mVideoEncoder?.pauseRecording(false)
        mAudioEncoder?.pauseRecording(false)
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        mVideoEncoder?.stopRecording()
        mAudioEncoder?.stopRecording()
    }

    fun isStarted(): Boolean {
        synchronized(lock) {
            return isStarted
        }
    }

    /**
     * 获取视频编码器
     */
    fun getVideoEncoder(): MediaEncoder? {
        return mVideoEncoder
    }

    /**
     * 获取音频编码器
     */
    fun getAudioEncoder(): MediaEncoder? {
        return mAudioEncoder
    }

    /**
     * request start recording from encoder
     *
     * @return true when muxer is ready to write
     */
    fun start(): Boolean {
        Log.d(TAG, "start")
        synchronized(lock) {
            mStartedCount++
            if ((mEncoderCount > 0) and (mStartedCount == mEncoderCount)) {
                mMediaMuxer.start()
                isStarted = true
                lock.notifyAll()
                Log.d(TAG, "MediaMuxer started")
            }
            return isStarted
        }
    }

    fun stop() {
        Log.d(TAG, "stop, mStartedCount = $mStartedCount")
        synchronized(lock) {
            mStartedCount--
            if ((mEncoderCount > 0) and (mStartedCount <= 0)) {
                mMediaMuxer.stop()
                mMediaMuxer.release()
                isStarted = false
                Log.d(TAG, "MediaMuxer stopped")
            }
        }
    }

    fun addTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (isStarted) {
                throw IllegalArgumentException("muxer already started")
            }
            val trackIndex = mMediaMuxer.addTrack(format)
            Log.d(TAG, "addTrack, trackNum = $mEncoderCount, trackIndex = $trackIndex, format = $format")
            return trackIndex
        }
    }

    fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (mStartedCount > 0) {
                mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo)
            }
        }
    }
}