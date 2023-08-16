package io.agora.beautyapi.demo

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.faceunity.core.callback.OperateCallback
import com.faceunity.core.entity.FUBundleData
import com.faceunity.core.enumeration.FUAITypeEnum
import com.faceunity.core.faceunity.FUAIKit
import com.faceunity.core.faceunity.FURenderConfig.OPERATE_SUCCESS_AUTH
import com.faceunity.core.faceunity.FURenderKit
import com.faceunity.core.faceunity.FURenderManager
import com.faceunity.core.model.makeup.SimpleMakeup
import com.faceunity.core.model.prop.Prop
import com.faceunity.core.model.prop.sticker.Sticker
import com.faceunity.core.utils.FULogger
import com.faceunity.wrapper.faceunity
import io.agora.base.VideoFrame
import io.agora.beautyapi.demo.databinding.BeautyActivityBinding
import io.agora.beautyapi.demo.utils.ReflectUtils
import io.agora.beautyapi.faceunity.BeautyPreset
import io.agora.beautyapi.faceunity.BeautyStats
import io.agora.beautyapi.faceunity.CameraConfig
import io.agora.beautyapi.faceunity.CaptureMode
import io.agora.beautyapi.faceunity.Config
import io.agora.beautyapi.faceunity.ErrorCode
import io.agora.beautyapi.faceunity.IEventCallback
import io.agora.beautyapi.faceunity.MirrorMode
import io.agora.beautyapi.faceunity.createFaceUnityBeautyAPI
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.ColorEnhanceOptions
import io.agora.rtc2.video.IVideoFrameObserver
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import io.agora.rtc2.video.VideoEncoderConfiguration.FRAME_RATE
import java.io.File
import java.util.concurrent.Executors

class FaceUnityActivity : ComponentActivity() {
    private val TAG = this.javaClass.simpleName

    companion object {
        private const val EXTRA_CHANNEL_NAME = "ChannelName"
        private const val EXTRA_RESOLUTION = "Resolution"
        private const val EXTRA_FRAME_RATE = "FrameRate"
        private const val EXTRA_CAPTURE_MODE = "CaptureMode"
        private const val EXTRA_PROCESS_MODE = "ProcessMode"

        fun launch(
            context: Context,
            channelName: String,
            resolution: String,
            frameRate: String,
            captureMode: String,
            processMode: String
        ) {
            Intent(context, FaceUnityActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_RESOLUTION, resolution)
                putExtra(EXTRA_FRAME_RATE, frameRate)
                putExtra(EXTRA_CAPTURE_MODE, captureMode)
                putExtra(EXTRA_PROCESS_MODE, processMode)
                context.startActivity(this)
            }
        }
    }

    private val mBinding by lazy {
        BeautyActivityBinding.inflate(LayoutInflater.from(this))
    }
    private val mChannelName by lazy {
        intent.getStringExtra(EXTRA_CHANNEL_NAME)
    }
    private val mRtcHandler = object : IRtcEngineEventHandler() {
        override fun onError(err: Int) {
            super.onError(err)
            Log.e(TAG, "Rtc error code=$err, msg=${RtcEngine.getErrorDescription(err)}")
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            super.onUserJoined(uid, elapsed)
            runOnUiThread {
                if (mBinding.remoteVideoView.tag == null) {
                    mBinding.remoteVideoView.tag = uid
                    val renderView = SurfaceView(this@FaceUnityActivity)
                    renderView.setZOrderMediaOverlay(true)
                    renderView.setZOrderOnTop(true)
                    mBinding.remoteVideoView.addView(renderView)
                    mRtcEngine.setupRemoteVideo(
                        VideoCanvas(
                            renderView,
                            Constants.RENDER_MODE_HIDDEN,
                            uid
                        )
                    )
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            super.onUserOffline(uid, reason)
            runOnUiThread {
                if (mBinding.remoteVideoView.tag == uid) {
                    mBinding.remoteVideoView.tag = null
                    mBinding.remoteVideoView.removeAllViews()
                    mRtcEngine.setupRemoteVideo(
                        VideoCanvas(null, Constants.RENDER_MODE_HIDDEN, uid)
                    )
                }
            }
        }
    }
    private val mRtcEngine by lazy {
        RtcEngine.create(RtcEngineConfig().apply {
            mContext = applicationContext
            mAppId = BuildConfig.AGORA_APP_ID
            mEventHandler = object : IRtcEngineEventHandler() {}
        }).apply {
            enableExtension("agora_video_filters_clear_vision", "clear_vision", true)
        }
    }
    private val mFaceUnityApi by lazy {
        createFaceUnityBeautyAPI()
    }
    private val mVideoEncoderConfiguration by lazy {
        VideoEncoderConfiguration(
            ReflectUtils.getStaticFiledValue(
                VideoEncoderConfiguration::class.java,
                intent.getStringExtra(EXTRA_RESOLUTION)
            ),
            ReflectUtils.getStaticFiledValue(
                FRAME_RATE::class.java,
                intent.getStringExtra(EXTRA_FRAME_RATE)
            ),
            0,
            VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        )
    }
    private val beautyEnableDefault = true
    private val mSettingDialog by lazy {
        SettingsDialog(this).apply {
            setBeautyEnable(beautyEnableDefault)
            setOnBeautyChangeListener { enable ->
                mFaceUnityApi.enable(enable)
            }
            setOnColorEnhanceChangeListener {enable ->
                val options = ColorEnhanceOptions()
                options.strengthLevel = 0.5f
                options.skinProtectLevel = 0.5f
                mRtcEngine.setColorEnhanceOptions(enable, options)
            }
            setOnI420ChangeListener { enable ->
                if(enable){
                    mFaceUnityApi.setParameters("beauty_mode", "2")
                }else{
                    mFaceUnityApi.setParameters("beauty_mode", "0")
                }
            }
        }
    }
    private var cameraConfig = CameraConfig()
    private val fuRenderKit = FaceUnityBeautySDK.fuRenderKit


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mBinding.root)
        window.decorView.keepScreenOn = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val isCustomCaptureMode =
            intent.getStringExtra(EXTRA_CAPTURE_MODE) == getString(R.string.beauty_capture_custom)
        mFaceUnityApi.initialize(
            Config(
                applicationContext,
                mRtcEngine,
                fuRenderKit,
                captureMode = if(isCustomCaptureMode) CaptureMode.Custom else CaptureMode.Agora,
                cameraConfig = this.cameraConfig,
                statsEnable = true,
                eventCallback = object: IEventCallback{
                    override fun onBeautyStats(stats: BeautyStats) {
                        Log.d(TAG, "BeautyStats stats = $stats")
                    }
                }
            )
        )
        when (intent.getStringExtra(EXTRA_PROCESS_MODE)) {
            getString(R.string.beauty_process_auto) -> mFaceUnityApi.setParameters("beauty_mode", "0")
            getString(R.string.beauty_process_texture) -> mFaceUnityApi.setParameters("beauty_mode", "3")
            getString(R.string.beauty_process_i420) -> mFaceUnityApi.setParameters("beauty_mode", "2")
        }
        if(isCustomCaptureMode){
            mRtcEngine.registerVideoFrameObserver(object : IVideoFrameObserver {

                override fun onCaptureVideoFrame(
                    sourceType: Int,
                    videoFrame: VideoFrame?
                ) = when (mFaceUnityApi.onFrame(videoFrame!!)) {
                    ErrorCode.ERROR_FRAME_SKIPPED.value -> false
                    else -> true
                }

                override fun onPreEncodeVideoFrame(
                    sourceType: Int,
                    videoFrame: VideoFrame?
                ) = true

                override fun onMediaPlayerVideoFrame(
                    videoFrame: VideoFrame?,
                    mediaPlayerId: Int
                ) = true

                override fun onRenderVideoFrame(
                    channelId: String?,
                    uid: Int,
                    videoFrame: VideoFrame?
                ) = true

                override fun getVideoFrameProcessMode() = IVideoFrameObserver.PROCESS_MODE_READ_WRITE

                override fun getVideoFormatPreference() = IVideoFrameObserver.VIDEO_PIXEL_DEFAULT

                override fun getRotationApplied() = false

                override fun getMirrorApplied() = mFaceUnityApi.getMirrorApplied()

                override fun getObservedFramePosition() = IVideoFrameObserver.POSITION_POST_CAPTURER
            })
        }
        if (beautyEnableDefault) {
            mFaceUnityApi.enable(true)
        }

        // Config RtcEngine
        mRtcEngine.addHandler(mRtcHandler)
        mRtcEngine.setVideoEncoderConfiguration(mVideoEncoderConfiguration)
        mRtcEngine.enableVideo()


        // render local video
        mFaceUnityApi.setupLocalVideo(mBinding.localVideoView, Constants.RENDER_MODE_FIT)


        // join channel
        mRtcEngine.joinChannel(null, mChannelName, 0, ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishCameraTrack = true
            publishMicrophoneTrack = false
            autoSubscribeAudio = false
            autoSubscribeVideo = true
        })


        // init view
        mBinding.ivCamera.setOnClickListener {
            mRtcEngine.switchCamera()
        }
        mBinding.ivSetting.setOnClickListener {
            mSettingDialog.show()
        }
        mBinding.ctvFaceBeauty.setOnClickListener {
            val enable = !mBinding.ctvFaceBeauty.isChecked
            mBinding.ctvFaceBeauty.isChecked = enable
            mFaceUnityApi.setBeautyPreset(if (enable) BeautyPreset.DEFAULT else BeautyPreset.CUSTOM)
        }
        mBinding.ctvMarkup.setOnClickListener {
            val enable = !mBinding.ctvMarkup.isChecked
            mBinding.ctvMarkup.isChecked = enable
            if (enable) {
                val makeup =
                    SimpleMakeup(FUBundleData("graphics" + File.separator + "face_makeup.bundle"))
                makeup.setCombinedConfig(FUBundleData("beauty_faceunity/makeup/naicha.bundle"))
                makeup.makeupIntensity = 1.0
                fuRenderKit.makeup = makeup
            } else {
                fuRenderKit.makeup = null
            }
        }
        mBinding.ctvSticker.setOnClickListener {
            val enable = !mBinding.ctvSticker.isChecked
            mBinding.ctvSticker.isChecked = enable
            if (enable) {
                val prop: Prop = Sticker(FUBundleData("beauty_faceunity/sticker/fu_zh_fenshu.bundle"))
                fuRenderKit.propContainer.replaceProp(null, prop)
            } else {
                fuRenderKit.propContainer.removeAllProp()
            }
        }
        mBinding.ivMirror.setOnClickListener {
            val isFront = mFaceUnityApi.isFrontCamera()
            if(isFront){
                cameraConfig = CameraConfig(
                    frontMirror = when(cameraConfig.frontMirror){
                        MirrorMode.MIRROR_LOCAL_REMOTE -> MirrorMode.MIRROR_LOCAL_ONLY
                        MirrorMode.MIRROR_LOCAL_ONLY -> MirrorMode.MIRROR_REMOTE_ONLY
                        MirrorMode.MIRROR_REMOTE_ONLY -> MirrorMode.MIRROR_NONE
                        MirrorMode.MIRROR_NONE -> MirrorMode.MIRROR_LOCAL_REMOTE
                    },
                    backMirror = cameraConfig.backMirror
                )
                Toast.makeText(this, "frontMirror=${cameraConfig.frontMirror}", Toast.LENGTH_SHORT).show()
            } else {
                cameraConfig = CameraConfig(
                    frontMirror = cameraConfig.frontMirror,
                    backMirror = when(cameraConfig.backMirror){
                        MirrorMode.MIRROR_NONE -> MirrorMode.MIRROR_LOCAL_REMOTE
                        MirrorMode.MIRROR_LOCAL_REMOTE -> MirrorMode.MIRROR_LOCAL_ONLY
                        MirrorMode.MIRROR_LOCAL_ONLY -> MirrorMode.MIRROR_REMOTE_ONLY
                        MirrorMode.MIRROR_REMOTE_ONLY -> MirrorMode.MIRROR_NONE
                    }
                )
                Toast.makeText(this, "backMirror=${cameraConfig.backMirror}", Toast.LENGTH_SHORT).show()
            }
            mFaceUnityApi.updateCameraConfig(cameraConfig)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        mRtcEngine.leaveChannel()
        mFaceUnityApi.release()
        RtcEngine.destroy()
    }

}

object FaceUnityBeautySDK {
    private val TAG = "FaceUnityBeautySDK"

    private val fuAIKit = FUAIKit.getInstance()
    val fuRenderKit = FURenderKit.getInstance()

    /* AI道具*/
    private val BUNDLE_AI_FACE = "model" + File.separator + "ai_face_processor.bundle"
    private val BUNDLE_AI_HUMAN = "model" + File.separator + "ai_human_processor.bundle"

    private val workerThread = Executors.newSingleThreadExecutor()

    fun initBeauty(context: Context){
        FURenderManager.setKitDebug(FULogger.LogLevel.TRACE)
        FURenderManager.setCoreDebug(FULogger.LogLevel.ERROR)
        FURenderManager.registerFURender(context, getAuth(), object : OperateCallback {
            override fun onSuccess(code: Int, msg: String) {
                Log.i(TAG, "FURenderManager onSuccess -- code=$code, msg=$msg")
                if (code == OPERATE_SUCCESS_AUTH) {
                    faceunity.fuSetUseTexAsync(1)
                    workerThread.submit {
                        fuAIKit.loadAIProcessor(BUNDLE_AI_FACE, FUAITypeEnum.FUAITYPE_FACEPROCESSOR)
                        fuAIKit.loadAIProcessor(BUNDLE_AI_HUMAN, FUAITypeEnum.FUAITYPE_HUMAN_PROCESSOR)
                    }
                }
            }

            override fun onFail(errCode: Int, errMsg: String) {
                Log.e(TAG, "FURenderManager onFail -- code=$errCode, msg=$errMsg")
            }
        })
    }

    private fun getAuth(): ByteArray{
        val authpack = Class.forName("io.agora.beautyapi.demo.authpack")
        val aMethod = authpack.getDeclaredMethod("A")
        aMethod.isAccessible = true
        val authValue = aMethod.invoke(null) as? ByteArray
        return authValue ?: ByteArray(0)
    }
}