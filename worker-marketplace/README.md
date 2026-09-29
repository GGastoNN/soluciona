# Soluciona Marketplace API 0.8.0

Backend Cloudflare Worker para Mercado Pago, QR, split de comisión y referidos.

## D1

1. `wrangler d1 create soluciona-marketplace`
2. Copiar el `database_id` a `wrangler.toml`.
3. `npm install`
4. `npm run db:remote`

## Variables / secretos

En `wrangler.toml` ajustar `MP_REDIRECT_URI` a la URL real del Worker.

Cargar con `wrangler secret put`:

- `MP_CLIENT_ID`
- `MP_CLIENT_SECRET`
- `MP_WEBHOOK_SECRET`
- `TOKEN_ENCRYPTION_KEY` (32 bytes aleatorios codificados en base64)
- `FIREBASE_CLIENT_EMAIL`
- `FIREBASE_PRIVATE_KEY`

`FIREBASE_PRIVATE_KEY` y las credenciales de Mercado Pago nunca van en el APK ni en GitHub.

## Mercado Pago

Crear una aplicación Marketplace, configurar OAuth con el redirect exacto de `MP_REDIRECT_URI` y configurar Webhooks de Orders hacia:

`https://TU_WORKER/v1/mp/webhook`

El profesional conecta su cuenta desde Soluciona. El Worker guarda los tokens OAuth cifrados, crea Store/POS y genera órdenes QR dinámicas. La comisión se calcula en el servidor.

## Comisión inicial

- estándar: `COMMISSION_BPS=1000` = 10%
- beneficio profesional referido: `REFERRAL_PRO_BPS=500` = 5%

## Referidos iniciales

- Cliente: al primer servicio pagado del referido, ambas cuentas reciben 1 Solicitud Prioritaria.
- Profesional referido: primeros 3 trabajos pagados al 5%.
- Profesional que refiere: 2 trabajos al 5% después del primer pago válido del referido.
