import { readFile } from 'node:fs/promises';
import { cert, getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';

const projectId = process.env.FIREBASE_PROJECT_ID || 'soluciona-dev';
const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
if (!raw) throw new Error('Falta FIREBASE_SERVICE_ACCOUNT_JSON.');

const serviceAccount = JSON.parse(raw);
if (!getApps().length) initializeApp({ credential: cert(serviceAccount), projectId });
const db = getFirestore();
const data = JSON.parse(await readFile(new URL('../firebase/seed-data.json', import.meta.url), 'utf8'));
const batch = db.batch();

for (const z of data.zones || []) {
  const { id, ...payload } = z;
  batch.set(db.collection('zones').doc(id), { ...payload, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}
for (const c of data.categories || []) {
  const { id, ...payload } = c;
  batch.set(db.collection('categories').doc(id), { ...payload, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}

await batch.commit();
console.log(`Seed OK: ${(data.zones || []).length} zonas, ${(data.categories || []).length} categorías en ${projectId}.`);
