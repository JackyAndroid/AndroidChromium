// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Layout which uses a {@link android.widget.TextView} to show a floating label above an
 * {@link android.widget.EditText} when the hint is hidden due to the user inputting text.
 *
 * @see <a href="https://dribbble.com/shots/1254439--GIF-Mobile-Form-Interaction">Matt D. Smith on Dribble</a>
 * @see <a href="http://bradfrostweb.com/blog/post/float-label-pattern/">Brad Frost's blog post</a>
 *
 * This class was originally written by Chris Banes; it should be replaced if/when floating labels
 * are available in the support library.
 * @see https://gist.github.com/chrisbanes/11247418
 *
 * Example XML layout:
 *     <org.chromium.chrome.browser.widget.FloatLabelLayout
 *          android:layout_width="match_parent"
 *          android:layout_height="wrap_content"
 *          app:floatLabelTextAppearance="@style/TextAppearance.YourApp.FloatLabel">
 *          <EditText
 *              android:id="@+id/edit_username"
 *              android:layout_width="match_parent"
 *              android:layout_height="wrap_content"
 *              android:hint="@string/account_username_hint" />
 *     </org.chromium.chrome.browser.widget.FloatLabelLayout>
 */
public class FloatLabelLayout extends LinearLayout {

    private static final long ANIMATION_DURATION = 150;

    private static final float DEFAULT_LABEL_PADDING_LEFT = 3f;
    private static final float DEFAULT_LABEL_PADDING_TOP = 4f;
    private static final float DEFAULT_LABEL_PADDING_RIGHT = 3f;
    private static final float DEFAULT_LABEL_PADDING_BOTTOM = 4f;

    private EditText mEditText;
    private TextView mLabel;

    private CharSequence mHint;
    private Interpolator mInterpolator;

    public FloatLabelLayout(Context context) {
        this(context, null);
    }

    public FloatLabelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatLabelLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrientation(VERTICAL);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FloatLabelLayout);

        int leftPadding = a.getDimensionPixelSize(
                R.styleable.FloatLabelLayout_floatLabelPaddingLeft,
                dipsToPix(DEFAULT_LABEL_PADDING_LEFT));
        int topPadding = a.getDimensionPixelSize(
                R.styleable.FloatLabelLayout_floatLabelPaddingTop,
                dipsToPix(DEFAULT_LABEL_PADDING_TOP));
        int rightPadding = a.getDimensionPixelSize(
                R.styleable.FloatLabelLayout_floatLabelPaddingRight,
                dipsToPix(DEFAULT_LABEL_PADDING_RIGHT));
        int bottomPadding = a.getDimensionPixelSize(
                R.styleable.FloatLabelLayout_floatLabelPaddingBottom,
                dipsToPix(DEFAULT_LABEL_PADDING_BOTTOM));
        mHint = a.getText(R.styleable.FloatLabelLayout_floatLabelHint);

        mLabel = new TextView(context);
        mLabel.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
        mLabel.setVisibility(INVISIBLE);
        mLabel.setText(mHint);
        mLabel.setFocusable(true);
        ViewCompat.setPivotX(mLabel, 0f);
        ViewCompat.setPivotY(mLabel, 0f);

        ApiCompatibilityUtils.setTextAppearance(mLabel,
                a.getResourceId(R.styleable.FloatLabelLayout_floatLabelTextAppearance,
                        android.R.style.TextAppearance_Small));
        a.recycle();

        addView(mLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        mInterpolator = AnimationUtils.loadInterpolator(context,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? android.R.interpolator.fast_out_slow_in
                    : android.R.anim.decelerate_interpolator);
    }

    @Override
    public final void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof EditText) {
            setEditText((EditText) child);
        }

        // Carry on adding the View...
        super.addView(child, index, params);
    }

    /**
     * Focuses the EditText and makes the label visible without the normal
     * animation that occurs on focus. This is particularly useful when
     * auto-focusing the first field on a form.
     */
    public void focusWithoutAnimation() {
        mEditText.setHint(null);
        mLabel.setVisibility(View.VISIBLE);
        mEditText.requestFocus();
    }

    private void setEditText(EditText editText) {
        // If we already have an EditText, throw an exception
        if (mEditText != null) {
            throw new IllegalArgumentException("We already have an EditText, can only have one");
        }
        mEditText = editText;

        // Update the label visibility with no animation
        updateLabelVisibility(false);

        // Add a TextWatcher so that we know when the text input has changed
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateLabelVisibility(true);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        // Add focus listener to the EditText so that we can notify the label that it is activated.
        // Allows the use of a ColorStateList for the text color on the label
        mEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                updateLabelVisibility(true);
            }
        });

        // If we do not have a valid hint, try and retrieve it from the EditText
        if (TextUtils.isEmpty(mHint)) {
            setHint(mEditText.getHint());
        }

        // If we do not have a valid content description, set it to the hint text.
        if (TextUtils.isEmpty(mEditText.getContentDescription())) {
            mEditText.setContentDescription(mHint);
        }
    }

    private void updateLabelVisibility(boolean animate) {
        boolean hasText = !TextUtils.isEmpty(mEditText.getText());
        boolean isFocused = mEditText.isFocused();

        mLabel.setActivated(isFocused);

        if (hasText || isFocused) {
            // We should be showing the label so do so if it isn't already
            if (mLabel.getVisibility() != VISIBLE) {
                showLabel(animate);
            }
        } else {
            // We should not be showing the label so hide it
            if (mLabel.getVisibility() == VISIBLE) {
                hideLabel(animate);
            }
        }
    }

    /**
     * @return the {@link android.widget.EditText} text input
     */
    public EditText getEditText() {
        return mEditText;
    }

    /**
     * @return the {@link android.widget.TextView} label
     */
    public TextView getLabel() {
        return mLabel;
    }

    /**
     * Set the hint to be displayed in the floating label
     */
    public void setHint(CharSequence hint) {
        mHint = hint;
        mLabel.setText(hint);
    }

    /**
     * Sets the EditText text and shows the floating label without animation. To set the EditText
     * text with animation, use getEditText().setText().
     *
     * @param text The text to display in EditText.
     */
    public void setText(CharSequence text) {
        showLabel(false);
        mEditText.setText(text);
    }

    /**
     * Show the label
     */
    private void showLabel(boolean animate) {
        if (animate) {
            mLabel.setVisibility(View.VISIBLE);
            ViewCompat.setTranslationY(mLabel, mLabel.getHeight());

            float scale = mEditText.getTextSize() / mLabel.getTextSize();
            ViewCompat.setScaleX(mLabel, scale);
            ViewCompat.setScaleY(mLabel, scale);

            ViewCompat.animate(mLabel)
                    .translationY(0f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(ANIMATION_DURATION)
                    .setListener(null)
                    .setInterpolator(mInterpolator).start();
        } else {
            mLabel.setVisibility(VISIBLE);
        }

        mEditText.setHint(null);
    }

    /**
     * Hide the label
     */
    private void hideLabel(boolean animate) {
        if (animate) {
            float scale = mEditText.getTextSize() / mLabel.getTextSize();
            ViewCompat.setScaleX(mLabel, 1f);
            ViewCompat.setScaleY(mLabel, 1f);
            ViewCompat.setTranslationY(mLabel, 0f);

            ViewCompat.animate(mLabel)
                    .translationY(mLabel.getHeight())
                    .setDuration(ANIMATION_DURATION)
                    .scaleX(scale)
                    .scaleY(scale)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(View view) {
                            mLabel.setVisibility(INVISIBLE);
                            mEditText.setHint(mHint);
                        }
                    })
                    .setInterpolator(mInterpolator).start();
        } else {
            mLabel.setVisibility(INVISIBLE);
            mEditText.setHint(mHint);
        }
    }

    /**
     * Helper method to convert dips to pixels.
     */
    private int dipsToPix(float dps) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dps,
                getResources().getDisplayMetrics());
    }
}