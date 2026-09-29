# Worker R2 — documentos profesionales

La app 0.6.0 puede subir PDF/JPEG/PNG/WebP directamente a este Worker usando un Firebase ID Token.

Binding R2 requerido:

- `DOCUMENTS` -> bucket privado `soluciona-dev-documents` (o su equivalente de producción)

Variables:

- `FIREBASE_PROJECT_ID`
- `MAX_UPLOAD_BYTES` (default recomendado 10485760)

Deploy:

```bash
npm install
npx wrangler deploy
```

Copiá la URL `https://...workers.dev` y guardala como GitHub Actions Variable:

`DOCUMENTS_API_URL`

Para producción cambiá `FIREBASE_PROJECT_ID` y el bucket al proyecto/entorno de producción antes de desplegar.
