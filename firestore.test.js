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

test("Unauthenticated users cannot read or write folders, documents, or signatures", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/folders`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/documents`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/signatures`).get());

  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/folders`).doc("f1").set({
      userId: ALICE_UID,
      name: "Contratos",
      colorHex: "#E84855",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's collections", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.doc(`users/${ALICE_UID}/folders/f_alice`).set({
      userId: ALICE_UID,
      name: "Trabajo",
      colorHex: "#E84855",
      createdAt: now,
      updatedAt: now,
    });
    await db.doc(`users/${ALICE_UID}/documents/d_alice`).set({
      userId: ALICE_UID,
      title: "Contrato",
      fileName: "contrato.pdf",
      category: "signed",
      favorite: true,
      pageCount: 3,
      fileSizeBytes: 120000,
      localPath: "/data/user/0/app/files/pdfs/contrato.pdf",
      createdAt: now,
      updatedAt: now,
    });
    await db.doc(`users/${ALICE_UID}/signatures/s_alice`).set({
      userId: ALICE_UID,
      name: "Firma Principal",
      inkColorHex: "#1E2430",
      imageBase64: "iVBORw0KGgoAAAANSUhEUg==",
      aspectRatio: 2.4,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/folders/f_alice`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/documents`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/signatures/s_alice`).delete());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/folders/f_alice`).update({
      name: "Robado",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can CRUD folders and validates schema + ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/folders/folder_1`).set({
      userId: ALICE_UID,
      name: "Facturas 2026",
      colorHex: "#D9822B",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Update allowed keys
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/folders/folder_1`).update({
      name: "Facturas Q4",
      colorHex: "#1B8A6B",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/folders/folder_2`).set({
      id: "folder_2",
      userId: ALICE_UID,
      name: "Con ID extra",
      colorHex: "#E84855",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid hex color
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/folders/folder_3`).set({
      userId: ALICE_UID,
      name: "Color invalido",
      colorHex: "red",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection succeeds even when empty or populated
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/folders`).get());

  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/folders/folder_1`).delete());
});

test("Owner can CRUD documents matching Kotlin payloads, folder consistency, and partial updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create target folder first
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/folders/f_legal`).set({
      userId: ALICE_UID,
      name: "Legales",
      colorHex: "#3B5BDB",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject document referencing non-existent folder
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_bad_folder`).set({
      userId: ALICE_UID,
      title: "Anexo",
      fileName: "anexo.pdf",
      folderId: "non_existent",
      category: "merged",
      favorite: false,
      pageCount: 4,
      fileSizeBytes: 50000,
      localPath: "/data/pdfs/anexo.pdf",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid create with optional folderId omitted
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_1`).set({
      userId: ALICE_UID,
      title: "Propuesta Comercial",
      fileName: "propuesta.pdf",
      category: "merged",
      favorite: false,
      pageCount: 6,
      fileSizeBytes: 245000,
      localPath: "/data/pdfs/propuesta.pdf",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update: favorite toggle
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_1`).update({
      favorite: true,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Partial update: rename
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_1`).update({
      title: "Propuesta Final",
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Partial update: move into existing folder
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_1`).update({
      folderId: "f_legal",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_1`).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/documents/doc_future`).set({
      userId: ALICE_UID,
      title: "Futuro",
      fileName: "futuro.pdf",
      category: "imported",
      favorite: false,
      pageCount: 1,
      fileSizeBytes: 1024,
      localPath: "/data/pdfs/futuro.pdf",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );
});

test("Owner can CRUD signatures with bounded Base64 size", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/signatures/sig_1`).set({
      userId: ALICE_UID,
      name: "Firma Personal",
      inkColorHex: "#1E2430",
      imageBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
      aspectRatio: 2.5,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/signatures/sig_1`).update({
      name: "Rúbrica Oficial",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject oversized Base64 payload (> 300,000 chars)
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/signatures/sig_huge`).set({
      userId: ALICE_UID,
      name: "Gigante",
      inkColorHex: "#1E2430",
      imageBase64: "A".repeat(300005),
      aspectRatio: 2.0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/signatures/sig_1`).delete());
});
