# Soluciona 0.8.1 — fix biometría al reingresar

Problema observado:
1. La app se recrea al volver desde segundo plano.
2. Se muestra correctamente BiometricPrompt.
3. Tras autenticar, Android restauraba el estado anterior del WebView.
4. `restoreState()` podía devolver un WebView visualmente válido pero con el
   bootstrap JavaScript/native detenido en “Conectando con Soluciona…”.

Corrección:
- Después de superar la biometría siempre se carga `index.html` desde cero.
- Ya no se persiste/restaura el estado de ejecución del WebView.
- La sesión Firebase NO se borra cuando la biometría es correcta.
- Firebase/Firestore reconstruyen la pantalla y perfil normalmente.
- Si el usuario toca “Usar correo y contraseña”, se mantiene el comportamiento
  seguro: se cierra la sesión Firebase y se abre el login.
- No cambia la configuración de huella, temas, referidos, zonas ni marketplace.

Versión:
- versionCode 13
- versionName 0.8.1
