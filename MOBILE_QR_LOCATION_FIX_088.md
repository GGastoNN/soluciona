# Soluciona 0.8.8 — cobro móvil con ubicación actual

## Qué cambia

- Se elimina el `prompt()` nativo del WebView que mostraba el diálogo blanco `La página "file://" dice`.
- El importe se ingresa en una hoja/modal propia de Soluciona, compatible con tema claro/oscuro.
- Al abrir el cobro se solicita la ubicación actual del teléfono.
- La ubicación se usa únicamente para ese cobro y no reemplaza el domicilio habitual del profesional.
- El Worker deja de intentar crear Store/POS al conectar OAuth.
- Store/POS se crean de forma diferida en el primer cobro, usando la dirección del servicio
  (`service_request_private/{requestId}.address`) y las coordenadas actuales del profesional.
- En cobros posteriores se conserva el mismo Store/POS y se actualiza la Store con el punto
  real donde se está prestando el servicio antes de crear el QR.

## Permisos Android

- ACCESS_COARSE_LOCATION
- ACCESS_FINE_LOCATION

Se solicitan en tiempo de ejecución al intentar cobrar.

## Versiones

Android:
- versionCode 20
- versionName 0.8.8

Worker Marketplace:
- health version 0.8.2
- package version 0.8.2

## Verificación rápida

Worker:
`/health` debe devolver:
`{"ok":true,"service":"soluciona-marketplace","version":"0.8.2"}`

App:
`Finalizar y cobrar con QR` debe abrir una hoja visual de Soluciona, pedir ubicación actual
y habilitar `Generar QR de cobro` únicamente con importe + ubicación disponibles.

## Nota de producción

Mercado Pago documenta las Stores como establecimientos físicos y exige ubicación real por
implicancias fiscales. Esta implementación sincroniza la Store con el punto real del servicio,
que encaja técnicamente con profesionales móviles, pero el caso de uso móvil debe validarse
con Mercado Pago antes de habilitar producción masiva.
