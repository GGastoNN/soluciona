# Soluciona 0.8.7 — Marketplace habilitado en Debug

## Causa
El APK Debug de Android CI se compilaba sin pasar `MARKETPLACE_API_URL` a Gradle.
Por eso `BuildConfig.MARKETPLACE_API_URL` quedaba vacío y `FeaturesBridge` informaba
`marketplaceConfigured=false`, aunque el Worker de Cloudflare ya estuviera operativo.

## Corrección
Android CI ahora inyecta en el APK Debug:
- `MARKETPLACE_API_URL` desde GitHub Actions Variables.
- `DOCUMENTS_API_URL` desde GitHub Actions Variables.

La URL no es un secreto; las credenciales Mercado Pago siguen únicamente en Cloudflare.

## GitHub requerido
Settings -> Secrets and variables -> Actions -> Variables:

MARKETPLACE_API_URL=https://soluciona-marketplace-api.cabasgaston.workers.dev

Si ya existe `DOCUMENTS_API_URL`, se reutiliza automáticamente.

## Versión
- versionCode 19
- versionName 0.8.7
