const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE = "alice_uid_1";
const BOB = "bob_uid_2";
const CHARLIE = "charlie_uid_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").where("profileVisibility", "in", ["EVERYONE", "FRIENDS"]).get());
  await assertFails(unauth.collection("chats").get());
  await assertFails(unauth.collection("groups").get());
  await assertFails(unauth.collection("messages").get());
  await assertFails(unauth.collection("statuses").get());
  await assertFails(unauth.collection("stories").get());
  await assertFails(unauth.collection("friend_requests").get());
  await assertFails(unauth.collection("friends").get());
  await assertFails(unauth.collection("blocked_users").get());
  await assertFails(unauth.collection("reports").get());
  await assertFails(unauth.collection("privacy_settings").doc(ALICE).get());
  await assertFails(unauth.collection("notifications").get());
  await assertFails(unauth.collection("calls").get());
  await assertFails(unauth.collection("sos_alerts").get());
  await assertFails(unauth.collection("users").doc(ALICE).collection("emergency_contacts").get());
});

test("Users & Private PII isolation + Privacy Settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).set({
      userId: ALICE,
      displayName: "Alice",
      username: "alice",
      bio: "Hey there!",
      avatarUrl: "",
      online: true,
      profileVisibility: "EVERYONE",
      hasActiveStory: false,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Private PII doc only accessible by owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("info").set({
      userId: ALICE,
      email: "alice@example.com",
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE).collection("private").doc("info").get());

  // Cross-user user profile overwrite fails
  await assertFails(
    bobDb.collection("users").doc(ALICE).update({ displayName: "Hacked", updatedAt: pastDate(500) })
  );

  // Discovery query matching allow list succeeds; unfiltered fails
  await assertSucceeds(
    bobDb.collection("users").where("profileVisibility", "in", ["EVERYONE", "FRIENDS"]).get()
  );
  await assertFails(bobDb.collection("users").get());

  // Privacy settings
  await assertSucceeds(
    aliceDb.collection("privacy_settings").doc(ALICE).set({
      userId: ALICE,
      profileVisibility: "EVERYONE",
      lastSeenPrivacy: "FRIENDS",
      onlineStatusPrivacy: "EVERYONE",
      profilePhotoPrivacy: "EVERYONE",
      bioPrivacy: "FRIENDS",
      statusPrivacy: "FRIENDS",
      storyPrivacy: "EVERYONE",
      whoCanMessageMe: "EVERYONE",
      whoCanSendFriendRequests: "EVERYONE",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("privacy_settings").doc(ALICE).get());
  await assertFails(bobDb.collection("privacy_settings").doc(ALICE).update({ whoCanMessageMe: "NOBODY", updatedAt: pastDate(500) }));
});

test("Chats, Messages, Edit, Delete for Me, Delete for Everyone, Reply & Block Enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();
  const now = pastDate(5000);
  const chatId = `${ALICE}_${BOB}`;

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE, BOB],
      participantNames: { [ALICE]: "Alice", [BOB]: "Bob" },
      lastMessage: "Hi Bob",
      lastMessageType: "TEXT",
      lastSenderId: ALICE,
      lastMessageAt: now,
      createdBy: ALICE,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Charlie cannot read Alice-Bob chat
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  // Alice sends message with reply metadata
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_1").set({
      chatId,
      conversationType: "DIRECT",
      senderId: ALICE,
      senderName: "Alice",
      recipientId: BOB,
      memberIds: [ALICE, BOB],
      text: "Hello Bob!",
      messageType: "TEXT",
      forwarded: false,
      edited: false,
      deletedForEveryone: false,
      deletedForUserIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot edit Alice's message or delete for everyone
  await assertFails(
    bobDb.collection("messages").doc("msg_1").update({
      text: "Tampered",
      edited: true,
      editedAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("messages").doc("msg_1").update({
      text: "This message was deleted",
      mediaData: "",
      deletedForEveryone: true,
      updatedAt: pastDate(),
    })
  );

  // Bob CAN delete for himself (Delete for Me)
  await assertSucceeds(
    bobDb.collection("messages").doc("msg_1").update({
      deletedForUserIds: [BOB],
      updatedAt: pastDate(),
    })
  );

  // Alice CAN edit her own message
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_1").update({
      text: "Hello Bob! (edited)",
      edited: true,
      editedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Alice CAN delete for everyone
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_1").update({
      text: "This message was deleted",
      mediaData: "",
      deletedForEveryone: true,
      updatedAt: pastDate(500),
    })
  );

  // Charlie cannot read msg_1 or query chat messages without memberIds filter; Alice can with exact filter
  await assertFails(charlieDb.collection("messages").doc("msg_1").get());
  await assertFails(charlieDb.collection("messages").where("chatId", "==", chatId).get());
  await assertSucceeds(aliceDb.collection("messages").where("chatId", "==", chatId).where("memberIds", "array-contains", ALICE).get());

  // Block user: Alice blocks Bob -> Bob CANNOT send messages or calls to Alice
  await assertSucceeds(
    aliceDb.collection("blocked_users").doc(`${ALICE}_${BOB}`).set({
      blockerId: ALICE,
      blockedId: BOB,
      blockedName: "Bob",
      reason: "Spam",
      createdAt: pastDate(400),
    })
  );

  await assertFails(
    bobDb.collection("messages").doc("msg_blocked").set({
      chatId,
      conversationType: "DIRECT",
      senderId: BOB,
      senderName: "Bob",
      recipientId: ALICE,
      memberIds: [ALICE, BOB],
      text: "Should be blocked",
      messageType: "TEXT",
      forwarded: false,
      edited: false,
      deletedForEveryone: false,
      deletedForUserIds: [],
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );

  await assertFails(
    bobDb.collection("calls").doc("call_blocked").set({
      callerId: BOB,
      callerName: "Bob",
      calleeId: ALICE,
      calleeName: "Alice",
      participantIds: [BOB, ALICE],
      callType: "VOICE",
      status: "RINGING",
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
});

test("Group Chat, Admin vs Member Permissions & Leave Group", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();
  const now = pastDate(5000);
  const groupId = "grp_1";

  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("groups").doc(groupId), {
    name: "ABHI Dev Group",
    description: "Team chat",
    createdBy: ALICE,
    adminIds: [ALICE],
    memberIds: [ALICE, BOB],
    lastMessage: "Group created",
    lastSenderName: "Alice",
    lastMessageAt: now,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(aliceDb.collection("group_members").doc(`${groupId}_${ALICE}`), {
    groupId,
    userId: ALICE,
    displayName: "Alice",
    role: "ADMIN",
    visibleToMemberIds: [ALICE, BOB],
    createdAt: now,
    updatedAt: now,
  });
  batch.set(aliceDb.collection("group_members").doc(`${groupId}_${BOB}`), {
    groupId,
    userId: BOB,
    displayName: "Bob",
    role: "MEMBER",
    visibleToMemberIds: [ALICE, BOB],
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Non-member Charlie cannot read group or send message
  await assertFails(charlieDb.collection("groups").doc(groupId).get());
  await assertFails(
    charlieDb.collection("messages").doc("grp_msg_fail").set({
      chatId: groupId,
      conversationType: "GROUP",
      senderId: CHARLIE,
      senderName: "Charlie",
      recipientId: "",
      memberIds: [ALICE, BOB, CHARLIE],
      text: "Intruder",
      messageType: "TEXT",
      forwarded: false,
      edited: false,
      deletedForEveryone: false,
      deletedForUserIds: [],
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Member Bob cannot rename group (admin-only), but CAN leave group
  await assertFails(
    bobDb.collection("groups").doc(groupId).update({ name: "Hijacked", updatedAt: pastDate(500) })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).update({
      memberIds: [ALICE],
      adminIds: [ALICE],
      updatedAt: pastDate(400),
    })
  );
});

test("Statuses, Stories, Friend Requests, Reports, Notifications & Calls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();
  const now = pastDate(5000);
  const expires = Date.now() + 86400000;

  // Status with FRIENDS privacy (only Bob allowed)
  await assertSucceeds(
    aliceDb.collection("statuses").doc("st_1").set({
      userId: ALICE,
      userName: "Alice",
      mediaType: "TEXT",
      text: "Feeling great!",
      backgroundColorHex: "#0F766E",
      privacy: "FRIENDS",
      allowedViewerIds: [BOB],
      viewerCount: 0,
      expiresAtMillis: expires,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("statuses").doc("st_1").get());
  await assertFails(charlieDb.collection("statuses").doc("st_1").get());

  // Status view
  await assertSucceeds(
    bobDb.collection("status_views").doc(`st_1_${BOB}`).set({
      statusId: "st_1",
      statusOwnerId: ALICE,
      viewerId: BOB,
      viewerName: "Bob",
      viewedAt: pastDate(1000),
    })
  );

  // Story + reaction
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      userId: ALICE,
      userName: "Alice",
      mediaType: "TEXT",
      caption: "Coffee morning",
      overlayText: "GM!",
      backgroundColorHex: "#1D4ED8",
      privacy: "EVERYONE",
      allowedViewerIds: [],
      reactionCounts: {},
      userReactions: {},
      viewerCount: 0,
      replyCount: 0,
      expiresAtMillis: expires,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      reactionCounts: { "❤️": 1 },
      userReactions: { [BOB]: "❤️" },
      viewerCount: 1,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("story_views").doc(`story_1_${BOB}`).set({
      storyId: "story_1",
      storyOwnerId: ALICE,
      viewerId: BOB,
      viewerName: "Bob",
      reaction: "❤️",
      replyText: "Nice!",
      viewedAt: pastDate(900),
    })
  );

  // Friend request & accept
  const reqId = `${ALICE}_${BOB}`;
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(reqId).set({
      senderId: ALICE,
      senderName: "Alice",
      receiverId: BOB,
      receiverName: "Bob",
      status: "PENDING",
      participantIds: [ALICE, BOB],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("friend_requests").doc(reqId).update({
      status: "ACCEPTED",
      updatedAt: pastDate(800),
    })
  );
  await assertSucceeds(
    bobDb.collection("friends").doc(reqId).set({
      participantIds: [ALICE, BOB],
      userNames: { [ALICE]: "Alice", [BOB]: "Bob" },
      createdAt: pastDate(800),
      updatedAt: pastDate(800),
    })
  );

  // Report (immutable, only reporter can read)
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE,
      reportedUserId: CHARLIE,
      reportedUserName: "Charlie",
      reason: "Harassment",
      details: "Unwanted messages",
      status: "SUBMITTED",
      createdAt: pastDate(500),
    })
  );
  await assertFails(charlieDb.collection("reports").doc("rep_1").get());
  await assertFails(aliceDb.collection("reports").doc("rep_1").delete());

  // Notifications
  await assertSucceeds(
    aliceDb.collection("notifications").doc("notif_1").set({
      recipientId: BOB,
      senderId: ALICE,
      senderName: "Alice",
      type: "FRIEND_REQUEST",
      title: "New Friend Request",
      body: "Alice sent you a friend request",
      targetId: reqId,
      read: false,
      createdAt: pastDate(500),
    })
  );
  await assertSucceeds(bobDb.collection("notifications").doc("notif_1").update({ read: true }));

  // Calls & Terminal State Locking
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE,
      callerName: "Alice",
      calleeId: BOB,
      calleeName: "Bob",
      participantIds: [ALICE, BOB],
      callType: "VIDEO",
      status: "RINGING",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      endedAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
  // Terminal state cannot be reopened
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      updatedAt: pastDate(100),
    })
  );
});

test("Emergency Contacts and In-Call SOS Live Location Sync", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const now = pastDate(5000);

  // Alice adds an emergency contact; Bob cannot read or write Alice's emergency contacts
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("emergency_contacts").doc("ec_1").set({
      userId: ALICE,
      name: "Mom",
      phoneNumber: "+15551234567",
      relationship: "Mother",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE).collection("emergency_contacts").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE).collection("emergency_contacts").doc("ec_hack").set({
      userId: BOB,
      name: "Fake",
      phoneNumber: "+10000000000",
      relationship: "Other",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates and updates SOS live location alert; Bob cannot read, spoof, or delete it
  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("sos_1").set({
      userId: ALICE,
      userName: "Alice",
      callId: "call_1",
      latitude: 28.6139,
      longitude: 77.2090,
      active: true,
      recipientPhones: ["+15551234567"],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("sos_alerts").doc("sos_1").get());
  await assertFails(
    bobDb.collection("sos_alerts").doc("sos_1").update({
      active: false,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("sos_1").update({
      latitude: 28.6145,
      longitude: 77.2099,
      active: true,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("sos_1").update({
      active: false,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(aliceDb.collection("sos_alerts").doc("sos_1").delete());
});

test("WebRTC SDP & ICE Signaling and Live Location Expiry Enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();
  const now = pastDate(5000);

  // Alice creates a call and attaches WebRTC SDP offer + caller ICE candidate
  await assertSucceeds(
    aliceDb.collection("calls").doc("webrtc_call_1").set({
      callerId: ALICE,
      callerName: "Alice",
      calleeId: BOB,
      calleeName: "Bob",
      participantIds: [ALICE, BOB],
      callType: "VIDEO",
      status: "RINGING",
      iceState: "NEW",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("webrtc_call_1").update({
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      offerType: "OFFER",
      iceState: "CONNECTING",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("webrtc_call_1").collection("caller_candidates").doc("cand_1").set({
      callId: "webrtc_call_1",
      senderId: ALICE,
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:1 1 UDP 2122252543 192.168.1.2 5000 typ host",
      createdAt: pastDate(1500),
    })
  );

  // Bob accepts and writes SDP answer + callee ICE candidate
  await assertSucceeds(
    bobDb.collection("calls").doc("webrtc_call_1").update({
      status: "ACTIVE",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      answerType: "ANSWER",
      iceState: "CONNECTED",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("webrtc_call_1").collection("callee_candidates").doc("cand_2").set({
      callId: "webrtc_call_1",
      senderId: BOB,
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:2 1 UDP 2122252543 192.168.1.3 5001 typ host",
      createdAt: pastDate(900),
    })
  );

  // Outsider Charlie cannot read or inject ICE candidates
  await assertFails(
    charlieDb.collection("calls").doc("webrtc_call_1").collection("caller_candidates").get()
  );
  await assertFails(
    charlieDb.collection("calls").doc("webrtc_call_1").collection("callee_candidates").doc("cand_evil").set({
      callId: "webrtc_call_1",
      senderId: CHARLIE,
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:evil",
      createdAt: pastDate(500),
    })
  );

  // Live location message with expiration fields
  const chatId = `${ALICE}_${BOB}`;
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE, BOB],
      participantNames: { [ALICE]: "Alice", [BOB]: "Bob" },
      lastMessage: "Live location shared",
      lastMessageType: "LOCATION",
      lastSenderId: ALICE,
      lastMessageAt: now,
      createdBy: ALICE,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("messages").doc("loc_msg_1").set({
      chatId,
      conversationType: "DIRECT",
      senderId: ALICE,
      senderName: "Alice",
      recipientId: BOB,
      memberIds: [ALICE, BOB],
      text: "Sharing live location",
      messageType: "LOCATION",
      locationLat: 22.5726,
      locationLng: 88.3639,
      locationLive: true,
      locationExpiresAtMillis: Date.now() + 900000,
      locationUpdatedAtMillis: Date.now(),
      forwarded: false,
      edited: false,
      deletedForEveryone: false,
      deletedForUserIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("messages").doc("loc_msg_1").update({
      locationLat: 22.5731,
      locationLng: 88.3645,
      locationLive: true,
      locationUpdatedAtMillis: Date.now(),
      updatedAt: pastDate(300),
    })
  );

  // FCM token in private doc (owner-only)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("info").set({
      userId: ALICE,
      email: "alice@example.com",
      fcmToken: "fcm_sample_token_12345",
      updatedAt: pastDate(200),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE).collection("private").doc("info").get());
});

