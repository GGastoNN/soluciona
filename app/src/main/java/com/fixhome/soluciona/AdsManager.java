package com.fixhome.soluciona;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

final class AdsManager {
    private static final String TAG = "SolucionaAds";

    private final Activity activity;
    private final FrameLayout container;
    private final ConsentInformation consentInformation;
    private AdView adView;
    private boolean adsStarted = false;

    AdsManager(Activity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
        this.consentInformation = UserMessagingPlatform.getConsentInformation(activity);
    }

    void requestConsentAndLoad() {
        try {
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
            consentInformation.requestConsentInfoUpdate(
                    activity,
                    params,
                    () -> {
                        try {
                            UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                                    activity,
                                    formError -> {
                                        if (formError != null) {
                                            Log.w(TAG, "Consent form warning: " + formError.getMessage());
                                        }
                                        maybeStartAds();
                                    }
                            );
                            maybeStartAds();
                        } catch (Throwable t) {
                            Log.e(TAG, "Consent form failed", t);
                            hideContainer();
                        }
                    },
                    requestConsentError -> {
                        if (requestConsentError != null) {
                            Log.w(TAG, "Consent info warning: " + requestConsentError.getMessage());
                        }
                        maybeStartAds();
                    }
            );
        } catch (Throwable t) {
            Log.e(TAG, "UMP initialization failed", t);
            hideContainer();
        }
    }

    boolean isPrivacyOptionsRequired() {
        try {
            return consentInformation.getPrivacyOptionsRequirementStatus()
                    == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        } catch (Throwable t) {
            return false;
        }
    }

    void showPrivacyOptions() {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
                if (formError != null) Log.w(TAG, "Privacy options warning: " + formError.getMessage());
                if (consentInformation.canRequestAds()) maybeStartAds();
            });
        } catch (Throwable t) {
            Log.e(TAG, "Privacy options failed", t);
        }
    }

    private void maybeStartAds() {
        try {
            if (!consentInformation.canRequestAds() || adsStarted) return;
            adsStarted = true;
            MobileAds.initialize(activity, initializationStatus -> {
                try {
                    loadBannerWhenMeasured();
                } catch (Throwable t) {
                    Log.e(TAG, "Banner initialization failed", t);
                    hideContainer();
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Mobile Ads failed", t);
            adsStarted = false;
            hideContainer();
        }
    }

    private void loadBannerWhenMeasured() {
        container.setVisibility(View.VISIBLE);
        container.post(() -> {
            try {
                int widthPx = container.getWidth();
                if (widthPx <= 0) {
                    DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                    widthPx = metrics.widthPixels;
                }
                float density = activity.getResources().getDisplayMetrics().density;
                int adWidthDp = Math.max(320, (int) (widthPx / density));

                destroyBannerOnly();
                adView = new AdView(activity);
                adView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
                adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp));
                container.removeAllViews();
                container.addView(adView);
                adView.loadAd(new AdRequest.Builder().build());
            } catch (Throwable t) {
                Log.e(TAG, "Banner load failed", t);
                hideContainer();
            }
        });
    }

    private void hideContainer() {
        activity.runOnUiThread(() -> {
            destroyBannerOnly();
            container.removeAllViews();
            container.setVisibility(View.GONE);
        });
    }

    private void destroyBannerOnly() {
        if (adView != null) {
            try {
                adView.destroy();
            } catch (Throwable ignored) {
            }
            adView = null;
        }
    }

    void pause() {
        if (adView != null) {
            try { adView.pause(); } catch (Throwable ignored) {}
        }
    }

    void resume() {
        if (adView != null) {
            try { adView.resume(); } catch (Throwable ignored) {}
        }
    }

    void destroy() {
        destroyBannerOnly();
        container.removeAllViews();
    }
}
