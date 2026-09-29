# Soluciona 0.6.0 — Play Release Candidate

Base Android preparada para dejar atrás las cuentas demo y operar con usuarios reales en Firebase Authentication + Cloud Firestore.

## Incluye

- Registro real de cliente: nombre, email, teléfono, contraseña y dirección.
- Registro real de profesional: mismos datos + servicios, zona y matrícula/habilitación declarada.
- Verificación de email real con Firebase Authentication.
- Verificación de teléfono por SMS real con Firebase Phone Auth.
- Recuperación de contraseña.
- Catálogo de zonas y rubros desde Firestore.
- Profesionales visibles solo si `verificationStatus == APPROVED` y `availability == true`.
- Solicitudes reales en Firestore, con dirección separada en `service_request_private`.
- Flujo profesional: pedido -> aceptado -> en camino -> en trabajo -> finalizado.
- Chat básico real en Firestore una vez aceptada la solicitud.
- AdMob banner inferior adaptable.
- UMP (User Messaging Platform) para consentimiento de publicidad.
- Target SDK 36 / Compile SDK 36.
- Workflow GitHub Actions para APK debug y AAB firmado de Play Console.
- R8/minificación en release.

## Importante antes de producción pública

Este proyecto trae `google-services.json` de `soluciona-dev`, útil para pruebas reales e Internal Testing. Para producción pública reemplazarlo por el archivo del proyecto Firebase `soluciona-prod`.

El build `release` NO se genera si faltan IDs reales de AdMob o la upload key. Esto evita publicar accidentalmente anuncios de prueba.

## Build debug

El workflow `.github/workflows/android-ci.yml` genera `Soluciona-0.6.0-debug.apk` usando IDs de anuncio de prueba de Google.

## Build Play Console

El workflow `.github/workflows/play-release.yml` genera:

`Soluciona-0.6.0-play.aab`

Requiere los secretos GitHub descriptos en `PLAY_CONSOLE_CHECKLIST.md`.

## Estado del backend documental

La app ya separa la verificación profesional de la visibilidad pública. La carga privada de matrícula/antecedentes/seguro debe conectarse al Worker R2 antes de aprobar profesionales a escala. Mientras tanto, la operación puede gestionar documentación manualmente desde administración y mantener los perfiles en `PENDING_DOCUMENTS` / `PENDING_REVIEW`.
