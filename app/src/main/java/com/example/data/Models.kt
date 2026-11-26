package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ChatType {
    DIRECT, GROUP;
    companion object {
        fun fromRaw(raw: String): ChatType = entries.firstOrNull { it.name == raw } ?: DIRECT
    }
}

enum class MessageType {
    TEXT, IMAGE, VIDEO, LOCATION, LIVE_LOCATION, CALL_LOG, DELETED;
    companion object {
        fun fromRaw(raw: String): MessageType = entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class CallType {
    VOICE, VIDEO;
    companion object {
        fun fromRaw(raw: String): CallType = entries.firstOrNull { it.name == raw } ?: VOICE
    }
}

enum class CallStatus {
    RINGING, CONNECTED, ENDED, DECLINED;
    companion object {
        fun fromRaw(raw: String): CallStatus = entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

enum class ChatReceiptOverride {
    DEFAULT, ALWAYS_ON, ALWAYS_OFF;
    companion object {
        fun fromRaw(raw: String): ChatReceiptOverride =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

enum class MessageReceiptStatus {
    SENDING, SENT, DELIVERED, READ
}

@Keep
@IgnoreExtraProperties
data class ReadReceiptPrivacySettings(
    val readReceiptsEnabled: Boolean = true,
    val groupReadReceiptsEnabled: Boolean = true,
    val chatReadReceiptOverrides: Map<String, String> = emptyMap()
) {
    fun overrideForChat(chatId: String): ChatReceiptOverride {
        val raw = chatReadReceiptOverrides[chatId] ?: return ChatReceiptOverride.DEFAULT
        return ChatReceiptOverride.fromRaw(raw)
    }

    fun isReadReceiptsEnabledFor(chat: ChatRoom): Boolean {
        return when (overrideForChat(chat.id)) {
            ChatReceiptOverride.ALWAYS_ON -> true
            ChatReceiptOverride.ALWAYS_OFF -> false
            ChatReceiptOverride.DEFAULT -> {
                if (chat.chatTypeEnum() == ChatType.GROUP) groupReadReceiptsEnabled
                else readReceiptsEnabled
            }
        }
    }
}

fun computeMessageReceiptStatus(
    createdAt: Timestamp?,
    senderId: String,
    memberUids: List<String>,
    readByUids: List<String>,
    readReceiptsVisible: Boolean,
    isAnyOtherMemberOnline: Boolean
): MessageReceiptStatus {
    if (createdAt == null) return MessageReceiptStatus.SENDING
    val otherMembers = memberUids.filter { it != senderId }
    if (readReceiptsVisible) {
        val allRead = if (otherMembers.isEmpty()) {
            true
        } else {
            otherMembers.all { it in readByUids }
        }
        if (allRead) return MessageReceiptStatus.READ
    }
    return if (isAnyOtherMemberOnline || (readReceiptsVisible && otherMembers.any { it in readByUids })) {
        MessageReceiptStatus.DELIVERED
    } else {
        MessageReceiptStatus.SENT
    }
}

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val statusText: String = "",
    val publicKey: String = "",
    val online: Boolean = false,
    val discoverable: Boolean = true,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val type: String = ChatType.DIRECT.name,
    val title: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val encryptedKeys: Map<String, String> = emptyMap(),
    val lastMessagePreview: String = "",
    val lastSenderId: String? = null,
    val lastMessageId: String? = null,
    val lastMessageReadByUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun chatTypeEnum(): ChatType = ChatType.fromRaw(type)

    fun displayTitle(currentUid: String): String {
        if (chatTypeEnum() == ChatType.GROUP) return title.ifBlank { "Grup Chat" }
        val otherUid = memberUids.firstOrNull { it != currentUid }
        return if (otherUid != null) {
            memberNames[otherUid]?.ifBlank { title } ?: title
        } else {
            "Pesan Tersimpan (Anda)"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessageDto(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = MessageType.TEXT.name,
    val encryptedContent: String = "",
    val iv: String = "",
    val encryptionVersion: String = "AES-256-GCM",
    val encryptedThumb: String? = null,
    val thumbIv: String? = null,
    val readByUids: List<String> = emptyList(),
    val readBy: Map<String, Timestamp> = emptyMap(),
    val edited: Boolean = false,
    val deleted: Boolean = false,
    @ServerTimestamp val editedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveLocationDto(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val sharerId: String = "",
    val sharerName: String = "",
    val memberUids: List<String> = emptyList(),
    val encryptedCoords: String = "",
    val iv: String = "",
    val active: Boolean = false,
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val chatTitle: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = CallType.VOICE.name,
    val status: String = CallStatus.RINGING.name,
    val e2eeSafetyCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun callTypeEnum(): CallType = CallType.fromRaw(callType)
    fun callStatusEnum(): CallStatus = CallStatus.fromRaw(status)
}

// Decrypted UI domain models
data class DecryptedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 10f,
    val addressLabel: String = "",
    val durationMinutes: Int = 0,
    val expiresAtEpochMs: Long = 0L
)

data class DecryptedMedia(
    val caption: String = "",
    val mimeType: String = "image/jpeg",
    val base64Data: String = "",
    val thumbBase64: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val localFallbackPath: String = ""
)

data class DecryptedMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val messageType: MessageType,
    val text: String = "",
    val media: DecryptedMedia? = null,
    val location: DecryptedLocation? = null,
    val readByUids: List<String> = emptyList(),
    val readByTimestamps: Map<String, Timestamp> = emptyMap(),
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val decryptionFailed: Boolean = false,
    val encryptionVersion: String = "AES-256-GCM",
    val ciphertextPreview: String = "",
    val createdAt: Timestamp? = null,
    val editedAt: Timestamp? = null
)

data class DecryptedLiveLocation(
    val sharerId: String,
    val sharerName: String,
    val active: Boolean,
    val expiresAtEpochMs: Long,
    val location: DecryptedLocation?,
    val updatedAt: Timestamp?
)
