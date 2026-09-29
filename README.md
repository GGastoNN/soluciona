# Soluciona 0.8.0

Android marketplace de servicios para el hogar.

## Android
- package: `com.fixhome.soluciona`
- minSdk 26 / targetSdk 36
- Firebase Auth (correo/contraseña) + Firestore
- biometría opcional
- tema Sistema / Claro / Oscuro
- AdMob + UMP
- zonas dinámicas y múltiples zonas por profesional
- referidos
- Mercado Pago Marketplace/QR preparado mediante backend

## Backends
- `worker-r2/`: documentos profesionales privados en Cloudflare R2
- `worker-marketplace/`: OAuth Mercado Pago, D1, QR, comisión y referidos

## Importante
El ZIP no incluye `app/google-services.json`. Al subir al repo, conservá el archivo actual de Firebase para `com.fixhome.soluciona`.

Seguí `DEPLOY_0.8.0.md` antes de generar el AAB de Play.
