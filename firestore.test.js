const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_eishlar_123";
const BOB_UID = "bob_eishlar_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot list, get, or write conversations or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized",
        modelId: "gemini-4-hydrogen",
        messageCount: 0,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner: valid Kotlin conversation and message payloads succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc("conv_01");

  // Create conversation (matches ChatRepository.createConversation)
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Toshkent ob-havosi",
      modelId: "gemini-4-hydrogen",
      lastMessagePreview: "Bugungi ob-havo qanday?",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create message (matches ChatRepository.saveMessage without @DocumentId id)
  await assertSucceeds(
    convRef.collection("messages").doc("msg_01").set({
      userId: ALICE_UID,
      role: "USER",
      text: "Bugungi ob-havo qanday?",
      modelId: "gemini-4-hydrogen",
      createdAt: now,
    })
  );

  // Create model message with optional thinkingText & groundingUrls
  await assertSucceeds(
    convRef.collection("messages").doc("msg_02").set({
      userId: ALICE_UID,
      role: "MODEL",
      text: "Toshkentda bugun quyoshli.",
      thinkingText: "Google Search orqali tekshirildi.",
      modelId: "gemini-4-hydrogen",
      groundingUrls: ["https://weather.com"],
      mapsPlaceNames: ["Toshkent"],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update on conversation (preserves userId & createdAt)
  await assertSucceeds(
    convRef.update({
      title: "Toshkent ob-havosi yangilandi",
      modelId: "gemini-4-hydrogen",
      lastMessagePreview: "Toshkentda bugun quyoshli.",
      messageCount: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List queries match Kotlin repository
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .orderBy("updatedAt", "desc")
      .get()
  );
  await assertSucceeds(
    convRef.collection("messages").orderBy("createdAt", "asc").get()
  );
});

test("Rejects @DocumentId id field, immutable mutations, future timestamps, and orphaned messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Extra "id" key rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad_id")
      .set({
        id: "conv_bad_id",
        userId: ALICE_UID,
        title: "Bad",
        modelId: "gemini-4-hydrogen",
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_future")
      .set({
        userId: ALICE_UID,
        title: "Future",
        modelId: "gemini-4-hydrogen",
        messageCount: 0,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Orphaned message without parent conversation rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("nonexistent_conv")
      .collection("messages")
      .doc("msg_orphan")
      .set({
        userId: ALICE_UID,
        role: "USER",
        text: "Orphan",
        modelId: "gemini-4-hydrogen",
        createdAt: now,
      })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's conversations or messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const convRef = db
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv");
    await convRef.set({
      userId: ALICE_UID,
      title: "Private Chat",
      modelId: "gemini-4-hydrogen",
      messageCount: 1,
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
    await convRef.collection("messages").doc("m1").set({
      userId: ALICE_UID,
      role: "USER",
      text: "Secret",
      modelId: "gemini-4-hydrogen",
      createdAt: new Date(Date.now() - 4000),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .collection("messages")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .delete()
  );
});
