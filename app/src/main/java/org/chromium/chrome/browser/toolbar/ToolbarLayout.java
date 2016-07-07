// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.compositor.Invalidator;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.chrome.browser.widget.ToolbarProgressBar;
import org.chromium.ui.UiUtils;
import org.chromium.ui.widget.Toast;

/**
 * Layout class that contains the base shared logic for manipulating the toolbar component. For
 * interaction that are not from Views inside Toolbar hierarchy all interactions should be done
 * through {@link Toolbar} rather than using this class directly.
 */
abstract class ToolbarLayout extends FrameLayout implements Toolbar {
    protected static final int BACKGROUND_TRANSITION_DURATION_MS = 400;

    private Invalidator mInvalidator;

    private final int[] mTempPosition = new int[2];

    /**
     * The ImageButton view that represents the menu button.
     */
    protected TintedImageButton mMenuButton;
    private AppMenuButtonHelper mAppMenuButtonHelper;

    protected final ColorStateList mDarkModeTint;
    protected final ColorStateList mLightModeTint;

    private ToolbarDataProvider mToolbarDataProvider;
    private ToolbarTabController mToolbarTabController;
    private ToolbarProgressBar mProgressBar;

    private boolean mNativeLibraryReady;
    private boolean mUrlHasFocus;

    private long mFirstDrawTimeMs;

    protected final int mToolbarHeightWithoutShadow;

    private boolean mFindInPageToolbarShowing;

    /**
     * Basic constructor for {@link ToolbarLayout}.
     */
    public ToolbarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mToolbarHeightWithoutShadow = getResources().getDimensionPixelOffset(
                getToolbarHeightWithoutShadowResId());
        mDarkModeTint =
                ApiCompatibilityUtils.getColorStateList(getResources(), R.color.dark_mode_tint);
        mLightModeTint =
                ApiCompatibilityUtils.getColorStateList(getResources(), R.color.light_mode_tint);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mProgressBar = (ToolbarProgressBar) findViewById(R.id.progress);
        if (mProgressBar != null) {
            removeView(mProgressBar);
            getFrameLayoutParams(mProgressBar).topMargin = mToolbarHeightWithoutShadow
                    - getFrameLayoutParams(mProgressBar).height;
            if (isNativeLibraryReady()) mProgressBar.initializeAnimation();
        }

        mMenuButton = (TintedImageButton) findViewById(R.id.menu_button);
        // Initialize the provider to an empty version to avoid null checking everywhere.
        mToolbarDataProvider = new ToolbarDataProvider() {
            @Override
            public boolean isIncognito() {
                return false;
            }

            @Override
            public Tab getTab() {
                return null;
            }

            @Override
            public String getText() {
                return null;
            }

            @Override
            public boolean wouldReplaceURL() {
                return false;
            }

            @Override
            public NewTabPage getNewTabPageForCurrentTab() {
                return null;
            }

            @Override
            public String getCorpusChipText() {
                return null;
            }

            @Override
            public int getPrimaryColor() {
                return 0;
            }

            @Override
            public boolean isUsingBrandColor() {
                return false;
            }
        };
    }

    /**
     * Quick getter for LayoutParams for a View inside a FrameLayout.
     * @param view {@link View} to fetch the layout params for.
     * @return {@link LayoutParams} the given {@link View} is currently using.
     */
    protected FrameLayout.LayoutParams getFrameLayoutParams(View view) {
        return ((FrameLayout.LayoutParams) view.getLayoutParams());
    }

    /**
     * @return The resource id to be used while getting the toolbar height with no shadow.
     */
    protected int getToolbarHeightWithoutShadowResId() {
        return R.dimen.toolbar_height_no_shadow;
    }

    /**
     * Initialize the external dependencies required for view interaction.
     * @param toolbarDataProvider The provider for toolbar data.
     * @param tabController       The controller that handles interactions with the tab.
     * @param appMenuButtonHelper The helper for managing menu button interactions.
     */
    public void initialize(ToolbarDataProvider toolbarDataProvider,
            ToolbarTabController tabController, AppMenuButtonHelper appMenuButtonHelper) {
        mToolbarDataProvider = toolbarDataProvider;
        mToolbarTabController = tabController;

        mMenuButton.setOnTouchListener(new OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                return mAppMenuButtonHelper.onTouch(v, event);
            }
        });
        mAppMenuButtonHelper = appMenuButtonHelper;
    }

    /**
     *  This function handles native dependent initialization for this class
     */
    public void onNativeLibraryReady() {
        mNativeLibraryReady = true;
        if (mProgressBar != null) mProgressBar.initializeAnimation();
    }

    /**
     * @return The menu button view.
     */
    protected View getMenuButton() {
        return mMenuButton;
    }

    /**
     * @return The {@link ProgressBar} this layout uses.
     */
    ToolbarProgressBar getProgressBar() {
        return mProgressBar;
    }

    @Override
    public void getPositionRelativeToContainer(View containerView, int[] position) {
        ViewUtils.getRelativeDrawPosition(containerView, this, position);
    }

    /**
     * @return The helper for menu button UI interactions.
     */
    protected AppMenuButtonHelper getMenuButtonHelper() {
        return mAppMenuButtonHelper;
    }

    /**
     * @return Whether or not the native library is loaded and ready.
     */
    protected boolean isNativeLibraryReady() {
        return mNativeLibraryReady;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        recordFirstDrawTime();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mProgressBar != null) {
            ViewGroup controlContainer =
                    (ViewGroup) getRootView().findViewById(R.id.control_container);
            int progressBarPosition = UiUtils.insertAfter(
                    controlContainer, mProgressBar, (View) getParent());
            assert progressBarPosition >= 0;
        }
    }

    /**
     * Shows the content description toast for items on the toolbar.
     * @param view The view to anchor the toast.
     * @param description The string shown in the toast.
     * @return Whether a toast has been shown successfully.
     */
    protected boolean showAccessibilityToast(View view, CharSequence description) {
        if (description == null) return false;

        final int screenWidth = getResources().getDisplayMetrics().widthPixels;
        final int[] screenPos = new int[2];
        view.getLocationOnScreen(screenPos);
        final int width = view.getWidth();

        Toast toast = Toast.makeText(getContext(), description, Toast.LENGTH_SHORT);
        toast.setGravity(
                Gravity.TOP | Gravity.END,
                screenWidth - screenPos[0] - width / 2,
                getHeight());
        toast.show();
        return true;
    }

    /**
     * @return The provider for toolbar related data.
     */
    protected ToolbarDataProvider getToolbarDataProvider() {
        return mToolbarDataProvider;
    }

    /**
     * Sets the {@link Invalidator} that will be called when the toolbar attempts to invalidate the
     * drawing surface.  This will give the object that registers as the host for the
     * {@link Invalidator} a chance to defer the actual invalidate to sync drawing.
     * @param invalidator An {@link Invalidator} instance.
     */
    public void setPaintInvalidator(Invalidator invalidator) {
        mInvalidator = invalidator;
    }

    /**
     * Triggers a paint but allows the {@link Invalidator} set by
     * {@link #setPaintInvalidator(Invalidator)} to decide when to actually invalidate.
     * @param client A {@link Invalidator.Client} instance that wants to be invalidated.
     */
    protected void triggerPaintInvalidate(Invalidator.Client client) {
        if (mInvalidator == null) {
            client.doInvalidate();
        } else {
            mInvalidator.invalidate(client);
        }
    }

    /**
     * Gives inheriting classes the chance to respond to
     * {@link org.chromium.chrome.browser.widget.findinpage.FindToolbar} state changes.
     * @param showing Whether or not the {@code FindToolbar} will be showing.
     */
    protected void handleFindToolbarStateChange(boolean showing) {
        mFindInPageToolbarShowing = showing;
    }

    /**
     * Sets the OnClickListener that will be notified when the TabSwitcher button is pressed.
     * @param listener The callback that will be notified when the TabSwitcher button is pressed.
     */
    public void setOnTabSwitcherClickHandler(OnClickListener listener) { }

    /**
     * Sets the OnClickListener that will be notified when the New Tab button is pressed.
     * @param listener The callback that will be notified when the New Tab button is pressed.
     */
    public void setOnNewTabClickHandler(OnClickListener listener) { }

    /**
     * Sets the OnClickListener that will be notified when the bookmark button is pressed.
     * @param listener The callback that will be notified when the bookmark button is pressed.
     */
    public void setBookmarkClickHandler(OnClickListener listener) { }

    /**
     * Sets the OnClickListener to notify when the close button is pressed in a custom tab.
     * @param listener The callback that will be notified when the close button is pressed.
     */
    public void setCustomTabCloseClickHandler(OnClickListener listener) { }

    /**
     * Gives inheriting classes the chance to update the visibility of the
     * back button.
     * @param canGoBack Whether or not the current tab has any history to go back to.
     */
    protected void updateBackButtonVisibility(boolean canGoBack) { }

    /**
     * Gives inheriting classes the chance to update the visibility of the
     * forward button.
     * @param canGoForward Whether or not the current tab has any history to go forward to.
     */
    protected void updateForwardButtonVisibility(boolean canGoForward) { }

    /**
     * Gives inheriting classes the chance to update the visibility of the
     * reload button.
     * @param isReloading Whether or not the current tab is loading.
     */
    protected void updateReloadButtonVisibility(boolean isReloading) { }

    /**
     * Gives inheriting classes the chance to update the visual status of the
     * bookmark button.
     * @param isBookmarked Whether or not the current tab is already bookmarked.
     * @param editingAllowed Whether or not bookmarks can be modified (added, edited, or removed).
     */
    protected void updateBookmarkButton(boolean isBookmarked, boolean editingAllowed) { }

    /**
     * Gives inheriting classes the chance to respond to accessibility state changes.
     * @param enabled Whether or not accessibility is enabled.
     */
    protected void onAccessibilityStatusChanged(boolean enabled) { }

    /**
     * Gives inheriting classes the chance to do the necessary UI operations after Chrome is
     * restored to a previously saved state.
     */
    protected void onStateRestored() { }

    /**
     * Gives inheriting classes the chance to update home button UI if home button preference is
     * changed.
     * @param homeButtonEnabled Whether or not home button is enabled in preference.
     */
    protected void onHomeButtonUpdate(boolean homeButtonEnabled) { }

    /**
     * Triggered when the current tab or model has changed.
     * <p>
     * As there are cases where you can select a model with no tabs (i.e. having incognito
     * tabs but no normal tabs will still allow you to select the normal model), this should
     * not guarantee that the model's current tab is non-null.
     */
    protected void onTabOrModelChanged() {
        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        if (ntp != null) getLocationBar().onTabLoadingNTP(ntp);

        getLocationBar().updateMicButtonState();
    }

    /**
     * For extending classes to override and carry out the changes related with the primary color
     * for the current tab changing.
     */
    protected void onPrimaryColorChanged(boolean shouldAnimate) { }

    /**
     * Sets the icon drawable that the close button in the toolbar (if any) should show.
     */
    public void setCloseButtonImageResource(Drawable drawable) { }

    /**
     * Sets/adds a custom action button to the {@link ToolbarLayout} if it is supported.
     * @param description  The content description for the button.
     * @param listener     The {@link OnClickListener} to use for clicks to the button.
     * @param buttonSource The {@link Bitmap} resource to use as the source for the button.
     */
    public void setCustomActionButton(Drawable drawable, String description,
            OnClickListener listener) { }

    /**
     * @return The height of the tab strip. Return 0 for toolbars that do not have a tabstrip.
     */
    public int getTabStripHeight() {
        return getResources().getDimensionPixelSize(R.dimen.tab_strip_height);
    }

    /**
     * Triggered when the content view for the specified tab has changed.
     */
    protected void onTabContentViewChanged() {
        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        if (ntp != null) getLocationBar().onTabLoadingNTP(ntp);
    }

    @Override
    public boolean isReadyForTextureCapture() {
        return true;
    }

    /**
     * @param attached Whether or not the web content is attached to the view heirarchy.
     */
    protected void setContentAttached(boolean attached) { }

    /**
     * Gives inheriting classes the chance to show or hide the TabSwitcher mode of this toolbar.
     * @param inTabSwitcherMode Whether or not TabSwitcher mode should be shown or hidden.
     * @param showToolbar    Whether or not to show the normal toolbar while animating.
     * @param delayAnimation Whether or not to delay the animation until after the transition has
     *                       finished (which can be detected by a call to
     *                       {@link #onTabSwitcherTransitionFinished()}).
     */
    protected void setTabSwitcherMode(
            boolean inTabSwitcherMode, boolean showToolbar, boolean delayAnimation) { }

    /**
     * Gives inheriting classes the chance to update their state when the TabSwitcher transition has
     * finished.
     */
    protected void onTabSwitcherTransitionFinished() { }

    /**
     * Gives inheriting classes the chance to update themselves based on the
     * number of tabs in the current TabModel.
     * @param numberOfTabs The number of tabs in the current model.
     */
    protected void updateTabCountVisuals(int numberOfTabs) { }

    /**
     * Gives inheriting classes the chance to update themselves based on default search engine
     * changes.
     */
    protected void onDefaultSearchEngineChanged() { }

    @Override
    public void getLocationBarContentRect(Rect outRect) {
        View container = getLocationBar().getContainerView();
        outRect.set(container.getPaddingLeft(), container.getPaddingTop(),
                container.getWidth() - container.getPaddingRight(),
                container.getHeight() - container.getPaddingBottom());
        ViewUtils.getRelativeDrawPosition(
                this, getLocationBar().getContainerView(), mTempPosition);
        outRect.offset(mTempPosition[0], mTempPosition[1]);
    }

    @Override
    public void setTextureCaptureMode(boolean textureMode) { }

    @Override
    public boolean shouldIgnoreSwipeGesture() {
        return mUrlHasFocus
                || (mAppMenuButtonHelper != null && mAppMenuButtonHelper.isAppMenuActive())
                || mFindInPageToolbarShowing;
    }

    /**
     * @return Whether or not the url bar has focus.
     */
    protected boolean urlHasFocus() {
        return mUrlHasFocus;
    }

    /**
     * Triggered when the URL input field has gained or lost focus.
     * @param hasFocus Whether the URL field has gained focus.
     */
    protected void onUrlFocusChange(boolean hasFocus) {
        mUrlHasFocus = hasFocus;
    }

    protected boolean shouldShowMenuButton() {
        return true;
    }

    /**
     * Keeps track of the first time the toolbar is drawn.
     */
    private void recordFirstDrawTime() {
        if (mFirstDrawTimeMs == 0) mFirstDrawTimeMs = SystemClock.elapsedRealtime();
    }

    /**
     * Returns the elapsed realtime in ms of the time at which first draw for the toolbar occurred.
     */
    public long getFirstDrawTime() {
        return mFirstDrawTimeMs;
    }

    /**
     * Notified when a navigation to a different page has occurred.
     */
    protected void onNavigatedToDifferentPage() {
    }

    /**
     * Starts load progress.
     */
    protected void startLoadProgress() {
        if (mProgressBar != null) {
            mProgressBar.start();
        }
    }

    /**
     * Sets load progress.
     * @param progress The load progress between 0 and 1.
     */
    protected void setLoadProgress(float progress) {
        if (mProgressBar != null) {
            mProgressBar.setProgress(progress);
        }
    }

    /**
     * Finishes load progress.
     * @param delayed Whether hiding progress bar should be delayed to give enough time for user to
     *                        recognize the last state.
     */
    protected void finishLoadProgress(boolean delayed) {
        if (mProgressBar != null) {
            mProgressBar.finish(delayed);
        }
    }

    /**
     * Finish any toolbar animations.
     */
    public void finishAnimations() { }

    /**
     * @return The current View showing in the Tab.
     */
    protected View getCurrentTabView() {
        Tab tab = mToolbarDataProvider.getTab();
        if (tab != null) {
            return tab.getView();
        }
        return null;
    }

    /**
     * @return Whether or not the toolbar is incognito.
     */
    protected boolean isIncognito() {
        return mToolbarDataProvider.isIncognito();
    }

    /**
     * @return {@link LocationBar} object this {@link ToolbarLayout} contains.
     */
    public abstract LocationBar getLocationBar();

    /**
     * Navigates the current Tab back.
     * @return Whether or not the current Tab did go back.
     */
    protected boolean back() {
        getLocationBar().hideSuggestions();
        return mToolbarTabController != null ? mToolbarTabController.back() : false;
    }

    /**
     * Navigates the current Tab forward.
     * @return Whether or not the current Tab did go forward.
     */
    protected boolean forward() {
        getLocationBar().hideSuggestions();
        return mToolbarTabController != null ? mToolbarTabController.forward() : false;
    }

    /**
     * If the page is currently loading, this will trigger the tab to stop.  If the page is fully
     * loaded, this will trigger a refresh.
     *
     * <p>The buttons of the toolbar will be updated as a result of making this call.
     */
    protected void stopOrReloadCurrentTab() {
        getLocationBar().hideSuggestions();
        if (mToolbarTabController != null) mToolbarTabController.stopOrReloadCurrentTab();
    }

    /**
     * Opens hompage in the current tab.
     */
    protected void openHomepage() {
        getLocationBar().hideSuggestions();
        if (mToolbarTabController != null) mToolbarTabController.openHomepage();
    }
}
