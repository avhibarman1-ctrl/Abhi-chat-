package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.EmergencyContact
import com.example.data.model.IceCandidateRecord
import com.example.data.model.SosAlert
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DangerRed
import com.example.util.LocationHelper
import com.example.util.SosSmsHelper
import com.example.webrtc.WebRtcCallManager
import com.example.webrtc.WebRtcConnectionStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    call: CallSession,
    currentUserId: String,
    currentUserName: String = "",
    emergencyContacts: List<EmergencyContact> = emptyList(),
    activeSosAlert: SosAlert? = null,
    remoteIceCandidates: List<IceCandidateRecord> = emptyList(),
    onOfferCreated: (callId: String, sdp: String, type: String) -> Unit = { _, _, _ -> },
    onAnswerCreated: (callId: String, sdp: String, type: String) -> Unit = { _, _, _ -> },
    onIceCandidate: (callId: String, isCaller: Boolean, sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit = { _, _, _, _, _ -> },
    onIceStateChanged: (callId: String, iceState: String) -> Unit = { _, _ -> },
    onActivateSos: (callId: String, lat: Double, lng: Double, recipientPhones: List<String>) -> Unit = { _, _, _, _ -> },
    onStopSos: (String) -> Unit = {},
    onAddEmergencyContact: (String, String, String) -> Unit = { _, _, _ -> },
    onEndCall: () -> Unit
) {
    BackHandler(onBack = onEndCall)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = call.callType == CallType.VIDEO.name
    val isCaller = call.callerId == currentUserId
    val peerName = if (isCaller) call.calleeName else call.callerName
    val peerAvatar = if (isCaller) call.calleeAvatarUrl else call.callerAvatarUrl
    val myDisplayName = currentUserName.ifBlank {
        if (isCaller) call.callerName else call.calleeName
    }

    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(isVideo) }
    var isCameraOn by remember { mutableStateOf(isVideo) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var isDispatchingSos by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickName by remember { mutableStateOf("") }
    var quickPhone by remember { mutableStateOf("") }
    var quickRelation by remember { mutableStateOf("") }

    val webRtcManager = remember(call.id) {
        WebRtcCallManager(
            context = context,
            isVideoCall = isVideo,
            onLocalOfferCreated = { desc ->
                onOfferCreated(call.id, desc.description, desc.type.canonicalForm())
            },
            onLocalAnswerCreated = { desc ->
                onAnswerCreated(call.id, desc.description, desc.type.canonicalForm())
            },
            onIceCandidateGenerated = { candidate ->
                onIceCandidate(call.id, isCaller, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            },
            onIceStateChanged = { stateName ->
                onIceStateChanged(call.id, stateName)
            }
        )
    }

    val rtcUiState by webRtcManager.uiState.collectAsState()

    DisposableEffect(call.id) {
        onDispose {
            webRtcManager.dispose()
        }
    }

    // Drive SDP offer / answer exchange based on call signaling fields
    LaunchedEffect(call.id, call.status, call.offerSdp, call.answerSdp, isCaller) {
        if (isCaller) {
            if (call.offerSdp.isBlank()) {
                webRtcManager.createAndSendOfferIfNeeded()
            }
            if (call.answerSdp.isNotBlank()) {
                webRtcManager.handleRemoteAnswer(call.answerSdp, call.answerType)
            }
        } else {
            if (call.offerSdp.isNotBlank() && call.answerSdp.isBlank()) {
                webRtcManager.handleRemoteOfferAndCreateAnswer(call.offerSdp, call.offerType)
            }
        }
    }

    // Feed incoming ICE candidates into PeerConnection
    LaunchedEffect(remoteIceCandidates) {
        remoteIceCandidates.forEach { rec ->
            webRtcManager.addRemoteIceCandidate(rec.sdpMid, rec.sdpMLineIndex, rec.candidate)
        }
    }

    fun triggerSosFlow() {
        if (isDispatchingSos) return
        isDispatchingSos = true
        scope.launch {
            try {
                val hasLocPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                val coords = if (hasLocPerm) LocationHelper.fetchCurrentLocation(context) else null
                val phones = emergencyContacts.map { it.phoneNumber }.filter { it.isNotBlank() }
                onActivateSos(
                    call.id,
                    coords?.latitude ?: 0.0,
                    coords?.longitude ?: 0.0,
                    phones
                )
                val smsBody = SosSmsHelper.buildSosMessage(
                    senderName = myDisplayName,
                    latitude = coords?.latitude,
                    longitude = coords?.longitude
                )
                SosSmsHelper.launchEmergencySms(context, phones, smsBody)
            } finally {
                isDispatchingSos = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val sosLocationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        triggerSosFlow()
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(call.status, rtcUiState.stage) {
        if (call.status == CallStatus.ACTIVE.name || rtcUiState.stage == WebRtcConnectionStage.CONNECTED) {
            while (true) {
                delay(1000L)
                elapsedSec += 1L
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF042F2E), Color(0xFF0F172A))
                )
            )
            .safeDrawingPadding()
    ) {
        // Remote WebRTC video surface background when video call is active
        if (isVideo && rtcUiState.isNativeAvailable) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { renderer ->
                        webRtcManager.attachRemoteRenderer(renderer)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("webrtc_remote_video_surface")
            )
        }

        // Local Camera Picture-in-Picture preview
        if (isVideo && isCameraOn && rtcUiState.isNativeAvailable) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 88.dp, end = 16.dp)
                    .size(width = 112.dp, height = 156.dp)
                    .testTag("webrtc_local_pip_surface")
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also { renderer ->
                            webRtcManager.attachLocalRenderer(renderer)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Emergency SOS Banner + ICE Connection Indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeSosAlert != null && activeSosAlert.active) {
                Surface(
                    color = DangerRed.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_sos_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "SOS Live Location Active",
                            tint = Color(0xFFFCA5A5)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SOS Live Location Syncing",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = LocationHelper.formatCoordinates(
                                    activeSosAlert.latitude,
                                    activeSosAlert.longitude
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFECACA)
                            )
                        }
                        TextButton(
                            onClick = { onStopSos(activeSosAlert.id) },
                            modifier = Modifier.testTag("stop_sos_button")
                        ) {
                            Text("Stop SOS", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (rtcUiState.stage == WebRtcConnectionStage.RECONNECTING ||
                rtcUiState.stage == WebRtcConnectionStage.FAILED ||
                call.iceState == "DISCONNECTED" ||
                call.iceState == "FAILED"
            ) {
                Surface(
                    color = Color(0xFF92400E).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("webrtc_connection_warning")
                ) {
                    Text(
                        text = if (rtcUiState.stage == WebRtcConnectionStage.FAILED || call.iceState == "FAILED") {
                            "Connection lost — check network or end call"
                        } else {
                            "Reconnecting peer audio/video..."
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (emergencyContacts.isEmpty()) {
                OutlinedButton(
                    onClick = { showQuickAddDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("in_call_add_sos_contact_button")
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Set Emergency SOS Contact",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Text(
                    text = "${emergencyContacts.size} SOS contact${if (emergencyContacts.size == 1) "" else "s"} ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF99F6E4)
                )
            }
        }

        // Center Peer Identity & Call Status (always shown cleanly when voice or awaiting remote video)
        if (!isVideo || !rtcUiState.hasRemoteVideo) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(
                    name = peerName,
                    avatarUrl = peerAvatar,
                    size = 112.dp,
                    showOnlineDot = true
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                val statusLabel = when {
                    call.status == CallStatus.RINGING.name -> if (isVideo) "Video Calling..." else "Voice Calling..."
                    rtcUiState.stage == WebRtcConnectionStage.CONNECTED || call.status == CallStatus.ACTIVE.name -> {
                        val m = elapsedSec / 60
                        val s = elapsedSec % 60
                        String.format("%02d:%02d", m, s)
                    }
                    else -> call.status
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF99F6E4),
                    modifier = Modifier.testTag("call_status_label")
                )
                Spacer(Modifier.height(6.dp))
                val connectionBadge = when (rtcUiState.stage) {
                    WebRtcConnectionStage.CONNECTED -> "WebRTC Encrypted • Connected"
                    WebRtcConnectionStage.RECONNECTING -> "WebRTC Reconnecting..."
                    WebRtcConnectionStage.FAILED -> "WebRTC Connection Failed"
                    else -> if (isVideo) "HD WebRTC Video Signaling" else "WebRTC Voice Signaling"
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("webrtc_stage_badge")
                ) {
                    Text(
                        text = connectionBadge,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Bottom Controls (SOS + Mute + Camera/Flip/Speaker + End Call)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    val fineGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted || coarseGranted) {
                        triggerSosFlow()
                    } else {
                        sosLocationPermLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB91C1C),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("in_call_sos_button")
            ) {
                Icon(Icons.Default.Emergency, contentDescription = "Emergency SOS")
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isDispatchingSos) "Sending SOS..." else "SOS • Sync GPS & SMS",
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        webRtcManager.setMicMuted(isMuted)
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color.White else Color.White.copy(alpha = 0.16f))
                        .testTag("mute_call_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) Color.Black else Color.White
                    )
                }

                if (isVideo) {
                    IconButton(
                        onClick = {
                            isCameraOn = !isCameraOn
                            webRtcManager.setCameraEnabled(isCameraOn)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (!isCameraOn) Color.White else Color.White.copy(alpha = 0.16f))
                            .testTag("toggle_camera_button")
                    ) {
                        Icon(
                            imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Camera",
                            tint = if (!isCameraOn) Color.Black else Color.White
                        )
                    }

                    IconButton(
                        onClick = { webRtcManager.switchCamera() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .testTag("flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = {
                        isSpeaker = !isSpeaker
                        webRtcManager.setSpeakerphoneOn(isSpeaker)
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaker) Color.White else Color.White.copy(alpha = 0.16f))
                        .testTag("speaker_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Speaker",
                        tint = if (isSpeaker) Color.Black else Color.White
                    )
                }

                FloatingActionButton(
                    onClick = {
                        webRtcManager.dispose()
                        onEndCall()
                    },
                    containerColor = DangerRed,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(62.dp)
                        .testTag("end_call_button")
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    if (showQuickAddDialog) {
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Add Emergency SOS Contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quickName,
                        onValueChange = { quickName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = quickPhone,
                        onValueChange = { quickPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = quickRelation,
                        onValueChange = { quickRelation = it },
                        label = { Text("Relationship") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEmergencyContact(quickName.trim(), quickPhone.trim(), quickRelation.trim())
                        quickName = ""
                        quickPhone = ""
                        quickRelation = ""
                        showQuickAddDialog = false
                    },
                    enabled = quickName.isNotBlank() && quickPhone.filter { it.isDigit() || it == '+' }.length >= 3
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
