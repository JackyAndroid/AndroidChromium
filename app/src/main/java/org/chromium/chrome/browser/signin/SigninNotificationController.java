// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.notifications.GoogleServicesNotificationController;
import org.chromium.chrome.browser.notifications.NotificationConstants;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;

/**
 * {@link SigninNotificationController} provides functionality for displaying Android notifications
 * regarding the user sign-in status.
 */
public class SigninNotificationController {
    private final Context mApplicationContext;
    private final GoogleServicesNotificationController mNotificationController;
    private final Class<? extends Fragment> mAccountManagementFragment;

    public SigninNotificationController(Context context,
            GoogleServicesNotificationController controller,
            Class<? extends Fragment> accountManagementFragment) {
        mApplicationContext = context.getApplicationContext();
        mNotificationController = controller;
        mAccountManagementFragment = accountManagementFragment;
    }

    /**
     * Alerts the user through the notification bar that they have been signed in to Chrome.
     * Clicking on the notification should immediately go to the sync account page.
     */
    public void showSyncSignInNotification() {
        // Create an Intent to go to the sync page.
        Intent prefIntent = PreferencesLauncher.createIntentForSettingsPage(
                mApplicationContext, mAccountManagementFragment.getCanonicalName());

        // Create the notification.
        String title =
                mApplicationContext.getResources().getString(R.string.firstrun_signed_in_title);
        String syncPromo = title + " "
                + mApplicationContext.getResources().getString(
                          R.string.firstrun_signed_in_description);
        mNotificationController.showNotification(
                NotificationConstants.NOTIFICATION_ID_SIGNED_IN, title, syncPromo, prefIntent);
    }

    /**
     * Called when the user signs outs.
     */
    public void onClearSignedInUser() {
        mNotificationController.cancelNotification(NotificationConstants.NOTIFICATION_ID_SIGNED_IN);
    }
}
