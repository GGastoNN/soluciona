# Soluciona 0.9.0 — Manifest merge fix

El SDK Checkout de Mercado Pago declara `android:allowBackup="true"`.
Soluciona mantiene `android:allowBackup="false"` por decisión propia y fuerza ese valor con:

`xmlns:tools="http://schemas.android.com/tools"`

`tools:replace="android:allowBackup"`
