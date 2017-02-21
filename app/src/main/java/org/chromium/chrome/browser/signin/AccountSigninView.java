// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.ProfileDownloader;
import org.chromium.chrome.browser.signin.AccountTrackerService.OnSystemAccountsSeededListener;
import org.chromium.chrome.browser.signin.ConfirmImportSyncDataDialog.ImportSyncType;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;
import org.chromium.ui.widget.ButtonCompat;

import java.util.List;

// TODO(gogerald): refactor common part into one place after redesign all sign in screens.

/**
 * This view allows the user to select an account to log in to, add an account,
 * cancel account selection, etc. Users of this class should
 * {@link AccountSigninView#setListener(Listener)} and
 * {@link AccountSigninView#setDelegate(Delegate)} after the view has been inflated.
 */

public class AccountSigninView extends FrameLayout implements ProfileDownloader.Observer {
    /**
     * Callbacks for various account selection events.
     */
    public interface Listener {
        /**
         * The user canceled account selection.
         */
        public void onAccountSelectionCanceled();

        /**
         * The user wants to make a new account.
         */
        public void onNewAccount();

        /**
         * The user completed the View and selected an account.
         * @param accountName The name of the account
         * @param settingsClicked If true, user requested to see their sync settings, if false
         *                        they just clicked Done.
         */
        public void onAccountSelected(String accountName, boolean settingsClicked);

        /**
         * Failed to set the forced account because it wasn't found.
         * @param forcedAccountName The name of the forced-sign-in account
         */
        public void onFailedToSetForcedAccount(String forcedAccountName);
    }

    // TODO(peconn): Investigate expanding the Delegate to simplify the Listener implementations.

    /**
     * Provides UI objects for new UI component creation.
     */
    public interface Delegate {
        /**
         * Provides an Activity for the View to check GMSCore version.
         */
        public Activity getActivity();

        /**
         * Provides a FragmentManager for the View to create dialogs. This is done through a
         * different mechanism than getActivity().getFragmentManager() as a potential fix to
         * https://crbug.com/646978 on the theory that getActivity() and getFragmentManager()
         * return null at different times.
         */
        public FragmentManager getFragmentManager();
    }

    private static final String TAG = "AccountSigninView";

    private static final String SETTINGS_LINK_OPEN = "<LINK1>";
    private static final String SETTINGS_LINK_CLOSE = "</LINK1>";

    private AccountManagerHelper mAccountManagerHelper;
    private List<String> mAccountNames;
    private AccountSigninChooseView mSigninChooseView;
    private ButtonCompat mPositiveButton;
    private Button mNegativeButton;
    private Button mMoreButton;
    private Listener mListener;
    private Delegate mDelegate;
    private String mForcedAccountName;
    private ProfileDataCache mProfileData;
    private boolean mSignedIn;
    private int mCancelButtonTextId;
    private boolean mIsChildAccount;

    private AccountSigninConfirmationView mSigninConfirmationView;
    private ImageView mSigninAccountImage;
    private TextView mSigninAccountName;
    private TextView mSigninAccountEmail;
    private TextView mSigninSettingsControl;

    public AccountSigninView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAccountManagerHelper = AccountManagerHelper.get(getContext().getApplicationContext());
    }

    /**
     * Initializes this view with profile images and full names.
     * @param profileData ProfileDataCache that will be used to call to retrieve user account info.
     */
    public void init(ProfileDataCache profileData) {
        mProfileData = profileData;
        mProfileData.setObserver(this);
        showSigninPage();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSigninChooseView = (AccountSigninChooseView) findViewById(R.id.account_signin_choose_view);
        mSigninChooseView.setAddNewAccountObserver(new AccountSigninChooseView.Observer() {
            @Override
            public void onAddNewAccount() {
                mListener.onNewAccount();
                RecordUserAction.record("Signin_AddAccountToDevice");
            }
        });

        mPositiveButton = (ButtonCompat) findViewById(R.id.positive_button);
        mNegativeButton = (Button) findViewById(R.id.negative_button);
        mMoreButton = (Button) findViewById(R.id.more_button);

        // A workaround for Android support library ignoring padding set in XML. b/20307607
        int padding = getResources().getDimensionPixelSize(R.dimen.fre_button_padding);
        ApiCompatibilityUtils.setPaddingRelative(mPositiveButton, padding, 0, padding, 0);
        ApiCompatibilityUtils.setPaddingRelative(mNegativeButton, padding, 0, padding, 0);

        // TODO(peconn): Ensure this is changed to R.string.cancel when used in Settings > Sign In.
        mCancelButtonTextId = R.string.no_thanks;

        mSigninConfirmationView =
                (AccountSigninConfirmationView) findViewById(R.id.signin_confirmation_view);
        mSigninConfirmationView.setScrolledToBottomObserver(
                new AccountSigninConfirmationView.Observer() {
                    @Override
                    public void onScrolledToBottom() {
                        setUpMoreButtonVisible(false);
                    }
                });
        mSigninAccountImage = (ImageView) findViewById(R.id.signin_account_image);
        mSigninAccountName = (TextView) findViewById(R.id.signin_account_name);
        mSigninAccountEmail = (TextView) findViewById(R.id.signin_account_email);
        mSigninSettingsControl = (TextView) findViewById(R.id.signin_settings_control);
        // For the spans to be clickable.
        mSigninSettingsControl.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAccounts();
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            updateAccounts();
        }
    }

    /**
     * Changes the visuals slightly for when this view appears in the recent tabs page instead of
     * in first run.
     * This is currently used when signing in from the Recent Tabs or Bookmarks pages.
     */
    public void configureForRecentTabsOrBookmarksPage() {
        mCancelButtonTextId = R.string.cancel;
        setUpCancelButton();
    }

    /**
     * Enable or disable UI elements so the user can't select an account, cancel, etc.
     *
     * @param enabled The state to change to.
     */
    public void setButtonsEnabled(boolean enabled) {
        mPositiveButton.setEnabled(enabled);
        mNegativeButton.setEnabled(enabled);
    }

    /**
     * Set the account selection event listener.  See {@link Listener}
     *
     * @param listener The listener.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Set the UI object creation delegate. See {@link Delegate}
     * @param delegate The delegate.
     */
    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    /**
     * Refresh the list of available system accounts.
     */
    private void updateAccounts() {
        if (mSignedIn || mProfileData == null) return;

        List<String> oldAccountNames = mAccountNames;
        mAccountNames = mAccountManagerHelper.getGoogleAccountNames();
        int accountToSelect = 0;
        if (isInForcedAccountMode()) {
            accountToSelect = mAccountNames.indexOf(mForcedAccountName);
            if (accountToSelect < 0) {
                mListener.onFailedToSetForcedAccount(mForcedAccountName);
                return;
            }
        } else {
            accountToSelect = getIndexOfNewElement(
                    oldAccountNames, mAccountNames, mSigninChooseView.getSelectedAccountPosition());
        }

        int oldSelectedAccount = mSigninChooseView.getSelectedAccountPosition();
        mSigninChooseView.updateAccounts(mAccountNames, accountToSelect, mProfileData);
        if (mAccountNames.isEmpty()) {
            setUpSigninButton(false);
            return;
        }
        setUpSigninButton(true);

        mProfileData.update();

        // Determine how the accounts have changed. Each list should only have unique elements.
        if (oldAccountNames == null || oldAccountNames.isEmpty()) return;

        if (!mAccountNames.get(accountToSelect).equals(oldAccountNames.get(oldSelectedAccount))) {
            // Any dialogs that may have been showing are now invalid (they were created for the
            // previously selected account).
            ConfirmSyncDataStateMachine
                    .cancelAllDialogs(mDelegate.getFragmentManager());

            if (mAccountNames.containsAll(oldAccountNames)) {
                // A new account has been added and no accounts have been deleted. We will have
                // changed the account selection to the newly added account, so shortcut to the
                // confirm signin page.
                showConfirmSigninPageAccountTrackerServiceCheck();
            }
        }
    }

    /**
     * Attempt to select a new element that is in the new list, but not in the old list.
     * If no such element exist and both the new and the old lists are the same then keep
     * the selection. Otherwise select the first element.
     * @param oldList Old list of user accounts.
     * @param newList New list of user accounts.
     * @param oldIndex Index of the selected account in the old list.
     * @return The index of the new element, if it does not exist but lists are the same the
     *         return the old index, otherwise return 0.
     */
    private static int getIndexOfNewElement(
            List<String> oldList, List<String> newList, int oldIndex) {
        if (oldList == null || newList == null) return 0;
        if (oldList.size() == newList.size() && oldList.containsAll(newList)) return oldIndex;
        if (oldList.size() + 1 == newList.size()) {
            for (int i = 0; i < newList.size(); i++) {
                if (!oldList.contains(newList.get(i))) return i;
            }
        }
        return 0;
    }

    @Override
    public void onProfileDownloaded(String accountId, String fullName, String givenName,
            Bitmap bitmap) {
        mSigninChooseView.updateAccountProfileImages(mProfileData);

        if (mSignedIn) updateSignedInAccountInfo();
    }

    private void updateSignedInAccountInfo() {
        String selectedAccountEmail = getSelectedAccountName();
        mSigninAccountImage.setImageBitmap(mProfileData.getImage(selectedAccountEmail));
        String name = null;
        if (mIsChildAccount) name = mProfileData.getGivenName(selectedAccountEmail);
        if (name == null) name = mProfileData.getFullName(selectedAccountEmail);
        if (name == null) name = selectedAccountEmail;
        String text = String.format(getResources().getString(R.string.signin_hi_name), name);
        mSigninAccountName.setText(text);
        mSigninAccountEmail.setText(selectedAccountEmail);
    }

    /**
     * Updates the view to show that sign in has completed.
     * This should only be used if the user is not currently signed in (eg on the First
     * Run Experience).
     */
    public void switchToSignedMode() {
        // TODO(peconn): Add a warning here
        showConfirmSigninPage();
    }

    private void showSigninPage() {
        mSignedIn = false;

        mSigninConfirmationView.setVisibility(View.GONE);
        mSigninChooseView.setVisibility(View.VISIBLE);

        setUpCancelButton();
        updateAccounts();
    }

    private void showConfirmSigninPage() {
        mSignedIn = true;

        updateSignedInAccountInfo();

        mSigninChooseView.setVisibility(View.GONE);
        mSigninConfirmationView.setVisibility(View.VISIBLE);

        setButtonsEnabled(true);
        setUpConfirmButton();
        setUpUndoButton();

        NoUnderlineClickableSpan settingsSpan = new NoUnderlineClickableSpan() {
            @Override
            public void onClick(View widget) {
                mListener.onAccountSelected(getSelectedAccountName(), true);
                RecordUserAction.record("Signin_Signin_WithAdvancedSyncSettings");
            }
        };
        mSigninSettingsControl.setText(
                SpanApplier.applySpans(getSettingsControlDescription(mIsChildAccount),
                        new SpanInfo(SETTINGS_LINK_OPEN, SETTINGS_LINK_CLOSE, settingsSpan)));
    }

    private void showConfirmSigninPageAccountTrackerServiceCheck() {
        if (!ExternalAuthUtils.getInstance().canUseGooglePlayServices(getContext(),
                new UserRecoverableErrorHandler.ModalDialog(mDelegate.getActivity()))) {
            return;
        }

        // Disable the buttons to prevent them being clicked again while waiting for the callbacks.
        setButtonsEnabled(false);

        // Ensure that the AccountTrackerService has a fully up to date GAIA id <-> email mapping,
        // as this is needed for the previous account check.
        if (AccountTrackerService.get(getContext()).checkAndSeedSystemAccounts()) {
            showConfirmSigninPagePreviousAccountCheck();
        } else {
            AccountTrackerService.get(getContext()).addSystemAccountsSeededListener(
                    new OnSystemAccountsSeededListener() {
                        @Override
                        public void onSystemAccountsSeedingComplete() {
                            AccountTrackerService.get(getContext())
                                    .removeSystemAccountsSeededListener(this);
                            showConfirmSigninPagePreviousAccountCheck();
                        }

                        @Override
                        public void onSystemAccountsChanged() {}
                    });
        }
    }

    private void showConfirmSigninPagePreviousAccountCheck() {
        String accountName = getSelectedAccountName();
        ConfirmSyncDataStateMachine.run(PrefServiceBridge.getInstance().getSyncLastAccountName(),
                accountName, ImportSyncType.PREVIOUS_DATA_FOUND,
                mDelegate.getFragmentManager(),
                getContext(), new ConfirmImportSyncDataDialog.Listener() {
                    @Override
                    public void onConfirm(boolean wipeData) {
                        SigninManager.wipeSyncUserDataIfRequired(wipeData)
                                .then(new Callback<Void>() {
                                    @Override
                                    public void onResult(Void v) {
                                        showConfirmSigninPage();
                                    }
                                });
                    }

                    @Override
                    public void onCancel() {
                        setButtonsEnabled(true);
                    }
                });
    }

    private void setUpCancelButton() {
        setNegativeButtonVisible(true);

        mNegativeButton.setText(getResources().getText(mCancelButtonTextId));
        mNegativeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setButtonsEnabled(false);
                mListener.onAccountSelectionCanceled();
            }
        });
    }

    private void setUpSigninButton(boolean hasAccounts) {
        if (hasAccounts) {
            mPositiveButton.setText(R.string.continue_sign_in);
            mPositiveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConfirmSigninPageAccountTrackerServiceCheck();
                }
            });
        } else {
            mPositiveButton.setText(R.string.choose_account_sign_in);
            mPositiveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    RecordUserAction.record("Signin_AddAccountToDevice");
                    mListener.onNewAccount();
                }
            });
        }
        setUpMoreButtonVisible(false);
    }

    private void setUpUndoButton() {
        setNegativeButtonVisible(!isInForcedAccountMode());
        if (isInForcedAccountMode()) return;

        mNegativeButton.setText(getResources().getText(R.string.undo));
        mNegativeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                RecordUserAction.record("Signin_Undo_Signin");
                showSigninPage();
            }
        });
    }

    private void setUpConfirmButton() {
        mPositiveButton.setText(getResources().getText(R.string.signin_accept));
        mPositiveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onAccountSelected(getSelectedAccountName(), false);
                RecordUserAction.record("Signin_Signin_WithDefaultSyncSettings");
            }
        });
        setUpMoreButtonVisible(true);
    }

    /*
    * mMoreButton is used to scroll mSigninConfirmationView down. It displays at the same position
    * as mPositiveButton.
    */
    private void setUpMoreButtonVisible(boolean enabled) {
        if (enabled) {
            mPositiveButton.setVisibility(View.GONE);
            mMoreButton.setVisibility(View.VISIBLE);
            mMoreButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSigninConfirmationView.smoothScrollBy(0, mSigninConfirmationView.getHeight());
                    RecordUserAction.record("Signin_MoreButton_Shown");
                }
            });
        } else {
            mPositiveButton.setVisibility(View.VISIBLE);
            mMoreButton.setVisibility(View.GONE);
        }
    }

    private void setNegativeButtonVisible(boolean enabled) {
        if (enabled) {
            mNegativeButton.setVisibility(View.VISIBLE);
            findViewById(R.id.positive_button_end_padding).setVisibility(View.GONE);
        } else {
            mNegativeButton.setVisibility(View.GONE);
            findViewById(R.id.positive_button_end_padding).setVisibility(View.INVISIBLE);
        }
    }

    private String getSettingsControlDescription(boolean childAccount) {
        if (childAccount) {
            return getResources().getString(R.string.signin_signed_in_settings_description) + '\n'
                    + getResources().getString(R.string.signin_signed_in_description_uca_addendum);
        } else {
            return getResources().getString(R.string.signin_signed_in_settings_description);
        }
    }

    /**
     * @param isChildAccount Whether this view is for a child account.
     */
    public void setIsChildAccount(boolean isChildAccount) {
        mIsChildAccount = isChildAccount;
    }

    /**
     * Switches the view to "no choice, just a confirmation" forced-account mode.
     * @param forcedAccountName An account that should be force-selected.
     */
    public void switchToForcedAccountMode(String forcedAccountName) {
        mForcedAccountName = forcedAccountName;
        updateAccounts();
        assert TextUtils.equals(getSelectedAccountName(), mForcedAccountName);
        switchToSignedMode();
        assert TextUtils.equals(getSelectedAccountName(), mForcedAccountName);
    }

    /**
     * @return Whether the view is in signed in mode.
     */
    public boolean isSignedIn() {
        return mSignedIn;
    }

    /**
     * @return Whether the view is in "no choice, just a confirmation" forced-account mode.
     */
    public boolean isInForcedAccountMode() {
        return mForcedAccountName != null;
    }

    private String getSelectedAccountName() {
        return mAccountNames.get(mSigninChooseView.getSelectedAccountPosition());
    }
}
