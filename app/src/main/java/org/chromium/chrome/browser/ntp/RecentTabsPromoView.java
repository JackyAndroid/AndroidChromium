// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.AccountFirstRunView;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;
import org.chromium.chrome.browser.signin.AccountAdder;
import org.chromium.chrome.browser.sync.ui.ConfirmAccountChangeFragment;
import org.chromium.sync.AndroidSyncSettings.AndroidSyncSettingsObserver;

/**
 * Promo view on the recent tabs page that encourages the user to sign in to Chrome or enable sync.
 * This view handles three scenarios:
 *
 * 1. The user is not signed in: Shows the sign-in screen from first run.
 * 2. The user is signed in but sync is disabled: Displays a message encouraging the user
 *    to enable sync with a corresponding button.
 * 3. The user is signed in and sync is enabled: Displays a message instructing
 *    the user to sign in and open tabs on another device to use this awesome feature.
 */
public class RecentTabsPromoView extends FrameLayout implements AndroidSyncSettingsObserver {

    /**
     * Interface definition for the model this view needs to interact with.
     */
    public interface SyncPromoModel {
        /**
         * @return Whether sync is enabled.
         */
        public boolean isSyncEnabled();

        /**
         * @return Whether the user is signed in.
         */
        public boolean isSignedIn();

        /**
         * Enables sync for the current signed in account.
         */
        public void enableSync();

        /**
         * Attaches a listener to sync state changes.
         *
         * @param changeListener The SyncStateChangedListener The object to register for sync
         * updates.
         */
        public void registerForSyncUpdates(AndroidSyncSettingsObserver changeListener);

        /**
         * Removes a listener for sync state changes.
         *
         * @param changeListener The SyncStateChangedListener The object to unregister for sync
         * updates.
         */
        public void unregisterForSyncUpdates(AndroidSyncSettingsObserver changeListener);

        /**
         * @return A ProfileDataCache to retrieve user account info.
         */
        public ProfileDataCache getProfileDataCache();
    }

    /**
     * Interface for listening user actions on this UI.
     */
    public interface UserActionListener {
        /**
         * Called when user confirms an account to sign-in.
         */
        void onAccountSelectionConfirmed();

        /**
         * Called when user attempts to create a new account.
         */
        void onNewAccount();
    }

    private static final int PROMO_TYPE_SIGN_IN = 0;
    private static final int PROMO_TYPE_SYNC_DISABLED = 1;
    private static final int PROMO_TYPE_SYNC_ENABLED = 2;

    private static final int TEXT_COLOR_NORMAL = 0xff333333;
    private static final int TEXT_COLOR_LIGHT = 0xffa0a0a0;

    private static final long FADE_DURATION_MS = 300L;

    private Activity mActivity;
    private SyncPromoModel mModel;
    private UserActionListener mUserActionListener;

    private View mPromo;
    private int mPromoType = -1;
    private Animator mFadeAnimation;

    /**
     * Constructor for use from Java.
     *
     * @param activity The Activity this view will be presented in.
     * @param model The SyncPromoModel used to determine the state of sync and sign-in.
     */
    public RecentTabsPromoView(Activity activity, SyncPromoModel model,
            UserActionListener userActionListener) {
        super(activity);
        mModel = model;
        mActivity = activity;
        mUserActionListener = userActionListener;
        int sidePadding = getResources().getDimensionPixelOffset(R.dimen.recent_tabs_promo_padding);
        setPadding(sidePadding, 0, sidePadding, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mModel.registerForSyncUpdates(this);
        configureForSyncState(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mModel.unregisterForSyncUpdates(this);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            configureForSyncState(false);
        }
    }

    // AndroidSyncSettingsObserver
    @Override
    public void androidSyncSettingsChanged() {
        configureForSyncState(true);
    }

    private void configureForSyncState(boolean animate) {
        int desiredPromoType = getDesiredPromoType();
        if (mPromo != null && mPromoType == desiredPromoType) {
            return;
        }

        // In the rare case that the promo type changes while an animation is already underway,
        // cancel the existing animation and show the new promo without animation. This is a rare
        // case, and it's not worth the complexity (and bug potential) of implementing a three-way
        // cross fade.
        if (mFadeAnimation != null) {
            mFadeAnimation.end();
            animate = false;
        }

        if (animate && mPromoType == PROMO_TYPE_SIGN_IN) {
            ((AccountFirstRunView) mPromo).switchToSignedMode();
        }

        final View oldPromo = mPromo;
        mPromoType = desiredPromoType;
        mPromo = createPromoView(desiredPromoType);

        if (animate) {
            // Fade in the new promo on top of the old one. Set the background color to white so
            // the old promo effectively fades out as the new one fades in.
            mPromo.setAlpha(0f);
            mPromo.setBackgroundColor(Color.WHITE);
            addView(mPromo);

            mFadeAnimation = ObjectAnimator.ofFloat(mPromo, View.ALPHA, 1f);
            mFadeAnimation.setDuration(FADE_DURATION_MS);
            mFadeAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mFadeAnimation = null;
                    mPromo.setBackgroundResource(0);
                    removeView(oldPromo);
                }
            });
            mFadeAnimation.start();
        } else {
            removeView(oldPromo);
            addView(mPromo);
        }
    }

    private int getDesiredPromoType() {
        if (!mModel.isSignedIn()) {
            return PROMO_TYPE_SIGN_IN;
        } else {
            return mModel.isSyncEnabled() ? PROMO_TYPE_SYNC_ENABLED : PROMO_TYPE_SYNC_DISABLED;
        }
    }

    private View createPromoView(int promoType) {
        if (promoType == PROMO_TYPE_SIGN_IN) {
            return createSignInPromoView();
        } else {
            return createSyncPromoView(promoType == PROMO_TYPE_SYNC_ENABLED);
        }
    }

    private View createSyncPromoView(boolean isSyncEnabled) {
        View syncPromoView = LayoutInflater.from(getContext()).inflate(
                R.layout.recent_tabs_sync_promo, this, false);

        TextView textView = (TextView) syncPromoView.findViewById(R.id.text_view);
        View enableSyncButton = syncPromoView.findViewById(R.id.enable_sync_button);

        if (isSyncEnabled) {
            textView.setText(R.string.ntp_recent_tabs_sync_promo_instructions);
            textView.setTextColor(TEXT_COLOR_LIGHT);
            enableSyncButton.setVisibility(View.GONE);
        } else {
            textView.setText(R.string.ntp_recent_tabs_sync_enable_sync);
            textView.setTextColor(TEXT_COLOR_NORMAL);
            enableSyncButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mModel.enableSync();
                }
            });
        }

        return syncPromoView;
    }

    private View createSignInPromoView() {
        AccountFirstRunView signInPromoView = (AccountFirstRunView)
                LayoutInflater.from(getContext()).inflate(R.layout.fre_choose_account, this, false);
        signInPromoView.init(mModel.getProfileDataCache());
        signInPromoView.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
        ((FrameLayout.LayoutParams) signInPromoView.getLayoutParams()).gravity = Gravity.CENTER;
        signInPromoView.configureForRecentTabsPage();
        signInPromoView.setCanCancel(false);
        signInPromoView.setListener(new AccountFirstRunView.Listener() {
            @Override
            public void onAccountSelectionConfirmed(String accountName) {
                if (mUserActionListener != null) mUserActionListener.onAccountSelectionConfirmed();

                ConfirmAccountChangeFragment.confirmSyncAccount(accountName, mActivity);
            }

            @Override
            public void onAccountSelectionCanceled() {
                assert false : "Button should be hidden";
            }

            @Override
            public void onNewAccount() {
                if (mUserActionListener != null) mUserActionListener.onNewAccount();

                AccountAdder.getInstance().addAccount(mActivity, AccountAdder.ADD_ACCOUNT_RESULT);
            }

            @Override
            public void onSigningInCompleted(String accountName) {
                assert false : "Button should be hidden";
            }

            @Override
            public void onSettingsButtonClicked(String accountName) {
                assert false : "Button should be hidden";
            }

            @Override
            public void onFailedToSetForcedAccount(String forcedAccountName) {
                // TODO(bauerb): make sure we shouldn't see SignInPromoView.
                assert false : "No forced accounts in SignInPromoView";
            }
        });

        return signInPromoView;
    }
}
