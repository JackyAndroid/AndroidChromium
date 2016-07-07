// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
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
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.childaccounts.ChildAccountService;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.preferences.ChromeBasePreference;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.profiles.ProfileAccountManagementMetrics;
import org.chromium.chrome.browser.profiles.ProfileDownloader;
import org.chromium.chrome.browser.signin.SignOutDialogFragment.SignOutDialogListener;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.sync.GoogleServiceAuthError;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.ProfileSyncService.SyncStateChangedListener;
import org.chromium.chrome.browser.sync.ui.SyncCustomizationFragment;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

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
                SyncStateChangedListener, SignInStateObserver {

    public static final String SIGN_OUT_DIALOG_TAG = "sign_out_dialog_tag";

    /**
     * The key for an integer value in
     * {@link Preferences#EXTRA_SHOW_FRAGMENT_ARGUMENTS} bundle to
     * specify the correct GAIA service that has triggered the dialog.
     * If the argument is not set, GAIA_SERVICE_TYPE_NONE is used as the origin of the dialog.
     */
    private static final String SHOW_GAIA_SERVICE_TYPE_EXTRA = "ShowGAIAServiceType";

    /**
     * The signin::GAIAServiceType value used in openAccountManagementScreen when the dialog
     * hasn't been triggered from the content area.
     */
    private static final int GAIA_SERVICE_TYPE_NONE = 0;

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

    public static final String PREF_GO_INCOGNITO = "go_incognito";

    public static final String PREF_SIGN_OUT_SWITCH = "sign_out_switch";
    public static final String PREF_SIGN_IN_CHILD_MESSAGE = "sign_in_child_message";
    public static final String PREF_ADD_ACCOUNT = "add_account";
    public static final String PREF_NOT_YOU = "not_you";
    public static final String PREF_PARENTAL_SETTINGS = "parental_settings";
    public static final String PREF_PARENT_ACCOUNTS = "parent_accounts";
    public static final String PREF_CHILD_CONTENT = "child_content";
    public static final String PREF_CHILD_SAFE_SEARCH = "child_safe_search";

    private int mGaiaServiceType;

    private ArrayList<Preference> mAccountsListPreferences = new ArrayList<Preference>();
    private Preference mPrimaryAccountPreference;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mGaiaServiceType = GAIA_SERVICE_TYPE_NONE;
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
        ProfileSyncService.get().addSyncStateChangedListener(this);

        update();
    }

    @Override
    public void onPause() {
        super.onPause();
        SigninManager.get(getActivity()).removeSignInStateObserver(this);
        ProfileDownloader.removeObserver(this);
        ProfileSyncService.get().removeSyncStateChangedListener(this);
    }

    /**
     * Initiate fetching the user accounts data (images and the full name).
     * Fetched data will be sent to observers of ProfileDownloader.
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
        configureAddAccountPreference(fullName);
        configureGoIncognitoPreferences(fullName);
        configureChildAccountPreferences();

        updateAccountsList();
    }

    private void configureSignOutSwitch() {
        boolean isChildAccount = ChildAccountService.isChildAccount();

        ChromeSwitchPreference signOutSwitch =
                (ChromeSwitchPreference) findPreference(PREF_SIGN_OUT_SWITCH);
        if (isChildAccount) {
            getPreferenceScreen().removePreference(signOutSwitch);
        } else {
            getPreferenceScreen().removePreference(findPreference(PREF_SIGN_IN_CHILD_MESSAGE));
            signOutSwitch.setChecked(true);
            signOutSwitch.setEnabled(getSignOutAllowedPreferenceValue(getActivity()));
            signOutSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (!isVisible() || !isResumed()) return false;
                    if ((boolean) newValue) return true;

                    if (ChromeSigninController.get(getActivity()).isSignedIn()
                            && getSignOutAllowedPreferenceValue(getActivity())) {
                        AccountManagementScreenHelper.logEvent(
                                ProfileAccountManagementMetrics.TOGGLE_SIGNOUT,
                                mGaiaServiceType);

                        SignOutDialogFragment signOutFragment = new SignOutDialogFragment();
                        signOutFragment.setTargetFragment(AccountManagementFragment.this, 0);
                        signOutFragment.show(getFragmentManager(), SIGN_OUT_DIALOG_TAG);
                    }

                    // Return false to prevent the switch from updating. The
                    // AccountManagementFragment is hidden when the user signs out of Chrome, so the
                    // switch never actually needs to be updated.
                    return false;
                }
            });
        }
    }

    private void configureAddAccountPreference(String fullName) {
        ChromeBasePreference addAccount = (ChromeBasePreference) findPreference(PREF_ADD_ACCOUNT);

        if (ChildAccountService.isChildAccount()) {
            getPreferenceScreen().removePreference(addAccount);
        } else {
            String addAccountString = getResources().getString(
                    R.string.account_management_add_account_title, fullName).toString();
            addAccount.setTitle(addAccountString);
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
                    if (mGaiaServiceType != GAIA_SERVICE_TYPE_NONE) {
                        if (isAdded()) getActivity().finish();
                    }

                    return true;
                }
            });
        }
    }

    private void configureGoIncognitoPreferences(String fullName) {
        boolean isChildAccount = ChildAccountService.isChildAccount();
        Preference notYouPref = findPreference(PREF_NOT_YOU);
        ChromeBasePreference goIncognito = (ChromeBasePreference) findPreference(PREF_GO_INCOGNITO);

        if (isChildAccount) {
            getPreferenceScreen().removePreference(notYouPref);
            getPreferenceScreen().removePreference(goIncognito);
        } else {
            notYouPref.setTitle(
                    getResources().getString(R.string.account_management_not_you_text, fullName));
            goIncognito.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (!isVisible() || !isResumed()) return false;
                    if (!PrefServiceBridge.getInstance().isIncognitoModeEnabled()) return false;

                    AccountManagementScreenHelper.logEvent(
                            ProfileAccountManagementMetrics.GO_INCOGNITO,
                            mGaiaServiceType);
                    openIncognitoTab(getActivity());
                    if (isAdded()) getActivity().finish();

                    return true;
                }
            });
            goIncognito.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
                @Override
                public boolean isPreferenceControlledByPolicy(Preference preference) {
                    // Incognito mode can be enabled by policy, but this has no visible impact on
                    // the user. Thus, the managed icon is displayed only if incognito mode is
                    // disabled.
                    PrefServiceBridge prefs = PrefServiceBridge.getInstance();
                    return prefs.isIncognitoModeManaged() && !prefs.isIncognitoModeEnabled();
                }
            });
        }
    }

    private static void openIncognitoTab(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                IntentHandler.GOOGLECHROME_NAVIGATE_PREFIX + UrlConstants.NTP_URL));
        intent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
        intent.setPackage(activity.getApplicationContext().getPackageName());
        intent.setClassName(activity.getApplicationContext().getPackageName(),
                ChromeLauncherActivity.class.getName());

        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        IntentHandler.startActivityForTrustedIntent(intent, activity);
    }

    private void configureChildAccountPreferences() {
        Preference parentAccounts = findPreference(PREF_PARENT_ACCOUNTS);
        Preference childContent = findPreference(PREF_CHILD_CONTENT);
        Preference childSafeSearch = findPreference(PREF_CHILD_SAFE_SEARCH);
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

            final String safeSearchText = res.getString(
                    prefService.isForceGoogleSafeSearch() ? R.string.text_on : R.string.text_off);
            childSafeSearch.setSummary(safeSearchText);
            childSafeSearch.setSelectable(false);
        } else {
            PreferenceScreen prefScreen = getPreferenceScreen();
            prefScreen.removePreference(findPreference(PREF_PARENTAL_SETTINGS));
            prefScreen.removePreference(parentAccounts);
            prefScreen.removePreference(childContent);
            prefScreen.removePreference(childSafeSearch);
        }
    }

    private void updateAccountsList() {
        PreferenceScreen prefScreen = getPreferenceScreen();
        if (prefScreen == null) return;

        for (int i = 0; i < mAccountsListPreferences.size(); i++) {
            prefScreen.removePreference(mAccountsListPreferences.get(i));
        }
        mAccountsListPreferences.clear();
        mPrimaryAccountPreference = null;

        final Preferences activity = (Preferences) getActivity();
        Account[] accounts = AccountManagerHelper.get(activity).getGoogleAccounts();
        int nextPrefOrder = FIRST_ACCOUNT_PREF_ORDER;

        for (final Account account : accounts) {
            ChromeBasePreference pref = new ChromeBasePreference(activity);
            pref.setTitle(account.name);

            String signedInAccountName =
                    ChromeSigninController.get(getActivity()).getSignedInAccountName();
            boolean isPrimaryAccount = TextUtils.equals(account.name, signedInAccountName);
            boolean isChildAccount = ChildAccountService.isChildAccount();

            pref.setIcon(new BitmapDrawable(getResources(), isChildAccount
                    ? getBadgedUserPicture(account.name) : getUserPicture(account.name)));

            if (isPrimaryAccount) {
                mPrimaryAccountPreference = pref;
                pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (!isVisible() || !isResumed()) return false;

                        AccountManagementScreenHelper.logEvent(
                                ProfileAccountManagementMetrics.CLICK_PRIMARY_ACCOUNT,
                                mGaiaServiceType);

                        if (AndroidSyncSettings.isMasterSyncEnabled(activity)) {
                            Bundle args = new Bundle();
                            args.putString(
                                    SyncCustomizationFragment.ARGUMENT_ACCOUNT, account.name);
                            activity.startFragment(SyncCustomizationFragment.class.getName(), args);
                        } else {
                            Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                            intent.putExtra("account_types", new String[]{"com.google"});
                            activity.startActivity(intent);
                        }

                        return true;
                    }
                });
            }

            pref.setOrder(nextPrefOrder++);
            prefScreen.addPreference(pref);
            mAccountsListPreferences.add(pref);
        }

        updateSyncStatus();
    }

    private void updateSyncStatus() {
        if (mPrimaryAccountPreference != null) {
            mPrimaryAccountPreference.setSummary(getSyncStatusSummary(getActivity()));
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

    @Override
    public void onSignOutClicked() {
        // In case the user reached this fragment without being signed in, we guard the sign out so
        // we do not hit a native crash.
        if (!ChromeSigninController.get(getActivity()).isSignedIn()) return;

        SigninManager.get(getActivity()).signOut(getActivity(), null);
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

    // ProfileSyncServiceListener implementation:

    @Override
    public void syncStateChanged() {
        updateSyncStatus();
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

    private static String getSyncStatusSummary(Activity activity) {
        if (!ChromeSigninController.get(activity).isSignedIn()) return "";

        ProfileSyncService profileSyncService = ProfileSyncService.get();
        Resources res = activity.getResources();

        if (ChildAccountService.isChildAccount()) {
            return res.getString(R.string.kids_account);
        }

        if (!AndroidSyncSettings.isMasterSyncEnabled(activity)) {
            return res.getString(R.string.sync_android_master_sync_disabled);
        }

        if (profileSyncService.getAuthError() != GoogleServiceAuthError.State.NONE) {
            return res.getString(profileSyncService.getAuthError().getMessage());
        }

        if (AndroidSyncSettings.isSyncEnabled(activity)) {
            if (!profileSyncService.isBackendInitialized()) {
                return res.getString(R.string.sync_setup_progress);
            }

            if (profileSyncService.isPassphraseRequiredForDecryption()) {
                return res.getString(R.string.sync_need_passphrase);
            }
        }

        return AndroidSyncSettings.isSyncEnabled(activity)
                ? res.getString(R.string.sync_is_enabled)
                : res.getString(R.string.sync_is_disabled);
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
    private Bitmap overlayChildBadgeOnUserPicture(Bitmap userPicture, Bitmap badge) {
        Resources resources = getResources();
        assert userPicture.getWidth()
                == resources.getDimensionPixelSize(R.dimen.user_picture_size);
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
     * @param accountId A child account.
     * @return A user picture with badge for a given child account.
     */
    private Bitmap getBadgedUserPicture(String accountId) {
        if (sChildAccountId != null) {
            assert TextUtils.equals(accountId, sChildAccountId);
            return sCachedBadgedPicture;
        }
        sChildAccountId = accountId;
        Bitmap picture = getUserPicture(accountId);
        Bitmap badge = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account_child);
        sCachedBadgedPicture = overlayChildBadgeOnUserPicture(picture, badge);
        return sCachedBadgedPicture;
    }

    /**
     * Gets the user picture for the account from the cache,
     * or returns the default picture if unavailable.
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
     * Gets the user picture for the account from the cache,
     * or returns the default picture if unavailable.
     * @param accountId An account.
     * @return A user picture for a given account.
     */
    private Bitmap getUserPicture(String accountId) {
        return getUserPicture(accountId, getResources());
    }

    /**
     * Initiate fetching of an image and a picture of a given account.
     * Fetched data will be sent to observers of ProfileDownloader.
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
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(SIGN_OUT_ALLOWED, true);
    }

    /**
     * Sets the sign out allowed preference value.
     * @param context A context
     * @param isAllowed True if the sign out is not disabled due to a child/EDU account
     */
    public static void setSignOutAllowedPreferenceValue(Context context, boolean isAllowed) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SIGN_OUT_ALLOWED, isAllowed)
                .apply();
    }
}
