// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.preferences;

import android.accounts.Account;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.BuildInfo;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.childaccounts.ChildAccountService;
import org.chromium.chrome.browser.sync.GoogleServiceAuthError;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;
import org.chromium.components.sync.ProtocolErrorClientAction;

/**
 * A preference that displays the current sync account and status (enabled, error, needs passphrase,
 * etc).
 */
public class SyncPreference extends Preference {
    public SyncPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateSyncSummaryAndIcon();
    }

    /**
     * Updates the summary and icon for this preference to reflect the current state of syncing.
     */
    public void updateSyncSummaryAndIcon() {
        setSummary(getSyncStatusSummary(getContext()));

        if (SyncPreference.showSyncErrorIcon(getContext())) {
            setIcon(ApiCompatibilityUtils.getDrawable(
                    getContext().getResources(), R.drawable.sync_error));
        } else {
            // Sets preference icon and tints it to blue.
            Drawable icon = ApiCompatibilityUtils.getDrawable(
                    getContext().getResources(), R.drawable.permission_background_sync);
            icon.setColorFilter(ApiCompatibilityUtils.getColor(
                                        getContext().getResources(), R.color.light_active_color),
                    PorterDuff.Mode.SRC_IN);
            setIcon(icon);
        }
    }

    /**
     * Checks if sync error icon should be shown. Show sync error icon if sync is off because
     * of error, passphrase required or disabled in Android.
     */
    static boolean showSyncErrorIcon(Context context) {
        if (!AndroidSyncSettings.isMasterSyncEnabled(context)) {
            return true;
        }

        ProfileSyncService profileSyncService = ProfileSyncService.get();
        if (profileSyncService != null) {
            if (profileSyncService.hasUnrecoverableError()) {
                return true;
            }

            if (profileSyncService.getAuthError() != GoogleServiceAuthError.State.NONE) {
                return true;
            }

            if (profileSyncService.isSyncActive()
                    && profileSyncService.isPassphraseRequiredForDecryption()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return a short summary of the current sync status.
     */
    static String getSyncStatusSummary(Context context) {
        if (!ChromeSigninController.get(context).isSignedIn()) return "";

        ProfileSyncService profileSyncService = ProfileSyncService.get();
        Resources res = context.getResources();

        if (ChildAccountService.isChildAccount()) {
            return res.getString(R.string.kids_account);
        }

        if (!AndroidSyncSettings.isMasterSyncEnabled(context)) {
            return res.getString(R.string.sync_android_master_sync_disabled);
        }

        if (profileSyncService == null) {
            return res.getString(R.string.sync_is_disabled);
        }

        if (profileSyncService.getAuthError() != GoogleServiceAuthError.State.NONE) {
            return res.getString(profileSyncService.getAuthError().getMessage());
        }

        if (profileSyncService.getProtocolErrorClientAction()
                == ProtocolErrorClientAction.UPGRADE_CLIENT) {
            return res.getString(
                    R.string.sync_error_upgrade_client, BuildInfo.getPackageLabel(context));
        }

        if (profileSyncService.hasUnrecoverableError()) {
            return res.getString(R.string.sync_error_generic);
        }

        boolean syncEnabled = AndroidSyncSettings.isSyncEnabled(context);

        if (syncEnabled) {
            if (!profileSyncService.isSyncActive()) {
                return res.getString(R.string.sync_setup_progress);
            }

            if (profileSyncService.isPassphraseRequiredForDecryption()) {
                return res.getString(R.string.sync_need_passphrase);
            }

            Account account = ChromeSigninController.get(context).getSignedInUser();
            return String.format(
                    context.getString(R.string.account_management_sync_summary), account.name);
        }

        return context.getString(R.string.sync_is_disabled);
    }
}
