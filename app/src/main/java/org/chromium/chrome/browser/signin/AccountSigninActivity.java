// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;

import org.chromium.base.Log;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.preferences.ManagedPreferencesUtils;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager.SignInCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An Activity displayed from the MainPreferences to allow the user to pick an account to
 * sign in to. The AccountSigninView.Delegate interface is fulfilled by the AppCompatActivity.
 */
public class AccountSigninActivity extends AppCompatActivity
        implements AccountSigninView.Listener, AccountSigninView.Delegate {
    private static final String TAG = "AccountSigninActivity";
    private static final String INTENT_SIGNIN_ACCESS_POINT =
            "AccountSigninActivity.SigninAccessPoint";

    private AccountSigninView mView;
    private ProfileDataCache mProfileDataCache;

    @IntDef({SigninAccessPoint.SETTINGS, SigninAccessPoint.BOOKMARK_MANAGER,
            SigninAccessPoint.RECENT_TABS, SigninAccessPoint.SIGNIN_PROMO,
            SigninAccessPoint.NTP_CONTENT_SUGGESTIONS, SigninAccessPoint.AUTOFILL_DROPDOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AccessPoint {}
    @AccessPoint private int mAccessPoint;

    /**
     * A convenience method to create a AccountSigninActivity passing the access point as an
     * intent.
     * @param accessPoint - A SigninAccessPoint designating where the activity is created from.
     */
    public static void startAccountSigninActivity(Context context, @AccessPoint int accessPoint) {
        Intent intent = new Intent(context, AccountSigninActivity.class);
        intent.putExtra(INTENT_SIGNIN_ACCESS_POINT, accessPoint);
        context.startActivity(intent);
    }

    /**
     * A convenience method to create a AccountSigninActivity passing the access point as an
     * intent. Checks if the sign in flow can be started before showing the activity.
     * @param accessPoint - A SigninAccessPoint designating where the activity is created from.
     * @return {@code true} if sign in has been allowed.
     */
    public static boolean startIfAllowed(Context context, @AccessPoint int accessPoint) {
        if (!SigninManager.get(context).isSignInAllowed()) {
            if (SigninManager.get(context).isSigninDisabledByPolicy()) {
                ManagedPreferencesUtils.showManagedByAdministratorToast(context);
            }
            return false;
        }

        startAccountSigninActivity(context, accessPoint);
        return true;
    }

    @Override
    @SuppressFBWarnings("DM_EXIT")
    protected void onCreate(Bundle savedInstanceState) {
        // The browser process must be started here because this activity may be started from the
        // recent apps list and it relies on other activities and the native library to be loaded.
        try {
            ChromeBrowserInitializer.getInstance(this).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.e(TAG, "Failed to start browser process.", e);
            // Since the library failed to initialize nothing in the application
            // can work, so kill the whole application not just the activity
            System.exit(-1);
        }

        // We don't trust android to restore the saved state correctly, so pass null.
        super.onCreate(null);

        mAccessPoint = getIntent().getIntExtra(INTENT_SIGNIN_ACCESS_POINT, -1);
        assert mAccessPoint == SigninAccessPoint.BOOKMARK_MANAGER
                || mAccessPoint == SigninAccessPoint.RECENT_TABS
                || mAccessPoint == SigninAccessPoint.SETTINGS
                || mAccessPoint == SigninAccessPoint.SIGNIN_PROMO
                || mAccessPoint == SigninAccessPoint.NTP_CONTENT_SUGGESTIONS
                || mAccessPoint == SigninAccessPoint.AUTOFILL_DROPDOWN
                : "invalid access point: " + mAccessPoint;

        mView = (AccountSigninView) LayoutInflater.from(this).inflate(
                R.layout.account_signin_view, null);
        mView.init(getProfileDataCache(), this, this);

        if (getAccessPoint() == SigninAccessPoint.BOOKMARK_MANAGER
                || getAccessPoint() == SigninAccessPoint.RECENT_TABS) {
            mView.configureForRecentTabsOrBookmarksPage();
        }

        setContentView(mView);

        SigninManager.logSigninStartAccessPoint(getAccessPoint());
        recordSigninStartedUserAction();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mProfileDataCache != null) {
            mProfileDataCache.destroy();
            mProfileDataCache = null;
        }
    }

    private ProfileDataCache getProfileDataCache() {
        if (mProfileDataCache == null) {
            mProfileDataCache = new ProfileDataCache(this, Profile.getLastUsedProfile());
        }
        return mProfileDataCache;
    }

    @AccessPoint private int getAccessPoint() {
        return mAccessPoint;
    }

    @Override
    public void onAccountSelectionCanceled() {
        finish();
    }

    @Override
    public void onNewAccount() {
        AccountAdder.getInstance().addAccount(this, AccountAdder.ADD_ACCOUNT_RESULT);
    }

    @Override
    public void onAccountSelected(final String accountName, final boolean settingsClicked) {
        final Context context = this;
        SigninManager.get(this).signIn(accountName, this, new SignInCallback(){

            @Override
            public void onSignInComplete() {
                if (settingsClicked) {
                    Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                            context, AccountManagementFragment.class.getName());
                    startActivity(intent);
                }

                finish();
            }

            @Override
            public void onSignInAborted() {}
        });
    }

    @Override
    public void onFailedToSetForcedAccount(String forcedAccountName) {}

    private void recordSigninStartedUserAction() {
        switch (getAccessPoint()) {
            case SigninAccessPoint.AUTOFILL_DROPDOWN:
                RecordUserAction.record("Signin_Signin_FromAutofillDropdown");
                break;
            case SigninAccessPoint.BOOKMARK_MANAGER:
                RecordUserAction.record("Signin_Signin_FromBookmarkManager");
                break;
            case SigninAccessPoint.RECENT_TABS:
                RecordUserAction.record("Signin_Signin_FromRecentTabs");
                break;
            case SigninAccessPoint.SETTINGS:
                RecordUserAction.record("Signin_Signin_FromSettings");
                break;
            case SigninAccessPoint.SIGNIN_PROMO:
                RecordUserAction.record("Signin_Signin_FromSigninPromo");
                break;
            case SigninAccessPoint.NTP_CONTENT_SUGGESTIONS:
                RecordUserAction.record("Signin_Signin_FromNTPContentSuggestions");
                break;
            default:
                assert false : "Invalid access point.";
        }
    }

    // AccountSigninView.Delegate implementation.
    @Override
    public Activity getActivity() {
        return this;
    }
}
