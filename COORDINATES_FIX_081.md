# Soluciona Marketplace 0.8.1 — coordenadas obligatorias para Store/POS

Mercado Pago exige `latitude` y `longitude` reales al crear una sucursal QR.

## Datos esperados en Firestore

Documento:

`users_private/{uid-profesional}`

Dentro del mapa `address` deben existir, además de calle/numero/ciudad/provincia:

- `latitude` (number)
- `longitude` (number)

Ejemplo conceptual:

address:
  street: "..."
  number: "..."
  city: "..."
  province: "..."
  latitude: -32.000000
  longitude: -61.000000

Usar las coordenadas reales del domicilio/punto de cobro del profesional.
No usar coordenadas inventadas.

## Cambio del Worker

`setupStoreAndPos()`:
- lee `address.latitude` / `address.longitude`;
- valida rangos;
- los envía a Mercado Pago en `location`;
- si faltan, guarda un `setup_error` claro y el QR responde con error de configuración
  en vez de intentar crear una Store inválida.

Health esperado después del deploy:

{"ok":true,"service":"soluciona-marketplace","version":"0.8.1"}
