// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.ImageCarousel.ImageCarouselPositionChangeListener;
import org.chromium.chrome.browser.profiles.ProfileDownloader;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.ui.widget.ButtonCompat;

import java.util.List;

/**
 * This view allows the user to select an account to log in to, add an account,
 * cancel account selection, etc. Users of this class should
 * {@link AccountFirstRunView#setListener(Listener)} after the view has been
 * inflated.
 */
public class AccountFirstRunView extends FrameLayout
        implements ImageCarouselPositionChangeListener, ProfileDownloader.Observer {

    /**
     * Callbacks for various account selection events.
     */
    public interface Listener {
        /**
         * The user selected an account.
         * @param accountName The name of the account
         */
        public void onAccountSelectionConfirmed(String accountName);

        /**
         * The user canceled account selection.
         */
        public void onAccountSelectionCanceled();

        /**
         * The user wants to make a new account.
         */
        public void onNewAccount();

        /**
         * The user has been signed in and pressed 'Done' button.
         * @param accountName The name of the account
         */
        public void onSigningInCompleted(String accountName);

        /**
         * The user has signed in and pressed 'Settings' button.
         * @param accountName The name of the account
         */
        public void onSettingsButtonClicked(String accountName);

        /**
         * Failed to set the forced account because it wasn't found.
         * @param forcedAccountName The name of the forced-sign-in account
         */
        public void onFailedToSetForcedAccount(String forcedAccountName);
    }

    private class SpinnerOnItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            String accountName = parent.getItemAtPosition(pos).toString();
            if (accountName.equals(mAddAnotherAccount)) {
                // Don't allow "add account" to remain selected. http://crbug.com/421052
                int oldPosition = mArrayAdapter.getPosition(mAccountName);
                if (oldPosition == -1) oldPosition = 0;
                mSpinner.setSelection(oldPosition, false);

                mListener.onNewAccount();
            } else {
                mAccountName = accountName;
                if (!mPositionSetProgrammatically) mImageCarousel.scrollTo(pos, false, false);
                mPositionSetProgrammatically = false;
            }
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            mAccountName = parent.getItemAtPosition(0).toString();
        }
    }

    private static final int EXPERIMENT_TITLE_VARIANT_MASK = 1;
    private static final int EXPERIMENT_SUMMARY_VARIANT_MASK = 2;
    private static final int EXPERIMENT_LAYOUT_VARIANT_MASK = 4;
    private static final int EXPERIMENT_MAX_VALUE = 7;

    private AccountManagerHelper mAccountManagerHelper;
    private List<String> mAccountNames;
    private ArrayAdapter<CharSequence> mArrayAdapter;
    private ImageCarousel mImageCarousel;
    private Button mPositiveButton;
    private Button mNegativeButton;
    private TextView mDescriptionText;
    private Listener mListener;
    private Spinner mSpinner;
    private String mForcedAccountName;
    private String mAccountName;
    private String mAddAnotherAccount;
    private ProfileDataCache mProfileData;
    private boolean mSignedIn;
    private boolean mPositionSetProgrammatically;
    private int mDescriptionTextId;
    private boolean mIsChildAccount;
    private boolean mHorizontalModeEnabled = true;

    public AccountFirstRunView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes this view with profile images and full names.
     * @param profileData ProfileDataCache that will be used to call to retrieve user account info.
     */
    public void init(ProfileDataCache profileData) {
        setProfileDataCache(profileData);
    }

    /**
     * Sets the profile data cache.
     * @param profileData ProfileDataCache that will be used to call to retrieve user account info.
     */
    public void setProfileDataCache(ProfileDataCache profileData) {
        mProfileData = profileData;
        mProfileData.setObserver(this);
        updateProfileImages();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImageCarousel = (ImageCarousel) findViewById(R.id.image_slider);
        mImageCarousel.setListener(this);

        mPositiveButton = (Button) findViewById(R.id.positive_button);
        mNegativeButton = (Button) findViewById(R.id.negative_button);
        mNegativeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setButtonsEnabled(false);
                mListener.onAccountSelectionCanceled();
            }
        });

        // A workaround for Android support library ignoring padding set in XML. b/20307607
        int padding = getResources().getDimensionPixelSize(R.dimen.fre_button_padding);
        ApiCompatibilityUtils.setPaddingRelative(mPositiveButton, padding, 0, padding, 0);
        ApiCompatibilityUtils.setPaddingRelative(mNegativeButton, padding, 0, padding, 0);

        mDescriptionText = (TextView) findViewById(R.id.description);
        mDescriptionTextId = R.string.fre_account_choice_description;

        mAddAnotherAccount = getResources().getString(R.string.fre_add_account);

        mSpinner = (Spinner) findViewById(R.id.google_accounts_spinner);
        mArrayAdapter = new ArrayAdapter<CharSequence>(
                getContext().getApplicationContext(), R.layout.fre_spinner_text);

        updateAccounts();

        mArrayAdapter.setDropDownViewResource(R.layout.fre_spinner_dropdown);
        mSpinner.setAdapter(mArrayAdapter);
        mSpinner.setOnItemSelectedListener(new SpinnerOnItemSelectedListener());
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This assumes that view's layout_width is set to match_parent.
        assert MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        LinearLayout content = (LinearLayout) findViewById(R.id.fre_content);
        int paddingStart = 0;
        if (mHorizontalModeEnabled
                && width >= 2 * getResources().getDimension(R.dimen.fre_image_carousel_width)
                && width > height) {
            content.setOrientation(LinearLayout.HORIZONTAL);
            paddingStart = getResources().getDimensionPixelSize(R.dimen.fre_margin);
        } else {
            content.setOrientation(LinearLayout.VERTICAL);
        }
        ApiCompatibilityUtils.setPaddingRelative(content,
                paddingStart,
                content.getPaddingTop(),
                ApiCompatibilityUtils.getPaddingEnd(content),
                content.getPaddingBottom());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Changes the visuals slightly for when this view appears in the recent tabs page instead of
     * in first run. For example, the title text is changed as well as the button style.
     */
    public void configureForRecentTabsPage() {
        mHorizontalModeEnabled = false;

        setBackgroundResource(R.color.ntp_bg);
        TextView title = (TextView) findViewById(R.id.title);
        title.setText(R.string.sign_in_to_chrome);

        // Remove the border above the button, swap in a new button with a blue material background,
        // and center the button.
        View buttonBarSeparator = findViewById(R.id.button_bar_separator);
        buttonBarSeparator.setVisibility(View.GONE);

        LinearLayout buttonContainer = (LinearLayout) findViewById(R.id.button_bar);
        buttonContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(0, 0, 0, getResources().getDimensionPixelOffset(
                R.dimen.sign_in_promo_padding_bottom));

        ButtonCompat positiveButton = new ButtonCompat(getContext(),
                ApiCompatibilityUtils.getColor(getResources(), R.color.light_active_color));
        positiveButton.setTextColor(Color.WHITE);
        positiveButton.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        buttonContainer.removeView(mPositiveButton);
        buttonContainer.addView(positiveButton);
        mPositiveButton = positiveButton;
    }

    /**
     * Changes the visuals slightly for when this view is shown in a subsequent run after user adds
     * a Google account to the device.
     */
    public void configureForAddAccountPromo() {
        int experimentGroup = SigninManager.getAndroidSigninPromoExperimentGroup();
        assert experimentGroup >= 0 && experimentGroup <= EXPERIMENT_MAX_VALUE;

        TextView title = (TextView) findViewById(R.id.title);
        if ((experimentGroup & EXPERIMENT_TITLE_VARIANT_MASK) != 0) {
            title.setText(R.string.make_chrome_yours);
        }

        mDescriptionTextId = (experimentGroup & EXPERIMENT_SUMMARY_VARIANT_MASK) != 0
                ? R.string.sign_in_to_chrome_summary_variant : R.string.sign_in_to_chrome_summary;

        if ((experimentGroup & EXPERIMENT_LAYOUT_VARIANT_MASK) != 0) {
            mImageCarousel.setVisibility(GONE);

            ImageView illustrationView = new ImageView(getContext());
            illustrationView.setImageResource(R.drawable.signin_promo_illustration);
            illustrationView.setBackgroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.illustration_background_color));

            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.fre_account_linear_layout);
            linearLayout.addView(illustrationView, 0);
        }
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
     * Tell the view whether or not the user can cancel account selection. In
     * wizards, it makes sense to allow the user to skip account selection.
     * However, in other settings-type contexts it does not make sense to allow
     * this.
     *
     * @param canCancel Whether or not account selection can be canceled.
     */
    public void setCanCancel(boolean canCancel) {
        mNegativeButton.setVisibility(canCancel ? View.VISIBLE : View.GONE);
        mPositiveButton.setGravity(
                canCancel ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.CENTER);
    }

    /**
     * Refresh the list of available system account.
     */
    private void updateAccounts() {
        if (mSignedIn) return;
        setButtonsEnabled(true);

        mAccountManagerHelper = AccountManagerHelper.get(getContext().getApplicationContext());

        List<String> oldAccountNames = mAccountNames;
        mAccountNames = mAccountManagerHelper.getGoogleAccountNames();
        int accountToSelect = 0;
        if (mForcedAccountName != null) {
            accountToSelect = mAccountNames.indexOf(mForcedAccountName);
            if (accountToSelect < 0) {
                mListener.onFailedToSetForcedAccount(mForcedAccountName);
                return;
            }
        } else {
            accountToSelect = getIndexOfNewElement(
                    oldAccountNames, mAccountNames, mSpinner.getSelectedItemPosition());
        }

        mArrayAdapter.clear();
        if (!mAccountNames.isEmpty()) {
            mSpinner.setVisibility(View.VISIBLE);
            mArrayAdapter.addAll(mAccountNames);
            mArrayAdapter.add(mAddAnotherAccount);
            mPositiveButton.setText(R.string.choose_account_sign_in);
            mPositiveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onAccountSelectionConfirmed(mAccountName);
                }
            });
            mDescriptionText.setText(mDescriptionTextId);
        } else {
            mSpinner.setVisibility(View.GONE);
            mArrayAdapter.add(mAddAnotherAccount);
            mPositiveButton.setText(R.string.fre_no_accounts);
            mPositiveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onNewAccount();
                }
            });
            mDescriptionText.setText(R.string.fre_no_account_choice_description);
        }

        if (mProfileData != null) mProfileData.update();
        updateProfileImages();

        mSpinner.setSelection(accountToSelect);
        mAccountName = mArrayAdapter.getItem(accountToSelect).toString();
        mImageCarousel.scrollTo(accountToSelect, false, false);
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
        updateProfileImages();
    }

    private void updateProfileImages() {
        if (mProfileData == null) return;

        int count = mAccountNames.size();

        Bitmap[] images;
        if (count == 0) {
            images = new Bitmap[1];
            images[0] = mProfileData.getImage(null);
        } else {
            images = new Bitmap[count];
            for (int i = 0; i < count; ++i) {
                images[i] = mProfileData.getImage(mAccountNames.get(i));
            }
        }

        mImageCarousel.setImages(images);
        updateProfileName();
    }

    private void updateProfileName() {
        if (!mSignedIn) return;

        String name = null;
        if (mIsChildAccount) name = mProfileData.getGivenName(mAccountName);
        if (name == null) name = mProfileData.getFullName(mAccountName);
        if (name == null) name = mAccountName;
        String text = String.format(getResources().getString(R.string.fre_hi_name), name);
        ((TextView) findViewById(R.id.title)).setText(text);
    }

    /**
     * Updates the view to show that sign in has completed.
     */
    public void switchToSignedMode() {
        mSignedIn = true;
        updateProfileName();

        mSpinner.setEnabled(false);
        mSpinner.setBackground(null);
        mPositiveButton.setText(getResources().getText(R.string.fre_done));
        mPositiveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onSigningInCompleted(mAccountName);
            }
        });
        mNegativeButton.setText(getResources().getText(R.string.fre_settings));
        mNegativeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onSettingsButtonClicked(mAccountName);
            }
        });
        setButtonsEnabled(true);
        String text = getResources().getString(R.string.fre_signed_in_description);
        if (mIsChildAccount) {
            text += "\n" + getResources().getString(
                    R.string.fre_signed_in_description_uca_addendum);
        }
        mDescriptionText.setText(text);
        mImageCarousel.setVisibility(VISIBLE);
        mImageCarousel.setSignedInMode();
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
        assert TextUtils.equals(mAccountName, mForcedAccountName);
        switchToSignedMode();
        assert TextUtils.equals(mAccountName, mForcedAccountName);
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

    @Override
    public void onPositionChanged(int i) {
        mPositionSetProgrammatically = true;
        mSpinner.setSelection(i);
    }
}
