# Soluciona 0.9.0

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
El ZIP incluye `app/google-services.json`. Confirmá que corresponde al proyecto Firebase del entorno antes de compilar. Las versiones de los Workers son independientes de la versión Android.

Seguí `DEPLOY_0.8.0.md` antes de generar el AAB de Play.

## Comandos desde la raíz

Instalá dependencias por separado con `npm --prefix worker-r2 install` y `npm --prefix worker-marketplace install`.
Usá `npm run dev:r2`, `npm run dev:marketplace`, `npm run typecheck:r2` y `npm run typecheck:marketplace`.
Para desplegar, `npm run deploy:r2` o `npm run deploy:marketplace`. Revisá bindings y secretos de cada Worker antes de desplegar.
Android requiere JDK 17, SDK 36 y un Gradle compatible con AGP 8.13.2. Este archivo todavía no incluye Wrapper: usar Android Studio o generar el Wrapper con la instalación local de Gradle validada.

Ver `CAMBIOS_Y_VALIDACION.md` para alcance y pendientes.
