# Soluciona 0.8.0

## Incluido

- Autenticación Firebase solo por correo + contraseña.
- Verificación de correo obligatoria.
- Ingreso biométrico opcional por dispositivo para clientes y profesionales.
- Tema Sistema / Claro / Oscuro.
- Zonas dinámicas desde Firestore; zona principal + zonas adicionales para profesionales.
- Programa de referidos para clientes y profesionales.
- Base de Mercado Pago Marketplace con OAuth por profesional.
- QR dinámico por servicio, comisión calculada exclusivamente en backend y confirmación por Webhook.
- Cloudflare Worker + D1 para tokens cifrados, pagos y referidos.

## Referidos

### Cliente
Cuando el referido completa su primer servicio pagado:
- referido: 1 Solicitud Prioritaria
- quien invitó: 1 Solicitud Prioritaria

No es dinero, no se retira y no se transfiere.

### Profesional
- profesional referido: 3 trabajos pagados con comisión promocional del 5%
- quien invitó: 2 trabajos con comisión del 5% cuando el referido completa su primer cobro válido
- comisión estándar inicial: 10%

Los porcentajes se configuran en el Worker, no en el APK.

## Biometría

La huella/biometría no sustituye Firebase ni guarda la contraseña. Protege localmente la reapertura de una sesión Firebase existente. Siempre existe la alternativa de correo + contraseña.
