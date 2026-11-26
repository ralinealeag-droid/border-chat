package com.example.data

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID
import javax.crypto.SecretKey

class ChatRepository(
    private val db: FirebaseFirestore,
    val crypto: E2eeCryptoManager
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        E2eeCryptoManager(context.applicationContext)
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    private fun currentUserName(): String {
        return auth.currentUser?.displayName?.take(60)?.ifBlank { "Pengguna" } ?: "Pengguna"
    }

    fun observePrivacySettings(uid: String): Flow<ReadReceiptPrivacySettings> = flow {
        if (uid.isBlank()) return@flow
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) {
                        ReadReceiptPrivacySettings()
                    } else {
                        val globalEnabled = snap.getBoolean("readReceiptsEnabled") ?: true
                        val groupEnabled = snap.getBoolean("groupReadReceiptsEnabled") ?: true
                        val rawOverrides = snap.get("chatReadReceiptOverrides") as? Map<*, *>
                        val overrides = rawOverrides?.entries
                            ?.mapNotNull { (k, v) ->
                                val key = k as? String
                                val value = v as? String
                                if (key != null && value != null) key to value else null
                            }
                            ?.toMap()
                            .orEmpty()
                        ReadReceiptPrivacySettings(
                            readReceiptsEnabled = globalEnabled,
                            groupReadReceiptsEnabled = groupEnabled,
                            chatReadReceiptOverrides = overrides
                        )
                    }
                }
        )
    }

    suspend fun updatePrivacySettings(
        readReceiptsEnabled: Boolean? = null,
        groupReadReceiptsEnabled: Boolean? = null,
        chatOverrides: Map<String, String>? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (readReceiptsEnabled != null) {
            updates["readReceiptsEnabled"] = readReceiptsEnabled
        }
        if (groupReadReceiptsEnabled != null) {
            updates["groupReadReceiptsEnabled"] = groupReadReceiptsEnabled
        }
        if (chatOverrides != null) {
            updates["chatReadReceiptOverrides"] = chatOverrides
                .filterValues { it != ChatReceiptOverride.DEFAULT.name }
                .entries
                .take(200)
                .associate { it.key to it.value }
        }
        return try {
            userRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    fun observePublicProfiles(): Flow<List<PublicProfile>> = flow {
        requireUserId()
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
        )
    }

    fun observeChats(uid: String): Flow<List<ChatRoom>> = flow {
        if (uid.isBlank()) return@flow
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
        )
    }

    fun observeMessages(chat: ChatRoom, uid: String): Flow<List<DecryptedMessage>> = flow {
        if (uid.isBlank() || chat.id.isBlank()) return@flow
        val roomKey = resolveRoomKey(chat, uid)
        emitAll(
            db.collection("chats").document(chat.id)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessageDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                        .map { dto -> decryptMessage(dto, roomKey) }
                }
        )
    }

    fun observeLiveLocations(chat: ChatRoom, uid: String): Flow<List<DecryptedLiveLocation>> = flow {
        if (uid.isBlank() || chat.id.isBlank()) return@flow
        val roomKey = resolveRoomKey(chat, uid)
        emitAll(
            db.collection("chats").document(chat.id)
                .collection("liveLocations")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    val nowMs = System.currentTimeMillis()
                    snap.toObjects(LiveLocationDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.active && it.expiresAtEpochMs > nowMs }
                        .map { dto ->
                            val loc = if (roomKey != null) {
                                crypto.decryptString(dto.encryptedCoords, dto.iv, roomKey)?.let { parseLocationJson(it) }
                            } else null
                            DecryptedLiveLocation(
                                sharerId = dto.sharerId,
                                sharerName = dto.sharerName,
                                active = dto.active,
                                expiresAtEpochMs = dto.expiresAtEpochMs,
                                location = loc,
                                updatedAt = dto.updatedAt
                            )
                        }
                }
        )
    }

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        if (uid.isBlank()) return@flow
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
        )
    }

    fun resolveRoomKey(chat: ChatRoom, uid: String): SecretKey? {
        val envelope = chat.encryptedKeys[uid] ?: return null
        return crypto.unwrapRoomKey(uid, envelope)
    }

    suspend fun getOrCreateDirectChat(other: PublicProfile): Result<ChatRoom> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val sortedIds = listOf(uid, other.userId).distinct().sorted()
        val chatId = if (sortedIds.size == 1) {
            "self_${uid.take(40)}"
        } else {
            "dm_${sortedIds[0].take(28)}_${sortedIds[1].take(28)}"
        }.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

        val docRef = db.collection("chats").document(chatId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val room = existing.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Gagal membaca ruang obrolan.")
                return Result.success(room)
            }

            val myName = currentUserName()
            val myPubKey = crypto.getPublicKeyBase64(uid)
            val roomKey = crypto.generateRoomAesKey()
            val keysMap = mutableMapOf<String, String>()
            keysMap[uid] = crypto.wrapRoomKeyForRecipient(uid, roomKey, myPubKey)
            if (other.userId != uid && other.publicKey.isNotBlank()) {
                keysMap[other.userId] = crypto.wrapRoomKeyForRecipient(uid, roomKey, other.publicKey)
            }

            val memberNames = mutableMapOf(uid to myName)
            if (other.userId != uid) {
                memberNames[other.userId] = other.displayName.take(60).ifBlank { "Kontak" }
            }

            val memberList = sortedIds
            val title = if (other.userId == uid) "Pesan Tersimpan (Anda)" else other.displayName.take(80)

            val payload = mapOf(
                "type" to ChatType.DIRECT.name,
                "title" to title,
                "memberUids" to memberList,
                "adminUids" to memberList,
                "memberNames" to memberNames,
                "encryptedKeys" to keysMap,
                "lastMessagePreview" to "Enkripsi end-to-end aktif",
                "lastMessageReadByUids" to emptyList<String>(),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatRoom(
                    id = chatId,
                    type = ChatType.DIRECT.name,
                    title = title,
                    memberUids = memberList,
                    adminUids = memberList,
                    memberNames = memberNames,
                    encryptedKeys = keysMap,
                    lastMessagePreview = "Enkripsi end-to-end aktif",
                    createdBy = uid
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(title: String, selectedMembers: List<PublicProfile>): Result<ChatRoom> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nama grup tidak boleh kosong."))
        }

        val chatId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("chats").document(chatId)
        return try {
            val myName = currentUserName()
            val myPubKey = crypto.getPublicKeyBase64(uid)
            val roomKey = crypto.generateRoomAesKey()

            val memberUids = (listOf(uid) + selectedMembers.map { it.userId }).distinct().take(50)
            val memberNames = mutableMapOf(uid to myName)
            val keysMap = mutableMapOf(uid to crypto.wrapRoomKeyForRecipient(uid, roomKey, myPubKey))

            selectedMembers.take(49).forEach { profile ->
                memberNames[profile.userId] = profile.displayName.take(60).ifBlank { "Anggota" }
                if (profile.publicKey.isNotBlank()) {
                    keysMap[profile.userId] = crypto.wrapRoomKeyForRecipient(uid, roomKey, profile.publicKey)
                }
            }

            val payload = mapOf(
                "type" to ChatType.GROUP.name,
                "title" to cleanTitle,
                "memberUids" to memberUids,
                "adminUids" to listOf(uid),
                "memberNames" to memberNames,
                "encryptedKeys" to keysMap,
                "lastMessagePreview" to "Grup terenkripsi dibuat",
                "lastSenderId" to uid,
                "lastMessageReadByUids" to listOf(uid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            val room = docRef.get().await()
                .toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatRoom(
                    id = chatId,
                    type = ChatType.GROUP.name,
                    title = cleanTitle,
                    memberUids = memberUids,
                    adminUids = listOf(uid),
                    memberNames = memberNames,
                    encryptedKeys = keysMap,
                    lastMessagePreview = "Grup terenkripsi dibuat",
                    createdBy = uid
                )
            Result.success(room)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(chat: ChatRoom, text: String): Result<Unit> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Pesan kosong"))
        val json = JSONObject().apply { put("text", clean) }.toString()
        return sendEncryptedMessage(
            chat = chat,
            messageType = MessageType.TEXT,
            plaintextJson = json,
            previewLabel = "🔒 Pesan terenkripsi"
        )
    }

    suspend fun sendImageMessage(chat: ChatRoom, jpegBytes: ByteArray, caption: String): Result<Unit> {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("caption", caption.trim())
            put("mimeType", "image/jpeg")
            put("data", b64)
            put("sizeBytes", jpegBytes.size.toLong())
        }.toString()
        return sendEncryptedMessage(
            chat = chat,
            messageType = MessageType.IMAGE,
            plaintextJson = json,
            previewLabel = "📷 Foto terenkripsi"
        )
    }

    suspend fun sendVideoMessage(
        chat: ChatRoom,
        thumbBytes: ByteArray,
        videoCloudBytes: ByteArray?,
        localPath: String,
        durationMs: Long,
        totalSizeBytes: Long,
        caption: String
    ): Result<Unit> {
        val videoB64 = if (videoCloudBytes != null) Base64.encodeToString(videoCloudBytes, Base64.NO_WRAP) else ""
        val thumbB64 = if (thumbBytes.isNotEmpty()) Base64.encodeToString(thumbBytes, Base64.NO_WRAP) else ""
        val json = JSONObject().apply {
            put("caption", caption.trim())
            put("mimeType", "video/mp4")
            put("data", videoB64)
            put("durationMs", durationMs)
            put("sizeBytes", totalSizeBytes)
            put("localFallbackPath", localPath)
        }.toString()
        return sendEncryptedMessage(
            chat = chat,
            messageType = MessageType.VIDEO,
            plaintextJson = json,
            previewLabel = "🎥 Video terenkripsi",
            thumbPlaintext = thumbB64.ifBlank { null }
        )
    }

    suspend fun sendLocationMessage(chat: ChatRoom, location: DecryptedLocation, isLive: Boolean): Result<Unit> {
        val json = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("acc", location.accuracyMeters.toDouble())
            put("addr", location.addressLabel)
            put("dur", location.durationMinutes)
            put("exp", location.expiresAtEpochMs)
        }.toString()
        val type = if (isLive) MessageType.LIVE_LOCATION else MessageType.LOCATION
        val preview = if (isLive) "📡 Berbagi lokasi realtime" else "📍 Lokasi terkini"
        return sendEncryptedMessage(
            chat = chat,
            messageType = type,
            plaintextJson = json,
            previewLabel = preview
        )
    }

    suspend fun sendCallLogMessage(chat: ChatRoom, summaryText: String): Result<Unit> {
        val json = JSONObject().apply { put("text", summaryText) }.toString()
        return sendEncryptedMessage(
            chat = chat,
            messageType = MessageType.CALL_LOG,
            plaintextJson = json,
            previewLabel = summaryText.take(180)
        )
    }

    private suspend fun sendEncryptedMessage(
        chat: ChatRoom,
        messageType: MessageType,
        plaintextJson: String,
        previewLabel: String,
        thumbPlaintext: String? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val roomKey = resolveRoomKey(chat, uid)
            ?: return Result.failure(IllegalStateException("Kunci enkripsi ruang obrolan tidak ditemukan."))

        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document(msgId)
        return try {
            val encMain = crypto.encryptString(plaintextJson, roomKey)
            val encThumb = if (!thumbPlaintext.isNullOrBlank()) crypto.encryptString(thumbPlaintext, roomKey) else null

            val payload = mutableMapOf<String, Any?>(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to currentUserName(),
                "memberUids" to chat.memberUids,
                "messageType" to messageType.name,
                "encryptedContent" to encMain.ciphertextBase64,
                "iv" to encMain.ivBase64,
                "encryptionVersion" to encMain.version,
                "encryptedThumb" to encThumb?.ciphertextBase64,
                "thumbIv" to encThumb?.ivBase64,
                "readByUids" to listOf(uid),
                "readBy" to mapOf(uid to FieldValue.serverTimestamp()),
                "edited" to false,
                "deleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            msgRef.set(payload).await()

            db.collection("chats").document(chat.id).update(
                mapOf(
                    "lastMessagePreview" to previewLabel.take(200),
                    "lastSenderId" to uid,
                    "lastMessageId" to msgId,
                    "lastMessageReadByUids" to listOf(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsRead(chat: ChatRoom, messageIds: List<String>): Result<Unit> {
        if (messageIds.isEmpty()) return Result.success(Unit)
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val chatRef = db.collection("chats").document(chat.id)
        return try {
            val chunks = messageIds.distinct().chunked(400)
            chunks.forEachIndexed { index, chunk ->
                val batch = db.batch()
                chunk.forEach { msgId ->
                    val msgRef = chatRef.collection("messages").document(msgId)
                    batch.update(
                        msgRef,
                        mapOf(
                            "readByUids" to FieldValue.arrayUnion(uid),
                            "readBy.$uid" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                val isLastChunk = index == chunks.lastIndex
                if (isLastChunk &&
                    chat.lastMessageId != null &&
                    chat.lastMessageId in messageIds &&
                    uid !in chat.lastMessageReadByUids
                ) {
                    batch.update(
                        chatRef,
                        mapOf(
                            "lastMessageReadByUids" to FieldValue.arrayUnion(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "${chatRef.path}/messages")
            Result.failure(e)
        }
    }

    suspend fun editMessage(chat: ChatRoom, messageId: String, newText: String): Result<Unit> {
        val clean = newText.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Pesan tidak boleh kosong."))
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val roomKey = resolveRoomKey(chat, uid)
            ?: return Result.failure(IllegalStateException("Kunci enkripsi tidak tersedia."))
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document(messageId)
        return try {
            val json = JSONObject().apply { put("text", clean) }.toString()
            val enc = crypto.encryptString(json, roomKey)
            msgRef.update(
                mapOf(
                    "encryptedContent" to enc.ciphertextBase64,
                    "iv" to enc.ivBase64,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(chat: ChatRoom, messageId: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "encryptedContent" to "",
                    "iv" to "",
                    "encryptedThumb" to null,
                    "thumbIv" to null,
                    "messageType" to MessageType.DELETED.name,
                    "deleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateLiveLocation(
        chat: ChatRoom,
        location: DecryptedLocation?,
        active: Boolean,
        expiresAtEpochMs: Long
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val roomKey = resolveRoomKey(chat, uid)
            ?: return Result.failure(IllegalStateException("Kunci enkripsi tidak tersedia."))
        val docRef = db.collection("chats").document(chat.id).collection("liveLocations").document(uid)
        return try {
            val json = if (location != null) {
                JSONObject().apply {
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("acc", location.accuracyMeters.toDouble())
                    put("addr", location.addressLabel)
                    put("exp", expiresAtEpochMs)
                }.toString()
            } else "{}"
            val enc = crypto.encryptString(json, roomKey)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "chatId" to chat.id,
                        "sharerId" to uid,
                        "sharerName" to currentUserName(),
                        "memberUids" to chat.memberUids,
                        "encryptedCoords" to enc.ciphertextBase64,
                        "iv" to enc.ivBase64,
                        "active" to active,
                        "expiresAtEpochMs" to expiresAtEpochMs,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "encryptedCoords" to enc.ciphertextBase64,
                        "iv" to enc.ivBase64,
                        "active" to active,
                        "expiresAtEpochMs" to expiresAtEpochMs,
                        "memberUids" to chat.memberUids,
                        "sharerName" to currentUserName(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun startCall(chat: ChatRoom, callType: CallType, safetyCode: String): Result<CallSession> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val docRef = db.collection("calls").document(callId)
        val title = chat.displayTitle(uid).take(80)
        return try {
            val payload = mapOf(
                "chatId" to chat.id,
                "chatTitle" to title,
                "callerId" to uid,
                "callerName" to currentUserName(),
                "participantUids" to chat.memberUids,
                "callType" to callType.name,
                "status" to CallStatus.RINGING.name,
                "e2eeSafetyCode" to safetyCode.take(64),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val session = CallSession(
                id = callId,
                chatId = chat.id,
                chatTitle = title,
                callerId = uid,
                callerName = currentUserName(),
                participantUids = chat.memberUids,
                callType = callType.name,
                status = CallStatus.RINGING.name,
                e2eeSafetyCode = safetyCode
            )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, status: CallStatus): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("calls").document(callId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun decryptMessage(dto: ChatMessageDto, roomKey: SecretKey?): DecryptedMessage {
        val type = MessageType.fromRaw(dto.messageType)
        if (dto.deleted || type == MessageType.DELETED) {
            return DecryptedMessage(
                id = dto.id,
                chatId = dto.chatId,
                senderId = dto.senderId,
                senderName = dto.senderName,
                messageType = MessageType.DELETED,
                text = "Pesan ini telah dihapus",
                deleted = true,
                encryptionVersion = dto.encryptionVersion,
                createdAt = dto.createdAt,
                editedAt = dto.editedAt
            )
        }

        if (roomKey == null) {
            return DecryptedMessage(
                id = dto.id,
                chatId = dto.chatId,
                senderId = dto.senderId,
                senderName = dto.senderName,
                messageType = type,
                text = "🔒 Pesan terenkripsi (kunci perangkat tidak tersedia)",
                readByUids = dto.readByUids,
                readByTimestamps = dto.readBy,
                decryptionFailed = true,
                encryptionVersion = dto.encryptionVersion,
                ciphertextPreview = dto.encryptedContent.take(36),
                createdAt = dto.createdAt
            )
        }

        val plainJson = crypto.decryptString(dto.encryptedContent, dto.iv, roomKey)
        if (plainJson == null) {
            return DecryptedMessage(
                id = dto.id,
                chatId = dto.chatId,
                senderId = dto.senderId,
                senderName = dto.senderName,
                messageType = type,
                text = "🔒 Gagal mendekripsi pesan",
                readByUids = dto.readByUids,
                readByTimestamps = dto.readBy,
                decryptionFailed = true,
                encryptionVersion = dto.encryptionVersion,
                ciphertextPreview = dto.encryptedContent.take(36),
                createdAt = dto.createdAt
            )
        }

        return try {
            val obj = JSONObject(plainJson)
            when (type) {
                MessageType.TEXT, MessageType.CALL_LOG -> DecryptedMessage(
                    id = dto.id,
                    chatId = dto.chatId,
                    senderId = dto.senderId,
                    senderName = dto.senderName,
                    messageType = type,
                    text = obj.optString("text", ""),
                    readByUids = dto.readByUids,
                    readByTimestamps = dto.readBy,
                    edited = dto.edited,
                    encryptionVersion = dto.encryptionVersion,
                    ciphertextPreview = dto.encryptedContent.take(36),
                    createdAt = dto.createdAt,
                    editedAt = dto.editedAt
                )
                MessageType.IMAGE -> DecryptedMessage(
                    id = dto.id,
                    chatId = dto.chatId,
                    senderId = dto.senderId,
                    senderName = dto.senderName,
                    messageType = type,
                    text = obj.optString("caption", ""),
                    media = DecryptedMedia(
                        caption = obj.optString("caption", ""),
                        mimeType = obj.optString("mimeType", "image/jpeg"),
                        base64Data = obj.optString("data", ""),
                        sizeBytes = obj.optLong("sizeBytes", 0L)
                    ),
                    readByUids = dto.readByUids,
                    readByTimestamps = dto.readBy,
                    edited = dto.edited,
                    encryptionVersion = dto.encryptionVersion,
                    ciphertextPreview = dto.encryptedContent.take(36),
                    createdAt = dto.createdAt,
                    editedAt = dto.editedAt
                )
                MessageType.VIDEO -> {
                    val thumb = if (!dto.encryptedThumb.isNullOrBlank() && !dto.thumbIv.isNullOrBlank()) {
                        crypto.decryptString(dto.encryptedThumb, dto.thumbIv, roomKey) ?: ""
                    } else ""
                    DecryptedMessage(
                        id = dto.id,
                        chatId = dto.chatId,
                        senderId = dto.senderId,
                        senderName = dto.senderName,
                        messageType = type,
                        text = obj.optString("caption", ""),
                        media = DecryptedMedia(
                            caption = obj.optString("caption", ""),
                            mimeType = obj.optString("mimeType", "video/mp4"),
                            base64Data = obj.optString("data", ""),
                            thumbBase64 = thumb,
                            durationMs = obj.optLong("durationMs", 0L),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            localFallbackPath = obj.optString("localFallbackPath", "")
                        ),
                        readByUids = dto.readByUids,
                        readByTimestamps = dto.readBy,
                        edited = dto.edited,
                        encryptionVersion = dto.encryptionVersion,
                        ciphertextPreview = dto.encryptedContent.take(36),
                        createdAt = dto.createdAt,
                        editedAt = dto.editedAt
                    )
                }
                MessageType.LOCATION, MessageType.LIVE_LOCATION -> DecryptedMessage(
                    id = dto.id,
                    chatId = dto.chatId,
                    senderId = dto.senderId,
                    senderName = dto.senderName,
                    messageType = type,
                    location = DecryptedLocation(
                        latitude = obj.optDouble("lat", 0.0),
                        longitude = obj.optDouble("lng", 0.0),
                        accuracyMeters = obj.optDouble("acc", 10.0).toFloat(),
                        addressLabel = obj.optString("addr", ""),
                        durationMinutes = obj.optInt("dur", 0),
                        expiresAtEpochMs = obj.optLong("exp", 0L)
                    ),
                    readByUids = dto.readByUids,
                    readByTimestamps = dto.readBy,
                    edited = dto.edited,
                    encryptionVersion = dto.encryptionVersion,
                    ciphertextPreview = dto.encryptedContent.take(36),
                    createdAt = dto.createdAt,
                    editedAt = dto.editedAt
                )
                MessageType.DELETED -> DecryptedMessage(
                    id = dto.id,
                    chatId = dto.chatId,
                    senderId = dto.senderId,
                    senderName = dto.senderName,
                    messageType = MessageType.DELETED,
                    text = "Pesan ini telah dihapus",
                    readByUids = dto.readByUids,
                    readByTimestamps = dto.readBy,
                    deleted = true,
                    createdAt = dto.createdAt
                )
            }
        } catch (_: Exception) {
            DecryptedMessage(
                id = dto.id,
                chatId = dto.chatId,
                senderId = dto.senderId,
                senderName = dto.senderName,
                messageType = type,
                text = plainJson,
                readByUids = dto.readByUids,
                readByTimestamps = dto.readBy,
                createdAt = dto.createdAt
            )
        }
    }

    private fun parseLocationJson(json: String): DecryptedLocation? {
        return try {
            val obj = JSONObject(json)
            if (!obj.has("lat") || !obj.has("lng")) return null
            DecryptedLocation(
                latitude = obj.getDouble("lat"),
                longitude = obj.getDouble("lng"),
                accuracyMeters = obj.optDouble("acc", 10.0).toFloat(),
                addressLabel = obj.optString("addr", ""),
                expiresAtEpochMs = obj.optLong("exp", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}
