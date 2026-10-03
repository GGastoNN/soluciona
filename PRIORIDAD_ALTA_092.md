# Soluciona 0.9.2 — DNI, fotos, presupuestos y avisos

Esta actualización está hecha sobre los archivos de la actualización 02. Incluye solo cambios del proyecto Android y sus servidores. No usa RENAPER ni el administrador VB.NET.

## Comportamiento

- Perfil → Mi DNI: frente y dorso, consentimiento y resultado de revisión manual para ambos roles. Documentación pendiente no bloquea servicios. Las imágenes solo están disponibles al titular y al equipo con permisos de consola.
- Solicitud → Fotos y presupuesto: el cliente agrega hasta 6 fotos mientras el pedido está REQUESTED, antes de que se acepte. Después de publicar, se abre esa pantalla. Las fotos las pueden ver profesionales aprobados compatibles cuando el pedido esté abierto, o los participantes asignados. Evitar fotos con documentos o información privada.
- Profesional asignado: propone visita, mano de obra, materiales y alcance. Importes ARS con hasta 2 decimales; máximo total $1.000.000. El cliente acepta o rechaza una revisión concreta. Se requiere aceptación antes de iniciar los nuevos servicios.
- Adicionales durante IN_PROGRESS: se ingresan solo los nuevos importes, se suman al total previo y requieren otra aceptación. Rechazarlos conserva el total previo. Presupuestos y respuestas quedan en el historial del pedido.
- El Worker de pagos valida que el importe sea el total aprobado y que no haya propuesta pendiente; también controla sesiones de tarjeta, pagos de tarjeta y QR. La pantalla precarga ese importe. Los pedidos antiguos sin budgetRequired conservan su funcionamiento hasta que se les proponga presupuesto.
- Perfil → Activar notificaciones: pide permiso en Android 13+ y registra el dispositivo. Los avisos se generan en el servidor para pedidos compatibles, aceptación/estado, mensajes, presupuestos, revisión de DNI y cambios de pago. No contienen imágenes, dirección, texto del chat ni detalles del DNI. Las escuchas se liberan al salir de las pantallas.

Las notificaciones requieren permiso del usuario, Google Play Services, conectividad y las funciones publicadas. FCM no garantiza entrega inmediata ni lectura. Los eventos se procesan con reintentos y registro de entrega; ante fallos o ejecuciones simultáneas puede repetirse un aviso. Consultar el estado del pedido es la fuente de verdad.

## Archivos y privacidad

R2 sigue privado, en el bucket actual de documentos:

| Contenido | Ruta R2 | Registro Firestore |
|---|---|---|
| DNI | identities/<uid>/<identificador aleatorio> | identity_submissions/<uid> |
| Fotos | request-photos/<requestId>/<uid>/<photoId> | service_requests/<requestId>/attachments/<photoId> |
| Presupuestos | No hay archivo | service_requests/<requestId>/budgets/<revision> |
| Dispositivos | No hay archivo | users/<uid>/notification_devices/<deviceId> |

Solo se aceptan JPG/PNG/WebP de hasta 10 MB para estos nuevos tipos, comprobando también la firma del formato. El Worker guarda el archivo y confirma metadatos en Firestore; si falla esa confirmación, intenta eliminar el archivo recién cargado. Un reemplazo exitoso de DNI elimina la imagen anterior de esa cara. Revisar objetos huérfanos si hay interrupciones de red. La subida actual es en primer plano y permite volver a intentar; no usa WorkManager ni continúa garantizadamente después de cerrar la app.

No se guarda número de DNI ni se hace OCR, reconocimiento facial o validación automática. La etiqueta de aprobación es Documentación revisada. No publicar el bucket mediante r2.dev ni dominio público. Publicar también la política de privacidad web actualizada.

## Instalación de los archivos de actualización

Cerrar Android Studio y hacer respaldo del proyecto. Copiar el contenido de `archivos` sobre la raíz de soluciona-main conservando rutas. `manifest.json` contiene el hash base y nuevo de cada archivo. Si modificaste un archivo localmente, integrar los cambios en lugar de sobrescribirlo. No copiar wrangler.toml ni gradle.properties de otro proyecto: esta actualización conserva tus URLs, bindings, claves y firma.

No aplicar archivos del paquete VB.NET. Si ya aplicaste esas reglas o Workers, comparar/fusionar esta actualización; su base es actualización 02, no el paquete administrativo cancelado.

## Despliegue desde tu PC (PowerShell)

Se necesita Node 22.18+ o 24 para ejecutar todas las pruebas, Firebase CLI actual y Cloudflare Wrangler. Usar npm.cmd/npx.cmd si PowerShell bloquea los scripts .ps1.

1. Firebase debe tener plan Blaze con facturación habilitada para publicar Cloud Functions. El paquete no activa facturación ni publica nada automáticamente. Configurar presupuestos/alertas en Google Cloud según uso previsto. Las funciones usan runtime Node 22 y región southamerica-east1; la app apunta a esa misma región.
2. Desde `firebase/functions` instalar dependencias:

```powershell
npm.cmd install
```

3. Desde la raíz del proyecto, iniciar sesión y publicar índices y funciones:

```powershell
npx.cmd firebase-tools login
npx.cmd firebase-tools deploy --only firestore:indexes --config firebase/firebase.json --project soluciona-dev
npx.cmd firebase-tools deploy --only functions:soluciona-priority --config firebase/firebase.json --project soluciona-dev
```

Esperar que el índice nuevo de profesionales quede habilitado: verificationStatus ASC + availability ASC + zones CONTAINS. Preservar índices personalizados existentes. Confirmar que la cuenta de servicio de ejecución de las funciones tiene permisos de lectura/escritura de Firestore, lectura de usuarios Auth y envío FCM. Si el proyecto limita permisos predeterminados, asignar a ESA cuenta los roles Cloud Datastore User, Firebase Authentication Viewer y Firebase Cloud Messaging API Admin; no asignarlos a usuarios de la app ni colocar su clave en el APK. Confirmar las APIs y permisos que solicita Firebase CLI durante el despliegue.

4. Publicar el Worker documental desde `worker-r2`, conservando su configuración y bucket:

```powershell
npm.cmd install
npx.cmd wrangler deploy
```

5. Publicar el Worker de pagos desde `worker-marketplace`, conservando D1 y secretos actuales:

```powershell
npm.cmd install
npm.cmd run typecheck
npx.cmd wrangler deploy
```

No hay migración nueva de D1 ni clave RENAPER. No se necesita la API ni el Worker VB.NET. El Worker documental usa el token del usuario para validar acceso a Firestore y guardar sus metadatos.

6. Compilar la nueva app en Android Studio: sincronizar Gradle y Build → Generate Signed App Bundle/APK, con la misma firma y applicationId. Conservar DOCUMENTS_API_URL, MARKETPLACE_API_URL y MP_PUBLIC_KEY existentes. La versión pasa a 0.9.2/code 23. Si ya publicaste code 23 o superior, elegir uno mayor.
7. Coordinar la distribución del APK con las reglas nuevas. Las reglas exigen budgetRequired=true en nuevos pedidos y no dejan al cliente aprobar importes. Versiones antiguas del APK no podrán crear nuevos pedidos al activar esas reglas. En pruebas, publicar reglas antes de probar el nuevo APK. En producción distribuir la actualización y comunicar el cambio antes del corte.

```powershell
npx.cmd firebase-tools deploy --only firestore:rules --config firebase/firebase.json --project soluciona-dev
```

El cambio no estará operativo hasta completar servidores, reglas y nuevo APK. En un primer despliegue sin compatibilidad total probar en un proyecto de pruebas antes de producción.

## Revisión del DNI desde Firebase y Cloudflare

1. Firestore → Datos → identity_submissions. Buscar el documento cuyo ID es el UID de la cuenta.
2. Confirmar que existen front y back, status=PENDING y consentimiento. Comparar uid con la cuenta en Authentication y consultar el perfil users/<uid>. No confundir la aprobación del DNI con la habilitación profesional.
3. Copiar front.storageKey y back.storageKey. En Cloudflare → R2 → bucket de documentos, buscar cada objeto y descargarlo desde tu sesión de consola autorizada.
4. Revisar ambas imágenes. La revisión de fotografías es documental; no acredita que quien subió el archivo sea su titular. Antes de aprobar confirmar que updatedAt y ambas rutas siguen iguales a las revisadas; si cambió una imagen, volver a revisar.
5. Editar status (string) a APPROVED o REJECTED y reviewNote (string) con una nota útil. Agregar reviewedAt (timestamp) y reviewedBy (string, UID o identificador del operador) para tu registro. No cambiar front/back al revisar. Si falta una cara, mantener PENDING y pedir que se complete la carga.
6. La app muestra el resultado al abrir Perfil → Mi DNI y recibe un aviso si el dispositivo tiene notificaciones activas. Reemplazar una cara vuelve el registro a PENDING.

Para atender una solicitud de eliminación, borrar ambos objetos del bucket y el documento identity_submissions desde las consolas, y eliminar las copias descargadas. No hay un borrado automático programado del DNI: definir una política de conservación para tu operación. Restringir el acceso de los miembros de Firebase/Cloudflare a los que realmente revisan documentación. No abrir las reglas al público para facilitar la revisión.

## Comprobación en dispositivos

Con cliente y profesional de prueba:

- Enviar ambas caras del DNI, rechazar con nota, reenviar y aprobar. Comprobar que una cuenta distinta no ve ni descarga ese DNI.
- Crear pedido, agregar fotos antes de aceptación, abrirlas con un profesional compatible, y comprobar límite y denegación de cargas luego de aceptar.
- Aceptar pedido, enviar presupuesto desglosado, rechazar y corregir. Sin aprobación no iniciar. Aceptar y luego iniciar.
- Proponer adicional: no generar cobro mientras esté pendiente. Rechazarlo mantiene el total; aceptarlo lo incrementa una vez. Verificar dos respuestas simultáneas y revisiones antiguas.
- Cobrar en sandbox de Mercado Pago el importe aprobado: comprobar que importes distintos, tarjetas y QR no evaden ese control. No hacer pruebas iniciales con cobros reales.
- Permitir/denegar notificaciones, probar app cerrada y abierta, tocar aviso, cerrar sesión/cambiar cuenta y confirmar que no se abre un pedido de otra cuenta.
- En Google Cloud revisar ejecuciones fallidas de functions y métricas de envíos FCM; no registrar documentos, tokens o conversaciones. La colección notification_receipts evita repeticiones después de entrega; opcionalmente habilitar TTL en su campo expireAt para limpieza a los 14 días.

## Validación realizada

38 pruebas automatizadas pasan: presupuesto/decimales, roles, revisión exacta, adicionales, guardas de estados, archivos/consentimiento/firma, limpieza ante fallo, límites y descargas, destinatarios de notificaciones, escritura transaccional del presupuesto, escuchas de pantallas, escape de texto y pruebas existentes de UI/chat. Se usan servicios simulados.

Se comprobó sintaxis de JavaScript y TypeScript con Node, JSON y XML. No hay SDK Android/JDK/Gradle ni emulador en este entorno: no se generó un APK ni se verificó compilación Java/Android. Tampoco se ejecutaron aquí Firebase Emulator para reglas, el typecheck completo de Workers, despliegues reales, FCM en dispositivos ni pagos reales. Esas comprobaciones deben completarse antes de publicar.

Referencias: https://firebase.google.com/docs/functions/callable ; https://firebase.google.com/docs/functions/firestore-events ; https://firebase.google.com/docs/cloud-messaging/android/get-started
