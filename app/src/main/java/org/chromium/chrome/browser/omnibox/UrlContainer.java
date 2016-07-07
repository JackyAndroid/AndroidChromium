// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LayoutDirection;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * Widget that handles displaying the URL.  Adds the capability to show the trailing portion
 * of the URL in a separate text widget for animations.
 */
public class UrlContainer extends ViewGroup {
    private static final int MIN_TRAILING_TEXT_SHOW_DURATION_MS = 3000;
    private static final int MAX_TRAILING_TEXT_SHOW_DURATION_MS = 6000;
    private static final int TRAILING_TEXT_ANIMATION_DURATION_MS = 200;
    private static final float TRAILING_TEXT_TRANSLATION_X = 50f;

    private final Runnable mTriggerHideRunnable;
    private final Runnable mTriggerHideAnimationRunnable;

    private UrlBar mUrlBarView;
    private TextView mTrailingTextView;

    private int mUrlBarTextWidth;
    private boolean mShowTrailingText;
    private Animator mTrailingTextAnimator;
    private long mLastShowRequestTime;
    private boolean mUseDarkColors;

    private boolean mUrlBarHasFocus;
    private boolean mTrailingTextShownWhileFocused;
    private boolean mTrailingTextHiddenWhileFocused;

    /**
     * Constructor used to inflate from XML.
     */
    public UrlContainer(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTriggerHideRunnable = new Runnable() {
            @Override
            public void run() {
                setTrailingTextVisible(false);
            }
        };
        mTriggerHideAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                hideTrailingText();
            }
        };

        mUseDarkColors = true;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mUrlBarView = (UrlBar) findViewById(R.id.url_bar);
        assert mUrlBarView != null : "url_bar is not defined as a child.";

        mTrailingTextView = (TextView) findViewById(R.id.trailing_text);
        assert mTrailingTextView != null : "trailing_text is not defined as a child.";

        setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        mUrlBarView.measure(
                MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY), heightMeasureSpec);

        if (TextUtils.isEmpty(mUrlBarView.getText())) {
            mUrlBarTextWidth = specWidth;
        } else if (mUrlBarView.hasFocus()) {
            mUrlBarTextWidth = specWidth;
        } else {
            mUrlBarTextWidth = (int) Math.ceil(Math.min(specWidth,
                    Layout.getDesiredWidth(mUrlBarView.getText(), mUrlBarView.getPaint())));
        }
        mTrailingTextView.measure(
                MeasureSpec.makeMeasureSpec(specWidth - mUrlBarTextWidth, MeasureSpec.EXACTLY),
                heightMeasureSpec);
    }

    @SuppressLint("NewApi")
    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        // Layout direction is driven by the text direction of the URL bar, so we just force
        // the path text view to match.
        switch (layoutDirection) {
            case LayoutDirection.LTR:
                ApiCompatibilityUtils.setTextDirection(mTrailingTextView, TEXT_DIRECTION_LTR);
                break;
            case LayoutDirection.RTL:
                ApiCompatibilityUtils.setTextDirection(mTrailingTextView, TEXT_DIRECTION_RTL);
                break;
            case LayoutDirection.LOCALE:
                ApiCompatibilityUtils.setTextDirection(mTrailingTextView, TEXT_DIRECTION_LOCALE);
                break;
            case LayoutDirection.INHERIT:
            default:
                ApiCompatibilityUtils.setTextDirection(mTrailingTextView, TEXT_DIRECTION_INHERIT);
                break;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int height = b - t;
        layoutChild(mUrlBarView, height, 0);
        if (ApiCompatibilityUtils.isLayoutRtl(mUrlBarView)) {
            layoutChild(mTrailingTextView, height, 0);
        } else {
            layoutChild(mTrailingTextView, height, mUrlBarTextWidth);
        }
    }

    private static void layoutChild(View view, int parentHeight, int childLeft) {
        int childTop = (parentHeight - view.getMeasuredHeight()) / 2;
        view.layout(
                childLeft,
                childTop,
                childLeft + view.getMeasuredWidth(),
                childTop + view.getMeasuredHeight());
    }

    /**
     * Sets the text to be displayed.
     *
     * @param displayText The primary display text to be shown.
     * @param trailingText The additional trailing text to be shown after the display text.
     * @param originalText The text that will be displayed if no display text is specified, and
     *                     is also supplied to features such as cut and copy.
     * @return Whether any components of the URL have changed.
     */
    public boolean setUrlText(String displayText, String trailingText, String originalText) {
        boolean changed = mUrlBarView.setUrl(originalText, displayText);
        if (changed && mTrailingTextView.getVisibility() == VISIBLE) requestLayout();

        if (trailingText == null) trailingText = "";
        if (!trailingText.equals(mTrailingTextView.getText())) {
            mTrailingTextView.setText(trailingText);
            changed |= true;
        }
        return changed;
    }

    /**
     * Specifies whether the trailing URL text should use dark text colors or light colors.
     * @param useDarkColors Whether the text colors should be dark (i.e. appropriate for use
     *                      on a light background).
     */
    public void setUseDarkTextColors(boolean useDarkColors) {
        mUseDarkColors = useDarkColors;
        mUrlBarView.setUseDarkTextColors(mUseDarkColors);

        int trailingTextColorId = R.color.url_emphasis_non_emphasized_text;
        if (!mUseDarkColors) {
            trailingTextColorId = R.color.url_emphasis_light_non_emphasized_text;
        }
        mTrailingTextView.setTextColor(ApiCompatibilityUtils.getColor(getResources(),
                trailingTextColorId));
    }

    /**
     * Updates the visibility of the trailing text view.
     * @param visible Whether the trailing text view should be visible.
     */
    public void setTrailingTextVisible(boolean visible) {
        if (mUrlBarHasFocus) {
            if (visible) {
                mTrailingTextShownWhileFocused = true;
                mTrailingTextHiddenWhileFocused = false;
            } else {
                assert mTrailingTextShownWhileFocused;
                mTrailingTextHiddenWhileFocused = true;
            }
            return;
        }
        if (visible) mLastShowRequestTime = SystemClock.uptimeMillis();

        if (visible == mShowTrailingText) return;
        mShowTrailingText = visible;

        removeCallbacks(mTriggerHideRunnable);
        removeCallbacks(mTriggerHideAnimationRunnable);

        if (visible) {
            if (mTrailingTextAnimator != null && mTrailingTextAnimator.isRunning()) {
                mTrailingTextAnimator.cancel();
            } else {
                float translationX = MathUtils.flipSignIf(
                        TRAILING_TEXT_TRANSLATION_X, ApiCompatibilityUtils.isLayoutRtl(this));
                mTrailingTextView.setAlpha(0f);
                mTrailingTextView.setTranslationX(translationX);
            }
            // Canceling the other animation will set the visibility to GONE, so only update
            // this to VISIBLE after the above call to cancel().
            mTrailingTextView.setVisibility(VISIBLE);

            if (!TextUtils.isEmpty(mTrailingTextView.getText())) {
                mUrlBarView.setAccessibilityTextOverride(
                        mUrlBarView.getText().toString() + mTrailingTextView.getText().toString());
            }

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(mTrailingTextView, ALPHA, 1f),
                    ObjectAnimator.ofFloat(mTrailingTextView, TRANSLATION_X, 0f));
            set.setDuration(TRAILING_TEXT_ANIMATION_DURATION_MS);
            set.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mTrailingTextAnimator = null;
                }
            });
            set.start();
            mTrailingTextAnimator = set;

            postDelayed(mTriggerHideRunnable, MAX_TRAILING_TEXT_SHOW_DURATION_MS);
        } else {
            long elapsedTime = SystemClock.uptimeMillis() - mLastShowRequestTime;
            if (elapsedTime >= MIN_TRAILING_TEXT_SHOW_DURATION_MS) {
                hideTrailingText();
            } else {
                postDelayed(mTriggerHideAnimationRunnable,
                        MIN_TRAILING_TEXT_SHOW_DURATION_MS - elapsedTime);
            }
        }
    }

    private void hideTrailingText() {
        if (mTrailingTextAnimator != null && mTrailingTextAnimator.isRunning()) {
            mTrailingTextAnimator.cancel();
        }

        mUrlBarView.setAccessibilityTextOverride(null);

        float translationX = MathUtils.flipSignIf(
                TRAILING_TEXT_TRANSLATION_X, ApiCompatibilityUtils.isLayoutRtl(this));

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(mTrailingTextView, ALPHA, 0f),
                ObjectAnimator.ofFloat(mTrailingTextView, TRANSLATION_X, translationX));
        set.setDuration(TRAILING_TEXT_ANIMATION_DURATION_MS);
        set.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mTrailingTextView.setVisibility(GONE);
                mTrailingTextAnimator = null;
            }
        });
        set.start();
        mTrailingTextAnimator = set;
    }

    /**
     * Updates the focus state of the url bar.
     * @param hasFocus Whether the url bar has focus.
     */
    void onUrlFocusChanged(boolean hasFocus) {
        mUrlBarHasFocus = hasFocus;

        if (mUrlBarHasFocus) {
            mTrailingTextShownWhileFocused = mShowTrailingText
                    || mTrailingTextView.getVisibility() == VISIBLE;
            mTrailingTextHiddenWhileFocused = !mShowTrailingText
                    && mTrailingTextView.getVisibility() == VISIBLE;

            removeCallbacks(mTriggerHideRunnable);
            removeCallbacks(mTriggerHideAnimationRunnable);
            if (mTrailingTextAnimator != null && mTrailingTextAnimator.isRunning()) {
                mTrailingTextAnimator.cancel();
                mTrailingTextAnimator = null;
            }
            mShowTrailingText = false;
        } else {
            if (mTrailingTextShownWhileFocused) {
                setTrailingTextVisible(true);
                if (mTrailingTextHiddenWhileFocused) {
                    setTrailingTextVisible(false);
                }
            }
        }
    }
}
