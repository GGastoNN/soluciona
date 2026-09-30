# Soluciona 0.8.4 — arranque determinista

El arreglo 0.8.3 seguía dejando un punto frágil: index.html preguntaba a Android
por la sesión mediante una llamada JavaScriptInterface síncrona (`hasSession()`).

En 0.8.4 se elimina por completo esa decisión del JavaScript.

## Nuevo flujo

MainActivity consulta Firebase antes de cargar index.html:

- Firebase sin usuario -> index.html?session=0
- Firebase con usuario -> index.html?session=1
- Biometría correcta -> index.html?session=1
- Elegir correo/contraseña -> signOut + index.html?session=0

index.html no puede quedarse en `loading` en una instalación limpia, porque con
`session=0` muestra Welcome directamente y ni siquiera ejecuta refreshSession().

La pantalla “Conectando con Soluciona…” queda reservada únicamente para una
sesión Firebase realmente persistida.

## Versión
- versionCode 16
- versionName 0.8.4
