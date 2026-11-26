package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.ChatReceiptOverride
import com.example.data.ChatRepository
import com.example.data.ChatRoom
import com.example.data.DecryptedLiveLocation
import com.example.data.DecryptedLocation
import com.example.data.DecryptedMessage
import com.example.data.PublicProfile
import com.example.data.ReadReceiptPrivacySettings
import com.example.data.UiState
import com.example.location.LocationHelper
import com.example.media.MediaHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val locationHelper: LocationHelper,
    val currentUserId: String
) : ViewModel() {

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)

    private val _liveSharingChatId = MutableStateFlow<String?>(null)
    val liveSharingChatId: StateFlow<String?> = _liveSharingChatId.asStateFlow()

    private val _liveSharingExpiresAtMs = MutableStateFlow(0L)
    val liveSharingExpiresAtMs: StateFlow<Long> = _liveSharingExpiresAtMs.asStateFlow()

    private var liveLocationJob: Job? = null

    private val inFlightReadReceiptIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                authRepository.syncSignedInProfile(user)
            }
        }
    }

    val privacySettingsState: StateFlow<ReadReceiptPrivacySettings> =
        chatRepository.observePrivacySettings(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(ReadReceiptPrivacySettings())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ReadReceiptPrivacySettings()
            )

    val chatsState: StateFlow<UiState<List<ChatRoom>>> =
        chatRepository.observeChats(currentUserId)
            .toUiStateFlow("observeChats")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val profilesState: StateFlow<UiState<List<PublicProfile>>> =
        chatRepository.observePublicProfiles()
            .toUiStateFlow("observeProfiles")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val selectedChat: StateFlow<ChatRoom?> =
        kotlinx.coroutines.flow.combine(chatsState, _selectedChatId) { state, selectedId ->
            if (selectedId == null) null
            else (state as? UiState.Success)?.data?.firstOrNull { it.id == selectedId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val messagesState: StateFlow<UiState<List<DecryptedMessage>>> =
        selectedChat.flatMapLatest { chat ->
            if (chat == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                chatRepository.observeMessages(chat, currentUserId)
                    .toUiStateFlow("observeMessages")
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val liveLocationsState: StateFlow<List<DecryptedLiveLocation>> =
        selectedChat.flatMapLatest { chat ->
            if (chat == null) {
                flowOf(emptyList())
            } else {
                chatRepository.observeLiveLocations(chat, currentUserId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val allCallsState: StateFlow<List<CallSession>> =
        chatRepository.observeCalls(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val activeCall: StateFlow<CallSession?> =
        kotlinx.coroutines.flow.combine(allCallsState, _activeCallId) { calls, activeId ->
            if (activeId == null) null
            else calls.firstOrNull {
                it.id == activeId &&
                    (it.callStatusEnum() == CallStatus.RINGING || it.callStatusEnum() == CallStatus.CONNECTED)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val incomingCall: StateFlow<CallSession?> =
        kotlinx.coroutines.flow.combine(allCallsState, _activeCallId) { calls, activeId ->
            if (activeId != null) null
            else calls.firstOrNull {
                it.callerId != currentUserId &&
                    it.callStatusEnum() == CallStatus.RINGING
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun openOrCreateDirectChat(profile: PublicProfile, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            chatRepository.getOrCreateDirectChat(profile)
                .onSuccess { room ->
                    _selectedChatId.value = room.id
                    onOpened(room.id)
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Gagal membuka obrolan."
                }
            _isBusy.value = false
        }
    }

    fun openSavedMessages(onOpened: (String) -> Unit) {
        val user = Firebase.auth.currentUser ?: return
        val selfProfile = PublicProfile(
            userId = user.uid,
            displayName = user.displayName ?: "Anda",
            publicKey = chatRepository.crypto.getPublicKeyBase64(user.uid)
        )
        openOrCreateDirectChat(selfProfile, onOpened)
    }

    fun createGroupChat(title: String, members: List<PublicProfile>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            chatRepository.createGroupChat(title, members)
                .onSuccess { room ->
                    _selectedChatId.value = room.id
                    _statusBanner.value = "Grup \"${room.title}\" dibuat dengan E2EE aktif."
                    onCreated(room.id)
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Gagal membuat grup."
                }
            _isBusy.value = false
        }
    }

    fun sendText(text: String) {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            chatRepository.sendTextMessage(chat, text)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Gagal mengirim pesan." }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            chatRepository.editMessage(chat, messageId, newText)
                .onSuccess { _statusBanner.value = "Pesan diperbarui & dienkripsi ulang." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Gagal mengedit pesan." }
        }
    }

    fun setGlobalReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updatePrivacySettings(readReceiptsEnabled = enabled)
                .onSuccess {
                    _statusBanner.value = if (enabled) {
                        "Laporan dibaca (centang biru) diaktifkan."
                    } else {
                        "Laporan dibaca (centang biru) dimatikan untuk privasi."
                    }
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Gagal memperbarui pengaturan privasi."
                }
        }
    }

    fun setGroupReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updatePrivacySettings(groupReadReceiptsEnabled = enabled)
                .onSuccess {
                    _statusBanner.value = if (enabled) {
                        "Laporan dibaca di grup chat diaktifkan."
                    } else {
                        "Laporan dibaca di grup chat dimatikan."
                    }
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Gagal memperbarui pengaturan privasi grup."
                }
        }
    }

    fun setChatReadReceiptOverride(chatId: String, override: ChatReceiptOverride) {
        viewModelScope.launch {
            val updatedMap = privacySettingsState.value.chatReadReceiptOverrides.toMutableMap()
            if (override == ChatReceiptOverride.DEFAULT) {
                updatedMap.remove(chatId)
            } else {
                updatedMap[chatId] = override.name
            }
            chatRepository.updatePrivacySettings(chatOverrides = updatedMap)
                .onSuccess {
                    val label = when (override) {
                        ChatReceiptOverride.DEFAULT -> "mengikuti pengaturan default"
                        ChatReceiptOverride.ALWAYS_ON -> "selalu aktif (centang biru)"
                        ChatReceiptOverride.ALWAYS_OFF -> "selalu dimatikan (privat)"
                    }
                    _statusBanner.value = "Laporan dibaca obrolan ini diatur: $label."
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Gagal menyimpan aturan privasi obrolan."
                }
        }
    }

    fun markChatMessagesAsRead(chat: ChatRoom, messages: List<DecryptedMessage>) {
        if (!privacySettingsState.value.isReadReceiptsEnabledFor(chat)) return
        val pendingIds = messages.asSequence()
            .filter { !it.deleted && it.senderId != currentUserId && currentUserId !in it.readByUids }
            .map { it.id }
            .filter { it !in inFlightReadReceiptIds }
            .toList()
        if (pendingIds.isEmpty()) return

        inFlightReadReceiptIds.addAll(pendingIds)
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(chat, pendingIds)
                .onFailure {
                    inFlightReadReceiptIds.removeAll(pendingIds.toSet())
                }
        }
    }

    fun deleteMessage(messageId: String) {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            chatRepository.deleteMessage(chat, messageId)
                .onSuccess { _statusBanner.value = "Pesan dihapus untuk semua." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Gagal menghapus pesan." }
        }
    }

    fun sendImage(context: Context, uri: Uri, caption: String = "") {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val prepared = MediaHelper.prepareImageFromUri(context, uri)
            if (prepared == null) {
                _statusBanner.value = "Gagal membaca gambar yang dipilih."
                _isBusy.value = false
                return@launch
            }
            chatRepository.sendImageMessage(chat, prepared.jpegBytes, caption)
                .onSuccess { _statusBanner.value = "Foto terenkripsi terkirim." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Gagal mengirim gambar." }
            _isBusy.value = false
        }
    }

    fun sendVideo(context: Context, uri: Uri, caption: String = "") {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val prepared = MediaHelper.prepareVideoFromUri(context, uri)
            if (prepared == null) {
                _statusBanner.value = "Gagal memproses video yang dipilih."
                _isBusy.value = false
                return@launch
            }
            chatRepository.sendVideoMessage(
                chat = chat,
                thumbBytes = prepared.thumbJpegBytes,
                videoCloudBytes = prepared.videoBytesForCloud,
                localPath = prepared.localVideoPath,
                durationMs = prepared.durationMs,
                totalSizeBytes = prepared.totalSizeBytes,
                caption = caption
            ).onSuccess {
                _statusBanner.value = if (prepared.videoBytesForCloud != null) {
                    "Video terenkripsi terkirim."
                } else {
                    "Preview video terenkripsi terkirim (ukuran video lokal > 390 KB)."
                }
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Gagal mengirim video."
            }
            _isBusy.value = false
        }
    }

    fun sendCurrentLocation() {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val loc = locationHelper.getCurrentLocation()
            if (loc == null) {
                _statusBanner.value = "Tidak dapat memperoleh GPS saat ini. Pastikan layanan lokasi aktif."
                _isBusy.value = false
                return@launch
            }
            chatRepository.sendLocationMessage(chat, loc, isLive = false)
                .onSuccess { _statusBanner.value = "Lokasi terkini terenkripsi terkirim." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Gagal mengirim lokasi." }
            _isBusy.value = false
        }
    }

    fun startRealtimeLocationSharing(durationMinutes: Int) {
        val chat = selectedChat.value ?: return
        liveLocationJob?.cancel()

        val expiresAt = System.currentTimeMillis() + durationMinutes * 60_000L
        _liveSharingChatId.value = chat.id
        _liveSharingExpiresAtMs.value = expiresAt

        liveLocationJob = viewModelScope.launch {
            _isBusy.value = true
            val initialLoc = locationHelper.getCurrentLocation()
            if (initialLoc == null) {
                _statusBanner.value = "Gagal memulai lokasi realtime. Pastikan GPS perangkat aktif."
                _liveSharingChatId.value = null
                _isBusy.value = false
                return@launch
            }

            val enrichedInitial = initialLoc.copy(
                durationMinutes = durationMinutes,
                expiresAtEpochMs = expiresAt
            )
            chatRepository.sendLocationMessage(chat, enrichedInitial, isLive = true)
            chatRepository.updateLiveLocation(chat, enrichedInitial, active = true, expiresAtEpochMs = expiresAt)
            _isBusy.value = false
            _statusBanner.value = "Berbagi lokasi realtime aktif ($durationMinutes menit)."

            val expiryWatchJob = launch {
                val remaining = expiresAt - System.currentTimeMillis()
                if (remaining > 0) delay(remaining)
                stopRealtimeLocationSharing()
            }

            try {
                locationHelper.observeRealtimeLocation(intervalMs = 4500L).collect { update ->
                    if (System.currentTimeMillis() >= expiresAt) {
                        stopRealtimeLocationSharing()
                    } else {
                        chatRepository.updateLiveLocation(
                            chat = chat,
                            location = update.copy(expiresAtEpochMs = expiresAt),
                            active = true,
                            expiresAtEpochMs = expiresAt
                        )
                    }
                }
            } finally {
                expiryWatchJob.cancel()
            }
        }
    }

    fun stopRealtimeLocationSharing() {
        val activeChatId = _liveSharingChatId.value
        val chat = (chatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == activeChatId }
            ?: selectedChat.value
        liveLocationJob?.cancel()
        liveLocationJob = null
        _liveSharingChatId.value = null
        _liveSharingExpiresAtMs.value = 0L

        if (chat != null) {
            viewModelScope.launch {
                chatRepository.updateLiveLocation(
                    chat = chat,
                    location = null,
                    active = false,
                    expiresAtEpochMs = 0L
                )
                _statusBanner.value = "Berbagi lokasi realtime dihentikan."
            }
        }
    }

    fun startCall(callType: CallType) {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            val safetyCode = computeShortCallSafetyCode(chat)
            chatRepository.startCall(chat, callType, safetyCode)
                .onSuccess { session ->
                    _activeCallId.value = session.id
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Gagal memulai panggilan."
                }
        }
    }

    fun acceptIncomingCall(call: CallSession) {
        _activeCallId.value = call.id
        viewModelScope.launch {
            chatRepository.updateCallStatus(call.id, CallStatus.CONNECTED)
        }
    }

    fun declineIncomingCall(call: CallSession) {
        viewModelScope.launch {
            chatRepository.updateCallStatus(call.id, CallStatus.DECLINED)
        }
    }

    fun endActiveCall(call: CallSession, durationSeconds: Int) {
        _activeCallId.value = null
        viewModelScope.launch {
            chatRepository.updateCallStatus(call.id, CallStatus.ENDED)
            val chat = (chatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == call.chatId }
            if (chat != null) {
                val label = if (call.callTypeEnum() == CallType.VIDEO) "Video Call" else "Panggilan Suara"
                val formattedDur = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60)
                chatRepository.sendCallLogMessage(chat, "📞 $label E2EE selesai ($formattedDur)")
            }
        }
    }

    fun computeSafetyNumberForChat(chat: ChatRoom): String {
        val profiles = (profilesState.value as? UiState.Success)?.data.orEmpty()
        val keys = chat.memberUids.mapNotNull { uid ->
            profiles.firstOrNull { it.userId == uid }?.publicKey
                ?: if (uid == currentUserId) chatRepository.crypto.getPublicKeyBase64(uid) else null
        }
        return chatRepository.crypto.computeSafetyNumber(keys.ifEmpty { listOf(chat.id) })
    }

    private fun computeShortCallSafetyCode(chat: ChatRoom): String {
        val profiles = (profilesState.value as? UiState.Success)?.data.orEmpty()
        val keys = chat.memberUids.mapNotNull { uid ->
            profiles.firstOrNull { it.userId == uid }?.publicKey
                ?: if (uid == currentUserId) chatRepository.crypto.getPublicKeyBase64(uid) else null
        }
        return chatRepository.crypto.computeShortCallCode(keys, chat.id)
    }

    fun myPublicKeyPreview(): String {
        val key = chatRepository.crypto.getPublicKeyBase64(currentUserId)
        return chatRepository.crypto.computeSafetyNumber(listOf(key))
    }

    private fun <T> Flow<T>.toUiStateFlow(tag: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w("ChatViewModel", "Error in $tag", rootError)
                emit(UiState.Error(rootError.message ?: "Gagal memuat data"))
            }

    override fun onCleared() {
        super.onCleared()
        liveLocationJob?.cancel()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
