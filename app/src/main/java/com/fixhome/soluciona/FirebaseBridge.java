package com.fixhome.soluciona;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class FirebaseBridge {
    static final int PROFESSIONAL_DOCUMENT_REQUEST = 2002;
    private final Activity activity;
    private final WebView webView;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final AdsManager adsManager;
    private String pendingDocumentType;
    private final RequestHistoryRepository history;
    private final ChatRepository chats;

    @JavascriptInterface
    public void stopMessages() { chats.stop(); }

    @JavascriptInterface
    public void loadOlderMessages(String requestId) { chats.older(requestId); }

    @JavascriptInterface
    public void loadMoreClientRequests() { history.load("clientRequests", "clientUid", true); }

    @JavascriptInterface
    public void loadMoreProfessionalJobs() { history.load("professionalJobs", "professionalUid", true); }

    FirebaseBridge(Activity activity, WebView webView, AdsManager adsManager) {
        this.activity = activity;
        this.webView = webView;
        this.adsManager = adsManager;
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.history = new RequestHistoryRepository(activity, auth, db, new RequestHistoryRepository.Output() {
            public JSONObject request(DocumentSnapshot doc) throws Exception { return requestJson(doc, false); }
            public void success(String event, JSONObject payload) { emit(event, true, payload); }
            public void failure(String event, Exception error) { emitError(event, error); }
        });
        this.chats = new ChatRepository(activity, auth, db, new ChatRepository.Output() {
            public void success(JSONObject payload) { emit("messages", true, payload); }
            public void failure(String requestId, Exception error) {
                JSONObject payload = new JSONObject();
                try { payload.put("requestId", requestId); payload.put("message", recoverableMessage(error)); }
                catch (JSONException ignored) {}
                emit("messages", false, payload);
            }
        });
    }

    @JavascriptInterface
    public boolean isAvailable() {
        return true;
    }

    @JavascriptInterface
    public void loadCatalog() {
        db.collection("zones").whereEqualTo("enabled", true).get()
                .addOnSuccessListener(activity, zones ->
                        db.collection("categories").whereEqualTo("enabled", true).get()
                                .addOnSuccessListener(activity, categories -> emitCatalog(zones, categories))
                                .addOnFailureListener(activity, e -> emitError("catalog", e)))
                .addOnFailureListener(activity, e -> emitError("catalog", e));
    }

    private void emitCatalog(QuerySnapshot zones, QuerySnapshot categories) {
        try {
            JSONArray zoneArray = new JSONArray();
            List<DocumentSnapshot> zoneDocs = new ArrayList<>(zones.getDocuments());
            sortByLongField(zoneDocs, "sortOrder");
            for (DocumentSnapshot d : zoneDocs) {
                JSONObject o = new JSONObject();
                o.put("id", d.getId());
                o.put("name", string(d.getString("name")));
                o.put("province", string(d.getString("province")));
                zoneArray.put(o);
            }

            JSONArray categoryArray = new JSONArray();
            List<DocumentSnapshot> categoryDocs = new ArrayList<>(categories.getDocuments());
            sortByLongField(categoryDocs, "sortOrder");
            for (DocumentSnapshot d : categoryDocs) {
                JSONObject o = new JSONObject();
                o.put("id", d.getId());
                o.put("name", string(d.getString("name")));
                o.put("requiresLicense", Boolean.TRUE.equals(d.getBoolean("requiresLicense")));
                o.put("requiresBackgroundCheck", Boolean.TRUE.equals(d.getBoolean("requiresBackgroundCheck")));
                o.put("requiresInsurance", Boolean.TRUE.equals(d.getBoolean("requiresInsurance")));
                categoryArray.put(o);
            }

            JSONObject payload = new JSONObject();
            payload.put("zones", zoneArray);
            payload.put("categories", categoryArray);
            payload.put("documentsApiConfigured", !BuildConfig.DOCUMENTS_API_URL.isBlank());
            emit("catalog", true, payload);
        } catch (Exception e) {
            emitError("catalog", e);
        }
    }

    private static void sortByLongField(List<DocumentSnapshot> docs, String field) {
        docs.sort(Comparator.comparingLong(d -> {
            Long value = d.getLong(field);
            return value == null ? Long.MAX_VALUE : value;
        }));
    }

    @JavascriptInterface
    public void registerClient(String json) {
        register(json, "CLIENT");
    }

    @JavascriptInterface
    public void registerProfessional(String json) {
        register(json, "PROFESSIONAL");
    }

    private void register(String json, String role) {
        try {
            JSONObject data = new JSONObject(json);
            String email = data.getString("email").trim();
            String password = data.getString("password");
            String name = data.getString("name").trim();
            String phone = data.getString("phone").trim();

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(activity, result -> {
                        FirebaseUser user = result.getUser();
                        if (user == null) {
                            emitMessage(registerEvent(role), false, "No se pudo crear la cuenta.");
                            return;
                        }
                        user.sendEmailVerification();
                        saveProfile(user.getUid(), role, name, email, phone, data, () -> {
                            JSONObject payload = new JSONObject();
                            try {
                                payload.put("uid", user.getUid());
                                payload.put("role", role);
                                payload.put("emailVerificationSent", true);
                            } catch (JSONException ignored) {}
                            emit(registerEvent(role), true, payload);
                        }, profileError -> rollbackIncompleteRegistration(user, registerEvent(role), profileError));
                    })
                    .addOnFailureListener(activity, e -> emitError(registerEvent(role), e));
        } catch (Exception e) {
            emitError(registerEvent(role), e);
        }
    }

    private static String registerEvent(String role) {
        return "CLIENT".equals(role) ? "registerClient" : "registerProfessional";
    }

    private void saveProfile(String uid, String role, String name, String email, String phone,
                             JSONObject source, Runnable onSuccess,
                             java.util.function.Consumer<Exception> onFailure) {
        try {
            Map<String, Object> user = new HashMap<>();
            user.put("uid", uid);
            user.put("role", role);
            user.put("displayName", name);
            user.put("accountStatus", "PROFESSIONAL".equals(role) ? "PENDING_REVIEW" : "ACTIVE");
            user.put("createdAt", FieldValue.serverTimestamp());
            user.put("updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> privateUser = new HashMap<>();
            privateUser.put("email", email);
            privateUser.put("phone", phone);
            privateUser.put("address", jsonObjectToMap(source.optJSONObject("address")));
            privateUser.put("updatedAt", FieldValue.serverTimestamp());

            WriteBatch batch = db.batch();
            batch.set(db.collection("users").document(uid), user);
            batch.set(db.collection("users_private").document(uid), privateUser);

            if ("PROFESSIONAL".equals(role)) {
                Map<String, Object> professional = new HashMap<>();
                professional.put("uid", uid);
                professional.put("displayName", name);
                professional.put("services", jsonArrayToList(source.optJSONArray("services")));
                List<String> professionalZones = jsonArrayToList(source.optJSONArray("zones"));
                professional.put("zones", professionalZones);
                professional.put("primaryZone", professionalZones.isEmpty() ? "" : professionalZones.get(0));
                professional.put("availability", false);
                professional.put("verificationStatus", "PENDING_DOCUMENTS");
                professional.put("rating", 0.0);
                professional.put("reviewCount", 0L);
                professional.put("completedJobs", 0L);
                professional.put("createdAt", FieldValue.serverTimestamp());
                professional.put("updatedAt", FieldValue.serverTimestamp());
                batch.set(db.collection("professionals").document(uid), professional);

                Map<String, Object> proPrivate = new HashMap<>();
                proPrivate.put("licenseNumber", source.optString("licenseNumber", ""));
                proPrivate.put("licenseJurisdiction", source.optString("licenseJurisdiction", ""));
                proPrivate.put("backgroundCheckStatus", "PENDING_UPLOAD");
                proPrivate.put("insuranceStatus", "PENDING_UPLOAD");
                proPrivate.put("updatedAt", FieldValue.serverTimestamp());
                batch.set(db.collection("professional_private").document(uid), proPrivate);
            }

            batch.commit()
                    .addOnSuccessListener(activity, unused -> onSuccess.run())
                    .addOnFailureListener(activity, onFailure::accept);
        } catch (Exception e) {
            onFailure.accept(e);
        }
    }

    private void rollbackIncompleteRegistration(FirebaseUser user, String event, Exception profileError) {
        // If Firestore profile creation fails after Firebase Auth succeeds, remove the
        // just-created Auth user so the account cannot remain orphaned.
        user.delete()
                .addOnCompleteListener(activity, task -> {
                    String detail = profileError == null || profileError.getMessage() == null
                            ? "No se pudo crear el perfil."
                            : profileError.getMessage();
                    emitMessage(event, false,
                            "No pudimos completar el registro. Intentá nuevamente. " + detail);
                });
    }

    @JavascriptInterface
    public void signIn(String email, String password) {
        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(activity, result -> emitProfile("signIn"))
                .addOnFailureListener(activity, e -> emitError("signIn", e));
    }

    @JavascriptInterface
    public void signOut() {
        chats.stop();
        history.clear();
        SolucionaMessagingService.logout(activity, () -> { auth.signOut(); emit("signOut", true, new JSONObject()); });
    }

    @JavascriptInterface
    public void refreshSession() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("session", false, "NO_SESSION");
            return;
        }
        user.reload()
                .addOnSuccessListener(activity, unused -> {
                    FirebaseUser refreshed = auth.getCurrentUser();
                    if (refreshed == null) {
                        emitMessage("session", false, "NO_SESSION");
                        return;
                    }
                    refreshed.getIdToken(true)
                            .addOnSuccessListener(activity, token -> emitProfile("session"))
                            .addOnFailureListener(activity, e -> emitError("session", e));
                })
                .addOnFailureListener(activity, e -> emitError("session", e));
    }

    @JavascriptInterface
    public void resendEmailVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("emailVerification", false, "No hay una sesión iniciada.");
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(activity, unused -> emitMessage("emailVerification", true,
                        "Correo de verificación reenviado."))
                .addOnFailureListener(activity, e -> emitError("emailVerification", e));
    }

    @JavascriptInterface
    public void sendPasswordReset(String email) {
        auth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(activity, unused -> emitMessage("passwordReset", true,
                        "Te enviamos un correo para restablecer tu contraseña."))
                .addOnFailureListener(activity, e -> emitError("passwordReset", e));
    }

    private void emitProfile(String event) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            emitMessage(event, false, "NO_SESSION");
            return;
        }
        String uid = firebaseUser.getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(activity, userDoc -> {
                    if (!userDoc.exists()) {
                        emitMessage(event, false, "PROFILE_NOT_FOUND");
                        return;
                    }
                    db.collection("users_private").document(uid).get()
                            .addOnSuccessListener(activity, privateDoc -> {
                                String role = userDoc.getString("role");
                                if ("PROFESSIONAL".equals(role)) {
                                    db.collection("professionals").document(uid).get()
                                            .addOnSuccessListener(activity, proDoc -> emitProfilePayload(
                                                    event, firebaseUser, userDoc, privateDoc, proDoc))
                                            .addOnFailureListener(activity, e -> emitError(event, e));
                                } else {
                                    emitProfilePayload(event, firebaseUser, userDoc, privateDoc, null);
                                }
                            })
                            .addOnFailureListener(activity, e -> emitError(event, e));
                })
                .addOnFailureListener(activity, e -> emitError(event, e));
    }

    private void emitProfilePayload(String event, FirebaseUser firebaseUser, DocumentSnapshot userDoc,
                                    DocumentSnapshot privateDoc, DocumentSnapshot proDoc) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("uid", firebaseUser.getUid());
            payload.put("email", string(firebaseUser.getEmail()));
            payload.put("emailVerified", firebaseUser.isEmailVerified());
            payload.put("role", string(userDoc.getString("role")));
            payload.put("displayName", string(userDoc.getString("displayName")));
            payload.put("accountStatus", string(userDoc.getString("accountStatus")));
            payload.put("phone", string(privateDoc.getString("phone")));
            Object address = privateDoc.get("address");
            payload.put("address", address instanceof Map ? new JSONObject((Map<?, ?>) address) : new JSONObject());
            payload.put("privacyOptionsRequired", adsManager != null && adsManager.isPrivacyOptionsRequired());
            payload.put("documentsApiConfigured", !BuildConfig.DOCUMENTS_API_URL.isBlank());

            if (proDoc != null && proDoc.exists()) {
                payload.put("verificationStatus", string(proDoc.getString("verificationStatus")));
                payload.put("availability", Boolean.TRUE.equals(proDoc.getBoolean("availability")));
                payload.put("services", new JSONArray(listStrings(proDoc.get("services"))));
                payload.put("zones", new JSONArray(listStrings(proDoc.get("zones"))));
                payload.put("rating", number(proDoc.get("rating")));
                payload.put("reviewCount", longNumber(proDoc.get("reviewCount")));
                payload.put("completedJobs", longNumber(proDoc.get("completedJobs")));
            }
            emit(event, true, payload);
        } catch (Exception e) {
            emitError(event, e);
        }
    }

    @JavascriptInterface
    public void setProfessionalAvailability(boolean available) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("availability", false, "NO_SESSION");
            return;
        }
        db.collection("professionals").document(user.getUid()).get()
                .addOnSuccessListener(activity, doc -> {
                    if (!"APPROVED".equals(doc.getString("verificationStatus"))) {
                        emitMessage("availability", false, "Tu perfil debe estar aprobado antes de recibir trabajos.");
                        return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    update.put("availability", available);
                    update.put("updatedAt", FieldValue.serverTimestamp());
                    db.collection("professionals").document(user.getUid()).update(update)
                            .addOnSuccessListener(activity, unused -> {
                                JSONObject p = new JSONObject();
                                try { p.put("availability", available); } catch (JSONException ignored) {}
                                emit("availability", true, p);
                            })
                            .addOnFailureListener(activity, e -> emitError("availability", e));
                })
                .addOnFailureListener(activity, e -> emitError("availability", e));
    }

    @JavascriptInterface
    public void listProfessionals(String categoryId, String zoneId) {
        requireVerifiedEmail("professionals", () -> db.collection("professionals")
                .whereEqualTo("verificationStatus", "APPROVED")
                .whereEqualTo("availability", true)
                .whereArrayContains("services", categoryId)
                .get()
                .addOnSuccessListener(activity, snapshot -> {
                    JSONArray arr = new JSONArray();
                    try {
                        for (QueryDocumentSnapshot d : snapshot) {
                            if (!"APPROVED".equals(d.getString("verificationStatus"))) continue;
                            if (!Boolean.TRUE.equals(d.getBoolean("availability"))) continue;
                            List<String> zones = listStrings(d.get("zones"));
                            if (!zones.contains(zoneId)) continue;
                            JSONObject o = new JSONObject();
                            o.put("uid", d.getId());
                            o.put("displayName", string(d.getString("displayName")));
                            o.put("rating", number(d.get("rating")));
                            o.put("reviewCount", longNumber(d.get("reviewCount")));
                            o.put("completedJobs", longNumber(d.get("completedJobs")));
                            arr.put(o);
                        }
                        JSONObject payload = new JSONObject();
                        payload.put("professionals", arr);
                        emit("professionals", true, payload);
                    } catch (Exception e) {
                        emitError("professionals", e);
                    }
                })
                .addOnFailureListener(activity, e -> emitError("professionals", e)));
    }

    @JavascriptInterface
    public void createServiceRequest(String json) {
        requireVerifiedEmail("createRequest", () -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;
            try {
                JSONObject data = new JSONObject(json);
                Map<String, Object> request = new HashMap<>();
                request.put("clientUid", user.getUid());
                request.put("professionalUid", null);
                String preferredProfessionalUid = data.optString("preferredProfessionalUid", "").trim();
                request.put("preferredProfessionalUid", preferredProfessionalUid.isEmpty() ? null : preferredProfessionalUid);
                request.put("categoryId", data.getString("categoryId"));
                request.put("zoneId", data.getString("zoneId"));
                request.put("description", data.getString("description").trim());
                request.put("urgent", data.optBoolean("urgent", false));
                request.put("status", "REQUESTED");
                request.put("budgetRequired", true);
                request.put("createdAt", FieldValue.serverTimestamp());
                request.put("updatedAt", FieldValue.serverTimestamp());

                DocumentReference requestRef = db.collection("service_requests").document();
                Map<String, Object> privateRequest = new HashMap<>();
                privateRequest.put("clientUid", user.getUid());
                privateRequest.put("professionalUid", null);
                privateRequest.put("address", jsonObjectToMap(data.optJSONObject("address")));
                privateRequest.put("createdAt", FieldValue.serverTimestamp());
                privateRequest.put("updatedAt", FieldValue.serverTimestamp());

                WriteBatch batch = db.batch();
                batch.set(requestRef, request);
                batch.set(db.collection("service_request_private").document(requestRef.getId()), privateRequest);
                batch.commit()
                        .addOnSuccessListener(activity, unused -> {
                            JSONObject payload = new JSONObject();
                            try { payload.put("id", requestRef.getId()); } catch (JSONException ignored) {}
                            emit("createRequest", true, payload);
                        })
                        .addOnFailureListener(activity, e -> emitError("createRequest", e));
            } catch (Exception e) {
                emitError("createRequest", e);
            }
        });
    }

    @JavascriptInterface
    public void listClientRequests() { history.load("clientRequests", "clientUid", false); }

    @JavascriptInterface
    public void listProfessionalJobs() { history.load("professionalJobs", "professionalUid", false); }

    @JavascriptInterface
    public void listOpenRequests() { history.loadOpen(false); }

    @JavascriptInterface
    public void loadMoreOpenRequests() { history.loadOpen(true); }

    private void emitRequests(String event, QuerySnapshot snapshot, boolean includeAddress) {
        try {
            List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
            Collections.sort(docs, (a, b) -> Long.compare(timestampMillis(b), timestampMillis(a)));
            JSONArray arr = new JSONArray();
            for (DocumentSnapshot d : docs) arr.put(requestJson(d, includeAddress));
            emit(event, true, objectWithArray("requests", arr));
        } catch (Exception e) {
            emitError(event, e);
        }
    }

    private JSONObject requestJson(DocumentSnapshot d, boolean includeAddress) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", d.getId());
        o.put("clientUid", string(d.getString("clientUid")));
        o.put("professionalUid", string(d.getString("professionalUid")));
        o.put("preferredProfessionalUid", string(d.getString("preferredProfessionalUid")));
        o.put("categoryId", string(d.getString("categoryId")));
        o.put("zoneId", string(d.getString("zoneId")));
        o.put("description", string(d.getString("description")));
        o.put("status", string(d.getString("status")));
        o.put("urgent", Boolean.TRUE.equals(d.getBoolean("urgent")));
        o.put("budgetRequired", Boolean.TRUE.equals(d.getBoolean("budgetRequired")));
        o.put("approvedTotalCents", d.getLong("approvedTotalCents") == null ? 0 : d.getLong("approvedTotalCents"));
        if (d.get("budget") instanceof Map) o.put("budget", new JSONObject((Map<?, ?>) d.get("budget")));
        o.put("createdAt", timestampMillis(d));
        return o;
    }

    @JavascriptInterface
    public void acceptRequest(String requestId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("requestAccepted", false, "NO_SESSION");
            return;
        }
        DocumentReference requestRef = db.collection("service_requests").document(requestId);
        DocumentReference privateRef = db.collection("service_request_private").document(requestId);
        DocumentReference proRef = db.collection("professionals").document(user.getUid());
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot pro = transaction.get(proRef);
            if (!"APPROVED".equals(pro.getString("verificationStatus")) || !Boolean.TRUE.equals(pro.getBoolean("availability"))) {
                throw new IllegalStateException("Tu perfil debe estar aprobado y disponible.");
            }
            DocumentSnapshot req = transaction.get(requestRef);
            if (!req.exists() || !"REQUESTED".equals(req.getString("status")) || req.get("professionalUid") != null) {
                throw new IllegalStateException("El pedido ya no está disponible.");
            }
            List<String> services = listStrings(pro.get("services"));
            List<String> zones = listStrings(pro.get("zones"));
            if (!services.contains(req.getString("categoryId")) || !zones.contains(req.getString("zoneId"))) {
                throw new IllegalStateException("Este pedido no corresponde a tus rubros o zonas.");
            }
            String preferred = req.getString("preferredProfessionalUid");
            if (preferred != null && !preferred.isBlank() && !user.getUid().equals(preferred)) {
                throw new IllegalStateException("Este pedido fue enviado a otro profesional.");
            }
            Map<String, Object> update = new HashMap<>();
            update.put("professionalUid", user.getUid());
            update.put("status", "ACCEPTED");
            update.put("acceptedAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(requestRef, update);
            Map<String, Object> privateUpdate = new HashMap<>();
            privateUpdate.put("professionalUid", user.getUid());
            privateUpdate.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(privateRef, privateUpdate);
            return null;
        }).addOnSuccessListener(activity, unused -> {
            requestRef.get().addOnSuccessListener(activity, req -> {
                Map<String, Object> chat = new HashMap<>();
                chat.put("members", List.of(req.getString("clientUid"), user.getUid()));
                chat.put("requestId", requestId);
                chat.put("createdAt", FieldValue.serverTimestamp());
                chat.put("updatedAt", FieldValue.serverTimestamp());
                db.collection("chats").document(requestId).set(chat, SetOptions.merge());
            });
            emitMessage("requestAccepted", true, "Pedido aceptado.");
        }).addOnFailureListener(activity, e -> emitError("requestAccepted", e));
    }

    @JavascriptInterface
    public void updateRequestStatus(String requestId, String newStatus) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("requestStatus", false, "NO_SESSION");
            return;
        }
        Set<String> allowed = Set.of("ON_THE_WAY", "IN_PROGRESS", "COMPLETED");
        if (!allowed.contains(newStatus)) {
            emitMessage("requestStatus", false, "Estado inválido.");
            return;
        }
        DocumentReference ref = db.collection("service_requests").document(requestId);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot req = transaction.get(ref);
            if (!user.getUid().equals(req.getString("professionalUid"))) {
                throw new IllegalStateException("No estás asignado a este pedido.");
            }
            String current = req.getString("status");
            boolean valid = ("ACCEPTED".equals(current) && "ON_THE_WAY".equals(newStatus))
                    || ("ON_THE_WAY".equals(current) && "IN_PROGRESS".equals(newStatus))
                    || ("IN_PROGRESS".equals(current) && "COMPLETED".equals(newStatus));
            if (!valid) throw new IllegalStateException("Transición de estado inválida.");
            Map<String, Object> update = new HashMap<>();
            update.put("status", newStatus);
            update.put("updatedAt", FieldValue.serverTimestamp());
            if ("COMPLETED".equals(newStatus)) update.put("completedAt", FieldValue.serverTimestamp());
            transaction.update(ref, update);
            return null;
        }).addOnSuccessListener(activity, unused -> emitMessage("requestStatus", true, "Estado actualizado."))
                .addOnFailureListener(activity, e -> emitError("requestStatus", e));
    }

    @JavascriptInterface
    public void cancelRequest(String requestId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("requestCancelled", false, "NO_SESSION");
            return;
        }
        DocumentReference ref = db.collection("service_requests").document(requestId);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot req = transaction.get(ref);
            if (!user.getUid().equals(req.getString("clientUid"))) {
                throw new IllegalStateException("No sos el titular de este pedido.");
            }
            String status = req.getString("status");
            if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
                throw new IllegalStateException("El pedido no puede cancelarse en este estado.");
            }
            Map<String, Object> update = new HashMap<>();
            update.put("status", "CANCELLED");
            update.put("cancelledAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(ref, update);
            return null;
        }).addOnSuccessListener(activity, unused -> emitMessage("requestCancelled", true, "Solicitud cancelada."))
                .addOnFailureListener(activity, e -> emitError("requestCancelled", e));
    }

    @JavascriptInterface
    public void listMessages(String requestId) { chats.listen(requestId); }

    @JavascriptInterface
    public void sendMessage(String requestId, String text) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitSendResult(requestId, "", false, "NO_SESSION");
            return;
        }
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() || clean.length() > 1000) {
            emitSendResult(requestId, user.getUid(), false, "El mensaje debe tener entre 1 y 1000 caracteres.");
            return;
        }
        DocumentReference chatRef = db.collection("chats").document(requestId);
        chatRef.get().addOnSuccessListener( chat -> {
            List<String> members = listStrings(chat.get("members"));
            if (!members.contains(user.getUid())) {
                emitSendResult(requestId, user.getUid(), false, "No tenés acceso a este chat.");
                return;
            }
            Map<String, Object> message = new HashMap<>();
            message.put("senderUid", user.getUid());
            message.put("text", clean);
            message.put("createdAt", FieldValue.serverTimestamp());
            chatRef.collection("messages").add(message)
                    .addOnSuccessListener( ref -> {
                        Map<String, Object> update = new HashMap<>();
                        update.put("lastMessage", clean);
                        update.put("updatedAt", FieldValue.serverTimestamp());
                        chatRef.set(update, SetOptions.merge());
                        emitSendResult(requestId, user.getUid(), true, "Mensaje enviado.");
                    })
                    .addOnFailureListener( e -> emitSendResult(requestId, user.getUid(), false, recoverableMessage(e)));
        }).addOnFailureListener( e -> emitSendResult(requestId, user.getUid(), false, recoverableMessage(e)));
    }

    private void emitSendResult(String requestId, String uid, boolean ok, String message) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("requestId", requestId); payload.put("ownerUid", uid); payload.put("message", message);
        } catch (JSONException ignored) {}
        emit("messageSent", ok, payload);
    }

    @JavascriptInterface
    public void chooseProfessionalDocument(String type) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("documentUpload", false, "NO_SESSION");
            return;
        }
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!Set.of("LICENSE", "BACKGROUND_CHECK", "INSURANCE").contains(normalized)) {
            emitMessage("documentUpload", false, "Tipo de documento inválido.");
            return;
        }
        if (BuildConfig.DOCUMENTS_API_URL.isBlank()) {
            emitMessage("documentUpload", false, "El servidor documental todavía no está configurado.");
            return;
        }
        pendingDocumentType = normalized;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/jpeg", "image/png", "image/webp"});
        activity.startActivityForResult(intent, PROFESSIONAL_DOCUMENT_REQUEST);
    }

    void onProfessionalDocumentCancelled() {
        pendingDocumentType = null;
        emitMessage("documentUpload", false, "Carga cancelada.");
    }

    void onProfessionalDocumentPicked(Uri uri) {
        final String type = pendingDocumentType;
        pendingDocumentType = null;
        if (uri == null || type == null) {
            emitMessage("documentUpload", false, "No se seleccionó un archivo.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage("documentUpload", false, "NO_SESSION");
            return;
        }
        user.getIdToken(true).addOnSuccessListener(activity, tokenResult -> {
            String token = tokenResult.getToken();
            if (token == null || token.isBlank()) {
                emitMessage("documentUpload", false, "No se pudo obtener una sesión válida.");
                return;
            }
            Executors.newSingleThreadExecutor().execute(() -> uploadDocument(uri, type, token, user.getUid()));
        }).addOnFailureListener(activity, e -> emitError("documentUpload", e));
    }

    private void uploadDocument(Uri uri, String type, String token, String uid) {
        HttpURLConnection connection = null;
        try {
            String name = queryDisplayName(uri);
            String mime = activity.getContentResolver().getType(uri);
            if (mime == null || mime.isBlank()) mime = "application/octet-stream";
            String boundary = "----Soluciona" + UUID.randomUUID().toString().replace("-", "");
            URL url = new URL(BuildConfig.DOCUMENTS_API_URL.replaceAll("/+$", "") + "/v1/professional-documents");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setChunkedStreamingMode(8192);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                writeUtf8(out, "--" + boundary + "\r\n");
                writeUtf8(out, "Content-Disposition: form-data; name=\"type\"\r\n\r\n");
                writeUtf8(out, type + "\r\n");
                writeUtf8(out, "--" + boundary + "\r\n");
                writeUtf8(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeHeader(name) + "\"\r\n");
                writeUtf8(out, "Content-Type: " + mime + "\r\n\r\n");
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("No se pudo leer el archivo.");
                    byte[] buffer = new byte[8192];
                    int read;
                    long total = 0;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > 10L * 1024L * 1024L) throw new IllegalStateException("El archivo supera 10 MB.");
                        out.write(buffer, 0, read);
                    }
                }
                writeUtf8(out, "\r\n--" + boundary + "--\r\n");
            }

            int code = connection.getResponseCode();
            InputStream responseStream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(responseStream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Error de almacenamiento (" + code + "): " + body);
            }
            JSONObject response = new JSONObject(body);
            String storageKey = response.optString("storageKey", "");
            if (storageKey.isBlank()) throw new IllegalStateException("El servidor no devolvió storageKey.");

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("professionalUid", uid);
            metadata.put("type", type);
            metadata.put("storageKey", storageKey);
            metadata.put("status", "PENDING");
            metadata.put("originalName", name);
            metadata.put("contentType", mime);
            metadata.put("createdAt", FieldValue.serverTimestamp());
            db.collection("professional_documents").add(metadata)
                    .addOnSuccessListener(activity, ref -> {
                        JSONObject payload = new JSONObject();
                        try {
                            payload.put("documentId", ref.getId());
                            payload.put("type", type);
                            payload.put("originalName", name);
                        } catch (JSONException ignored) {}
                        emit("documentUpload", true, payload);
                    })
                    .addOnFailureListener(activity, e -> emitError("documentUpload", e));
        } catch (Exception e) {
            emitError("documentUpload", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = "documento";
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return name == null || name.isBlank() ? "documento" : name;
    }

    private static String safeHeader(String value) {
        return value.replace("\r", "_").replace("\n", "_").replace("\"", "_");
    }

    private static void writeUtf8(DataOutputStream out, String text) throws Exception {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    @JavascriptInterface
    public void showPrivacyOptions() {
        activity.runOnUiThread(() -> {
            if (adsManager != null) adsManager.showPrivacyOptions();
        });
    }

    private void requireVerifiedEmail(String event, Runnable action) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emitMessage(event, false, "NO_SESSION");
            return;
        }
        user.reload().addOnSuccessListener(activity, unused -> {
            FirebaseUser refreshed = auth.getCurrentUser();
            if (refreshed == null || !refreshed.isEmailVerified()) {
                emitMessage(event, false, "Verificá tu correo antes de continuar.");
                return;
            }
            refreshed.getIdToken(true)
                    .addOnSuccessListener(activity, token -> action.run())
                    .addOnFailureListener(activity, e -> emitError(event, e));
        }).addOnFailureListener(activity, e -> emitError(event, e));
    }

    private static JSONObject objectWithArray(String key, JSONArray value) {
        JSONObject o = new JSONObject();
        try { o.put(key, value); } catch (JSONException ignored) {}
        return o;
    }

    private static String string(String value) {
        return value == null ? "" : value;
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private static long longNumber(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static long timestampMillis(DocumentSnapshot snapshot) {
        com.google.firebase.Timestamp ts = snapshot.getTimestamp("createdAt");
        return ts == null ? 0L : ts.toDate().getTime();
    }

    private static List<String> listStrings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) if (item != null) result.add(String.valueOf(item));
        }
        return result;
    }

    private static Map<String, Object> jsonObjectToMap(JSONObject object) {
        Map<String, Object> map = new HashMap<>();
        if (object == null) return map;
        JSONArray names = object.names();
        if (names == null) return map;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            map.put(key, object.opt(key));
        }
        return map;
    }

    private static List<String> jsonArrayToList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!value.isBlank()) list.add(value);
        }
        return list;
    }

    private void emitMessage(String event, boolean ok, String message) {
        JSONObject payload = new JSONObject();
        try { payload.put("message", message); } catch (JSONException ignored) {}
        emit(event, ok, payload);
    }

    private static String recoverableMessage(Exception error) {
        if (error instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code code =
                    ((com.google.firebase.firestore.FirebaseFirestoreException) error).getCode();
            switch (code) {
                case UNAVAILABLE: case DEADLINE_EXCEEDED:
                    return "No pudimos conectar. Revisá tu conexión y reintentá.";
                case PERMISSION_DENIED: return "No tenés permiso para consultar estos datos.";
                case FAILED_PRECONDITION: return "Esta consulta requiere configuración del servidor. Contactá a soporte.";
                default: return "No pudimos sincronizar los datos. Reintentá en unos instantes.";
            }
        }
        if (error instanceof IllegalStateException && "NO_SESSION".equals(error.getMessage())) return "NO_SESSION";
        return "No pudimos cargar los datos. Reintentá en unos instantes.";
    }

    private void emitError(String event, Exception exception) {
        if (exception instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
            emitMessage(event, false, recoverableMessage(exception)); return;
        }
        String message = exception.getMessage() == null ? "Error inesperado." : exception.getMessage();
        if (exception instanceof FirebaseAuthException) {
            message = ((FirebaseAuthException) exception).getErrorCode() + ": " + message;
        }
        emitMessage(event, false, message);
    }

    private void emit(String event, boolean ok, JSONObject payload) {
        if (!ok) RuntimeDiagnostics.failure(event);
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            String js = "window.solucionaNativeEvent && window.solucionaNativeEvent(" +
                    JSONObject.quote(event) + "," + ok + "," + payload.toString() + ");";
            webView.evaluateJavascript(js, null);
        });
    }
}
