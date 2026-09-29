package com.fixhome.soluciona;

import android.app.Activity;
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

public class MainActivity extends Activity {
    private static final String TAG = "SolucionaStartup";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final long MIN_SPLASH_MS = 1100L;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private AdsManager adsManager;
    private FirebaseBridge bridge;
    private FrameLayout root;
    private FrameLayout adContainer;
    private View splashView;
    private long splashStartedAt;
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the normal app theme immediately after the Android launch preview.
        setTheme(com.fixhome.soluciona.R.style.Theme_Soluciona);
        super.onCreate(savedInstanceState);

        splashStartedAt = SystemClock.uptimeMillis();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        final boolean darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int surfaceColor = darkMode ? Color.rgb(15, 23, 42) : Color.rgb(244, 247, 252);

        try {
            configureSystemBars(darkMode, surfaceColor);
            buildUi(darkMode, surfaceColor);
            configureWebView();
            initializeServicesSafely();

            if (bridge == null) {
                showStartupError("No se pudo conectar con Firebase. Revisá la configuración de la aplicación.");
                return;
            }

            webView.addJavascriptInterface(bridge, "SolucionaNative");
            if (savedInstanceState == null) {
                webView.loadUrl("file:///android_asset/index.html");
            } else {
                webView.restoreState(savedInstanceState);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Fatal error during app startup", t);
            showStartupError("Soluciona no pudo iniciar correctamente. Intentá nuevamente.");
        }
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
        splash.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

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
            adsManager = new AdsManager(this, adContainer);
        } catch (Throwable adError) {
            Log.e(TAG, "AdMob/UMP initialization failed. App will continue without ads.", adError);
            adsManager = null;
            if (adContainer != null) adContainer.setVisibility(View.GONE);
        }

        try {
            bridge = new FirebaseBridge(this, webView, adsManager);
        } catch (Throwable firebaseError) {
            Log.e(TAG, "Firebase initialization failed", firebaseError);
            bridge = null;
        }
    }

    private void configureSystemBars(boolean darkMode, int surfaceColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 15+ enforces edge-to-edge for modern targets. We explicitly opt in,
            // then add the real system-bar/cutout insets to the root view below.
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkMode ? 0 : mask, mask);
            }
        } else {
            getWindow().setStatusBarColor(surfaceColor);
            getWindow().setNavigationBarColor(surfaceColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (!darkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
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

                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
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

    private void showStartupError(String message) {
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
        detail.setText(message + "\n\nSoporte: infosoluciona2026@gmail.com");
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
        retry.setOnClickListener(v -> recreate());
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
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
            webView.destroy();
        }
        super.onDestroy();
    }
}
