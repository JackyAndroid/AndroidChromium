// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.support.v4.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.banners.AppData;

/**
 * Infobar informing the user about an app related to this page.
 */
public class AppBannerInfoBarAndroid extends ConfirmInfoBar implements View.OnClickListener {
    // Installation states.
    public static final int INSTALL_STATE_NOT_INSTALLED = 0;
    public static final int INSTALL_STATE_INSTALLING = 1;
    public static final int INSTALL_STATE_INSTALLED = 2;

    // Views composing the infobar.
    private Button mButton;
    private ViewGroup mTitleView;
    private View mIconView;

    private final String mAppTitle;

    // Data for native app installs.
    private final AppData mAppData;
    private int mInstallState;

    // Data for web app installs.
    private final String mAppUrl;

    // Banner for native apps.
    private AppBannerInfoBarAndroid(String appTitle, Bitmap iconBitmap, AppData data) {
        super(null, 0, iconBitmap, appTitle, null, data.installButtonText(), null);
        mAppTitle = appTitle;
        mAppData = data;
        mAppUrl = null;
        mInstallState = INSTALL_STATE_NOT_INSTALLED;
    }

    // Banner for web apps.
    private AppBannerInfoBarAndroid(String appTitle, Bitmap iconBitmap, String url) {
        super(null, 0, iconBitmap, appTitle, null, getAddToHomescreenText(), null);
        mAppTitle = appTitle;
        mAppData = null;
        mAppUrl = url;
        mInstallState = INSTALL_STATE_NOT_INSTALLED;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);

        mButton = layout.getPrimaryButton();
        mIconView = layout.getIcon();

        Resources res = getContext().getResources();
        int iconSize = res.getDimensionPixelSize(R.dimen.webapp_home_screen_icon_size);
        int iconSpacing = res.getDimensionPixelSize(R.dimen.app_banner_icon_spacing);
        layout.setIconSizeAndSpacing(iconSize, iconSize, iconSpacing);

        mTitleView = (ViewGroup) LayoutInflater.from(getContext()).inflate(
                R.layout.app_banner_title, null);
        TextView appName = (TextView) mTitleView.findViewById(R.id.app_name);
        RatingBar ratingView = (RatingBar) mTitleView.findViewById(R.id.rating_bar);
        TextView webAppUrl = (TextView) mTitleView.findViewById(R.id.web_app_url);
        appName.setText(mAppTitle);
        layout.setMessageView(mTitleView);

        Context context = getContext();
        if (mAppData != null) {
            // Native app.
            ImageView playLogo = new ImageView(layout.getContext());
            playLogo.setImageResource(R.drawable.google_play);
            layout.setCustomViewInButtonRow(playLogo);

            ratingView.setRating(mAppData.rating());
            layout.getPrimaryButton().setButtonColor(ApiCompatibilityUtils.getColor(
                    getContext().getResources(),
                    R.color.app_banner_install_button_bg));
            mTitleView.setContentDescription(context.getString(
                    R.string.app_banner_view_native_app_accessibility, mAppTitle,
                    mAppData.rating()));
            mTitleView.removeView(webAppUrl);
            updateButton();
        } else {
            // Web app.
            webAppUrl.setText(mAppUrl);
            mTitleView.setContentDescription(context.getString(
                    R.string.app_banner_view_web_app_accessibility, mAppTitle,
                    mAppUrl));
            mTitleView.removeView(ratingView);

        }

        // Hide uninteresting views from accessibility.
        ViewCompat.setImportantForAccessibility(ratingView, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (mIconView != null) {
            ViewCompat.setImportantForAccessibility(mIconView, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        // Set up clicking on the controls to bring up the app details.
        mTitleView.setOnClickListener(this);
        if (mIconView != null) mIconView.setOnClickListener(this);
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        if (mInstallState == INSTALL_STATE_INSTALLING) {
            setControlsEnabled(true);
            updateButton();
            return;
        }
        super.onButtonClicked(isPrimaryButton);
    }

    @CalledByNative
    public void onInstallStateChanged(int newState) {
        setControlsEnabled(true);
        mInstallState = newState;
        updateButton();
    }

    private void updateButton() {
        assert mAppData != null;

        String text;
        String accessibilityText = null;
        boolean enabled = true;
        if (mInstallState == INSTALL_STATE_NOT_INSTALLED) {
            text = mAppData.installButtonText();
            accessibilityText = getContext().getString(
                    R.string.app_banner_view_native_app_install_accessibility, text);
        } else if (mInstallState == INSTALL_STATE_INSTALLING) {
            text = getContext().getString(R.string.app_banner_installing);
            enabled = false;
        } else {
            text = getContext().getString(R.string.app_banner_open);
        }

        mButton.setText(text);
        mButton.setContentDescription(accessibilityText);
        mButton.setEnabled(enabled);
    }

    @Override
    public void onClick(View v) {
        if (v == mTitleView || v == mIconView) onLinkClicked();
    }

    private static String getAddToHomescreenText() {
        return ApplicationStatus.getApplicationContext().getString(R.string.menu_add_to_homescreen);
    }

    @CalledByNative
    private static InfoBar createNativeAppInfoBar(
            String appTitle, Bitmap iconBitmap, AppData appData) {
        return new AppBannerInfoBarAndroid(appTitle, iconBitmap, appData);
    }

    @CalledByNative
    private static InfoBar createWebAppInfoBar(String appTitle, Bitmap iconBitmap, String url) {
        return new AppBannerInfoBarAndroid(appTitle, iconBitmap, url);
    }
}
