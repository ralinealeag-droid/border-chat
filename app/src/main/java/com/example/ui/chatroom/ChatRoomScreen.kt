package com.example.ui.chatroom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.CallType
import com.example.data.ChatReceiptOverride
import com.example.data.ChatRoom
import com.example.data.ChatType
import com.example.data.DecryptedLiveLocation
import com.example.data.DecryptedMessage
import com.example.data.MessageReceiptStatus
import com.example.data.MessageType
import com.example.data.PublicProfile
import com.example.data.ReadReceiptPrivacySettings
import com.example.data.UiState
import com.example.data.computeMessageReceiptStatus
import com.example.ui.components.LocationMiniMapCard
import com.example.ui.components.MessageReceiptTicks
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatTimeShort
import com.example.ui.privacy.PrivacySettingsSheet
import com.example.ui.theme.DarkChatWallpaper
import com.example.ui.theme.DarkIncomingBubble
import com.example.ui.theme.DarkOutgoingBubble
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LightChatWallpaper
import com.example.ui.theme.LightIncomingBubble
import com.example.ui.theme.LightOutgoingBubble
import java.util.Locale

private enum class PendingLocationAction {
    NONE, CURRENT, LIVE_15, LIVE_60, LIVE_480
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chat: ChatRoom,
    allChats: List<ChatRoom>,
    profiles: List<PublicProfile>,
    currentUserId: String,
    messagesState: UiState<List<DecryptedMessage>>,
    liveLocations: List<DecryptedLiveLocation>,
    privacySettings: ReadReceiptPrivacySettings,
    isSharingLiveInThisChat: Boolean,
    isBusy: Boolean,
    showBackButton: Boolean,
    safetyNumber: String,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onMarkMessagesRead: (List<DecryptedMessage>) -> Unit,
    onToggleGlobalReadReceipts: (Boolean) -> Unit,
    onToggleGroupReadReceipts: (Boolean) -> Unit,
    onSetChatReadReceiptOverride: (String, ChatReceiptOverride) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVideo: (Uri) -> Unit,
    onShareCurrentLocation: () -> Unit,
    onStartLiveLocation: (Int) -> Unit,
    onStopLiveLocation: () -> Unit,
    onStartCall: (CallType) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var actionTargetMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showLiveTrackerSheet by remember { mutableStateOf(false) }
    var lightboxMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var videoPlayerMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var pendingLocationAction by remember { mutableStateOf(PendingLocationAction.NONE) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) onSendImage(uri) }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) onSendVideo(uri) }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                when (pendingLocationAction) {
                    PendingLocationAction.CURRENT -> onShareCurrentLocation()
                    PendingLocationAction.LIVE_15 -> onStartLiveLocation(15)
                    PendingLocationAction.LIVE_60 -> onStartLiveLocation(60)
                    PendingLocationAction.LIVE_480 -> onStartLiveLocation(480)
                    PendingLocationAction.NONE -> Unit
                }
            } else {
                onShowMessage("Izin lokasi diperlukan untuk mengirim ShareLoc.")
            }
            pendingLocationAction = PendingLocationAction.NONE
        }
    )

    fun triggerLocationAction(action: PendingLocationAction) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            when (action) {
                PendingLocationAction.CURRENT -> onShareCurrentLocation()
                PendingLocationAction.LIVE_15 -> onStartLiveLocation(15)
                PendingLocationAction.LIVE_60 -> onStartLiveLocation(60)
                PendingLocationAction.LIVE_480 -> onStartLiveLocation(480)
                PendingLocationAction.NONE -> Unit
            }
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val isReadReceiptsActive = privacySettings.isReadReceiptsEnabledFor(chat)
    val isAnyOtherMemberOnline = remember(profiles, chat.memberUids, currentUserId) {
        profiles.any { it.userId in chat.memberUids && it.userId != currentUserId && it.online }
    }

    val currentMessages = (messagesState as? UiState.Success)?.data.orEmpty()
    val unreadIncoming = remember(currentMessages, currentUserId) {
        currentMessages.filter {
            !it.deleted && it.senderId != currentUserId && currentUserId !in it.readByUids
        }
    }
    val unreadIdsKey = remember(unreadIncoming) { unreadIncoming.joinToString(",") { it.id } }

    LaunchedEffect(chat.id, unreadIdsKey, isReadReceiptsActive) {
        if (isReadReceiptsActive && unreadIncoming.isNotEmpty()) {
            onMarkMessagesRead(unreadIncoming)
        }
    }

    val messageCount = currentMessages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    val isGroup = chat.chatTypeEnum() == ChatType.GROUP
    val chatTitle = chat.displayTitle(currentUserId)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (isDark) DarkChatWallpaper else LightChatWallpaper,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSafetyDialog = true }
                    ) {
                        UserAvatar(
                            name = chatTitle,
                            isGroup = isGroup,
                            size = 40.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chatTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isReadReceiptsActive) Icons.Default.Lock else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (isReadReceiptsActive) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = buildString {
                                        append(if (isGroup) "${chat.memberUids.size} anggota • E2EE" else "Terenkripsi E2EE")
                                        if (!isReadReceiptsActive) append(" • Tanda baca mati")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isReadReceiptsActive) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onStartCall(CallType.VIDEO) },
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                    }
                    IconButton(
                        onClick = { onStartCall(CallType.VOICE) },
                        modifier = Modifier.testTag("voice_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Telepon Suara")
                    }
                    IconButton(
                        onClick = { showSafetyDialog = true },
                        modifier = Modifier.testTag("e2ee_verify_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verifikasi Enkripsi End-to-End",
                            tint = EmeraldPrimary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = EmeraldPrimary
                )
            }

            if (liveLocations.isNotEmpty()) {
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLiveTrackerSheet = true }
                        .testTag("live_location_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShareLocation,
                            contentDescription = null,
                            tint = EmeraldPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "ShareLoc Realtime Aktif (${liveLocations.size} orang)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ketuk untuk lihat koordinat langsung terenkripsi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSharingLiveInThisChat) {
                            Text(
                                text = "LIVE",
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = messagesState.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        val messages = messagesState.data
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(key = "e2ee_notice_header") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Pesan, media & lokasi terenkripsi end-to-end dengan AES-256-GCM.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            items(messages, key = { it.id }) { msg ->
                                if (msg.messageType == MessageType.CALL_LOG) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "${msg.text} • ${formatTimeShort(msg.createdAt)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                } else {
                                    val isMine = msg.senderId == currentUserId
                                    val receiptStatus = computeMessageReceiptStatus(
                                        createdAt = msg.createdAt,
                                        senderId = msg.senderId,
                                        memberUids = chat.memberUids,
                                        readByUids = msg.readByUids,
                                        readReceiptsVisible = isReadReceiptsActive,
                                        isAnyOtherMemberOnline = isAnyOtherMemberOnline
                                    )
                                    MessageBubble(
                                        message = msg,
                                        isMine = isMine,
                                        showSenderName = isGroup && !isMine,
                                        receiptStatus = receiptStatus,
                                        isDark = isDark,
                                        onLongOrMenuClick = { actionTargetMessage = msg },
                                        onOpenImage = { lightboxMessage = msg },
                                        onOpenVideo = { videoPlayerMessage = msg },
                                        onOpenLocation = { loc ->
                                            val uri = Uri.parse(
                                                String.format(
                                                    Locale.US,
                                                    "geo:%f,%f?q=%f,%f(%s)",
                                                    loc.latitude,
                                                    loc.longitude,
                                                    loc.latitude,
                                                    loc.longitude,
                                                    Uri.encode(loc.addressLabel.ifBlank { "Lokasi BORDER CHAT" })
                                                )
                                            )
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        },
                                        onOpenLiveTracker = { showLiveTrackerSheet = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (editingMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Mengedit Pesan",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = editingMessage!!.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                editingMessage = null
                                inputText = ""
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Batal edit")
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier.testTag("attachment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Lampirkan gambar, video, atau lokasi"
                            )
                        }
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ketik pesan terenkripsi...") },
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input_field")
                        )
                        if (editingMessage == null) {
                            IconButton(
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("quick_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Kirim gambar cepat"
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                Surface(
                    shape = CircleShape,
                    color = EmeraldPrimary,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable {
                            val trimmed = inputText.trim()
                            if (trimmed.isNotEmpty()) {
                                val targetEdit = editingMessage
                                if (targetEdit != null) {
                                    onEditMessage(targetEdit.id, trimmed)
                                    editingMessage = null
                                } else {
                                    onSendText(trimmed)
                                }
                                inputText = ""
                            }
                        }
                        .testTag("send_message_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (editingMessage != null) "Simpan edit pesan" else "Kirim pesan",
                            tint = Color(0xFF002119)
                        )
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        AttachmentBottomSheet(
            isSharingLive = isSharingLiveInThisChat,
            onDismiss = { showAttachmentSheet = false },
            onPickImage = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onShareCurrentLocation = { triggerLocationAction(PendingLocationAction.CURRENT) },
            onSelectLiveDuration = { mins ->
                val action = when (mins) {
                    15 -> PendingLocationAction.LIVE_15
                    60 -> PendingLocationAction.LIVE_60
                    else -> PendingLocationAction.LIVE_480
                }
                triggerLocationAction(action)
            },
            onStopLiveLocation = onStopLiveLocation
        )
    }

    actionTargetMessage?.let { msg ->
        MessageActionsSheet(
            message = msg,
            chat = chat,
            currentUserId = currentUserId,
            readReceiptsActive = isReadReceiptsActive,
            isOwnMessage = msg.senderId == currentUserId,
            onDismiss = { actionTargetMessage = null },
            onEdit = {
                editingMessage = msg
                inputText = msg.text
            },
            onDelete = { onDeleteMessage(msg.id) },
            onCopy = { txt ->
                clipboard.setText(AnnotatedString(txt))
                onShowMessage("Teks disalin ke clipboard.")
            }
        )
    }

    if (showSafetyDialog) {
        SafetyNumberDialog(
            chatId = chat.id,
            chatTitle = chatTitle,
            safetyNumber = safetyNumber,
            currentOverride = privacySettings.overrideForChat(chat.id),
            effectiveReadReceiptsActive = isReadReceiptsActive,
            onSetChatOverride = { override -> onSetChatReadReceiptOverride(chat.id, override) },
            onOpenGlobalPrivacy = { showPrivacySheet = true },
            onDismiss = { showSafetyDialog = false }
        )
    }

    if (showPrivacySheet) {
        PrivacySettingsSheet(
            privacySettings = privacySettings,
            chats = allChats,
            currentUserId = currentUserId,
            onToggleGlobalReadReceipts = onToggleGlobalReadReceipts,
            onToggleGroupReadReceipts = onToggleGroupReadReceipts,
            onSetChatOverride = onSetChatReadReceiptOverride,
            onDismiss = { showPrivacySheet = false }
        )
    }

    if (showLiveTrackerSheet) {
        LiveLocationTrackerSheet(
            liveLocations = liveLocations,
            currentUserId = currentUserId,
            onStopMySharing = {
                onStopLiveLocation()
                showLiveTrackerSheet = false
            },
            onDismiss = { showLiveTrackerSheet = false }
        )
    }

    lightboxMessage?.let { msg ->
        msg.media?.let { media ->
            ImageLightboxDialog(
                base64Jpeg = media.base64Data,
                caption = media.caption,
                onDismiss = { lightboxMessage = null }
            )
        }
    }

    videoPlayerMessage?.let { msg ->
        VideoPlayerDialog(
            message = msg,
            onDismiss = { videoPlayerMessage = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: DecryptedMessage,
    isMine: Boolean,
    showSenderName: Boolean,
    receiptStatus: MessageReceiptStatus,
    isDark: Boolean,
    onLongOrMenuClick: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenLocation: (com.example.data.DecryptedLocation) -> Unit,
    onOpenLiveTracker: () -> Unit
) {
    val bubbleColor = when {
        isMine && isDark -> DarkOutgoingBubble
        isMine && !isDark -> LightOutgoingBubble
        !isMine && isDark -> DarkIncomingBubble
        else -> LightIncomingBubble
    }
    val bubbleShape = RoundedCornerShape(
        topStart = if (isMine) 16.dp else 4.dp,
        topEnd = if (isMine) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 1.dp,
            modifier = Modifier
                .widthIn(min = 96.dp, max = 300.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = {
                        when (message.messageType) {
                            MessageType.IMAGE -> if (!message.deleted) onOpenImage()
                            MessageType.VIDEO -> if (!message.deleted) onOpenVideo()
                            MessageType.LOCATION -> message.location?.let(onOpenLocation)
                            MessageType.LIVE_LOCATION -> onOpenLiveTracker()
                            else -> onLongOrMenuClick()
                        }
                    },
                    onLongClick = onLongOrMenuClick
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                if (showSenderName) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (message.deleted || message.messageType == MessageType.DELETED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Pesan ini telah dihapus",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    when (message.messageType) {
                        MessageType.IMAGE -> {
                            val b64 = message.media?.base64Data.orEmpty()
                            val bitmap = remember(b64) {
                                try {
                                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Foto terenkripsi",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Text("📷 Foto terenkripsi")
                            }
                            if (message.text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        MessageType.VIDEO -> {
                            val thumbB64 = message.media?.thumbBase64.orEmpty()
                            val thumbBitmap = remember(thumbB64) {
                                try {
                                    if (thumbB64.isBlank()) null
                                    else {
                                        val bytes = Base64.decode(thumbB64, Base64.NO_WRAP)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0E1B21)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap.asImageBitmap(),
                                        contentDescription = "Thumbnail video",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Putar video",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = formatDurationMs(message.media?.durationMs ?: 0L),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (message.text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        MessageType.LOCATION, MessageType.LIVE_LOCATION -> {
                            val loc = message.location
                            if (loc != null) {
                                LocationMiniMapCard(
                                    location = loc,
                                    isLive = message.messageType == MessageType.LIVE_LOCATION
                                )
                            } else {
                                Text("📍 Lokasi terenkripsi")
                            }
                        }

                        else -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.edited && !message.deleted) {
                        Text(
                            text = "diedit",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatTimeShort(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine && !message.deleted) {
                        MessageReceiptTicks(
                            status = receiptStatus,
                            isDark = isDark,
                            size = 15.dp
                        )
                    }
                }
            }
        }
    }
}
