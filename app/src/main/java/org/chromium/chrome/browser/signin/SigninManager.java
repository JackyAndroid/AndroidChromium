// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.ObserverList;
import org.chromium.base.Promise;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.chrome.browser.sync.SyncUserDataWiper;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;

import javax.annotation.Nullable;

/**
 * Android wrapper of the SigninManager which provides access from the Java layer.
 * <p/>
 * This class handles common paths during the sign-in and sign-out flows.
 * <p/>
 * Only usable from the UI thread as the native SigninManager requires its access to be in the
 * UI thread.
 * <p/>
 * See chrome/browser/signin/signin_manager_android.h for more details.
 */
public class SigninManager implements AccountTrackerService.OnSystemAccountsSeededListener {
    private static final String TAG = "SigninManager";

    private static SigninManager sSigninManager;
    private static int sSignInAccessPoint = SigninAccessPoint.UNKNOWN;

    private final Context mContext;
    private final long mNativeSigninManagerAndroid;

    /** Tracks whether the First Run check has been completed.
     *
     * A new sign-in can not be started while this is pending, to prevent the
     * pending check from eventually starting a 2nd sign-in.
     */
    private boolean mFirstRunCheckIsPending = true;

    private final ObserverList<SignInStateObserver> mSignInStateObservers =
            new ObserverList<SignInStateObserver>();

    private final ObserverList<SignInAllowedObserver> mSignInAllowedObservers =
            new ObserverList<SignInAllowedObserver>();

    /**
    * Will be set during the sign in process, and nulled out when there is not a pending sign in.
    * Needs to be null checked after ever async entry point because it can be nulled out at any time
    * by system accounts changing.
    */
    private SignInState mSignInState;

    private Runnable mSignOutCallback;

    private boolean mSigninAllowedByPolicy;

    private boolean mSignOutInProgress;

    /**
     * A SignInStateObserver is notified when the user signs in to or out of Chrome.
     */
    public interface SignInStateObserver {
        /**
         * Invoked when the user has signed in to Chrome.
         */
        void onSignedIn();

        /**
         * Invoked when the user has signed out of Chrome.
         */
        void onSignedOut();
    }

    /**
     * SignInAllowedObservers will be notified once signing-in becomes allowed or disallowed.
     */
    public interface SignInAllowedObserver {
        /**
         * Invoked once all startup checks are done and signing-in becomes allowed, or disallowed.
         */
        void onSignInAllowedChanged();
    }

    /**
     * Callbacks for the sign-in flow.
     */
    public interface SignInCallback {
        /**
         * Invoked after sign-in is completed successfully.
         */
        void onSignInComplete();

        /**
         * Invoked if the sign-in processes does not complete for any reason.
         */
        void onSignInAborted();
    }

    /**
     * Hooks for wiping data during sign out.
     */
    public interface WipeDataHooks {
        /**
         * Called before data is wiped.
         */
        public void preWipeData();

        /**
         * Called after data is wiped.
         */
        public void postWipeData();
    }

    /**
     * Contains all the state needed for signin. This forces signin flow state to be
     * cleared atomically, and all final fields to be set upon initialization.
     */
    private static class SignInState {
        public final Account account;
        public final Activity activity;
        public final SignInCallback callback;

        /**
         * If the system accounts need to be seeded, the sign in flow will block for that to occur.
         * This boolean should be set to true during that time and then reset back to false
         * afterwards. This allows the manager to know if it should progress the flow when the
         * account tracker broadcasts updates.
         */
        public boolean blockedOnAccountSeeding = false;

        /**
         * @param account The account to sign in to.
         * @param activity Reference to the UI to use for dialogs. Null means forced signin.
         * @param callback Called when the sign-in process finishes or is cancelled. Can be null.
         */
        public SignInState(
                Account account, @Nullable Activity activity, @Nullable SignInCallback callback) {
            this.account = account;
            this.activity = activity;
            this.callback = callback;
        }

        /**
         * Returns whether this is an interactive sign-in flow.
         */
        public boolean isInteractive() {
            return activity != null;
        }

        /**
         * Returns whether the sign-in flow activity was set but is no longer visible to the user.
         */
        private boolean isActivityInvisible() {
            return activity != null
                    && (ApplicationStatus.getStateForActivity(activity) == ActivityState.STOPPED
                               || ApplicationStatus.getStateForActivity(activity)
                                       == ActivityState.DESTROYED);
        }
    }

    /**
     * A helper method for retrieving the application-wide SigninManager.
     * <p/>
     * Can only be accessed on the main thread.
     *
     * @param context the ApplicationContext is retrieved from the context used as an argument.
     * @return a singleton instance of the SigninManager.
     */
    public static SigninManager get(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sSigninManager == null) {
            sSigninManager = new SigninManager(context);
        }
        return sSigninManager;
    }

    private SigninManager(Context context) {
        ThreadUtils.assertOnUiThread();
        mContext = context.getApplicationContext();
        mNativeSigninManagerAndroid = nativeInit();
        mSigninAllowedByPolicy = nativeIsSigninAllowedByPolicy(mNativeSigninManagerAndroid);

        AccountTrackerService.get(mContext).addSystemAccountsSeededListener(this);
    }

    /**
    * Log the access point when the user see the view of choosing account to sign in.
    * @param accessPoint the enum value of AccessPoint defined in signin_metrics.h.
    */
    public static void logSigninStartAccessPoint(int accessPoint) {
        RecordHistogram.recordEnumeratedHistogram(
                "Signin.SigninStartedAccessPoint", accessPoint, SigninAccessPoint.MAX);
        sSignInAccessPoint = accessPoint;
    }

    private void logSigninCompleteAccessPoint() {
        RecordHistogram.recordEnumeratedHistogram(
                "Signin.SigninCompletedAccessPoint", sSignInAccessPoint, SigninAccessPoint.MAX);
        sSignInAccessPoint = SigninAccessPoint.UNKNOWN;
    }

    /**
     * Notifies the SigninManager that the First Run check has completed.
     *
     * The user will be allowed to sign-in once this is signaled.
     */
    public void onFirstRunCheckDone() {
        mFirstRunCheckIsPending = false;

        if (isSignInAllowed()) {
            notifySignInAllowedChanged();
        }
    }

    /**
     * Returns true if signin can be started now.
     */
    public boolean isSignInAllowed() {
        return !mFirstRunCheckIsPending && mSignInState == null && mSigninAllowedByPolicy
                && ChromeSigninController.get(mContext).getSignedInUser() == null
                && isSigninSupported();
    }

    /**
     * Returns true if signin is disabled by policy.
     */
    public boolean isSigninDisabledByPolicy() {
        return !mSigninAllowedByPolicy;
    }

    /**
     * @return Whether true if the current user is not demo user and the user has a reasonable
     *         Google Play Services installed.
     */
    public boolean isSigninSupported() {
        return !ApiCompatibilityUtils.isDemoUser(mContext)
                && !ExternalAuthUtils.getInstance().isGooglePlayServicesMissing(mContext);
    }

    /**
     * Registers a SignInStateObserver to be notified when the user signs in or out of Chrome.
     */
    public void addSignInStateObserver(SignInStateObserver observer) {
        mSignInStateObservers.addObserver(observer);
    }

    /**
     * Unregisters a SignInStateObserver to be notified when the user signs in or out of Chrome.
     */
    public void removeSignInStateObserver(SignInStateObserver observer) {
        mSignInStateObservers.removeObserver(observer);
    }

    public void addSignInAllowedObserver(SignInAllowedObserver observer) {
        mSignInAllowedObservers.addObserver(observer);
    }

    public void removeSignInAllowedObserver(SignInAllowedObserver observer) {
        mSignInAllowedObservers.removeObserver(observer);
    }

    private void notifySignInAllowedChanged() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                for (SignInAllowedObserver observer : mSignInAllowedObservers) {
                    observer.onSignInAllowedChanged();
                }
            }
        });
    }

    /**
    * Continue pending sign in after system accounts have been seeded into AccountTrackerService.
    */
    @Override
    public void onSystemAccountsSeedingComplete() {
        if (mSignInState != null && mSignInState.blockedOnAccountSeeding) {
            mSignInState.blockedOnAccountSeeding = false;
            progressSignInFlowCheckPolicy();
        }
    }

    /**
    * Clear pending sign in when system accounts in AccountTrackerService were refreshed.
    */
    @Override
    public void onSystemAccountsChanged() {
        if (mSignInState != null) {
            abortSignIn();
        }
    }

    /**
     * Starts the sign-in flow, and executes the callback when finished.
     *
     * If an activity is provided, it is considered an "interactive" sign-in and the user can be
     * prompted to confirm various aspects of sign-in using dialogs inside the activity.
     * The sign-in flow goes through the following steps:
     *
     *   - Wait for AccountTrackerService to be seeded.
     *   - If interactive, confirm the account change with the user.
     *   - Wait for policy to be checked for the account.
     *   - If interactive and the account is managed, warn the user.
     *   - If managed, wait for the policy to be fetched.
     *   - Complete sign-in with the native SigninManager and kick off token requests.
     *   - Call the callback if provided.
     *
     * @param account The account to sign in to.
     * @param activity The activity used to launch UI prompts, or null for a forced signin.
     * @param callback Optional callback for when the sign-in process is finished.
     */
    public void signIn(
            Account account, @Nullable Activity activity, @Nullable SignInCallback callback) {
        if (account == null) {
            Log.w(TAG, "Ignoring sign-in request due to null account.");
            if (callback != null) callback.onSignInAborted();
            return;
        }

        if (mSignInState != null) {
            Log.w(TAG, "Ignoring sign-in request as another sign-in request is pending.");
            if (callback != null) callback.onSignInAborted();
            return;
        }

        if (mFirstRunCheckIsPending) {
            Log.w(TAG, "Ignoring sign-in request until the First Run check completes.");
            if (callback != null) callback.onSignInAborted();
            return;
        }

        mSignInState = new SignInState(account, activity, callback);
        notifySignInAllowedChanged();

        progressSignInFlowSeedSystemAccounts();
    }

    /**
     * Same as above but retrieves the Account object for the given accountName.
     */
    public void signIn(String accountName, @Nullable final Activity activity,
            @Nullable final SignInCallback callback) {
        AccountManagerHelper.get(mContext).getAccountFromName(accountName, new Callback<Account>() {
            @Override
            public void onResult(Account account) {
                signIn(account, activity, callback);
            }
        });
    }

    private void progressSignInFlowSeedSystemAccounts() {
        if (AccountTrackerService.get(mContext).checkAndSeedSystemAccounts()) {
            progressSignInFlowCheckPolicy();
        } else if (AccountIdProvider.getInstance().canBeUsed(mContext)) {
            mSignInState.blockedOnAccountSeeding = true;
        } else {
            Activity activity = mSignInState.activity;
            UserRecoverableErrorHandler errorHandler = activity != null
                    ? new UserRecoverableErrorHandler.ModalDialog(activity)
                    : new UserRecoverableErrorHandler.SystemNotification();
            ExternalAuthUtils.getInstance().canUseGooglePlayServices(mContext, errorHandler);
            Log.w(TAG, "Cancelling the sign-in process as Google Play services is unavailable");
            abortSignIn();
        }
    }

    /**
     * Continues the signin flow by checking if there is a policy that the account is subject to.
     */
    private void progressSignInFlowCheckPolicy() {
        if (mSignInState == null) {
            Log.w(TAG, "Ignoring sign in progress request as no pending sign in.");
            return;
        }

        if (mSignInState.isActivityInvisible()) {
            abortSignIn();
            return;
        }

        if (!nativeShouldLoadPolicyForUser(mSignInState.account.name)) {
            // Proceed with the sign-in flow without checking for policy if it can be determined
            // that this account can't have management enabled based on the username.
            finishSignIn();
            return;
        }

        Log.d(TAG, "Checking if account has policy management enabled");
        // This will call back to onPolicyCheckedBeforeSignIn.
        nativeCheckPolicyBeforeSignIn(mNativeSigninManagerAndroid, mSignInState.account.name);
    }

    @CalledByNative
    private void onPolicyCheckedBeforeSignIn(String managementDomain) {
        assert mSignInState != null;

        if (managementDomain == null) {
            Log.d(TAG, "Account doesn't have policy");
            finishSignIn();
            return;
        }

        if (mSignInState.isActivityInvisible()) {
            abortSignIn();
            return;
        }

        // The user has already been notified that they are signing into a managed account.
        // This will call back to onPolicyFetchedBeforeSignIn.
        nativeFetchPolicyBeforeSignIn(mNativeSigninManagerAndroid);
    }

    @CalledByNative
    private void onPolicyFetchedBeforeSignIn() {
        // Policy has been fetched for the user and is being enforced; features like sync may now
        // be disabled by policy, and the rest of the sign-in flow can be resumed.
        finishSignIn();
    }

    private void finishSignIn() {
        // This method should be called at most once per sign-in flow.
        assert mSignInState != null;

        // Tell the native side that sign-in has completed.
        nativeOnSignInCompleted(mNativeSigninManagerAndroid, mSignInState.account.name);

        // Cache the signed-in account name. This must be done after the native call, otherwise
        // sync tries to start without being signed in natively and crashes.
        ChromeSigninController.get(mContext).setSignedInAccountName(mSignInState.account.name);
        AndroidSyncSettings.updateAccount(mContext, mSignInState.account);

        if (mSignInState.callback != null) {
            mSignInState.callback.onSignInComplete();
        }

        // Trigger token requests via native.
        logInSignedInUser();

        if (mSignInState.isInteractive()) {
            // If signin was a user action, record that it succeeded.
            RecordUserAction.record("Signin_Signin_Succeed");
            logSigninCompleteAccessPoint();
            // Log signin in reason as defined in signin_metrics.h. Right now only
            // SIGNIN_PRIMARY_ACCOUNT available on Android.
            RecordHistogram.recordEnumeratedHistogram("Signin.SigninReason",
                    SigninReason.SIGNIN_PRIMARY_ACCOUNT, SigninReason.MAX);
        }

        Log.d(TAG, "Signin completed.");
        mSignInState = null;
        notifySignInAllowedChanged();

        for (SignInStateObserver observer : mSignInStateObservers) {
            observer.onSignedIn();
        }
    }

    /**
     * Invokes signOut and returns a {@link Promise} that will be fulfilled on completion.
     * This is equivalent to calling {@link #signOut(Runnable callback)} with a callback that
     * fulfills the returned {@link Promise}.
     */
    public Promise<Void> signOutPromise() {
        final Promise<Void> promise = new Promise<Void>();

        signOut(new Runnable(){
            @Override
            public void run() {
                promise.fulfill(null);
            }
        });

        return promise;
    }

    /**
     * Invokes signOut with no callback or wipeDataHooks.
     */
    public void signOut() {
        signOut(null, null);
    }

    /**
     * Invokes signOut() with no wipeDataHooks.
     */
    public void signOut(Runnable callback) {
        signOut(callback, null);
    }

    /**
     * Signs out of Chrome.
     * <p/>
     * This method clears the signed-in username, stops sync and sends out a
     * sign-out notification on the native side.
     *
     * @param callback Will be invoked after signout completes, if not null.
     * @param wipeDataHooks Hooks to call during data wiping in case the account is managed.
     */
    public void signOut(Runnable callback, WipeDataHooks wipeDataHooks) {
        mSignOutInProgress = true;
        mSignOutCallback = callback;

        boolean wipeData = getManagementDomain() != null;
        Log.d(TAG, "Signing out, wipe data? " + wipeData);

        // Native signout must happen before resetting the account so data is deleted correctly.
        // http://crbug.com/589028
        nativeSignOut(mNativeSigninManagerAndroid);
        ChromeSigninController.get(mContext).setSignedInAccountName(null);
        AndroidSyncSettings.updateAccount(mContext, null);

        if (wipeData) {
            wipeProfileData(wipeDataHooks);
        } else {
            onSignOutDone();
        }

        AccountTrackerService.get(mContext).invalidateAccountSeedStatus(true);
    }

    /**
     * Returns the management domain if the signed in account is managed, otherwise returns null.
     */
    public String getManagementDomain() {
        return nativeGetManagementDomain(mNativeSigninManagerAndroid);
    }

    public void logInSignedInUser() {
        nativeLogInSignedInUser(mNativeSigninManagerAndroid);
    }

    public void clearLastSignedInUser() {
        nativeClearLastSignedInUser(mNativeSigninManagerAndroid);
    }

    /**
     * Aborts the current sign in.
     *
     * Package protected to allow dialog fragments to abort the signin flow.
     */
    void abortSignIn() {
        // Ensure this function can only run once per signin flow.
        SignInState signInState = mSignInState;
        assert signInState != null;
        mSignInState = null;

        if (signInState.callback != null) {
            signInState.callback.onSignInAborted();
        }

        nativeAbortSignIn(mNativeSigninManagerAndroid);

        Log.d(TAG, "Signin flow aborted.");
        notifySignInAllowedChanged();
    }

    private void wipeProfileData(WipeDataHooks hooks) {
        if (hooks != null) hooks.preWipeData();
        // This will call back to onProfileDataWiped().
        nativeWipeProfileData(mNativeSigninManagerAndroid, hooks);
    }

    /**
     * Convenience method to return a Promise to be fulfilled when the user's sync data has been
     * wiped if the parameter is true, or an already fulfilled Promise if the parameter is false.
     */
    public static Promise<Void> wipeSyncUserDataIfRequired(boolean required) {
        if (required) {
            return SyncUserDataWiper.wipeSyncUserData();
        } else {
            return Promise.fulfilled(null);
        }
    }

    @CalledByNative
    private void onProfileDataWiped(WipeDataHooks hooks) {
        if (hooks != null) hooks.postWipeData();
        onSignOutDone();
    }

    @CalledByNative
    private void onNativeSignOut() {
        if (!mSignOutInProgress) {
            signOut();
        }
    }

    private void onSignOutDone() {
        mSignOutInProgress = false;
        if (mSignOutCallback != null) {
            new Handler().post(mSignOutCallback);
            mSignOutCallback = null;
        }

        for (SignInStateObserver observer : mSignInStateObservers) {
            observer.onSignedOut();
        }
    }

    /**
     * @return Whether there is a signed in account on the native side.
     */
    public boolean isSignedInOnNative() {
        return nativeIsSignedInOnNative(mNativeSigninManagerAndroid);
    }

    @CalledByNative
    private void onSigninAllowedByPolicyChanged(boolean newSigninAllowedByPolicy) {
        mSigninAllowedByPolicy = newSigninAllowedByPolicy;
        notifySignInAllowedChanged();
    }

    /**
     * Performs an asynchronous check to see if the user is a managed user.
     * @param callback A callback to be called with true if the user is a managed user and false
     *         otherwise.
     */
    public static void isUserManaged(String email, final Callback<Boolean> callback) {
        if (nativeShouldLoadPolicyForUser(email)) {
            nativeIsUserManaged(email, callback);
        } else {
            // Although we know the result immediately, the caller may not be able to handle the
            // callback being executed during this method call. So we post the callback on the
            // looper.
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(false);
                }
            });
        }
    }

    public static String extractDomainName(String email) {
        return nativeExtractDomainName(email);
    }

    @VisibleForTesting
    public static void setInstanceForTesting(SigninManager signinManager) {
        sSigninManager = signinManager;
    }

    // Native methods.
    private static native String nativeExtractDomainName(String email);
    private static native boolean nativeShouldLoadPolicyForUser(String username);
    private static native void nativeIsUserManaged(String username, Callback<Boolean> callback);
    private native long nativeInit();
    private native boolean nativeIsSigninAllowedByPolicy(long nativeSigninManagerAndroid);
    private native void nativeCheckPolicyBeforeSignIn(
            long nativeSigninManagerAndroid, String username);
    private native void nativeFetchPolicyBeforeSignIn(long nativeSigninManagerAndroid);
    private native void nativeAbortSignIn(long nativeSigninManagerAndroid);
    private native void nativeOnSignInCompleted(long nativeSigninManagerAndroid, String username);
    private native void nativeSignOut(long nativeSigninManagerAndroid);
    private native String nativeGetManagementDomain(long nativeSigninManagerAndroid);
    private native void nativeWipeProfileData(long nativeSigninManagerAndroid, WipeDataHooks hooks);
    private native void nativeClearLastSignedInUser(long nativeSigninManagerAndroid);
    private native void nativeLogInSignedInUser(long nativeSigninManagerAndroid);
    private native boolean nativeIsSignedInOnNative(long nativeSigninManagerAndroid);
}
