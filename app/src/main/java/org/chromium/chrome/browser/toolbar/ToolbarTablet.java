// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

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
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.widget.TintedImageButton;

/**
 * The Toolbar object for Tablet screens.
 */
@SuppressLint("Instantiatable")

public class ToolbarTablet extends ToolbarLayout implements OnClickListener {

    private TintedImageButton mHomeButton;
    private TintedImageButton mBackButton;
    private TintedImageButton mForwardButton;
    private TintedImageButton mReloadButton;
    private TintedImageButton mBookmarkButton;
    private ImageButton mAccessibilitySwitcherButton;

    private OnClickListener mBookmarkListener;
    private OnClickListener mTabSwitcherListener;

    private boolean mInTabSwitcherwMode = false;

    private boolean mShowTabStack;

    private NavigationPopup mNavigationPopup;

    private TabSwitcherDrawable mTabSwitcherButtonDrawable;
    private TabSwitcherDrawable mTabSwitcherButtonDrawableLight;

    private Boolean mUseLightColorAssets;
    private LocationBar mLocationBar;

    /**
     * Constructs a ToolbarTablet object.
     * @param context The Context in which this View object is created.
     * @param attrs The AttributeSet that was specified with this View.
     */
    public ToolbarTablet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mLocationBar = (LocationBar) findViewById(R.id.location_bar);

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
        mMenuButton.setVisibility(
                shouldShowMenuButton() ? View.VISIBLE : View.GONE);

        if (mAccessibilitySwitcherButton.getVisibility() == View.GONE
                && mMenuButton.getVisibility() == View.GONE) {
            ApiCompatibilityUtils.setPaddingRelative((View) mMenuButton.getParent(), 0, 0,
                    getResources().getDimensionPixelSize(R.dimen.tablet_toolbar_end_padding), 0);
        }
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
                mTabSwitcherListener.onClick(mAccessibilitySwitcherButton);
            }
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
            if (incognito) {
                mLocationBar.getContainerView().getBackground().setAlpha(
                        ToolbarPhone.LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA);
            } else {
                mLocationBar.getContainerView().getBackground().setAlpha(255);
            }
            mAccessibilitySwitcherButton.setImageDrawable(
                    incognito ? mTabSwitcherButtonDrawableLight : mTabSwitcherButtonDrawable);
            mLocationBar.updateVisualsForState();
            mUseLightColorAssets = incognito;
        }
        mLocationBar.setUrlBarFocus(false);
    }

    @Override
    protected void updateBackButtonVisibility(boolean canGoBack) {
        boolean enableButton = canGoBack && !mInTabSwitcherwMode;
        mBackButton.setEnabled(enableButton);
        mBackButton.setFocusable(enableButton);
    }

    @Override
    protected void updateForwardButtonVisibility(boolean canGoForward) {
        boolean enableButton = canGoForward && !mInTabSwitcherwMode;
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
        mReloadButton.setEnabled(!mInTabSwitcherwMode);
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
        } else {
            mBookmarkButton.setImageResource(R.drawable.btn_star);
            mBookmarkButton.setTint(isIncognito() ? mLightModeTint : mDarkModeTint);
        }
        mBookmarkButton.setEnabled(editingAllowed);
    }

    @Override
    protected void setTabSwitcherMode(
            boolean inTabSwitcherMode, boolean showToolbar, boolean delayAnimation) {
        if (mShowTabStack && inTabSwitcherMode) {
            mInTabSwitcherwMode = true;
            mBackButton.setEnabled(false);
            mForwardButton.setEnabled(false);
            mReloadButton.setEnabled(false);
            mLocationBar.getContainerView().setVisibility(View.INVISIBLE);
        } else {
            mInTabSwitcherwMode = false;
            mLocationBar.getContainerView().setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void updateTabCountVisuals(int numberOfTabs) {
        mAccessibilitySwitcherButton.setContentDescription(
                getResources().getString(R.string.accessibility_toolbar_btn_tabswitcher_toggle,
                        numberOfTabs));
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

}
