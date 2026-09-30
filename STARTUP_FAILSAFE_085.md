# Soluciona 0.8.5 — fail-safe definitivo de primer inicio

## Qué cambia
La pantalla pública ya no depende solamente del JavaScript ni del query string.
MainActivity guarda en memoria si la página fue abierta esperando una sesión.

Cuando Android abre la app SIN sesión Firebase:
- carga index.html?session=0;
- al terminar de cargar la página, MainActivity fuerza `state.screen = welcome`;
- nunca ejecuta un retry de sesión desde Android;
- aunque cambie el orden de ejecución del WebView, no puede quedar indefinidamente
  en “Conectando con Soluciona…”.

Cuando Android abre la app CON sesión Firebase:
- mantiene el flujo biométrico;
- carga index.html?session=1;
- intenta restaurar la sesión;
- si en 2.2 segundos no existe perfil, el loader cae automáticamente a Welcome;
- cualquier error de `session` también recupera Welcome en vez de dejar el loader.

No se cambia autenticación, datos, biometría, zonas, referidos ni Mercado Pago.

## Versión
- versionCode 17
- versionName 0.8.5
