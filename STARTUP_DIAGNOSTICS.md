# Diagnóstico de cierre al iniciar — 0.6.2

La versión anterior podía compilar un release si los valores AdMob existían aunque estuvieran intercambiados o mal formados. El SDK de anuncios se carga muy temprano en el proceso Android, por lo que una configuración inválida puede producir un cierre antes de que la pantalla HTML llegue a mostrarse.

No fue posible confirmar el stack trace del dispositivo sin Logcat, pero 0.6.2 elimina ese punto ciego:

- release valida `ADMOB_APP_ID` con formato `ca-app-pub-...~...`;
- release valida `ADMOB_BANNER_ID` con formato `ca-app-pub-.../...`;
- UMP/AdMob se inicializan después de cargar la UI;
- los fallos recuperables de anuncios ocultan el banner en lugar de cerrar la app;
- la inicialización Firebase está protegida y muestra una pantalla de error/reintento si no puede iniciar;
- el CI rechaza un `google-services.json` cuyo package no sea `com.fixhome.soluciona`.

Si un dispositivo vuelve a cerrar la app antes de mostrar la presentación, capturar Logcat con filtro `AndroidRuntime`, `SolucionaStartup` y `SolucionaAds`.
