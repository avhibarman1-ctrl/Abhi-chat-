package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.findActivity
import com.example.data.model.AiReplyCategory
import com.example.data.model.AiReplyLanguage
import com.example.data.model.BlockedUser
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.EmergencyContact
import com.example.data.model.FriendEdge
import com.example.data.model.FriendRequestItem
import com.example.data.model.GroupChat
import com.example.data.model.MessageType
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.ReplySuggestion
import com.example.data.model.ReplySuggestionsUiState
import com.example.data.model.SosAlert
import com.example.data.model.StatusItem
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.ui.components.AbhiAiSheetContent
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.ProfilePrivacyScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.AbhiChatTheme
import com.example.ui.viewmodel.UiState
import com.example.util.LocationHelper
import com.example.util.SosSmsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val currentUid = "uid_alice"
    private val sampleUsers = listOf(
        UserProfile(id = "uid_alice", userId = "uid_alice", displayName = "Alice", username = "alice", online = true),
        UserProfile(id = "uid_bob", userId = "uid_bob", displayName = "Bob Sharma", username = "bob", online = true, hasActiveStory = true)
    )

    @Test
    fun `read app name is ABHI CHAT`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ABHI CHAT", appName)
    }

    @Test
    fun `auth screen renders branding and sign in button`() {
        composeTestRule.setContent {
            AbhiChatTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ABHI CHAT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `findActivity unwraps nested ContextWrappers and default_web_client_id is configured`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val wrappedOnce = ContextWrapper(activity)
        val wrappedTwice = ContextWrapper(wrappedOnce)

        assertSame(activity, wrappedTwice.findActivity())

        val webClientId = activity.getString(R.string.default_web_client_id)
        assertNotNull(webClientId)
        assertTrue(webClientId.endsWith(".apps.googleusercontent.com"))
    }

    @Test
    fun `chats list displays groups direct chats privacy and opens create group dialog`() {
        val chats = listOf(
            ChatRoom(
                id = "uid_alice_uid_bob",
                participantIds = listOf("uid_alice", "uid_bob"),
                participantNames = mapOf("uid_alice" to "Alice", "uid_bob" to "Bob Sharma"),
                lastMessage = "See you at 5!"
            )
        )
        val groups = listOf(
            GroupChat(
                id = "grp_1",
                name = "ABHI Founders",
                adminIds = listOf("uid_alice"),
                memberIds = listOf("uid_alice", "uid_bob"),
                lastMessage = "Release tonight",
                lastSenderName = "Bob Sharma"
            )
        )
        composeTestRule.setContent {
            AbhiChatTheme {
                ChatsListScreen(
                    currentUserId = currentUid,
                    chatsState = UiState.Success(chats),
                    groupsState = UiState.Success(groups),
                    usersState = UiState.Success(sampleUsers),
                    storiesState = UiState.Success(emptyList()),
                    blockedUsers = emptyList(),
                    targetPrivacyMap = mapOf(
                        "uid_bob" to PrivacySettings(
                            userId = "uid_bob",
                            onlineStatusPrivacy = PrivacyLevel.EVERYONE.name
                        )
                    ),
                    onOpenDirectChat = { _, _, _ -> },
                    onOpenGroupChat = { _, _ -> },
                    onStartDirectChatWithUser = {},
                    onCreateGroup = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ABHI Founders").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob Sharma").assertIsDisplayed()

        composeTestRule.onNodeWithTag("create_group_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_name_input").performTextInput("Weekend Trip")
        composeTestRule.onNodeWithTag("confirm_create_group_button").assertIsDisplayed()
    }

    @Test
    fun `chat detail displays forwarded reply edited deleted and live location cards`() {
        val futureExpiry = System.currentTimeMillis() + 3_600_000L
        val messages = listOf(
            ChatMessage(
                id = "m1",
                chatId = "c1",
                senderId = "uid_bob",
                senderName = "Bob Sharma",
                text = "Original idea",
                messageType = MessageType.TEXT.name
            ),
            ChatMessage(
                id = "m2",
                chatId = "c1",
                senderId = currentUid,
                senderName = "Alice",
                text = "Updated reply",
                replyToMessageId = "m1",
                replyToSenderName = "Bob Sharma",
                replyToText = "Original idea",
                forwarded = true,
                edited = true
            ),
            ChatMessage(
                id = "m3",
                chatId = "c1",
                senderId = currentUid,
                senderName = "Alice",
                text = "This message was deleted",
                deletedForEveryone = true
            ),
            ChatMessage(
                id = "m4",
                chatId = "c1",
                senderId = currentUid,
                senderName = "Alice",
                text = "Sharing live location (1 hour)",
                messageType = MessageType.LOCATION.name,
                locationLat = 22.5726,
                locationLng = 88.3639,
                locationLive = true,
                locationExpiresAtMillis = futureExpiry
            )
        )
        composeTestRule.setContent {
            AbhiChatTheme {
                ChatDetailScreen(
                    currentUserId = currentUid,
                    chatId = "c1",
                    conversationType = "DIRECT",
                    title = "Bob Sharma",
                    recipientId = "uid_bob",
                    memberIds = listOf(currentUid, "uid_bob"),
                    messagesState = UiState.Success(messages),
                    otherUser = sampleUsers[1],
                    group = null,
                    allUsers = sampleUsers,
                    allGroups = emptyList(),
                    isBlocked = false,
                    onBack = {},
                    onOpenGroupInfo = {},
                    onStartCall = { _, _ -> },
                    onSendText = { _, _ -> },
                    onSendMedia = { _, _, _, _, _ -> },
                    onShareLocation = { _, _, _, _ -> },
                    onStopLiveLocation = { _, _, _ -> },
                    onEditMessage = { _, _ -> },
                    onDeleteForMe = {},
                    onDeleteForEveryone = {},
                    onForwardMessage = { _, _, _ -> },
                    onBlockUser = {},
                    onReportUser = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("messages_list").performScrollToNode(hasTestTag("location_card_m4"))
        composeTestRule.onNodeWithTag("location_card_m4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stop_live_location_m4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_maps_button_m4").assertIsDisplayed()

        composeTestRule.onNodeWithTag("messages_list").performScrollToNode(hasText("Forwarded"))
        composeTestRule.onNodeWithText("Forwarded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Updated reply").assertIsDisplayed()

        composeTestRule.onNodeWithTag("messages_list").performScrollToNode(hasText("This message was deleted"))
        composeTestRule.onNodeWithText("This message was deleted").assertIsDisplayed()

        composeTestRule.onNodeWithTag("voice_call_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_call_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_location_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_reply_trigger_button").assertIsDisplayed()
    }

    @Test
    fun `abhi ai sheet displays 6 categories bengali english hindi edit delete and manual send only`() {
        val sentMessages = mutableListOf<String>()
        var deletedId: String? = null
        var regeneratedCount = 0

        val suggestionsState = ReplySuggestionsUiState.Success(
            suggestions = listOf(
                ReplySuggestion(id = "s1", language = "Bengali", category = "Friendly", text = "হ্যাঁ, অবশ্যই! বিকেলে দেখা হচ্ছে।"),
                ReplySuggestion(id = "s2", language = "English", category = "Friendly", text = "Sounds great, see you at 5!"),
                ReplySuggestion(id = "s3", language = "Hindi", category = "Friendly", text = "बिल्कुल, शाम 5 बजे मिलते हैं!")
            ),
            languageMode = AiReplyLanguage.ALL.label,
            category = AiReplyCategory.FRIENDLY
        )

        composeTestRule.setContent {
            AbhiChatTheme {
                AbhiAiSheetContent(
                    recentMessages = listOf(
                        ChatMessage(
                            id = "m_in",
                            senderId = "uid_bob",
                            senderName = "Bob Sharma",
                            text = "Are we still meeting at 5?"
                        )
                    ),
                    currentUserId = currentUid,
                    selectedCategory = AiReplyCategory.FRIENDLY,
                    selectedLanguage = AiReplyLanguage.ALL,
                    suggestionsState = suggestionsState,
                    onSelectCategory = {},
                    onSelectLanguage = {},
                    onRegenerate = { regeneratedCount++ },
                    onEditSuggestion = { _, _ -> },
                    onDeleteSuggestion = { deletedId = it },
                    onCopied = {},
                    onSendReply = { sentMessages.add(it) },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify never auto-sends on render
        assertTrue(sentMessages.isEmpty())

        // Verify header & incoming preview
        composeTestRule.onNodeWithText("ABHI AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review & approve before sending • Never auto-sends").assertIsDisplayed()

        // Verify all 6 categories
        AiReplyCategory.entries.forEach { category ->
            val tag = "ai_category_${category.name.lowercase()}"
            composeTestRule.onNodeWithTag("ai_categories_row").performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        // Verify Bengali, English, Hindi language chips
        listOf("ai_language_bengali", "ai_language_english", "ai_language_hindi").forEach { langTag ->
            composeTestRule.onNodeWithTag("ai_languages_row").performScrollToNode(hasTestTag(langTag))
            composeTestRule.onNodeWithTag(langTag).assertIsDisplayed()
        }

        // Verify suggestions rendered inside LazyColumn
        composeTestRule.onNodeWithTag("ai_suggestions_list").performScrollTo()
        composeTestRule.onNodeWithTag("ai_suggestions_list").performScrollToNode(hasText("হ্যাঁ, অবশ্যই! বিকেলে দেখা হচ্ছে।"))
        composeTestRule.onNodeWithText("হ্যাঁ, অবশ্যই! বিকেলে দেখা হচ্ছে।").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_suggestions_list").performScrollToNode(hasText("Sounds great, see you at 5!"))
        composeTestRule.onNodeWithText("Sounds great, see you at 5!").assertIsDisplayed()

        // Manual Send on approved suggestion s2
        composeTestRule.onNodeWithTag("ai_suggestions_list").performScrollToNode(hasTestTag("ai_send_s2"))
        composeTestRule.onNodeWithTag("ai_send_s2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("Sounds great, see you at 5!"), sentMessages)

        // Delete suggestion s3
        composeTestRule.onNodeWithTag("ai_suggestions_list").performScrollToNode(hasTestTag("ai_delete_s3"))
        composeTestRule.onNodeWithTag("ai_delete_s3").performClick()
        assertEquals("s3", deletedId)

        // Regenerate
        composeTestRule.onNodeWithTag("abhi_ai_regenerate_button").performScrollTo().performClick()
        assertEquals(1, regeneratedCount)

        // Custom reply manual send
        composeTestRule.onNodeWithTag("abhi_ai_custom_reply_input").performScrollTo().performTextInput("Running 5 mins late")
        composeTestRule.onNodeWithTag("abhi_ai_custom_send_button").performScrollTo().performClick()
        assertEquals(listOf("Sounds great, see you at 5!", "Running 5 mins late"), sentMessages)
    }

    @Test
    fun `chat detail opens ABHI AI sheet on trigger click`() {
        var generateCalled = 0
        composeTestRule.setContent {
            AbhiChatTheme {
                ChatDetailScreen(
                    currentUserId = currentUid,
                    chatId = "c1",
                    conversationType = "GROUP",
                    title = "ABHI Founders",
                    recipientId = "",
                    memberIds = listOf(currentUid, "uid_bob"),
                    messagesState = UiState.Success(emptyList()),
                    otherUser = null,
                    group = GroupChat(id = "c1", name = "ABHI Founders", memberIds = listOf(currentUid, "uid_bob")),
                    allUsers = sampleUsers,
                    allGroups = emptyList(),
                    isBlocked = false,
                    onGenerateReplies = { _, _, _, _ -> generateCalled++ },
                    onBack = {},
                    onOpenGroupInfo = {},
                    onStartCall = { _, _ -> },
                    onSendText = { _, _ -> },
                    onSendMedia = { _, _, _, _, _ -> },
                    onShareLocation = { _, _, _, _ -> },
                    onStopLiveLocation = { _, _, _ -> },
                    onEditMessage = { _, _ -> },
                    onDeleteForMe = {},
                    onDeleteForEveryone = {},
                    onForwardMessage = { _, _, _ -> },
                    onBlockUser = {},
                    onReportUser = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gemini_reply_trigger_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, generateCalled)
        composeTestRule.onNodeWithTag("abhi_ai_bottom_sheet").assertIsDisplayed()
    }

    @Test
    fun `call screen renders WebRTC controls camera flip speaker and in-call SOS banner`() {
        val activeCall = CallSession(
            id = "call_1",
            callerId = currentUid,
            callerName = "Alice",
            calleeId = "uid_bob",
            calleeName = "Bob Sharma",
            callType = CallType.VIDEO.name,
            status = CallStatus.ACTIVE.name
        )
        val sosAlert = SosAlert(
            id = "sos_1",
            userId = currentUid,
            userName = "Alice",
            callId = "call_1",
            latitude = 28.61390,
            longitude = 77.20900,
            active = true,
            recipientPhones = listOf("+919876543210")
        )
        var stoppedAlertId: String? = null
        composeTestRule.setContent {
            AbhiChatTheme {
                CallScreen(
                    call = activeCall,
                    currentUserId = currentUid,
                    currentUserName = "Alice",
                    emergencyContacts = listOf(
                        EmergencyContact(
                            id = "ec_1",
                            userId = currentUid,
                            name = "Mom",
                            phoneNumber = "+919876543210",
                            relationship = "Mother"
                        )
                    ),
                    activeSosAlert = sosAlert,
                    onStopSos = { stoppedAlertId = it },
                    onEndCall = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("in_call_sos_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_sos_status_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mute_call_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_camera_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flip_camera_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speaker_call_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("end_call_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("webrtc_stage_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 SOS contact ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stop_sos_button").performClick()
        assertEquals("sos_1", stoppedAlertId)
    }

    @Test
    fun `sos sms helper and live location duration formatter behave accurately`() {
        val withCoords = SosSmsHelper.buildSosMessage("Alice", 28.6139, 77.2090)
        assertTrue(withCoords.contains("EMERGENCY SOS from Alice"))
        assertTrue(withCoords.contains("28.61390, 77.20900"))
        assertTrue(withCoords.contains("https://maps.google.com/?q=28.613900,77.209000"))

        val withoutCoords = SosSmsHelper.buildSosMessage("Alice", null, null)
        assertTrue(withoutCoords.contains("EMERGENCY SOS from Alice"))
        assertTrue(withoutCoords.contains("Acquiring live GPS"))

        assertEquals("Expired", LocationHelper.formatRemainingDuration(1_000L, nowMillis = 2_000L))
        assertEquals("15m left", LocationHelper.formatRemainingDuration(900_000L, nowMillis = 0L))
        assertEquals("1h 0m left", LocationHelper.formatRemainingDuration(3_600_000L, nowMillis = 0L))
    }

    @Test
    fun `updates screen renders stories and statuses`() {
        val future = System.currentTimeMillis() + 60_000_000L
        val statuses = listOf(
            StatusItem(
                id = "st1",
                userId = "uid_bob",
                userName = "Bob Sharma",
                text = "Working on ABHI CHAT!",
                expiresAtMillis = future
            )
        )
        val stories = listOf(
            StoryItem(
                id = "sr1",
                userId = "uid_bob",
                userName = "Bob Sharma",
                caption = "Evening coffee",
                overlayText = "Good Vibes",
                expiresAtMillis = future
            )
        )
        composeTestRule.setContent {
            AbhiChatTheme {
                UpdatesScreen(
                    currentUserId = currentUid,
                    myProfile = sampleUsers[0],
                    statusesState = UiState.Success(statuses),
                    statusViewsState = UiState.Success(emptyList()),
                    storiesState = UiState.Success(stories),
                    storyViewsState = UiState.Success(emptyList()),
                    onPostStatus = { _, _, _, _, _ -> },
                    onMarkStatusViewed = {},
                    onReplyToStatus = { _, _ -> },
                    onDeleteStatus = {},
                    onPostStory = { _, _, _, _, _, _ -> },
                    onMarkStoryViewed = {},
                    onReactToStory = { _, _ -> },
                    onReplyToStory = { _, _ -> },
                    onDeleteStory = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Stories").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status Updates").assertIsDisplayed()
        composeTestRule.onNodeWithText("Good Vibes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Working on ABHI CHAT!").assertIsDisplayed()
    }

    @Test
    fun `friends and profile privacy screens render core controls`() {
        val reqs = listOf(
            FriendRequestItem(
                id = "uid_bob_uid_alice",
                senderId = "uid_bob",
                senderName = "Bob Sharma",
                receiverId = currentUid,
                receiverName = "Alice",
                status = "PENDING"
            )
        )
        composeTestRule.setContent {
            AbhiChatTheme {
                FriendsScreen(
                    currentUserId = currentUid,
                    usersState = UiState.Success(sampleUsers),
                    friendsState = UiState.Success(emptyList<FriendEdge>()),
                    requestsState = UiState.Success(reqs),
                    blockedUsers = emptyList(),
                    targetPrivacyMap = emptyMap(),
                    onFetchTargetPrivacy = {},
                    onSendRequest = {},
                    onAcceptRequest = {},
                    onRejectRequest = {},
                    onCancelRequest = {},
                    onRemoveFriend = {},
                    onOpenChat = {},
                    onStartCall = { _, _ -> },
                    onBlockUser = {},
                    onReportUser = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pending Friend Requests (1)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accept_friend_req_uid_bob_uid_alice").assertIsDisplayed()
    }

    @Test
    fun `profile privacy screen renders all privacy and emergency contact controls`() {
        val emergencyList = listOf(
            EmergencyContact(
                id = "ec_1",
                userId = currentUid,
                name = "Mom",
                phoneNumber = "+919876543210",
                relationship = "Mother"
            )
        )
        composeTestRule.setContent {
            AbhiChatTheme {
                ProfilePrivacyScreen(
                    myProfileState = UiState.Success(sampleUsers[0]),
                    privacyState = UiState.Success(PrivacySettings(id = currentUid, userId = currentUid)),
                    blockedUsers = listOf(BlockedUser(id = "b1", blockerId = currentUid, blockedId = "u3", blockedName = "Spammer")),
                    emergencyContacts = emergencyList,
                    onUpdateProfile = { _, _, _, _ -> },
                    onUpdatePrivacy = {},
                    onUnblockUser = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_privacy_list").performScrollToNode(hasTestTag("save_profile_button"))
        composeTestRule.onNodeWithTag("save_profile_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_privacy_list").performScrollToNode(hasText("Profile Privacy Settings"))
        composeTestRule.onNodeWithText("Profile Privacy Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_privacy_list").performScrollToNode(hasTestTag("emergency_contacts_card"))
        composeTestRule.onNodeWithTag("emergency_contacts_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mom").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_emergency_contact_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_privacy_list").performScrollToNode(hasTestTag("manage_blocked_users_button"))
        composeTestRule.onNodeWithTag("manage_blocked_users_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_privacy_list").performScrollToNode(hasTestTag("sign_out_button"))
        composeTestRule.onNodeWithTag("sign_out_button").assertIsDisplayed()
    }
}
