// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView.OnEditorActionListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.payments.ui.PaymentRequestUI.PaymentRequestObserverForTest;
import org.chromium.chrome.browser.widget.CompatibilityTextInputLayout;
import org.chromium.chrome.browser.widget.TintedDrawable;

import javax.annotation.Nullable;

/** Handles validation and display of one field from the {@link EditorFieldModel}. */
@VisibleForTesting
public class EditorTextField extends FrameLayout implements EditorFieldView, View.OnClickListener {
    private EditorFieldModel mEditorFieldModel;
    private CompatibilityTextInputLayout mInputLayout;
    private AutoCompleteTextView mInput;
    private ImageView mActionIcon;
    private boolean mHasFocusedAtLeastOnce;
    @Nullable private PaymentRequestObserverForTest mObserverForTest;

    public EditorTextField(Context context, final EditorFieldModel fieldModel,
            OnEditorActionListener actionlistener, @Nullable InputFilter filter,
            @Nullable TextWatcher formatter, @Nullable PaymentRequestObserverForTest observer) {
        super(context);
        assert fieldModel.getInputTypeHint() != EditorFieldModel.INPUT_TYPE_HINT_DROPDOWN;
        mEditorFieldModel = fieldModel;
        mObserverForTest = observer;

        LayoutInflater.from(context).inflate(R.layout.payments_request_editor_textview, this, true);
        mInputLayout = (CompatibilityTextInputLayout) findViewById(R.id.text_input_layout);

        // Build up the label.  Required fields are indicated by appending a '*'.
        CharSequence label = fieldModel.getLabel();
        if (fieldModel.isRequired()) label = label + EditorView.REQUIRED_FIELD_INDICATOR;
        mInputLayout.setHint(label);

        mInput = (AutoCompleteTextView) mInputLayout.findViewById(R.id.text_view);
        mInput.setText(fieldModel.getValue());
        mInput.setContentDescription(label);
        mInput.setOnEditorActionListener(actionlistener);

        if (fieldModel.getIconAction() != null) {
            mActionIcon = (ImageView) findViewById(R.id.action_icon);
            mActionIcon.setImageDrawable(
                    TintedDrawable.constructTintedDrawable(context.getResources(),
                            fieldModel.getActionIconResourceId(), R.color.light_active_color));
            mActionIcon.setContentDescription(context.getResources().getString(
                    fieldModel.getActionDescriptionForAccessibility()));
            mActionIcon.setOnClickListener(this);
            mActionIcon.setVisibility(VISIBLE);
        }

        // Validate the field when the user de-focuses it.
        mInput.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mHasFocusedAtLeastOnce = true;
                } else if (mHasFocusedAtLeastOnce) {
                    // Show no errors until the user has already tried to edit the field once.
                    updateDisplayedError(!mEditorFieldModel.isValid());
                }
            }
        });

        // Update the model as the user edits the field.
        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                fieldModel.setValue(s.toString());
                updateDisplayedError(false);
                if (mObserverForTest != null) {
                    mObserverForTest.onPaymentRequestEditorTextUpdate();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        // Display any autofill suggestions.
        if (fieldModel.getSuggestions() != null && !fieldModel.getSuggestions().isEmpty()) {
            mInput.setAdapter(new ArrayAdapter<CharSequence>(getContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    fieldModel.getSuggestions()));
            mInput.setThreshold(0);
        }

        if (filter != null) mInput.setFilters(new InputFilter[] {filter});
        if (formatter != null) mInput.addTextChangedListener(formatter);

        switch (fieldModel.getInputTypeHint()) {
            case EditorFieldModel.INPUT_TYPE_HINT_CREDIT_CARD:
                // Intentionally fall through.
                //
                // There's no keyboard that allows numbers, spaces, and "-" only, so use the phone
                // keyboard instead. The phone keyboard has more symbols than necessary. A filter
                // should be used to prevent input of phone number symbols that are not relevant for
                // credit card numbers, e.g., "+", "*", and "#".
                //
                // The number keyboard is not suitable, because it filters out everything except
                // digits.
            case EditorFieldModel.INPUT_TYPE_HINT_PHONE:
                // Show the keyboard with numbers and phone-related symbols.
                mInput.setInputType(InputType.TYPE_CLASS_PHONE);
                break;
            case EditorFieldModel.INPUT_TYPE_HINT_EMAIL:
                mInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                break;
            case EditorFieldModel.INPUT_TYPE_HINT_STREET_LINES:
                // TODO(rouslan): Provide a hint to the keyboard that the street lines are
                // likely to have numbers.
                mInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
                break;
            case EditorFieldModel.INPUT_TYPE_HINT_PERSON_NAME:
                mInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                break;
            case EditorFieldModel.INPUT_TYPE_HINT_ALPHA_NUMERIC:
                // Intentionally fall through.
                // TODO(rouslan): Provide a hint to the keyboard that postal code and sorting
                // code are likely to have numbers.
            case EditorFieldModel.INPUT_TYPE_HINT_REGION:
                mInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                        | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
                break;
            default:
                mInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
                break;
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mActionIcon != null) {
            if (mActionIcon.getMeasuredWidth() == 0) {
                mActionIcon.measure(widthMeasureSpec, heightMeasureSpec);
            }

            // Padding at the end of mInput to preserve space for mActionIcon.
            ApiCompatibilityUtils.setPaddingRelative(mInput,
                    ApiCompatibilityUtils.getPaddingStart(mInput), mInput.getPaddingTop(),
                    mActionIcon.getWidth(), mInput.getPaddingBottom());
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (changed && mActionIcon != null) {
            // Align the bottom of mActionIcon to the bottom of mInput (mActionIcon overlaps
            // mInput).
            // Note one:   mActionIcon can not be put inside mInputLayout to display on top of
            // mInput since mInputLayout is LinearLayout in essential.
            // Note two:   mActionIcon and mInput can not be put in ViewGroup to display over each
            // other inside mInputLayout since mInputLayout must contain an instance of EditText
            // child view.
            // Note three: mInputLayout's bottom changes when displaying error.
            float offset = mInputLayout.getY() + mInput.getY() + (float) mInput.getHeight()
                    - (float) mActionIcon.getHeight() - mActionIcon.getTop();
            mActionIcon.setTranslationY(offset);
        }
    }

    @Override
    public void onClick(View v) {
        mEditorFieldModel.getIconAction().run();
    }

    /** @return The EditorFieldModel that the TextView represents. */
    public EditorFieldModel getFieldModel() {
        return mEditorFieldModel;
    }

    /** @return The AutoCompleteTextView this field associates*/
    public AutoCompleteTextView getEditText() {
        return mInput;
    }

    @Override
    public boolean isValid() {
        return mEditorFieldModel.isValid();
    }

    @Override
    public void updateDisplayedError(boolean showError) {
        mInputLayout.setError(showError ? mEditorFieldModel.getErrorMessage() : null);
    }

    @Override
    public void scrollToAndFocus() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.requestChildFocus(this, this);
        requestFocus();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    @Override
    public void update() {
        mInput.setText(mEditorFieldModel.getValue());
    }
}
