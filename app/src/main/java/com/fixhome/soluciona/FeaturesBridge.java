package com.fixhome.soluciona;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FeaturesBridge {
    private static final String PREFS = "soluciona_features";
    private static final String THEME_KEY = "theme";
    private static final int BIOMETRIC_FLAGS = BiometricManager.Authenticators.BIOMETRIC_WEAK;
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(3);

    private final MainActivity activity;
    private final WebView webView;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final SharedPreferences prefs;

    FeaturesBridge(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String readThemePreference(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(THEME_KEY, "system");
    }

    @JavascriptInterface
    public boolean isAvailable() {
        return true;
    }

    @JavascriptInterface
    public void getSettings() {
        JSONObject p = new JSONObject();
        try {
            p.put("theme", readThemePreference(activity));
            p.put("biometricAvailable", canAuthenticateBiometric());
            p.put("biometricEnabled", isBiometricEnabledForCurrentUser());
            p.put("marketplaceConfigured", !BuildConfig.MARKETPLACE_API_URL.trim().isEmpty());
            FirebaseUser user = auth.getCurrentUser();
            p.put("signedIn", user != null);
        } catch (Exception ignored) {
        }
        emit("settings", true, p);
    }

    @JavascriptInterface
    public void setTheme(String value) {
        String theme = value == null ? "system" : value.trim().toLowerCase(Locale.ROOT);
        if (!theme.equals("light") && !theme.equals("dark") && !theme.equals("system")) {
            theme = "system";
        }
        prefs.edit().putString(THEME_KEY, theme).apply();
        activity.applyThemePreference(theme);
        JSONObject p = new JSONObject();
        try {
            p.put("theme", theme);
        } catch (Exception ignored) {
        }
        emit("theme", true, p);
    }

    boolean canAuthenticateBiometric() {
        try {
            return BiometricManager.from(activity).canAuthenticate(BIOMETRIC_FLAGS)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String biometricKeyForCurrentUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return null;
        return "biometric_" + user.getUid();
    }

    boolean isBiometricEnabledForCurrentUser() {
        String key = biometricKeyForCurrentUser();
        return key != null && prefs.getBoolean(key, false);
    }

    @JavascriptInterface
    public boolean isBiometricEnabled() {
        return isBiometricEnabledForCurrentUser();
    }

    @JavascriptInterface
    public void setBiometricEnabled(boolean enabled) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("biometric", false, "Iniciá sesión antes de configurar la biometría.");
            return;
        }
        String key = biometricKeyForCurrentUser();
        if (!enabled) {
            prefs.edit().putBoolean(key, false).apply();
            JSONObject p = new JSONObject();
            try { p.put("enabled", false); } catch (Exception ignored) {}
            emit("biometric", true, p);
            return;
        }
        if (!canAuthenticateBiometric()) {
            emitMessage("biometric", false, "Este dispositivo no tiene una biometría compatible configurada.");
            return;
        }
        showBiometricPrompt(
                "Activar ingreso biométrico",
                "Confirmá tu huella o biometría para proteger Soluciona.",
                () -> {
                    prefs.edit().putBoolean(key, true).apply();
                    JSONObject p = new JSONObject();
                    try { p.put("enabled", true); } catch (Exception ignored) {}
                    emit("biometric", true, p);
                },
                () -> emitMessage("biometric", false, "No se activó el ingreso biométrico.")
        );
    }

    void authenticateForStartup(Runnable onSuccess, Runnable onUsePassword) {
        activity.runOnUiThread(() -> showBiometricPrompt(
                "Ingresar a Soluciona",
                "Usá tu huella o biometría.",
                onSuccess,
                onUsePassword
        ));
    }

    private void showBiometricPrompt(String title, String subtitle, Runnable onSuccess, Runnable onFallback) {
        activity.runOnUiThread(() -> {
            BiometricPrompt prompt = new BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            onSuccess.run();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            onFallback.run();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                        }
                    }
            );
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(BIOMETRIC_FLAGS)
                    .setNegativeButtonText("Usar correo y contraseña")
                    .build();
            prompt.authenticate(info);
        });
    }

    @JavascriptInterface
    public void loadZones() {
        db.collection("zones").whereEqualTo("enabled", true).get()
                .addOnSuccessListener(activity, snapshot -> {
                    try {
                        List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                        docs.sort(Comparator.comparingLong(d -> {
                            Long n = d.getLong("sortOrder");
                            return n == null ? Long.MAX_VALUE : n;
                        }));
                        JSONArray zones = new JSONArray();
                        for (DocumentSnapshot d : docs) {
                            JSONObject z = new JSONObject();
                            z.put("id", d.getId());
                            z.put("name", value(d.getString("name")));
                            z.put("province", value(d.getString("province")));
                            zones.put(z);
                        }
                        JSONObject p = new JSONObject();
                        p.put("zones", zones);
                        emit("zones", true, p);
                    } catch (Exception e) {
                        emitMessage("zones", false, safeMessage(e));
                    }
                })
                .addOnFailureListener(activity, e -> emitMessage("zones", false, safeMessage(e)));
    }

    @JavascriptInterface
    public void getFeatureBootstrap() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("bootstrap", false, "NO_SESSION");
            return;
        }
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(activity, userDoc -> {
                    String role = value(userDoc.getString("role"));
                    JSONObject p = new JSONObject();
                    try {
                        p.put("uid", user.getUid());
                        p.put("role", role);
                        p.put("theme", readThemePreference(activity));
                        p.put("biometricAvailable", canAuthenticateBiometric());
                        p.put("biometricEnabled", isBiometricEnabledForCurrentUser());
                        p.put("marketplaceConfigured", !BuildConfig.MARKETPLACE_API_URL.trim().isEmpty());
                    } catch (Exception ignored) {
                    }
                    if (!"PROFESSIONAL".equals(role)) {
                        emit("bootstrap", true, p);
                        return;
                    }
                    db.collection("professionals").document(user.getUid()).get()
                            .addOnCompleteListener(activity, task -> {
                                try {
                                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                        DocumentSnapshot pro = task.getResult();
                                        p.put("primaryZone", value(pro.getString("primaryZone")));
                                        p.put("zones", new JSONArray(stringList(pro.get("zones"))));
                                    }
                                } catch (Exception ignored) {
                                }
                                emit("bootstrap", true, p);
                            });
                })
                .addOnFailureListener(activity, e -> emitMessage("bootstrap", false, safeMessage(e)));
    }

    @JavascriptInterface
    public void saveWorkZones(String json) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("workZones", false, "NO_SESSION");
            return;
        }
        try {
            JSONObject data = new JSONObject(json);
            String primary = data.optString("primaryZone", "").trim();
            JSONArray arr = data.optJSONArray("zones");
            List<String> zones = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, "").trim();
                    if (!id.isEmpty() && !zones.contains(id)) zones.add(id);
                }
            }
            if (primary.isEmpty()) {
                emitMessage("workZones", false, "Elegí una zona principal.");
                return;
            }
            if (!zones.contains(primary)) zones.add(0, primary);

            Map<String, Object> update = new HashMap<>();
            update.put("primaryZone", primary);
            update.put("zones", zones);
            update.put("updatedAt", FieldValue.serverTimestamp());

            db.collection("professionals").document(user.getUid()).update(update)
                    .addOnSuccessListener(activity, unused -> {
                        JSONObject p = new JSONObject();
                        try {
                            p.put("primaryZone", primary);
                            p.put("zones", new JSONArray(zones));
                        } catch (Exception ignored) {}
                        emit("workZones", true, p);
                    })
                    .addOnFailureListener(activity, e -> emitMessage("workZones", false, safeMessage(e)));
        } catch (Exception e) {
            emitMessage("workZones", false, safeMessage(e));
        }
    }

    @JavascriptInterface
    public void getReferralDashboard() {
        api("GET", "/v1/referrals/dashboard", null, "referrals", null);
    }

    @JavascriptInterface
    public void applyReferralCode(String code) {
        JSONObject body = new JSONObject();
        try { body.put("code", code == null ? "" : code.trim()); } catch (Exception ignored) {}
        api("POST", "/v1/referrals/apply", body, "referralApply", null);
    }

    @JavascriptInterface
    public void getMarketplaceStatus() {
        api("GET", "/v1/mp/status", null, "marketplaceStatus", null);
    }

    @JavascriptInterface
    public void connectMercadoPago() {
        api("POST", "/v1/mp/connect", new JSONObject(), "marketplaceConnect", payload -> {
            String authUrl = payload.optString("authUrl", "");
            if (!authUrl.isEmpty()) {
                activity.runOnUiThread(() -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
                    } catch (Exception e) {
                        emitMessage("marketplaceConnect", false, "No se pudo abrir Mercado Pago.");
                    }
                });
            }
        });
    }

    @JavascriptInterface
    public void createPaymentQr(String requestId, String amountText) {
        try {
            double amount = Double.parseDouble(amountText.replace(",", "."));
            if (amount <= 0) {
                emitMessage("paymentQr", false, "Ingresá un importe válido.");
                return;
            }
            JSONObject body = new JSONObject();
            body.put("requestId", requestId);
            body.put("amount", String.format(Locale.US, "%.2f", amount));
            api("POST", "/v1/payments/qr", body, "paymentQr", null);
        } catch (Exception e) {
            emitMessage("paymentQr", false, "Ingresá un importe válido.");
        }
    }

    @JavascriptInterface
    public void getPaymentForRequest(String requestId) {
        api("GET", "/v1/payments/by-request/" + Uri.encode(requestId), null, "paymentStatus", null);
    }

    private interface ApiSuccess {
        void run(JSONObject payload);
    }

    private void api(String method, String path, JSONObject body, String event, ApiSuccess success) {
        if (BuildConfig.MARKETPLACE_API_URL.trim().isEmpty()) {
            emitMessage(event, false, "El backend de marketplace todavía no está configurado.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage(event, false, "NO_SESSION");
            return;
        }
        user.getIdToken(true)
                .addOnSuccessListener(activity, result -> {
                    String token = result.getToken();
                    if (token == null || token.trim().isEmpty()) {
                        emitMessage(event, false, "No se pudo obtener una sesión válida.");
                        return;
                    }
                    NETWORK.execute(() -> executeApi(method, path, body, event, token, success));
                })
                .addOnFailureListener(activity, e -> emitMessage(event, false, safeMessage(e)));
    }

    private void executeApi(String method, String path, JSONObject body, String event,
                            String token, ApiSuccess success) {
        HttpURLConnection connection = null;
        try {
            String base = BuildConfig.MARKETPLACE_API_URL.replaceAll("/+$", "");
            connection = (HttpURLConnection) new URL(base + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(35000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null && !"GET".equals(method)) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readAll(stream);
            JSONObject payload = responseText.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(responseText);
            if (code < 200 || code >= 300) {
                String message = payload.optString("message",
                        payload.optString("error", "Error del servidor (" + code + ")."));
                emitMessage(event, false, message);
                return;
            }
            if (success != null) success.run(payload);
            emit(event, true, payload);
        } catch (Exception e) {
            emitMessage(event, false, safeMessage(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String value(String s) {
        return s == null ? "" : s;
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) if (item != null) out.add(String.valueOf(item));
        }
        return out;
    }

    private static String safeMessage(Throwable t) {
        if (t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()) {
            return "Ocurrió un error inesperado.";
        }
        return t.getMessage();
    }

    private void emitMessage(String event, boolean ok, String message) {
        JSONObject p = new JSONObject();
        try { p.put("message", message); } catch (Exception ignored) {}
        emit(event, ok, p);
    }

    private void emit(String event, boolean ok, JSONObject payload) {
        activity.runOnUiThread(() -> {
            String js = "window.solucionaFeaturesEvent && window.solucionaFeaturesEvent("
                    + JSONObject.quote(event) + "," + ok + "," + payload.toString() + ");";
            webView.evaluateJavascript(js, null);
        });
    }
}
