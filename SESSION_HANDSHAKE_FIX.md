# Soluciona 0.8.2 — fix de sesión después de biometría

Síntoma:
- Biometría correcta.
- Aparecía NO_SESSION.
- La pantalla quedaba en “Conectando con Soluciona…”.
- El gesto Atrás hacía aparecer Inicio.

Causa:
Dos inicializaciones corrían en paralelo al volver de biometría:
1. la sesión principal de Firebase;
2. bootstrap/referidos de FeaturesBridge.

Los módulos auxiliares podían consultar Firebase antes de que la sesión principal
terminara de restaurarse. Además, si el perfil ya había llegado pero la pantalla
seguía en `loading`, no existía un watchdog que forzara la navegación.

Corrección:
- FeaturesBridge no consulta módulos autenticados hasta recibir session/signIn OK.
- NO_SESSION transitorio de módulos auxiliares no se muestra.
- MainActivity repite refreshSession 350 ms después de cargar index.html.
- index.html reintenta la sesión y recupera automáticamente la pantalla si quedó
  trabada en `loading`.
- La biometría sigue siendo opcional y no se guarda ninguna contraseña.

Versión:
- versionCode 14
- versionName 0.8.2
