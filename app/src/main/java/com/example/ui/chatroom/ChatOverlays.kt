package com.example.ui.chatroom

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ChatReceiptOverride
import com.example.data.ChatRoom
import com.example.data.DecryptedLiveLocation
import com.example.data.DecryptedMessage
import com.example.data.MessageType
import com.example.media.MediaHelper
import com.example.ui.components.LocationMiniMapCard
import com.example.ui.components.formatTimeShort
import com.example.ui.privacy.ChatReceiptOverrideSelector
import com.example.ui.theme.CallRed
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ReadReceiptBlueDark
import com.example.ui.theme.ReadReceiptBlueLight
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    isSharingLive: Boolean,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onShareCurrentLocation: () -> Unit,
    onSelectLiveDuration: (Int) -> Unit,
    onStopLiveLocation: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Kirim Lampiran Terenkripsi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentTile(
                    icon = Icons.Default.Image,
                    label = "Gambar",
                    tint = Color(0xFF7E57C2),
                    testTag = "attach_image_button",
                    onClick = {
                        onDismiss()
                        onPickImage()
                    }
                )
                AttachmentTile(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    tint = Color(0xFFEF5350),
                    testTag = "attach_video_button",
                    onClick = {
                        onDismiss()
                        onPickVideo()
                    }
                )
                AttachmentTile(
                    icon = Icons.Default.MyLocation,
                    label = "Lokasi Terkini",
                    tint = EmeraldPrimary,
                    testTag = "attach_current_location_button",
                    onClick = {
                        onDismiss()
                        onShareCurrentLocation()
                    }
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShareLocation,
                            contentDescription = null,
                            tint = EmeraldPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ShareLoc Realtime (GPS Live)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (isSharingLive) {
                        Button(
                            onClick = {
                                onDismiss()
                                onStopLiveLocation()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CallRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Berhenti Berbagi Lokasi Realtime")
                        }
                    } else {
                        Text(
                            text = "Pilih durasi pembaruan koordinat langsung terenkripsi:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(15 to "15 Menit", 60 to "1 Jam", 480 to "8 Jam").forEach { (mins, label) ->
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onSelectLiveDuration(mins)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("live_loc_${mins}m_button")
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: DecryptedMessage,
    chat: ChatRoom,
    currentUserId: String,
    readReceiptsActive: Boolean,
    isOwnMessage: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val blueTint = if (isDark) ReadReceiptBlueDark else ReadReceiptBlueLight
    val otherRecipients = remember(chat.memberUids, currentUserId) {
        chat.memberUids.filter { it != currentUserId }
    }
    val readRecipients = remember(otherRecipients, message.readByUids) {
        otherRecipients.filter { it in message.readByUids }
    }
    val unreadRecipients = remember(otherRecipients, message.readByUids) {
        otherRecipients.filter { it !in message.readByUids }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Opsi & Info Pesan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (isOwnMessage && !message.deleted) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (readReceiptsActive) Icons.Default.DoneAll else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (readReceiptsActive && readRecipients.size == otherRecipients.size) {
                                    blueTint
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Info Laporan Dibaca",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!readReceiptsActive) {
                            Text(
                                text = "Privasi Aktif: Laporan dibaca dimatikan untuk obrolan ini.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (otherRecipients.isEmpty()) {
                            Text(
                                text = "Pesan Tersimpan pribadi (dibaca oleh Anda).",
                                style = MaterialTheme.typography.labelSmall,
                                color = blueTint
                            )
                        } else {
                            if (readRecipients.isNotEmpty()) {
                                readRecipients.forEach { uid ->
                                    val name = chat.memberNames[uid] ?: "Anggota"
                                    val readTs = message.readByTimestamps[uid]
                                    val timeSuffix = if (readTs != null) " • ${formatTimeShort(readTs)}" else ""
                                    Text(
                                        text = "✓✓ Dibaca oleh $name$timeSuffix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = blueTint,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Text(
                                    text = "Belum dibaca oleh penerima.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (unreadRecipients.isNotEmpty() && readRecipients.isNotEmpty()) {
                                val waitingNames = unreadRecipients.joinToString(", ") {
                                    chat.memberNames[it] ?: "Anggota"
                                }
                                Text(
                                    text = "Menunggu: $waitingNames",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (isOwnMessage && message.messageType == MessageType.TEXT && !message.deleted) {
                ActionSheetRow(
                    icon = Icons.Default.Edit,
                    label = "Edit Pesan",
                    tint = MaterialTheme.colorScheme.onSurface,
                    testTag = "action_edit_message",
                    onClick = {
                        onDismiss()
                        onEdit()
                    }
                )
            }

            if (isOwnMessage && !message.deleted) {
                ActionSheetRow(
                    icon = Icons.Default.Delete,
                    label = "Hapus Pesan untuk Semua",
                    tint = CallRed,
                    testTag = "action_delete_message",
                    onClick = {
                        onDismiss()
                        onDelete()
                    }
                )
            }

            if (message.text.isNotBlank() && !message.deleted) {
                ActionSheetRow(
                    icon = Icons.Default.ContentCopy,
                    label = "Salin Teks",
                    tint = MaterialTheme.colorScheme.onSurface,
                    testTag = "action_copy_message",
                    onClick = {
                        onCopy(message.text)
                        onDismiss()
                    }
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Metadata Enkripsi: ${message.encryptionVersion}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (message.ciphertextPreview.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Ciphertext: ${message.ciphertextPreview}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(testTag)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SafetyNumberDialog(
    chatId: String,
    chatTitle: String,
    safetyNumber: String,
    currentOverride: ChatReceiptOverride,
    effectiveReadReceiptsActive: Boolean,
    onSetChatOverride: (ChatReceiptOverride) -> Unit,
    onOpenGlobalPrivacy: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()
    val blueTint = if (isDark) ReadReceiptBlueDark else ReadReceiptBlueLight

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Keamanan & Privasi Obrolan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pesan, foto, video, lokasi, dan panggilan di \"$chatTitle\" diamankan dengan enkripsi end-to-end ECDH P-256 & AES-256-GCM.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Nomor Keamanan (60 Digit):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = safetyNumber,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (effectiveReadReceiptsActive) Icons.Default.DoneAll else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (effectiveReadReceiptsActive) blueTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Laporan Dibaca (Centang Biru)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (effectiveReadReceiptsActive) {
                                "Status saat ini: Aktif untuk obrolan ini"
                            } else {
                                "Status saat ini: Dimatikan (Privat) untuk obrolan ini"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (effectiveReadReceiptsActive) blueTint else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ChatReceiptOverrideSelector(
                            selected = currentOverride,
                            chatId = chatId,
                            onSelect = onSetChatOverride
                        )
                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenGlobalPrivacy()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Pengaturan Privasi Global")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tutup")
            }
        },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(safetyNumber)) }) {
                Text("Salin Nomor")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationTrackerSheet(
    liveLocations: List<DecryptedLiveLocation>,
    currentUserId: String,
    onStopMySharing: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pelacak Lokasi Realtime (${liveLocations.size} Aktif)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(liveLocations, key = { it.sharerId }) { item ->
                    val remainingMin = ((item.expiresAtEpochMs - System.currentTimeMillis()) / 60_000L)
                        .coerceAtLeast(1L)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.sharerId == currentUserId) "${item.sharerName} (Anda)" else item.sharerName,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sisa ~${remainingMin}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val loc = item.location
                            if (loc != null) {
                                LocationMiniMapCard(location = loc, isLive = true)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val uri = Uri.parse(
                                                String.format(
                                                    Locale.US,
                                                    "geo:%f,%f?q=%f,%f(%s)",
                                                    loc.latitude,
                                                    loc.longitude,
                                                    loc.latitude,
                                                    loc.longitude,
                                                    Uri.encode(item.sharerName)
                                                )
                                            )
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Buka Peta")
                                    }
                                    if (item.sharerId == currentUserId) {
                                        Button(
                                            onClick = onStopMySharing,
                                            colors = ButtonDefaults.buttonColors(containerColor = CallRed),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Hentikan")
                                        }
                                    }
                                }
                            } else {
                                Text("Menunggu sinyal koordinat terenkripsi...")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ImageLightboxDialog(
    base64Jpeg: String,
    caption: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(base64Jpeg) {
        try {
            val bytes = Base64.decode(base64Jpeg, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = caption.ifBlank { "Foto terenkripsi" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Tutup gambar", tint = Color.White)
            }
            if (caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = caption,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(
    message: DecryptedMessage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val media = message.media
    val videoFile = remember(message.id, media) {
        if (media == null) null
        else if (media.base64Data.isNotBlank()) {
            MediaHelper.writeTempVideoForPlayback(context, message.id, media.base64Data)
        } else if (media.localFallbackPath.isNotBlank() && File(media.localFallbackPath).exists()) {
            File(media.localFallbackPath)
        } else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (videoFile != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(videoFile.absolutePath)
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = EmeraldBright,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Preview Video Terenkripsi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ukuran video asli melebihi batas sinkronisasi dokumen Firestore (390 KB), hanya thumbnail terenkripsi yang dikirim ke perangkat lain.",
                        color = Color(0xFFB0BEC5),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Tutup video", tint = Color.White)
            }
        }
    }
}
