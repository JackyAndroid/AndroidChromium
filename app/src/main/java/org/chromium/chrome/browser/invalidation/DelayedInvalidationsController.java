// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.components.invalidation.PendingInvalidation;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.sync.AndroidSyncSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class for controlling whether an invalidation should be notified immediately, or should be
 * delayed until Chrome comes to the foreground again.
 */
public class DelayedInvalidationsController {
    private static final String TAG = "invalidation";
    private static final String DELAYED_ACCOUNT_NAME = "delayed_account";
    private static final String DELAYED_INVALIDATIONS = "delayed_invalidations";

    private static class LazyHolder {
        private static final DelayedInvalidationsController INSTANCE =
                new DelayedInvalidationsController();
    }

    public static DelayedInvalidationsController getInstance() {
        return LazyHolder.INSTANCE;
    }

    @VisibleForTesting
    DelayedInvalidationsController() {}

    /**
     * Notify any invalidations that were delayed while Chromium was backgrounded.
     * @return whether there were any invalidations pending to be notified.
     */
    public boolean notifyPendingInvalidations(final Context context) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        String accountName = prefs.getString(DELAYED_ACCOUNT_NAME, null);
        if (accountName == null) {
            Log.d(TAG, "No pending invalidations.");
            return false;
        } else {
            Log.d(TAG, "Handling pending invalidations.");
            Account account = AccountManagerHelper.createAccountFromName(accountName);
            List<Bundle> bundles = popPendingInvalidations(context);
            notifyInvalidationsOnBackgroundThread(context, account, bundles);
            return true;
        }
    }

    /**
     * Calls ContentResolver.requestSync() in a separate thread as it performs some blocking
     * IO operations.
     */
    @VisibleForTesting
    void notifyInvalidationsOnBackgroundThread(
            final Context context, final Account account, final List<Bundle> bundles) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                String contractAuthority = AndroidSyncSettings.getContractAuthority(context);
                for (Bundle bundle : bundles) {
                    ContentResolver.requestSync(account, contractAuthority, bundle);
                }
                return null;
            }
        }.execute();
    }

    /**
     * Stores preferences to indicate that an invalidation has arrived, but dropped on the floor.
     */
    @VisibleForTesting
    void addPendingInvalidation(Context context, String account, PendingInvalidation invalidation) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        String oldAccount = prefs.getString(DELAYED_ACCOUNT_NAME, null);
        // Make sure to construct a new set so it can be modified safely. See crbug.com/568369.
        Set<String> invals = new HashSet<String>(
                prefs.getStringSet(DELAYED_INVALIDATIONS, new HashSet<String>(1)));
        assert invals.isEmpty() || oldAccount != null;
        if (oldAccount != null && !oldAccount.equals(account)) {
            invals.clear();
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DELAYED_ACCOUNT_NAME, account);
        if (invalidation.mObjectSource == 0 || (oldAccount != null && invals.isEmpty())) {
            editor.putStringSet(DELAYED_INVALIDATIONS, null);
        } else {
            invals.add(invalidation.encodeToString());
            editor.putStringSet(DELAYED_INVALIDATIONS, invals);
        }
        editor.apply();
    }

    private List<Bundle> popPendingInvalidations(final Context context) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        assert prefs.contains(DELAYED_ACCOUNT_NAME);
        Set<String> savedInvalidations = prefs.getStringSet(DELAYED_INVALIDATIONS, null);
        clearPendingInvalidations(context);
        // Absence of specific invalidations indicates invalidate all types.
        if (savedInvalidations == null) return Arrays.asList(new Bundle());

        List<Bundle> bundles = new ArrayList<Bundle>(savedInvalidations.size());
        for (String invalidation : savedInvalidations) {
            Bundle bundle = PendingInvalidation.decodeToBundle(invalidation);
            if (bundle == null) {
                Log.e(TAG, "Error parsing saved invalidation. Invalidating all.");
                return Arrays.asList(new Bundle());
            }
            bundles.add(bundle);
        }
        return bundles;
    }

    /**
     * If there are any pending invalidations, they will be cleared.
     */
    @VisibleForTesting
    public void clearPendingInvalidations(Context context) {
        SharedPreferences.Editor editor =
                ContextUtils.getAppSharedPreferences().edit();
        editor.putString(DELAYED_ACCOUNT_NAME, null);
        editor.putStringSet(DELAYED_INVALIDATIONS, null);
        editor.apply();
    }

    @VisibleForTesting
    boolean shouldNotifyInvalidation(Bundle extras) {
        return isManualRequest(extras) || ApplicationStatus.hasVisibleActivities();
    }

    private static boolean isManualRequest(Bundle extras) {
        if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false)) {
            Log.d(TAG, "Manual sync requested.");
            return true;
        }
        return false;
    }
}
