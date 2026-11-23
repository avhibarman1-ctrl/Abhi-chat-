package com.example.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

enum class WebRtcConnectionStage {
    IDLE,
    INITIALIZING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    FAILED,
    CLOSED
}

data class WebRtcUiState(
    val stage: WebRtcConnectionStage = WebRtcConnectionStage.IDLE,
    val isFrontCamera: Boolean = true,
    val hasRemoteVideo: Boolean = false,
    val isNativeAvailable: Boolean = true,
    val statusDetail: String = ""
)

class WebRtcCallManager(
    context: Context,
    private val isVideoCall: Boolean,
    private val onLocalOfferCreated: (SessionDescription) -> Unit,
    private val onLocalAnswerCreated: (SessionDescription) -> Unit,
    private val onIceCandidateGenerated: (IceCandidate) -> Unit,
    private val onIceStateChanged: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _uiState = MutableStateFlow(WebRtcUiState())
    val uiState = _uiState.asStateFlow()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var localRendererInitialized = false
    private var remoteRendererInitialized = false

    private var remoteDescriptionSet = false
    private var offerSent = false
    private var answerSent = false
    private var disposed = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private val addedCandidateKeys = HashSet<String>()

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        _uiState.value = _uiState.value.copy(stage = WebRtcConnectionStage.INITIALIZING)
        try {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                factoryInitialized = true
            }

            eglBase = EglBase.create()
            val eglCtx = eglBase!!.eglBaseContext
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglCtx, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglCtx))
                .createPeerConnectionFactory()

            configureAudioSession(speakerEnabled = isVideoCall)
            createPeerConnection()
            setupLocalMediaTracks()
            _uiState.value = _uiState.value.copy(
                stage = WebRtcConnectionStage.CONNECTING,
                isNativeAvailable = true,
                statusDetail = "Signaling ready"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "WebRTC native initialization unavailable in current environment", t)
            _uiState.value = _uiState.value.copy(
                stage = WebRtcConnectionStage.CONNECTING,
                isNativeAvailable = false,
                statusDetail = "Signaling only (media native fallback)"
            )
        }
    }

    private fun configureAudioSession(speakerEnabled: Boolean) {
        try {
            audioManager?.let { am ->
                previousAudioMode = am.mode
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                setSpeakerphoneOn(speakerEnabled)
            }
        } catch (_: Throwable) {
        }
    }

    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return
        val iceServers = buildList {
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            if (BuildConfig.TURN_SERVER_URL.isNotBlank()) {
                val builder = PeerConnection.IceServer.builder(BuildConfig.TURN_SERVER_URL)
                if (BuildConfig.TURN_USERNAME.isNotBlank()) {
                    builder.setUsername(BuildConfig.TURN_USERNAME)
                }
                if (BuildConfig.TURN_PASSWORD.isNotBlank()) {
                    builder.setPassword(BuildConfig.TURN_PASSWORD)
                }
                add(builder.createIceServer())
            }
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    if (!disposed) onIceCandidateGenerated(candidate)
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    if (disposed) return
                    val mapped = when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> WebRtcConnectionStage.CONNECTED
                        PeerConnection.IceConnectionState.DISCONNECTED -> WebRtcConnectionStage.RECONNECTING
                        PeerConnection.IceConnectionState.FAILED -> WebRtcConnectionStage.FAILED
                        PeerConnection.IceConnectionState.CLOSED -> WebRtcConnectionStage.CLOSED
                        else -> WebRtcConnectionStage.CONNECTING
                    }
                    _uiState.value = _uiState.value.copy(
                        stage = mapped,
                        statusDetail = newState.name
                    )
                    onIceStateChanged(newState.name)
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack && !disposed) {
                        remoteVideoTrack = track
                        remoteRenderer?.let { renderer ->
                            try {
                                track.addSink(renderer)
                            } catch (_: Throwable) {
                            }
                        }
                        _uiState.value = _uiState.value.copy(hasRemoteVideo = true)
                    }
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: MediaStream?) {
                    val vTrack = stream?.videoTracks?.firstOrNull() ?: return
                    if (!disposed) {
                        remoteVideoTrack = vTrack
                        remoteRenderer?.let { renderer ->
                            try {
                                vTrack.addSink(renderer)
                            } catch (_: Throwable) {
                            }
                        }
                        _uiState.value = _uiState.value.copy(hasRemoteVideo = true)
                    }
                }
                override fun onRemoveStream(stream: MediaStream?) = Unit
                override fun onDataChannel(dataChannel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
            }
        )
    }

    private fun setupLocalMediaTracks() {
        val factory = peerConnectionFactory ?: return
        val pc = peerConnection ?: return

        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("abhi_audio_track", audioSource).apply {
                setEnabled(true)
            }
            pc.addTrack(localAudioTrack, listOf("abhi_stream"))
        } catch (t: Throwable) {
            Log.w(TAG, "Could not start local audio track", t)
        }

        if (isVideoCall) {
            try {
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    surfaceTextureHelper = SurfaceTextureHelper.create("AbhiCaptureThread", eglBase!!.eglBaseContext)
                    videoSource = factory.createVideoSource(capturer.isScreencast)
                    capturer.initialize(surfaceTextureHelper, appContext, videoSource!!.capturerObserver)
                    capturer.startCapture(720, 1280, 30)
                    localVideoTrack = factory.createVideoTrack("abhi_video_track", videoSource).apply {
                        setEnabled(true)
                    }
                    pc.addTrack(localVideoTrack, listOf("abhi_stream"))
                    localRenderer?.let { renderer ->
                        localVideoTrack?.addSink(renderer)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not start local camera track", t)
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val devices = enumerator.deviceNames
        for (name in devices) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in devices) {
            if (!enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let {
                    _uiState.value = _uiState.value.copy(isFrontCamera = false)
                    return it
                }
            }
        }
        return null
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        val egl = eglBase ?: return
        try {
            if (!localRendererInitialized) {
                renderer.init(egl.eglBaseContext, null)
                renderer.setMirror(_uiState.value.isFrontCamera)
                renderer.setZOrderMediaOverlay(true)
                renderer.setEnableHardwareScaler(true)
                localRendererInitialized = true
            }
            localRenderer = renderer
            localVideoTrack?.addSink(renderer)
        } catch (t: Throwable) {
            Log.w(TAG, "attachLocalRenderer skipped", t)
        }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        val egl = eglBase ?: return
        try {
            if (!remoteRendererInitialized) {
                renderer.init(egl.eglBaseContext, null)
                renderer.setMirror(false)
                renderer.setEnableHardwareScaler(true)
                remoteRendererInitialized = true
            }
            remoteRenderer = renderer
            remoteVideoTrack?.addSink(renderer)
        } catch (t: Throwable) {
            Log.w(TAG, "attachRemoteRenderer skipped", t)
        }
    }

    fun createAndSendOfferIfNeeded() {
        val pc = peerConnection ?: return
        if (offerSent || disposed) return
        offerSent = true
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }
        pc.createOffer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    if (disposed) return
                    pc.setLocalDescription(SimpleSdpObserver(), desc)
                    onLocalOfferCreated(desc)
                }
            },
            constraints
        )
    }

    fun handleRemoteOfferAndCreateAnswer(offerSdp: String, offerType: String) {
        val pc = peerConnection ?: return
        if (offerSdp.isBlank() || answerSent || disposed) return
        answerSent = true
        val type = if (offerType.equals("ANSWER", true)) {
            SessionDescription.Type.ANSWER
        } else {
            SessionDescription.Type.OFFER
        }
        val remoteDesc = SessionDescription(type, offerSdp)
        pc.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (disposed) return
                    remoteDescriptionSet = true
                    flushPendingCandidates()
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
                    }
                    pc.createAnswer(
                        object : SimpleSdpObserver() {
                            override fun onCreateSuccess(desc: SessionDescription) {
                                if (disposed) return
                                pc.setLocalDescription(SimpleSdpObserver(), desc)
                                onLocalAnswerCreated(desc)
                            }
                        },
                        constraints
                    )
                }
            },
            remoteDesc
        )
    }

    fun handleRemoteAnswer(answerSdp: String, answerType: String) {
        val pc = peerConnection ?: return
        if (answerSdp.isBlank() || remoteDescriptionSet || disposed) return
        val type = if (answerType.equals("OFFER", true)) {
            SessionDescription.Type.OFFER
        } else {
            SessionDescription.Type.ANSWER
        }
        val remoteDesc = SessionDescription(type, answerSdp)
        pc.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (disposed) return
                    remoteDescriptionSet = true
                    flushPendingCandidates()
                }
            },
            remoteDesc
        )
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        if (candidateSdp.isBlank() || disposed) return
        val dedupeKey = "$sdpMid|$sdpMLineIndex|$candidateSdp"
        if (!addedCandidateKeys.add(dedupeKey)) return
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        if (!remoteDescriptionSet) {
            pendingIceCandidates.add(iceCandidate)
        } else {
            try {
                peerConnection?.addIceCandidate(iceCandidate)
            } catch (_: Throwable) {
            }
        }
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        val iterator = pendingIceCandidates.iterator()
        while (iterator.hasNext()) {
            try {
                pc.addIceCandidate(iterator.next())
            } catch (_: Throwable) {
            }
            iterator.remove()
        }
    }

    fun setMicMuted(muted: Boolean) {
        try {
            localAudioTrack?.setEnabled(!muted)
        } catch (_: Throwable) {
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        try {
            localVideoTrack?.setEnabled(enabled)
        } catch (_: Throwable) {
        }
    }

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        try {
            capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    _uiState.value = _uiState.value.copy(isFrontCamera = isFrontCamera)
                    localRenderer?.setMirror(isFrontCamera)
                }

                override fun onCameraSwitchError(errorDescription: String?) {
                    Log.w(TAG, "switchCamera error: $errorDescription")
                }
            })
        } catch (_: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    fun setSpeakerphoneOn(enabled: Boolean) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (device != null) {
                    am.setCommunicationDevice(device)
                } else if (!enabled) {
                    am.clearCommunicationDevice()
                }
            } else {
                am.isSpeakerphoneOn = enabled
            }
        } catch (_: Throwable) {
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        try {
            localRenderer?.let { r ->
                try {
                    localVideoTrack?.removeSink(r)
                } catch (_: Throwable) {
                }
                if (localRendererInitialized) r.release()
            }
            remoteRenderer?.let { r ->
                try {
                    remoteVideoTrack?.removeSink(r)
                } catch (_: Throwable) {
                }
                if (remoteRendererInitialized) r.release()
            }
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            surfaceTextureHelper?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnectionFactory?.dispose()
            eglBase?.release()
        } catch (_: Throwable) {
        }
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
                am.mode = previousAudioMode
            }
        } catch (_: Throwable) {
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        private const val TAG = "WebRtcCallManager"
        private var factoryInitialized = false
    }
}
