package com.example.ui.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CallRed
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun IncomingCallBanner(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        color = Color(0xFF102A26),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            UserAvatar(name = call.callerName, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = call.callerName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (call.callTypeEnum() == CallType.VIDEO) "Video Call E2EE Masuk..." else "Telepon E2EE Masuk...",
                    color = EmeraldBright,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            IconButton(
                onClick = onDecline,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CallRed)
                    .testTag("decline_call_button")
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Tolak panggilan", tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = onAccept,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary)
                    .testTag("accept_call_button")
            ) {
                Icon(
                    imageVector = if (call.callTypeEnum() == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = "Terima panggilan",
                    tint = Color(0xFF002119)
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ActiveCallScreen(
    call: CallSession,
    onEndCall: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isVideoCall = call.callTypeEnum() == CallType.VIDEO

    var isMicMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(isVideoCall) }
    var isVideoEnabled by remember { mutableStateOf(isVideoCall) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var micAmplitude by remember { mutableFloatStateOf(0f) }

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            hasAudioPerm = grants[Manifest.permission.RECORD_AUDIO] == true || hasAudioPerm
            hasCameraPerm = grants[Manifest.permission.CAMERA] == true || hasCameraPerm
        }
    )

    LaunchedEffect(isVideoCall) {
        val needed = buildList {
            if (!hasAudioPerm) add(Manifest.permission.RECORD_AUDIO)
            if (isVideoCall && !hasCameraPerm) add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // AudioManager speakerphone control
    DisposableEffect(isSpeakerOn) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am?.isSpeakerphoneOn = isSpeakerOn
        onDispose {
            am?.mode = AudioManager.MODE_NORMAL
        }
    }

    // Real AudioRecord mic amplitude monitor when unmuted
    LaunchedEffect(hasAudioPerm, isMicMuted) {
        if (!hasAudioPerm || isMicMuted) {
            micAmplitude = 0f
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return@withContext
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buf = ShortArray(minBuf / 2)
                    while (isActive) {
                        val read = recorder.read(buf, 0, buf.size)
                        if (read > 0) {
                            var peak = 0
                            for (i in 0 until read) {
                                val v = abs(buf[i].toInt())
                                if (v > peak) peak = v
                            }
                            micAmplitude = (peak / 12000f).coerceIn(0f, 1f)
                        }
                        delay(80)
                    }
                }
            } catch (_: Exception) {
                // Ignore if device audio record unavailable
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(call.id) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = 1f + (micAmplitude * 0.25f),
        label = "micPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071E1B), Color(0xFF0B141A), Color(0xFF050A0E))
                )
            )
    ) {
        if (isVideoCall && isVideoEnabled && hasCameraPerm) {
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(useFrontCamera, isVideoEnabled, hasCameraPerm) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (_: Exception) {
                        // Camera unavailable on device/emulator
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Voice Call or Video Off centerpiece
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatar(name = call.chatTitle, size = 108.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isMicMuted) "Mikrofon Dibisukan" else "Audio Terenkripsi Aktif",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9FB0B9)
                )
            }
        }

        // Top Call Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 18.dp, start = 20.dp, end = 20.dp)
                .align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = EmeraldBright,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "E2EE Terverifikasi • ${call.e2eeSafetyCode}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = call.chatTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            val statusLabel = when (call.callStatusEnum()) {
                CallStatus.RINGING -> "Memanggil... (${String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)})"
                CallStatus.CONNECTED -> String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                else -> "Panggilan Berakhir"
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = EmeraldBright
            )
        }

        // Bottom Call Controls
        Surface(
            color = Color(0xFF111B21).copy(alpha = 0.92f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(vertical = 20.dp, horizontal = 16.dp)
            ) {
                CallControlBtn(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeUp,
                    label = "Speaker",
                    active = isSpeakerOn,
                    testTag = "call_speaker_button",
                    onClick = { isSpeakerOn = !isSpeakerOn }
                )

                if (isVideoCall) {
                    CallControlBtn(
                        icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = "Kamera",
                        active = !isVideoEnabled,
                        testTag = "call_camera_toggle_button",
                        onClick = { isVideoEnabled = !isVideoEnabled }
                    )
                    CallControlBtn(
                        icon = Icons.Default.Cameraswitch,
                        label = "Putar",
                        active = false,
                        testTag = "call_flip_camera_button",
                        onClick = { useFrontCamera = !useFrontCamera }
                    )
                }

                CallControlBtn(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMicMuted) "Unmute" else "Bisu",
                    active = isMicMuted,
                    testTag = "call_mute_button",
                    onClick = { isMicMuted = !isMicMuted }
                )

                Button(
                    onClick = { onEndCall(elapsedSeconds) },
                    colors = ButtonDefaults.buttonColors(containerColor = CallRed),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(62.dp)
                        .testTag("end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Akhiri panggilan",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlBtn(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color(0xFF233138))
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF111B21) else Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelSmall)
    }
}
