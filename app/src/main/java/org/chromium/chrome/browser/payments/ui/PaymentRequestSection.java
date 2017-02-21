// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.DualControlLayout;
import org.chromium.chrome.browser.widget.TintedDrawable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Represents a single section in the {@link PaymentRequestUI} that flips between multiple states.
 *
 * The row is broken up into three major, vertically-centered sections:
 * .............................................................................................
 * . TITLE                                                          |                | CHEVRON .
 * .................................................................|                |    or   .
 * . LEFT SUMMARY TEXT                        |  RIGHT SUMMARY TEXT |           LOGO |   ADD   .
 * .................................................................|                |    or   .
 * . MAIN SECTION CONTENT                                           |                |  SELECT .
 * .............................................................................................
 *
 * 1) MAIN CONTENT
 *    The main content is on the left side of the UI.  This includes the title of the section and
 *    two bits of optional summary text.  Subclasses may extend this class to append more controls
 *    via the {@link #createMainSectionContent} function.
 *
 * 2) LOGO
 *    Displays an optional logo (e.g. a credit card image) that floats to the right of the main
 *    content.
 *
 * 3) CHEVRON or ADD or SELECT
 *    Drawn to indicate that the current section may be expanded.  Displayed only when the view is
 *    in the {@link #DISPLAY_MODE_EXPANDABLE} state and only if an ADD or SELECT button isn't shown.
 *
 * There are three states that the UI may flip between; see {@link #DISPLAY_MODE_NORMAL},
 * {@link #DISPLAY_MODE_EXPANDABLE}, and {@link #DISPLAY_MODE_FOCUSED} for details.
 */
public abstract class PaymentRequestSection extends LinearLayout implements View.OnClickListener {
    public static final String TAG = "PaymentRequestUI";

    /** Handles clicks on the widgets and providing data to the PaymentsRequestSection. */
    public interface SectionDelegate extends View.OnClickListener {
        /**
         * Called when the user selects a radio button option from an {@link OptionSection}.
         *
         * @param section Section that was changed.
         * @param option  {@link PaymentOption} that was selected.
         */
        void onPaymentOptionChanged(PaymentRequestSection section, PaymentOption option);

        /** Called when the user requests adding a new PaymentOption to a given section. */
        void onAddPaymentOption(PaymentRequestSection section);

        /** Checks whether or not the text should be formatted with a bold label. */
        boolean isBoldLabelNeeded(PaymentRequestSection section);

        /** Checks whether or not the user should be allowed to click on controls. */
        boolean isAcceptingUserInput();

        /** Returns any additional text that needs to be displayed. */
        @Nullable String getAdditionalText(PaymentRequestSection section);

        /** Returns true if the additional text should be stylized as a warning instead of info. */
        boolean isAdditionalTextDisplayingWarning(PaymentRequestSection section);

        /** Called when a section has been clicked. */
        void onSectionClicked(PaymentRequestSection section);
    }

    /** Edit button mode: Hide the button. */
    public static final int EDIT_BUTTON_GONE = 0;

    /** Edit button mode: Indicate that the section requires a selection. */
    public static final int EDIT_BUTTON_SELECT = 1;

    /** Edit button mode: Indicate that the section requires adding an option. */
    public static final int EDIT_BUTTON_ADD = 2;

    /** Normal mode: White background, displays the item assuming the user accepts it as is. */
    static final int DISPLAY_MODE_NORMAL = 3;

    /** Editable mode: White background, displays the item with an edit chevron. */
    static final int DISPLAY_MODE_EXPANDABLE = 4;

    /** Focused mode: Gray background, more padding, no edit chevron. */
    static final int DISPLAY_MODE_FOCUSED = 5;

    /** Checking mode: Gray background, spinner overlay hides everything except the title. */
    static final int DISPLAY_MODE_CHECKING = 6;

    protected final SectionDelegate mDelegate;
    protected final int mLargeSpacing;
    protected final Button mEditButtonView;
    protected final boolean mIsLayoutInitialized;

    protected int mDisplayMode = DISPLAY_MODE_NORMAL;

    private final int mVerticalSpacing;
    private final int mFocusedBackgroundColor;
    private final LinearLayout mMainSection;
    private final ImageView mLogoView;
    private final ImageView mChevronView;

    private TextView mTitleView;
    private LinearLayout mSummaryLayout;
    private TextView mSummaryLeftTextView;
    private TextView mSummaryRightTextView;

    private int mLogoResourceId;
    private boolean mIsSummaryAllowed = true;

    /**
     * Constructs a PaymentRequestSection.
     *
     * @param context     Context to pull resources from.
     * @param sectionName Title of the section to display.
     * @param delegate    Delegate to alert when something changes in the dialog.
     */
    private PaymentRequestSection(Context context, String sectionName, SectionDelegate delegate) {
        super(context);
        mDelegate = delegate;
        setOnClickListener(delegate);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        // Set the styling of the view.
        mFocusedBackgroundColor = ApiCompatibilityUtils.getColor(
                getResources(), R.color.payments_section_edit_background);
        mLargeSpacing =
                getResources().getDimensionPixelSize(R.dimen.payments_section_large_spacing);
        mVerticalSpacing =
                getResources().getDimensionPixelSize(R.dimen.payments_section_vertical_spacing);
        setPadding(mLargeSpacing, mVerticalSpacing, mLargeSpacing, mVerticalSpacing);

        // Create the main content.
        mMainSection = prepareMainSection(sectionName);
        mLogoView = isLogoNecessary() ? createAndAddLogoView(this, 0, mLargeSpacing) : null;
        mEditButtonView = createAndAddEditButton(this);
        mChevronView = createAndAddChevron(this);
        mIsLayoutInitialized = true;
        setDisplayMode(DISPLAY_MODE_NORMAL);
    }

    /**
     * Sets what logo should be displayed.
     *
     * @param resourceId ID of the logo to display.
     */
    protected void setLogoResource(int resourceId) {
        assert isLogoNecessary();
        mLogoResourceId = resourceId;
        mLogoView.setImageResource(resourceId);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Allow touches to propagate to children only if the layout can be interacted with.
        return !mDelegate.isAcceptingUserInput();
    }

    @Override
    public final void onClick(View v) {
        if (!mDelegate.isAcceptingUserInput()) return;

        // Handle clicking on "ADD" or "SELECT".
        if (v == mEditButtonView) {
            if (getEditButtonState() == EDIT_BUTTON_ADD) {
                mDelegate.onAddPaymentOption(this);
            } else {
                mDelegate.onSectionClicked(this);
            }
            return;
        }

        handleClick(v);
        updateControlLayout();
    }

    /** Handles clicks on the PaymentRequestSection. */
    protected void handleClick(View v) { }

    /**
     * Called when the UI is telling the section that it has either gained or lost focus.
     */
    public void focusSection(boolean shouldFocus) {
        setDisplayMode(shouldFocus ? DISPLAY_MODE_FOCUSED : DISPLAY_MODE_EXPANDABLE);
    }

    /**
     * Updates what Views are displayed and how they look.
     *
     * @param displayMode What mode the widget is being displayed in.
     */
    public void setDisplayMode(int displayMode) {
        mDisplayMode = displayMode;
        updateControlLayout();
    }

    /**
     * Changes what is being displayed in the summary.
     *
     * @param leftText  Text to display on the left side.  If null, the whole row hides.
     * @param rightText Text to display on the right side.  If null, only the right View hides.
     */
    public void setSummaryText(
            @Nullable CharSequence leftText, @Nullable CharSequence rightText) {
        mSummaryLeftTextView.setText(leftText);
        mSummaryRightTextView.setText(rightText);
        mSummaryRightTextView.setVisibility(TextUtils.isEmpty(rightText) ? GONE : VISIBLE);
        updateControlLayout();
    }

    /**
     * Sets how the summary text should be displayed.
     *
     * @param leftTruncate How to truncate the left summary text.  Set to null to clear.
     * @param rightTruncate How to truncate the right summary text.  Set to null to clear.
     */
    public void setSummaryProperties(@Nullable TruncateAt leftTruncate, boolean leftIsSingleLine,
            @Nullable TruncateAt rightTruncate, boolean rightIsSingleLine) {
        mSummaryLeftTextView.setEllipsize(leftTruncate);
        mSummaryLeftTextView.setSingleLine(leftIsSingleLine);

        mSummaryRightTextView.setEllipsize(rightTruncate);
        mSummaryRightTextView.setSingleLine(rightIsSingleLine);
    }

    /**
     * Subclasses may override this method to add additional controls to the layout.
     *
     * @param mainSectionLayout Layout containing all of the main content of the section.
     */
    protected abstract void createMainSectionContent(LinearLayout mainSectionLayout);

    /**
     * Sets whether the edit button may be interacted with.
     *
     * @param isEnabled Whether the button may be interacted with.
     */
    public void setIsEditButtonEnabled(boolean isEnabled) {
        mEditButtonView.setEnabled(isEnabled);
    }

    /**
     * Sets whether the summary text can be displayed.
     *
     * @param isAllowed Whether to display the summary text when needed.
     */
    protected void setIsSummaryAllowed(boolean isAllowed) {
        mIsSummaryAllowed = isAllowed;
    }

    /** @return Whether or not the logo should be displayed. */
    protected boolean isLogoNecessary() {
        return false;
    }

    /**
     * Returns the state of the edit button, which is hidden by default.
     *
     * @return State of the edit button.
     */
    public int getEditButtonState() {
        return EDIT_BUTTON_GONE;
    }

    /**
     * Creates the main section.  Subclasses must call super#createMainSection() immediately to
     * guarantee that Views are added in the correct order.
     *
     * @param sectionName Title to display for the section.
     */
    private LinearLayout prepareMainSection(String sectionName) {
        // The main section is a vertical linear layout that subclasses can append to.
        LinearLayout mainSectionLayout = new LinearLayout(getContext());
        mainSectionLayout.setOrientation(VERTICAL);
        LinearLayout.LayoutParams mainParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT);
        mainParams.weight = 1;
        addView(mainSectionLayout, mainParams);

        // The title is always displayed for the row at the top of the main section.
        mTitleView = new TextView(getContext());
        mTitleView.setText(sectionName);
        ApiCompatibilityUtils.setTextAppearance(
                mTitleView, R.style.PaymentsUiSectionHeader);
        mainSectionLayout.addView(
                mTitleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Create the two TextViews for showing the summary text.
        mSummaryLeftTextView = new TextView(getContext());
        mSummaryLeftTextView.setId(R.id.payments_left_summary_label);
        ApiCompatibilityUtils.setTextAppearance(
                mSummaryLeftTextView, R.style.PaymentsUiSectionDefaultText);

        mSummaryRightTextView = new TextView(getContext());
        ApiCompatibilityUtils.setTextAppearance(
                mSummaryRightTextView, R.style.PaymentsUiSectionDefaultText);
        ApiCompatibilityUtils.setTextAlignment(mSummaryRightTextView, TEXT_ALIGNMENT_TEXT_END);

        // The main TextView sucks up all the available space.
        LinearLayout.LayoutParams leftLayoutParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT);
        leftLayoutParams.weight = 1;

        LinearLayout.LayoutParams rightLayoutParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ApiCompatibilityUtils.setMarginStart(
                rightLayoutParams,
                getContext().getResources().getDimensionPixelSize(
                        R.dimen.payments_section_small_spacing));

        // The summary section displays up to two TextViews side by side.
        mSummaryLayout = new LinearLayout(getContext());
        mSummaryLayout.addView(mSummaryLeftTextView, leftLayoutParams);
        mSummaryLayout.addView(mSummaryRightTextView, rightLayoutParams);
        mainSectionLayout.addView(mSummaryLayout, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setSummaryText(null, null);

        createMainSectionContent(mainSectionLayout);
        return mainSectionLayout;
    }

    private static ImageView createAndAddLogoView(
            ViewGroup parent, int resourceId, int startMargin) {
        ImageView view = new ImageView(parent.getContext());
        view.setBackgroundResource(R.drawable.payments_ui_logo_bg);
        if (resourceId != 0) view.setImageResource(resourceId);

        // The logo has a pre-defined height and width.
        LayoutParams params = new LayoutParams(
                parent.getResources().getDimensionPixelSize(R.dimen.payments_section_logo_width),
                parent.getResources().getDimensionPixelSize(R.dimen.payments_section_logo_height));
        ApiCompatibilityUtils.setMarginStart(params, startMargin);
        parent.addView(view, params);
        return view;
    }

    private Button createAndAddEditButton(ViewGroup parent) {
        Resources resources = parent.getResources();
        Button view = DualControlLayout.createButtonForLayout(
                parent.getContext(), true, resources.getString(R.string.select), this);
        view.setId(R.id.payments_section);

        LayoutParams params =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ApiCompatibilityUtils.setMarginStart(params, mLargeSpacing);
        parent.addView(view, params);
        return view;
    }

    private ImageView createAndAddChevron(ViewGroup parent) {
        Resources resources = parent.getResources();
        TintedDrawable chevron = TintedDrawable.constructTintedDrawable(
                resources, R.drawable.ic_expanded, R.color.payments_section_chevron);

        ImageView view = new ImageView(parent.getContext());
        view.setImageDrawable(chevron);

        // Wrap whatever image is passed in.
        LayoutParams params =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ApiCompatibilityUtils.setMarginStart(params, mLargeSpacing);
        parent.addView(view, params);
        return view;
    }

    /**
     * Called when the section's controls need to be updated after configuration changes.
     *
     * Because of the complicated special casing of what controls hide other controls, all calls to
     * update just one of the controls causes the visibility logic to trigger for all of them.
     *
     * Subclasses should call the super method after they update their own controls.
     */
    protected void updateControlLayout() {
        if (!mIsLayoutInitialized) return;

        boolean isExpanded =
                mDisplayMode == DISPLAY_MODE_FOCUSED || mDisplayMode == DISPLAY_MODE_CHECKING;
        setBackgroundColor(isExpanded ? mFocusedBackgroundColor : Color.WHITE);

        // Update whether the logo is displayed.
        if (mLogoView != null) {
            boolean show = mLogoResourceId != 0 && mDisplayMode != DISPLAY_MODE_FOCUSED;
            mLogoView.setVisibility(show ? VISIBLE : GONE);
        }

        // The button takes precedence over the summary text and the chevron.
        int editButtonState = getEditButtonState();
        if (editButtonState == EDIT_BUTTON_GONE) {
            mEditButtonView.setVisibility(GONE);
            mChevronView.setVisibility(
                    mDisplayMode == DISPLAY_MODE_EXPANDABLE ? VISIBLE : GONE);

            // Update whether the summary is displayed.
            boolean showSummary =
                    mIsSummaryAllowed && !TextUtils.isEmpty(mSummaryLeftTextView.getText());
            mSummaryLayout.setVisibility(showSummary ? VISIBLE : GONE);
        } else {
            // Show the edit button and hide the chevron and the summary.
            boolean isButtonAllowed = mDisplayMode == DISPLAY_MODE_EXPANDABLE
                    || mDisplayMode == DISPLAY_MODE_NORMAL;
            mSummaryLayout.setVisibility(GONE);
            mChevronView.setVisibility(GONE);
            mEditButtonView.setVisibility(isButtonAllowed ? VISIBLE : GONE);
            mEditButtonView.setText(
                    editButtonState == EDIT_BUTTON_SELECT ? R.string.select : R.string.add);
        }

        // The title gains extra spacing when there is another visible view in the main section.
        int numVisibleMainViews = 0;
        for (int i = 0; i < mMainSection.getChildCount(); i++) {
            if (mMainSection.getChildAt(i).getVisibility() == VISIBLE) numVisibleMainViews += 1;
        }

        boolean isTitleMarginNecessary = numVisibleMainViews > 1 && isExpanded;
        int oldMargin =
                ((ViewGroup.MarginLayoutParams) mTitleView.getLayoutParams()).bottomMargin;
        int newMargin = isTitleMarginNecessary ? mVerticalSpacing : 0;

        if (oldMargin != newMargin) {
            ((ViewGroup.MarginLayoutParams) mTitleView.getLayoutParams()).bottomMargin =
                    newMargin;
            requestLayout();
        }
    }

    /**
     * Section with a secondary TextView beneath the summary to show additional details.
     *
     * ............................................................................
     * . TITLE                                                          | CHEVRON .
     * .................................................................|    or   .
     * . LEFT SUMMARY TEXT                        |  RIGHT SUMMARY TEXT |   ADD   .
     * .................................................................|    or   .
     * . EXTRA TEXT                                                     |  SELECT .
     * ............................................................................
     */
    public static class ExtraTextSection extends PaymentRequestSection {
        private TextView mExtraTextView;
        private int mEditButtonState = EDIT_BUTTON_GONE;

        public ExtraTextSection(Context context, String sectionName, SectionDelegate delegate) {
            super(context, sectionName, delegate);
            setExtraText(null);
        }

        @Override
        protected void createMainSectionContent(LinearLayout mainSectionLayout) {
            Context context = mainSectionLayout.getContext();

            mExtraTextView = new TextView(context);
            ApiCompatibilityUtils.setTextAppearance(
                    mExtraTextView, R.style.PaymentsUiSectionDescriptiveTextEndAligned);
            mainSectionLayout.addView(mExtraTextView, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        /**
         * Sets the CharSequence that is displayed in the secondary TextView.
         *
         * @param text Text to display.
         */
        public void setExtraText(CharSequence text) {
            mExtraTextView.setText(text);
            mExtraTextView.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        }

        /** Sets the state of the edit button. */
        public void setEditButtonState(int state) {
            mEditButtonState = state;
            updateControlLayout();
        }

        @Override
        public int getEditButtonState() {
            return mEditButtonState;
        }
    }

    /**
     * Section with an additional Layout for showing a total and how it is broken down.
     *
     * Normal mode:     Just the summary is displayed.
     *                  If no option is selected, the "empty label" is displayed in its place.
     * Expandable mode: Same as Normal, but shows the chevron.
     * Focused mode:    Hides the summary and chevron, then displays the full set of options.
     *
     * ............................................................................
     * . TITLE                                                          |         .
     * .................................................................| CHERVON .
     * . LEFT SUMMARY TEXT                        |  RIGHT SUMMARY TEXT |    or   .
     * .................................................................|   ADD   .
     * .                                      | Line item 1 |    $13.99 |    or   .
     * .                                      | Line item 2 |      $.99 |  SELECT .
     * .                                      | Line item 3 |     $2.99 |         .
     * ............................................................................
     */
    public static class LineItemBreakdownSection extends PaymentRequestSection {
        private GridLayout mBreakdownLayout;

        public LineItemBreakdownSection(
                Context context, String sectionName, SectionDelegate delegate) {
            super(context, sectionName, delegate);
        }

        @Override
        protected void createMainSectionContent(LinearLayout mainSectionLayout) {
            Context context = mainSectionLayout.getContext();

            // The breakdown is represented by an end-aligned GridLayout that takes up only as much
            // space as it needs.  The GridLayout ensures a consistent margin between the columns.
            mBreakdownLayout = new GridLayout(context);
            mBreakdownLayout.setColumnCount(2);
            LayoutParams breakdownParams =
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            breakdownParams.gravity = Gravity.END;
            mainSectionLayout.addView(mBreakdownLayout, breakdownParams);
        }

        /**
         * Updates the total and how it's broken down.
         *
         * @param cart The shopping cart contents and the total.
         */
        public void update(ShoppingCart cart) {
            Context context = mBreakdownLayout.getContext();

            // Update the summary to display information about the total.
            setSummaryText(cart.getTotal().getLabel(), createValueString(
                    cart.getTotal().getCurrency(), cart.getTotal().getPrice(), true));

            mBreakdownLayout.removeAllViews();
            if (cart.getContents() == null) return;

            int maximumDescriptionWidthPx =
                    ((View) mBreakdownLayout.getParent()).getWidth() * 2 / 3;

            // Update the breakdown, using one row per {@link LineItem}.
            int numItems = cart.getContents().size();
            mBreakdownLayout.setRowCount(numItems);
            for (int i = 0; i < numItems; i++) {
                LineItem item = cart.getContents().get(i);

                TextView description = new TextView(context);
                ApiCompatibilityUtils.setTextAppearance(
                        description, R.style.PaymentsUiSectionDescriptiveTextEndAligned);
                description.setText(item.getLabel());
                description.setEllipsize(TruncateAt.END);
                description.setMaxLines(2);
                if (maximumDescriptionWidthPx > 0) {
                    description.setMaxWidth(maximumDescriptionWidthPx);
                }

                TextView amount = new TextView(context);
                ApiCompatibilityUtils.setTextAppearance(
                        amount, R.style.PaymentsUiSectionDescriptiveTextEndAligned);
                amount.setText(createValueString(item.getCurrency(), item.getPrice(), false));

                // Each item is represented by a row in the GridLayout.
                GridLayout.LayoutParams descriptionParams = new GridLayout.LayoutParams(
                        GridLayout.spec(i, 1, GridLayout.END),
                        GridLayout.spec(0, 1, GridLayout.END));
                GridLayout.LayoutParams amountParams = new GridLayout.LayoutParams(
                        GridLayout.spec(i, 1, GridLayout.END),
                        GridLayout.spec(1, 1, GridLayout.END));
                ApiCompatibilityUtils.setMarginStart(amountParams,
                        context.getResources().getDimensionPixelSize(
                                R.dimen.payments_section_descriptive_item_spacing));

                mBreakdownLayout.addView(description, descriptionParams);
                mBreakdownLayout.addView(amount, amountParams);
            }
        }

        /**
         * Builds a CharSequence that displays a value in a particular currency.
         *
         * @param currency    Currency of the value being displayed.
         * @param value       Value to display.
         * @param isValueBold Whether or not to bold the item.
         * @return CharSequence that represents the whole value.
         */
        private CharSequence createValueString(String currency, String value, boolean isValueBold) {
            SpannableStringBuilder valueBuilder = new SpannableStringBuilder();
            valueBuilder.append(currency);
            valueBuilder.append(" ");

            int boldStartIndex = valueBuilder.length();
            valueBuilder.append(value);

            if (isValueBold) {
                valueBuilder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), boldStartIndex,
                        boldStartIndex + value.length(), 0);
            }

            return valueBuilder;
        }

        @Override
        protected void updateControlLayout() {
            if (!mIsLayoutInitialized) return;

            mBreakdownLayout.setVisibility(mDisplayMode == DISPLAY_MODE_FOCUSED ? VISIBLE : GONE);
            super.updateControlLayout();
        }
    }

    /**
     * Section that allows selecting one thing from a set of mutually-exclusive options.
     *
     * Normal mode:     The summary text displays the selected option, and the icon for the option
     *                  is displayed in the logo section (if it exists).
     *                  If no option is selected, the "empty label" is displayed in its place.
     *                  This is important for shipping options (e.g.) because there will be no
     *                  option selected by default and a prompt can be displayed.
     * Expandable mode: Same as Normal, but shows the chevron.
     * Focused mode:    Hides the summary and chevron, then displays the full set of options.
     *
     * .............................................................................................
     * . TITLE                                                          |                |         .
     * .................................................................|                |         .
     * . LEFT SUMMARY TEXT                        |  RIGHT SUMMARY TEXT |                |         .
     * .................................................................|                | CHEVRON .
     * . Descriptive text that spans all three columns because it can.  |                |    or   .
     * . ! Warning text that displays a big scary warning and icon.     |           LOGO |   ADD   .
     * . O Option 1                                              ICON 1 |                |    or   .
     * . O Option 2                                              ICON 2 |                |  SELECT .
     * . O Option 3                                              ICON 3 |                |         .
     * . + ADD THING                                                    |                |         .
     * .............................................................................................
     */
    public static class OptionSection extends PaymentRequestSection {

        private static final int INVALID_OPTION_INDEX = -1;

        private final List<TextView> mLabelsForTest = new ArrayList<>();
        private boolean mCanAddItems = true;

        /**
         * Displays a row representing either a selectable option or some flavor text.
         *
         * + The "button" is on the left and shows either an icon or a radio button to represent th
         *   row type.
         * + The "label" is text describing the row.
         * + The "icon" is a logo representing the option, like a credit card.
         */
        public class OptionRow {
            private static final int OPTION_ROW_TYPE_OPTION = 0;
            private static final int OPTION_ROW_TYPE_ADD = 1;
            private static final int OPTION_ROW_TYPE_DESCRIPTION = 2;
            private static final int OPTION_ROW_TYPE_WARNING = 3;

            private final int mRowType;
            private final PaymentOption mOption;
            private final View mButton;
            private final TextView mLabel;
            private final View mIcon;

            public OptionRow(GridLayout parent, int rowIndex, int rowType, PaymentOption item,
                    boolean isSelected) {
                boolean iconExists = item != null && item.getDrawableIconId() != 0;
                boolean isEnabled = item != null && item.isValid();
                mRowType = rowType;
                mOption = item;
                mButton = createButton(parent, rowIndex, isSelected, isEnabled);
                mLabel = createLabel(parent, rowIndex, iconExists, isEnabled);
                mIcon = iconExists ? createIcon(parent, rowIndex) : null;
            }

            /** Sets the selected state of this item, alerting the delegate if selected. */
            public void setChecked(boolean isChecked) {
                if (mOption == null) return;

                ((RadioButton) mButton).setChecked(isChecked);
                if (isChecked) {
                    updateSelectedItem(mOption);
                    mDelegate.onPaymentOptionChanged(OptionSection.this, mOption);
                }
            }

            /** Change the label for the row. */
            public void setLabel(int stringId) {
                setLabel(getContext().getString(stringId));
            }

            /** Change the label for the row. */
            public void setLabel(CharSequence string) {
                mLabel.setText(string);
            }

            /** Set the button identifier for the option. */
            public void setButtonId(int id) {
                mButton.setId(id);
            }

            /** @return the label for the row. */
            @VisibleForTesting
            public CharSequence getLabelText() {
                return mLabel.getText();
            }

            private View createButton(
                    GridLayout parent, int rowIndex, boolean isSelected, boolean isEnabled) {
                if (mRowType == OPTION_ROW_TYPE_DESCRIPTION) return null;

                Context context = parent.getContext();
                View view;

                if (mRowType == OPTION_ROW_TYPE_OPTION) {
                    // Show a radio button indicating whether the PaymentOption is selected.
                    RadioButton button = new RadioButton(context);
                    button.setChecked(isSelected && isEnabled);
                    button.setEnabled(isEnabled);
                    view = button;
                } else {
                    // Show an icon representing the row type, defaulting to the add button.
                    int drawableId;
                    int drawableTint;
                    if (mRowType == OPTION_ROW_TYPE_WARNING) {
                        drawableId = R.drawable.ic_warning_white_24dp;
                        drawableTint = R.color.error_text_color;
                    } else {
                        drawableId = R.drawable.plus;
                        drawableTint = R.color.light_active_color;
                    }

                    TintedDrawable tintedDrawable = TintedDrawable.constructTintedDrawable(
                            context.getResources(), drawableId, drawableTint);
                    ImageButton button = new ImageButton(context);
                    button.setBackground(null);
                    button.setImageDrawable(tintedDrawable);
                    button.setPadding(0, 0, 0, 0);
                    view = button;
                }

                // The button hugs left.
                GridLayout.LayoutParams buttonParams = new GridLayout.LayoutParams(
                        GridLayout.spec(rowIndex, 1, GridLayout.CENTER),
                        GridLayout.spec(0, 1, GridLayout.CENTER));
                buttonParams.topMargin = mVerticalMargin;
                ApiCompatibilityUtils.setMarginEnd(buttonParams, mLargeSpacing);
                parent.addView(view, buttonParams);

                view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                view.setOnClickListener(OptionSection.this);
                return view;
            }

            private TextView createLabel(
                    GridLayout parent, int rowIndex, boolean iconExists, boolean isEnabled) {
                Context context = parent.getContext();
                Resources resources = context.getResources();

                // By default, the label appears to the right of the "button" in the second column.
                // + If there is no button and no icon, the label spans the whole row.
                // + If there is no icon, the label spans two columns.
                // + Otherwise, the label occupies only its own column.
                int columnStart = 1;
                int columnSpan = iconExists ? 1 : 2;

                TextView labelView = new TextView(context);
                if (mRowType == OPTION_ROW_TYPE_OPTION) {
                    // Show the string representing the PaymentOption.
                    ApiCompatibilityUtils.setTextAppearance(labelView, isEnabled
                            ? R.style.PaymentsUiSectionDefaultText
                            : R.style.PaymentsUiSectionDisabledText);
                    labelView.setText(convertOptionToString(
                            mOption, mDelegate.isBoldLabelNeeded(OptionSection.this)));
                    labelView.setEnabled(isEnabled);
                } else if (mRowType == OPTION_ROW_TYPE_ADD) {
                    // Shows string saying that the user can add a new option, e.g. credit card no.
                    String typeface = resources.getString(R.string.roboto_medium_typeface);
                    int textStyle = resources.getInteger(R.integer.roboto_medium_textstyle);
                    int buttonHeight = resources.getDimensionPixelSize(
                            R.dimen.payments_section_add_button_height);

                    ApiCompatibilityUtils.setTextAppearance(
                            labelView, R.style.PaymentsUiSectionAddButtonLabel);
                    labelView.setMinimumHeight(buttonHeight);
                    labelView.setGravity(Gravity.CENTER_VERTICAL);
                    labelView.setTypeface(Typeface.create(typeface, textStyle));
                } else if (mRowType == OPTION_ROW_TYPE_DESCRIPTION) {
                    // The description spans all the columns.
                    columnStart = 0;
                    columnSpan = 3;

                    ApiCompatibilityUtils.setTextAppearance(
                            labelView, R.style.PaymentsUiSectionDescriptiveText);
                } else if (mRowType == OPTION_ROW_TYPE_WARNING) {
                    // Warnings use two columns.
                    columnSpan = 2;
                    ApiCompatibilityUtils.setTextAppearance(
                            labelView, R.style.PaymentsUiSectionWarningText);
                }

                // The label spans two columns if no icon exists.  Setting the view width to 0
                // forces it to stretch.
                GridLayout.LayoutParams labelParams = new GridLayout.LayoutParams(
                        GridLayout.spec(rowIndex, 1, GridLayout.CENTER),
                        GridLayout.spec(columnStart, columnSpan, GridLayout.FILL));
                labelParams.topMargin = mVerticalMargin;
                labelParams.width = 0;
                parent.addView(labelView, labelParams);

                labelView.setOnClickListener(OptionSection.this);
                return labelView;
            }

            private View createIcon(GridLayout parent, int rowIndex) {
                // The icon has a pre-defined width.
                ImageView icon = new ImageView(parent.getContext());
                icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                icon.setBackgroundResource(R.drawable.payments_ui_logo_bg);
                icon.setImageResource(mOption.getDrawableIconId());
                icon.setMaxWidth(mIconMaxWidth);

                // The icon floats to the right of everything.
                GridLayout.LayoutParams iconParams = new GridLayout.LayoutParams(
                        GridLayout.spec(rowIndex, 1, GridLayout.CENTER), GridLayout.spec(2, 1));
                iconParams.topMargin = mVerticalMargin;
                ApiCompatibilityUtils.setMarginStart(iconParams, mLargeSpacing);
                parent.addView(icon, iconParams);

                icon.setOnClickListener(OptionSection.this);
                return icon;
            }
        }

        /** Top and bottom margins for each item. */
        private final int mVerticalMargin;

        /** All the possible PaymentOptions in Layout form, then one row for adding new options. */
        private final ArrayList<OptionRow> mOptionRows = new ArrayList<>();

        /** Width that the icon takes. */
        private final int mIconMaxWidth;

        /** Layout containing all the {@link OptionRow}s. */
        private GridLayout mOptionLayout;

        /** A spinner to show when the user selection is being checked. */
        private View mCheckingProgress;

        /** SectionInformation that is used to populate the views in this section. */
        private SectionInformation mSectionInformation;

        /**
         * Constructs an OptionSection.
         *
         * @param context     Context to pull resources from.
         * @param sectionName Title of the section to display.
         * @param delegate    Delegate to alert when something changes in the dialog.
         */
        public OptionSection(Context context, String sectionName, SectionDelegate delegate) {
            super(context, sectionName, delegate);
            mVerticalMargin = context.getResources().getDimensionPixelSize(
                    R.dimen.payments_section_small_spacing);
            mIconMaxWidth = context.getResources().getDimensionPixelSize(
                    R.dimen.payments_section_logo_width);
            setSummaryText(null, null);
        }

        @Override
        public void handleClick(View v) {
            // Handle click on the "ADD THING" button.
            for (int i = 0; i < mOptionRows.size(); i++) {
                OptionRow row = mOptionRows.get(i);
                boolean wasClicked = row.mButton == v || row.mLabel == v || row.mIcon == v;
                if (row.mOption == null && wasClicked) {
                    mDelegate.onAddPaymentOption(this);
                    return;
                }
            }

            // Update the radio button state: checked/unchecked.
            for (int i = 0; i < mOptionRows.size(); i++) {
                OptionRow row = mOptionRows.get(i);
                boolean wasClicked = row.mButton == v || row.mLabel == v || row.mIcon == v;
                if (row.mOption != null) row.setChecked(wasClicked);
            }
        }

        @Override
        public void focusSection(boolean shouldFocus) {
            // Override expansion of the section if there's no options to show.
            boolean mayFocus = mSectionInformation != null && mSectionInformation.getSize() > 0;
            if (!mayFocus && shouldFocus) {
                setDisplayMode(PaymentRequestSection.DISPLAY_MODE_NORMAL);
                return;
            }

            super.focusSection(shouldFocus);
        }

        @Override
        protected boolean isLogoNecessary() {
            return true;
        }

        @Override
        protected void createMainSectionContent(LinearLayout mainSectionLayout) {
            Context context = mainSectionLayout.getContext();
            mCheckingProgress = createLoadingSpinner();

            mOptionLayout = new GridLayout(context);
            mOptionLayout.setColumnCount(3);
            mainSectionLayout.addView(mOptionLayout, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        /** @param canAddItems If false, this section will not show [+ ADD THING] button. */
        public void setCanAddItems(boolean canAddItems) {
            mCanAddItems = canAddItems;
        }

        /** Updates the View to account for the new {@link SectionInformation} being passed in. */
        public void update(SectionInformation information) {
            mSectionInformation = information;
            PaymentOption selectedItem = information.getSelectedItem();
            updateSelectedItem(selectedItem);
            updateOptionList(information, selectedItem);
            updateControlLayout();
        }

        private View createLoadingSpinner() {
            ViewGroup spinnyLayout = (ViewGroup) LayoutInflater.from(getContext()).inflate(
                    R.layout.payment_request_spinny, null);

            TextView textView = (TextView) spinnyLayout.findViewById(R.id.message);
            textView.setText(getContext().getString(R.string.payments_checking_option));

            return spinnyLayout;
        }

        private void setSpinnerVisibility(boolean visibility) {
            if (visibility) {
                if (mCheckingProgress.getParent() != null) return;

                ViewGroup parent = (ViewGroup) mOptionLayout.getParent();
                int optionLayoutIndex = parent.indexOfChild(mOptionLayout);
                parent.addView(mCheckingProgress, optionLayoutIndex);

                MarginLayoutParams params =
                        (MarginLayoutParams) mCheckingProgress.getLayoutParams();
                params.width = LayoutParams.MATCH_PARENT;
                params.height = LayoutParams.WRAP_CONTENT;
                params.bottomMargin = getContext().getResources().getDimensionPixelSize(
                        R.dimen.payments_section_checking_spacing);
                mCheckingProgress.requestLayout();
            } else {
                if (mCheckingProgress.getParent() == null) return;

                ViewGroup parent = (ViewGroup) mCheckingProgress.getParent();
                parent.removeView(mCheckingProgress);
            }
        }

        @Override
        protected void updateControlLayout() {
            if (!mIsLayoutInitialized) return;

            if (mDisplayMode == DISPLAY_MODE_FOCUSED) {
                setIsSummaryAllowed(false);
                mOptionLayout.setVisibility(VISIBLE);
                setSpinnerVisibility(false);
            } else if (mDisplayMode == DISPLAY_MODE_CHECKING) {
                setIsSummaryAllowed(false);
                mOptionLayout.setVisibility(GONE);
                setSpinnerVisibility(true);
            } else {
                setIsSummaryAllowed(true);
                mOptionLayout.setVisibility(GONE);
                setSpinnerVisibility(false);
            }

            super.updateControlLayout();
        }

        @Override
        public int getEditButtonState() {
            if (mSectionInformation == null) return EDIT_BUTTON_GONE;

            if (mSectionInformation.getSize() == 0 && mCanAddItems) {
                // There aren't any PaymentOptions.  Ask the user to add a new one.
                return EDIT_BUTTON_ADD;
            } else if (mSectionInformation.getSelectedItem() == null) {
                // The user hasn't selected any available PaymentOptions.  Ask the user to pick one.
                return EDIT_BUTTON_SELECT;
            } else {
                return EDIT_BUTTON_GONE;
            }
        }

        private void updateSelectedItem(PaymentOption selectedItem) {
            if (selectedItem == null) {
                setLogoResource(0);
                setIsSummaryAllowed(false);
                setSummaryText(null, null);
            } else {
                setLogoResource(selectedItem.getDrawableIconId());
                setSummaryText(convertOptionToString(selectedItem, false), null);
            }

            updateControlLayout();
        }

        private void updateOptionList(SectionInformation information, PaymentOption selectedItem) {
            mOptionLayout.removeAllViews();
            mOptionRows.clear();
            mLabelsForTest.clear();

            // Show any additional text requested by the layout.
            String additionalText = mDelegate.getAdditionalText(this);
            if (!TextUtils.isEmpty(additionalText)) {
                OptionRow descriptionRow = new OptionRow(mOptionLayout,
                        mOptionRows.size(),
                        mDelegate.isAdditionalTextDisplayingWarning(this)
                                ? OptionRow.OPTION_ROW_TYPE_WARNING
                                : OptionRow.OPTION_ROW_TYPE_DESCRIPTION,
                                null, false);
                mOptionRows.add(descriptionRow);
                descriptionRow.setLabel(additionalText);
            }

            // List out known payment options.
            int firstOptionIndex = INVALID_OPTION_INDEX;
            for (int i = 0; i < information.getSize(); i++) {
                int currentRow = mOptionRows.size();
                if (firstOptionIndex == INVALID_OPTION_INDEX) firstOptionIndex = currentRow;

                PaymentOption item = information.getItem(i);
                OptionRow currentOptionRow = new OptionRow(mOptionLayout, currentRow,
                        OptionRow.OPTION_ROW_TYPE_OPTION, item, item == selectedItem);
                mOptionRows.add(currentOptionRow);

                // For testing, keep the labels in a list for easy access.
                mLabelsForTest.add(currentOptionRow.mLabel);
            }

            // TODO(crbug.com/627186): Find another way to give access to this resource in tests.
            // For testing.
            if (firstOptionIndex != INVALID_OPTION_INDEX) {
                mOptionRows.get(firstOptionIndex).setButtonId(R.id.payments_first_radio_button);
            }

            // If the user is allowed to add new options, show the button for it.
            if (information.getAddStringId() != 0 && mCanAddItems) {
                OptionRow addRow = new OptionRow(mOptionLayout, mOptionLayout.getChildCount(),
                        OptionRow.OPTION_ROW_TYPE_ADD, null, false);
                addRow.setLabel(information.getAddStringId());
                addRow.setButtonId(R.id.payments_add_option_button);
                mOptionRows.add(addRow);
            }
        }

        private CharSequence convertOptionToString(PaymentOption item, boolean useBoldLabel) {
            SpannableStringBuilder builder = new SpannableStringBuilder(item.getLabel());
            if (useBoldLabel) {
                builder.setSpan(
                        new StyleSpan(android.graphics.Typeface.BOLD), 0, builder.length(), 0);
            }

            if (!TextUtils.isEmpty(item.getSublabel())) {
                if (builder.length() > 0) builder.append("\n");
                builder.append(item.getSublabel());
            }

            if (!TextUtils.isEmpty(item.getTertiaryLabel())) {
                if (builder.length() > 0) builder.append("\n");
                builder.append(item.getTertiaryLabel());
            }

            return builder;
        }

        /**
         * Returns the label at the specified |labelIndex|. Returns null if there is no label at
         * that index.
         */
        @VisibleForTesting
        public TextView getOptionLabelsForTest(int labelIndex) {
            return mLabelsForTest.get(labelIndex);
        }

        /** Returns the number of option labels. */
        @VisibleForTesting
        public int getNumberOfOptionLabelsForTest() {
            return mLabelsForTest.size();
        }

        /** Returns the OptionRow at the specified |index|. */
        @VisibleForTesting
        public OptionRow getOptionRowAtIndex(int index) {
            return mOptionRows.get(index);
        }
    }

    /**
     * Drawn as a 1dp separator.  Initially drawn without being expanded to the full width of the
     * UI, but can be expanded to separate sections fully.
     */
    public static class SectionSeparator extends View {
        /** Creates the View and adds it to the parent. */
        public SectionSeparator(ViewGroup parent) {
            this(parent, -1);
        }

        /** Creates the View and adds it to the parent at the given index. */
        public SectionSeparator(ViewGroup parent, int index) {
            super(parent.getContext());
            Resources resources = parent.getContext().getResources();
            setBackgroundColor(ApiCompatibilityUtils.getColor(
                    resources, R.color.payments_section_separator));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.payments_section_separator_height));

            int margin = resources.getDimensionPixelSize(R.dimen.payments_section_large_spacing);
            ApiCompatibilityUtils.setMarginStart(params, margin);
            ApiCompatibilityUtils.setMarginEnd(params, margin);
            parent.addView(this, index, params);
        }

        /** Expand the separator to be the full width of the dialog. */
        public void expand() {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
            ApiCompatibilityUtils.setMarginStart(params, 0);
            ApiCompatibilityUtils.setMarginEnd(params, 0);
        }
    }
}
