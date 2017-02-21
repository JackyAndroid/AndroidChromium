// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalauth;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

import com.google.android.gms.common.GoogleApiAvailability;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.metrics.LaunchMetrics.EnumeratedHistogramSample;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles situations where Google Play Services encounters a user-recoverable
 * error. This is typically because Google Play Services requires an upgrade.
 * Three "canned" handlers are provided, with suggested use cases as described
 * below.
 * <br>
 * <strong>Silent</strong>: use this handler if the dependency is purely for
 * convenience and a (potentially suboptimal) fallback is available.
 * <br>
 * <strong>SystemNotification</strong>: use this handler if the dependency is
 * for a feature that the user isn't actively trying to access interactively.
 * To avoid excessively nagging the user, only one notification will ever be
 * shown during the lifetime of the process.
 * <br>
 * <strong>ModalDialog</strong>: use this handler if the dependency is
 * for a feature that the user is actively trying to access interactively where
 * the feature cannot function (or would be severely impaired) unless the
 * dependency is satisfied. The dialog will be presented as many times as the
 * user tries to access the feature.
 * <br>
 * If none of these behaviors is suitable, a new behavior can be defined by
 * subclassing this class.
 */
public abstract class UserRecoverableErrorHandler {
    private static final String ERROR_HANDLER_ACTION_HISTOGRAM_NAME =
            "GooglePlayServices.ErrorHandlerAction";
    // Never remove or reorder histogram values. It is safe to append new values to the end.
    private static final int ERROR_HANDLER_ACTION_SILENT = 0;
    private static final int ERROR_HANDLER_ACTION_SYSTEM_NOTIFICATION = 1;
    private static final int ERROR_HANDLER_ACTION_MODAL_DIALOG = 2;
    private static final int ERROR_HANDLER_ACTION_IGNORED_AS_REDUNDANT = 3;
    private static final int ERROR_HANDLER_ACTION_HISTOGRAM_BOUNDARY = 4;

    private static final EnumeratedHistogramSample sErrorHandlerActionHistogramSample =
            new EnumeratedHistogramSample(ERROR_HANDLER_ACTION_HISTOGRAM_NAME,
                    ERROR_HANDLER_ACTION_HISTOGRAM_BOUNDARY);

    /**
     * Handles the specified error code from Google Play Services.
     * This method must only be called on the UI thread.
     * This method asserts that it is being called on the UI thread, then calls
     * {@link #handle(Context, int)}.
     * @param context the context in which the error was encountered
     * @param errorCode the error code from Google Play Services
     */
    public final void handleError(final Context context, final int errorCode) {
        ThreadUtils.assertOnUiThread();
        handle(context, errorCode);
    }

    /**
     * This method is invoked by {@link #handleError(Context, int)} to do the
     * work appropriate for the subclass on the UI thread.
     * @param context the context in which the error was encountered
     * @param errorCode the error code from Google Play Services
     */
    protected abstract void handle(final Context context, final int errorCode);

    /**
     * A handler that does nothing.
     */
    public static final class Silent extends UserRecoverableErrorHandler {
        @Override
        protected final void handle(final Context context, final int errorCode) {
            sErrorHandlerActionHistogramSample.record(ERROR_HANDLER_ACTION_SILENT);
        }
    }

    /**
     * A handler that displays a System Notification. To avoid repeatedly
     * nagging the user, this is done at most one time per application
     * lifecycle. The system notification is shown by calling
     * {@link GoogleApiAvailability#showErrorNotification(int, Context)}.
     * @see GoogleApiAvailability#showErrorNotification(Context, int)
     */
    public static final class SystemNotification extends UserRecoverableErrorHandler {
        /**
         * Tracks whether the notification has yet been shown, used to ensure
         * that the notification is shown at most one time per application
         * lifecycle.
         */
        private static final AtomicBoolean sNotificationShown = new AtomicBoolean(false);

        @Override
        protected void handle(final Context context, final int errorCode) {
            if (!sNotificationShown.getAndSet(true)) {
                sErrorHandlerActionHistogramSample
                        .record(ERROR_HANDLER_ACTION_IGNORED_AS_REDUNDANT);
                return;
            }
            GoogleApiAvailability.getInstance().showErrorNotification(context, errorCode);
            sErrorHandlerActionHistogramSample.record(ERROR_HANDLER_ACTION_SYSTEM_NOTIFICATION);
        }
    }

    /**
     * A handler that displays a modal dialog. Unlike
     * {@link SystemNotification}, this handler will take action every time it
     * is invoked.
     * @see GoogleApiAvailability#getErrorDialog(Activity, int, int,
     * android.content.DialogInterface.OnCancelListener)
     */
    public static class ModalDialog extends UserRecoverableErrorHandler {
        private static final int NO_RESPONSE_REQUIRED = -1;

        /**
         * The activity from which to start the dialog and any subsequent
         * actions, and the activity which will receive the response from those
         * actions.
         */
        private final Activity mActivity;

        /**
         * Create a new Modal Dialog handler for the specified activity and error code. The
         * specified activity may be used to launch the dialog via
         * {@link Activity#startActivityForResult(android.content.Intent, int)} and also to receive
         * the result via Activity's protected onActivityResult method.
         *
         * @param activity the activity to use
         */
        public ModalDialog(Activity activity) {
            mActivity = activity;
        }

        /**
         * Displays the dialog in a modal manner using
         * {@link GoogleApiAvailability#showErrorDialog(int, Activity, int)}.
         * @param context the context in which the error was encountered
         * @param errorCode the error code from Google Play Services
         */
        @Override
        protected final void handle(final Context context, final int errorCode) {
            final Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(
                    mActivity, errorCode, NO_RESPONSE_REQUIRED);
            if (dialog != null) {
                // This can happen if |errorCode| is ConnectionResult.SERVICE_INVALID.
                dialog.show();
            }
            sErrorHandlerActionHistogramSample.record(ERROR_HANDLER_ACTION_MODAL_DIALOG);
        }
    }
}
