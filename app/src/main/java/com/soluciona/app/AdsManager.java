package com.soluciona.app;

import android.app.Activity;
import android.util.DisplayMetrics;
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
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> maybeStartAds());
                    maybeStartAds();
                },
                requestConsentError -> maybeStartAds()
        );
    }

    boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    void showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (consentInformation.canRequestAds()) maybeStartAds();
        });
    }

    private void maybeStartAds() {
        if (!consentInformation.canRequestAds() || adsStarted) return;
        adsStarted = true;
        MobileAds.initialize(activity, initializationStatus -> loadBannerWhenMeasured());
    }

    private void loadBannerWhenMeasured() {
        container.setVisibility(View.VISIBLE);
        container.post(() -> {
            int widthPx = container.getWidth();
            if (widthPx <= 0) {
                DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                widthPx = metrics.widthPixels;
            }
            float density = activity.getResources().getDisplayMetrics().density;
            int adWidthDp = Math.max(320, (int) (widthPx / density));

            destroy();
            adView = new AdView(activity);
            adView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp));
            container.removeAllViews();
            container.addView(adView);
            adView.loadAd(new AdRequest.Builder().build());
        });
    }

    void pause() {
        if (adView != null) adView.pause();
    }

    void resume() {
        if (adView != null) adView.resume();
    }

    void destroy() {
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        container.removeAllViews();
    }
}
