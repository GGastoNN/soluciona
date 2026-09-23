# Soluciona · Android MVP

Aplicación Android nativa inspirada en las pantallas de referencia provistas. El MVP conecta clientes con prestadores de servicios y está preparado para compilar un APK con GitHub Actions.

## Incluye

- Registro e inicio de sesión de clientes y prestadores.
- Perfil del prestador con servicios, zona, matrícula, descripción y valor de visita.
- Categorías: electricidad, plomería, gas, climatización, cerrajería y construcción.
- Búsqueda/listado de prestadores por categoría, zona, reputación, trabajos y precio.
- Flujo de solicitud: problema → profesional → confirmación → seguimiento.
- Estados: solicitado, confirmado, en camino y finalizado.
- Panel del prestador para aceptar y avanzar trabajos.
- Historial de solicitudes para ambas partes.
- Chat local por solicitud.
- Llamada telefónica al prestador desde el detalle.
- Calificación del servicio al finalizar.
- Persistencia local con SharedPreferences + JSON.
- Datos demo para probar el flujo inmediatamente.
- Workflow `.github/workflows/android.yml` que genera `app-debug.apk` y lo publica como artifact.

## Cuentas demo

- Cliente: `cliente@soluciona.app` / `demo1234`
- Prestador: `martin@soluciona.app` / `demo1234`

## Compilar con GitHub Actions

1. Subí este proyecto a un repositorio de GitHub.
2. Abrí la pestaña **Actions**.
3. Elegí **Build Android APK**.
4. Ejecutá **Run workflow** o hacé un push a `main`.
5. Al terminar, abrí el run y descargá el artifact **soluciona-debug-apk**.

El workflow usa JDK 17, Gradle 8.7, Android SDK 34 y `assembleDebug`.

## Arquitectura actual

Este MVP es **local-first**: usuarios, solicitudes y mensajes viven en el teléfono. Esto permite validar UX y generar un APK sin credenciales externas.

Para producción conviene reemplazar `LocalRepository` por un backend real (por ejemplo Firebase o Supabase) y agregar:

- autenticación segura y recuperación de contraseña;
- base de datos remota y sincronización en tiempo real;
- verificación documental real de identidad/matrícula;
- geolocalización y cálculo real de distancia;
- push notifications;
- carga de fotos, video y audio;
- pagos/señas, facturación y comprobantes;
- disponibilidad/calendario del prestador;
- moderación, soporte, reportes y bloqueo;
- términos, privacidad, tratamiento de datos y cumplimiento legal;
- firma de release y publicación en Play Store.

## Branding

- Nombre: **Soluciona.**
- Package: `com.soluciona.app`
- UI: Jetpack Compose + Material 3.

## Release firmado

El workflow actual genera un APK debug, ideal para instalar y probar. Para una versión release firmada se deben crear un keystore y secretos en GitHub Actions; no subas claves privadas al repositorio.
