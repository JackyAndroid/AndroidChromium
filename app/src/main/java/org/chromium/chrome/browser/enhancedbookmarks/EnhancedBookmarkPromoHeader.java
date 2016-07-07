// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.sync.AndroidSyncSettings.AndroidSyncSettingsObserver;

/**
 * Class that manages all the logic and UI behind the signin promo header in the enhanced bookmark
 * content UI. The header is shown only on certain situations, (e.g., not signed in).
 */
class EnhancedBookmarkPromoHeader implements AndroidSyncSettingsObserver,
        SignInStateObserver {
    /**
     * Interface to listen signin promo header visibility changes.
     */
    interface PromoHeaderShowingChangeListener {
        /**
         * Called when signin promo header visibility is changed.
         * @param isShowing Whether it should be showing.
         */
        void onPromoHeaderShowingChanged(boolean isShowing);
    }

    private static final String PREF_SIGNIN_PROMO_DECLINED =
            "enhanced_bookmark_signin_promo_declined";
    private static final String PREF_SIGNIN_PROMO_SHOW_COUNT =
            "enhanced_bookmark_signin_promo_show_count";
    // TODO(kkimlabs): Figure out the optimal number based on UMA data.
    private static final int MAX_SIGNIN_PROMO_SHOW_COUNT = 5;

    private Context mContext;
    private SigninManager mSignInManager;
    private boolean mShouldShow;
    private PromoHeaderShowingChangeListener mShowingChangeListener;

    /**
     * Initializes the class. Note that this will start listening to signin related events and
     * update itself if needed.
     */
    EnhancedBookmarkPromoHeader(Context context,
            PromoHeaderShowingChangeListener showingChangeListener) {
        mContext = context;
        mShowingChangeListener = showingChangeListener;

        AndroidSyncSettings.registerObserver(mContext, this);

        mSignInManager = SigninManager.get(mContext);
        mSignInManager.addSignInStateObserver(this);

        updateShouldShow(false);
        if (shouldShow()) {
            int promoShowCount = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getInt(PREF_SIGNIN_PROMO_SHOW_COUNT, 0) + 1;
            PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                    .putInt(PREF_SIGNIN_PROMO_SHOW_COUNT, promoShowCount).apply();
            RecordUserAction.record("Stars_SignInPromoHeader_Displayed");
        }
    }

    /**
     * Clean ups the class.  Must be called once done using this class.
     */
    void destroy() {
        AndroidSyncSettings.unregisterObserver(mContext, this);

        mSignInManager.removeSignInStateObserver(this);
        mSignInManager = null;
    }

    /**
     * @return Whether it should be showing.
     */
    boolean shouldShow() {
        return mShouldShow;
    }

    /**
     * @return Signin promo header {@link ViewHolder} instance that can be used with
     *         {@link RecyclerView}.
     */
    ViewHolder createHolder(ViewGroup parent) {
        ViewGroup promoHeader = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.eb_promo_header, parent, false);

        promoHeader.findViewById(R.id.no_thanks).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                RecordUserAction.record("Stars_SignInPromoHeader_Dismissed");
                setSigninPromoDeclined();
                updateShouldShow(true);
            }
        });

        promoHeader.findViewById(R.id.sign_in).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mContext.startActivity(new Intent(mContext, EnhancedBookmarkSigninActivity.class));
            }
        });

        return new ViewHolder(promoHeader) {};
    }

    /**
     * @return Whether user tapped "No" button on the signin promo header.
     */
    private boolean wasSigninPromoDeclined() {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(
                PREF_SIGNIN_PROMO_DECLINED, false);
    }

    /**
     * Save that user tapped "No" button on the signin promo header.
     */
    private void setSigninPromoDeclined() {
        SharedPreferences.Editor sharedPreferencesEditor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        sharedPreferencesEditor.putBoolean(PREF_SIGNIN_PROMO_DECLINED, true);
        sharedPreferencesEditor.apply();
    }

    private void updateShouldShow(boolean notifyUI) {
        boolean oldIsShowing = mShouldShow;
        mShouldShow = AndroidSyncSettings.isMasterSyncEnabled(mContext)
                && mSignInManager.isSignInAllowed()
                && !wasSigninPromoDeclined()
                && PreferenceManager.getDefaultSharedPreferences(mContext).getInt(
                        PREF_SIGNIN_PROMO_SHOW_COUNT, 0) < MAX_SIGNIN_PROMO_SHOW_COUNT;
        if (oldIsShowing != mShouldShow && notifyUI) {
            mShowingChangeListener.onPromoHeaderShowingChanged(mShouldShow);
        }
    }

    // AndroidSyncSettingsObserver implementation

    @Override
    public void androidSyncSettingsChanged() {
        updateShouldShow(true);
    }

    // SignInStateObserver implementations

    @Override
    public void onSignedIn() {
        updateShouldShow(true);
    }

    @Override
    public void onSignedOut() {
        updateShouldShow(true);
    }
}
