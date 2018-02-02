// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.banners.AppData;
import org.chromium.chrome.browser.widget.DualControlLayout;

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
    private InfoBarControlLayout mMessageLayout;
    private View mTitleView;
    private View mIconView;

    private final String mAppTitle;

    // Data for native app installs.
    private final AppData mAppData;
    private int mInstallState;

    // Data for web app installs.
    private final String mAppUrl;

    // Indicates whether the infobar is for installing a WebAPK.
    private boolean mIsWebApk;

    // Banner for native apps.
    private AppBannerInfoBarAndroid(String appTitle, Bitmap iconBitmap, AppData data) {
        super(0, iconBitmap, appTitle, null, data.installButtonText(), null);
        mAppTitle = appTitle;
        mAppData = data;
        mAppUrl = null;
        mInstallState = INSTALL_STATE_NOT_INSTALLED;
    }

    // Banner for web apps.
    private AppBannerInfoBarAndroid(String appTitle, Bitmap iconBitmap, String url,
            boolean isWebApk) {
        super(0, iconBitmap, appTitle, null, getAddToHomescreenText(), null);
        mAppTitle = appTitle;
        mAppData = null;
        mAppUrl = url;
        mIsWebApk = isWebApk;
        mInstallState = INSTALL_STATE_NOT_INSTALLED;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);

        mButton = layout.getPrimaryButton();
        mIconView = layout.getIcon();
        layout.setIsUsingBigIcon();
        layout.setMessage(mAppTitle);

        mMessageLayout = layout.getMessageLayout();
        mTitleView = layout.getMessageTextView();

        Context context = getContext();
        if (mAppData != null) {
            // Native app.
            layout.getPrimaryButton().setButtonColor(ApiCompatibilityUtils.getColor(
                    context.getResources(), R.color.app_banner_install_button_bg));
            mMessageLayout.addRatingBar(mAppData.rating());
            mMessageLayout.setContentDescription(context.getString(
                    R.string.app_banner_view_native_app_accessibility, mAppTitle,
                    mAppData.rating()));
            updateButton();
        } else {
            // Web app.
            mMessageLayout.addDescription(mAppUrl);
            mMessageLayout.setContentDescription(context.getString(
                    R.string.app_banner_view_web_app_accessibility, mAppTitle,
                    mAppUrl));
        }

        // Hide uninteresting views from accessibility.
        if (mIconView != null) {
            ViewCompat.setImportantForAccessibility(mIconView, View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        // Clicking on the controls brings up the app's details.  The OnClickListener has to be
        // added to both the TextView and its parent because the TextView has special handling for
        // links within the text.
        mMessageLayout.setOnClickListener(this);
        mTitleView.setOnClickListener(this);
        if (mIconView != null) mIconView.setOnClickListener(this);
    }

    @Override
    protected void setButtons(InfoBarLayout layout, String primaryText, String secondaryText) {
        if (mAppData == null) {
            // The banner for web apps uses standard buttons.
            super.setButtons(layout, primaryText, secondaryText);
        } else {
            // The banner for native apps shows a Play logo in place of a secondary button.
            assert secondaryText == null;
            ImageView playLogo = new ImageView(layout.getContext());
            playLogo.setImageResource(R.drawable.google_play);
            layout.setBottomViews(primaryText, playLogo, DualControlLayout.ALIGN_APART);
        }
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        if (isPrimaryButton && mInstallState == INSTALL_STATE_INSTALLING) {
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
        if (mButton == null || (mAppData == null && !mIsWebApk)) return;

        String text;
        String accessibilityText = null;
        boolean enabled = true;
        Context context = getContext();
        if (mInstallState == INSTALL_STATE_NOT_INSTALLED) {
            if (mIsWebApk) {
                // If the installation of the WebAPK fails, the banner will disappear and
                // a failure toast will be shown.
                return;
            }
            text = mAppData.installButtonText();
            accessibilityText = context.getString(
                    R.string.app_banner_view_native_app_install_accessibility, text);
        } else if (mInstallState == INSTALL_STATE_INSTALLING) {
            text = mIsWebApk ? context.getString(R.string.app_banner_installing_webapk)
                    : context.getString(R.string.app_banner_installing);
            enabled = false;
        } else {
            text = context.getString(R.string.app_banner_open);
        }

        mButton.setText(text);
        mButton.setContentDescription(accessibilityText);
        mButton.setEnabled(enabled);
    }

    @Override
    public void onClick(View v) {
        if (v == mMessageLayout || v == mTitleView || v == mIconView) onLinkClicked();
    }

    private static String getAddToHomescreenText() {
        return ContextUtils.getApplicationContext().getString(
                AppBannerManager.getAppBannerLanguageOption());
    }

    @CalledByNative
    private static InfoBar createNativeAppInfoBar(
            String appTitle, Bitmap iconBitmap, AppData appData) {
        return new AppBannerInfoBarAndroid(appTitle, iconBitmap, appData);
    }

    @CalledByNative
    private static InfoBar createWebAppInfoBar(String appTitle, Bitmap iconBitmap, String url,
            boolean isWebApk) {
        return new AppBannerInfoBarAndroid(appTitle, iconBitmap, url, isWebApk);
    }
}
