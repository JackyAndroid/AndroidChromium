// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalauth;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.text.TextUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeApplication;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for external authentication tools.
 *
 * This class is safe to use on any thread.
 */
public class ExternalAuthUtils {
    public static final int FLAG_SHOULD_BE_GOOGLE_SIGNED = 1 << 0;
    public static final int FLAG_SHOULD_BE_SYSTEM = 1 << 1;
    private static final String TAG = "cr.ExternalAuthUtils";
    private static final String CONNECTION_RESULT_HISTOGRAM_NAME =
            "GooglePlayServices.ConnectionResult";

    // Use an AtomicReference since getInstance() can be called from multiple threads.
    private static AtomicReference<ExternalAuthUtils> sInstance =
            new AtomicReference<ExternalAuthUtils>();

    /**
     * Returns the singleton instance of ExternalAuthUtils, creating it if needed.
     */
    public static ExternalAuthUtils getInstance() {
        if (sInstance.get() == null) {
            ChromeApplication application =
                    (ChromeApplication) ApplicationStatus.getApplicationContext();
            sInstance.compareAndSet(null, application.createExternalAuthUtils());
        }
        return sInstance.get();
    }

    /**
     * Gets the calling package names for the current transaction.
     * @param context The context to use for accessing the package manager.
     * @return The calling package names.
     */
    private static String[] getCallingPackages(Context context) {
        int callingUid = Binder.getCallingUid();
        PackageManager pm = context.getApplicationContext().getPackageManager();
        return pm.getPackagesForUid(callingUid);
    }

    /**
     * Returns whether the caller application is a part of the system build.
     * @param pm Package manager to use for getting package related info.
     * @param packageName The package name to inquire about.
     */
    @VisibleForTesting
    public boolean isSystemBuild(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, ApplicationInfo.FLAG_SYSTEM);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) throw new SecurityException();
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Package with name " + packageName + " not found");
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "Caller with package name " + packageName + " is not in the system build");
            return false;
        }

        return true;
    }

    /**
     * Returns whether the current build of Chrome is a Google-signed package.
     *
     * @param context the current context.
     * @return whether the currently running application is signed with Google keys.
     */
    public boolean isChromeGoogleSigned(Context context) {
        return isGoogleSigned(
                context.getApplicationContext().getPackageManager(), context.getPackageName());
    }

    /**
     * Returns whether the call is originating from a Google-signed package.
     * @param pm Package manager to use for getting package related info.
     * @param packageName The package name to inquire about.
     */
    public boolean isGoogleSigned(PackageManager pm, String packageName) {
        // This is overridden in a subclass.
        return false;
    }

    /**
     * Returns whether the callers of the current transaction contains a package that matches
     * the give authentication requirements.
     * @param context The context to use for getting package information.
     * @param authRequirements The requirements to be exercised on the caller.
     * @param packageToMatch The package name to compare with the caller.
     * @return Whether the caller meets the authentication requirements.
     */
    private boolean isCallerValid(Context context, int authRequirements, String packageToMatch) {
        boolean shouldBeGoogleSigned = (authRequirements & FLAG_SHOULD_BE_GOOGLE_SIGNED) != 0;
        boolean shouldBeSystem = (authRequirements & FLAG_SHOULD_BE_SYSTEM) != 0;

        String[] callingPackages = getCallingPackages(context);
        PackageManager pm = context.getApplicationContext().getPackageManager();
        boolean matchFound = false;

        for (String packageName : callingPackages) {
            if (!TextUtils.isEmpty(packageToMatch) && !packageName.equals(packageToMatch)) continue;
            matchFound = true;
            if ((shouldBeGoogleSigned && !isGoogleSigned(pm, packageName))
                    || (shouldBeSystem && !isSystemBuild(pm, packageName))) {
                return false;
            }
        }
        return matchFound;
    }

    /**
     * Returns whether the callers of the current transaction contains a package that matches
     * the give authentication requirements.
     * @param context The context to use for getting package information.
     * @param authRequirements The requirements to be exercised on the caller.
     * @param packageToMatch The package name to compare with the caller. Should be non-empty.
     * @return Whether the caller meets the authentication requirements.
     */
    public boolean isCallerValidForPackage(
            Context context, int authRequirements, String packageToMatch) {
        assert !TextUtils.isEmpty(packageToMatch);

        return isCallerValid(context, authRequirements, packageToMatch);
    }

    /**
     * Returns whether the callers of the current transaction matches the given authentication
     * requirements.
     * @param context The context to use for getting package information.
     * @param authRequirements The requirements to be exercised on the caller.
     * @return Whether the caller meets the authentication requirements.
     */
    public boolean isCallerValid(Context context, int authRequirements) {
        return isCallerValid(context, authRequirements, "");
    }

    /**
     * Checks whether Google Play Services can be used, applying the specified error-handling
     * policy if a user-recoverable error occurs. This method is threadsafe. If the specified
     * error-handling policy requires UI interaction, it will be run on the UI thread.
     * Subclasses should generally not override this method; instead, they should override the
     * helper methods {@link #checkGooglePlayServicesAvailable(Context)},
     * {@link #isSuccess(int)}, {@link #describeError(int)}, and
     * {@link #isUserRecoverableError(int)} instead, which are called in that order (as
     * necessary) by this method.
     * @param context The current context.
     * @param errorHandler How to handle user-recoverable errors; must be non-null.
     * @return true if and only if Google Play Services can be used
     */
    public boolean canUseGooglePlayServices(
            final Context context, final UserRecoverableErrorHandler errorHandler) {
        final int resultCode = checkGooglePlayServicesAvailable(context);
        recordConnectionResult(resultCode);
        if (isSuccess(resultCode)) {
            return true; // Hooray!
        }
        // resultCode is some kind of error.
        Log.v(TAG, "Unable to use Google Play Services: %s", describeError(resultCode));
        if (isUserRecoverableError(resultCode)) {
            Runnable errorHandlerTask = new Runnable() {
                @Override
                public void run() {
                    errorHandler.handleError(context, resultCode);
                }
            };
            ThreadUtils.runOnUiThread(errorHandlerTask);
        }
        return false;
    }

    /**
     * Same as {@link #canUseGooglePlayServices(Context, UserRecoverableErrorHandler)}
     * but also with the constraint that first-party APIs must be available. This check is
     * implemented by verifying that the package is Google-signed; if not, first-party APIs will
     * be unavailable at runtime.
     * Nuance: The check on whether or not the package is Google-signed itself requires access to
     * Google Play Services, so this method first checks for "normal" (non-first-party) access and,
     * if successful, makes a second call to Google Play Services to determine the state of the
     * package signature. The failure handling policy only applies to the first check, since Google
     * Play Services provides "canned" ways to deal with failures; there is no special handling of
     * the case where the Google Play Services check succeeds and the Google-signed package check
     * fails (the method will simply return false).
     * @param context The current context.
     * @param userRecoverableErrorHandler How to handle user-recoverable errors from Google
     * Play Services; must be non-null.
     * @return true if and only if first-party Google Play Services can be used
     */
    public boolean canUseFirstPartyGooglePlayServices(
            Context context, UserRecoverableErrorHandler userRecoverableErrorHandler) {
        ExternalAuthUtils authUtils = ExternalAuthUtils.getInstance();
        return authUtils.canUseGooglePlayServices(context, userRecoverableErrorHandler)
                && authUtils.isGoogleSigned(context.getPackageManager(), context.getPackageName());
    }

    /**
     * Record the result of a connection attempt. The default implementation records via a UMA
     * histogram.
     * @param resultCode the result from {@link #checkGooglePlayServicesAvailable(Context)}
     */
    protected void recordConnectionResult(final int resultCode) {
        // Doing it this way avoids a hard dependency on RecordHistogram, which allows the code to
        // be tested with simple mocks and junit instead of having an instrumentation test.
        RecordHistogram.recordSparseSlowlyHistogram(
                CONNECTION_RESULT_HISTOGRAM_NAME, resultCode);
    }

    /**
     * Invokes whatever external code is necessary to check if Google Play Services is available
     * and returns the code produced by the attempt. Subclasses can override to force the behavior
     * one way or another, or to change the way that the check is performed.
     * @param context The current context.
     * @return The code produced by calling the external code
     */
    protected int checkGooglePlayServicesAvailable(final Context context) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    }

    /**
     * Invokes whatever external code is necessary to check if the specified result code from
     * {@link #checkGooglePlayServicesAvailable(Context)} represents success.
     * @param resultCode The code to check
     * @return true If the result code represents success.
     */
    protected boolean isSuccess(final int resultCode) {
        return resultCode == ConnectionResult.SUCCESS;
    }

    /**
     * Invokes whatever external code is necessary to check if the specified error code produced
     * by {@link #checkGooglePlayServicesAvailable(Context)} represents a user-recoverable error.
     * Subclasses can override to filter error codes as desired.
     * @param errorCode The code to check
     * @return true If the code represents a user-recoverable error
     */
    protected boolean isUserRecoverableError(final int errorCode) {
        return GoogleApiAvailability.getInstance().isUserResolvableError(errorCode);
    }

    /**
     * Invokes whatever external code is necessary to obtain a textual description of an error
     * code produced by {@link #checkGooglePlayServicesAvailable(Context)}.
     * @param errorCode The code to check
     * @return a textual description of the error code
     */
    protected String describeError(final int errorCode) {
        return GoogleApiAvailability.getInstance().getErrorString(errorCode);
    }

    /**
     * Invokes an external mechanism to display an error notification for the specified error code
     * and context. Must be called on the UI thread.
     * @param errorCode The error code
     * @param context The current context
     */
    public void showErrorNotification(final int errorCode, final Context context) {
        GoogleApiAvailability.getInstance().showErrorNotification(context, errorCode);
    }

    /**
     * Invokes an external mechanism to display a modal error dialog for the specified error code,
     * using the specified activity to receive the response (if desired) and/or notifying the
     * specified listener of cancellation.
     * the parameters
     * @param errorCode The error code
     * @param activity see {@link UserRecoverableErrorHandler.ModalDialog#ModalDialog(Activity)}
     * @param requestCode see {@link UserRecoverableErrorHandler.ModalDialog#getRequestCode()}
     * @param onCancelListener see
     *        {@link UserRecoverableErrorHandler.ModalDialog#getOnCancelListener()}
     */
    public void showErrorDialog(final int errorCode, final Activity activity, final int requestCode,
            final OnCancelListener onCancelListener) {
        final Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(
                activity, errorCode, requestCode, onCancelListener);
        if (dialog != null) { // This can happen if errorCode is ConnectionResult.SERVICE_INVALID
            dialog.show();
        }
    }
}
