# Mercado Pago Marketplace — puesta en marcha

1. Crear una aplicación de Mercado Pago para modelo Marketplace / OAuth.
2. Configurar Redirect URI exactamente igual a `MP_REDIRECT_URI` del Worker.
3. Crear D1 `soluciona-marketplace`, copiar su ID a `worker-marketplace/wrangler.toml` y ejecutar `schema.sql`.
4. Cargar secretos del Worker:
   - MP_CLIENT_ID
   - MP_CLIENT_SECRET
   - MP_WEBHOOK_SECRET
   - TOKEN_ENCRYPTION_KEY
   - FIREBASE_CLIENT_EMAIL
   - FIREBASE_PRIVATE_KEY
5. Desplegar `worker-marketplace`.
6. En Mercado Pago configurar Webhook de Orders a `https://TU_WORKER/v1/mp/webhook`.
7. En GitHub Actions crear Repository Variable `MARKETPLACE_API_URL=https://TU_WORKER`.
8. Publicar las reglas de `firebase/firestore.rules`.
9. Ejecutar `Seed Firebase Catalog` para cargar zonas/categorías.

## Importante

Para que Mercado Pago cree la sucursal/POS, el profesional debe tener una dirección real completa en su perfil. Mercado Pago puede exigir datos adicionales de ubicación según la cuenta/país; si la creación del POS falla, Soluciona conserva `setupError` y no habilita el QR hasta resolverlo.

No subir jamás secretos de Mercado Pago o la cuenta de servicio Firebase al APK ni al repositorio.
