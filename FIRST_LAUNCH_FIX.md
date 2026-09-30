# Soluciona 0.8.3 — primer inicio sin sesión

## Síntoma
Después de una instalación limpia, la app mostraba:
`Conectando con Soluciona…`
y podía quedar allí hasta usar el gesto Atrás.

Después de iniciar sesión y activar biometría, el reingreso funcionaba.

## Causa
El primer inicio también usaba el flujo asíncrono `refreshSession()`.
En una instalación limpia Firebase no tiene usuario persistido, por lo que la
pantalla pública dependía de recibir a tiempo el callback `NO_SESSION`.
Si ese callback no cambiaba el estado visual, `state.screen` permanecía en
`loading`. El gesto Atrás funcionaba porque `homeScreen()` elegía `welcome`
cuando `state.profile` era nulo.

## Corrección
- Se agrega `SolucionaNative.hasSession()`.
- En el arranque de `index.html`:
  - si NO hay usuario Firebase persistido, muestra `welcome` inmediatamente;
  - si SÍ hay usuario persistido, entra al flujo `loading -> refreshSession()`.
- El flujo biométrico 0.8.2 se mantiene sin cambios.
- No se interpreta `hasSession()` como autenticación: la sesión real sigue
  validándose con `refreshSession()`.

## Versión
- versionCode 15
- versionName 0.8.3
