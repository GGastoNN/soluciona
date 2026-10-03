package com.fixhome.soluciona;

import android.util.Log;

/** Debug-only local diagnostics. Never receives payloads, IDs, tokens or exception text. */
final class RuntimeDiagnostics {
    private RuntimeDiagnostics() {}
    static void startup(long milliseconds) {
        if (BuildConfig.DEBUG) Log.i("SolucionaMetrics", "event=web_ready duration_ms=" + Math.max(0, milliseconds));
    }
    static void failure(String operation) {
        if (!BuildConfig.DEBUG) return;
        String category;
        switch (operation) {
            case "clientRequests": case "professionalJobs": case "openRequests": case "messages":
                category = "sync"; break;
            case "paymentRequest": case "paymentStatus": case "paymentRequestQr": case "cardCheckoutResult":
                category = "payment"; break;
            default: return;
        }
        Log.w("SolucionaMetrics", "event=recoverable_failure category=" + category);
    }
}
