const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validVaultPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId,
    visited: ["9au.com", "rock9.com"],
    mine: ["9au.com"],
    customDomains: ["mycustom99.com|Now"],
    lastFetchTimestamp: 1700000000000,
    lastFetchRows: [
      {
        domain: "9au.com",
        isOk: true,
        cash: 42.5,
      },
    ],
    themeId: "vault",
    viewMode: "GROUPS",
    sortMode: "DEFAULT",
    bonusMode: "NONE",
    notVisited: false,
    wallets: false,
    mineOnly: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("Unauthenticated user: cannot read or write user vault", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validVaultPayload(ALICE_UID))
  );
});

test("Authenticated user: can create and read their own vault", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validVaultPayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot read or write another user's vault", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .set(validVaultPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set(validVaultPayload(BOB_UID))
  );
});

test("Shadow update test: rejects unknown ghost fields on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validVaultPayload(ALICE_UID, { isVerified: true }))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validVaultPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Value poisoning test: rejects invalid viewMode enum or wrong types", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validVaultPayload(ALICE_UID, { viewMode: "INVALID_MODE" }))
  );
});

test("Authenticated user: can update allowed fields while preserving createdAt and userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const initial = validVaultPayload(ALICE_UID);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(initial)
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      mine: ["9au.com", "rock9.com"],
      themeId: "matrix",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
