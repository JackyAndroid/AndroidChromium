// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.NavigationPopup;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.omnibox.LocationBarTablet;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The Toolbar object for Tablet screens.
 */
@SuppressLint("Instantiatable")

public class ToolbarTablet extends ToolbarLayout implements OnClickListener {
    // The number of toolbar buttons that can be hidden at small widths (reload, back, forward).
    public static final int HIDEABLE_BUTTON_COUNT = 3;

    private TintedImageButton mHomeButton;
    private TintedImageButton mBackButton;
    private TintedImageButton mForwardButton;
    private TintedImageButton mReloadButton;
    private TintedImageButton mBookmarkButton;
    private TintedImageButton mSaveOfflineButton;
    private ImageButton mAccessibilitySwitcherButton;

    private OnClickListener mBookmarkListener;
    private OnClickListener mTabSwitcherListener;

    private boolean mIsInTabSwitcherMode = false;

    private boolean mShowTabStack;
    private boolean mToolbarButtonsVisible;
    private TintedImageButton[] mToolbarButtons;

    private NavigationPopup mNavigationPopup;

    private TabSwitcherDrawable mTabSwitcherButtonDrawable;
    private TabSwitcherDrawable mTabSwitcherButtonDrawableLight;

    private Boolean mUseLightColorAssets;
    private LocationBarTablet mLocationBar;

    private final int mStartPaddingWithButtons;
    private final int mStartPaddingWithoutButtons;
    private boolean mShouldAnimateButtonVisibilityChange;
    private AnimatorSet mButtonVisibilityAnimators;

    private NewTabPage mVisibleNtp;

    /**
     * Constructs a ToolbarTablet object.
     * @param context The Context in which this View object is created.
     * @param attrs The AttributeSet that was specified with this View.
     */
    public ToolbarTablet(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStartPaddingWithButtons = getResources().getDimensionPixelOffset(
                R.dimen.tablet_toolbar_start_padding);
        mStartPaddingWithoutButtons = getResources().getDimensionPixelOffset(
                R.dimen.tablet_toolbar_start_padding_no_buttons);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mLocationBar = (LocationBarTablet) findViewById(R.id.location_bar);

        mHomeButton = (TintedImageButton) findViewById(R.id.home_button);
        mBackButton = (TintedImageButton) findViewById(R.id.back_button);
        mForwardButton = (TintedImageButton) findViewById(R.id.forward_button);
        mReloadButton = (TintedImageButton) findViewById(R.id.refresh_button);
        mShowTabStack = DeviceClassManager.isAccessibilityModeEnabled(getContext())
                || CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_TABLET_TAB_STACK);

        mTabSwitcherButtonDrawable =
                TabSwitcherDrawable.createTabSwitcherDrawable(getResources(), false);
        mTabSwitcherButtonDrawableLight =
                TabSwitcherDrawable.createTabSwitcherDrawable(getResources(), true);

        mAccessibilitySwitcherButton = (ImageButton) findViewById(R.id.tab_switcher_button);
        mAccessibilitySwitcherButton.setImageDrawable(mTabSwitcherButtonDrawable);
        updateSwitcherButtonVisibility(mShowTabStack);

        mBookmarkButton = (TintedImageButton) findViewById(R.id.bookmark_button);

        mMenuButton = (TintedImageButton) findViewById(R.id.menu_button);
        mMenuButtonWrapper.setVisibility(View.VISIBLE);

        if (mAccessibilitySwitcherButton.getVisibility() == View.GONE
                && mMenuButtonWrapper.getVisibility() == View.GONE) {
            ApiCompatibilityUtils.setPaddingRelative((View) mMenuButtonWrapper.getParent(), 0, 0,
                    getResources().getDimensionPixelSize(R.dimen.tablet_toolbar_end_padding), 0);
        }

        mSaveOfflineButton = (TintedImageButton) findViewById(R.id.save_offline_button);

        // Initialize values needed for showing/hiding toolbar buttons when the activity size
        // changes.
        mShouldAnimateButtonVisibilityChange = false;
        mToolbarButtonsVisible = true;
        mToolbarButtons = new TintedImageButton[] {mBackButton, mForwardButton, mReloadButton};
    }

    /**
     * Sets up key listeners after native initialization is complete, so that we can invoke
     * native functions.
     */
    @Override
    public void onNativeLibraryReady() {
        super.onNativeLibraryReady();
        mLocationBar.onNativeLibraryReady();
        mHomeButton.setOnClickListener(this);
        mHomeButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                if (mBackButton.isFocusable()) {
                    return findViewById(R.id.back_button);
                } else if (mForwardButton.isFocusable()) {
                    return findViewById(R.id.forward_button);
                } else {
                    return findViewById(R.id.refresh_button);
                }
            }

            @Override
            public View getNextFocusBackward() {
                return findViewById(R.id.menu_button);
            }
        });

        mBackButton.setOnClickListener(this);
        mBackButton.setLongClickable(true);
        mBackButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                if (mForwardButton.isFocusable()) {
                    return findViewById(R.id.forward_button);
                } else {
                    return findViewById(R.id.refresh_button);
                }
            }

            @Override
            public View getNextFocusBackward() {
                if (mHomeButton.getVisibility() == VISIBLE) {
                    return findViewById(R.id.home_button);
                } else {
                    return findViewById(R.id.menu_button);
                }
            }
        });

        mForwardButton.setOnClickListener(this);
        mForwardButton.setLongClickable(true);
        mForwardButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                return findViewById(R.id.refresh_button);
            }

            @Override
            public View getNextFocusBackward() {
                if (mBackButton.isFocusable()) {
                    return mBackButton;
                } else if (mHomeButton.getVisibility() == VISIBLE) {
                    return findViewById(R.id.home_button);
                } else {
                    return findViewById(R.id.menu_button);
                }
            }
        });

        mReloadButton.setOnClickListener(this);
        mReloadButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                return findViewById(R.id.url_bar);
            }

            @Override
            public View getNextFocusBackward() {
                if (mForwardButton.isFocusable()) {
                    return mForwardButton;
                } else if (mBackButton.isFocusable()) {
                    return mBackButton;
                } else if (mHomeButton.getVisibility() == VISIBLE) {
                    return findViewById(R.id.home_button);
                } else {
                    return findViewById(R.id.menu_button);
                }
            }
        });

        mAccessibilitySwitcherButton.setOnClickListener(this);
        mBookmarkButton.setOnClickListener(this);

        mMenuButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                return getCurrentTabView();
            }

            @Override
            public View getNextFocusBackward() {
                return findViewById(R.id.url_bar);
            }

            @Override
            protected boolean handleEnterKeyPress() {
                return getMenuButtonHelper().onEnterKeyPress(mMenuButton);
            }
        });
        if (HomepageManager.isHomepageEnabled(getContext())) {
            mHomeButton.setVisibility(VISIBLE);
        }

        mSaveOfflineButton.setOnClickListener(this);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        if (mBackButton == originalView) {
            // Display backwards navigation popup.
            displayNavigationPopup(false, mBackButton);
            return true;
        } else if (mForwardButton == originalView) {
            // Display forwards navigation popup.
            displayNavigationPopup(true, mForwardButton);
            return true;
        }
        return super.showContextMenuForChild(originalView);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        // Ensure the the popup is not shown after resuming activity from background.
        if (hasWindowFocus && mNavigationPopup != null) {
            mNavigationPopup.dismiss();
            mNavigationPopup = null;
        }
        super.onWindowFocusChanged(hasWindowFocus);
    }

    private void displayNavigationPopup(boolean isForward, View anchorView) {
        Tab tab = getToolbarDataProvider().getTab();
        if (tab == null || tab.getWebContents() == null) return;
        mNavigationPopup = new NavigationPopup(tab.getProfile(), getContext(),
                tab.getWebContents().getNavigationController(), isForward);

        mNavigationPopup.setAnchorView(anchorView);

        int menuWidth = getResources().getDimensionPixelSize(R.dimen.menu_width);
        mNavigationPopup.setWidth(menuWidth);

        if (mNavigationPopup.shouldBeShown()) mNavigationPopup.show();
    }

    @Override
    public void onClick(View v) {
        if (mHomeButton == v) {
            openHomepage();
        } else if (mBackButton == v) {
            if (!back()) return;
            RecordUserAction.record("MobileToolbarBack");
            RecordUserAction.record("MobileTabClobbered");
        } else if (mForwardButton == v) {
            forward();
            RecordUserAction.record("MobileToolbarForward");
            RecordUserAction.record("MobileTabClobbered");
        } else if (mReloadButton == v) {
            stopOrReloadCurrentTab();
        } else if (mBookmarkButton == v) {
            if (mBookmarkListener != null) {
                mBookmarkListener.onClick(mBookmarkButton);
                RecordUserAction.record("MobileToolbarToggleBookmark");
            }
        } else if (mAccessibilitySwitcherButton == v) {
            if (mTabSwitcherListener != null) {
                cancelAppMenuUpdateBadgeAnimation();
                mTabSwitcherListener.onClick(mAccessibilitySwitcherButton);
            }
        } else if (mSaveOfflineButton == v) {
            DownloadUtils.downloadOfflinePage(getContext(), getToolbarDataProvider().getTab());
            RecordUserAction.record("MobileToolbarDownloadPage");
        }
    }

    private void updateSwitcherButtonVisibility(boolean enabled) {
        mAccessibilitySwitcherButton.setVisibility(mShowTabStack || enabled
                ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean isReadyForTextureCapture() {
        return !urlHasFocus();
    }

    @Override
    public void onTabOrModelChanged() {
        super.onTabOrModelChanged();
        boolean incognito = isIncognito();
        if (mUseLightColorAssets == null || mUseLightColorAssets != incognito) {
            setBackgroundResource(incognito
                    ? R.color.incognito_primary_color : R.color.default_primary_color);

            mMenuButton.setTint(incognito ? mLightModeTint : mDarkModeTint);
            mHomeButton.setTint(incognito ? mLightModeTint : mDarkModeTint);
            mBackButton.setTint(incognito ? mLightModeTint : mDarkModeTint);
            mForwardButton.setTint(incognito ? mLightModeTint : mDarkModeTint);
            mSaveOfflineButton.setTint(incognito ? mLightModeTint : mDarkModeTint);
            if (incognito) {
                mLocationBar.getContainerView().getBackground().setAlpha(
                        ToolbarPhone.LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA);
            } else {
                mLocationBar.getContainerView().getBackground().setAlpha(255);
            }
            mAccessibilitySwitcherButton.setImageDrawable(
                    incognito ? mTabSwitcherButtonDrawableLight : mTabSwitcherButtonDrawable);
            mLocationBar.updateVisualsForState();
            if (mShowMenuBadge) {
                setAppMenuUpdateBadgeDrawable(incognito);
            }
            mUseLightColorAssets = incognito;
        }

        updateNtp();
    }

    /**
     * Called when the currently visible New Tab Page changes.
     */
    private void updateNtp() {
        NewTabPage newVisibleNtp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        if (mVisibleNtp == newVisibleNtp) return;

        if (mVisibleNtp != null) {
            mVisibleNtp.setSearchBoxScrollListener(null);
        }
        mVisibleNtp = newVisibleNtp;
        if (mVisibleNtp != null) {
            mVisibleNtp.setSearchBoxScrollListener(new NewTabPage.OnSearchBoxScrollListener() {
                @Override
                public void onNtpScrollChanged(float scrollPercentage) {
                    // Fade the search box out in the first 40% of the scrolling transition.
                    float alpha = Math.max(1f - scrollPercentage * 2.5f, 0f);
                    mVisibleNtp.setSearchBoxAlpha(alpha);
                    mVisibleNtp.setSearchProviderLogoAlpha(alpha);
                }
            });
        }
    }

    @Override
    protected void onTabContentViewChanged() {
        super.onTabContentViewChanged();
        updateNtp();
    }

    @Override
    public void updateButtonVisibility() {
        mLocationBar.updateButtonVisibility();
    }

    @Override
    protected void updateBackButtonVisibility(boolean canGoBack) {
        boolean enableButton = canGoBack && !mIsInTabSwitcherMode;
        mBackButton.setEnabled(enableButton);
        mBackButton.setFocusable(enableButton);
    }

    @Override
    protected void updateForwardButtonVisibility(boolean canGoForward) {
        boolean enableButton = canGoForward && !mIsInTabSwitcherMode;
        mForwardButton.setEnabled(enableButton);
        mForwardButton.setFocusable(enableButton);
    }

    @Override
    protected void updateReloadButtonVisibility(boolean isReloading) {
        if (isReloading) {
            mReloadButton.setImageResource(R.drawable.btn_close);
            mReloadButton.setContentDescription(getContext().getString(
                    R.string.accessibility_btn_stop_loading));
        } else {
            mReloadButton.setImageResource(R.drawable.btn_toolbar_reload);
            mReloadButton.setContentDescription(getContext().getString(
                    R.string.accessibility_btn_refresh));
        }
        mReloadButton.setTint(isIncognito() ? mLightModeTint : mDarkModeTint);
        mReloadButton.setEnabled(!mIsInTabSwitcherMode);
    }

    @Override
    protected void updateBookmarkButton(boolean isBookmarked, boolean editingAllowed) {
        if (isBookmarked) {
            mBookmarkButton.setImageResource(R.drawable.btn_star_filled);
            // Non-incognito mode shows a blue filled star.
            mBookmarkButton.setTint(isIncognito()
                    ? mLightModeTint
                    : ApiCompatibilityUtils.getColorStateList(
                            getResources(), R.color.blue_mode_tint));
            mBookmarkButton.setContentDescription(getContext().getString(
                    R.string.edit_bookmark));
        } else {
            mBookmarkButton.setImageResource(R.drawable.btn_star);
            mBookmarkButton.setTint(isIncognito() ? mLightModeTint : mDarkModeTint);
            mBookmarkButton.setContentDescription(getContext().getString(
                    R.string.accessibility_menu_bookmark));
        }
        mBookmarkButton.setEnabled(editingAllowed);
    }

    @Override
    protected void setTabSwitcherMode(
            boolean inTabSwitcherMode, boolean showToolbar, boolean delayAnimation) {
        if (mShowTabStack && inTabSwitcherMode) {
            mIsInTabSwitcherMode = true;
            mBackButton.setEnabled(false);
            mForwardButton.setEnabled(false);
            mReloadButton.setEnabled(false);
            mLocationBar.getContainerView().setVisibility(View.INVISIBLE);
            if (mShowMenuBadge) {
                mMenuBadge.setVisibility(View.GONE);
                setMenuButtonContentDescription(false);
            }
        } else {
            mIsInTabSwitcherMode = false;
            mLocationBar.getContainerView().setVisibility(View.VISIBLE);
            if (mShowMenuBadge) {
                setAppMenuUpdateBadgeToVisible(false);
            }
        }
    }

    @Override
    protected void updateTabCountVisuals(int numberOfTabs) {
        mAccessibilitySwitcherButton.setContentDescription(
                getResources().getQuantityString(
                        R.plurals.accessibility_toolbar_btn_tabswitcher_toggle,
                        numberOfTabs, numberOfTabs));
        mTabSwitcherButtonDrawable.updateForTabCount(numberOfTabs, isIncognito());
        mTabSwitcherButtonDrawableLight.updateForTabCount(numberOfTabs, isIncognito());
    }

    @Override
    public void onAccessibilityStatusChanged(boolean enabled) {
        mShowTabStack = enabled || CommandLine.getInstance().hasSwitch(
                ChromeSwitches.ENABLE_TABLET_TAB_STACK);
        updateSwitcherButtonVisibility(enabled);
    }

    @Override
    public void setBookmarkClickHandler(OnClickListener listener) {
        mBookmarkListener = listener;
    }

    @Override
    public void setOnTabSwitcherClickHandler(OnClickListener listener) {
        mTabSwitcherListener = listener;
    }

    @Override
    protected void onHomeButtonUpdate(boolean homeButtonEnabled) {
        mHomeButton.setVisibility(homeButtonEnabled ? VISIBLE : GONE);
    }

    @Override
    public LocationBar getLocationBar() {
        return mLocationBar;
    }

    @Override
    public void showAppMenuUpdateBadge() {
        super.showAppMenuUpdateBadge();
        if (!mIsInTabSwitcherMode) {
            if (mUseLightColorAssets) {
                setAppMenuUpdateBadgeDrawable(mUseLightColorAssets);
            }
            setAppMenuUpdateBadgeToVisible(true);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // After the first layout, button visibility changes should be animated. On the first
        // layout, the button visibility shouldn't be animated because the visibility may be
        // changing solely because Chrome was launched into multi-window.
        mShouldAnimateButtonVisibilityChange = true;

        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Hide or show toolbar buttons if needed. With the introduction of multi-window on
        // Android N, the Activity can be < 600dp, in which case the toolbar buttons need to be
        // moved into the menu so that the location bar is usable. The buttons must be shown
        // in onMeasure() so that the location bar gets measured and laid out correctly.
        setToolbarButtonsVisible(MeasureSpec.getSize(widthMeasureSpec)
                >= DeviceFormFactor.getMinimumTabletWidthPx(getContext()));

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void setToolbarButtonsVisible(boolean visible) {
        if (mToolbarButtonsVisible == visible) return;

        mToolbarButtonsVisible = visible;

        if (mShouldAnimateButtonVisibilityChange) {
            runToolbarButtonsVisibilityAnimation(visible);
        } else {
            for (TintedImageButton button : mToolbarButtons) {
                button.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            mLocationBar.setShouldShowButtonsWhenUnfocused(visible);
            setStartPaddingBasedOnButtonVisibility(visible);
        }
    }

    /**
     * Sets the toolbar start padding based on whether the buttons are visible.
     * @param buttonsVisible Whether the toolbar buttons are visible.
     */
    private void setStartPaddingBasedOnButtonVisibility(boolean buttonsVisible) {
        buttonsVisible = buttonsVisible || mHomeButton.getVisibility() == View.VISIBLE;

        ApiCompatibilityUtils.setPaddingRelative(this,
                buttonsVisible ? mStartPaddingWithButtons : mStartPaddingWithoutButtons,
                getPaddingTop(),
                ApiCompatibilityUtils.getPaddingEnd(this),
                getPaddingBottom());
    }

    /**
     * @return The difference in start padding when the buttons are visible and when they are not
     *         visible.
     */
    public int getStartPaddingDifferenceForButtonVisibilityAnimation() {
        // If the home button is visible then the padding doesn't change.
        return mHomeButton.getVisibility() == View.VISIBLE ? 0
                : mStartPaddingWithButtons - mStartPaddingWithoutButtons;
    }

    private void runToolbarButtonsVisibilityAnimation(boolean visible) {
        if (mButtonVisibilityAnimators != null) mButtonVisibilityAnimators.cancel();

        mButtonVisibilityAnimators = visible ? buildShowToolbarButtonsAnimation()
                : buildHideToolbarButtonsAnimation();
        mButtonVisibilityAnimators.start();
    }

    private AnimatorSet buildShowToolbarButtonsAnimation() {
        Collection<Animator> animators = new ArrayList<>();

        // Create animators for all of the toolbar buttons.
        for (TintedImageButton button : mToolbarButtons) {
            animators.add(mLocationBar.createShowButtonAnimator(button));
        }

        // Add animators for location bar.
        animators.addAll(mLocationBar.getShowButtonsWhenUnfocusedAnimators(
                getStartPaddingDifferenceForButtonVisibilityAnimation()));

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                for (TintedImageButton button : mToolbarButtons) {
                    button.setVisibility(View.VISIBLE);
                }
                // Set the padding at the start of the animation so the toolbar buttons don't jump
                // when the animation ends.
                setStartPaddingBasedOnButtonVisibility(true);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mButtonVisibilityAnimators = null;
            }
        });

        return set;
    }

    private AnimatorSet buildHideToolbarButtonsAnimation() {
        Collection<Animator> animators = new ArrayList<>();

        // Create animators for all of the toolbar buttons.
        for (TintedImageButton button : mToolbarButtons) {
            animators.add(mLocationBar.createHideButtonAnimator(button));
        }

        // Add animators for location bar.
        animators.addAll(mLocationBar.getHideButtonsWhenUnfocusedAnimators(
                getStartPaddingDifferenceForButtonVisibilityAnimation()));

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Only set end visibility and alpha if the animation is ending because it's
                // completely finished and not because it was canceled.
                if (mToolbarButtons[0].getAlpha() == 0.f) {
                    for (TintedImageButton button : mToolbarButtons) {
                        button.setVisibility(View.GONE);
                        button.setAlpha(1.f);
                    }
                    // Set the padding at the end of the animation so the toolbar buttons don't jump
                    // when the animation starts.
                    setStartPaddingBasedOnButtonVisibility(false);
                }

                mButtonVisibilityAnimators = null;
            }
        });

        return set;
    }

}
