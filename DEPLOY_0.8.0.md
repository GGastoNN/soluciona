# Orden de despliegue 0.8.0

1. Subir/reemplazar los archivos del parche en GitHub, conservando `app/google-services.json` actual.
2. Ejecutar Android CI y probar APK debug.
3. Publicar `firebase/firestore.rules` e índices.
4. Crear secret GitHub `FIREBASE_SERVICE_ACCOUNT_JSON` y ejecutar workflow `Seed Firebase Catalog`.
5. Crear D1 y desplegar `worker-marketplace` siguiendo MARKETPLACE_SETUP.md.
6. Crear variable GitHub `MARKETPLACE_API_URL`.
7. Probar Mercado Pago con cuentas/credenciales de prueba antes de producción.
8. Probar: registro cliente, registro profesional, zonas, biometría, temas, referidos, OAuth MP, QR, webhook y finalización del servicio.
9. Recién después generar el AAB firmado 0.8.0.
