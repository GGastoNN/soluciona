# Firebase config obligatorio para com.fixhome.soluciona

El archivo `app/google-services.json` NO se debe editar a mano.

El archivo actualmente observado en el repositorio conserva este `mobilesdk_app_id` anterior:

`1:1010518269428:android:20355f9950cd96fd17ada1`

Ese ID correspondía a la app Android anterior y no debe reutilizarse para `com.fixhome.soluciona`.

## Correcto

1. Firebase Console -> proyecto `soluciona-dev`.
2. Project settings -> Your apps -> Add app -> Android.
3. Package: `com.fixhome.soluciona`.
4. Registrar la app.
5. Descargar el `google-services.json` que Firebase genera para esa nueva app.
6. Reemplazar `app/google-services.json` completo.
7. No modificar `package_name`, `mobilesdk_app_id` ni la API key manualmente.

Los workflows 0.6.3 validan tanto el package como que no se esté reutilizando el App ID anterior.
