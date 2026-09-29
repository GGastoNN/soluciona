# AdMob — Soluciona

La integración usa un banner adaptable anclado en la parte inferior del Activity, debajo del contenido de la app.

## Desarrollo

El build debug usa exclusivamente los IDs de prueba oficiales de Google:

- App ID de prueba: `ca-app-pub-3940256099942544~3347511713`
- Banner de prueba: `ca-app-pub-3940256099942544/9214589741`

No deben reemplazarse por anuncios reales mientras se desarrolla o depura.

## Release

El build release exige propiedades reales:

- `ADMOB_APP_ID`
- `ADMOB_BANNER_ID`

Si faltan, Gradle corta el build deliberadamente.

## Privacidad

Se integra Google User Messaging Platform (UMP). En cada inicio se actualiza el estado de consentimiento. El banner solo se solicita cuando `canRequestAds()` devuelve true.

Cuando AdMob indique que se requiere un punto de entrada de opciones de privacidad, la pantalla de cuenta muestra el acceso correspondiente.
