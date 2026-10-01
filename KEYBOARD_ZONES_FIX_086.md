# Soluciona 0.8.6 — teclado y zonas en registro profesional

## Problema
En la pantalla `registerPro`, `features.js` usaba un `MutationObserver` que volvía a
pedir las zonas cada vez que el `<select id="proZone">` estaba vacío. Si Firestore
devolvía cero zonas (o todavía no había terminado de cargar), ocurría un ciclo:

`loadZones -> zones event -> render -> DOM mutation -> loadZones -> ...`

Cada `render()` reemplaza `#screen.innerHTML`, destruye el `<input>` que tenía foco y
Android cierra inmediatamente el teclado. Por eso el teclado parecía "rebotar".

## Corrección
- La carga de zonas queda limitada a una solicitud a la vez y una carga inicial.
- Un resultado vacío ya no dispara un loop de consultas/render.
- Las respuestas asíncronas de settings/zones no redibujan Login/Registro mientras
  el usuario está escribiendo.
- `proZone` se actualiza directamente sin reconstruir todo el formulario.
- Si no hay zonas en Firestore, el selector muestra `No hay zonas cargadas` y queda
  deshabilitado en vez de consultar infinitamente.
- El WebView queda explícitamente focusable para mejorar compatibilidad con teclados
  de distintos fabricantes.

## Versión
- versionCode 18
- versionName 0.8.6
