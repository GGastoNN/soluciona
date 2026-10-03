# Actualización 02 — rendimiento, sincronización y formularios

## Implementado

- Conserva la migración HTTPS local con WebViewAssetLoader de la primera entrega.
- RequestHistoryRepository separa consultas y carga privada del bridge. Solicitudes y trabajos: 20 resultados por bloque, createdAt descendente, cursor DocumentSnapshot, consulta de 21 para detectar la página siguiente. El orden se conserva aunque las lecturas privadas terminen desordenadas.
- Pedidos nuevos: bloques de 20 con estado REQUESTED desde Firestore. Compatibilidad de rubro, zona y profesional preferido se filtra dentro de cada bloque; pueden existir bloques sin coincidencias con botón Cargar más. El cursor avanza sobre los documentos consultados, incluso cuando no hay coincidencias. Se incluye índice status/createdAt.
- Datos privados: únicamente se leen direcciones para servicios no cerrados de la página consultada. En históricos COMPLETED/CANCELLED se muestra la zona pública. Fallos privados no descartan el pedido y producen aviso recuperable.
- ChatRepository separa escucha y paginación del bridge. Últimos 50 mensajes en tiempo real y bloques anteriores de 50 con cursor. Se conservan mensajes ya cargados cuando se mueve la ventana de escucha, y se deduplican por ID. Las reglas existentes prohíben editar/borrar mensajes desde clientes; eliminaciones administrativas de mensajes antiguos no se rastrean en esta entrega.
- La escucha se libera al navegar, cerrar sesión, pausar o destruir Activity. Al reanudar, se restablece si el chat sigue abierto.
- Formularios: reconciliación extraída a ui-runtime.js; conserva nodos y borradores. Se corrige la desaparición de categorías profesionales durante un refresco, se conserva aria-pressed y se actualizan opciones de zona.
- Envíos: evita doble envío y solo borra el texto enviado si el usuario no escribió un nuevo borrador. Se aíslan respuestas por cuenta y chat.
- Estados de carga, caché, errores y reintentos; anclaje de desplazamiento al cargar mensajes anteriores.
- Se codifican como datos los IDs interpolados en acciones de la pantalla principal. No constituye una auditoría completa de features.js ni de SVG recibidos del backend.
- Campos, avisos y botones secundarios respetan el tema explícito de la app. El checkout nativo aún requiere una revisión independiente.
- Diagnóstico local solo en debug: tiempo hasta WebView lista y categorías de fallo sync/payment. No incluye IDs, importes, contenido, tokens, URLs o textos de excepción. No se agrega un servicio remoto de analítica.
- Scripts raíz separados para Workers. Se corrige check:r2: usa Wrangler --dry-run, no un typecheck inexistente. npm test ejecuta regresiones sin instalar dependencias.

## Validación ejecutada

12 pruebas Node de estado y reconciliación con adaptador DOM mínimo; todas aprobadas. Sintaxis de todos los scripts JavaScript. Integridad del ZIP y simulación de aplicación del paquete incremental sobre ambas bases, repetición idempotente y detección de conflictos.

No se compiló Android: no hay JDK/Gradle/SDK disponibles en este entorno. No se pudo ejecutar navegador Chromium. No se ejecutaron Firebase Emulator ni los Workers, ni se desplegó nada. Las pruebas de nodos DOM no certifican comportamiento real de teclado o foco Android.

## Validación antes de publicar

1. Aplicar los archivos y desplegar índices de Firestore en el proyecto correcto; esperar que terminen de construirse.
2. Compilar en Android Studio y ejecutar en dispositivo. Los documentos usados por consultas ordenadas deben tener createdAt.
3. Con más de 20 pedidos y 100 mensajes: cargar todas las páginas; verificar orden, ausencia de duplicados y el último bloque; probar un bloque de pedidos nuevos sin compatibilidad.
4. Escribir con teclado visible mientras llegan mensajes o catálogos. Confirmar selección de categorías, zonas, cursor y desplazamiento.
5. Cambiar de chat rápidamente, cerrar sesión, alternar segundo plano/primer plano. Comprobar que no se muestran datos de otra cuenta.
6. Simular conexión interrumpida y fallos de lecturas privadas; confirmar avisos y reintentos sin perder borradores.
7. Probar tema claro/oscuro explícito, tamaño de texto ampliado, enlaces externos, splash, documentos y pagos.

## Pendientes

Separación de autenticación, documentos y pagos; paginación de profesionales; Wrapper y locks con dependencias resueltas y compilación validada; pruebas Android/Firebase; checkout oscuro, iconos uniformes, rediseño completo y push (requiere FCM y backend emisor). Se mantiene la versión Android 0.9.0; aumentar versionCode antes de distribuir una nueva versión en Play.
