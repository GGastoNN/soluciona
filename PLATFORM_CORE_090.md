# Soluciona 0.9.0 — Platform Core

Esta versión cambia el modelo de cobro: el profesional ya no crea un pago directamente.
Primero envía una **Solicitud de cobro** al cliente. El cliente paga dentro del ecosistema
Soluciona con tarjeta, mientras que el QR híbrido queda como alternativa presencial.

## Flujo

1. Profesional: IN_PROGRESS -> "Enviar cobro al cliente".
2. Backend crea `payment_requests` y cambia el servicio a AWAITING_PAYMENT.
3. Cliente ve "Pagar ahora".
4. Tarjeta:
   - backend crea Order `online`, `processing_mode=manual`;
   - devuelve `orderId` + `clientToken`;
   - Android abre Mercado Pago SDK Checkout nativo;
   - los datos PCI nunca pasan por WebView ni por nuestro backend.
5. QR:
   - el profesional puede pedir ubicación y mostrar QR híbrido;
   - usa la misma solicitud de cobro.
6. Webhook de Mercado Pago confirma el pago y el backend marca el servicio COMPLETED.

## D1 obligatorio

Ejecutar una vez:

`npx wrangler d1 execute soluciona-marketplace --remote --file=migrations/0002_platform_payments.sql`

desde `worker-marketplace`.

## GitHub Actions Variable requerida

`MP_PUBLIC_KEY`

Usar la Public Key de la integración Mercado Pago correspondiente al ambiente de prueba.
No es un secreto, pero se inyecta en build y no queda hardcodeada en Git.

## Android

- Kotlin 2.0.0
- Mercado Pago SDK Android BOM 1.0.0
- sdk-android + checkout
- CardTransaction nativo
- minSdk existente 26 (SDK MP requiere 23+)

## Versiones

Android:
- versionCode 22
- versionName 0.9.0

Worker:
- version 0.9.0

## Alcance

Este es el núcleo transaccional del ecosistema. Próximos bloques:
- FCM/notificaciones y tracking;
- agenda/disponibilidad;
- presupuestos/señas;
- recibos/reembolsos/reclamos;
- favoritos/repetir servicio;
- panel admin y antifraude;
- Wallet Connect / medios adicionales según habilitación de Mercado Pago.
