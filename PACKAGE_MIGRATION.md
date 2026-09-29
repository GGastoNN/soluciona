# Migración de package — com.fixhome.soluciona

El identificador anterior `com.soluciona.app` no debe utilizarse para la nueva publicación.

## Firebase

Registrar una segunda app Android dentro del proyecto Firebase existente:

- Android package name: `com.fixhome.soluciona`
- App nickname sugerido: `Soluciona Android FixHome`

Descargar el archivo generado por Firebase y reemplazar en GitHub:

`app/google-services.json`

El CI 0.6.2 comprueba el package y se detiene con un mensaje explícito si sigue presente el JSON antiguo.

## GitHub

Eliminar, si todavía existe, el directorio viejo:

`app/src/main/java/com/soluciona/app/`

El proyecto 0.6.2 usa:

`app/src/main/java/com/fixhome/soluciona/`

Gradle también excluye el directorio Java viejo para evitar que una carga manual deje clases antiguas dentro del build.

## Play Console

Crear/usar la ficha cuya identidad de aplicación sea:

`com.fixhome.soluciona`

El package de Android no se puede cambiar después de publicar una app bajo ese identificador; por eso esta migración debe quedar cerrada antes de la primera versión pública.
