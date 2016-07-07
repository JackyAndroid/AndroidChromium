// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.ui.widget.ButtonCompat;

import java.util.ArrayList;

/**
 * Layout that arranges an InfoBar's views. An InfoBarLayout consists of:
 * - A message describing the action that the user can take.
 * - A close button on the right side.
 * - (optional) An icon representing the infobar's purpose on the left side.
 * - (optional) Additional "custom" views (e.g. a checkbox and text, or a pair of spinners)
 * - (optional) One or two buttons with text at the bottom.
 *
 * When adding custom views, widths and heights defined in the LayoutParams will be ignored.
 * However, setting a minimum width in another way, like TextView.getMinWidth(), should still be
 * obeyed.
 *
 * Logic for what happens when things are clicked should be implemented by the InfoBarView.
 */
public class InfoBarLayout extends ViewGroup implements View.OnClickListener {

    /**
     * Parameters used for laying out children.
     */
    private static class LayoutParams extends ViewGroup.LayoutParams {

        public int startMargin;
        public int endMargin;
        public int topMargin;
        public int bottomMargin;

        // Where this view will be laid out. These values are assigned in onMeasure() and used in
        // onLayout().
        public int start;
        public int top;

        LayoutParams(int startMargin, int topMargin, int endMargin, int bottomMargin) {
            super(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            this.startMargin = startMargin;
            this.topMargin = topMargin;
            this.endMargin = endMargin;
            this.bottomMargin = bottomMargin;
        }
    }

    private static class Group {
        public View[] views;

        /**
         * The gravity of each view in Group. Must be either Gravity.START, Gravity.END, or
         * Gravity.FILL_HORIZONTAL.
         */
        public int gravity = Gravity.START;

        /** Whether the views are vertically stacked. */
        public boolean isStacked;

        Group(View... views) {
            this.views = views;
        }

        static View[] filterNullViews(View... views) {
            ArrayList<View> viewsList = new ArrayList<View>();
            for (View v : views) {
                if (v != null) viewsList.add(v);
            }
            return viewsList.toArray(new View[viewsList.size()]);
        }

        void setHorizontalMode(int horizontalSpacing, int startMargin, int endMargin) {
            isStacked = false;
            for (int i = 0; i < views.length; i++) {
                LayoutParams lp = (LayoutParams) views[i].getLayoutParams();
                lp.startMargin = i == 0 ? startMargin : horizontalSpacing;
                lp.topMargin = 0;
                lp.endMargin = i == views.length - 1 ? endMargin : 0;
                lp.bottomMargin = 0;
            }
        }

        void setVerticalMode(int verticalSpacing, int bottomMargin) {
            isStacked = true;
            for (int i = 0; i < views.length; i++) {
                LayoutParams lp = (LayoutParams) views[i].getLayoutParams();
                lp.startMargin = 0;
                lp.topMargin = i == 0 ? 0 : verticalSpacing;
                lp.endMargin = 0;
                lp.bottomMargin = i == views.length - 1 ? bottomMargin : 0;
            }
        }
    }

    private static final int ROW_MAIN = 1;
    private static final int ROW_OTHER = 2;

    private final int mMargin;
    private final int mIconSize;
    private final int mMinWidth;
    private final int mAccentColor;

    private final InfoBarView mInfoBarView;
    private final ImageButton mCloseButton;
    private TextView mMessageTextView;
    private View mMessageView;
    private ImageView mIconView;
    private ButtonCompat mPrimaryButton;
    private Button mSecondaryButton;
    private Button mTertiaryButton;
    private View mCustomButton;

    private Group mMainGroup;
    private Group mCustomGroup;
    private Group mButtonGroup;

    /**
     * These values are used during onMeasure() to track where the next view will be placed.
     *
     * mWidth is the infobar width.
     * [mStart, mEnd) is the range of unoccupied space on the current row.
     * mTop and mBottom are the top and bottom of the current row.
     *
     * These values, along with a view's gravity, are used to position the next view.
     * These values are updated after placing a view and after starting a new row.
     */
    private int mWidth;
    private int mStart;
    private int mEnd;
    private int mTop;
    private int mBottom;

    /**
     * Constructs a layout for the specified InfoBar. After calling this, be sure to set the
     * message, the buttons, and/or the custom content using setMessage(), setButtons(), and
     * setCustomContent().
     *
     * @param context The context used to render.
     * @param infoBarView InfoBarView that listens to events.
     * @param iconResourceId ID of the icon to use for the InfoBar.
     * @param iconBitmap Bitmap for the icon to use, if the resource ID wasn't passed through.
     * @param message The message to show in the infobar.
     */
    public InfoBarLayout(Context context, InfoBarView infoBarView, int iconResourceId,
            Bitmap iconBitmap, CharSequence message) {
        super(context);
        mInfoBarView = infoBarView;

        // Grab the dimensions.
        Resources res = getResources();
        mMargin = res.getDimensionPixelOffset(R.dimen.infobar_margin);
        mIconSize = res.getDimensionPixelSize(R.dimen.infobar_icon_size);
        mMinWidth = res.getDimensionPixelSize(R.dimen.infobar_min_width);
        mAccentColor = ApiCompatibilityUtils.getColor(res, R.color.infobar_accent_blue);

        // Set up the close button. Apply padding so it has a big touch target.
        mCloseButton = new ImageButton(context);
        mCloseButton.setId(R.id.infobar_close_button);
        mCloseButton.setImageResource(R.drawable.btn_close);
        TypedArray a = getContext().obtainStyledAttributes(
                new int [] {R.attr.selectableItemBackground});
        Drawable closeButtonBackground = a.getDrawable(0);
        a.recycle();
        mCloseButton.setBackground(closeButtonBackground);
        mCloseButton.setPadding(mMargin, mMargin, mMargin, mMargin);
        mCloseButton.setOnClickListener(this);
        mCloseButton.setContentDescription(res.getString(R.string.infobar_close));
        mCloseButton.setLayoutParams(new LayoutParams(0, -mMargin, -mMargin, -mMargin));

        // Set up the icon.
        if (iconResourceId != 0 || iconBitmap != null) {
            mIconView = new ImageView(context);
            if (iconResourceId != 0) {
                mIconView.setImageResource(iconResourceId);
            } else if (iconBitmap != null) {
                mIconView.setImageBitmap(iconBitmap);
            }
            mIconView.setLayoutParams(new LayoutParams(0, 0, mMargin / 2, 0));
            mIconView.getLayoutParams().width = mIconSize;
            mIconView.getLayoutParams().height = mIconSize;
            mIconView.setFocusable(false);
        }

        // Set up the message view.
        mMessageTextView = (TextView) LayoutInflater.from(context).inflate(R.layout.infobar_text,
                null);
        mMessageTextView.setText(message, TextView.BufferType.SPANNABLE);
        mMessageTextView.setMovementMethod(LinkMovementMethod.getInstance());
        mMessageTextView.setLinkTextColor(mAccentColor);
        mMessageView = mMessageTextView;
    }

    /**
     * Sets the message to show on the infobar.
     */
    public void setMessage(CharSequence message) {
        mMessageTextView.setText(message, TextView.BufferType.SPANNABLE);
    }

    /**
     * Sets a custom view to show in place of the message.
     */
    public void setMessageView(View view) {
        mMessageView = view;
        mMessageTextView = null;
    }

    /**
     * Sets the custom content of the infobar. These views will be displayed in addition to the
     * standard infobar controls (icon, text, buttons). Depending on the available space, view1 and
     * view2 will be laid out:
     *  - Side by side on the main row,
     *  - Side by side on a separate row, each taking up half the width of the infobar,
     *  - Stacked above each other on two separate rows, taking up the full width of the infobar.
     */
    public void setCustomContent(View view1, View view2) {
        mCustomGroup = new Group(view1, view2);
    }

    /**
     * Sets the custom content of the infobar to a single view. This view will be displayed in
     * addition to the standard infobar controls. Depending on the available space, the view will be
     * displayed:
     *  - On the main row, start-aligned or end-aligned depending on whether there are also
     *    buttons on the main row, OR
     *  - On a separate row, start-aligned
     */
    public void setCustomContent(View view) {
        mCustomGroup = new Group(view);
    }

    /**
     * Calls setButtons(primaryText, secondaryText, null).
     */
    public void setButtons(String primaryText, String secondaryText) {
        setButtons(primaryText, secondaryText, null);
    }

    /**
     * Adds one, two, or three buttons to the layout.
     *
     * @param primaryText Text for the primary button.
     * @param secondaryText Text for the secondary button, or null if there isn't a second button.
     * @param tertiaryText Text for the tertiary button, or null if there isn't a third button.
     */
    public void setButtons(String primaryText, String secondaryText, String tertiaryText) {
        if (TextUtils.isEmpty(primaryText)) return;

        mPrimaryButton = new ButtonCompat(getContext(), mAccentColor);
        mPrimaryButton.setId(R.id.button_primary);
        mPrimaryButton.setOnClickListener(this);
        mPrimaryButton.setText(primaryText);
        mPrimaryButton.setTextColor(Color.WHITE);

        if (TextUtils.isEmpty(secondaryText)) return;

        mSecondaryButton = ButtonCompat.createBorderlessButton(getContext());
        mSecondaryButton.setId(R.id.button_secondary);
        mSecondaryButton.setOnClickListener(this);
        mSecondaryButton.setText(secondaryText);
        mSecondaryButton.setTextColor(mAccentColor);

        if (TextUtils.isEmpty(tertiaryText)) return;

        mTertiaryButton = ButtonCompat.createBorderlessButton(getContext());
        mTertiaryButton.setId(R.id.button_tertiary);
        mTertiaryButton.setOnClickListener(this);
        mTertiaryButton.setText(tertiaryText);
        mTertiaryButton.setPadding(mMargin / 2, mTertiaryButton.getPaddingTop(), mMargin / 2,
                mTertiaryButton.getPaddingBottom());
        mTertiaryButton.setTextColor(ApiCompatibilityUtils.getColor(getContext().getResources(),
                R.color.infobar_tertiary_button_text));
    }

    /**
     * Adds a custom view to show in the button row in place of the tertiary button.
     */
    public void setCustomViewInButtonRow(View view) {
        mCustomButton = view;
    }

    /**
     * Sets the size of the icon and the spacing between it and the message.
     */
    public void setIconSizeAndSpacing(int width, int height, int iconMessageSpacing) {
        LayoutParams lp = (LayoutParams) mIconView.getLayoutParams();
        lp.width = width;
        lp.height = height;
        lp.endMargin = iconMessageSpacing;
    }


    /**
     * Returns the primary button, or null if it doesn't exist.
     */
    public ButtonCompat getPrimaryButton() {
        return mPrimaryButton;
    }

    /**
     * Returns the icon, or null if it doesn't exist.
     */
    public ImageView getIcon() {
        return mIconView;
    }

    /**
     * Must be called after the message, buttons, and custom content have been set, and before the
     * first call to onMeasure().
     */
    void onContentCreated() {
        int messageTopMargin = mMessageTextView != null ? mMargin / 4 : 0;
        mMessageView.setLayoutParams(new LayoutParams(0, messageTopMargin, 0, 0));
        mMainGroup = new Group(Group.filterNullViews(mIconView, mMessageView));

        View[] buttons = Group.filterNullViews(mCustomButton, mTertiaryButton,
                mSecondaryButton, mPrimaryButton);
        if (buttons.length != 0) mButtonGroup = new Group(buttons);

        // Add the child views in the desired focus order.
        for (View v : mMainGroup.views) addView(v);
        if (mCustomGroup != null) {
            for (View v : mCustomGroup.views) addView(v);
        }
        if (mButtonGroup != null) {
            for (View v : mButtonGroup.views) addView(v);
        }
        addView(mCloseButton);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(0, 0, 0, 0);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Place all the views in the positions already determined during onMeasure().
        int width = right - left;
        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int childLeft = lp.start;
            int childRight = lp.start + child.getMeasuredWidth();

            if (isRtl) {
                int tmp = width - childRight;
                childRight = width - childLeft;
                childLeft = tmp;
            }

            child.layout(childLeft, lp.top, childRight, lp.top + child.getMeasuredHeight());
        }
    }

    /**
     * Measures *and* assigns positions to all of the views in the infobar. These positions are
     * saved in each view's LayoutParams (lp.start and lp.top) and used during onLayout(). All of
     * the interesting logic happens inside onMeasure(); onLayout() just assigns the already-
     * determined positions and mirrors everything for RTL, if needed.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        assert getLayoutParams().height == LayoutParams.WRAP_CONTENT
                : "InfoBar heights cannot be constrained.";

        // Measure all children without imposing any size constraints on them. This determines how
        // big each child wants to be.
        int unspecifiedSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            measureChild(getChildAt(i), unspecifiedSpec, unspecifiedSpec);
        }

        // Avoid overlapping views, division by zero, infinite heights, and other fun problems that
        // could arise with extremely narrow infobars.
        mWidth = Math.max(MeasureSpec.getSize(widthMeasureSpec), mMinWidth);
        mTop = mBottom = 0;
        placeGroups();

        setMeasuredDimension(mWidth, resolveSize(mBottom, heightMeasureSpec));
    }

    /**
     * Assigns positions to all of the views in the infobar. The icon, text, and close button are
     * placed on the main row. The custom content and finally the buttons are placed on the main row
     * if they fit. Otherwise, they go on their own rows.
     */
    private void placeGroups() {
        startRow();
        placeChild(mCloseButton, Gravity.END);
        placeGroup(mMainGroup);

        int customGroupWidth = 0;
        if (mCustomGroup != null) {
            updateCustomGroupForRow(ROW_MAIN);
            customGroupWidth = getWidthWithMargins(mCustomGroup);
        }

        int buttonGroupWidth = 0;
        if (mButtonGroup != null) {
            updateButtonGroupForRow(ROW_MAIN);
            buttonGroupWidth = getWidthWithMargins(mButtonGroup);
        }

        boolean customGroupOnMainRow = customGroupWidth <= availableWidth();
        boolean buttonGroupOnMainRow = customGroupWidth + buttonGroupWidth <= availableWidth();

        if (mCustomGroup != null) {
            if (customGroupOnMainRow) {
                mCustomGroup.gravity = (mButtonGroup != null && buttonGroupOnMainRow)
                        ? Gravity.START : Gravity.END;
            } else {
                startRow();
                updateCustomGroupForRow(ROW_OTHER);
            }
            placeGroup(mCustomGroup);
        }

        if (mButtonGroup != null) {
            if (!buttonGroupOnMainRow) {
                startRow();
                updateButtonGroupForRow(ROW_OTHER);

                // If the infobar consists of just a main row and a buttons row, the buttons must be
                // at least 32dp below the bottom of the message text.
                if (mCustomGroup == null && mMessageTextView != null) {
                    LayoutParams lp = (LayoutParams) mMessageTextView.getLayoutParams();
                    int messageBottom = lp.top + mMessageTextView.getMeasuredHeight();
                    mTop = Math.max(mTop, messageBottom + 2 * mMargin);
                }
            }
            placeGroup(mButtonGroup);

            if (mCustomButton != null && !buttonGroupOnMainRow) {
                // The custom button is start-aligned with the message view (unless that causes it
                // to overlap the primary button).
                LayoutParams primaryButtonLP = (LayoutParams) mPrimaryButton.getLayoutParams();
                LayoutParams customButtonLP = (LayoutParams) mCustomButton.getLayoutParams();
                LayoutParams messageLP = (LayoutParams) mMessageView.getLayoutParams();
                if (customButtonLP.start >= messageLP.start) {
                    customButtonLP.start = messageLP.start;
                } else {
                    customButtonLP.start = mMargin;
                }
                if (!mButtonGroup.isStacked) {
                    // Center the custom button vertically relative to the primary button.
                    customButtonLP.top = primaryButtonLP.top
                            + (mPrimaryButton.getMeasuredHeight()
                            - mCustomButton.getMeasuredHeight()) / 2;
                }
            }
        }

        startRow();

        // If everything fits on a single row, center everything vertically.
        if (buttonGroupOnMainRow) {
            int layoutHeight = mBottom;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int extraSpace = layoutHeight - child.getMeasuredHeight();
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                lp.top = extraSpace / 2;
            }
        }
    }

    /**
     * Places a group of views on the current row, or stacks them over multiple rows if
     * group.isStacked is true. mStart, mEnd, and mBottom are updated to reflect the space taken by
     * the group.
     */
    private void placeGroup(Group group) {
        if (group.gravity == Gravity.END) {
            for (int i = group.views.length - 1; i >= 0; i--) {
                placeChild(group.views[i], group.gravity);
                if (group.isStacked && i != 0) startRow();
            }
        } else {  // group.gravity is Gravity.START or Gravity.FILL_HORIZONTAL
            for (int i = 0; i < group.views.length; i++) {
                placeChild(group.views[i], group.gravity);
                if (group.isStacked && i != group.views.length - 1) startRow();
            }
        }
    }

    /**
     * Places a single view on the current row, and updates the view's layout parameters to remember
     * its position. mStart, mEnd, and mBottom are updated to reflect the space taken by the view.
     */
    private void placeChild(View child, int gravity) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();

        int availableWidth = Math.max(0, mEnd - mStart - lp.startMargin - lp.endMargin);
        if (child.getMeasuredWidth() > availableWidth || gravity == Gravity.FILL_HORIZONTAL) {
            measureChildWithFixedWidth(child, availableWidth);
        }

        if (gravity == Gravity.START || gravity == Gravity.FILL_HORIZONTAL) {
            lp.start = mStart + lp.startMargin;
            mStart = lp.start + child.getMeasuredWidth() + lp.endMargin;
        } else {  // gravity == Gravity.END
            lp.start = mEnd - lp.endMargin - child.getMeasuredWidth();
            mEnd = lp.start - lp.startMargin;
        }

        lp.top = mTop + lp.topMargin;
        mBottom = Math.max(mBottom, lp.top + child.getMeasuredHeight() + lp.bottomMargin);
    }

    /**
     * Advances the current position to the next row and adds margins on the left, right, and top
     * of the new row.
     */
    private void startRow() {
        mStart = mMargin;
        mEnd = mWidth - mMargin;
        mTop = mBottom + mMargin;
        mBottom = mTop;
    }

    private int availableWidth() {
        return mEnd - mStart;
    }

    /**
     * @return The width of the group, including the items' margins.
     */
    private int getWidthWithMargins(Group group) {
        if (group.isStacked) return getWidthWithMargins(group.views[0]);

        int width = 0;
        for (View v : group.views) {
            width += getWidthWithMargins(v);
        }
        return width;
    }

    private int getWidthWithMargins(View child) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return child.getMeasuredWidth() + lp.startMargin + lp.endMargin;
    }

    private void measureChildWithFixedWidth(View child, int width) {
        int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(widthSpec, heightSpec);
    }

    /**
     * The button group has different layout properties (margins, gravity, etc) when placed on the
     * main row as opposed to on a separate row. This updates the layout properties of the button
     * group to prepare for placing it on either the main row or a separate row.
     *
     * @param row One of ROW_MAIN or ROW_OTHER.
     */
    private void updateButtonGroupForRow(int row) {
        int startEndMargin = row == ROW_MAIN ? mMargin : 0;
        mButtonGroup.setHorizontalMode(mMargin / 2, startEndMargin, startEndMargin);
        mButtonGroup.gravity = Gravity.END;

        if (row == ROW_OTHER && mButtonGroup.views.length >= 2) {
            int extraWidth = availableWidth() - getWidthWithMargins(mButtonGroup);
            if (extraWidth < 0) {
                // Group is too wide to fit on a single row, so stack the group items vertically.
                mButtonGroup.setVerticalMode(mMargin / 2, 0);
                mButtonGroup.gravity = Gravity.FILL_HORIZONTAL;
            } else if (mTertiaryButton != null) {
                // Align tertiary or custom button at the start and the other buttons at the end.
                ((LayoutParams) mTertiaryButton.getLayoutParams()).endMargin += extraWidth;
            }
        }
        if (row == ROW_MAIN && mCustomButton != null) {
            // Increase spacing between custom button and primary button.
            ((LayoutParams) mCustomButton.getLayoutParams()).endMargin = mMargin;
        }
    }

    /**
     * Analagous to updateButtonGroupForRow(), but for the custom group istead of the button group.
     */
    private void updateCustomGroupForRow(int row) {
        int startEndMargin = row == ROW_MAIN ? mMargin : 0;
        mCustomGroup.setHorizontalMode(mMargin, startEndMargin, startEndMargin);
        mCustomGroup.gravity = Gravity.START;

        if (row == ROW_OTHER && mCustomGroup.views.length == 2) {
            int extraWidth = availableWidth() - getWidthWithMargins(mCustomGroup);
            if (extraWidth < 0) {
                // Group is too wide to fit on a single row, so stack the group items vertically.
                mCustomGroup.setVerticalMode(0, mMargin);
                mCustomGroup.gravity = Gravity.FILL_HORIZONTAL;
            } else {
                // Expand the children to take up the entire row.
                View view0 = mCustomGroup.views[0];
                View view1 = mCustomGroup.views[1];
                int extraWidth0 = extraWidth / 2;
                int extraWidth1 = extraWidth - extraWidth0;
                measureChildWithFixedWidth(view0, view0.getMeasuredWidth() + extraWidth0);
                measureChildWithFixedWidth(view1, view1.getMeasuredWidth() + extraWidth1);
            }
        }
        if (row == ROW_OTHER && mCustomGroup.views.length == 1) {
            mCustomGroup.gravity = Gravity.FILL_HORIZONTAL;
        }
    }

    /**
     * Listens for View clicks.
     * Classes that override this function MUST call this one.
     * @param view View that was clicked on.
     */
    @Override
    public void onClick(View view) {
        // Disable the infobar controls unless the user clicked the tertiary button, which by
        // convention is the "learn more" link.
        if (view.getId() != R.id.button_tertiary) {
            mInfoBarView.setControlsEnabled(false);
        }

        if (view.getId() == R.id.infobar_close_button) {
            mInfoBarView.onCloseButtonClicked();
        } else if (view.getId() == R.id.button_primary) {
            mInfoBarView.onButtonClicked(true);
        } else if (view.getId() == R.id.button_secondary) {
            mInfoBarView.onButtonClicked(false);
        } else if (view.getId() == R.id.button_tertiary) {
            mInfoBarView.onLinkClicked();
        }
    }
}
