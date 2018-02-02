// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.blimp.ui;

import android.app.Activity;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.ui.widget.Toast;

/**
 * BlimpMessageBuilder is a helper class to show message in a {@link Snackbar} or Toast.
 */
public class BlimpMessageBuilder implements SnackbarController {
    public enum MessageLength {
        SHORT,
        LONG,
    }

    private static final int BLIMP_SNACKBAR_DURATION_LONG_MS = 6000;
    private static final int BLIMP_SNACKBAR_DURATION_SHORT_MS = 3000;

    private Snackbar mSnackbar;
    private MessageLength mLength = MessageLength.LONG;

    /**
     * Show a message in a {@link Snackbar} or {@link Toast}.
     * If the current foreground activity doesn't have a {@link SnackbarManager}, fall back to
     * show a Toast.
     * @param message Message to show.
     */
    public void showMessage(String message) {
        mSnackbar = null;
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        SnackbarManager manager = getSnackbarManager(activity);

        // Fall back to show Toast if no SnackbarManager is found in the foreground activity.
        if (manager == null) {
            int length = (mLength == MessageLength.SHORT ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
            Toast.makeText(activity, message, length).show();
            return;
        }

        // Show a Snackbar message.
        int length = (mLength == MessageLength.SHORT ? BLIMP_SNACKBAR_DURATION_SHORT_MS
                                                     : BLIMP_SNACKBAR_DURATION_LONG_MS);
        mSnackbar = Snackbar.make(message, this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_BLIMP)
                            .setSingleLine(false)
                            .setDuration(length);
        manager.showSnackbar(mSnackbar);
    }

    /**
     * Set the message duration.
     * @param length Message length defined in {@link MessageLength}.
     * @return The message builder.
     */
    public BlimpMessageBuilder setDuration(MessageLength length) {
        mLength = length;
        return this;
    }

    @Override
    public void onAction(Object actionData) {}

    @Override
    public void onDismissNoAction(Object actionData) {}

    /**
     * Get {@link SnackbarManager} of current focused activity.
     * @return The SnackbarManager or null if the activity doesn't hold a SnackbarManager.
     */
    @VisibleForTesting
    protected SnackbarManager getSnackbarManager(Activity activity) {
        if (activity != null && activity instanceof SnackbarManager.SnackbarManageable) {
            return ((SnackbarManager.SnackbarManageable) activity).getSnackbarManager();
        }
        return null;
    }

    @VisibleForTesting
    protected Snackbar getSnackbarForTest() {
        return mSnackbar;
    }
}
