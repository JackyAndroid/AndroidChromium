// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.os.Bundle;

import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;
import org.chromium.chrome.browser.ntp.RecentTabsPromoView;
import org.chromium.chrome.browser.ntp.RecentTabsPromoView.SyncPromoModel;
import org.chromium.chrome.browser.ntp.RecentTabsPromoView.UserActionListener;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.sync.AndroidSyncSettings.AndroidSyncSettingsObserver;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * Sign in promotion activity that is triggered from enhanced bookmark UI.
 */
public class EnhancedBookmarkSigninActivity extends EnhancedBookmarkActivityBase implements
        AndroidSyncSettingsObserver, SignInStateObserver, SyncPromoModel, UserActionListener {
    private SigninManager mSignInManager;
    private ProfileDataCache mProfileDataCache;
    private final ObserverList<AndroidSyncSettingsObserver> mObservers =
            new ObserverList<AndroidSyncSettingsObserver>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            RecordUserAction.record("Stars_SignInPromoActivity_Launched");
        }

        setContentView(new RecentTabsPromoView(this, this, this));

        AndroidSyncSettings.registerObserver(this, this);

        mSignInManager = SigninManager.get(this);
        mSignInManager.addSignInStateObserver(this);

        // This signin activity shouldn't be created if user is signed in already, but for just in
        // case it was signed in just before onCreate somehow.
        if (isSignedIn()) finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AndroidSyncSettings.unregisterObserver(this, this);

        mSignInManager.removeSignInStateObserver(this);
        mSignInManager = null;

        if (mProfileDataCache != null) {
            mProfileDataCache.destroy();
            mProfileDataCache = null;
        }
    }

    // AndroidSyncSettingsObserver

    @Override
    public void androidSyncSettingsChanged() {
        for (AndroidSyncSettingsObserver observer : mObservers) {
            observer.androidSyncSettingsChanged();
        }
    }

    // SignInStateObserver

    @Override
    public void onSignedIn() {
        androidSyncSettingsChanged();
        finish();
    }

    @Override
    public void onSignedOut() {
        assert false : "onSignedOut() called on signin activity.";
    }

    // SyncPromoModel

    @Override
    public boolean isSyncEnabled() {
        return AndroidSyncSettings.isSyncEnabled(this);
    }

    @Override
    public boolean isSignedIn() {
        return ChromeSigninController.get(this).isSignedIn();
    }

    @Override
    public void enableSync() {
        SyncController.get(this).start();
    }

    @Override
    public void registerForSyncUpdates(AndroidSyncSettingsObserver changeListener) {
        mObservers.addObserver(changeListener);
    }

    @Override
    public void unregisterForSyncUpdates(AndroidSyncSettingsObserver changeListener) {
        mObservers.removeObserver(changeListener);
    }

    // UserActionListener

    @Override
    public void onAccountSelectionConfirmed() {
        RecordUserAction.record("Stars_SignInPromoActivity_SignedIn");
    }

    @Override
    public void onNewAccount() {
        RecordUserAction.record("Stars_SignInPromoActivity_NewAccount");
    }

    @Override
    public ProfileDataCache getProfileDataCache() {
        if (mProfileDataCache == null) {
            mProfileDataCache = new ProfileDataCache(this, Profile.getLastUsedProfile());
        }
        return mProfileDataCache;
    }
}
