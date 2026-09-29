import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectId = process.env.FIREBASE_PROJECT_ID;
if (!projectId) throw new Error('Definí FIREBASE_PROJECT_ID.');

initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();
const here = path.dirname(fileURLToPath(import.meta.url));
const seed = JSON.parse(fs.readFileSync(path.join(here, '../firebase/seed-data.json'), 'utf8'));

const batch = db.batch();
for (const z of seed.zones) {
  const { id, ...data } = z;
  batch.set(db.collection('zones').doc(id), { ...data, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}
for (const c of seed.categories) {
  const { id, ...data } = c;
  batch.set(db.collection('categories').doc(id), { ...data, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}
await batch.commit();
console.log(`Seed aplicado en ${projectId}: ${seed.zones.length} zonas, ${seed.categories.length} categorías.`);
