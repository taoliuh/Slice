package me.sonaive.slice.recorder

import android.media.MediaCodec
import android.util.Log
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

/**
 * Created by liutao on 2019-10-21.
 */

abstract class MediaEncoder(muxer: MediaMuxerWrapper, listener: MediaEncoderListener): Runnable {
    companion object {
        const val TAG = "MediaEncoder"
        const val TIMEOUT_USEC = 10000L // 10ms
    }

    interface MediaEncoderListener {
        fun onPrepared(encoder: MediaEncoder)
        fun onStarted(encoder: MediaEncoder)
        fun onStopped(encoder: MediaEncoder)
        fun onReleased(encoder: MediaEncoder)
        fun onError(error: Int)
    }

    /**
     * Flag that indicate the frame data will be available soon.
     */
    private var mRequestDrain = 0

    /**
     * BufferInfo instance for dequeuing
     */
    private var mBufferInfo: MediaCodec.BufferInfo? = null

    private var mPauseBeginNans = 0L

    private var mTotalNans = 0L

    /**
     * Flag that indicate pause recording
     */
    private var isPause = false

    /**
     * Flag that indicate this encoder is capturing now.
     */
    @JvmField protected var isCapturing = false

    /**
     * Flag to request stop capturing
     */
    @JvmField protected var mRequestStop = false

    @JvmField protected val mSync = Object()

    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    @JvmField protected var isEOS = false

    /**
     * Flag the indicate the muxer is running
     */
    @JvmField protected var mMuxerStarted = false

    /**
     * Track Number
     */
    @JvmField protected var mTrackIndex = -1

    /**
     * MediaCodec instance for encoding
     */
    @JvmField protected var mMediaCodec: MediaCodec? = null // API >= 16(Android 4.1.2)

    /**
     * Weak refarence of MediaMuxerWarapper instance
     */
    @JvmField protected var mMuxerRef: WeakReference<MediaMuxerWrapper> = WeakReference(muxer)

    @JvmField protected var mListener: MediaEncoderListener? = null

    init {
        muxer.addEncoder(this)
        mListener = listener
        synchronized(mSync) {
            mBufferInfo = MediaCodec.BufferInfo()
            Thread(this, javaClass.simpleName).start()
            try {
                mSync.wait()
            } catch (e: InterruptedException) {
                // do nothing
            }
        }
    }

    abstract fun prepare()

    /**
     * the method to indicate frame data is soon available or already available
     *
     * @return return true if encoder is ready to encod.
     */
    fun frameAvailableSoon(): Boolean {
        synchronized(mSync) {
            if (!isCapturing or mRequestStop) return false
            mRequestDrain++
            mSync.notifyAll()
        }
        return true
    }

    open fun startRecording() {
        Log.d(TAG, "startRecording")
        synchronized(mSync) {
            isCapturing = true
            mRequestStop = false
            mSync.notifyAll()
        }
    }

    open fun pauseRecording(isPause: Boolean) {
        this.isPause = isPause
        if (isPause) {
            mPauseBeginNans = System.nanoTime()
        } else {
            mTotalNans += System.nanoTime() - mPauseBeginNans
        }
    }

    open fun stopRecording() {
        Log.d(TAG, "stopRecording")
        synchronized(mSync) {
            if (!isCapturing or mRequestStop) {
                return
            }
            mRequestStop = true // for rejecting newer frame
            mSync.notifyAll()
            // We can not know when the encoding and writing finish.
            // so we return immediately after request to avoid delay of caller thread
        }
    }

    /**
     * Release all related objects
     */
    open fun release() {
        Log.d(TAG, "release:")
        isCapturing = false
        try {
            mMediaCodec?.stop()
            mMediaCodec?.release()
            mMediaCodec = null
        } catch (e: Exception) {
            Log.e(TAG, "failed releasing MediaCodec", e)
        }
        if (mMuxerStarted) {
            val muxer = mMuxerRef.get()
            try {
                muxer?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "failed stopping muxer", e)
            }
        }
        try {
            Thread.sleep(100L)
            mListener?.onStopped(this)
        } catch (e: Exception) {
            Log.e(TAG, "failed onStopped", e)
        }
        mBufferInfo = null
        mListener?.onReleased(this)
    }

    override fun run() {
        synchronized(mSync) {
            mRequestStop = false
            mRequestDrain = 0
            mSync.notifyAll()
        }
        val isRunning = true
        var localRequestStop = false
        var localRequestDrain = false
        while (isRunning) {
            synchronized(mSync) {
                localRequestStop = mRequestStop
                localRequestDrain = (mRequestDrain > 0)
                if (localRequestDrain) {
                    mRequestDrain--
                }
            }
            if (localRequestStop) {
                drain()
                // request stop recording
                signalEndOfInputStream()
                // process output data again for EOS signal
                drain()
                // release all related objects
                release()
                break
            }
            if (localRequestDrain) {
                drain()
            } else {
                try {
                    synchronized(mSync) {
                        mSync.wait()
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Encoder thread interrupted")
                    break
                }
            }
        }
        Log.d(TAG, "Encoder thread exiting")
        synchronized(mSync) {
            mRequestStop = true
            isCapturing = false
        }

    }

    /**
     * drain encoded data and write them to muxer
     */
    protected fun drain() {
        if (mMediaCodec == null) return
        var encoderOutputBuffers = mMediaCodec!!.outputBuffers
        var encoderStatus = 0
        var count = 0
        val muxer = mMuxerRef.get()
        if (muxer == null) {
            Log.w(TAG, "muxer is unexpectedly null")
            return
        }
        if (mBufferInfo == null) {
            Log.w(TAG, "buffer info is unexpectedly null")
            return
        }
        while (isCapturing) {
            // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
            encoderStatus = mMediaCodec!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
                if (!isEOS) {
                    if (++count > 5) {
                        break
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED")
                // this should not come when encoding
                encoderOutputBuffers = mMediaCodec!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED")
                // this status indicate the output format of codec is changed
                // this should come only once before actual encoded data
                // but this status never come on Android4.3 or less
                // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice.")
                }
                // get output format from codec and pass them to muxer
                // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                val format = mMediaCodec!!.outputFormat // API >= 16
                Log.d(TAG, "MediaCodec format: $format")
                mTrackIndex = muxer.addTrack(format)
                mMuxerStarted = true
                if (!muxer.start()) {
                    // we should wait until muxer is ready
                    synchronized(muxer.lock) {
                        while (!muxer.isStarted()) {
                            muxer.lock.wait(100)
                        }
                    }
                }
            } else if (encoderStatus < 0) {
                Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: $encoderStatus")
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: throw RuntimeException("encoderOutputBuffer + $encoderStatus + was null")
                if (mBufferInfo!!.flags.and(MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // You shoud set output format to muxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED
                    // don't come yet)
                    // therefore we should expand and prepare output format from buffer data.
                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                    Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo!!.size = 0
                }

                if (mBufferInfo!!.size != 0) {
                    // encoded data is ready, clear waiting counter
                    count = 0
                    if (!mMuxerStarted) {
                        // muxer is not ready...this will programing failure.
                        throw RuntimeException("drain:muxer hasn't started")
                    }
                    // write encoded data to muxer, need to adjust presentationTimeUs.
                    mBufferInfo!!.presentationTimeUs = getPTSUs()
                    muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo!!)
                }

                // return buffer to encoder
                mMediaCodec?.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo!!.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // when EOS come.
                    isCapturing = false
                    break
                }
            }
        }
    }

    open fun signalEndOfInputStream() {
        // signalEndOfInputStream is only available for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
        // MediaCodec.signalEndOfInputStream();	// API >= 18
        Log.d(TAG, "sending EOS to encoder")
        encode(null, 0, getPTSUs())
    }

    protected fun encode(buffer: ByteBuffer?, length: Int, presentationTimeUs: Long) {
        if (!isCapturing) return
        if (mMediaCodec == null) return
        val inputBuffers = mMediaCodec!!.getInputBuffers()
        while (isCapturing) {
            val inputBufferIndex = mMediaCodec!!.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferIndex >= 0) {
                val inputBuffer = inputBuffers[inputBufferIndex]
                inputBuffer.clear()
                if (buffer != null) {
                    inputBuffer.put(buffer)
                }
                if (length <= 0) {
                    // send EOS
                    isEOS = true
                    Log.d(TAG, "send BUFFER_FLAG_END_OF_STREAM")
                    mMediaCodec!!.queueInputBuffer(
                        inputBufferIndex, 0, 0,
                        presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    break
                } else {
                    mMediaCodec!!.queueInputBuffer(
                        inputBufferIndex, 0, length,
                        presentationTimeUs, 0
                    )
                }
                break
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }

    /**
     * get next encoding presentationTimeUs
     */
    protected fun getPTSUs(): Long {
        val result = System.nanoTime()
        return (result - mTotalNans) / 1000L
    }
}