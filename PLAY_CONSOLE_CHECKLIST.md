# Checklist Play Console — Soluciona 0.6.0

## 1. Firebase producción

Antes de Producción pública:

1. Crear proyecto `soluciona-prod`.
2. Registrar Android package `com.soluciona.app`.
3. Descargar y reemplazar `app/google-services.json`.
4. Habilitar Authentication Email/Password.
5. Habilitar Phone Auth si se usará SMS.
6. Crear Firestore.
7. Desplegar `firebase/firestore.rules` e índices.
8. Cargar `zones` y `categories`.

Para Internal Testing puede seguir usándose `soluciona-dev` mientras se valida el flujo.

## 2. AdMob

Crear la aplicación Android `com.soluciona.app` en AdMob y crear una unidad Banner.

Guardar en GitHub Secrets:

- `ADMOB_APP_ID` -> formato `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`
- `ADMOB_BANNER_ID` -> formato `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ`

Configurar en AdMob > Privacy & messaging el mensaje de consentimiento que corresponda. La app integra UMP y solo pide anuncios cuando el SDK indica que puede hacerlo.

## 3. Upload key

NO subir el archivo `.jks` al repositorio.

Convertirlo a Base64 localmente y guardar en GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Los datos de la upload key entregada están en el TXT separado `Soluciona-upload-key-CREDENTIALS.txt`.

## 4. Build AAB

GitHub > Actions > `Build Play Console AAB` > Run workflow.

Descargar el artifact `Soluciona-0.6.0-play` y subir `Soluciona-0.6.0-play.aab` a Play Console.

## 5. Play App Signing

Activar Play App Signing. Tras el primer AAB:

- Play Console > Integridad de la app > Firma de aplicaciones.
- Copiar SHA-1 y SHA-256 del **certificado de firma de la aplicación**.
- Agregarlos a Firebase Console para `com.soluciona.app`.

Esto es especialmente importante para autenticación telefónica / Play Integrity.

## 6. Ficha y cumplimiento

Preparar antes de Producción:

- Nombre: Soluciona
- Descripción corta y completa.
- Ícono 512x512.
- Feature graphic 1024x500.
- Capturas de teléfono reales.
- URL pública de Política de Privacidad.
- Email de soporte.
- Formulario Data safety acorde a Firebase, teléfono, dirección, mensajes y publicidad.
- Declaración de anuncios: Sí, la app contiene anuncios.
- Clasificación de contenido.
- Público objetivo.
- Acceso a la app para revisión si existe login obligatorio.

## 7. Versiones

- package: `com.soluciona.app`
- versionCode: `6`
- versionName: `0.6.0`
- targetSdk: `36`

Cada siguiente publicación debe aumentar `versionCode`.
