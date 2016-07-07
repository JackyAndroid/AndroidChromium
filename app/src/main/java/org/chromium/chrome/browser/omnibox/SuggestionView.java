// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.omnibox.OmniboxResultsAdapter.OmniboxResultItem;
import org.chromium.chrome.browser.omnibox.OmniboxResultsAdapter.OmniboxSuggestionDelegate;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.Locale;

/**
 * Container view for omnibox suggestions made very specific for omnibox suggestions to minimize
 * any unnecessary measures and layouts.
 */
class SuggestionView extends ViewGroup {
    private enum SuggestionIconType {
        BOOKMARK,
        HISTORY,
        GLOBE,
        MAGNIFIER,
        VOICE
    }

    private static final int FIRST_LINE_TEXT_SIZE_SP = 17;
    private static final int SECOND_LINE_TEXT_SIZE_SP = 14;

    private static final long RELAYOUT_DELAY_MS = 20;

    private static final int TITLE_COLOR_STANDARD_FONT_DARK = Color.rgb(51, 51, 51);
    private static final int TITLE_COLOR_STANDARD_FONT_LIGHT = Color.rgb(255, 255, 255);
    private static final int URL_COLOR = Color.rgb(85, 149, 254);

    private static final int ANSWER_IMAGE_HORIZONTAL_SPACING_DP = 4;
    private static final int ANSWER_IMAGE_VERTICAL_SPACING_DP = 5;
    private static final float ANSWER_IMAGE_SCALING_FACTOR = 1.15f;

    private LocationBar mLocationBar;
    private UrlBar mUrlBar;
    private ImageView mNavigationButton;

    private int mSuggestionHeight;
    private int mSuggestionAnswerHeight;

    private OmniboxResultItem mSuggestionItem;
    private OmniboxSuggestion mSuggestion;
    private OmniboxSuggestionDelegate mSuggestionDelegate;
    private Boolean mUseDarkColors;
    private int mPosition;

    private SuggestionContentsContainer mContentsView;

    private int mRefineWidth;
    private View mRefineView;
    private TintedDrawable mRefineIcon;

    private final int[] mViewPositionHolder = new int[2];

    // The offset for the phone's suggestions left-alignment.
    private static final int PHONE_URL_BAR_LEFT_OFFSET_DP = 10;
    private static final int PHONE_URL_BAR_LEFT_OFFSET_RTL_DP = 46;
    // Pre-computed offsets in px.
    private final int mPhoneUrlBarLeftOffsetPx;
    private final int mPhoneUrlBarLeftOffsetRtlPx;

    /**
     * Constructs a new omnibox suggestion view.
     *
     * @param context The context used to construct the suggestion view.
     * @param locationBar The location bar showing these suggestions.
     */
    public SuggestionView(Context context, LocationBar locationBar) {
        super(context);
        mLocationBar = locationBar;

        mSuggestionHeight =
                context.getResources().getDimensionPixelOffset(R.dimen.omnibox_suggestion_height);
        mSuggestionAnswerHeight =
                context.getResources().getDimensionPixelOffset(
                        R.dimen.omnibox_suggestion_answer_height);

        TypedArray a = getContext().obtainStyledAttributes(
                new int [] {R.attr.selectableItemBackground});
        Drawable itemBackground = a.getDrawable(0);
        a.recycle();

        mContentsView = new SuggestionContentsContainer(context, itemBackground);
        addView(mContentsView);

        mRefineView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                if (mRefineIcon == null) return;
                canvas.save();
                canvas.translate(
                        (getMeasuredWidth() - mRefineIcon.getIntrinsicWidth()) / 2f,
                        (getMeasuredHeight() - mRefineIcon.getIntrinsicHeight()) / 2f);
                mRefineIcon.draw(canvas);
                canvas.restore();
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);

                if (visibility == VISIBLE) {
                    setClickable(true);
                    setFocusable(true);
                } else {
                    setClickable(false);
                    setFocusable(false);
                }
            }

            @Override
            protected void drawableStateChanged() {
                super.drawableStateChanged();

                if (mRefineIcon != null && mRefineIcon.isStateful()) {
                    mRefineIcon.setState(getDrawableState());
                }
            }
        };
        mRefineView.setContentDescription(getContext().getString(
                R.string.accessibility_omnibox_btn_refine));

        // Although this has the same background as the suggestion view, it can not be shared as
        // it will result in the state of the drawable being shared and always showing up in the
        // refine view.
        mRefineView.setBackground(itemBackground.getConstantState().newDrawable());
        mRefineView.setId(R.id.refine_view_id);
        mRefineView.setClickable(true);
        mRefineView.setFocusable(true);
        mRefineView.setLayoutParams(new LayoutParams(0, 0));
        addView(mRefineView);

        mRefineWidth = (int) (getResources().getDisplayMetrics().density * 48);

        mUrlBar = (UrlBar) locationBar.getContainerView().findViewById(R.id.url_bar);

        mPhoneUrlBarLeftOffsetPx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                PHONE_URL_BAR_LEFT_OFFSET_DP,
                getContext().getResources().getDisplayMetrics()));
        mPhoneUrlBarLeftOffsetRtlPx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                PHONE_URL_BAR_LEFT_OFFSET_RTL_DP,
                getContext().getResources().getDisplayMetrics()));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getMeasuredWidth() == 0) return;

        if (mSuggestion.getType() != OmniboxSuggestionType.SEARCH_SUGGEST_TAIL) {
            mContentsView.resetTextWidths();
        }

        boolean refineVisible = mRefineView.getVisibility() == VISIBLE;
        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);
        int contentsViewOffsetX = isRtl ? mRefineWidth : 0;
        if (!refineVisible) contentsViewOffsetX = 0;
        mContentsView.layout(
                contentsViewOffsetX,
                0,
                contentsViewOffsetX + mContentsView.getMeasuredWidth(),
                mContentsView.getMeasuredHeight());
        int refineViewOffsetX = isRtl ? 0 : getMeasuredWidth() - mRefineWidth;
        mRefineView.layout(
                refineViewOffsetX,
                0,
                refineViewOffsetX + mRefineWidth,
                mContentsView.getMeasuredHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = mSuggestionHeight;
        if (!TextUtils.isEmpty(mSuggestion.getAnswerContents())) {
            height = mSuggestionAnswerHeight;
        }
        setMeasuredDimension(width, height);

        // The width will be specified as 0 when determining the height of the popup, so exit early
        // after setting the height.
        if (width == 0) return;

        boolean refineVisible = mRefineView.getVisibility() == VISIBLE;
        int refineWidth = refineVisible ? mRefineWidth : 0;
        mContentsView.measure(
                MeasureSpec.makeMeasureSpec(width - refineWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        mContentsView.getLayoutParams().width = mContentsView.getMeasuredWidth();
        mContentsView.getLayoutParams().height = mContentsView.getMeasuredHeight();

        mRefineView.measure(
                MeasureSpec.makeMeasureSpec(mRefineWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        mRefineView.getLayoutParams().width = mRefineView.getMeasuredWidth();
        mRefineView.getLayoutParams().height = mRefineView.getMeasuredHeight();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        mContentsView.invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Whenever the suggestion dropdown is touched, we dispatch onGestureDown which is
        // used to let autocomplete controller know that it should stop updating suggestions.
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) mSuggestionDelegate.onGestureDown();
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Sets the contents and state of the view for the given suggestion.
     *
     * @param suggestionItem The omnibox suggestion item this view represents.
     * @param suggestionDelegate The suggestion delegate.
     * @param position Position of the suggestion in the dropdown list.
     * @param useDarkColors Whether dark colors should be used for fonts and icons.
     */
    public void init(OmniboxResultItem suggestionItem,
            OmniboxSuggestionDelegate suggestionDelegate,
            int position, boolean useDarkColors) {
        ViewCompat.setLayoutDirection(this, ViewCompat.getLayoutDirection(mUrlBar));

        // Update the position unconditionally.
        mPosition = position;
        jumpDrawablesToCurrentState();
        boolean colorsChanged = mUseDarkColors == null || mUseDarkColors != useDarkColors;
        if (suggestionItem.equals(mSuggestionItem) && !colorsChanged) return;
        mUseDarkColors = useDarkColors;
        if (colorsChanged) {
            mContentsView.mTextLine1.setTextColor(getStandardFontColor());
            setRefineIcon(true);
        }

        mSuggestionItem = suggestionItem;
        mSuggestion = suggestionItem.getSuggestion();
        mSuggestionDelegate = suggestionDelegate;
        // Reset old computations.
        mContentsView.resetTextWidths();
        mContentsView.mAnswerImage.setVisibility(GONE);
        mContentsView.mAnswerImage.getLayoutParams().height = 0;
        mContentsView.mAnswerImage.getLayoutParams().width = 0;
        mContentsView.mAnswerImage.setImageDrawable(null);
        mContentsView.mAnswerImageMaxSize = 0;
        mContentsView.mTextLine1.setTextSize(FIRST_LINE_TEXT_SIZE_SP);
        mContentsView.mTextLine2.setTextSize(SECOND_LINE_TEXT_SIZE_SP);

        // Suggestions with attached answers are rendered with rich results regardless of which
        // suggestion type they are.
        if (mSuggestion.hasAnswer()) {
            setAnswer(mSuggestion.getAnswer());
            mContentsView.setSuggestionIcon(SuggestionIconType.MAGNIFIER, colorsChanged);
            mContentsView.mTextLine2.setVisibility(VISIBLE);
            setRefinable(true);
            return;
        }

        boolean sameAsTyped =
                suggestionItem.getMatchedQuery().equalsIgnoreCase(mSuggestion.getDisplayText());
        int suggestionType = mSuggestion.getType();
        if (mSuggestion.isUrlSuggestion()) {
            if (mSuggestion.isStarred()) {
                mContentsView.setSuggestionIcon(SuggestionIconType.BOOKMARK, colorsChanged);
            } else if (suggestionType == OmniboxSuggestionType.HISTORY_URL) {
                mContentsView.setSuggestionIcon(SuggestionIconType.HISTORY, colorsChanged);
            } else {
                mContentsView.setSuggestionIcon(SuggestionIconType.GLOBE, colorsChanged);
            }
            boolean urlShown = !TextUtils.isEmpty(mSuggestion.getUrl());
            boolean urlHighlighted = false;
            if (urlShown) {
                urlHighlighted = setUrlText(suggestionItem);
            } else {
                mContentsView.mTextLine2.setVisibility(INVISIBLE);
            }
            setSuggestedQuery(suggestionItem, true, urlShown, urlHighlighted);
            setRefinable(!sameAsTyped);
        } else {
            SuggestionIconType suggestionIcon = SuggestionIconType.MAGNIFIER;
            if (suggestionType == OmniboxSuggestionType.VOICE_SUGGEST) {
                suggestionIcon = SuggestionIconType.VOICE;
            } else if ((suggestionType == OmniboxSuggestionType.SEARCH_SUGGEST_PERSONALIZED)
                    || (suggestionType == OmniboxSuggestionType.SEARCH_HISTORY)) {
                // Show history icon for suggestions based on user queries.
                suggestionIcon = SuggestionIconType.HISTORY;
            }
            mContentsView.setSuggestionIcon(suggestionIcon, colorsChanged);
            setRefinable(!sameAsTyped);
            setSuggestedQuery(suggestionItem, false, false, false);
            if ((suggestionType == OmniboxSuggestionType.SEARCH_SUGGEST_ENTITY)
                    || (suggestionType == OmniboxSuggestionType.SEARCH_SUGGEST_PROFILE)) {
                showDescriptionLine(
                        SpannableString.valueOf(mSuggestion.getDescription()),
                        getStandardFontColor());
            } else {
                mContentsView.mTextLine2.setVisibility(INVISIBLE);
            }
        }
    }

    private void setRefinable(boolean refinable) {
        if (refinable) {
            mRefineView.setVisibility(VISIBLE);
            mRefineView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Post the refine action to the end of the UI thread to allow the refine view
                    // a chance to update its background selection state.
                    PerformRefineSuggestion performRefine = new PerformRefineSuggestion();
                    if (!post(performRefine)) performRefine.run();
                }
            });
        } else {
            mRefineView.setOnClickListener(null);
            mRefineView.setVisibility(GONE);
        }
    }

    private int getStandardFontColor() {
        return (mUseDarkColors == null || mUseDarkColors)
                ? TITLE_COLOR_STANDARD_FONT_DARK : TITLE_COLOR_STANDARD_FONT_LIGHT;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (selected && !isInTouchMode()) {
            mSuggestionDelegate.onSetUrlToSuggestion(mSuggestion);
        }
    }

    private void setRefineIcon(boolean invalidateIcon) {
        if (!invalidateIcon && mRefineIcon != null) return;

        mRefineIcon = TintedDrawable.constructTintedDrawable(
                getResources(), R.drawable.btn_suggestion_refine);
        mRefineIcon.setTint(ApiCompatibilityUtils.getColorStateList(getResources(),
                mUseDarkColors ? R.color.dark_mode_tint : R.color.light_mode_tint));
        mRefineIcon.setBounds(
                0, 0,
                mRefineIcon.getIntrinsicWidth(),
                mRefineIcon.getIntrinsicHeight());
        mRefineIcon.setState(mRefineView.getDrawableState());
        mRefineView.postInvalidateOnAnimation();
    }

    /**
     * Sets (and highlights) the URL text of the second line of the omnibox suggestion.
     *
     * @param suggestion The suggestion containing the URL.
     * @return Whether the URL was highlighted based on the user query.
     */
    private boolean setUrlText(OmniboxResultItem suggestion) {
        String query = suggestion.getMatchedQuery();
        String url = suggestion.getSuggestion().getFormattedUrl();
        int index = url.indexOf(query);
        Spannable str = SpannableString.valueOf(url);
        if (index >= 0) {
            // Bold the part of the URL that matches the user query.
            str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    index, index + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        showDescriptionLine(str, URL_COLOR);
        return index >= 0;
    }

    /**
     * Sets a description line for the omnibox suggestion.
     *
     * @param str The description text.
     */
    private void showDescriptionLine(Spannable str, int textColor) {
        if (mContentsView.mTextLine2.getVisibility() != VISIBLE) {
            mContentsView.mTextLine2.setVisibility(VISIBLE);
        }
        mContentsView.mTextLine2.setTextColor(textColor);
        mContentsView.mTextLine2.setText(str, BufferType.SPANNABLE);
    }

    /**
     * Sets the text of the first line of the omnibox suggestion.
     *
     * @param suggestionItem The item containing the suggestion data.
     * @param showDescriptionIfPresent Whether to show the description text of the suggestion if
     *                                 the item contains valid data.
     * @param isUrlQuery Whether this suggestion is showing an URL.
     * @param isUrlHighlighted Whether the URL contains any highlighted matching sections.
     */
    private void setSuggestedQuery(
            OmniboxResultItem suggestionItem, boolean showDescriptionIfPresent,
            boolean isUrlQuery, boolean isUrlHighlighted) {
        String userQuery = suggestionItem.getMatchedQuery();
        String suggestedQuery = null;
        OmniboxSuggestion suggestion = suggestionItem.getSuggestion();
        if (showDescriptionIfPresent && !TextUtils.isEmpty(suggestion.getUrl())
                && !TextUtils.isEmpty(suggestion.getDescription())) {
            suggestedQuery = suggestion.getDescription();
        } else {
            suggestedQuery = suggestion.getDisplayText();
        }
        if (suggestedQuery == null) {
            assert false : "Invalid suggestion sent with no displayable text";
            suggestedQuery = "";
        } else if (suggestedQuery.equals(suggestion.getUrl())) {
            // This is a navigation match with the title defaulted to the URL, display formatted URL
            // so that they continue matching.
            suggestedQuery = suggestion.getFormattedUrl();
        }

        if (mSuggestion.getType() == OmniboxSuggestionType.SEARCH_SUGGEST_TAIL) {
            String fillIntoEdit = mSuggestion.getFillIntoEdit();
            // Data sanity checks.
            if (fillIntoEdit.startsWith(userQuery)
                    && fillIntoEdit.endsWith(suggestedQuery)
                    && fillIntoEdit.length() < userQuery.length() + suggestedQuery.length()) {
                String ignoredPrefix = fillIntoEdit.substring(
                        0, fillIntoEdit.length() - suggestedQuery.length());
                final String ellipsisPrefix = "\u2026 ";
                suggestedQuery = ellipsisPrefix + suggestedQuery;
                if (userQuery.startsWith(ignoredPrefix)) {
                    userQuery = ellipsisPrefix + userQuery.substring(ignoredPrefix.length());
                }
                if (DeviceFormFactor.isTablet(getContext())) {
                    TextPaint tp = mContentsView.mTextLine1.getPaint();
                    mContentsView.mRequiredWidth =
                            tp.measureText(fillIntoEdit, 0, fillIntoEdit.length());
                    mContentsView.mMatchContentsWidth =
                            tp.measureText(suggestedQuery, 0, suggestedQuery.length());

                    // Update the max text widths values in SuggestionList. These will be passed to
                    // the contents view on layout.
                    mSuggestionDelegate.onTextWidthsUpdated(
                            mContentsView.mRequiredWidth, mContentsView.mMatchContentsWidth);
                }
            }
        }

        Spannable str = SpannableString.valueOf(suggestedQuery);
        int userQueryIndex = isUrlHighlighted ? -1
                : suggestedQuery.toLowerCase(Locale.getDefault()).indexOf(
                        userQuery.toLowerCase(Locale.getDefault()));
        if (userQueryIndex != -1) {
            int spanStart = 0;
            int spanEnd = 0;
            if (isUrlQuery) {
                spanStart = userQueryIndex;
                spanEnd = userQueryIndex + userQuery.length();
            } else {
                spanStart = userQueryIndex + userQuery.length();
                spanEnd = str.length();
            }
            spanStart = Math.min(spanStart, str.length());
            spanEnd = Math.min(spanEnd, str.length());
            if (spanStart != spanEnd) {
                str.setSpan(
                        new StyleSpan(android.graphics.Typeface.BOLD),
                        spanStart, spanEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        mContentsView.mTextLine1.setText(str, BufferType.SPANNABLE);
    }

    /**
     * Sets both lines of the Omnibox suggestion based on an Answers in Suggest result.
     *
     * @param answer The answer to be displayed.
     */
    private void setAnswer(SuggestionAnswer answer) {
        float density = getResources().getDisplayMetrics().density;

        SuggestionAnswer.ImageLine firstLine = answer.getFirstLine();
        mContentsView.mTextLine1.setTextSize(AnswerTextBuilder.getMaxTextHeightSp(firstLine));
        Spannable firstLineText = AnswerTextBuilder.buildSpannable(
                firstLine, mContentsView.mTextLine1.getPaint().getFontMetrics(), density);
        mContentsView.mTextLine1.setText(firstLineText, BufferType.SPANNABLE);

        SuggestionAnswer.ImageLine secondLine = answer.getSecondLine();
        mContentsView.mTextLine2.setTextSize(AnswerTextBuilder.getMaxTextHeightSp(secondLine));
        Spannable secondLineText = AnswerTextBuilder.buildSpannable(
                secondLine, mContentsView.mTextLine2.getPaint().getFontMetrics(), density);
        mContentsView.mTextLine2.setText(secondLineText, BufferType.SPANNABLE);

        if (secondLine.hasImage()) {
            mContentsView.mAnswerImage.setVisibility(VISIBLE);

            float textSize = mContentsView.mTextLine2.getTextSize();
            int imageSize = (int) (textSize * ANSWER_IMAGE_SCALING_FACTOR);
            mContentsView.mAnswerImage.getLayoutParams().height = imageSize;
            mContentsView.mAnswerImage.getLayoutParams().width = imageSize;
            mContentsView.mAnswerImageMaxSize = imageSize;

            String url = "https:" + secondLine.getImage().replace("\\/", "/");
            AnswersImage.requestAnswersImage(
                    mLocationBar.getCurrentTab().getProfile(),
                    url,
                    new AnswersImage.AnswersImageObserver() {
                        @Override
                        public void onAnswersImageChanged(Bitmap bitmap) {
                            mContentsView.mAnswerImage.setImageBitmap(bitmap);
                        }
                    });
        }
    }

    /**
     * Handles triggering a selection request for the suggestion rendered by this view.
     */
    private class PerformSelectSuggestion implements Runnable {
        @Override
        public void run() {
            mSuggestionDelegate.onSelection(mSuggestion, mPosition);
        }
    }

    /**
     * Handles triggering a refine request for the suggestion rendered by this view.
     */
    private class PerformRefineSuggestion implements Runnable {
        @Override
        public void run() {
            mSuggestionDelegate.onRefineSuggestion(mSuggestion);
        }
    }

    /**
     * Container view for the contents of the suggestion (the search query, URL, and suggestion type
     * icon).
     */
    private class SuggestionContentsContainer extends ViewGroup implements OnLayoutChangeListener {
        private int mSuggestionIconLeft = Integer.MIN_VALUE;
        private int mTextLeft = Integer.MIN_VALUE;
        private int mTextRight = Integer.MIN_VALUE;
        private Drawable mSuggestionIcon;
        private SuggestionIconType mSuggestionIconType;

        private final TextView mTextLine1;
        private final TextView mTextLine2;
        private final ImageView mAnswerImage;

        private int mAnswerImageMaxSize;  // getMaxWidth() is API 16+, so store it locally.
        private float mRequiredWidth;
        private float mMatchContentsWidth;
        private boolean mForceIsFocused;

        private final Runnable mRelayoutRunnable = new Runnable() {
            @Override
            public void run() {
                requestLayout();
            }
        };

        @SuppressLint("InlinedApi")
        SuggestionContentsContainer(Context context, Drawable backgroundDrawable) {
            super(context);

            ApiCompatibilityUtils.setLayoutDirection(this, View.LAYOUT_DIRECTION_INHERIT);

            setBackground(backgroundDrawable);
            setClickable(true);
            setFocusable(true);
            setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, mSuggestionHeight));
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Post the selection action to the end of the UI thread to allow the suggestion
                    // view a chance to update their background selection state.
                    PerformSelectSuggestion performSelection = new PerformSelectSuggestion();
                    if (!post(performSelection)) performSelection.run();
                }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    RecordUserAction.record("MobileOmniboxDeleteGesture");
                    if (!mSuggestion.isDeletable()) return true;

                    AlertDialog.Builder b =
                            new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme);
                    b.setTitle(mSuggestion.getDisplayText());
                    b.setMessage(R.string.omnibox_confirm_delete);
                    DialogInterface.OnClickListener okListener =
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    RecordUserAction.record("MobileOmniboxDeleteRequested");
                                    mSuggestionDelegate.onDeleteSuggestion(mPosition);
                                }
                            };
                    b.setPositiveButton(android.R.string.ok, okListener);
                    DialogInterface.OnClickListener cancelListener =
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            };
                    b.setNegativeButton(android.R.string.cancel, cancelListener);

                    AlertDialog dialog = b.create();
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            mSuggestionDelegate.onHideModal();
                        }
                    });

                    mSuggestionDelegate.onShowModal();
                    dialog.show();
                    return true;
                }
            });

            mTextLine1 = new TextView(context);
            mTextLine1.setLayoutParams(
                    new LayoutParams(LayoutParams.WRAP_CONTENT, mSuggestionHeight));
            mTextLine1.setSingleLine();
            mTextLine1.setTextColor(getStandardFontColor());
            ApiCompatibilityUtils.setTextAlignment(mTextLine1, TEXT_ALIGNMENT_VIEW_START);
            addView(mTextLine1);

            mTextLine2 = new TextView(context);
            mTextLine2.setLayoutParams(
                    new LayoutParams(LayoutParams.WRAP_CONTENT, mSuggestionHeight));
            mTextLine2.setSingleLine();
            mTextLine2.setVisibility(INVISIBLE);
            ApiCompatibilityUtils.setTextAlignment(mTextLine2, TEXT_ALIGNMENT_VIEW_START);
            addView(mTextLine2);

            mAnswerImage = new ImageView(context);
            mAnswerImage.setVisibility(GONE);
            mAnswerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mAnswerImage.setLayoutParams(new LayoutParams(0, 0));
            mAnswerImageMaxSize = 0;
            addView(mAnswerImage);
        }

        private void resetTextWidths() {
            mRequiredWidth = 0;
            mMatchContentsWidth = 0;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (DeviceFormFactor.isTablet(getContext())) {
                // Use the same image transform matrix as the navigation icon to ensure the same
                // scaling, which requires centering vertically based on the height of the
                // navigation icon view and not the image itself.
                canvas.save();
                mSuggestionIconLeft = getSuggestionIconLeftPosition();
                canvas.translate(
                        mSuggestionIconLeft,
                        (getMeasuredHeight() - mNavigationButton.getMeasuredHeight()) / 2f);
                canvas.concat(mNavigationButton.getImageMatrix());
                mSuggestionIcon.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child != mTextLine1 && child != mTextLine2 && child != mAnswerImage) {
                return super.drawChild(canvas, child, drawingTime);
            }

            int height = getMeasuredHeight();
            int line1Height = mTextLine1.getMeasuredHeight();
            int line2Height = mTextLine2.getVisibility() == VISIBLE
                    ? mTextLine2.getMeasuredHeight() : 0;

            int verticalOffset = 0;
            if (line1Height + line2Height > height) {
                // The text lines total height is larger than this view, snap them to the top and
                // bottom of the view.
                if (child == mTextLine1) {
                    verticalOffset = 0;
                } else {
                    verticalOffset = height - line2Height;
                }
            } else {
                // The text lines fit comfortably, so vertically center them.
                verticalOffset = (height - line1Height - line2Height) / 2;
                if (child == mTextLine2) verticalOffset += line1Height;

                // When one line is larger than the other, it contains extra vertical padding. This
                // produces more apparent whitespace above or below the text lines.  Add a small
                // offset to compensate.
                if (line1Height != line2Height) {
                    verticalOffset += (line2Height - line1Height) / 10;
                }

                // The image is positioned vertically aligned with the second text line but
                // requires a small additional offset to align with the ascent of the text instead
                // of the top of the text which includes some whitespace.
                if (child == mAnswerImage) {
                    verticalOffset += ANSWER_IMAGE_VERTICAL_SPACING_DP
                            * getResources().getDisplayMetrics().density;
                }
            }

            canvas.save();
            canvas.translate(0, verticalOffset);
            boolean retVal = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return retVal;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            View locationBarView = mLocationBar.getContainerView();
            if (mUrlBar == null) {
                mUrlBar = (UrlBar) locationBarView.findViewById(R.id.url_bar);
                mUrlBar.addOnLayoutChangeListener(this);
            }
            if (mNavigationButton == null) {
                mNavigationButton =
                        (ImageView) locationBarView.findViewById(R.id.navigation_button);
                mNavigationButton.addOnLayoutChangeListener(this);
            }

            // Align the text to be pixel perfectly aligned with the text in the url bar.
            mTextLeft = getSuggestionTextLeftPosition();
            mTextRight = getSuggestionTextRightPosition();
            boolean isRTL = ApiCompatibilityUtils.isLayoutRtl(this);
            if (DeviceFormFactor.isTablet(getContext())) {
                int textWidth = isRTL ? mTextRight : (r - l - mTextLeft);
                final float maxRequiredWidth = mSuggestionDelegate.getMaxRequiredWidth();
                final float maxMatchContentsWidth = mSuggestionDelegate.getMaxMatchContentsWidth();
                float paddingStart = (textWidth > maxRequiredWidth)
                        ? (mRequiredWidth - mMatchContentsWidth)
                        : Math.max(textWidth - maxMatchContentsWidth, 0);
                ApiCompatibilityUtils.setPaddingRelative(
                        mTextLine1, (int) paddingStart, mTextLine1.getPaddingTop(),
                        0, // TODO(skanuj) : Change to ApiCompatibilityUtils.getPaddingEnd(...).
                        mTextLine1.getPaddingBottom());
            }

            int imageWidth = mAnswerImageMaxSize;
            int imageSpacing = 0;
            if (mAnswerImage.getVisibility() == VISIBLE && imageWidth > 0) {
                float density = getResources().getDisplayMetrics().density;
                imageSpacing = (int) (ANSWER_IMAGE_HORIZONTAL_SPACING_DP * density);
            }
            if (isRTL) {
                mTextLine1.layout(0, t, mTextRight, b);
                mAnswerImage.layout(mTextRight - imageWidth , t, mTextRight, b);
                mTextLine2.layout(0, t, mTextRight - (imageWidth + imageSpacing), b);
            } else {
                mTextLine1.layout(mTextLeft, t, r - l, b);
                mAnswerImage.layout(mTextLeft, t, mTextLeft + imageWidth, b);
                mTextLine2.layout(mTextLeft + imageWidth + imageSpacing, t, r - l, b);
            }

            int suggestionIconPosition = getSuggestionIconLeftPosition();
            if (mSuggestionIconLeft != suggestionIconPosition
                    && mSuggestionIconLeft != Integer.MIN_VALUE) {
                mContentsView.postInvalidateOnAnimation();
            }
            mSuggestionIconLeft = suggestionIconPosition;
        }

        private int getUrlBarLeftOffset() {
            if (DeviceFormFactor.isTablet(getContext())) {
                mUrlBar.getLocationOnScreen(mViewPositionHolder);
                return mViewPositionHolder[0];
            } else {
                return ApiCompatibilityUtils.isLayoutRtl(this) ? mPhoneUrlBarLeftOffsetRtlPx
                        : mPhoneUrlBarLeftOffsetPx;
            }
        }

        /**
         * @return The left offset for the suggestion text.
         */
        private int getSuggestionTextLeftPosition() {
            if (mLocationBar == null) return 0;

            int leftOffset = getUrlBarLeftOffset();
            getLocationOnScreen(mViewPositionHolder);
            return leftOffset + mUrlBar.getPaddingLeft() - mViewPositionHolder[0];
        }

        /**
         * @return The right offset for the suggestion text.
         */
        private int getSuggestionTextRightPosition() {
            if (mLocationBar == null) return 0;

            int leftOffset = getUrlBarLeftOffset();
            getLocationOnScreen(mViewPositionHolder);
            return leftOffset + mUrlBar.getWidth() - mUrlBar.getPaddingRight()
                    - mViewPositionHolder[0];
        }

        /**
         * @return The left offset for the suggestion type icon that aligns it with the url bar.
         */
        private int getSuggestionIconLeftPosition() {
            if (mNavigationButton == null) return 0;

            // Ensure the suggestion icon matches the location of the navigation icon in the omnibox
            // perfectly.
            mNavigationButton.getLocationOnScreen(mViewPositionHolder);
            int navButtonXPosition = mViewPositionHolder[0] + mNavigationButton.getPaddingLeft();

            getLocationOnScreen(mViewPositionHolder);

            return navButtonXPosition - mViewPositionHolder[0];
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);

            if (mTextLine1.getMeasuredWidth() != width
                    || mTextLine1.getMeasuredHeight() != height) {
                mTextLine1.measure(
                        MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(mSuggestionHeight, MeasureSpec.AT_MOST));
            }

            if (mTextLine2.getMeasuredWidth() != width
                    || mTextLine2.getMeasuredHeight() != height) {
                mTextLine2.measure(
                        MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(mSuggestionHeight, MeasureSpec.AT_MOST));
            }
        }

        @Override
        public void invalidate() {
            if (getSuggestionTextLeftPosition() != mTextLeft
                    || getSuggestionTextRightPosition() != mTextRight) {
                // When the text position is changed, it typically is caused by the suggestions
                // appearing while the URL bar on the phone is gaining focus (if you trigger an
                // intent that will result in suggestions being shown before focusing the omnibox).
                // Triggering a relayout will cause any animations to stutter, so we continually
                // push the relayout to end of the UI queue until the animation is complete.
                removeCallbacks(mRelayoutRunnable);
                postDelayed(mRelayoutRunnable, RELAYOUT_DELAY_MS);
            } else {
                super.invalidate();
            }
        }

        @Override
        public boolean isFocused() {
            return mForceIsFocused || super.isFocused();
        }

        @Override
        protected int[] onCreateDrawableState(int extraSpace) {
            // When creating the drawable states, treat selected as focused to get the proper
            // highlight when in non-touch mode (i.e. physical keyboard).  This is because only
            // a single view in a window can have focus, and the these will only appear if
            // the omnibox has focus, so we trick the drawable state into believing it has it.
            mForceIsFocused = isSelected() && !isInTouchMode();
            int[] drawableState = super.onCreateDrawableState(extraSpace);
            mForceIsFocused = false;
            return drawableState;
        }

        private void setSuggestionIcon(SuggestionIconType type, boolean invalidateCurrentIcon) {
            if (mSuggestionIconType == type && !invalidateCurrentIcon) return;

            int drawableId = R.drawable.ic_omnibox_page;
            switch (type) {
                case BOOKMARK:
                    drawableId = R.drawable.btn_star;
                    break;
                case MAGNIFIER:
                    drawableId = R.drawable.ic_suggestion_magnifier;
                    break;
                case HISTORY:
                    drawableId = R.drawable.ic_suggestion_history;
                    break;
                case VOICE:
                    drawableId = R.drawable.btn_mic;
                    break;
                default:
                    break;
            }
            mSuggestionIcon = ApiCompatibilityUtils.getDrawable(getResources(), drawableId);
            mSuggestionIcon.setColorFilter(mUseDarkColors
                    ? ApiCompatibilityUtils.getColor(getResources(), R.color.light_normal_color)
                    : Color.WHITE, PorterDuff.Mode.SRC_IN);
            mSuggestionIcon.setBounds(
                    0, 0,
                    mSuggestionIcon.getIntrinsicWidth(),
                    mSuggestionIcon.getIntrinsicHeight());
            mSuggestionIconType = type;
            invalidate();
        }

        @Override
        public void onLayoutChange(
                View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            boolean needsInvalidate = false;
            if (v == mNavigationButton) {
                if (mSuggestionIconLeft != getSuggestionIconLeftPosition()
                        && mSuggestionIconLeft != Integer.MIN_VALUE) {
                    needsInvalidate = true;
                }
            } else {
                if (mTextLeft != getSuggestionTextLeftPosition()
                        && mTextLeft != Integer.MIN_VALUE) {
                    needsInvalidate = true;
                }
                if (mTextRight != getSuggestionTextRightPosition()
                        && mTextRight != Integer.MIN_VALUE) {
                    needsInvalidate = true;
                }
            }
            if (needsInvalidate) {
                removeCallbacks(mRelayoutRunnable);
                postDelayed(mRelayoutRunnable, RELAYOUT_DELAY_MS);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (mNavigationButton != null) mNavigationButton.addOnLayoutChangeListener(this);
            if (mUrlBar != null) mUrlBar.addOnLayoutChangeListener(this);
            if (mLocationBar != null) {
                mLocationBar.getContainerView().addOnLayoutChangeListener(this);
            }
            getRootView().addOnLayoutChangeListener(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (mNavigationButton != null) mNavigationButton.removeOnLayoutChangeListener(this);
            if (mUrlBar != null) mUrlBar.removeOnLayoutChangeListener(this);
            if (mLocationBar != null) {
                mLocationBar.getContainerView().removeOnLayoutChangeListener(this);
            }
            getRootView().removeOnLayoutChangeListener(this);

            super.onDetachedFromWindow();
        }
    }
}
