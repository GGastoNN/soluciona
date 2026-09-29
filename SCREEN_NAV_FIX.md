# Soluciona 0.6.1 — ajuste de pantalla y navegación

Cambios principales:

- Se aplican `WindowInsets` nativos para que la cabecera no quede debajo de la barra de estado/notificaciones en Android 15/16.
- La barra inferior de la app y el banner de AdMob quedan por encima de la barra de navegación del sistema.
- Se soporta el gesto Atrás de Android 13+ mediante `OnBackInvokedDispatcher`.
- El botón Atrás del sistema en Android 8–12 usa la misma navegación interna.
- La flecha Atrás de la cabecera, el gesto y el botón del sistema comparten el mismo historial de pantallas.
- La UI adapta padding y tarjetas a teléfonos chicos y pantallas >= 600dp.
- Se aumentan objetivos táctiles de cabecera/navegación a ~44–50px.
- Se agrega el correo oficial de soporte `infosoluciona2026@gmail.com` en cuenta y privacidad.

## Archivos que cambian

- `app/src/main/java/com/soluciona/app/MainActivity.java`
- `app/src/main/assets/index.html`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/styles.xml`
- `app/build.gradle.kts`
- `.github/workflows/android-ci.yml`
- `.github/workflows/play-release.yml`

## Limpieza recomendada en el repo actual

Estos archivos pertenecen a etapas anteriores y no forman parte del paquete 0.6.1 limpio. Si todavía existen en GitHub, eliminarlos manualmente:

- `app/src/main/java/com/soluciona/app/MainActivity.kt`
- `app/src/main/java/com/soluciona/app/AppViewModel.kt`
- `app/src/main/java/com/soluciona/app/LocalRepository.kt`
- `app/src/main/java/com/soluciona/app/Models.kt`
- `app/src/main/java/com/soluciona/app/Theme.kt`
- `app/src/main/java/com/soluciona/app/r.md`
- `app/src/main/assets/r.md`
- `app/src/main/assets/pro_carla.jpg`
- `app/src/main/assets/pro_diego.jpg`
- `app/src/main/assets/pro_martin.jpg`

La compilación actual puede ignorar algunos de ellos, pero conviene retirarlos antes de publicar.
