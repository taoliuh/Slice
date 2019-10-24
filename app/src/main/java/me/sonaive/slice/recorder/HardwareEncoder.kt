package me.sonaive.slice.recorder

import android.app.Application
import android.opengl.EGLContext
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import me.sonaive.slice.render.EGLBase
import me.sonaive.slice.render.filters.NoFilter
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Created by liutao on 2019-10-22.
 */

class HardwareEncoder private constructor() {
    companion object {
        private const val TAG = "HardwareEncoder"

        /**
         * 初始化录制器
         */
        const val MSG_INIT_RECORDER = 0
        /**
         * 帧可用
         */
        const val MSG_FRAME_AVAILABLE = 1
        /**
         * 渲染帧
         */
        const val MSG_DRAW_FRAME = 2
        /**
         * 开始录制
         */
        const val MSG_START_RECORDING = 3
        /**
         * 停止录制
         */
        const val MSG_STOP_RECORDING = 4
        /**
         * 暂停录制
         */
        const val MSG_PAUSE_RECORDING = 5
        /**
         * 继续录制
         */
        const val MSG_RESUME_RECORDING = 6
        /**
         * 是否录制音频
         */
        const val MSG_ENABLE_AUDIO = 7
        /**
         * 是否高清录制
         */
        const val MSG_ENABLE_HD = 8
        /**
         * 退出
         */
        const val MSG_QUIT = 9

        val mReadyFence = Object()

        val instance: HardwareEncoder by lazy {
            HardwareEncoder()
        }
    }

    private var mRecordThread: RecordThread? = null
    private lateinit var mOutputPath: String
    private lateinit var mApplication: Application

    fun prepareRecorder(): HardwareEncoder {
        synchronized(mReadyFence) {
            if (mRecordThread == null) {
                mRecordThread = RecordThread(this)
                mRecordThread!!.start()
                mRecordThread!!.waitUntilReady()
            }
        }
        return this
    }

    fun enableHDMode(enable: Boolean): HardwareEncoder {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_ENABLE_HD, enable))
        return this
    }

    fun enableAudioRecord(enable: Boolean): HardwareEncoder {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_ENABLE_AUDIO, enable))
        return this
    }

    fun setOutputPath(path: String): HardwareEncoder {
        mOutputPath = path
        return this
    }

    fun getOutputPath(): String {
        return mOutputPath
    }

    fun initApplication(application: Application): HardwareEncoder {
        mApplication = application
        return this
    }

    fun destroyRecorder() {
        synchronized(mReadyFence) {
            if (mRecordThread != null) {
                val handler = mRecordThread!!.getHandler()
                handler?.sendMessage(handler.obtainMessage(MSG_QUIT))
                mRecordThread = null
            }
        }
    }

    fun initRecorder(width: Int, height: Int, listener: MediaEncoder.MediaEncoderListener?) {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_INIT_RECORDER, width, height, listener))
    }

    fun startRecording(sharedContext: EGLContext?) {
        if (sharedContext == null) return
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_START_RECORDING, sharedContext))
    }

    fun frameAvailable() {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_FRAME_AVAILABLE))
    }

    fun drawRecordFrame(texture: Int) {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_DRAW_FRAME, texture, 0))
    }

    fun stopRecording() {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING))
    }

    fun pauseRecording() {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_PAUSE_RECORDING))
    }

    fun resumeRecording() {
        val handler = mRecordThread?.getHandler()
        handler?.sendMessage(handler.obtainMessage(MSG_RESUME_RECORDING))
    }

    private class RecordThread(manager: HardwareEncoder): Thread() {
        companion object {
            val mReadyLock = Object()
        }

        private var mReady: Boolean = false
        private var mVideoWidth = 0
        private var mVideoHeight = 0
        private var mEgl: EGLBase? = null
        private var mEglSurface: EGLBase.EglSurface? = null
        private var mMuxerWrapper: MediaMuxerWrapper? = null
        private var enableAudio = true
        private var isRecording = false
        private var enableHD = false
        private var mProcessTime = 0L
        private var mHandler: RecordHandler? = null
        private var mWeakRecorder: WeakReference<HardwareEncoder> = WeakReference(manager)
        private var mDrawer: NoFilter? = null

        override fun run() {
            Looper.prepare()
            synchronized(mReadyLock) {
                mHandler = RecordHandler(this)
                mReady = true
                mReadyLock.notifyAll()
            }
            Looper.loop()
            Log.d(TAG, "Record thread exiting")
            synchronized(mReadyLock) {
                release()
                mReady = false
                mHandler = null
            }
        }

        fun waitUntilReady() {
            synchronized(mReadyLock) {
                while (!mReady) {
                    try {
                        mReadyLock.wait()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }

        fun initRecorder(width: Int, height: Int, listener: MediaEncoder.MediaEncoderListener) {
            Log.d(TAG, "init recorder.")
            synchronized(mReadyLock) {
                val time = System.currentTimeMillis()
                mVideoWidth = width
                mVideoHeight = height
                if (mWeakRecorder.get() == null) {
                    return
                }
                val filePath = mWeakRecorder.get()!!.getOutputPath()
                if (TextUtils.isEmpty(filePath)) {
                    throw IllegalArgumentException("file path must no be empty!")
                }
                val file = File(filePath!!)
                if (!file.parentFile.exists()) {
                    file.parentFile.mkdirs()
                }
                try {
                    mMuxerWrapper = MediaMuxerWrapper(file.getAbsolutePath())
                    val videoEncoder = MediaVideoEncoder(mMuxerWrapper!!, listener, mVideoWidth, mVideoHeight)
                    videoEncoder.enableHighDefinition(enableHD)
                    if (enableAudio) {
                        MediaAudioEncoder(mMuxerWrapper!!, listener)
                    }
                    mMuxerWrapper!!.prepare()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                mProcessTime += System.currentTimeMillis() - time
            }
        }


        fun startRecording(sharedContext: EGLContext) {
            Log.d(TAG, "start recording.")
            synchronized(mReadyLock) {
                if (mMuxerWrapper == null) return
                if (mMuxerWrapper!!.getVideoEncoder() == null) return
                mEgl = EGLBase(sharedContext, false, true)
                mEglSurface = mEgl!!.createFromSurface(
                    (mMuxerWrapper!!.getVideoEncoder() as MediaVideoEncoder).getInputSurface()
                )
                mEglSurface!!.makeCurrent()
                initRecordingFilter()
                mMuxerWrapper!!.startRecording()
                isRecording = true
            }
        }

        fun frameAvailable() {
            Log.d(TAG, "frame available")
            synchronized(mReadyLock) {
                if (mMuxerWrapper?.getVideoEncoder() != null && isRecording) {
                    mMuxerWrapper?.getVideoEncoder()?.frameAvailableSoon()
                }
            }
        }

        fun drawRecordingFrame(currentTexture: Int) {
            Log.d(TAG, "draw recording frame")
            synchronized(mReadyLock) {
                mEglSurface?.makeCurrent()
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                mDrawer?.setTextureId(currentTexture)
                mDrawer?.draw()
                mEglSurface?.swap()
            }
        }

        fun stopRecording() {
            Log.d(TAG, "stop recording.")
            synchronized(mReadyLock) {
                val time = System.currentTimeMillis()
                isRecording = false
                mMuxerWrapper?.stopRecording()
                mMuxerWrapper = null
                mEglSurface?.release()
                mEglSurface = null

                mProcessTime += System.currentTimeMillis() - time
                Log.d(TAG, "sum of init and release time: $mProcessTime ms")
                mProcessTime = 0
            }
        }

        fun enableAudioRecording(enable: Boolean) {
            Log.d(TAG, "enable audio recording ? $enable")
            synchronized(mReadyLock) {
                enableAudio = enable
            }
        }

        fun enableHighDefinition(enable: Boolean) {
            Log.d(TAG, "enable high definition ? $enable")
            synchronized(mReadyLock) {
                enableHD = enable
            }
        }

        fun pauseRecording() {
            Log.d(TAG, "pause recording.")
            synchronized(mReadyLock) {
                if (isRecording) {
                    mMuxerWrapper?.pauseRecording()
                }
            }
        }

        fun resumeRecording() {
            Log.d(TAG, "resume recording.")
            synchronized(mReadyLock) {
                if (isRecording) {
                    mMuxerWrapper?.resumeRecording()
                }
            }
        }

        private fun initRecordingFilter() {
            if (mWeakRecorder.get() == null) {
                return
            }
            mDrawer = NoFilter(mWeakRecorder.get()!!.mApplication)
            mDrawer!!.create()
        }


        fun getHandler(): RecordHandler? {
            return mHandler
        }

        fun release() {
            stopRecording()
            mDrawer?.release()
            mDrawer = null
            mEgl?.release()
            mEgl = null
        }
    }

    private class RecordHandler(thread: RecordThread): Handler(Looper.myLooper()) {
        private var mWeakRecordThread = WeakReference<RecordThread>(thread)

        override fun handleMessage(msg: Message?) {
            if (msg == null) return
            val thread = mWeakRecordThread.get()
            if (thread == null) {
                Log.w(TAG, "RecordHandler encoder is null!")
                return
            }
            when (msg.what) {
                MSG_INIT_RECORDER -> thread.initRecorder(
                    msg.arg1, msg.arg2,
                    msg.obj as MediaEncoder.MediaEncoderListener
                )
                MSG_FRAME_AVAILABLE -> thread.frameAvailable()
                MSG_DRAW_FRAME -> thread.drawRecordingFrame(msg.arg1)
                MSG_START_RECORDING -> thread.startRecording(msg.obj as EGLContext)
                MSG_STOP_RECORDING -> thread.stopRecording()
                MSG_PAUSE_RECORDING -> thread.pauseRecording()
                MSG_RESUME_RECORDING -> thread.resumeRecording()
                MSG_ENABLE_AUDIO -> thread.enableAudioRecording(msg.obj as Boolean)
                MSG_ENABLE_HD -> thread.enableHighDefinition(msg.obj as Boolean)
                MSG_QUIT -> {
                    removeCallbacksAndMessages(null)
                    Looper.myLooper()!!.quit()
                }
                else -> throw RuntimeException("Unhandled msg what = ${msg.what}")
            }
        }
    }
}