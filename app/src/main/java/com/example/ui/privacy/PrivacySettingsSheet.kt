package com.example.ui.privacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatReceiptOverride
import com.example.data.ChatRoom
import com.example.data.ChatType
import com.example.data.MessageReceiptStatus
import com.example.data.ReadReceiptPrivacySettings
import com.example.ui.components.MessageReceiptTicks
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DarkOutgoingBubble
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LightOutgoingBubble
import com.example.ui.theme.ReadReceiptBlueDark
import com.example.ui.theme.ReadReceiptBlueLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
    privacySettings: ReadReceiptPrivacySettings,
    chats: List<ChatRoom>,
    currentUserId: String,
    onToggleGlobalReadReceipts: (Boolean) -> Unit,
    onToggleGroupReadReceipts: (Boolean) -> Unit,
    onSetChatOverride: (String, ChatReceiptOverride) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val blueTint = if (isDark) ReadReceiptBlueDark else ReadReceiptBlueLight
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privasi Laporan Dibaca",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Atur centang biru secara global, khusus grup, atau per obrolan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Selesai")
                }
            }

            // Live Visual Preview Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pratinjau Status Pesan",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (privacySettings.readReceiptsEnabled) {
                                "Centang biru aktif — pengirim tahu saat Anda membaca pesan."
                            } else {
                                "Mode privat aktif — centang biru disembunyikan dua arah."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        color = if (isDark) DarkOutgoingBubble else LightOutgoingBubble,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 4.dp,
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "10:42",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            MessageReceiptTicks(
                                status = if (privacySettings.readReceiptsEnabled) {
                                    MessageReceiptStatus.READ
                                } else {
                                    MessageReceiptStatus.DELIVERED
                                },
                                isDark = isDark,
                                size = 15.dp
                            )
                        }
                    }
                }
            }

            // Global & Group Toggles
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (privacySettings.readReceiptsEnabled) blueTint else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Laporan Dibaca (Chat Pribadi)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Jika dimatikan, Anda tidak mengirim atau melihat centang biru di chat pribadi.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = privacySettings.readReceiptsEnabled,
                            onCheckedChange = onToggleGlobalReadReceipts,
                            modifier = Modifier.testTag("toggle_global_read_receipts")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = if (privacySettings.groupReadReceiptsEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Laporan Dibaca di Grup Chat",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Kontrol terpisah untuk mengirim dan melihat info siapa yang membaca pesan grup.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = privacySettings.groupReadReceiptsEnabled,
                            onCheckedChange = onToggleGroupReadReceipts,
                            modifier = Modifier.testTag("toggle_group_read_receipts")
                        )
                    }
                }
            }

            // Granular Per-Chat Overrides
            Text(
                text = "Pengaturan Khusus Per Obrolan (${chats.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (chats.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Mulai obrolan untuk mengatur privasi centang biru per kontak atau grup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(chats, key = { it.id }) { room ->
                        val override = privacySettings.overrideForChat(room.id)
                        val effectiveActive = privacySettings.isReadReceiptsEnabledFor(room)
                        val isGroup = room.chatTypeEnum() == ChatType.GROUP

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(
                                        name = room.displayTitle(currentUserId),
                                        isGroup = isGroup,
                                        size = 36.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = room.displayTitle(currentUserId),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (effectiveActive) Icons.Default.DoneAll else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = if (effectiveActive) blueTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (effectiveActive) "Centang biru aktif" else "Privat (tanda baca mati)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (effectiveActive) blueTint else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isGroup) Icons.Default.Groups else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                ChatReceiptOverrideSelector(
                                    selected = override,
                                    chatId = room.id,
                                    onSelect = { onSetChatOverride(room.id, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatReceiptOverrideSelector(
    selected: ChatReceiptOverride,
    chatId: String,
    onSelect: (ChatReceiptOverride) -> Unit
) {
    val options = listOf(
        ChatReceiptOverride.DEFAULT to "Default",
        ChatReceiptOverride.ALWAYS_ON to "Selalu Aktif",
        ChatReceiptOverride.ALWAYS_OFF to "Selalu Mati"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { (opt, label) ->
            val isSelected = selected == opt
            val containerColor = when {
                isSelected && opt == ChatReceiptOverride.ALWAYS_OFF -> MaterialTheme.colorScheme.errorContainer
                isSelected -> EmeraldPrimary.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }
            val textColor = when {
                isSelected && opt == ChatReceiptOverride.ALWAYS_OFF -> MaterialTheme.colorScheme.onErrorContainer
                isSelected -> EmeraldPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) textColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(opt) }
                    .testTag("override_${chatId}_${opt.name}")
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
