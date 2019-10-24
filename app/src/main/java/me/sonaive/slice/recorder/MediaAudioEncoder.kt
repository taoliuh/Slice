package me.sonaive.slice.recorder

import android.media.*
import android.util.Log
import java.lang.Exception
import java.nio.ByteBuffer

/**
 * Created by liutao on 2019-10-21.
 */

class MediaAudioEncoder(muxer: MediaMuxerWrapper, listener: MediaEncoderListener): MediaEncoder(muxer, listener) {

    companion object {
        private const val TAG = "MediaAudioEncoder"
        // AAC 适合作为视频中音频轨道
        private const val MIME_TYPE = "audio/mp4a-latm"
        // 适合人耳接收范围20Hz~20kHz 这里double使用主流的44.1kHz 保证在所有设备都能安全运行
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 96000
        // AAC, bytes/frame/channel
        private const val SAMPLES_PER_FRAME = 1024
        // AAC, frame/buffer/sec
        private const val FRAMES_PER_BUFFER = 25

        val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )

        private fun selectAudioCodec(mimeType: String): MediaCodecInfo? {
            Log.d(TAG, "selectAudioCodec")

            var result: MediaCodecInfo? = null
            // get the list of available codecs
            val numCodecs = MediaCodecList.getCodecCount()
            LOOP@ for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {    // skipp decoder
                    continue
                }
                val types = codecInfo.supportedTypes
                for (type in types) {
                    Log.d(TAG, "supportedType = ${codecInfo.name} , MIME = $type")
                    if (type.equals(mimeType, ignoreCase = true)) {
                        if (result == null) {
                            result = codecInfo
                            break@LOOP
                        }
                    }
                }
            }
            return result
        }
    }

    private var mAudioThread: AudioThread? = null

    override fun prepare() {
        Log.d(TAG, "prepare")
        mTrackIndex = -1
        mMuxerStarted = false
        isEOS = false
        // prepare MediaCodec for AAC encoding of audio data from internal mic.
        val audioCodecInfo = selectAudioCodec(MIME_TYPE)
        if (audioCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for $MIME_TYPE")
            mListener?.onError(-1)
            return
        }
        Log.d(TAG, "selected codec: ${audioCodecInfo.name}")
        val audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 2)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
        Log.d(TAG, "format: $audioFormat")
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mMediaCodec!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mMediaCodec!!.start()
        Log.d(TAG, "prepare finishing")
        mListener?.onPrepared(this)
    }

    override fun startRecording() {
        super.startRecording()
        // create and execute audio capturing thread using internal mic
        if (mAudioThread == null) {
            mAudioThread = AudioThread()
            mAudioThread!!.start()
        }
    }

    override fun release() {
        mAudioThread = null
        super.release()
    }

    inner class AudioThread: Thread() {

        override fun run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
                )
                var bufferSize = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER
                if (bufferSize < minBufferSize) {
                    bufferSize = (minBufferSize / SAMPLES_PER_FRAME + 1) * SAMPLES_PER_FRAME * 2
                }
                var audioRecord: AudioRecord? = null
                for (source in AUDIO_SOURCES) {
                    try {
                        audioRecord = AudioRecord(
                            source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO,
                            AudioFormat.ENCODING_PCM_16BIT, bufferSize
                        )
                        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                            audioRecord = null
                        }
                    } catch (e: Exception) {
                        audioRecord = null
                    }
                    if (audioRecord != null) break
                }
                if (audioRecord != null) {
                    try {
                        if (isCapturing) {
                            Log.d(TAG, "AudioThread:start audio recording")
                            val buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME)
                            audioRecord.startRecording()
                            try {
                                while (isCapturing && !mRequestStop && !isEOS) {
                                    // read audio data from internal mic
                                    buf.clear()
                                    val readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME)
                                    if (readBytes > 0) {
                                        // set audio data to encoder
                                        buf.position(readBytes)
                                        buf.flip()
                                        encode(buf, readBytes, getPTSUs())
                                        frameAvailableSoon()
                                    }
                                }
                                frameAvailableSoon()
                            } finally {
                                audioRecord.stop()
                            }
                        }
                    } finally {
                        audioRecord.release()
                    }
                } else {
                    Log.e(TAG, "failed to initialize AudioRecord")
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioThread#run, occurred exception: $e")
            }
            Log.d(TAG, "AudioThread:finished")
        }
    }
}