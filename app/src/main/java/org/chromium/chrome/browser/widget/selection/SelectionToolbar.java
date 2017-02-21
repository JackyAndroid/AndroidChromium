// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.NumberRollView;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A toolbar that changes its view depending on whether a selection is established. The XML inflated
 * for this class must include number_roll_view.xml.
 *
 * @param <E> The type of the selectable items this toolbar interacts with.
 */
public class SelectionToolbar<E> extends Toolbar implements SelectionObserver<E>, OnClickListener {
    /** No navigation button is displayed. **/
    protected static final int NAVIGATION_BUTTON_NONE = 0;
    /** Button to open the DrawerLayout. Only valid if mDrawerLayout is set. **/
    protected static final int NAVIGATION_BUTTON_MENU = 1;
    /** Button to navigate back. This calls {@link #onNavigationBack()}. **/
    protected static final int NAVIGATION_BUTTON_BACK = 2;
    /** Button to clear the selection. **/
    protected static final int NAVIGATION_BUTTON_SELECTION_BACK = 3;

    protected boolean mIsSelectionEnabled;
    protected SelectionDelegate<E> mSelectionDelegate;

    protected NumberRollView mNumberRollView;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private int mNavigationButton;
    private int mTitleResId;
    private int mNormalGroupResId;
    private int mSelectedGroupResId;

    /**
     * Constructor for inflating from XML.
     */
    public SelectionToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Destroys and cleans up itself.
     */
    public void destroy() {
        if (mSelectionDelegate != null) {
            mSelectionDelegate.removeObserver(this);
        }
    }

    /**
     * Initializes the SelectionToolbar.
     *
     * @param delegate The SelectionDelegate that will inform the toolbar of selection changes.
     * @param titleResId The resource id of the title string. May be 0 if this class shouldn't set
     *                   set a title when the selection is cleared.
     * @param drawerLayout The DrawerLayout whose navigation icon is displayed in this toolbar.
     * @param normalGroupResId The resource id of the menu group to show when a selection isn't
     *                         established.
     * @param selectedGroupResId The resource id of the menu item to show when a selection is
     *                           established.
     */
    public void initialize(SelectionDelegate<E> delegate, int titleResId,
            @Nullable DrawerLayout drawerLayout, int normalGroupResId, int selectedGroupResId) {
        mTitleResId = titleResId;
        mDrawerLayout = drawerLayout;
        mNormalGroupResId = normalGroupResId;
        mSelectedGroupResId = selectedGroupResId;

        mSelectionDelegate = delegate;
        mSelectionDelegate.addObserver(this);

        if (mDrawerLayout != null) initActionBarDrawerToggle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNumberRollView = (NumberRollView) findViewById(R.id.selection_mode_number);
    }

    @Override
    @CallSuper
    public void onSelectionStateChange(List<E> selectedItems) {
        boolean wasSelectionEnabled = mIsSelectionEnabled;
        mIsSelectionEnabled = mSelectionDelegate.isSelectionEnabled();

        // If onSelectionStateChange() gets called before onFinishInflate(), mNumberRollView
        // will be uninitialized. See crbug.com/637948.
        if (mNumberRollView == null) {
            mNumberRollView = (NumberRollView) findViewById(R.id.selection_mode_number);
        }

        if (mIsSelectionEnabled) {
            // TODO(twellington): add the concept of normal & selected tint to apply to all
            //                    toolbar buttons.
            setOverflowIcon(TintedDrawable.constructTintedDrawable(getResources(),
                    R.drawable.btn_menu, android.R.color.white));
            setNavigationButton(NAVIGATION_BUTTON_SELECTION_BACK);
            setTitle(null);

            getMenu().setGroupVisible(mNormalGroupResId, false);
            getMenu().setGroupVisible(mSelectedGroupResId, true);

            setBackgroundColor(
                    ApiCompatibilityUtils.getColor(getResources(), R.color.light_active_color));

            mNumberRollView.setVisibility(View.VISIBLE);
            if (!wasSelectionEnabled) mNumberRollView.setNumber(0, false);
            mNumberRollView.setNumber(selectedItems.size(), true);
        } else {
            setOverflowIcon(TintedDrawable.constructTintedDrawable(getResources(),
                    R.drawable.btn_menu));
            getMenu().setGroupVisible(mNormalGroupResId, true);
            getMenu().setGroupVisible(mSelectedGroupResId, false);
            setBackgroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.appbar_background));

            if (mTitleResId != 0) setTitle(mTitleResId);
            setNavigationButton(NAVIGATION_BUTTON_MENU);

            mNumberRollView.setVisibility(View.GONE);
            mNumberRollView.setNumber(0, false);
        }

        if (mIsSelectionEnabled && !wasSelectionEnabled) {
            announceForAccessibility(
                    getResources().getString(R.string.accessibility_toolbar_screen_position));
        }
    }

    @Override
    public void onClick(View view) {
        switch (mNavigationButton) {
            case NAVIGATION_BUTTON_NONE:
                break;
            case NAVIGATION_BUTTON_MENU:
                // ActionBarDrawerToggle handles this.
                break;
            case NAVIGATION_BUTTON_BACK:
                onNavigationBack();
                break;
            case NAVIGATION_BUTTON_SELECTION_BACK:
                mSelectionDelegate.clearSelection();
                break;
            default:
                assert false : "Incorrect navigation button state";
        }
    }

    /**
     * Handle a click on the navigation back button. Subclasses should override this method if
     * navigation back is a valid toolbar action.
     */
    protected void onNavigationBack() {}

    /**
     * Update the current navigation button (the top-left icon on LTR)
     * @param navigationButton one of NAVIGATION_BUTTON_* constants.
     */
    protected void setNavigationButton(int navigationButton) {
        int iconResId = 0;
        int contentDescriptionId = 0;

        if (navigationButton == NAVIGATION_BUTTON_MENU && mDrawerLayout == null) {
            mNavigationButton = NAVIGATION_BUTTON_NONE;
        } else {
            mNavigationButton = navigationButton;
        }

        if (mNavigationButton == NAVIGATION_BUTTON_MENU) {
            initActionBarDrawerToggle();
            // ActionBarDrawerToggle will take care of icon and content description, so just return.
            return;
        }

        if (mActionBarDrawerToggle != null) {
            mActionBarDrawerToggle.setDrawerIndicatorEnabled(false);
            mDrawerLayout.addDrawerListener(null);
        }

        setNavigationOnClickListener(this);

        switch (mNavigationButton) {
            case NAVIGATION_BUTTON_NONE:
                break;
            case NAVIGATION_BUTTON_BACK:
                // TODO(twellington): use ic_arrow_back_white_24dp and tint it.
                iconResId = R.drawable.back_normal;
                contentDescriptionId = R.string.accessibility_toolbar_btn_back;
                break;
            case NAVIGATION_BUTTON_SELECTION_BACK:
                // TODO(twellington): use btn_close and tint it.
                iconResId = R.drawable.btn_close_white;
                contentDescriptionId = R.string.accessibility_cancel_selection;
                break;
            default:
                assert false : "Incorrect navigationButton argument";
        }

        if (iconResId == 0) {
            setNavigationIcon(null);
        } else {
            setNavigationIcon(iconResId);
        }
        setNavigationContentDescription(contentDescriptionId);
    }

    /**
     * Set up ActionBarDrawerToggle, a.k.a. hamburger button.
     */
    private void initActionBarDrawerToggle() {
        // Sadly, the only way to set correct toolbar button listener for ActionBarDrawerToggle
        // is constructing, so we will need to construct every time we re-show this button.
        mActionBarDrawerToggle = new ActionBarDrawerToggle((Activity) getContext(),
                mDrawerLayout, this,
                R.string.accessibility_drawer_toggle_btn_open,
                R.string.accessibility_drawer_toggle_btn_close);
        mDrawerLayout.addDrawerListener(mActionBarDrawerToggle);
        mActionBarDrawerToggle.syncState();
    }
}
