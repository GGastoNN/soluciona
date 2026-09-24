# Soluciona. · Beta 0.4.0

Versión de prueba basada en el branding aprobado y el último prototipo funcional revisado con socios.

## Nuevas funciones

- Branding Soluciona con splash, icono, navegación inferior y UI móvil.
- Descripción del problema con sugerencia automática de rubro.
- Detección local de riesgos de gas, electricidad e inundación.
- Botón destacado de emergencia y priorización por cercanía.
- Disponibilidad por rubro: las categorías sin profesionales aparecen deshabilitadas.
- Listado de profesionales por categoría, cercanía, puntuación y precio.
- Flujo de solicitud, seguimiento, chat y reportes.
- Calificación de 1 a 5 estrellas únicamente al finalizar un trabajo.
- Comentarios/reseñas vinculados a cada servicio finalizado.
- La puntuación local del profesional refleja las reseñas creadas en la beta.
- Alta profesional local con rubros, zona, matrícula, antecedentes y seguro RC.
- Un profesional dado de alta localmente puede habilitar un rubro sin oferta.
- Estados documentales `EN REVISIÓN` hasta que exista validación real.
- Política de privacidad y términos en modo borrador/beta.
- Pagos, geolocalización y verificaciones siguen siendo simulados: no hay Firebase ni dinero real.

## Compilación

El workflow `.github/workflows/android.yml` genera:

`Soluciona-0.4.0-socio-debug.apk`

Usa JDK 17, Gradle 8.7 y Android SDK 34.

## Importante

No agregar archivos `.md`, `.txt` u otros formatos dentro de `app/src/main/res/drawable*`. Android solo admite recursos compatibles en esas carpetas. La documentación debe permanecer fuera de `res/`.
