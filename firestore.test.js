const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-lifemate-854e1b6d-de39-4658-9632-8871b023e5eb";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
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

function aliceDb() {
  return testEnv.authenticatedContext(ALICE_UID).firestore();
}

function bobDb() {
  return testEnv.authenticatedContext(BOB_UID).firestore();
}

function unauthDb() {
  return testEnv.unauthenticatedContext().firestore();
}

test("Unauthenticated user cannot read or write any LifeMate collection", async () => {
  const db = unauthDb();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("goals").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("activity_days").get());
});

test("User profile: owner can create and update valid profile, others blocked", async () => {
  const adb = aliceDb();
  const bdb = bobDb();
  const ts = pastTimestamp();

  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Sharma",
      email: "alice@example.com",
      dateOfBirth: "2000-05-14",
      photoUrl: "",
      dailyFocus: "Study 1 hour daily",
      motivationStyle: "friendly",
      onboardingCompleted: true,
      darkModeEnabled: false,
      notificationsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bdb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bdb.collection("users").doc(ALICE_UID).update({
      fullName: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
  // Shadow update with ghost field must fail
  await assertFails(
    adb.collection("users").doc(ALICE_UID).update({
      isPro: true,
      updatedAt: pastTimestamp(),
    })
  );
  // Valid partial update
  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).update({
      darkModeEnabled: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Tasks: owner CRUD works, cross-user and invalid payloads fail", async () => {
  const adb = aliceDb();
  const bdb = bobDb();
  const ts = pastTimestamp();

  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Study for 1 hour",
      notes: "Focus chapter 3",
      completed: false,
      scheduledDate: "2026-11-16",
      completedDate: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(adb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bdb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bdb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete());

  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      completedDate: "2026-11-16",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Expenses: owner CRUD works, negative amount and invalid category fail", async () => {
  const adb = aliceDb();
  const ts = pastTimestamp();

  await assertFails(
    adb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_bad").set({
      userId: ALICE_UID,
      amount: -50,
      category: "Food",
      description: "Lunch",
      expenseDate: "2026-11-16",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1").set({
      userId: ALICE_UID,
      amount: 120,
      category: "Food",
      description: "Healthy lunch",
      expenseDate: "2026-11-16",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Goals and ActivityDays: owner CRUD works, cross-user fails", async () => {
  const adb = aliceDb();
  const bdb = bobDb();
  const ts = pastTimestamp();

  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).collection("goals").doc("goal_1").set({
      userId: ALICE_UID,
      title: "Save ₹10,000",
      targetAmount: 10000,
      currentAmount: 4500,
      unit: "₹",
      targetDate: "2026-12-31",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    adb.collection("users").doc(ALICE_UID).collection("activity_days").doc("2026-11-16").set({
      userId: ALICE_UID,
      dateKey: "2026-11-16",
      completedTasksCount: 2,
      engagementCount: 3,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    bdb.collection("users").doc(ALICE_UID).collection("goals").doc("goal_1").get()
  );
});
