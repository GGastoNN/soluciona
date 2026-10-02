# Soluciona 0.9.0 — Kotlin 2.3.0 fix

GitHub Actions llegó a `:app:compileDebugKotlin` y falló porque dependencias actuales de Google/Firebase contienen metadata Kotlin 2.3.0, mientras el proyecto compilaba con Kotlin 2.0.0.

Cambio único:
`org.jetbrains.kotlin.android` 2.0.0 -> 2.3.0
