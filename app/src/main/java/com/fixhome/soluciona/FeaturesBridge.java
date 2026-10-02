package com.fixhome.soluciona;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
            p.put("cardPaymentsConfigured", !BuildConfig.MP_PUBLIC_KEY.trim().isEmpty());
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
                        p.put("cardPaymentsConfigured", !BuildConfig.MP_PUBLIC_KEY.trim().isEmpty());
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
    public void requestCurrentLocation() {
        activity.requestPaymentLocation();
    }

    void onLocationPermissionDenied() {
        emitMessage(
                "currentLocation",
                false,
                "Para generar el QR necesitamos la ubicación actual del servicio. Podés habilitarla y volver a intentar."
        );
    }

    @SuppressLint("MissingPermission")
    void captureCurrentLocation() {
        boolean fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            onLocationPermissionDenied();
            return;
        }

        activity.runOnUiThread(() -> {
            LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                emitMessage("currentLocation", false, "No pudimos acceder al servicio de ubicación.");
                return;
            }

            String provider = null;
            try {
                if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                } else if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                }
            } catch (Exception ignored) {
            }

            if (provider == null) {
                emitMessage("currentLocation", false, "Activá la ubicación del teléfono para generar el QR.");
                return;
            }

            final Location fallback = bestLastKnownLocation(manager);
            final Handler handler = new Handler(Looper.getMainLooper());
            final AtomicBoolean delivered = new AtomicBoolean(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                final CancellationSignal signal = new CancellationSignal();
                final Runnable timeout = () -> {
                    if (!delivered.compareAndSet(false, true)) return;
                    signal.cancel();
                    if (isRecentLocation(fallback)) emitCurrentLocation(fallback);
                    else emitMessage("currentLocation", false,
                            "No pudimos obtener tu ubicación actual. Revisá GPS/ubicación e intentá nuevamente.");
                };
                handler.postDelayed(timeout, 12_000L);
                try {
                    manager.getCurrentLocation(
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(activity),
                            location -> {
                                if (!delivered.compareAndSet(false, true)) return;
                                handler.removeCallbacks(timeout);
                                if (location != null) emitCurrentLocation(location);
                                else if (isRecentLocation(fallback)) emitCurrentLocation(fallback);
                                else emitMessage("currentLocation", false,
                                        "No pudimos obtener tu ubicación actual. Intentá nuevamente.");
                            }
                    );
                } catch (Exception e) {
                    handler.removeCallbacks(timeout);
                    if (delivered.compareAndSet(false, true)) {
                        if (isRecentLocation(fallback)) emitCurrentLocation(fallback);
                        else emitMessage("currentLocation", false, "No pudimos obtener tu ubicación actual.");
                    }
                }
                return;
            }

            final LocationListener[] holder = new LocationListener[1];
            final Runnable timeout = () -> {
                if (!delivered.compareAndSet(false, true)) return;
                try {
                    if (holder[0] != null) manager.removeUpdates(holder[0]);
                } catch (Exception ignored) {
                }
                if (isRecentLocation(fallback)) emitCurrentLocation(fallback);
                else emitMessage("currentLocation", false,
                        "No pudimos obtener tu ubicación actual. Revisá GPS/ubicación e intentá nuevamente.");
            };
            holder[0] = location -> {
                if (!delivered.compareAndSet(false, true)) return;
                handler.removeCallbacks(timeout);
                try {
                    manager.removeUpdates(holder[0]);
                } catch (Exception ignored) {
                }
                if (location != null) emitCurrentLocation(location);
                else if (isRecentLocation(fallback)) emitCurrentLocation(fallback);
                else emitMessage("currentLocation", false, "No pudimos obtener tu ubicación actual.");
            };

            handler.postDelayed(timeout, 12_000L);
            try {
                manager.requestSingleUpdate(provider, holder[0], Looper.getMainLooper());
            } catch (Exception e) {
                handler.removeCallbacks(timeout);
                if (delivered.compareAndSet(false, true)) {
                    if (isRecentLocation(fallback)) emitCurrentLocation(fallback);
                    else emitMessage("currentLocation", false, "No pudimos obtener tu ubicación actual.");
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private Location bestLastKnownLocation(LocationManager manager) {
        Location best = null;
        String[] providers = new String[]{
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
        };
        for (String provider : providers) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate == null) continue;
                if (best == null
                        || candidate.getTime() > best.getTime()
                        || (candidate.getTime() == best.getTime()
                        && candidate.getAccuracy() < best.getAccuracy())) {
                    best = candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    private boolean isRecentLocation(Location location) {
        return location != null
                && location.getLatitude() >= -90 && location.getLatitude() <= 90
                && location.getLongitude() >= -180 && location.getLongitude() <= 180
                && Math.abs(System.currentTimeMillis() - location.getTime()) <= 3 * 60_000L;
    }

    private void emitCurrentLocation(Location location) {
        JSONObject p = new JSONObject();
        try {
            p.put("latitude", location.getLatitude());
            p.put("longitude", location.getLongitude());
            p.put("accuracyMeters", Math.max(0f, location.getAccuracy()));
            p.put("capturedAt", location.getTime());
        } catch (Exception ignored) {
        }
        emit("currentLocation", true, p);
    }

    @JavascriptInterface
    public void createPaymentRequest(String requestId, String amountText, String note) {
        try {
            double amount = Double.parseDouble(amountText.replace(",", "."));
            if (amount <= 0) {
                emitMessage("paymentRequestCreated", false, "Ingresá un importe válido.");
                return;
            }
            JSONObject body = new JSONObject();
            body.put("requestId", requestId == null ? "" : requestId.trim());
            body.put("amount", String.format(Locale.US, "%.2f", amount));
            body.put("note", note == null ? "" : note.trim());
            api("POST", "/v1/payment-requests", body, "paymentRequestCreated", null);
        } catch (Exception e) {
            emitMessage("paymentRequestCreated", false, "Ingresá un importe válido.");
        }
    }

    @JavascriptInterface
    public void getPaymentRequestForService(String requestId) {
        api(
                "GET",
                "/v1/payment-requests/by-service/" + Uri.encode(requestId),
                null,
                "paymentRequest",
                null
        );
    }

    @JavascriptInterface
    public void startCardPayment(String paymentRequestId) {
        if (BuildConfig.MP_PUBLIC_KEY.trim().isEmpty()) {
            emitMessage("cardSession", false, "Falta configurar MP_PUBLIC_KEY en la aplicación.");
            return;
        }
        if (!SolucionaApplication.isMercadoPagoReady()) {
            String detail = SolucionaApplication.mercadoPagoInitError();
            if (detail == null || detail.trim().isEmpty()) {
                detail = "El SDK de Mercado Pago no quedó inicializado.";
            }
            emitMessage("cardSession", false, detail);
            return;
        }
        api(
                "POST",
                "/v1/payment-requests/" + Uri.encode(paymentRequestId) + "/card-session",
                new JSONObject(),
                "cardSession",
                payload -> {
                    String serviceRequestId = payload.optString("requestId", "");
                    long amountCents = payload.optLong("amountCents", 0L);
                    String amountFormatted = payload.optString("amountFormatted", "");
                    if (amountCents <= 0L) {
                        emitMessage("cardSession", false, "El cobro no tiene un importe válido.");
                        return;
                    }
                    activity.launchCardCheckout(
                            paymentRequestId,
                            serviceRequestId,
                            amountCents,
                            amountFormatted
                    );
                }
        );
    }

    @JavascriptInterface
    public void createPaymentRequestQr(String paymentRequestId,
                                       String latitudeText, String longitudeText) {
        try {
            double latitude = Double.parseDouble(latitudeText);
            double longitude = Double.parseDouble(longitudeText);
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                emitMessage("paymentRequestQr", false, "No pudimos validar la ubicación actual.");
                return;
            }
            JSONObject body = new JSONObject();
            body.put("latitude", latitude);
            body.put("longitude", longitude);
            api(
                    "POST",
                    "/v1/payment-requests/" + Uri.encode(paymentRequestId) + "/qr",
                    body,
                    "paymentRequestQr",
                    null
            );
        } catch (Exception e) {
            emitMessage("paymentRequestQr", false, "No pudimos validar la ubicación actual.");
        }
    }

    void onCardCheckoutLaunchFailed() {
        emitMessage("cardCheckoutResult", false, "No se pudo abrir el pago con tarjeta.");
    }

    void onCardCheckoutResult(int resultCode, Intent data) {
        JSONObject p = new JSONObject();
        try {
            String status = data == null ? "CANCELLED"
                    : data.getStringExtra(CardCheckoutActivity.RESULT_STATUS);
            String orderStatus = data == null ? ""
                    : data.getStringExtra(CardCheckoutActivity.RESULT_ORDER_STATUS);
            String orderId = data == null ? ""
                    : data.getStringExtra(CardCheckoutActivity.RESULT_ORDER_ID);
            String message = data == null ? "Pago cancelado."
                    : data.getStringExtra(CardCheckoutActivity.RESULT_MESSAGE);
            String errorCode = data == null ? ""
                    : data.getStringExtra(CardCheckoutActivity.RESULT_ERROR_CODE);
            String paymentRequestId = data == null ? ""
                    : data.getStringExtra(CardCheckoutActivity.EXTRA_PAYMENT_REQUEST_ID);
            String serviceRequestId = data == null ? ""
                    : data.getStringExtra(CardCheckoutActivity.EXTRA_SERVICE_REQUEST_ID);

            p.put("status", status == null ? "" : status);
            p.put("orderStatus", orderStatus == null ? "" : orderStatus);
            p.put("orderId", orderId == null ? "" : orderId);
            p.put("message", message == null ? "" : message);
            p.put("errorCode", errorCode == null ? "" : errorCode);
            p.put("paymentRequestId", paymentRequestId == null ? "" : paymentRequestId);
            p.put("requestId", serviceRequestId == null ? "" : serviceRequestId);
        } catch (Exception ignored) {
        }
        emit("cardCheckoutResult", true, p);
    }

    // Legacy 0.8.x helpers kept to avoid crashing an older WebView asset during
    // rolling upgrades. New UI uses payment requests above.
    @JavascriptInterface
    public void createPaymentQr(String requestId, String amountText) {
        emitMessage(
                "paymentQr",
                false,
                "Actualizá Soluciona: el cobro ahora se envía primero al cliente."
        );
    }

    @JavascriptInterface
    public void createPaymentQrAtLocation(String requestId, String amountText,
                                          String latitudeText, String longitudeText) {
        emitMessage(
                "paymentQr",
                false,
                "Actualizá Soluciona: el cobro ahora se envía primero al cliente."
        );
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
