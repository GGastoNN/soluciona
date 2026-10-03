package com.fixhome.soluciona;

import android.app.Activity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Latest messages remain live; older pages use a stable document cursor. */
final class ChatRepository {
    interface Output {
        void success(JSONObject payload);
        void failure(String requestId, Exception error);
    }
    private static final int PAGE_SIZE = 50;
    private final Activity activity;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final Output output;
    private ListenerRegistration listener;
    private String chatId = "", uid = "";
    private int generation;
    private DocumentSnapshot oldest;
    private boolean historyLoading, hasMore, initialReady, historyStarted;
    ChatRepository(Activity activity, FirebaseAuth auth, FirebaseFirestore db, Output output) {
        this.activity = activity; this.auth = auth; this.db = db; this.output = output;
    }
    void stop() { activity.runOnUiThread(this::clear); }
    private void clear() {
        generation++;
        if (listener != null) listener.remove();
        listener = null; chatId = ""; uid = ""; oldest = null;
        historyLoading = false; hasMore = false; initialReady = false; historyStarted = false;
    }
    private boolean current(int token) {
        FirebaseUser user = auth.getCurrentUser();
        return token == generation && user != null && uid.equals(user.getUid());
    }
    private Query query() {
        return db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING);
    }
    void listen(String requestId) {
        activity.runOnUiThread(() -> {
            clear();
            if (requestId == null || requestId.isBlank() || requestId.contains("/")) return;
            chatId = requestId;
            final int token = generation;
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { output.failure(requestId, new IllegalStateException("NO_SESSION")); return; }
            uid = user.getUid();
            db.collection("chats").document(requestId).get().addOnSuccessListener(activity, chat -> {
                if (!current(token)) return;
                Object members = chat.get("members");
                if (!(members instanceof List) || !((List<?>) members).contains(uid)) {
                    output.failure(requestId, new IllegalStateException("No tenés acceso a este chat.")); return;
                }
                listener = query().limit(PAGE_SIZE).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!current(token)) return;
                    if (error != null) { output.failure(requestId, error); return; }
                    if (snapshot == null) return;
                    // A moving latest window must not advance the older-page boundary.
                    if (!historyStarted && !snapshot.getMetadata().hasPendingWrites()
                            && (!initialReady || !snapshot.getMetadata().isFromCache())) {
                        List<DocumentSnapshot> docs = snapshot.getDocuments();
                        oldest = docs.isEmpty() ? null : docs.get(docs.size() - 1);
                        hasMore = docs.size() == PAGE_SIZE;
                        initialReady = true;
                    }
                    publish(snapshot, false);
                });
            }).addOnFailureListener(activity, error -> {
                if (current(token)) output.failure(requestId, error);
            });
        });
    }
    void older(String requestId) {
        activity.runOnUiThread(() -> {
            if (!requestId.equals(chatId) || !current(generation) || historyLoading
                    || !initialReady || !hasMore || oldest == null) return;
            final int token = generation;
            historyLoading = true; historyStarted = true;
            query().startAfter(oldest).limit(PAGE_SIZE + 1).get()
                .addOnSuccessListener(activity, snapshot -> {
                    if (!current(token)) return;
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    hasMore = docs.size() > PAGE_SIZE;
                    int count = Math.min(PAGE_SIZE, docs.size());
                    if (count > 0) oldest = docs.get(count - 1);
                    historyLoading = false;
                    publish(snapshot, true);
                }).addOnFailureListener(activity, error -> {
                    if (!current(token)) return;
                    historyLoading = false; output.failure(requestId, error);
                });
        });
    }
    private void publish(QuerySnapshot snapshot, boolean older) {
        try {
            List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
            if (older && docs.size() > PAGE_SIZE) docs.remove(docs.size() - 1);
            Collections.reverse(docs);
            JSONArray messages = new JSONArray();
            for (DocumentSnapshot doc : docs) {
                JSONObject item = new JSONObject();
                item.put("id", doc.getId()); item.put("senderUid", doc.getString("senderUid"));
                item.put("text", doc.getString("text"));
                com.google.firebase.Timestamp time = doc.getTimestamp("createdAt");
                item.put("createdAt", time == null ? 0 : time.toDate().getTime());
                messages.put(item);
            }
            JSONObject payload = new JSONObject();
            payload.put("ownerUid", uid); payload.put("requestId", chatId); payload.put("messages", messages);
            payload.put("older", older); payload.put("hasMore", hasMore);
            payload.put("fromCache", snapshot.getMetadata().isFromCache());
            output.success(payload);
        } catch (Exception error) { output.failure(chatId, error); }
    }
}
