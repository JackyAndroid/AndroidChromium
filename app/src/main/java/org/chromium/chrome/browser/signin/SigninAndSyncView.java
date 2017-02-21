// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.signin.AccountSigninActivity.AccessPoint;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.sync.ui.SyncCustomizationFragment;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;
import org.chromium.components.sync.AndroidSyncSettings.AndroidSyncSettingsObserver;

/**
 * A View that shows the user the next step they must complete to start syncing their data (eg.
 * Recent Tabs or Bookmarks). For example, if the user is not signed in, the View will prompt them
 * to do so and link to the AccountSigninActivity.
 * If inflated manually, {@link SigninAndSyncView#init()} must be called before attaching this View
 * to a ViewGroup.
 */
public class SigninAndSyncView extends LinearLayout
        implements AndroidSyncSettingsObserver, SignInStateObserver {
    private Listener mListener;
    @AccessPoint private int mAccessPoint;
    private boolean mInitialized;
    private final SigninManager mSigninManager;

    private TextView mTitle;
    private TextView mDescription;
    private Button mNegativeButton;
    private Button mPositiveButton;

    /**
     * A listener for the container of the SigninAndSyncView to be informed of certain user
     * interactions.
     */
    public interface Listener {
        /**
         * The user has pressed 'no thanks' and expects the view to be removed from its parent.
         */
        public void onViewDismissed();
    }

    /**
     * A convenience method to inflate and initialize a SigninAndSyncView.
     * @param parent A parent used to provide LayoutParams (the SigninAndSyncView will not be
     *     attached).
     * @param listener Listener for user interactions.
     * @param accessPoint Where the SigninAndSyncView is used.
     */
    public static SigninAndSyncView create(ViewGroup parent, Listener listener,
            @AccessPoint int accessPoint) {
        SigninAndSyncView signinView = (SigninAndSyncView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.signin_and_sync_view, parent, false);
        signinView.init(listener, accessPoint);
        return signinView;
    }

    /**
     * Constructor for inflating from xml.
     */
    public SigninAndSyncView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSigninManager = SigninManager.get(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitle = (TextView) findViewById(R.id.title);
        mDescription = (TextView) findViewById(R.id.description);
        mNegativeButton = (Button) findViewById(R.id.no_thanks);
        mPositiveButton = (Button) findViewById(R.id.sign_in);
    }

    /**
     * Provide the information necessary for this class to function.
     * @param listener Listener for user interactions.
     * @param accessPoint Where this UI component is used.
     */
    public void init(Listener listener, @AccessPoint int accessPoint) {
        mListener = listener;
        mAccessPoint = accessPoint;
        mInitialized = true;

        assert mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER
                || mAccessPoint == SigninAccessPoint.RECENT_TABS
                : "SigninAndSyncView only has strings for bookmark manager and recent tabs.";

        // The title stays the same no matter what action the user must take.
        if (mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER) {
            mTitle.setText(R.string.sync_your_bookmarks);
        } else {
            mTitle.setVisibility(View.GONE);
        }

        // We don't call update() here as it will be called in onAttachedToWindow().
    }

    private void update() {
        ViewState viewState;
        if (!ChromeSigninController.get(getContext()).isSignedIn()) {
            viewState = getStateForSignin();
        } else if (!AndroidSyncSettings.isMasterSyncEnabled(getContext())) {
            viewState = getStateForEnableAndroidSync();
        } else if (!AndroidSyncSettings.isChromeSyncEnabled(getContext())) {
            viewState = getStateForEnableChromeSync();
        } else {
            viewState = getStateForStartUsing();
        }
        viewState.apply(mDescription, mPositiveButton, mNegativeButton);
    }

    /**
     * The ViewState class represents all the UI elements that can change for each variation of
     * this View. We use this to ensure each variation (created in the getStateFor* methods)
     * explicitly touches each UI element.
     */
    private static class ViewState {
        private final int mDescriptionText;
        private final ButtonState mPositiveButtonState;
        private final ButtonState mNegativeButtonState;

        public ViewState(int mDescriptionText,
                ButtonState mPositiveButtonState, ButtonState mNegativeButtonState) {
            this.mDescriptionText = mDescriptionText;
            this.mPositiveButtonState = mPositiveButtonState;
            this.mNegativeButtonState = mNegativeButtonState;
        }

        public void apply(TextView description, Button positiveButton, Button negativeButton) {
            description.setText(mDescriptionText);
            mNegativeButtonState.apply(negativeButton);
            mPositiveButtonState.apply(positiveButton);
        }
    }

    /**
     * Classes to represent the state of a button that we are interested in, used to keep ViewState
     * tidy and provide some convenience methods.
     */
    private interface ButtonState {
        void apply(Button button);
    }

    private static class ButtonAbsent implements ButtonState {
        @Override
        public void apply(Button button) {
            button.setVisibility(View.GONE);
        }
    }

    private static class ButtonPresent implements ButtonState {
        private final int mTextResource;
        private final OnClickListener mOnClickListener;

        public ButtonPresent(int textResource, OnClickListener onClickListener) {
            mTextResource = textResource;
            mOnClickListener = onClickListener;
        }

        @Override
        public void apply(Button button) {
            button.setVisibility(View.VISIBLE);
            button.setText(mTextResource);
            button.setOnClickListener(mOnClickListener);
        }
    }

    private ViewState getStateForSignin() {
        int descId = mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER
                ? R.string.bookmark_sign_in_promo_description
                : R.string.recent_tabs_sign_in_promo_description;

        ButtonState positiveButton = new ButtonPresent(
                R.string.sign_in_button,
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AccountSigninActivity
                                .startAccountSigninActivity(getContext(), mAccessPoint);
                    }
                });

        ButtonState negativeButton;
        if (mAccessPoint == SigninAccessPoint.RECENT_TABS) {
            negativeButton = new ButtonAbsent();
        } else {
            negativeButton = new ButtonPresent(R.string.no_thanks, new OnClickListener() {
                @Override
                public void onClick(View view) {
                    mListener.onViewDismissed();
                }
            });
        }

        return new ViewState(descId, positiveButton, negativeButton);
    }

    private ViewState getStateForEnableAndroidSync() {
        assert mAccessPoint == SigninAccessPoint.RECENT_TABS
                : "Enable Android Sync should not be showing from bookmarks";

        int descId = R.string.recent_tabs_sync_promo_enable_android_sync;

        ButtonState positiveButton = new ButtonPresent(
                R.string.open_settings_button,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO(crbug.com/557784): Like AccountManagementFragment, this would also
                        // benefit from going directly to an account.
                        Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                        intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[] {"com.google"});
                        getContext().startActivity(intent);
                    }
                });

        return new ViewState(descId, positiveButton, new ButtonAbsent());
    }

    private ViewState getStateForEnableChromeSync() {
        int descId = mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER
                ? R.string.bookmarks_sync_promo_enable_sync
                : R.string.recent_tabs_sync_promo_enable_chrome_sync;

        ButtonState positiveButton = new ButtonPresent(
                R.string.enable_sync_button,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PreferencesLauncher.launchSettingsPage(getContext(),
                                SyncCustomizationFragment.class.getName());
                    }
                });

        return new ViewState(descId, positiveButton, new ButtonAbsent());
    }

    private ViewState getStateForStartUsing() {
        assert mAccessPoint == SigninAccessPoint.RECENT_TABS
                : "This should not be showing from bookmarks";

        return new ViewState(R.string.ntp_recent_tabs_sync_promo_instructions,
                new ButtonAbsent(), new ButtonAbsent());
    }

    @Override
    protected void onAttachedToWindow() {
        assert mInitialized : "init(...) must be called on SigninAndSyncView before use.";

        super.onAttachedToWindow();
        mSigninManager.addSignInStateObserver(this);
        AndroidSyncSettings.registerObserver(getContext(), this);
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSigninManager.removeSignInStateObserver(this);
        AndroidSyncSettings.unregisterObserver(getContext(), this);
    }

    // SigninStateObserver
    @Override
    public void onSignedIn() {
        update();
    }

    @Override
    public void onSignedOut() {
        update();
    }

    // AndroidSyncStateObserver
    @Override
    public void androidSyncSettingsChanged() {
        update();
    }
}