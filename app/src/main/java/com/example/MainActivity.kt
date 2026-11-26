package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.crypto.E2eeCryptoManager
import com.example.data.AuthRepository
import com.example.data.ChatRepository
import com.example.data.UiState
import com.example.location.LocationHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.call.ActiveCallScreen
import com.example.ui.call.IncomingCallBanner
import com.example.ui.chatroom.ChatRoomScreen
import com.example.ui.chats.ChatListScreen
import com.example.ui.theme.KriptoChatTheme
import com.example.viewmodel.ChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KriptoChatTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            AuthRepository.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = { /* Interactive AuthScreen handles sign-in */ },
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            KriptoChatAuthenticatedShell(
                currentUserId = uid,
                currentUserName = user.displayName ?: "Pengguna BORDER CHAT"
            )
        }
    }
}

@Composable
fun KriptoChatAuthenticatedShell(
    currentUserId: String,
    currentUserName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authRepo = remember(context) { AuthRepository(context) }
    val viewModel: ChatViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val crypto = E2eeCryptoManager(app)
                ChatViewModel(
                    chatRepository = ChatRepository(db, crypto),
                    authRepository = AuthRepository(db, crypto),
                    locationHelper = LocationHelper(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val profilesState by viewModel.profilesState.collectAsStateWithLifecycle()
    val privacySettings by viewModel.privacySettingsState.collectAsStateWithLifecycle()
    val selectedChat by viewModel.selectedChat.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val liveLocations by viewModel.liveLocationsState.collectAsStateWithLifecycle()
    val liveSharingChatId by viewModel.liveSharingChatId.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingCall.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
    val allProfiles = (profilesState as? UiState.Success)?.data.orEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusBanner()
    }

    val myFingerprint = remember(currentUserId) { viewModel.myPublicKeyPreview() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isExpanded = maxWidth >= 840.dp

                if (isExpanded) {
                    // Adaptive List-Detail canonical layout on tablets / foldables
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                        ) {
                            ChatListScreen(
                                currentUserId = currentUserId,
                                currentUserName = currentUserName,
                                myFingerprint = myFingerprint,
                                chatsState = chatsState,
                                profilesState = profilesState,
                                privacySettings = privacySettings,
                                selectedChatId = selectedChatId,
                                onOpenChat = { viewModel.selectChat(it) },
                                onStartDirectChat = { viewModel.openOrCreateDirectChat(it) {} },
                                onOpenSavedMessages = { viewModel.openSavedMessages {} },
                                onCreateGroup = { title, members ->
                                    viewModel.createGroupChat(title, members) {}
                                },
                                onToggleGlobalReadReceipts = viewModel::setGlobalReadReceiptsEnabled,
                                onToggleGroupReadReceipts = viewModel::setGroupReadReceiptsEnabled,
                                onSetChatReadReceiptOverride = viewModel::setChatReadReceiptOverride,
                                onSignOut = {
                                    AuthRepository.signOut(credentialManager, authRepo, scope)
                                }
                            )
                        }
                        VerticalDivider()
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val chat = selectedChat
                            if (chat != null) {
                                ChatRoomScreen(
                                    chat = chat,
                                    allChats = allChats,
                                    profiles = allProfiles,
                                    currentUserId = currentUserId,
                                    messagesState = messagesState,
                                    liveLocations = liveLocations,
                                    privacySettings = privacySettings,
                                    isSharingLiveInThisChat = liveSharingChatId == chat.id,
                                    isBusy = isBusy,
                                    showBackButton = false,
                                    safetyNumber = viewModel.computeSafetyNumberForChat(chat),
                                    onBack = { viewModel.selectChat(null) },
                                    onSendText = viewModel::sendText,
                                    onEditMessage = viewModel::editMessage,
                                    onDeleteMessage = viewModel::deleteMessage,
                                    onMarkMessagesRead = { msgs ->
                                        viewModel.markChatMessagesAsRead(chat, msgs)
                                    },
                                    onToggleGlobalReadReceipts = viewModel::setGlobalReadReceiptsEnabled,
                                    onToggleGroupReadReceipts = viewModel::setGroupReadReceiptsEnabled,
                                    onSetChatReadReceiptOverride = viewModel::setChatReadReceiptOverride,
                                    onSendImage = { uri -> viewModel.sendImage(context, uri) },
                                    onSendVideo = { uri -> viewModel.sendVideo(context, uri) },
                                    onShareCurrentLocation = viewModel::sendCurrentLocation,
                                    onStartLiveLocation = viewModel::startRealtimeLocationSharing,
                                    onStopLiveLocation = viewModel::stopRealtimeLocationSharing,
                                    onStartCall = viewModel::startCall,
                                    onShowMessage = viewModel::showMessage
                                )
                            } else {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Pilih percakapan atau buat grup baru untuk mulai chat terenkripsi.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Compact single-pane navigation with BackHandler
                    val chat = selectedChat
                    if (chat == null) {
                        ChatListScreen(
                            currentUserId = currentUserId,
                            currentUserName = currentUserName,
                            myFingerprint = myFingerprint,
                            chatsState = chatsState,
                            profilesState = profilesState,
                            privacySettings = privacySettings,
                            selectedChatId = null,
                            onOpenChat = { viewModel.selectChat(it) },
                            onStartDirectChat = { viewModel.openOrCreateDirectChat(it) {} },
                            onOpenSavedMessages = { viewModel.openSavedMessages {} },
                            onCreateGroup = { title, members ->
                                viewModel.createGroupChat(title, members) {}
                            },
                            onToggleGlobalReadReceipts = viewModel::setGlobalReadReceiptsEnabled,
                            onToggleGroupReadReceipts = viewModel::setGroupReadReceiptsEnabled,
                            onSetChatReadReceiptOverride = viewModel::setChatReadReceiptOverride,
                            onSignOut = {
                                AuthRepository.signOut(credentialManager, authRepo, scope)
                            }
                        )
                    } else {
                        BackHandler { viewModel.selectChat(null) }
                        ChatRoomScreen(
                            chat = chat,
                            allChats = allChats,
                            profiles = allProfiles,
                            currentUserId = currentUserId,
                            messagesState = messagesState,
                            liveLocations = liveLocations,
                            privacySettings = privacySettings,
                            isSharingLiveInThisChat = liveSharingChatId == chat.id,
                            isBusy = isBusy,
                            showBackButton = true,
                            safetyNumber = viewModel.computeSafetyNumberForChat(chat),
                            onBack = { viewModel.selectChat(null) },
                            onSendText = viewModel::sendText,
                            onEditMessage = viewModel::editMessage,
                            onDeleteMessage = viewModel::deleteMessage,
                            onMarkMessagesRead = { msgs ->
                                viewModel.markChatMessagesAsRead(chat, msgs)
                            },
                            onToggleGlobalReadReceipts = viewModel::setGlobalReadReceiptsEnabled,
                            onToggleGroupReadReceipts = viewModel::setGroupReadReceiptsEnabled,
                            onSetChatReadReceiptOverride = viewModel::setChatReadReceiptOverride,
                            onSendImage = { uri -> viewModel.sendImage(context, uri) },
                            onSendVideo = { uri -> viewModel.sendVideo(context, uri) },
                            onShareCurrentLocation = viewModel::sendCurrentLocation,
                            onStartLiveLocation = viewModel::startRealtimeLocationSharing,
                            onStopLiveLocation = viewModel::stopRealtimeLocationSharing,
                            onStartCall = viewModel::startCall,
                            onShowMessage = viewModel::showMessage
                        )
                    }
                }
            }

            // Incoming Call Ringing Banner
            incomingCall?.let { call ->
                IncomingCallBanner(
                    call = call,
                    onAccept = { viewModel.acceptIncomingCall(call) },
                    onDecline = { viewModel.declineIncomingCall(call) }
                )
            }

            // Active Voice / Video Call Full-Screen Overlay
            activeCall?.let { call ->
                BackHandler { viewModel.endActiveCall(call, 0) }
                ActiveCallScreen(
                    call = call,
                    onEndCall = { dur -> viewModel.endActiveCall(call, dur) }
                )
            }
        }
    }
}
