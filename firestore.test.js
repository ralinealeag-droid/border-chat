const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const CHARLIE_UID = "charlie_uid_303";

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated context: denied on all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("publicProfiles").where("discoverable", "==", true).get());
  await assertFails(unauth.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
});

test("Private users PII & granular read receipt settings: owner-only read/write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      readReceiptsEnabled: true,
      groupReadReceiptsEnabled: true,
      chatReadReceiptOverrides: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Granular privacy settings update by owner succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      readReceiptsEnabled: false,
      groupReadReceiptsEnabled: true,
      chatReadReceiptOverrides: { chat_dm_1: "ALWAYS_ON", chat_grp_2: "ALWAYS_OFF" },
      updatedAt: pastDate(1000),
    })
  );

  // Invalid type for readReceiptsEnabled rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      readReceiptsEnabled: "no",
      updatedAt: pastDate(500),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user PII / privacy settings read & write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      readReceiptsEnabled: true,
      updatedAt: pastDate(500),
    })
  );
});

test("Public profiles: discoverable query succeeds; unfiltered list fails; cross-user update fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("publicProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      statusText: "Terenkripsi End-to-End",
      publicKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAlicePublicKeyBase64",
      online: true,
      discoverable: true,
      lastSeen: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("publicProfiles").where("discoverable", "==", true).get());
  await assertFails(bobDb.collection("publicProfiles").get());
  await assertFails(
    bobDb.collection("publicProfiles").doc(ALICE_UID).update({
      publicKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEHijackedKeyBase64",
      updatedAt: pastDate(1000),
    })
  );
});

test("Chats & E2EE messages: members can create/read/edit/delete own messages; non-members blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_group_1").set({
      type: "GROUP",
      title: "Tim Privasi",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      encryptedKeys: { [ALICE_UID]: "wrapA", [BOB_UID]: "wrapB" },
      lastMessagePreview: "Grup terenkripsi dibuat",
      lastMessageReadByUids: [],
      createdBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(bobDb.collection("chats").get());
  await assertFails(charlieDb.collection("chats").doc("chat_group_1").get());

  // Bob sends encrypted message with initial self read receipt
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_1").set({
      chatId: "chat_group_1",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      encryptedContent: "ciphertext_base64_payload",
      iv: "iv_12bytes_b64",
      encryptionVersion: "AES-256-GCM",
      readByUids: [BOB_UID],
      readBy: { [BOB_UID]: ts },
      edited: false,
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject sender spoofing Alice's read receipt upon creation
  await assertFails(
    bobDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_spoof").set({
      chatId: "chat_group_1",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      encryptedContent: "cipher",
      iv: "iv",
      encryptionVersion: "AES-256-GCM",
      readByUids: [BOB_UID, ALICE_UID],
      readBy: { [BOB_UID]: ts, [ALICE_UID]: ts },
      edited: false,
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject message create containing @DocumentId 'id'
  await assertFails(
    bobDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_bad").set({
      id: "msg_bad",
      chatId: "chat_group_1",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      encryptedContent: "cipher",
      iv: "iv",
      encryptionVersion: "AES-256-GCM",
      readByUids: [BOB_UID],
      edited: false,
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_group_1").collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_group_1").collection("messages").get()
  );

  // Bob edits own message; Alice cannot edit Bob's message
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_1").update({
      encryptedContent: "edited_ciphertext_b64",
      iv: "new_iv_b64",
      edited: true,
      editedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_1").update({
      encryptedContent: "hijacked",
      iv: "iv2",
      edited: true,
      editedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Bob deletes own message (soft tombstone)
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_1").update({
      encryptedContent: "",
      iv: "",
      messageType: "DELETED",
      deleted: true,
      updatedAt: pastDate(500),
    })
  );

  // Cannot edit a deleted message
  await assertFails(
    bobDb.collection("chats").doc("chat_group_1").collection("messages").doc("msg_1").update({
      encryptedContent: "revived",
      iv: "iv3",
      edited: true,
      editedAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );
});

test("Read receipts: members append own read receipt safely; forging or deleting others' receipts fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(10000);
  const readTs = pastDate(2000);

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_dm_rr").set({
      type: "DIRECT",
      title: "Alice & Bob",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      encryptedKeys: { [ALICE_UID]: "kA", [BOB_UID]: "kB" },
      lastMessagePreview: "Halo",
      lastSenderId: BOB_UID,
      lastMessageId: "msg_rr_1",
      lastMessageReadByUids: [BOB_UID],
      createdBy: BOB_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_dm_rr").collection("messages").doc("msg_rr_1").set({
      chatId: "chat_dm_rr",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      encryptedContent: "cipher_hello",
      iv: "iv_hello",
      encryptionVersion: "AES-256-GCM",
      readByUids: [BOB_UID],
      readBy: { [BOB_UID]: ts },
      edited: false,
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice marks Bob's message as read (append-only own UID + readBy timestamp)
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_dm_rr").collection("messages").doc("msg_rr_1").update({
      readByUids: [BOB_UID, ALICE_UID],
      [`readBy.${ALICE_UID}`]: readTs,
      updatedAt: readTs,
    })
  );

  // Alice updates chat's lastMessageReadByUids cleanly
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_dm_rr").update({
      lastMessageReadByUids: [BOB_UID, ALICE_UID],
      updatedAt: readTs,
    })
  );

  // Alice cannot remove Bob from readByUids
  await assertFails(
    aliceDb.collection("chats").doc("chat_dm_rr").collection("messages").doc("msg_rr_1").update({
      readByUids: [ALICE_UID],
      updatedAt: pastDate(500),
    })
  );

  // Alice cannot forge Charlie's readBy timestamp
  await assertFails(
    aliceDb.collection("chats").doc("chat_dm_rr").collection("messages").doc("msg_rr_1").update({
      readByUids: [BOB_UID, ALICE_UID],
      [`readBy.${CHARLIE_UID}`]: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Non-member Charlie cannot mark message as read
  await assertFails(
    charlieDb.collection("chats").doc("chat_dm_rr").collection("messages").doc("msg_rr_1").update({
      readByUids: [BOB_UID, ALICE_UID, CHARLIE_UID],
      [`readBy.${CHARLIE_UID}`]: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Live locations & Call signaling: owner updates live location; terminal call state locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("chats").doc("chat_1").set({
      type: "DIRECT",
      title: "Alice & Bob",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      encryptedKeys: { [ALICE_UID]: "kA", [BOB_UID]: "kB" },
      lastMessagePreview: "Mulai",
      createdBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("liveLocations").doc(ALICE_UID).set({
      chatId: "chat_1",
      sharerId: ALICE_UID,
      sharerName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      encryptedCoords: "enc_lat_lng_payload",
      iv: "iv_loc",
      active: true,
      expiresAtEpochMs: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_1").collection("liveLocations")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_1").collection("liveLocations").doc(ALICE_UID).update({
      active: false,
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      chatId: "chat_1",
      chatTitle: "Alice & Bob",
      callerId: ALICE_UID,
      callerName: "Alice",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      e2eeSafetyCode: "48291-73014-99201",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "CONNECTED",
      updatedAt: pastDate(2000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "CONNECTED",
      updatedAt: pastDate(500),
    })
  );
});
