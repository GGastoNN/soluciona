package com.fixhome.soluciona;

import android.app.Activity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Ordered, bounded history queries. All mutable state belongs to the UI thread. */
final class RequestHistoryRepository {
    interface Output {
        JSONObject request(DocumentSnapshot doc) throws Exception;
        void success(String event, JSONObject payload);
        void failure(String event, Exception error);
    }
    private static final int PAGE_SIZE = 20;
    private final Activity activity;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final Output output;
    private final Map<String, Page> pages = new HashMap<>();
    private static final class Page {
        String uid;
        DocumentSnapshot cursor;
        boolean loading, more = true;
        int generation;
        Set<String> services = new HashSet<>(), zones = new HashSet<>();
    }
    RequestHistoryRepository(Activity activity, FirebaseAuth auth, FirebaseFirestore db, Output output) {
        this.activity = activity; this.auth = auth; this.db = db; this.output = output;
    }
    void clear() { activity.runOnUiThread(pages::clear); }
    void load(String event, String ownerField, boolean append) {
        activity.runOnUiThread(() -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { output.failure(event, new IllegalStateException("NO_SESSION")); return; }
            Page page = pages.computeIfAbsent(event, key -> new Page());
            if (append && page.loading) return;
            if (!append || !user.getUid().equals(page.uid)) {
                page.generation++; page.cursor = null; page.more = true; page.loading = false;
                page.uid = user.getUid(); appendLoad(event, ownerField, page, false);
            } else if (page.more) appendLoad(event, ownerField, page, true);
        });
    }
    void loadOpen(boolean append) {
        activity.runOnUiThread(() -> {
            final String event = "openRequests";
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { output.failure(event, new IllegalStateException("NO_SESSION")); return; }
            Page page = pages.computeIfAbsent(event, key -> new Page());
            if (append && user.getUid().equals(page.uid)) {
                if (!page.loading && page.more) appendLoad(event, "status", page, true);
                return;
            }
            page.uid = user.getUid(); page.generation++; page.loading = true;
            page.cursor = null; page.more = true;
            final int generation = page.generation;
            db.collection("professionals").document(page.uid).get().addOnSuccessListener(activity, pro -> {
                if (!current(event, page, generation)) return;
                if (!pro.exists() || !"APPROVED".equals(pro.getString("verificationStatus"))) {
                    hydrate(event, page, generation, false, false, pro.getMetadata().isFromCache(),
                            new ArrayList<>(), null);
                    return;
                }
                page.services = strings(pro.get("services")); page.zones = strings(pro.get("zones"));
                appendLoad(event, "status", page, false);
            }).addOnFailureListener(activity, error -> {
                if (!current(event, page, generation)) return;
                page.loading = false; output.failure(event, error);
            });
        });
    }
    private static Set<String> strings(Object value) {
        Set<String> result = new HashSet<>();
        if (value instanceof List<?>) for (Object item : (List<?>) value)
            if (item != null) result.add(String.valueOf(item));
        return result;
    }
    private boolean current(String event, Page page, int generation) {
        FirebaseUser user = auth.getCurrentUser();
        return pages.get(event) == page && page.generation == generation
                && user != null && user.getUid().equals(page.uid);
    }
    private void appendLoad(String event, String field, Page page, boolean append) {
        page.loading = true;
        final int generation = page.generation;
        Query query = db.collection("service_requests").whereEqualTo(field, "openRequests".equals(event) ? "REQUESTED" : page.uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGE_SIZE + 1);
        if (append && page.cursor != null) query = query.startAfter(page.cursor);
        query.get().addOnSuccessListener(activity, snapshot -> {
            if (!current(event, page, generation)) return;
            List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
            final boolean more = docs.size() > PAGE_SIZE;
            if (more) docs.remove(docs.size() - 1);
            DocumentSnapshot nextCursor = docs.isEmpty() ? page.cursor : docs.get(docs.size() - 1);
            if ("openRequests".equals(event)) {
                docs.removeIf(doc -> !page.services.contains(doc.getString("categoryId"))
                        || !page.zones.contains(doc.getString("zoneId"))
                        || (doc.getString("preferredProfessionalUid") != null
                            && !doc.getString("preferredProfessionalUid").isBlank()
                            && !page.uid.equals(doc.getString("preferredProfessionalUid"))));
            }
            hydrate(event, page, generation, append, more, snapshot.getMetadata().isFromCache(), docs, nextCursor);
        }).addOnFailureListener(activity, error -> {
            if (!current(event, page, generation)) return;
            page.loading = false; output.failure(event, error);
        });
    }
    private void hydrate(String event, Page page, int generation, boolean append, boolean more, boolean fromCache,
                         List<DocumentSnapshot> docs, DocumentSnapshot nextCursor) {
        // Slot by query position: private reads may finish in any order.
        JSONObject[] items = new JSONObject[docs.size()];
        AtomicInteger remaining = new AtomicInteger(docs.size());
        boolean[] partial = {false};
        Runnable complete = () -> {
            if (!current(event, page, generation)) return;
            try {
                JSONArray array = new JSONArray();
                for (JSONObject item : items) array.put(item);
                JSONObject payload = new JSONObject();
                payload.put("ownerUid", page.uid); payload.put("requests", array); payload.put("append", append);
                payload.put("fromCache", fromCache); payload.put("hasMore", more); payload.put("privateDataUnavailable", partial[0]);
                page.cursor = nextCursor;
                page.more = more; page.loading = false;
                output.success(event, payload);
            } catch (Exception error) { page.loading = false; output.failure(event, error); }
        };
        if (docs.isEmpty()) { complete.run(); return; }
        for (int i = 0; i < docs.size(); i++) {
            final int slot = i;
            DocumentSnapshot doc = docs.get(i);
            try { items[slot] = output.request(doc); }
            catch (Exception error) { page.loading = false; page.generation++; output.failure(event, error); return; }
            // Closed services only show the public zone in history; no address read needed.
            String status = doc.getString("status");
            if ("openRequests".equals(event) || "COMPLETED".equals(status) || "CANCELLED".equals(status)) {
                if (remaining.decrementAndGet() == 0) complete.run();
                continue;
            }
            db.collection("service_request_private").document(doc.getId()).get()
                .addOnCompleteListener(activity, task -> {
                    if (!current(event, page, generation)) return;
                    try {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            Object address = task.getResult().get("address");
                            if (address instanceof Map) items[slot].put("address", new JSONObject((Map<?, ?>) address));
                        } else partial[0] = true;
                    } catch (Exception error) { partial[0] = true; }
                    if (remaining.decrementAndGet() == 0) complete.run();
                });
        }
    }
}
