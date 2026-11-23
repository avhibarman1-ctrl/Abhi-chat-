package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.findActivity
import com.example.data.firebase.performSignOut
import com.google.firebase.auth.FirebaseAuth
import com.example.data.model.CallStatus
import com.example.data.repository.ChatGroupRepository
import com.example.data.repository.StatusStoryRepository
import com.example.data.repository.UserPrivacyRepository
import com.example.service.LiveLocationForegroundService
import com.example.ui.components.IncomingCallBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.GroupInfoScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfilePrivacyScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.AbhiChatTheme
import com.example.ui.viewmodel.AbhiChatViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

const val ROUTE_CHATS = "chats"
const val ROUTE_UPDATES = "updates"
const val ROUTE_FRIENDS = "friends"
const val ROUTE_NOTIFICATIONS = "notifications"
const val ROUTE_PROFILE = "profile"
const val ROUTE_CHAT_DETAIL = "chat_detail/{type}/{chatId}/{title}/{recipientId}"
const val ROUTE_GROUP_INFO = "group_info/{groupId}"

private fun chatDetailRoute(type: String, chatId: String, title: String, recipientId: String): String {
    return "chat_detail/${Uri.encode(type)}/${Uri.encode(chatId)}/${Uri.encode(title.ifBlank { "Chat" })}/${Uri.encode(recipientId.ifBlank { "_" })}"
}

private fun groupInfoRoute(groupId: String): String {
    return "group_info/${Uri.encode(groupId)}"
}

private data class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        setContent {
            AbhiChatTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val auth = Firebase.auth
        currentUser = auth.currentUser
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            val appContext = LocalContext.current.applicationContext
            val viewModel: AbhiChatViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        AbhiChatViewModel(
                            userRepo = UserPrivacyRepository(appContext),
                            chatRepo = ChatGroupRepository(appContext),
                            statusStoryRepo = StatusStoryRepository(appContext),
                            currentUserId = user.uid
                        )
                    }
                }
            )
            AuthenticatedAbhiChatApp(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedAbhiChatApp(
    viewModel: AbhiChatViewModel
) {
    val context = LocalContext.current
    val hostContext = remember(context) { context.findActivity() ?: context }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(hostContext) { CredentialManager.create(hostContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUserId = viewModel.currentUserId
    val myProfileState by viewModel.myProfile.collectAsStateWithLifecycle()
    val myPrivacyState by viewModel.myPrivacySettings.collectAsStateWithLifecycle()
    val usersState by viewModel.discoverableUsers.collectAsStateWithLifecycle()
    val chatsState by viewModel.chats.collectAsStateWithLifecycle()
    val groupsState by viewModel.groups.collectAsStateWithLifecycle()
    val friendsState by viewModel.friends.collectAsStateWithLifecycle()
    val friendRequestsState by viewModel.friendRequests.collectAsStateWithLifecycle()
    val blockedState by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val statusesState by viewModel.statuses.collectAsStateWithLifecycle()
    val statusViewsState by viewModel.statusViews.collectAsStateWithLifecycle()
    val storiesState by viewModel.stories.collectAsStateWithLifecycle()
    val storyViewsState by viewModel.storyViews.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notifications.collectAsStateWithLifecycle()
    val callsState by viewModel.calls.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeMessages.collectAsStateWithLifecycle()
    val activeGroupMembersState by viewModel.activeGroupMembers.collectAsStateWithLifecycle()
    val targetPrivacyMap by viewModel.targetUserPrivacy.collectAsStateWithLifecycle()
    val liveLocationMessageId by viewModel.liveLocationMessageId.collectAsStateWithLifecycle()
    val liveLocationExpiresAtMillis by viewModel.liveLocationExpiresAtMillis.collectAsStateWithLifecycle()
    val isBlockedByPeer by viewModel.isBlockedByPeer.collectAsStateWithLifecycle()
    val emergencyContactsState by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val sosAlertsState by viewModel.sosAlerts.collectAsStateWithLifecycle()
    val activeSosAlertId by viewModel.activeSosAlertId.collectAsStateWithLifecycle()
    val selectedAiCategory by viewModel.selectedAiCategory.collectAsStateWithLifecycle()
    val selectedAiLanguage by viewModel.selectedAiLanguage.collectAsStateWithLifecycle()
    val replySuggestionsState by viewModel.replySuggestionsState.collectAsStateWithLifecycle()

    // Request POST_NOTIFICATIONS on Android 13+
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Online / Offline lifecycle presence tracking
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setOnlineStatus(true)
                Lifecycle.Event.ON_STOP -> viewModel.setOnlineStatus(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Collect one-shot UI feedback events into Snackbar
    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Real-time continuous GPS updates + foreground service + auto-expiry for active live location
    LaunchedEffect(liveLocationMessageId, liveLocationExpiresAtMillis) {
        val msgId = liveLocationMessageId
        if (msgId == null) {
            LiveLocationForegroundService.stop(context)
            return@LaunchedEffect
        }
        LiveLocationForegroundService.start(context, "Updating your live location in real time")
        var lastLat = 0.0
        var lastLng = 0.0
        try {
            LocationHelper.observeLocationUpdates(context, intervalMs = 8_000L).collect { coords ->
                lastLat = coords.latitude
                lastLng = coords.longitude
                if (liveLocationExpiresAtMillis > 0L && System.currentTimeMillis() >= liveLocationExpiresAtMillis) {
                    viewModel.stopLiveLocation(msgId, lastLat, lastLng)
                    LiveLocationForegroundService.stop(context)
                    return@collect
                }
                viewModel.updateLiveLocationCoordinates(msgId, coords.latitude, coords.longitude)
            }
        } catch (_: Exception) {
            // Fall back to periodic location polling if stream closes early
            while (true) {
                delay(10_000L)
                val coords = LocationHelper.fetchCurrentLocation(context)
                if (coords != null) {
                    lastLat = coords.latitude
                    lastLng = coords.longitude
                }
                if (liveLocationExpiresAtMillis > 0L && System.currentTimeMillis() >= liveLocationExpiresAtMillis) {
                    viewModel.stopLiveLocation(msgId, lastLat, lastLng)
                    break
                }
                if (coords != null) {
                    viewModel.updateLiveLocationCoordinates(msgId, coords.latitude, coords.longitude)
                }
            }
        } finally {
            LiveLocationForegroundService.stop(context)
        }
    }

    // Continuous live GPS updates for active in-call Emergency SOS alert
    LaunchedEffect(activeSosAlertId) {
        val alertId = activeSosAlertId ?: return@LaunchedEffect
        while (true) {
            delay(10_000L)
            val coords = LocationHelper.fetchCurrentLocation(context)
            if (coords != null) {
                viewModel.updateSosLocation(alertId, coords.latitude, coords.longitude)
            }
        }
    }

    // OS Notification dispatching for newly arrived unread Firestore notifications
    var initialNotifSeeded by remember { mutableStateOf(false) }
    var seenNotifIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(notificationsState) {
        val list = (notificationsState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (!initialNotifSeeded) {
            seenNotifIds = list.map { it.id }.toSet()
            initialNotifSeeded = true
        } else {
            val freshUnread = list.filter { !it.read && !seenNotifIds.contains(it.id) }
            freshUnread.forEachIndexed { index, notif ->
                NotificationHelper.showNotification(
                    context = context,
                    id = (notif.id.hashCode() + index),
                    title = notif.title,
                    body = notif.body
                )
            }
            if (freshUnread.isNotEmpty()) {
                seenNotifIds = seenNotifIds + freshUnread.map { it.id }
            }
        }
    }

    val allUsers = (usersState as? UiState.Success)?.data.orEmpty()
    val allGroups = (groupsState as? UiState.Success)?.data.orEmpty()
    val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
    val blockedUsers = (blockedState as? UiState.Success)?.data.orEmpty()
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }
    val myProfile = (myProfileState as? UiState.Success)?.data
    val emergencyContacts = (emergencyContactsState as? UiState.Success)?.data.orEmpty()
    val sosAlerts = (sosAlertsState as? UiState.Success)?.data.orEmpty()
    val activeSosAlert = sosAlerts.firstOrNull {
        (activeSosAlertId != null && it.id == activeSosAlertId) || it.active
    }

    val unreadNotificationsCount = (notificationsState as? UiState.Success)?.data
        .orEmpty()
        .count { !it.read }
    val pendingIncomingRequestsCount = (friendRequestsState as? UiState.Success)?.data
        .orEmpty()
        .count { it.receiverId == currentUserId && it.status == "PENDING" }

    // Voice & Video call state detection
    val calls = (callsState as? UiState.Success)?.data.orEmpty()
    val activeOrOutgoingCall = calls.firstOrNull { call ->
        call.status == CallStatus.ACTIVE.name ||
            (call.status == CallStatus.RINGING.name && call.callerId == currentUserId)
    }
    val incomingRingingCall = calls.firstOrNull { call ->
        call.status == CallStatus.RINGING.name &&
            call.calleeId == currentUserId &&
            !blockedIds.contains(call.callerId)
    }

    if (activeOrOutgoingCall != null) {
        val observedCall by remember(activeOrOutgoingCall.id) {
            viewModel.observeCallSession(activeOrOutgoingCall.id)
        }.collectAsStateWithLifecycle(initialValue = activeOrOutgoingCall)
        val liveCall = observedCall ?: activeOrOutgoingCall
        val isCallerInCall = liveCall.callerId == currentUserId
        val remoteCandidates by remember(liveCall.id, isCallerInCall) {
            viewModel.observeRemoteIceCandidates(liveCall.id, fromCaller = !isCallerInCall)
        }.collectAsStateWithLifecycle(initialValue = emptyList())

        CallScreen(
            call = liveCall,
            currentUserId = currentUserId,
            currentUserName = myProfile?.displayName.orEmpty(),
            emergencyContacts = emergencyContacts,
            activeSosAlert = activeSosAlert,
            remoteIceCandidates = remoteCandidates,
            onOfferCreated = { cid, sdp, type -> viewModel.saveCallOffer(cid, sdp, type) },
            onAnswerCreated = { cid, sdp, type -> viewModel.saveCallAnswer(cid, sdp, type) },
            onIceCandidate = { cid, callerFlag, mid, idx, cand ->
                viewModel.saveIceCandidate(cid, callerFlag, mid, idx, cand)
            },
            onIceStateChanged = { cid, iceState -> viewModel.updateCallIceState(cid, iceState) },
            onActivateSos = { callId, lat, lng, phones ->
                viewModel.activateInCallSos(callId, lat, lng, phones)
            },
            onStopSos = { alertId -> viewModel.stopSosAlert(alertId) },
            onAddEmergencyContact = { name, phone, relation ->
                viewModel.addEmergencyContact(name, phone, relation)
            },
            onEndCall = { viewModel.endCall(liveCall.id) }
        )
        return
    }

    val tabs = remember {
        listOf(
            MainTab(ROUTE_CHATS, "Chats", Icons.Default.Chat, "tab_chats"),
            MainTab(ROUTE_UPDATES, "Updates", Icons.Default.AutoAwesome, "tab_updates"),
            MainTab(ROUTE_FRIENDS, "Friends", Icons.Default.People, "tab_friends"),
            MainTab(ROUTE_NOTIFICATIONS, "Alerts", Icons.Default.Notifications, "tab_alerts"),
            MainTab(ROUTE_PROFILE, "Profile", Icons.Default.Person, "tab_profile")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelTab = tabs.any { it.route == currentRoute }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isTopLevelTab) {
                    TopAppBar(
                        title = {
                            val subtitle = when (currentRoute) {
                                ROUTE_UPDATES -> "Statuses & Stories"
                                ROUTE_FRIENDS -> "Friends & Discovery"
                                ROUTE_NOTIFICATIONS -> "Notifications"
                                ROUTE_PROFILE -> "Profile & Privacy"
                                else -> "Social Messaging"
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isTopLevelTab && !useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val badgeCount = when (tab.route) {
                                ROUTE_FRIENDS -> pendingIncomingRequestsCount
                                ROUTE_NOTIFICATIONS -> unreadNotificationsCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelTab && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        tabs.forEach { tab ->
                            val badgeCount = when (tab.route) {
                                ROUTE_FRIENDS -> pendingIncomingRequestsCount
                                ROUTE_NOTIFICATIONS -> unreadNotificationsCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (incomingRingingCall != null) {
                        IncomingCallBanner(
                            call = incomingRingingCall,
                            onAccept = { viewModel.acceptCall(incomingRingingCall.id) },
                            onDecline = { viewModel.endCall(incomingRingingCall.id, rejected = true) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = if (useRail && isTopLevelTab) 860.dp else Int.MAX_VALUE.dp)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_CHATS,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(ROUTE_CHATS) {
                                ChatsListScreen(
                                    currentUserId = currentUserId,
                                    chatsState = chatsState,
                                    groupsState = groupsState,
                                    usersState = usersState,
                                    storiesState = storiesState,
                                    blockedUsers = blockedUsers,
                                    friendsState = friendsState,
                                    targetPrivacyMap = targetPrivacyMap,
                                    onFetchTargetPrivacy = { viewModel.fetchTargetPrivacy(it) },
                                    onOpenDirectChat = { chatId, title, otherUid ->
                                        viewModel.openConversation(chatId, "DIRECT", otherUid)
                                        navController.navigate(chatDetailRoute("DIRECT", chatId, title, otherUid))
                                    },
                                    onOpenGroupChat = { groupId, title ->
                                        viewModel.openConversation(groupId, "GROUP")
                                        navController.navigate(chatDetailRoute("GROUP", groupId, title, "_"))
                                    },
                                    onStartDirectChatWithUser = { target ->
                                        viewModel.startDirectChat(target) { chatId ->
                                            navController.navigate(chatDetailRoute("DIRECT", chatId, target.displayName, target.userId))
                                        }
                                    },
                                    onCreateGroup = { name, desc, photoData, members ->
                                        viewModel.createGroup(name, desc, photoData, members) { groupId ->
                                            navController.navigate(chatDetailRoute("GROUP", groupId, name, "_"))
                                        }
                                    }
                                )
                            }

                            composable(ROUTE_UPDATES) {
                                UpdatesScreen(
                                    currentUserId = currentUserId,
                                    myProfile = myProfile,
                                    statusesState = statusesState,
                                    statusViewsState = statusViewsState,
                                    storiesState = storiesState,
                                    storyViewsState = storyViewsState,
                                    onPostStatus = { mediaType, text, bgHex, mediaData, privacy ->
                                        viewModel.postStatus(mediaType, text, bgHex, mediaData, privacy)
                                    },
                                    onMarkStatusViewed = { viewModel.markStatusViewed(it) },
                                    onReplyToStatus = { status, reply ->
                                        viewModel.replyToStatus(status, reply) { chatId ->
                                            viewModel.openConversation(chatId, "DIRECT")
                                            navController.navigate(
                                                chatDetailRoute("DIRECT", chatId, status.userName, status.userId)
                                            )
                                        }
                                    },
                                    onDeleteStatus = { viewModel.deleteStatus(it) },
                                    onPostStory = { mediaType, caption, overlayText, bgHex, mediaData, privacy ->
                                        viewModel.postStory(mediaType, caption, overlayText, bgHex, mediaData, privacy)
                                    },
                                    onMarkStoryViewed = { viewModel.markStoryViewed(it) },
                                    onReactToStory = { story, emoji -> viewModel.reactToStory(story, emoji) },
                                    onReplyToStory = { story, reply -> viewModel.replyToStory(story, reply) },
                                    onDeleteStory = { viewModel.deleteStory(it) }
                                )
                            }

                            composable(ROUTE_FRIENDS) {
                                FriendsScreen(
                                    currentUserId = currentUserId,
                                    usersState = usersState,
                                    friendsState = friendsState,
                                    requestsState = friendRequestsState,
                                    blockedUsers = blockedUsers,
                                    targetPrivacyMap = targetPrivacyMap,
                                    onFetchTargetPrivacy = { viewModel.fetchTargetPrivacy(it) },
                                    onSendRequest = { viewModel.sendFriendRequest(it) },
                                    onAcceptRequest = { viewModel.acceptFriendRequest(it) },
                                    onRejectRequest = { viewModel.rejectFriendRequest(it) },
                                    onCancelRequest = { viewModel.cancelFriendRequest(it) },
                                    onRemoveFriend = { viewModel.removeFriend(it) },
                                    onOpenChat = { target ->
                                        viewModel.startDirectChat(target) { chatId ->
                                            navController.navigate(
                                                chatDetailRoute("DIRECT", chatId, target.displayName, target.userId)
                                            )
                                        }
                                    },
                                    onStartCall = { user, callType -> viewModel.startCall(user, callType) },
                                    onBlockUser = { viewModel.blockUser(it) },
                                    onReportUser = { target, reason, details, alsoBlock ->
                                        viewModel.reportUser(target, reason, details, alsoBlock)
                                    }
                                )
                            }

                            composable(ROUTE_NOTIFICATIONS) {
                                NotificationsScreen(
                                    notificationsState = notificationsState,
                                    onMarkRead = { viewModel.markNotificationRead(it) },
                                    onMarkAllRead = { viewModel.markAllNotificationsRead(it) },
                                    onNotificationClick = { notif ->
                                        when (notif.type) {
                                            "GROUP_MESSAGE" -> {
                                                val grp = allGroups.firstOrNull { it.id == notif.targetId }
                                                val title = grp?.name ?: notif.title
                                                viewModel.openConversation(notif.targetId, "GROUP")
                                                navController.navigate(chatDetailRoute("GROUP", notif.targetId, title, "_"))
                                            }
                                            "MESSAGE_REPLY" -> {
                                                val grp = allGroups.firstOrNull { it.id == notif.targetId }
                                                if (grp != null) {
                                                    viewModel.openConversation(grp.id, "GROUP")
                                                    navController.navigate(chatDetailRoute("GROUP", grp.id, grp.name, "_"))
                                                } else {
                                                    val chat = allChats.firstOrNull { it.id == notif.targetId }
                                                    val otherId = chat?.participantIds?.firstOrNull { it != currentUserId } ?: notif.senderId
                                                    val otherUser = allUsers.firstOrNull { it.userId == otherId }
                                                    val title = otherUser?.displayName ?: notif.senderName.ifBlank { "Chat" }
                                                    viewModel.openConversation(notif.targetId, "DIRECT")
                                                    navController.navigate(chatDetailRoute("DIRECT", notif.targetId, title, otherId))
                                                }
                                            }
                                            "FRIEND_REQUEST", "FRIEND_ACCEPTED" -> navigateToTab(ROUTE_FRIENDS)
                                            "STORY_REPLY" -> navigateToTab(ROUTE_UPDATES)
                                            else -> Unit
                                        }
                                    }
                                )
                            }

                            composable(ROUTE_PROFILE) {
                                ProfilePrivacyScreen(
                                    myProfileState = myProfileState,
                                    privacyState = myPrivacyState,
                                    blockedUsers = blockedUsers,
                                    emergencyContacts = emergencyContacts,
                                    onUpdateProfile = { name, username, bio, avatar ->
                                        viewModel.updateProfile(name, username, bio, avatar)
                                    },
                                    onUpdatePrivacy = { settings ->
                                        viewModel.updatePrivacySettings(settings)
                                    },
                                    onUnblockUser = { viewModel.unblockUser(it) },
                                    onAddEmergencyContact = { name, phone, relation ->
                                        viewModel.addEmergencyContact(name, phone, relation)
                                    },
                                    onDeleteEmergencyContact = { contactId ->
                                        viewModel.deleteEmergencyContact(contactId)
                                    },
                                    onSignOut = {
                                        performSignOut(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeAuthSignOut = { viewModel.prepareSignOut() }
                                        )
                                    }
                                )
                            }

                            composable(
                                route = ROUTE_CHAT_DETAIL,
                                arguments = listOf(
                                    navArgument("type") { type = NavType.StringType },
                                    navArgument("chatId") { type = NavType.StringType },
                                    navArgument("title") { type = NavType.StringType },
                                    navArgument("recipientId") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val convType = Uri.decode(backStackEntry.arguments?.getString("type") ?: "DIRECT")
                                val chatId = Uri.decode(backStackEntry.arguments?.getString("chatId").orEmpty())
                                val argTitle = Uri.decode(backStackEntry.arguments?.getString("title") ?: "Chat")
                                val rawRecipient = Uri.decode(backStackEntry.arguments?.getString("recipientId").orEmpty())
                                val recipientId = if (rawRecipient == "_") "" else rawRecipient

                                LaunchedEffect(chatId, convType, recipientId) {
                                    viewModel.openConversation(chatId, convType, recipientId)
                                }

                                val group = if (convType == "GROUP") allGroups.firstOrNull { it.id == chatId } else null
                                val otherUser = if (convType == "DIRECT") allUsers.firstOrNull { it.userId == recipientId } else null
                                val memberIds = if (convType == "GROUP") {
                                    group?.memberIds ?: listOf(currentUserId)
                                } else {
                                    listOf(currentUserId, recipientId).filter { it.isNotBlank() }.distinct()
                                }
                                val displayTitle = group?.name ?: otherUser?.displayName ?: argTitle
                                val isBlocked = convType == "DIRECT" && blockedIds.contains(recipientId)
                                val friendIdsSet = remember(friendsState, currentUserId) {
                                    (friendsState as? UiState.Success)?.data.orEmpty()
                                        .flatMap { it.participantIds }
                                        .filter { it != currentUserId }
                                        .toSet()
                                }
                                val isFriendWithPeer = convType == "DIRECT" && friendIdsSet.contains(recipientId)
                                val peerPrivacy = if (convType == "DIRECT") targetPrivacyMap[recipientId] else null

                                ChatDetailScreen(
                                    currentUserId = currentUserId,
                                    chatId = chatId,
                                    conversationType = convType,
                                    title = displayTitle,
                                    recipientId = recipientId,
                                    memberIds = memberIds,
                                    messagesState = activeMessagesState,
                                    otherUser = otherUser,
                                    group = group,
                                    allUsers = allUsers,
                                    allGroups = allGroups,
                                    isBlocked = isBlocked,
                                    isBlockedByPeer = convType == "DIRECT" && isBlockedByPeer,
                                    peerPrivacy = peerPrivacy,
                                    isFriendWithPeer = isFriendWithPeer,
                                    selectedAiCategory = selectedAiCategory,
                                    selectedAiLanguage = selectedAiLanguage,
                                    replySuggestionsState = replySuggestionsState,
                                    onSelectAiCategory = { cat, msgs -> viewModel.selectAiCategory(cat, msgs) },
                                    onSelectAiLanguage = { lang, msgs -> viewModel.selectAiLanguage(lang, msgs) },
                                    onGenerateReplies = { msgs, cat, lang, custom ->
                                        viewModel.generateAbhiAiReplies(msgs, cat, lang, custom)
                                    },
                                    onEditAiSuggestion = { id, newText -> viewModel.editAiSuggestion(id, newText) },
                                    onDeleteAiSuggestion = { id -> viewModel.deleteAiSuggestion(id) },
                                    onDismissReplies = {
                                        viewModel.clearReplySuggestions()
                                    },
                                    onBack = {
                                        viewModel.closeConversation()
                                        navController.popBackStack()
                                    },
                                    onOpenGroupInfo = {
                                        if (convType == "GROUP") {
                                            navController.navigate(groupInfoRoute(chatId))
                                        }
                                    },
                                    onStartCall = { target, callType ->
                                        viewModel.startCall(target, callType)
                                    },
                                    onSendText = { text, replyTo ->
                                        viewModel.sendTextMessage(
                                            chatId = chatId,
                                            conversationType = convType,
                                            recipientId = recipientId,
                                            memberIds = memberIds,
                                            text = text,
                                            replyTo = replyTo,
                                            groupName = group?.name.orEmpty()
                                        )
                                    },
                                    onSendMedia = { mediaType, data, duration, caption, replyTo ->
                                        viewModel.sendMediaMessage(
                                            chatId = chatId,
                                            conversationType = convType,
                                            recipientId = recipientId,
                                            memberIds = memberIds,
                                            mediaType = mediaType,
                                            mediaData = data,
                                            durationSec = duration,
                                            caption = caption,
                                            replyTo = replyTo,
                                            groupName = group?.name.orEmpty()
                                        )
                                    },
                                    onShareLocation = { lat, lng, isLive, duration ->
                                        viewModel.sendLocationMessage(
                                            chatId = chatId,
                                            conversationType = convType,
                                            recipientId = recipientId,
                                            memberIds = memberIds,
                                            lat = lat,
                                            lng = lng,
                                            isLive = isLive,
                                            duration = duration,
                                            groupName = group?.name.orEmpty()
                                        )
                                    },
                                    onStopLiveLocation = { msgId, lat, lng ->
                                        viewModel.stopLiveLocation(msgId, lat, lng)
                                    },
                                    onEditMessage = { msgId, newText ->
                                        viewModel.editMessage(msgId, newText)
                                    },
                                    onDeleteForMe = { msg ->
                                        viewModel.deleteMessageForMe(msg)
                                    },
                                    onDeleteForEveryone = { msgId ->
                                        viewModel.deleteMessageForEveryone(msgId)
                                    },
                                    onForwardMessage = { original, targetUsers, targetGroups ->
                                        viewModel.forwardMessage(original, targetUsers, targetGroups)
                                    },
                                    onBlockUser = { target ->
                                        viewModel.blockUser(target)
                                    },
                                    onReportUser = { target, reason, details, alsoBlock ->
                                        viewModel.reportUser(target, reason, details, alsoBlock)
                                    }
                                )
                            }

                            composable(
                                route = ROUTE_GROUP_INFO,
                                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val groupId = Uri.decode(backStackEntry.arguments?.getString("groupId").orEmpty())
                                val group = allGroups.firstOrNull { it.id == groupId }
                                if (group != null) {
                                    GroupInfoScreen(
                                        currentUserId = currentUserId,
                                        group = group,
                                        membersState = activeGroupMembersState,
                                        allUsers = allUsers.filter { !blockedIds.contains(it.userId) },
                                        onBack = { navController.popBackStack() },
                                        onUpdateGroup = { name, desc, photoData ->
                                            viewModel.updateGroupInfo(group.id, name, desc, photoData)
                                        },
                                        onAddMember = { user ->
                                            viewModel.addGroupMember(group, user)
                                        },
                                        onRemoveMember = { targetUid ->
                                            viewModel.removeGroupMember(group, targetUid)
                                        },
                                        onToggleAdmin = { targetUid, makeAdmin ->
                                            viewModel.toggleGroupAdmin(group, targetUid, makeAdmin)
                                        },
                                        onLeaveGroup = {
                                            viewModel.leaveGroup(group) {
                                                navController.popBackStack(ROUTE_CHATS, inclusive = false)
                                            }
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
