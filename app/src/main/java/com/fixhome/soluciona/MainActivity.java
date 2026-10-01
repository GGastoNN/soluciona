package com.fixhome.soluciona;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends FragmentActivity {
    private static final String TAG = "SolucionaStartup";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final long MIN_SPLASH_MS = 950L;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private AdsManager adsManager;
    private FirebaseBridge bridge;
    private FeaturesBridge featuresBridge;
    private FrameLayout root;
    private FrameLayout adContainer;
    private View splashView;
    private long splashStartedAt;
    private boolean pageReady;
    private boolean enhancementsInjected;
    private boolean expectSessionForPage;
    private String startupDiagnostic = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Soluciona);
        super.onCreate(savedInstanceState);

        splashStartedAt = SystemClock.uptimeMillis();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        try {
            boolean darkMode = resolveDarkMode();
            int surfaceColor = surfaceColor(darkMode);
            buildUi(darkMode, surfaceColor);
            configureWebView();
            initializeServicesSafely();

            if (bridge == null || featuresBridge == null) {
                showStartupError(
                        "No se pudo conectar con los servicios de Soluciona. Revisá la configuración de Firebase.",
                        "SERVICES_INIT"
                );
                return;
            }

            webView.addJavascriptInterface(bridge, "SolucionaNative");
            webView.addJavascriptInterface(featuresBridge, "SolucionaFeatures");

            // The WebView must always be bootstrapped from a fresh local page after the
            // biometric gate. Restoring an Android WebView snapshot can restore the HTML
            // while leaving the JavaScript/native bridge bootstrap in the previous
            // "loading" state, which is exactly what caused the post-biometric freeze.
            boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
            boolean biometricEnabled = signedIn && featuresBridge.isBiometricEnabledForCurrentUser();

            if (biometricEnabled) {
                if (featuresBridge.canAuthenticateBiometric()) {
                    featuresBridge.authenticateForStartup(
                            () -> loadFreshApp(true),
                            () -> {
                                FirebaseAuth.getInstance().signOut();
                                loadFreshApp(false);
                            }
                    );
                } else {
                    // If biometrics were enabled and later removed/disabled, never bypass the gate.
                    FirebaseAuth.getInstance().signOut();
                    loadFreshApp(false);
                }
            } else {
                // Native Firebase state is the startup source of truth.
                // A clean install has signedIn=false and opens Welcome immediately.
                loadFreshApp(signedIn);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Fatal error during app startup", t);
            startupDiagnostic = diagnosticOf(t);
            showStartupError("Soluciona no pudo iniciar correctamente. Intentá nuevamente.", "STARTUP_FATAL");
        }
    }

    private void loadFreshApp(boolean expectSession) {
        if (webView == null) return;
        expectSessionForPage = expectSession;
        pageReady = false;
        enhancementsInjected = false;
        try {
            webView.stopLoading();
            webView.clearHistory();
        } catch (Throwable ignored) {
            // A fresh asset load below is the source of truth for app state.
        }

        // Do not ask JavaScript to discover Firebase startup state through a
        // synchronous bridge call. Native code already knows whether Firebase has
        // a persisted user, so pass that state explicitly to the local page.
        String sessionFlag = expectSession ? "1" : "0";
        webView.loadUrl("file:///android_asset/index.html?session=" + sessionFlag);
    }

    private void buildUi(boolean darkMode, int surfaceColor) {
        root = new FrameLayout(this);
        root.setBackgroundColor(surfaceColor);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(surfaceColor);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(surfaceColor);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);
        content.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        adContainer = new FrameLayout(this);
        adContainer.setBackgroundColor(darkMode ? Color.rgb(23, 32, 51) : Color.WHITE);
        adContainer.setVisibility(View.GONE);
        content.addView(adContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        splashView = createSplashView(darkMode);
        root.addView(splashView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        configureSystemBars(darkMode, surfaceColor);
        applySystemBarInsets(root);
        registerSystemBackHandler();
    }

    private View createSplashView(boolean darkMode) {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setPadding(dp(28), dp(28), dp(28), dp(28));
        splash.setBackgroundColor(darkMode ? Color.rgb(15, 23, 42) : Color.WHITE);
        splash.setClickable(true);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(148), dp(148));
        logoLp.bottomMargin = dp(20);
        splash.addView(logo, logoLp);

        TextView name = new TextView(this);
        name.setText("Soluciona.");
        name.setTextColor(darkMode ? Color.WHITE : Color.rgb(20, 33, 61));
        name.setTextSize(30);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        splash.addView(name);

        TextView slogan = new TextView(this);
        slogan.setText("Encontrá. Resolvé. Listo.");
        slogan.setTextColor(darkMode ? Color.rgb(148, 163, 184) : Color.rgb(100, 116, 139));
        slogan.setTextSize(15);
        slogan.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sloganLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sloganLp.topMargin = dp(7);
        splash.addView(slogan, sloganLp);

        return splash;
    }

    private void initializeServicesSafely() {
        try {
            FirebaseApp firebaseApp;
            if (FirebaseApp.getApps(this).isEmpty()) {
                firebaseApp = FirebaseApp.initializeApp(this);
            } else {
                firebaseApp = FirebaseApp.getInstance();
            }
            if (firebaseApp == null) {
                throw new IllegalStateException("FirebaseApp.initializeApp returned null");
            }
            Log.i(TAG, "Firebase ready: project=" + firebaseApp.getOptions().getProjectId());
        } catch (Throwable firebaseAppError) {
            startupDiagnostic = diagnosticOf(firebaseAppError);
            Log.e(TAG, "FirebaseApp initialization failed", firebaseAppError);
            bridge = null;
            return;
        }

        try {
            adsManager = new AdsManager(this, adContainer);
        } catch (Throwable adError) {
            Log.e(TAG, "AdMob/UMP initialization failed. App will continue without ads.", adError);
            adsManager = null;
            if (adContainer != null) adContainer.setVisibility(View.GONE);
        }

        try {
            bridge = new FirebaseBridge(this, webView, adsManager);
            featuresBridge = new FeaturesBridge(this, webView);
        } catch (Throwable serviceError) {
            startupDiagnostic = diagnosticOf(serviceError);
            Log.e(TAG, "Native bridge initialization failed", serviceError);
            bridge = null;
            featuresBridge = null;
        }
    }

    private boolean resolveDarkMode() {
        String pref = FeaturesBridge.readThemePreference(this);
        if ("dark".equals(pref)) return true;
        if ("light".equals(pref)) return false;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int surfaceColor(boolean darkMode) {
        return darkMode ? Color.rgb(15, 23, 42) : Color.rgb(244, 247, 252);
    }

    void applyThemePreference(String theme) {
        runOnUiThread(() -> {
            boolean dark;
            if ("dark".equals(theme)) dark = true;
            else if ("light".equals(theme)) dark = false;
            else dark = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

            int surface = surfaceColor(dark);
            if (root != null) root.setBackgroundColor(surface);
            if (webView != null) webView.setBackgroundColor(surface);
            if (adContainer != null) adContainer.setBackgroundColor(dark ? Color.rgb(23, 32, 51) : Color.WHITE);
            configureSystemBars(dark, surface);
        });
    }

    private void configureSystemBars(boolean darkMode, int surfaceColor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);

                View decorView = getWindow().getDecorView();
                if (decorView != null) {
                    WindowInsetsController controller = decorView.getWindowInsetsController();
                    if (controller != null) {
                        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                        controller.setSystemBarsAppearance(darkMode ? 0 : mask, mask);
                    }
                }
            } else {
                getWindow().setStatusBarColor(surfaceColor);
                getWindow().setNavigationBarColor(surfaceColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    View decorView = getWindow().getDecorView();
                    if (decorView != null) {
                        int flags = darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (!darkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        decorView.setSystemUiVisibility(flags);
                    }
                }
            }
        } catch (Throwable systemUiError) {
            Log.w(TAG, "System bar configuration failed; continuing", systemUiError);
        }
    }

    private void applySystemBarInsets(View target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        target.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        target.requestApplyInsets();
    }

    private void registerSystemBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackNavigation
            );
        }
    }

    private void handleBackNavigation() {
        if (splashView != null && splashView.getParent() != null && !pageReady) {
            finish();
            return;
        }
        if (webView == null) {
            finish();
            return;
        }

        webView.evaluateJavascript(
                "(window.solucionaAndroidBack && window.solucionaAndroidBack()) ? true : false",
                value -> {
                    boolean handledByApp = "true".equalsIgnoreCase(value);
                    if (handledByApp) return;
                    if (webView.canGoBack()) webView.goBack();
                    else finish();
                }
        );
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        webView.setWebViewClient(new WebViewClient() {
            private boolean openExternal(Uri uri) {
                if (uri == null) return false;
                String scheme = uri.getScheme();
                if (scheme == null) return false;
                if ("file".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)) return false;
                if ("soluciona".equalsIgnoreCase(scheme)) {
                    handleAppDeepLink(uri);
                    return true;
                }
                try {
                    Intent intent;
                    if ("tel".equalsIgnoreCase(scheme)) {
                        intent = new Intent(Intent.ACTION_DIAL, uri);
                    } else if ("mailto".equalsIgnoreCase(scheme)) {
                        intent = new Intent(Intent.ACTION_SENDTO, uri);
                    } else {
                        intent = new Intent(Intent.ACTION_VIEW, uri);
                    }
                    startActivity(intent);
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternal(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url != null && openExternal(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("file:///android_asset/")) {
                    pageReady = true;
                    injectEnhancements();

                    if (!expectSessionForPage) {
                        // Hard native fail-safe for a signed-out/clean launch. Even if a
                        // WebView URL query is lost or JavaScript startup order changes,
                        // the public Welcome screen wins and the app can never remain on
                        // "Conectando con Soluciona…" without a Firebase session.
                        view.post(() -> view.evaluateJavascript(
                                "(function(){try{if(typeof state!=='undefined'&&typeof render==='function'){state.profile=null;if(typeof backStack!=='undefined')backStack.length=0;state.screen='welcome';render();}}catch(e){}})();",
                                null
                        ));
                    } else if (bridge != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
                        // Retry the persisted Firebase session once after the page is
                        // fully attached. This closes the biometric/resume race.
                        view.postDelayed(() -> {
                            if (bridge != null) bridge.refreshSession();
                        }, 350L);
                    }

                    hideSplashWhenReady();
                    startAdsAfterUiIsVisible();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });
    }

    private void injectEnhancements() {
        if (enhancementsInjected || webView == null) return;
        enhancementsInjected = true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("features.js"), StandardCharsets.UTF_8))) {
            StringBuilder js = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) js.append(line).append('\n');
            webView.evaluateJavascript(js.toString(), null);
        } catch (Throwable e) {
            Log.e(TAG, "Could not inject features.js", e);
        }
    }

    private void handleAppDeepLink(Uri uri) {
        if (uri == null) return;
        if ("soluciona".equalsIgnoreCase(uri.getScheme())
                && "mp-connected".equalsIgnoreCase(uri.getHost())) {
            if (featuresBridge != null) featuresBridge.getMarketplaceStatus();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) handleAppDeepLink(intent.getData());
    }

    private void hideSplashWhenReady() {
        if (splashView == null || splashView.getParent() == null) return;
        long elapsed = SystemClock.uptimeMillis() - splashStartedAt;
        long delay = Math.max(0L, MIN_SPLASH_MS - elapsed);
        splashView.postDelayed(() -> {
            if (splashView == null || splashView.getParent() == null) return;
            splashView.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .withEndAction(() -> {
                        if (root != null && splashView != null) root.removeView(splashView);
                        splashView = null;
                    })
                    .start();
        }, delay);
    }

    private void startAdsAfterUiIsVisible() {
        if (adsManager == null || root == null) return;
        root.postDelayed(() -> {
            try {
                adsManager.requestConsentAndLoad();
            } catch (Throwable adError) {
                Log.e(TAG, "Ad request failed. Continuing without ads.", adError);
                if (adContainer != null) adContainer.setVisibility(View.GONE);
            }
        }, 250L);
    }

    private void showStartupError(String message, String code) {
        if (root == null) {
            root = new FrameLayout(this);
            setContentView(root);
        }
        if (splashView != null && splashView.getParent() != null) {
            root.removeView(splashView);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setBackgroundColor(Color.WHITE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(108), dp(108));
        logoLp.bottomMargin = dp(18);
        box.addView(logo, logoLp);

        TextView title = new TextView(this);
        title.setText("No pudimos iniciar Soluciona");
        title.setTextColor(Color.rgb(20, 33, 61));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView detail = new TextView(this);
        String visibleDetail = message + "\n\nCódigo: " + code;
        if (BuildConfig.DEBUG && startupDiagnostic != null && !startupDiagnostic.isEmpty()) {
            visibleDetail += "\n\nDiagnóstico: " + startupDiagnostic;
        }
        visibleDetail += "\n\nSoporte: infosoluciona2026@gmail.com";
        detail.setText(visibleDetail);
        detail.setTextColor(Color.rgb(100, 116, 139));
        detail.setTextSize(14);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        detailLp.topMargin = dp(10);
        detailLp.bottomMargin = dp(20);
        box.addView(detail, detailLp);

        Button retry = new Button(this);
        retry.setText("Reintentar");
        retry.setOnClickListener(v -> restartFresh());
        box.addView(retry, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        root.removeAllViews();
        root.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        splashView = box;
        pageReady = false;
        applySystemBarInsets(root);
    }

    private void restartFresh() {
        Intent restart = new Intent(this, MainActivity.class);
        restart.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        finish();
        startActivity(restart);
        overridePendingTransition(0, 0);
    }

    private static String diagnosticOf(Throwable t) {
        if (t == null) return "";
        String name = t.getClass().getSimpleName();
        String message = t.getMessage();
        if (message == null) message = "";
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 220) message = message.substring(0, 220);
        return message.isEmpty() ? name : name + ": " + message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Do not persist WebView execution state. Soluciona reconstructs its screen from
        // Firebase/Firestore after the biometric gate; persisting the WebView can leave
        // a restored page stuck on "Conectando con Soluciona…" without re-running boot.
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FirebaseBridge.PROFESSIONAL_DOCUMENT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && bridge != null) {
                bridge.onProfessionalDocumentPicked(data.getData());
            } else if (bridge != null) {
                bridge.onProfessionalDocumentCancelled();
            }
            return;
        }
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    @Override
    protected void onPause() {
        if (adsManager != null) adsManager.pause();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (adsManager != null) adsManager.resume();
    }

    @Override
    protected void onDestroy() {
        if (adsManager != null) adsManager.destroy();
        if (webView != null) {
            webView.removeJavascriptInterface("SolucionaNative");
            webView.removeJavascriptInterface("SolucionaFeatures");
            webView.destroy();
        }
        super.onDestroy();
    }
}
