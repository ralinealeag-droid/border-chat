package com.example.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatReceiptOverride
import com.example.data.ChatRoom
import com.example.data.ChatType
import com.example.data.MessageReceiptStatus
import com.example.data.PublicProfile
import com.example.data.ReadReceiptPrivacySettings
import com.example.data.UiState
import com.example.data.computeMessageReceiptStatus
import com.example.ui.components.MessageReceiptTicks
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.privacy.PrivacySettingsSheet
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary

private enum class ChatFilter(val label: String) {
    ALL("Semua"),
    GROUPS("Grup"),
    DIRECT("Pribadi")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    currentUserId: String,
    currentUserName: String,
    myFingerprint: String,
    chatsState: UiState<List<ChatRoom>>,
    profilesState: UiState<List<PublicProfile>>,
    privacySettings: ReadReceiptPrivacySettings,
    selectedChatId: String?,
    onOpenChat: (String) -> Unit,
    onStartDirectChat: (PublicProfile) -> Unit,
    onOpenSavedMessages: () -> Unit,
    onCreateGroup: (String, List<PublicProfile>) -> Unit,
    onToggleGlobalReadReceipts: (Boolean) -> Unit,
    onToggleGroupReadReceipts: (Boolean) -> Unit,
    onSetChatReadReceiptOverride: (String, ChatReceiptOverride) -> Unit,
    onSignOut: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ChatFilter.ALL) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }

    val profiles = (profilesState as? UiState.Success)?.data.orEmpty()
    val otherProfiles = remember(profiles, currentUserId) {
        profiles.filter { it.userId != currentUserId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari chat atau grup...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_chats_input")
                        )
                    } else {
                        Column {
                            Text(
                                text = "BORDER CHAT",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "E2EE Aktif",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "End-to-End Encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Cari obrolan"
                        )
                    }
                    IconButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.testTag("new_group_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Buat grup chat baru"
                        )
                    }
                    IconButton(
                        onClick = { showPrivacySheet = true },
                        modifier = Modifier.testTag("privacy_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = "Privasi Laporan Dibaca",
                            tint = if (privacySettings.readReceiptsEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                EmeraldPrimary
                            }
                        )
                    }
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier.testTag("security_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Keamanan & Profil",
                            tint = EmeraldPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatSheet = true },
                containerColor = EmeraldPrimary,
                contentColor = Color(0xFF002119),
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "Mulai chat baru"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ChatFilter.entries) { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = onOpenSavedMessages,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Pesan Tersimpan") },
                        modifier = Modifier.testTag("saved_messages_chip")
                    )
                }
            }

            when (chatsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chatsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = remember(chatsState.data, filter, searchQuery, currentUserId) {
                        chatsState.data.filter { room ->
                            val matchesFilter = when (filter) {
                                ChatFilter.ALL -> true
                                ChatFilter.GROUPS -> room.chatTypeEnum() == ChatType.GROUP
                                ChatFilter.DIRECT -> room.chatTypeEnum() == ChatType.DIRECT
                            }
                            val q = searchQuery.trim()
                            val matchesQuery = q.isEmpty() ||
                                room.displayTitle(currentUserId).contains(q, ignoreCase = true)
                            matchesFilter && matchesQuery
                        }
                    }

                    if (filtered.isEmpty()) {
                        EmptyChatsView(
                            onNewChat = { showNewChatSheet = true },
                            onNewGroup = { showCreateGroupDialog = true },
                            onSavedMessages = onOpenSavedMessages
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { room ->
                                val isGroup = room.chatTypeEnum() == ChatType.GROUP
                                val otherUid = room.memberUids.firstOrNull { it != currentUserId }
                                val otherProfile = otherProfiles.firstOrNull { it.userId == otherUid }
                                val isSelected = room.id == selectedChatId
                                val anyOtherOnline = otherProfiles.any {
                                    it.userId in room.memberUids && it.userId != currentUserId && it.online
                                }
                                val lastMsgStatus = if (room.lastSenderId == currentUserId) {
                                    computeMessageReceiptStatus(
                                        createdAt = room.updatedAt ?: room.createdAt,
                                        senderId = currentUserId,
                                        memberUids = room.memberUids,
                                        readByUids = room.lastMessageReadByUids,
                                        readReceiptsVisible = privacySettings.isReadReceiptsEnabledFor(room),
                                        isAnyOtherMemberOnline = anyOtherOnline
                                    )
                                } else null

                                ChatListRow(
                                    title = room.displayTitle(currentUserId),
                                    subtitle = room.lastMessagePreview,
                                    timeLabel = formatTimeShort(room.updatedAt ?: room.createdAt),
                                    isGroup = isGroup,
                                    memberCount = room.memberUids.size,
                                    photoUrl = otherProfile?.photoUrl,
                                    isOnline = otherProfile?.online == true,
                                    selected = isSelected,
                                    lastMessageReceiptStatus = lastMsgStatus,
                                    isDark = isDark,
                                    onClick = { onOpenChat(room.id) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        ModalBottomSheet(onDismissRequest = { showNewChatSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Mulai Obrolan Terenkripsi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showNewChatSheet = false
                            showCreateGroupDialog = true
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color(0xFF002119))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Buat Grup Chat Baru", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tambah banyak anggota dengan kunci grup E2EE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showNewChatSheet = false
                            onOpenSavedMessages()
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = EmeraldPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Pesan Tersimpan (Anda)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ruang catatan & uji fitur E2EE pribadi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Kontak Terdaftar (${otherProfiles.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                if (otherProfiles.isEmpty()) {
                    Text(
                        text = "Belum ada kontak lain yang masuk. Gunakan 'Pesan Tersimpan' atau masuk dengan akun Google kedua di perangkat lain.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        items(otherProfiles, key = { it.userId }) { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showNewChatSheet = false
                                        onStartDirectChat(profile)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                            ) {
                                UserAvatar(
                                    name = profile.displayName,
                                    photoUrl = profile.photoUrl,
                                    isOnline = profile.online,
                                    size = 44.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = profile.statusText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = otherProfiles,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { groupTitle, selected ->
                showCreateGroupDialog = false
                onCreateGroup(groupTitle, selected)
            }
        )
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
            },
            title = { Text("Profil & Keamanan E2EE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = currentUserName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sidik Jari Kunci Identitas (ECDH P-256):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = myFingerprint,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Kunci privat dilindungi Android Keystore",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showSecurityDialog = false
                                showPrivacySheet = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Privasi Laporan Dibaca",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (privacySettings.readReceiptsEnabled) {
                                        "Centang biru aktif • Ketuk untuk atur detail"
                                    } else {
                                        "Centang biru dimatikan • Ketuk untuk atur"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSecurityDialog = false
                        onSignOut()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Keluar Akun")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showPrivacySheet) {
        val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
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
}

@Composable
private fun ChatListRow(
    title: String,
    subtitle: String,
    timeLabel: String,
    isGroup: Boolean,
    memberCount: Int,
    photoUrl: String?,
    isOnline: Boolean,
    selected: Boolean,
    lastMessageReceiptStatus: MessageReceiptStatus?,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        UserAvatar(
            name = title,
            photoUrl = photoUrl,
            isGroup = isGroup,
            isOnline = isOnline,
            size = 50.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isGroup) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "$memberCount",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lastMessageReceiptStatus != null) {
                    MessageReceiptTicks(
                        status = lastMessageReceiptStatus,
                        isDark = isDark,
                        size = 15.dp
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyChatsView(
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onSavedMessages: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = EmeraldBright,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Belum Ada Percakapan",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Mulai chat pribadi, buat grup chat baru, atau buka Pesan Tersimpan yang diamankan dengan enkripsi end-to-end.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSavedMessages, modifier = Modifier.testTag("empty_saved_msg_btn")) {
                Text("Pesan Tersimpan")
            }
            Button(onClick = onNewGroup, modifier = Modifier.testTag("empty_new_group_btn")) {
                Text("Buat Grup")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNewChat) {
            Text("Pilih dari Daftar Kontak")
        }
    }
}

@Composable
private fun CreateGroupDialog(
    contacts: List<PublicProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, List<PublicProfile>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Grup Terenkripsi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(80) },
                    label = { Text("Nama Grup") },
                    placeholder = { Text("Contoh: Tim Proyek Rahasia") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input")
                )
                Text(
                    text = "Pilih Anggota (${selectedIds.size} dipilih):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contacts.isEmpty()) {
                    Text(
                        text = "Anda tetap bisa membuat grup sekarang dan mengundang anggota saat akun lain terdaftar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(contacts, key = { it.userId }) { profile ->
                            val checked = selectedIds.contains(profile.userId)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedIds.remove(profile.userId)
                                        else selectedIds.add(profile.userId)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) selectedIds.add(profile.userId)
                                        else selectedIds.remove(profile.userId)
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(profile.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = contacts.filter { selectedIds.contains(it.userId) }
                    onCreate(groupName.trim(), chosen)
                },
                enabled = groupName.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_group_button")
            ) {
                Text("Buat Grup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
