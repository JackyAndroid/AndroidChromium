// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.childaccounts.ChildAccountService;
import org.chromium.chrome.browser.preferences.ChromeBasePreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.SyncPreference;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.profiles.ProfileAccountManagementMetrics;
import org.chromium.chrome.browser.profiles.ProfileDownloader;
import org.chromium.chrome.browser.signin.SignOutDialogFragment.SignOutDialogListener;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.ProfileSyncService.SyncStateChangedListener;
import org.chromium.chrome.browser.sync.ui.SyncCustomizationFragment;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * The settings screen with information and settings related to the user's accounts.
 *
 * This shows which accounts the user is signed in with, allows the user to sign out of Chrome,
 * links to sync settings, has links to add accounts and go incognito, and shows parental settings
 * if a child account is in use.
 *
 * Note: This can be triggered from a web page, e.g. a GAIA sign-in page.
 */
public class AccountManagementFragment extends PreferenceFragment
        implements SignOutDialogListener, ProfileDownloader.Observer,
                SyncStateChangedListener, SignInStateObserver,
                ConfirmManagedSyncDataDialog.Listener {
    private static final String TAG = "AcctManagementPref";

    public static final String SIGN_OUT_DIALOG_TAG = "sign_out_dialog_tag";
    private static final String CLEAR_DATA_PROGRESS_DIALOG_TAG = "clear_data_progress";

    /**
     * The key for an integer value in
     * {@link Preferences#EXTRA_SHOW_FRAGMENT_ARGUMENTS} bundle to
     * specify the correct GAIA service that has triggered the dialog.
     * If the argument is not set, GAIA_SERVICE_TYPE_NONE is used as the origin of the dialog.
     */
    public static final String SHOW_GAIA_SERVICE_TYPE_EXTRA = "ShowGAIAServiceType";

    /**
     * Account name preferences will be ordered sequentially, starting with this "order" value.
     * This ensures that the account name preferences appear in the correct location in the
     * preference fragment. See account_management_preferences.xml for details.
     */
    private static final int FIRST_ACCOUNT_PREF_ORDER = 100;

    /**
     * SharedPreference name for the preference that disables signing out of Chrome.
     * Signing out is forever disabled once Chrome signs the user in automatically
     * if the device has a child account or if the device is an Android EDU device.
     */
    private static final String SIGN_OUT_ALLOWED = "auto_signed_in_school_account";

    private static final HashMap<String, Pair<String, Bitmap>> sToNamePicture =
            new HashMap<String, Pair<String, Bitmap>>();

    private static String sChildAccountId = null;
    private static Bitmap sCachedBadgedPicture = null;

    public static final String PREF_SIGN_OUT = "sign_out";
    public static final String PREF_ADD_ACCOUNT = "add_account";
    public static final String PREF_PARENTAL_SETTINGS = "parental_settings";
    public static final String PREF_PARENT_ACCOUNTS = "parent_accounts";
    public static final String PREF_CHILD_CONTENT = "child_content";
    public static final String PREF_CHILD_SAFE_SITES = "child_safe_sites";
    public static final String PREF_GOOGLE_ACTIVITY_CONTROLS = "google_activity_controls";
    public static final String PREF_SYNC_SETTINGS = "sync_settings";

    private int mGaiaServiceType;

    private ArrayList<Preference> mAccountsListPreferences = new ArrayList<Preference>();

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Prevent sync from starting if it hasn't already to give the user a chance to change
        // their sync settings.
        ProfileSyncService syncService = ProfileSyncService.get();
        if (syncService != null) {
            syncService.setSetupInProgress(true);
        }

        mGaiaServiceType = AccountManagementScreenHelper.GAIA_SERVICE_TYPE_NONE;
        if (getArguments() != null) {
            mGaiaServiceType =
                    getArguments().getInt(SHOW_GAIA_SERVICE_TYPE_EXTRA, mGaiaServiceType);
        }

        AccountManagementScreenHelper.logEvent(
                ProfileAccountManagementMetrics.VIEW,
                mGaiaServiceType);

        startFetchingAccountsInformation(getActivity(), Profile.getLastUsedProfile());
    }

    @Override
    public void onResume() {
        super.onResume();
        SigninManager.get(getActivity()).addSignInStateObserver(this);
        ProfileDownloader.addObserver(this);
        ProfileSyncService syncService = ProfileSyncService.get();
        if (syncService != null) {
            syncService.addSyncStateChangedListener(this);
        }

        update();
    }

    @Override
    public void onPause() {
        super.onPause();
        SigninManager.get(getActivity()).removeSignInStateObserver(this);
        ProfileDownloader.removeObserver(this);
        ProfileSyncService syncService = ProfileSyncService.get();
        if (syncService != null) {
            syncService.removeSyncStateChangedListener(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Allow sync to begin syncing if it hasn't yet.
        ProfileSyncService syncService = ProfileSyncService.get();
        if (syncService != null) {
            syncService.setSetupInProgress(false);
        }
    }

    /**
     * Initiate fetching the user accounts data (images and the full name).
     * Fetched data will be sent to observers of ProfileDownloader.
     *
     * @param profile Profile to use.
     */
    private static void startFetchingAccountsInformation(Context context, Profile profile) {
        Account[] accounts = AccountManagerHelper.get(context).getGoogleAccounts();
        for (int i = 0; i < accounts.length; i++) {
            startFetchingAccountInformation(context, profile, accounts[i].name);
        }
    }

    public void update() {
        final Context context = getActivity();
        if (context == null) return;

        if (getPreferenceScreen() != null) getPreferenceScreen().removeAll();

        ChromeSigninController signInController = ChromeSigninController.get(context);
        if (!signInController.isSignedIn()) {
            // The AccountManagementFragment can only be shown when the user is signed in. If the
            // user is signed out, exit the fragment.
            getActivity().finish();
            return;
        }

        addPreferencesFromResource(R.xml.account_management_preferences);

        String signedInAccountName =
                ChromeSigninController.get(getActivity()).getSignedInAccountName();
        String fullName = getCachedUserName(signedInAccountName);
        if (TextUtils.isEmpty(fullName)) {
            fullName = ProfileDownloader.getCachedFullName(Profile.getLastUsedProfile());
        }
        if (TextUtils.isEmpty(fullName)) fullName = signedInAccountName;

        getActivity().setTitle(fullName);

        configureSignOutSwitch();
        configureAddAccountPreference();
        configureChildAccountPreferences();
        configureSyncSettings();
        configureGoogleActivityControls();

        updateAccountsList();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean canAddAccounts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true;

        UserManager userManager = (UserManager) getActivity()
                .getSystemService(Context.USER_SERVICE);
        return !userManager.hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS);
    }

    private void configureSignOutSwitch() {
        boolean isChildAccount = ChildAccountService.isChildAccount();

        Preference signOutSwitch = findPreference(PREF_SIGN_OUT);
        if (isChildAccount) {
            getPreferenceScreen().removePreference(signOutSwitch);
        } else {
            signOutSwitch.setEnabled(getSignOutAllowedPreferenceValue(getActivity()));
            signOutSwitch.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (!isVisible() || !isResumed()) return false;

                    if (ChromeSigninController.get(getActivity()).isSignedIn()
                            && getSignOutAllowedPreferenceValue(getActivity())) {
                        AccountManagementScreenHelper.logEvent(
                                ProfileAccountManagementMetrics.TOGGLE_SIGNOUT,
                                mGaiaServiceType);

                        String managementDomain =
                                SigninManager.get(getActivity()).getManagementDomain();
                        if (managementDomain != null) {
                            // Show the 'You are signing out of a managed account' dialog.
                            ConfirmManagedSyncDataDialog.showSignOutFromManagedAccountDialog(
                                    AccountManagementFragment.this, getFragmentManager(),
                                    getResources(), managementDomain);
                        } else {
                            // Show the 'You are signing out' dialog.
                            SignOutDialogFragment signOutFragment = new SignOutDialogFragment();
                            Bundle args = new Bundle();
                            args.putInt(SHOW_GAIA_SERVICE_TYPE_EXTRA, mGaiaServiceType);
                            signOutFragment.setArguments(args);

                            signOutFragment.setTargetFragment(AccountManagementFragment.this, 0);
                            signOutFragment.show(getFragmentManager(), SIGN_OUT_DIALOG_TAG);
                        }

                        return true;
                    }

                    return false;
                }

            });
        }
    }

    private void configureSyncSettings() {
        final Preferences preferences = (Preferences) getActivity();
        final Account account = ChromeSigninController.get(getActivity()).getSignedInUser();
        findPreference(PREF_SYNC_SETTINGS)
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (!isVisible() || !isResumed()) return false;

                        if (ProfileSyncService.get() == null) return true;

                        Bundle args = new Bundle();
                        args.putString(SyncCustomizationFragment.ARGUMENT_ACCOUNT, account.name);
                        preferences.startFragment(SyncCustomizationFragment.class.getName(), args);

                        return true;
                    }
                });
    }

    private void configureGoogleActivityControls() {
        Preference pref = findPreference(PREF_GOOGLE_ACTIVITY_CONTROLS);
        pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Activity activity = getActivity();
                ((ChromeApplication) (activity.getApplicationContext()))
                        .createGoogleActivityController()
                        .openWebAndAppActivitySettings(activity,
                                ChromeSigninController.get(activity).getSignedInAccountName());
                RecordUserAction.record("Signin_AccountSettings_GoogleActivityControlsClicked");
                return true;
            }
        });
    }

    private void configureAddAccountPreference() {
        ChromeBasePreference addAccount = (ChromeBasePreference) findPreference(PREF_ADD_ACCOUNT);

        if (ChildAccountService.isChildAccount()) {
            getPreferenceScreen().removePreference(addAccount);
        } else {
            addAccount.setTitle(getResources().getString(
                    R.string.account_management_add_account_title));
            addAccount.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (!isVisible() || !isResumed()) return false;

                    AccountManagementScreenHelper.logEvent(
                            ProfileAccountManagementMetrics.ADD_ACCOUNT,
                            mGaiaServiceType);

                    AccountAdder.getInstance().addAccount(
                            getActivity(), AccountAdder.ADD_ACCOUNT_RESULT);

                    // Return to the last opened tab if triggered from the content area.
                    if (mGaiaServiceType != AccountManagementScreenHelper.GAIA_SERVICE_TYPE_NONE) {
                        if (isAdded()) getActivity().finish();
                    }

                    return true;
                }
            });
            addAccount.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
                @Override
                public boolean isPreferenceControlledByPolicy(Preference preference) {
                    return !canAddAccounts();
                }
            });
        }
    }

    private void configureChildAccountPreferences() {
        Preference parentAccounts = findPreference(PREF_PARENT_ACCOUNTS);
        Preference childContent = findPreference(PREF_CHILD_CONTENT);
        Preference childSafeSites = findPreference(PREF_CHILD_SAFE_SITES);
        if (ChildAccountService.isChildAccount()) {
            Resources res = getActivity().getResources();
            PrefServiceBridge prefService = PrefServiceBridge.getInstance();

            String firstParent = prefService.getSupervisedUserCustodianEmail();
            String secondParent = prefService.getSupervisedUserSecondCustodianEmail();
            String parentText;

            if (!secondParent.isEmpty()) {
                parentText = res.getString(R.string.account_management_two_parent_names,
                        firstParent, secondParent);
            } else if (!firstParent.isEmpty()) {
                parentText = res.getString(R.string.account_management_one_parent_name,
                        firstParent);
            } else {
                parentText = res.getString(R.string.account_management_no_parental_data);
            }
            parentAccounts.setSummary(parentText);
            parentAccounts.setSelectable(false);

            final boolean unapprovedContentBlocked =
                    prefService.getDefaultSupervisedUserFilteringBehavior()
                    == PrefServiceBridge.SUPERVISED_USER_FILTERING_BLOCK;
            final String contentText = res.getString(
                    unapprovedContentBlocked ? R.string.account_management_child_content_approved
                            : R.string.account_management_child_content_all);
            childContent.setSummary(contentText);
            childContent.setSelectable(false);

            final String safeSitesText = res.getString(
                    prefService.isSupervisedUserSafeSitesEnabled()
                            ? R.string.text_on : R.string.text_off);
            childSafeSites.setSummary(safeSitesText);
            childSafeSites.setSelectable(false);
        } else {
            PreferenceScreen prefScreen = getPreferenceScreen();
            prefScreen.removePreference(findPreference(PREF_PARENTAL_SETTINGS));
            prefScreen.removePreference(parentAccounts);
            prefScreen.removePreference(childContent);
            prefScreen.removePreference(childSafeSites);
        }
    }

    private void updateAccountsList() {
        PreferenceScreen prefScreen = getPreferenceScreen();
        if (prefScreen == null) return;

        for (int i = 0; i < mAccountsListPreferences.size(); i++) {
            prefScreen.removePreference(mAccountsListPreferences.get(i));
        }
        mAccountsListPreferences.clear();

        final Preferences activity = (Preferences) getActivity();
        Account[] accounts = AccountManagerHelper.get(activity).getGoogleAccounts();
        int nextPrefOrder = FIRST_ACCOUNT_PREF_ORDER;

        for (Account account : accounts) {
            ChromeBasePreference pref = new ChromeBasePreference(activity);
            pref.setSelectable(false);
            pref.setTitle(account.name);

            boolean isChildAccount = ChildAccountService.isChildAccount();

            pref.setIcon(new BitmapDrawable(getResources(),
                    isChildAccount ? getBadgedUserPicture(account.name, getResources()) :
                        getUserPicture(account.name, getResources())));

            pref.setOrder(nextPrefOrder++);
            prefScreen.addPreference(pref);
            mAccountsListPreferences.add(pref);
        }
    }

    // ProfileDownloader.Observer implementation:

    @Override
    public void onProfileDownloaded(String accountId, String fullName, String givenName,
            Bitmap bitmap) {
        updateUserNamePictureCache(accountId, fullName, bitmap);
        updateAccountsList();
    }

    // SignOutDialogListener implementation:

    /**
     * This class must be public and static. Otherwise an exception will be thrown when Android
     * recreates the fragment (e.g. after a configuration change).
     */
    public static class ClearDataProgressDialog extends DialogFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Don't allow the dialog to be recreated by Android, since it wouldn't ever be
            // dismissed after recreation.
            if (savedInstanceState != null) dismiss();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            setCancelable(false);
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setTitle(getString(R.string.wiping_profile_data_title));
            dialog.setMessage(getString(R.string.wiping_profile_data_message));
            dialog.setIndeterminate(true);
            return dialog;
        }
    }

    @Override
    public void onSignOutClicked() {
        // In case the user reached this fragment without being signed in, we guard the sign out so
        // we do not hit a native crash.
        if (!ChromeSigninController.get(getActivity()).isSignedIn()) return;

        final Activity activity = getActivity();
        final DialogFragment clearDataProgressDialog = new ClearDataProgressDialog();
        SigninManager.get(activity).signOut(null, new SigninManager.WipeDataHooks() {
            @Override
            public void preWipeData() {
                clearDataProgressDialog.show(
                        activity.getFragmentManager(), CLEAR_DATA_PROGRESS_DIALOG_TAG);
            }
            @Override
            public void postWipeData() {
                if (clearDataProgressDialog.isAdded()) {
                    clearDataProgressDialog.dismissAllowingStateLoss();
                }
            }
        });
        AccountManagementScreenHelper.logEvent(
                ProfileAccountManagementMetrics.SIGNOUT_SIGNOUT,
                mGaiaServiceType);
    }

    @Override
    public void onSignOutDialogDismissed(boolean signOutClicked) {
        if (!signOutClicked) {
            AccountManagementScreenHelper.logEvent(
                    ProfileAccountManagementMetrics.SIGNOUT_CANCEL,
                    mGaiaServiceType);
        }
    }

    // ConfirmManagedSyncDataDialog.Listener implementation
    @Override
    public void onConfirm() {
        onSignOutClicked();
    }

    @Override
    public void onCancel() {
        onSignOutDialogDismissed(false);
    }

    // ProfileSyncServiceListener implementation:

    @Override
    public void syncStateChanged() {
        SyncPreference pref = (SyncPreference) findPreference(PREF_SYNC_SETTINGS);
        if (pref != null) {
            pref.updateSyncSummaryAndIcon();
        }

        // TODO(crbug/557784): Show notification for sync error
    }

    // SignInStateObserver implementation:

    @Override
    public void onSignedIn() {
        update();
    }

    @Override
    public void onSignedOut() {
        update();
    }

    /**
     * Open the account management UI.
     * @param applicationContext An application context.
     * @param profile A user profile.
     * @param serviceType A signin::GAIAServiceType that triggered the dialog.
     */
    public static void openAccountManagementScreen(
            Context applicationContext, Profile profile, int serviceType) {
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(applicationContext,
                AccountManagementFragment.class.getName());
        Bundle arguments = new Bundle();
        arguments.putInt(SHOW_GAIA_SERVICE_TYPE_EXTRA, serviceType);
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, arguments);
        applicationContext.startActivity(intent);
    }

    /**
     * Converts a square user picture to a round user picture.
     * @param bitmap A bitmap to convert.
     * @return A rounded picture bitmap.
     */
    public static Bitmap makeRoundUserPicture(Bitmap bitmap) {
        if (bitmap == null) return null;

        Bitmap output = Bitmap.createBitmap(
                bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        canvas.drawARGB(0, 0, 0, 0);
        paint.setAntiAlias(true);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f,
                bitmap.getWidth() * 0.5f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    /**
     * Creates a new image with the picture overlaid by the badge.
     * @param userPicture A bitmap to overlay on.
     * @param badge A bitmap to overlay with.
     * @return A bitmap with the badge overlaying the {@code userPicture}.
     */
    private static Bitmap overlayChildBadgeOnUserPicture(
            Bitmap userPicture, Bitmap badge, Resources resources) {
        assert userPicture.getWidth() == resources.getDimensionPixelSize(R.dimen.user_picture_size);
        int borderSize = resources.getDimensionPixelOffset(R.dimen.badge_border_size);
        int badgeRadius = resources.getDimensionPixelOffset(R.dimen.badge_radius);

        // Create a larger image to accommodate the badge which spills the original picture.
        int badgedPictureWidth =
                resources.getDimensionPixelOffset(R.dimen.badged_user_picture_width);
        int badgedPictureHeight =
                resources.getDimensionPixelOffset(R.dimen.badged_user_picture_height);
        Bitmap badgedPicture = Bitmap.createBitmap(badgedPictureWidth, badgedPictureHeight,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(badgedPicture);
        canvas.drawBitmap(userPicture, 0, 0, null);

        // Cut a transparent hole through the background image.
        // This will serve as a border to the badge being overlaid.
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        int badgeCenterX = badgedPictureWidth - badgeRadius;
        int badgeCenterY = badgedPictureHeight - badgeRadius;
        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius + borderSize, paint);

        // Draw the badge
        canvas.drawBitmap(badge, badgeCenterX - badgeRadius, badgeCenterY - badgeRadius, null);
        return badgedPicture;
    }

    /**
     * Updates the user name and picture in the cache.
     * @param accountId User's account id.
     * @param fullName User name.
     * @param bitmap User picture.
     */
    public static void updateUserNamePictureCache(
            String accountId, String fullName, Bitmap bitmap) {
        sChildAccountId = null;
        sCachedBadgedPicture = null;
        sToNamePicture.put(accountId,
                new Pair<String, Bitmap>(fullName, makeRoundUserPicture(bitmap)));
    }

    /**
     * @param accountId An account.
     * @return A cached user name for a given account.
     */
    public static String getCachedUserName(String accountId) {
        Pair<String, Bitmap> pair = sToNamePicture.get(accountId);
        return pair != null ? pair.first : null;
    }

    /**
     * Gets the user picture for the account from the cache, or returns the default picture if
     * unavailable.
     *
     * @param accountId A child account.
     * @return A user picture with badge for a given child account.
     */
    public static Bitmap getBadgedUserPicture(String accountId, Resources res) {
        if (sChildAccountId != null) {
            assert TextUtils.equals(accountId, sChildAccountId);
            return sCachedBadgedPicture;
        }
        sChildAccountId = accountId;
        Bitmap picture = getUserPicture(accountId, res);
        Bitmap badge = BitmapFactory.decodeResource(res, R.drawable.ic_account_child);
        sCachedBadgedPicture = overlayChildBadgeOnUserPicture(picture, badge, res);
        return sCachedBadgedPicture;
    }

    /**
     * Gets the user picture for the account from the cache, or returns the default picture if
     * unavailable.
     *
     * @param accountId An account.
     * @param resources The collection containing the application resources.
     * @return A user picture for a given account.
     */
    public static Bitmap getUserPicture(String accountId, Resources resources) {
        Pair<String, Bitmap> pair = sToNamePicture.get(accountId);
        return pair != null ? pair.second : BitmapFactory.decodeResource(resources,
                R.drawable.account_management_no_picture);
    }

    /**
     * Initiate fetching of an image and a picture of a given account. Fetched data will be sent to
     * observers of ProfileDownloader.
     *
     * @param context A context.
     * @param profile A profile.
     * @param accountName An account name.
     */
    public static void startFetchingAccountInformation(
            Context context, Profile profile, String accountName) {
        if (TextUtils.isEmpty(accountName)) return;
        if (sToNamePicture.get(accountName) != null) return;

        final int imageSidePixels =
                context.getResources().getDimensionPixelOffset(R.dimen.user_picture_size);
        ProfileDownloader.startFetchingAccountInfoFor(
                context, profile, accountName, imageSidePixels, false);
    }

    /**
     * Prefetch the primary account image and name.
     *
     * @param context A context to use.
     * @param profile A profile to use.
     */
    public static void prefetchUserNamePicture(Context context, Profile profile) {
        final String accountName = ChromeSigninController.get(context).getSignedInAccountName();
        if (TextUtils.isEmpty(accountName)) return;
        if (sToNamePicture.get(accountName) != null) return;

        ProfileDownloader.addObserver(new ProfileDownloader.Observer() {
            @Override
            public void onProfileDownloaded(String accountId, String fullName, String givenName,
                    Bitmap bitmap) {
                if (TextUtils.equals(accountName, accountId)) {
                    updateUserNamePictureCache(accountId, fullName, bitmap);
                    ProfileDownloader.removeObserver(this);
                }
            }
        });
        startFetchingAccountInformation(context, profile, accountName);
    }

    /**
     * @param context A context
     * @return Whether the sign out is not disabled due to a child/EDU account.
     */
    private static boolean getSignOutAllowedPreferenceValue(Context context) {
        return ContextUtils.getAppSharedPreferences()
                .getBoolean(SIGN_OUT_ALLOWED, true);
    }

    /**
     * Sets the sign out allowed preference value.
     *
     * @param context A context
     * @param isAllowed True if the sign out is not disabled due to a child/EDU account
     */
    public static void setSignOutAllowedPreferenceValue(Context context, boolean isAllowed) {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(SIGN_OUT_ALLOWED, isAllowed)
                .apply();
    }
}
