# Firebase — despliegue inicial

## Firestore

Desde la carpeta `firebase/`, desplegar reglas e índices con Firebase CLI:

```bash
firebase login
firebase use soluciona-dev
firebase deploy --only firestore:rules,firestore:indexes
```

Para producción cambiar a `soluciona-prod`.

## Catálogo inicial

El archivo `firebase/seed-data.json` contiene las zonas y categorías iniciales.

El script `tools/seed-firestore.mjs` usa Firebase Admin y Application Default Credentials. No contiene claves privadas.

Ejemplo en un entorno autenticado con Google Cloud:

```bash
export FIREBASE_PROJECT_ID=soluciona-dev
node tools/seed-firestore.mjs
```

No se debe incluir una service-account privada en el repositorio.
