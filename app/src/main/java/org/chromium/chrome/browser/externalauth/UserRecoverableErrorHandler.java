// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalauth;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;

import com.google.android.gms.common.GoogleApiAvailability;

import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;

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
 * user tries to access the feature. A subclass can (and usually should) be
 * created to provide custom handling of the user response to the dialog by
 * overriding {@link ModalDialog#prepareToHandle(Activity, Context, int)},
 * {@link ModalDialog#getRequestCode()} and
 * {@link ModalDialog#getOnCancelListener()} as appropriate. This will allow the
 * code encountering the error to respond to the user's corrective actions (or
 * lack thereof) immediately.
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
     * The default implementation does nothing.
     * @param context the context in which the error was encountered
     * @param errorCode the error code from Google Play Services
     */
    protected void handle(final Context context, final int errorCode) {
        // Nothing.
    }

    /**
     * A handler that does nothing.
     */
    public static final class Silent extends UserRecoverableErrorHandler {
        @Override
        protected final void handle(final Context context, final int errorCode) {
            RecordHistogram.recordEnumeratedHistogram(ERROR_HANDLER_ACTION_HISTOGRAM_NAME,
                    ERROR_HANDLER_ACTION_SILENT, ERROR_HANDLER_ACTION_HISTOGRAM_BOUNDARY);
        }
    }

    /**
     * A handler that displays a System Notification. To avoid repeatedly
     * nagging the user, this is done at most one time per application
     * lifecycle. The system notification is shown by calling
     * {@link ExternalAuthUtils#showErrorNotification(int, Context)}.
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
                RecordHistogram.recordEnumeratedHistogram(ERROR_HANDLER_ACTION_HISTOGRAM_NAME,
                        ERROR_HANDLER_ACTION_IGNORED_AS_REDUNDANT,
                        ERROR_HANDLER_ACTION_HISTOGRAM_BOUNDARY);
                return;
            }
            ExternalAuthUtils.getInstance().showErrorNotification(errorCode, context);
            RecordHistogram.recordEnumeratedHistogram(ERROR_HANDLER_ACTION_HISTOGRAM_NAME,
                    ERROR_HANDLER_ACTION_SYSTEM_NOTIFICATION,
                    ERROR_HANDLER_ACTION_HISTOGRAM_BOUNDARY);
        }
    }

    /**
     * A handler that displays a modal dialog. Unlike
     * {@link SystemNotification}, this handler will take action every time it
     * is invoked. Subclasses should override the methods
     * {@link ModalDialog#prepareToHandle(Activity, Context, int)},
     * {@link ModalDialog#getRequestCode()} and
     * {@link ModalDialog#getOnCancelListener()} to provide custom handling of
     * the user response.
     * @see GoogleApiAvailability#getErrorDialog(Activity, int, int,
     * android.content.DialogInterface.OnCancelListener)
     */
    public static class ModalDialog extends UserRecoverableErrorHandler {
        /**
         * Value to be returned from {@link #getRequestCode()} to indicate that
         * no response information is needed from the dialog.
         */
        public static final int NO_RESPONSE_REQUIRED = -1;

        /**
         * The activity from which to start the dialog and any subsequent
         * actions, and the activity which will receive the response from those
         * actions.
         */
        private final Activity mActivity;

        /**
         * Create a new Modal Dialog handler for the specified activity and
         * error code. The specified activity may be used to launch the dialog
         * via
         * {@link Activity#startActivityForResult(android.content.Intent, int)}
         * and also to receive the result via Activity's protected
         * onActivityResult method.
         * @param activity the activity to use
         */
        public ModalDialog(Activity activity) {
            mActivity = activity;
        }

        /**
         * Returns the activity that was passed to the constructor.
         * @return the activity
         */
        protected final Activity getActivity() {
            return mActivity;
        }

        /**
         * Convenience method for subclasses that is guaranteed to be called
         * immediately prior to {@link #handle(Context, int)} on the UI thread.
         * The default implementation does nothing. Subclasses can override
         * this method to prepare a request code for {@link #getRequestCode()}
         * and an {@link OnCancelListener} for {@link #getOnCancelListener()}
         * on-demand (i.e., when it is known that an error has occurred and the
         * error code is available).
         * @param activity the activity that was passed to the constructor
         * @param context the context in which the error was encountered
         * @param errorCode the error code from Google Play Services
         */
        protected void prepareToHandle(
                final Activity activity, final Context context, final int errorCode) {
            // Nothing.
        }

        /**
         * Returns an integer request code to pass to
         * {@link Activity#startActivityForResult(android.content.Intent, int)}.
         * If this method returns a positive value, then the dialog will be
         * launched by calling
         * {@link Activity#startActivityForResult(android.content.Intent, int)}
         * on the Activity that was passed to the constructor and upon
         * completion the Activity's protected onActivityResult method will
         * receive the results. The default implementation returns
         * {@link #NO_RESPONSE_REQUIRED} (a negative value), indicating that
         * the dialog can be launched independent of the Activity passed to the
         * constructor and that no response needs to be processed by the
         * Activity. This method is guaranteed to be called only after a call to
         * {@link #prepareToHandle(Activity, Context, int)}.
         * @return the request code
         */
        protected int getRequestCode() {
            return NO_RESPONSE_REQUIRED;
        }

        /**
         * Optionally, returns a {@link OnCancelListener} that should be invoked
         * if the dialog is canceled or null if the activity doesn't care about
         * this event. The default implementation returns null.
         * This method is guaranteed to be called only after a call to
         * {@link #prepareToHandle(Activity, Context, int)}.
         * @return the listener, or null
         */
        protected OnCancelListener getOnCancelListener() {
            return null;
        }

        /**
         * Invokes {@link #prepareToHandle(Activity, Context, int)}, gathers
         * the request code and cancel listener from {@link #getRequestCode()}
         * and {@link #getOnCancelListener()} respectively, and displays the
         * dialog in a modal manner using
         * {@link ExternalAuthUtils#showErrorDialog(int, Activity, int, OnCancelListener)}.
         * @param context the context in which the error was encountered
         * @param errorCode the error code from Google Play Services
         */
        @Override
        protected final void handle(final Context context, final int errorCode) {
            prepareToHandle(getActivity(), context, errorCode);
            ExternalAuthUtils.getInstance().showErrorDialog(
                    errorCode, getActivity(), getRequestCode(), getOnCancelListener());
            RecordHistogram.recordEnumeratedHistogram(ERROR_HANDLER_ACTION_HISTOGRAM_NAME,
                    ERROR_HANDLER_ACTION_MODAL_DIALOG, ERROR_HANDLER_ACTION_HISTOGRAM_BOUNDARY);
        }
    }
}
