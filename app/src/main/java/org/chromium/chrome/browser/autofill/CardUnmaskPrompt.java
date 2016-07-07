// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.MarginLayoutParamsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.Calendar;

/**
 * A prompt that bugs users to enter their CVC when unmasking a Wallet instrument (credit card).
 */
public class CardUnmaskPrompt
        implements DialogInterface.OnDismissListener, TextWatcher, OnClickListener {
    private final CardUnmaskPromptDelegate mDelegate;
    private final AlertDialog mDialog;
    private boolean mShouldRequestExpirationDate;
    private final int mThisYear;

    private final View mMainView;
    private final TextView mInstructions;
    private final TextView mNoRetryErrorMessage;
    private final EditText mCardUnmaskInput;
    private final EditText mMonthInput;
    private final EditText mYearInput;
    private final View mExpirationContainer;
    private final TextView mNewCardLink;
    private final TextView mErrorMessage;
    private final CheckBox mStoreLocallyCheckbox;
    private final ImageView mStoreLocallyTooltipIcon;
    private PopupWindow mStoreLocallyTooltipPopup;
    private final ViewGroup mControlsContainer;
    private final View mVerificationOverlay;
    private final ProgressBar mVerificationProgressBar;
    private final TextView mVerificationView;

    /**
     * An interface to handle the interaction with an CardUnmaskPrompt object.
     */
    public interface CardUnmaskPromptDelegate {
        /**
         * Called when the dialog has been dismissed.
         */
        void dismissed();

        /**
         * Returns whether |userResponse| represents a valid value.
         * @param userResponse A CVC entered by the user.
         */
        boolean checkUserInputValidity(String userResponse);

        /**
         * Called when the user has entered a value and pressed "verify".
         * @param userResponse The value the user entered (a CVC), or an empty string if the
         *        user canceled.
         * @param month The value the user selected for expiration month, if any.
         * @param year The value the user selected for expiration month, if any.
         * @param shouldStoreLocally The state of the "Save locally?" checkbox at the time.
         */
        void onUserInput(String cvc, String month, String year, boolean shouldStoreLocally);

        /**
         * Called when the "New card?" link has been clicked.
         * The controller will call update() in response.
         */
        void onNewCardLinkClicked();
    }

    public CardUnmaskPrompt(Context context, CardUnmaskPromptDelegate delegate, String title,
            String instructions, int drawableId, boolean shouldRequestExpirationDate,
            boolean canStoreLocally, boolean defaultToStoringLocally) {
        mDelegate = delegate;

        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(R.layout.autofill_card_unmask_prompt, null);
        mInstructions = (TextView) v.findViewById(R.id.instructions);
        mInstructions.setText(instructions);

        mMainView = v;
        mNoRetryErrorMessage = (TextView) v.findViewById(R.id.no_retry_error_message);
        mCardUnmaskInput = (EditText) v.findViewById(R.id.card_unmask_input);
        mMonthInput = (EditText) v.findViewById(R.id.expiration_month);
        mYearInput = (EditText) v.findViewById(R.id.expiration_year);
        mExpirationContainer = v.findViewById(R.id.expiration_container);
        mNewCardLink = (TextView) v.findViewById(R.id.new_card_link);
        mNewCardLink.setOnClickListener(this);
        mErrorMessage = (TextView) v.findViewById(R.id.error_message);
        mStoreLocallyCheckbox = (CheckBox) v.findViewById(R.id.store_locally_checkbox);
        mStoreLocallyCheckbox.setChecked(canStoreLocally && defaultToStoringLocally);
        mStoreLocallyTooltipIcon = (ImageView) v.findViewById(R.id.store_locally_tooltip_icon);
        mStoreLocallyTooltipIcon.setOnClickListener(this);
        if (!canStoreLocally) v.findViewById(R.id.store_locally_container).setVisibility(View.GONE);
        mControlsContainer = (ViewGroup) v.findViewById(R.id.controls_container);
        mVerificationOverlay = v.findViewById(R.id.verification_overlay);
        mVerificationProgressBar = (ProgressBar) v.findViewById(R.id.verification_progress_bar);
        mVerificationView = (TextView) v.findViewById(R.id.verification_message);
        ((ImageView) v.findViewById(R.id.cvc_hint_image)).setImageResource(drawableId);

        mDialog = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(title)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.autofill_card_unmask_confirm_button, null)
                .create();
        mDialog.setOnDismissListener(this);

        mShouldRequestExpirationDate = shouldRequestExpirationDate;
        mThisYear = Calendar.getInstance().get(Calendar.YEAR);
    }

    public void show() {
        mDialog.show();

        showExpirationDateInputsInputs();

        // Override the View.OnClickListener so that pressing the positive button doesn't dismiss
        // the dialog.
        Button verifyButton = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        verifyButton.setEnabled(false);
        verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDelegate.onUserInput(mCardUnmaskInput.getText().toString(),
                        mMonthInput.getText().toString(),
                        Integer.toString(getFourDigitYear()),
                        mStoreLocallyCheckbox != null && mStoreLocallyCheckbox.isChecked());
            }
        });

        mCardUnmaskInput.addTextChangedListener(this);
        mCardUnmaskInput.post(new Runnable() {
            @Override
            public void run() {
                setInitialFocus();
            }
        });
    }

    public void update(String title, String instructions, boolean shouldRequestExpirationDate) {
        assert mDialog.isShowing();
        mDialog.setTitle(title);
        mInstructions.setText(instructions);
        mShouldRequestExpirationDate = shouldRequestExpirationDate;
        showExpirationDateInputsInputs();
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    public void disableAndWaitForVerification() {
        setInputsEnabled(false);
        setOverlayVisibility(View.VISIBLE);
        mVerificationProgressBar.setVisibility(View.VISIBLE);
        mVerificationView.setText(R.string.autofill_card_unmask_verification_in_progress);
        mVerificationView.announceForAccessibility(mVerificationView.getText());
        setInputError(null);
    }

    public void verificationFinished(String errorMessage, boolean allowRetry) {
        if (errorMessage != null) {
            setOverlayVisibility(View.GONE);
            if (allowRetry) {
                setInputError(errorMessage);
                setInputsEnabled(true);
                setInitialFocus();

                if (!mShouldRequestExpirationDate) mNewCardLink.setVisibility(View.VISIBLE);
            } else {
                setInputError(null);
                setNoRetryError(errorMessage);
            }
        } else {
            mVerificationProgressBar.setVisibility(View.GONE);
            mDialog.findViewById(R.id.verification_success).setVisibility(View.VISIBLE);
            mVerificationView.setText(
                    R.string.autofill_card_unmask_verification_success);
            Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            }, 1000);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDelegate.dismissed();
    }

    @Override
    public void afterTextChanged(Editable s) {
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(areInputsValid());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void onClick(View v) {
        if (v == mStoreLocallyTooltipIcon) {
            onTooltipIconClicked();
        } else {
            assert v == mNewCardLink;
            onNewCardLinkClicked();
        }
    }

    private void showExpirationDateInputsInputs() {
        if (!mShouldRequestExpirationDate || mExpirationContainer.getVisibility() == View.VISIBLE) {
            return;
        }

        mExpirationContainer.setVisibility(View.VISIBLE);
        mCardUnmaskInput.setEms(3);
        mMonthInput.addTextChangedListener(this);
        mYearInput.addTextChangedListener(this);
    }

    private void onTooltipIconClicked() {
        // Don't show the popup if there's already one showing (or one has been dismissed
        // recently). This prevents a tap on the (?) from hiding and then immediately re-showing
        // the popup.
        if (mStoreLocallyTooltipPopup != null) return;

        mStoreLocallyTooltipPopup = new PopupWindow(mDialog.getContext());
        TextView text = new TextView(mDialog.getContext());
        text.setText(R.string.autofill_card_unmask_prompt_storage_tooltip);
        // Width is the dialog's width less the margins and padding around the checkbox and
        // icon.
        text.setWidth(mMainView.getWidth() - ViewCompat.getPaddingStart(mStoreLocallyCheckbox)
                - ViewCompat.getPaddingEnd(mStoreLocallyTooltipIcon)
                - MarginLayoutParamsCompat.getMarginStart((RelativeLayout.LayoutParams)
                        mStoreLocallyCheckbox.getLayoutParams())
                - MarginLayoutParamsCompat.getMarginEnd((RelativeLayout.LayoutParams)
                        mStoreLocallyTooltipIcon.getLayoutParams()));
        text.setTextColor(Color.WHITE);
        Resources resources = mDialog.getContext().getResources();
        int hPadding = resources.getDimensionPixelSize(
                R.dimen.autofill_card_unmask_tooltip_horizontal_padding);
        int vPadding = resources.getDimensionPixelSize(
                R.dimen.autofill_card_unmask_tooltip_vertical_padding);
        text.setPadding(hPadding, vPadding, hPadding, vPadding);

        mStoreLocallyTooltipPopup.setContentView(text);
        mStoreLocallyTooltipPopup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mStoreLocallyTooltipPopup.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mStoreLocallyTooltipPopup.setOutsideTouchable(true);
        mStoreLocallyTooltipPopup.setBackgroundDrawable(ApiCompatibilityUtils.getDrawable(
                resources, R.drawable.store_locally_tooltip_background));
        mStoreLocallyTooltipPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mStoreLocallyTooltipPopup = null;
                    }
                }, 200);
            }
        });
        mStoreLocallyTooltipPopup.showAsDropDown(mStoreLocallyCheckbox,
                ViewCompat.getPaddingStart(mStoreLocallyCheckbox), 0);
        text.announceForAccessibility(text.getText());
    }

    private void onNewCardLinkClicked() {
        mDelegate.onNewCardLinkClicked();
        assert mShouldRequestExpirationDate;
        mNewCardLink.setVisibility(View.GONE);
        mCardUnmaskInput.setText(null);
        setInputError(null);
        mMonthInput.requestFocus();
    }

    private void setInitialFocus() {
        InputMethodManager imm = (InputMethodManager) mDialog.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        View view = mShouldRequestExpirationDate ? mMonthInput : mCardUnmaskInput;
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private boolean areInputsValid() {
        if (mShouldRequestExpirationDate) {
            try {
                int month = Integer.parseInt(mMonthInput.getText().toString());
                if (month < 1 || month > 12) return false;
            } catch (NumberFormatException e) {
                return false;
            }

            int year = getFourDigitYear();
            if (year < mThisYear || year > mThisYear + 10) return false;
        }
        return mDelegate.checkUserInputValidity(mCardUnmaskInput.getText().toString());
    }

    /**
     * Sets the enabled state of the main contents, and hides or shows the verification overlay.
     * @param enabled True if the inputs should be useable, false if the verification overlay
     *        obscures them.
     */
    private void setInputsEnabled(boolean enabled) {
        mCardUnmaskInput.setEnabled(enabled);
        mMonthInput.setEnabled(enabled);
        mYearInput.setEnabled(enabled);
        mStoreLocallyCheckbox.setEnabled(enabled);
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
    }

    /**
     * Updates the verification overlay and main contents such that the overlay has |visibility|.
     * @param visibility A View visibility enumeration value.
     */
    private void setOverlayVisibility(int visibility) {
        mVerificationOverlay.setVisibility(visibility);
        mControlsContainer.setAlpha(1f);
        boolean contentsShowing = visibility == View.GONE;
        if (!contentsShowing) {
            int durationMs = 250;
            mVerificationOverlay.setAlpha(0f);
            mVerificationOverlay.animate().alpha(1f).setDuration(durationMs);
            mControlsContainer.animate().alpha(0f).setDuration(durationMs);
        }
        ViewCompat.setImportantForAccessibility(mControlsContainer,
                contentsShowing ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        mControlsContainer.setDescendantFocusability(
                contentsShowing ? ViewGroup.FOCUS_BEFORE_DESCENDANTS
                                : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    /**
     * Sets the error message on the cvc input.
     * @param message The error message to show, or null if the error state should be cleared.
     */
    private void setInputError(String message) {
        mErrorMessage.setText(message);
        mErrorMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);

        // A null message is passed in during card verification, which also makes an announcement.
        // Announcing twice in a row may cancel the first announcement.
        if (message != null) {
            mErrorMessage.announceForAccessibility(message);
        }

        // The rest of this code makes L-specific assumptions about the background being used to
        // draw the TextInput.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        ColorFilter filter = null;
        if (message != null) {
            filter = new PorterDuffColorFilter(ApiCompatibilityUtils.getColor(
                    mDialog.getContext().getResources(),
                    R.color.input_underline_error_color), PorterDuff.Mode.SRC_IN);
        }

        // TODO(estade): it would be nicer if the error were specific enough to tell us which input
        // was invalid.
        updateColorForInput(mCardUnmaskInput, filter);
        updateColorForInput(mMonthInput, filter);
        updateColorForInput(mYearInput, filter);
    }

    /**
     * Displays an error that indicates the user can't retry.
     */
    private void setNoRetryError(String message) {
        mNoRetryErrorMessage.setText(message);
        mNoRetryErrorMessage.setVisibility(View.VISIBLE);
        mNoRetryErrorMessage.announceForAccessibility(message);
    }

    /**
     * Sets the stroke color for the given input.
     * @param input The input to modify.
     * @param filter The color filter to apply to the background.
     */
    private void updateColorForInput(EditText input, ColorFilter filter) {
        input.getBackground().mutate().setColorFilter(filter);
    }

    /**
     * Returns the expiration year the user entered.
     * Two digit values (such as 17) will be converted to 4 digit years (such as 2017).
     * Returns -1 if the input is empty or otherwise not a valid year.
     */
    private int getFourDigitYear() {
        try {
            int year = Integer.parseInt(mYearInput.getText().toString());
            if (year < 0) return -1;
            if (year < 100) year += mThisYear - mThisYear % 100;
            return year;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
