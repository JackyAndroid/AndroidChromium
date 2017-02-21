// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.Invalidator;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.omnibox.LocationBarPhone;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.chrome.browser.widget.newtab.NewTabButton;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phone specific toolbar implementation.
 */
public class ToolbarPhone extends ToolbarLayout
        implements Invalidator.Client, OnClickListener, OnLongClickListener,
                NewTabPage.OnSearchBoxScrollListener {

    /** The amount of time transitioning from one theme color to another should take in ms. */
    public static final long THEME_COLOR_TRANSITION_DURATION = 250;

    public static final int URL_FOCUS_CHANGE_ANIMATION_DURATION_MS = 250;
    private static final int URL_FOCUS_TOOLBAR_BUTTONS_TRANSLATION_X_DP = 10;
    private static final int URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS = 100;
    private static final int URL_CLEAR_FOCUS_TABSTACK_DELAY_MS = 200;
    private static final int URL_CLEAR_FOCUS_MENU_DELAY_MS = 250;

    private static final int TAB_SWITCHER_MODE_ENTER_ANIMATION_DURATION_MS = 200;
    private static final int TAB_SWITCHER_MODE_EXIT_NORMAL_ANIMATION_DURATION_MS = 200;
    private static final int TAB_SWITCHER_MODE_EXIT_FADE_ANIMATION_DURATION_MS = 100;
    private static final int TAB_SWITCHER_MODE_POST_EXIT_ANIMATION_DURATION_MS = 100;

    private static final float UNINITIALIZED_PERCENT = -1f;

    /** States that the toolbar can be in regarding the tab switcher. */
    private static final int STATIC_TAB = 0;
    private static final int TAB_SWITCHER = 1;
    private static final int ENTERING_TAB_SWITCHER = 2;
    private static final int EXITING_TAB_SWITCHER = 3;

    @ViewDebug.ExportedProperty(category = "chrome", mapping = {
            @ViewDebug.IntToString(from = STATIC_TAB, to = "STATIC_TAB"),
            @ViewDebug.IntToString(from = TAB_SWITCHER, to = "TAB_SWITCHER"),
            @ViewDebug.IntToString(from = ENTERING_TAB_SWITCHER, to = "ENTERING_TAB_SWITCHER"),
            @ViewDebug.IntToString(from = EXITING_TAB_SWITCHER, to = "EXITING_TAB_SWITCHER")
            })

    static final int LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA = 51;

    private static final Interpolator NTP_SEARCH_BOX_EXPANSION_INTERPOLATOR =
            new FastOutSlowInInterpolator();

    private LocationBarPhone mLocationBar;

    private ViewGroup mToolbarButtonsContainer;
    private ImageView mToggleTabStackButton;
    private NewTabButton mNewTabButton;
    private TintedImageButton mHomeButton;
    private TextView mUrlBar;
    private View mUrlActionContainer;
    private ImageView mToolbarShadow;

    private final int mProgressBackBackgroundColorWhite;

    private ObjectAnimator mTabSwitcherModeAnimation;
    private ObjectAnimator mDelayedTabSwitcherModeAnimation;

    private final List<View> mTabSwitcherModeViews = new ArrayList<>();
    private final Set<View> mBrowsingModeViews = new HashSet<>();
    @ViewDebug.ExportedProperty(category = "chrome")
    private int mTabSwitcherState;

    // This determines whether or not the toolbar draws as expected (false) or whether it always
    // draws as if it's showing the non-tabswitcher, non-animating toolbar. This is used in grabbing
    // a bitmap to use as a texture representation of this view.
    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mTextureCaptureMode;
    private boolean mForceTextureCapture;
    private boolean mUseLightDrawablesForTextureCapture;
    private boolean mLightDrawablesUsedForLastTextureCapture;

    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mAnimateNormalToolbar;
    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mDelayingTabSwitcherAnimation;

    private ColorDrawable mTabSwitcherAnimationBgOverlay;
    private TabSwitcherDrawable mTabSwitcherAnimationTabStackDrawable;
    private Drawable mTabSwitcherAnimationMenuDrawable;
    private Drawable mTabSwitcherAnimationMenuBadgeDarkDrawable;
    private Drawable mTabSwitcherAnimationMenuBadgeLightDrawable;
    // Value that determines the amount of transition from the normal toolbar mode to TabSwitcher
    // mode.  0 = entirely in normal mode and 1.0 = entirely in TabSwitcher mode.  In between values
    // can be used for animating between the two view modes.
    @ViewDebug.ExportedProperty(category = "chrome")
    private float mTabSwitcherModePercent = 0;

    // Used to clip the toolbar during the fade transition into and out of TabSwitcher mode.  Only
    // used when |mAnimateNormalToolbar| is false.
    @ViewDebug.ExportedProperty(category = "chrome")
    private Rect mClipRect;

    private OnClickListener mTabSwitcherListener;
    private OnClickListener mNewTabListener;

    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mUrlFocusChangeInProgress;

    /** 1.0 is 100% focused, 0 is completely unfocused */
    @ViewDebug.ExportedProperty(category = "chrome")
    private float mUrlFocusChangePercent;

    /**
     * The degree to which the omnibox has expanded to full width, either because it is getting
     * focused or the NTP search box is being scrolled up. Note that in the latter case, the actual
     * width of the omnibox is not interpolated linearly from this value. The value will be the
     * maximum of {@link #mUrlFocusChangePercent} and {@link #mNtpSearchBoxScrollPercent}.
     */
    @ViewDebug.ExportedProperty(category = "chrome")
    private float mUrlExpansionPercent;
    private AnimatorSet mUrlFocusLayoutAnimator;
    private boolean mDisableLocationBarRelayout;
    private boolean mLayoutLocationBarInFocusedMode;
    private int mUnfocusedLocationBarLayoutWidth;
    private int mUnfocusedLocationBarLayoutLeft;
    private boolean mUnfocusedLocationBarUsesTransparentBg;

    private int mLocationBarBackgroundAlpha = 255;
    private float mNtpSearchBoxScrollPercent = UNINITIALIZED_PERCENT;
    private ColorDrawable mToolbarBackground;

    /** The omnibox background (white with a shadow). */
    private Drawable mLocationBarBackground;

    private boolean mForceDrawLocationBarBackground;
    private TabSwitcherDrawable mTabSwitcherButtonDrawable;
    private TabSwitcherDrawable mTabSwitcherButtonDrawableLight;

    private final int mLightModeDefaultColor;
    private final int mDarkModeDefaultColor;

    /** The boundaries of the omnibox, without the NTP-specific offset applied. */
    private final Rect mLocationBarBackgroundBounds = new Rect();

    private final Rect mLocationBarBackgroundPadding = new Rect();
    private final Rect mBackgroundOverlayBounds = new Rect();

    /** Offset applied to the bounds of the omnibox if we are showing a New Tab Page. */
    private final Rect mLocationBarBackgroundNtpOffset = new Rect();

    /**
     * Offsets applied to the <i>contents</i> of the omnibox if we are showing a New Tab Page.
     * This can be different from {@link #mLocationBarBackgroundNtpOffset} due to the fact that we
     * extend the omnibox horizontally beyond the screen boundaries when focused, to hide its
     * rounded corners.
     */
    private float mLocationBarNtpOffsetLeft;
    private float mLocationBarNtpOffsetRight;

    private final Rect mNtpSearchBoxBounds = new Rect();
    private final Point mNtpSearchBoxTranslation = new Point();

    private final int mToolbarSidePadding;
    private final int mLocationBarVerticalMargin;
    private final int mLocationBarBackgroundCornerRadius;

    private ValueAnimator mBrandColorTransitionAnimation;
    private boolean mBrandColorTransitionActive;

    private boolean mIsHomeButtonEnabled;

    private LayoutUpdateHost mLayoutUpdateHost;

    /** Callout for the tab switcher button. */
    private TabSwitcherCallout mTabSwitcherCallout;

    /** Whether or not we've checked if the TabSwitcherCallout needs to be shown. */
    private boolean mHasCheckedIfTabSwitcherCalloutIsNecessary;

    /** Manages when the Toolbar hides and unhides. */
    private FullscreenManager mFullscreenManager;

    /** Token held when the TabSwitcherCallout is displayed to prevent the Toolbar from hiding. */
    private int mFullscreenCalloutToken = FullscreenManager.INVALID_TOKEN;

    /**
     * Used to specify the visual state of the toolbar.
     */
    private enum VisualState {
        TAB_SWITCHER_INCOGNITO,
        TAB_SWITCHER_NORMAL,
        NORMAL,
        INCOGNITO,
        BRAND_COLOR,
        NEW_TAB_NORMAL
    }

    private VisualState mVisualState = VisualState.NORMAL;
    private VisualState mOverlayDrawablesVisualState;
    private boolean mUseLightToolbarDrawables;

    private NewTabPage mVisibleNewTabPage;
    private float mPreTextureCaptureAlpha = 1f;
    private boolean mIsOverlayTabStackDrawableLight;

    // The following are some properties used during animation.  We use explicit property classes
    // to avoid the cost of reflection for each animation setup.

    private final Property<ToolbarPhone, Float> mUrlFocusChangePercentProperty =
            new Property<ToolbarPhone, Float>(Float.class, "") {
        @Override
        public Float get(ToolbarPhone object) {
            return object.mUrlFocusChangePercent;
        }

        @Override
        public void set(ToolbarPhone object, Float value) {
            setUrlFocusChangePercent(value);
        }
    };

    private final Property<ToolbarPhone, Float> mTabSwitcherModePercentProperty =
            new Property<ToolbarPhone, Float>(Float.class, "") {
        @Override
        public Float get(ToolbarPhone object) {
            return object.mTabSwitcherModePercent;
        }

        @Override
        public void set(ToolbarPhone object, Float value) {
            object.mTabSwitcherModePercent = value;
            triggerPaintInvalidate(ToolbarPhone.this);
        }
    };

    /**
     * Constructs a ToolbarPhone object.
     * @param context The Context in which this View object is created.
     * @param attrs The AttributeSet that was specified with this View.
     */
    public ToolbarPhone(Context context, AttributeSet attrs) {
        super(context, attrs);
        mToolbarSidePadding = getResources().getDimensionPixelOffset(
                R.dimen.toolbar_edge_padding);
        mLocationBarVerticalMargin =
                getResources().getDimensionPixelOffset(R.dimen.location_bar_vertical_margin);
        mLocationBarBackgroundCornerRadius =
                getResources().getDimensionPixelOffset(R.dimen.location_bar_corner_radius);
        mProgressBackBackgroundColorWhite = ApiCompatibilityUtils.getColor(getResources(),
                R.color.progress_bar_background_white);
        mLightModeDefaultColor =
                ApiCompatibilityUtils.getColor(getResources(), R.color.light_mode_tint);
        mDarkModeDefaultColor =
                ApiCompatibilityUtils.getColor(getResources(), R.color.dark_mode_tint);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mLocationBar = (LocationBarPhone) findViewById(R.id.location_bar);

        mToolbarButtonsContainer = (ViewGroup) findViewById(R.id.toolbar_buttons);

        mHomeButton = (TintedImageButton) findViewById(R.id.home_button);

        mUrlBar = (TextView) findViewById(R.id.url_bar);

        mUrlActionContainer = findViewById(R.id.url_action_container);

        mBrowsingModeViews.add(mLocationBar);

        mToolbarBackground = new ColorDrawable(getToolbarColorForVisualState(VisualState.NORMAL));
        mTabSwitcherAnimationBgOverlay =
                new ColorDrawable(getToolbarColorForVisualState(VisualState.NORMAL));

        mLocationBarBackground =
                ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.textbox);
        mLocationBarBackground.getPadding(mLocationBarBackgroundPadding);
        mLocationBar.setPadding(
                mLocationBarBackgroundPadding.left, mLocationBarBackgroundPadding.top,
                mLocationBarBackgroundPadding.right, mLocationBarBackgroundPadding.bottom);

        setLayoutTransition(null);

        mMenuButtonWrapper.setVisibility(View.VISIBLE);
        inflateTabSwitchingResources();

        setWillNotDraw(false);
    }

    private void inflateTabSwitchingResources() {
        mToggleTabStackButton = (ImageView) findViewById(R.id.tab_switcher_button);
        mNewTabButton = (NewTabButton) findViewById(R.id.new_tab_button);

        mToggleTabStackButton.setClickable(false);
        Resources resources = getResources();
        mTabSwitcherButtonDrawable =
                TabSwitcherDrawable.createTabSwitcherDrawable(resources, false);
        mTabSwitcherButtonDrawableLight =
                TabSwitcherDrawable.createTabSwitcherDrawable(resources, true);
        mToggleTabStackButton.setImageDrawable(mTabSwitcherButtonDrawable);
        mTabSwitcherModeViews.add(mNewTabButton);

        // Ensure that the new tab button will not draw over the toolbar buttons if the
        // translated string is long.  Set a margin to the size of the toolbar button container
        // for the new tab button.
        WindowManager wm = (WindowManager) getContext().getSystemService(
                Context.WINDOW_SERVICE);
        Point screenSize = new Point();
        wm.getDefaultDisplay().getSize(screenSize);

        mToolbarButtonsContainer.measure(
                MeasureSpec.makeMeasureSpec(screenSize.x, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(screenSize.y, MeasureSpec.AT_MOST));

        ApiCompatibilityUtils.setMarginEnd(getFrameLayoutParams(mNewTabButton),
                mToolbarButtonsContainer.getMeasuredWidth());
    }

    private void enableTabSwitchingResources() {
        mToggleTabStackButton.setOnClickListener(this);
        mToggleTabStackButton.setOnLongClickListener(this);
        mToggleTabStackButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                if (mMenuButton != null && mMenuButton.isShown()) {
                    return mMenuButton;
                } else {
                    return getCurrentTabView();
                }
            }

            @Override
            public View getNextFocusBackward() {
                return findViewById(R.id.url_bar);
            }
        });
        mNewTabButton.setOnClickListener(this);
    }

    @Override
    protected boolean onMenuButtonTouchEvent(View v, MotionEvent event) {
        dismissTabSwitcherCallout();
        return super.onMenuButtonTouchEvent(v, event);
    }

    /**
     * Sets up click and key listeners once we have native library available to handle clicks.
     */
    @Override
    public void onNativeLibraryReady() {
        super.onNativeLibraryReady();
        getLocationBar().onNativeLibraryReady();

        enableTabSwitchingResources();

        mHomeButton.setOnClickListener(this);

        mMenuButton.setOnKeyListener(new KeyboardNavigationListener() {
            @Override
            public View getNextFocusForward() {
                return getCurrentTabView();
            }

            @Override
            public View getNextFocusBackward() {
                return mToggleTabStackButton;
            }

            @Override
            protected boolean handleEnterKeyPress() {
                return getMenuButtonHelper().onEnterKeyPress(mMenuButton);
            }
        });
        onHomeButtonUpdate(HomepageManager.isHomepageEnabled(getContext()));

        updateVisualsForToolbarState();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // If the NTP is partially scrolled, prevent all touch events to the child views.  This
        // is to not allow a secondary touch event to trigger entering the tab switcher, which
        // can lead to really odd snapshots and transitions to the switcher.
        if (mNtpSearchBoxScrollPercent != 0f
                && mNtpSearchBoxScrollPercent != 1f
                && mNtpSearchBoxScrollPercent != UNINITIALIZED_PERCENT) {
            return true;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Forward touch events to the NTP if the toolbar is moved away but the search box hasn't
        // reached the top of the page yet.
        if (mNtpSearchBoxTranslation.y < 0 && mLocationBar.getTranslationY() > 0) {
            NewTabPage newTabPage = getToolbarDataProvider().getNewTabPageForCurrentTab();

            // No null check -- the toolbar should not be moved if we are not on an NTP.
            return newTabPage.getView().dispatchTouchEvent(ev);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void onClick(View v) {
        // Don't allow clicks while the omnibox is being focused.
        if (mLocationBar != null && mLocationBar.hasFocus()) return;

        if (mToggleTabStackButton == v) {
            // The button is clickable before the native library is loaded
            // and the listener is setup.
            if (mToggleTabStackButton != null && mToggleTabStackButton.isClickable()
                    && mTabSwitcherListener != null) {
                dismissTabSwitcherCallout();
                cancelAppMenuUpdateBadgeAnimation();
                mTabSwitcherListener.onClick(mToggleTabStackButton);
                RecordUserAction.record("MobileToolbarShowStackView");
            }
        } else if (mNewTabButton == v) {
            v.setEnabled(false);

            if (mNewTabListener != null) {
                mNewTabListener.onClick(v);
                RecordUserAction.record("MobileToolbarStackViewNewTab");
                RecordUserAction.record("MobileNewTabOpened");
                // TODO(kkimlabs): Record UMA action for homepage button.
            }
        } else if (mHomeButton == v) {
            openHomepage();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        CharSequence description = null;
        if (v == mToggleTabStackButton) {
            description = getResources().getString(R.string.open_tabs);
        } else {
            return false;
        }
        return showAccessibilityToast(v, description);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mDisableLocationBarRelayout) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            boolean changed = layoutLocationBar(MeasureSpec.getSize(widthMeasureSpec));
            if (mTabSwitcherState == STATIC_TAB) updateUrlExpansionAnimation();
            if (!changed) return;
        } else {
            updateUnfocusedLocationBarLayoutParams();
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateUnfocusedLocationBarLayoutParams() {
        boolean hasVisibleViewPriorToUrlBar = false;
        for (int i = 0; i < mLocationBar.getChildCount(); i++) {
            View child = mLocationBar.getChildAt(i);
            if (child == mUrlBar) break;
            if (child.getVisibility() != GONE) {
                hasVisibleViewPriorToUrlBar = true;
                break;
            }
        }

        int leftViewBounds = getViewBoundsLeftOfLocationBar(mVisualState);
        int rightViewBounds = getViewBoundsRightOfLocationBar(mVisualState);

        if (!hasVisibleViewPriorToUrlBar) {
            if (ApiCompatibilityUtils.isLayoutRtl(mLocationBar)) {
                rightViewBounds -= mToolbarSidePadding;
            } else {
                leftViewBounds += mToolbarSidePadding;
            }
        }

        // Add spacing between the end of the URL and the edge of the omnibox drawable.
        // This only applies if there is no end aligned view that should be visible
        // while the omnibox is unfocused.
        if (ApiCompatibilityUtils.isLayoutRtl(mLocationBar)) {
            leftViewBounds += mToolbarSidePadding;
        } else {
            rightViewBounds -= mToolbarSidePadding;
        }

        mUnfocusedLocationBarLayoutWidth = rightViewBounds - leftViewBounds;
        mUnfocusedLocationBarLayoutLeft = leftViewBounds;
    }

    /**
     * @return The background drawable for the fullscreen overlay.
     */
    @VisibleForTesting
    ColorDrawable getOverlayDrawable() {
        return mTabSwitcherAnimationBgOverlay;
    }

    /**
     * @return The background drawable for the toolbar view.
     */
    @VisibleForTesting
    ColorDrawable getBackgroundDrawable() {
        return mToolbarBackground;
    }

    @SuppressLint("RtlHardcoded")
    private boolean layoutLocationBar(int containerWidth) {
        // Note that Toolbar's direction depends on system layout direction while
        // LocationBar's direction depends on its text inside.
        FrameLayout.LayoutParams locationBarLayoutParams =
                getFrameLayoutParams(getLocationBar().getContainerView());

        // Chrome prevents layout_gravity="left" from being defined in XML, but it simplifies
        // the logic, so it is manually specified here.
        locationBarLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        int width = 0;
        int leftMargin = 0;

        // Always update the unfocused layout params regardless of whether we are using
        // those in this current layout pass as they are needed for animations.
        updateUnfocusedLocationBarLayoutParams();

        if (mLayoutLocationBarInFocusedMode || mVisualState == VisualState.NEW_TAB_NORMAL) {
            int priorVisibleWidth = 0;
            for (int i = 0; i < mLocationBar.getChildCount(); i++) {
                View child = mLocationBar.getChildAt(i);
                if (child == mLocationBar.getFirstViewVisibleWhenFocused()) break;
                if (child.getVisibility() == GONE) continue;
                priorVisibleWidth += child.getMeasuredWidth();
            }

            width = containerWidth - (2 * mToolbarSidePadding) + priorVisibleWidth;
            if (ApiCompatibilityUtils.isLayoutRtl(mLocationBar)) {
                leftMargin = mToolbarSidePadding;
            } else {
                leftMargin = -priorVisibleWidth + mToolbarSidePadding;
            }
        } else {
            width = mUnfocusedLocationBarLayoutWidth;
            leftMargin = mUnfocusedLocationBarLayoutLeft;
        }

        boolean changed = false;
        changed |= (width != locationBarLayoutParams.width);
        locationBarLayoutParams.width = width;

        changed |= (leftMargin != locationBarLayoutParams.leftMargin);
        locationBarLayoutParams.leftMargin = leftMargin;

        return changed;
    }

    private int getViewBoundsLeftOfLocationBar(VisualState visualState) {
        // Uses getMeasuredWidth()s instead of getLeft() because this is called in onMeasure
        // and the layout values have not yet been set.
        if (visualState == VisualState.NEW_TAB_NORMAL) {
            return 0;
        } else if (ApiCompatibilityUtils.isLayoutRtl(this)) {
            return Math.max(
                    mToolbarSidePadding, mToolbarButtonsContainer.getMeasuredWidth());
        } else {
            return getBoundsAfterAccountingForLeftButton();
        }
    }

    private int getBoundsAfterAccountingForLeftButton() {
        int padding = mToolbarSidePadding;
        if (mHomeButton.getVisibility() != GONE) padding = mHomeButton.getMeasuredWidth();
        return padding;
    }

    private int getViewBoundsRightOfLocationBar(VisualState visualState) {
        // Uses getMeasuredWidth()s instead of getRight() because this is called in onMeasure
        // and the layout values have not yet been set.
        if (visualState == VisualState.NEW_TAB_NORMAL) {
            return getMeasuredWidth();
        } else if (ApiCompatibilityUtils.isLayoutRtl(this)) {
            return getMeasuredWidth() - getBoundsAfterAccountingForLeftButton();
        } else {
            int margin = Math.max(
                    mToolbarSidePadding, mToolbarButtonsContainer.getMeasuredWidth());
            return getMeasuredWidth() - margin;
        }
    }

    private void updateToolbarBackground(int color) {
        mToolbarBackground.setColor(color);
        invalidate();
    }

    private void updateToolbarBackground(VisualState visualState) {
        updateToolbarBackground(getToolbarColorForVisualState(visualState));
    }

    private int getToolbarColorForVisualState(final VisualState visualState) {
        Resources res = getResources();
        switch (visualState) {
            case NEW_TAB_NORMAL:
                return Color.TRANSPARENT;
            case NORMAL:
                return ApiCompatibilityUtils.getColor(res, R.color.default_primary_color);
            case INCOGNITO:
                return ApiCompatibilityUtils.getColor(res, R.color.incognito_primary_color);
            case BRAND_COLOR:
                return getToolbarDataProvider().getPrimaryColor();
            case TAB_SWITCHER_NORMAL:
            case TAB_SWITCHER_INCOGNITO:
                return ApiCompatibilityUtils.getColor(res, R.color.tab_switcher_background);
            default:
                assert false;
                return ApiCompatibilityUtils.getColor(res, R.color.default_primary_color);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!mTextureCaptureMode && mToolbarBackground.getColor() != Color.TRANSPARENT) {
            // Update to compensate for orientation changes.
            mToolbarBackground.setBounds(0, 0, getWidth(), getHeight());
            mToolbarBackground.draw(canvas);
        }

        if (mLocationBarBackground != null
                && (mLocationBar.getVisibility() == VISIBLE || mTextureCaptureMode)) {
            updateLocationBarBackgroundBounds(mLocationBarBackgroundBounds, mVisualState);
        }

        if (mTextureCaptureMode) {
            drawTabSwitcherAnimationOverlay(canvas, 0.f);
        } else {
            boolean tabSwitcherAnimationFinished = false;
            if (mTabSwitcherModeAnimation != null) {
                tabSwitcherAnimationFinished = !mTabSwitcherModeAnimation.isRunning();

                // Perform the fade logic before super.dispatchDraw(canvas) so that we can properly
                // set the values before the draw happens.
                if (!mAnimateNormalToolbar) {
                    drawTabSwitcherFadeAnimation(
                            tabSwitcherAnimationFinished, mTabSwitcherModePercent);
                }
            }

            super.dispatchDraw(canvas);

            if (mTabSwitcherModeAnimation != null) {
                // Perform the overlay logic after super.dispatchDraw(canvas) as we need to draw on
                // top of the current views.
                if (mAnimateNormalToolbar) {
                    drawTabSwitcherAnimationOverlay(canvas, mTabSwitcherModePercent);
                }

                // Clear the animation.
                if (tabSwitcherAnimationFinished) mTabSwitcherModeAnimation = null;
            }
        }
    }

    // NewTabPage.OnSearchBoxScrollListener
    @Override
    public void onNtpScrollChanged(float scrollPercentage) {
        // TODO(peconn): Clear up the animation transition calculations so that the parts that
        // depend on the absolute scroll value (such as the Toolbar location) are separate from the
        // parts that depend on the fakebox transition percentage (such as the omnibox width and
        // opacity).
        // At the moment, we disable the check below because these two concepts are not
        // separate and we want to still update the parts that depend on scroll value when the
        // transition percentage is not changed.
        if (scrollPercentage == mNtpSearchBoxScrollPercent
                && !getToolbarDataProvider().getNewTabPageForCurrentTab().isCardsUiEnabled()) {
            return;
        }

        mNtpSearchBoxScrollPercent = scrollPercentage;
        updateUrlExpansionPercent();
        updateUrlExpansionAnimation();
    }

    /**
     * Calculate the bounds for the location bar background and set them to {@code out}.
     */
    private void updateLocationBarBackgroundBounds(Rect out, VisualState visualState) {
        // Calculate the visible boundaries of the left and right most child views of the
        // location bar.
        float expansion = visualState == VisualState.NEW_TAB_NORMAL ? 1 : mUrlExpansionPercent;
        int leftViewPosition = (int) MathUtils.interpolate(
                getViewBoundsLeftOfLocationBar(visualState),
                -mLocationBarBackgroundCornerRadius,
                expansion);
        int rightViewPosition = (int) MathUtils.interpolate(
                getViewBoundsRightOfLocationBar(visualState),
                getWidth() + mLocationBarBackgroundCornerRadius,
                expansion);

        // The bounds are set by the following:
        // - The left most visible location bar child view.
        // - The top of the viewport is aligned with the top of the location bar.
        // - The right most visible location bar child view.
        // - The bottom of the viewport is aligned with the bottom of the location bar.
        // Additional padding can be applied for use during animations.
        int verticalMargin = (int) MathUtils.interpolate(mLocationBarVerticalMargin, 0, expansion);
        out.set(leftViewPosition,
                mLocationBar.getTop() + verticalMargin,
                rightViewPosition,
                mLocationBar.getBottom() - verticalMargin);
    }

    /**
     * Updates percentage of current the URL focus change animation.
     * @param percent 1.0 is 100% focused, 0 is completely unfocused.
     */
    private void setUrlFocusChangePercent(float percent) {
        mUrlFocusChangePercent = percent;
        updateUrlExpansionPercent();
        updateUrlExpansionAnimation();
    }

    private void updateUrlExpansionPercent() {
        mUrlExpansionPercent = Math.max(mNtpSearchBoxScrollPercent, mUrlFocusChangePercent);
        assert mUrlExpansionPercent >= 0;
        assert mUrlExpansionPercent <= 1;
    }

    /**
     * Updates the parameters relating to expanding the location bar, as the result of either a
     * focus change or scrolling the New Tab Page.
     */
    private void updateUrlExpansionAnimation() {
        if (mTabSwitcherState != STATIC_TAB) {
            mToolbarButtonsContainer.setVisibility(VISIBLE);
            return;
        }

        FrameLayout.LayoutParams locationBarLayoutParams = getFrameLayoutParams(mLocationBar);
        int currentLeftMargin = locationBarLayoutParams.leftMargin;
        int currentWidth = locationBarLayoutParams.width;

        float locationBarBaseTranslationX = mUnfocusedLocationBarLayoutLeft - currentLeftMargin;
        boolean isLocationBarRtl = ApiCompatibilityUtils.isLayoutRtl(mLocationBar);
        if (isLocationBarRtl) {
            locationBarBaseTranslationX += mUnfocusedLocationBarLayoutWidth - currentWidth;
        }
        locationBarBaseTranslationX *= 1f - mUrlExpansionPercent;

        mLocationBarBackgroundNtpOffset.setEmpty();
        mLocationBarNtpOffsetLeft = 0;
        mLocationBarNtpOffsetRight = 0;

        Tab currentTab = getToolbarDataProvider().getTab();
        if (currentTab != null) {
            NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
            // Explicitly use the focus change percentage here because it applies scroll
            // compensation that only applies during focus animations.
            if (ntp != null && mUrlFocusChangeInProgress) {
                ntp.setUrlFocusChangeAnimationPercent(mUrlFocusChangePercent);
            }

            if (isLocationBarShownInNTP()) {
                updateNtpTransitionAnimation();
            } else {
                // Reset these values in case we transitioned to a different page during the
                // transition.
                resetNtpAnimationValues();
            }
        }

        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);

        float locationBarTranslationX;
        // Get the padding straight from the location bar instead of
        // |mLocationBarBackgroundPadding|, because it might be different in incognito mode.
        if (isRtl) {
            locationBarTranslationX = locationBarBaseTranslationX
                    + mLocationBarNtpOffsetRight - mLocationBar.getPaddingRight();
        } else {
            locationBarTranslationX = locationBarBaseTranslationX
                    + mLocationBarNtpOffsetLeft + mLocationBar.getPaddingLeft();
        }
        mLocationBar.setTranslationX(locationBarTranslationX);

        // Negate the location bar translation to keep the URL action container in the same
        // place during the focus expansion.
        float urlActionsTranslationX = 0;
        if (!isLocationBarRtl || isRtl) {
            urlActionsTranslationX = -locationBarBaseTranslationX;
        }

        if (isRtl) {
            urlActionsTranslationX += mLocationBarNtpOffsetLeft - mLocationBarNtpOffsetRight;
        } else {
            urlActionsTranslationX += mLocationBarNtpOffsetRight - mLocationBarNtpOffsetLeft;
        }
        mUrlActionContainer.setTranslationX(urlActionsTranslationX);

        mLocationBar.setUrlFocusChangePercent(mUrlExpansionPercent);

        // Ensure the buttons are invisible after focusing the omnibox to prevent them from
        // accepting click events.
        int toolbarButtonVisibility = mUrlExpansionPercent == 1f ? INVISIBLE : VISIBLE;
        mToolbarButtonsContainer.setVisibility(toolbarButtonVisibility);
        if (mHomeButton.getVisibility() != GONE) {
            mHomeButton.setVisibility(toolbarButtonVisibility);
        }

        // Force an invalidation of the location bar to properly handle the clipping of the URL
        // bar text as a result of the url action container translations.
        mLocationBar.invalidate();
        invalidate();
    }

    /**
     * Reset the parameters for the New Tab Page transition animation (expanding the location bar as
     * a result of scrolling the New Tab Page) to their default values.
     */
    private void resetNtpAnimationValues() {
        mLocationBarBackgroundNtpOffset.setEmpty();
        mNtpSearchBoxTranslation.set(0, 0);
        mLocationBar.setTranslationY(0);
        if (!mUrlFocusChangeInProgress) {
            mToolbarButtonsContainer.setTranslationY(0);
            mHomeButton.setTranslationY(0);
        }
        mToolbarShadow.setAlpha(1f);
        mLocationBar.setAlpha(1);
        mForceDrawLocationBarBackground = false;
        mLocationBarBackgroundAlpha = 255;
        if (isIncognito()
                || (mUnfocusedLocationBarUsesTransparentBg && !mUrlFocusChangeInProgress
                        && !mLocationBar.hasFocus())) {
            mLocationBarBackgroundAlpha = LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
        }
        setAncestorsShouldClipChildren(true);
        mNtpSearchBoxScrollPercent = UNINITIALIZED_PERCENT;
        updateUrlExpansionPercent();
    }

    /**
     * Updates the parameters of the New Tab Page transition animation (expanding the location bar
     * as a result of scrolling the New Tab Page).
     */
    private void updateNtpTransitionAnimation() {
        // Skip if in or entering tab switcher mode.
        if (mTabSwitcherState == TAB_SWITCHER || mTabSwitcherState == ENTERING_TAB_SWITCHER) return;

        setAncestorsShouldClipChildren(mUrlExpansionPercent == 0f);
        mToolbarShadow.setAlpha(0f);

        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        ntp.getSearchBoxBounds(mNtpSearchBoxBounds, mNtpSearchBoxTranslation);
        int locationBarTranslationY =
                Math.max(0, (mNtpSearchBoxBounds.top - mLocationBar.getTop()));
        mLocationBar.setTranslationY(locationBarTranslationY);
        updateButtonsTranslationY();

        // Linearly interpolate between the bounds of the search box on the NTP and the omnibox
        // background bounds. |shrinkage| is the scaling factor for the offset -- if it's 1, we are
        // shrinking the omnibox down to the size of the search box.
        float shrinkage;
        if (ntp.isCardsUiEnabled()) {
            shrinkage = 1f
                    - NTP_SEARCH_BOX_EXPANSION_INTERPOLATOR.getInterpolation(mUrlExpansionPercent);
        } else {
            // During the transition from middle of the NTP to the top, keep the omnibox drawing
            // at the same size of the search box for first 40% of the scroll transition.
            shrinkage = Math.min(1f, (1f - mUrlExpansionPercent) * 1.66667f);
        }

        int leftBoundDifference = mNtpSearchBoxBounds.left - mLocationBarBackgroundBounds.left;
        int rightBoundDifference = mNtpSearchBoxBounds.right - mLocationBarBackgroundBounds.right;
        mLocationBarBackgroundNtpOffset.set(
                Math.round(leftBoundDifference * shrinkage),
                locationBarTranslationY,
                Math.round(rightBoundDifference * shrinkage),
                locationBarTranslationY);

        // The omnibox background bounds are outset by |mLocationBarBackgroundCornerRadius| in the
        // fully expanded state (and only there!) to hide the rounded corners, so undo that before
        // applying the shrinkage factor.
        mLocationBarNtpOffsetLeft =
                (leftBoundDifference - mLocationBarBackgroundCornerRadius) * shrinkage;
        mLocationBarNtpOffsetRight =
                (rightBoundDifference + mLocationBarBackgroundCornerRadius) * shrinkage;

        mLocationBarBackgroundAlpha = mUrlExpansionPercent > 0f ? 255 : 0;
        mForceDrawLocationBarBackground = mLocationBarBackgroundAlpha > 0;
        float relativeAlpha = mLocationBarBackgroundAlpha / 255f;
        mLocationBar.setAlpha(relativeAlpha);

        // The search box on the NTP is visible if our omnibox is invisible, and vice-versa.
        ntp.setSearchBoxAlpha(1f - relativeAlpha);

        if (!ntp.isCardsUiEnabled()) {
            ntp.setSearchProviderLogoAlpha(Math.max(1f - mUrlExpansionPercent * 2.5f, 0f));
        }
    }

    /**
     * Update the y translation of the buttons to make it appear as if they were scrolling with
     * the new tab page.
     */
    private void updateButtonsTranslationY() {
        int transY = mTabSwitcherState == STATIC_TAB ? Math.min(mNtpSearchBoxTranslation.y, 0) : 0;

        mToolbarButtonsContainer.setTranslationY(transY);
        mHomeButton.setTranslationY(transY);
    }

    private void setAncestorsShouldClipChildren(boolean clip) {
        if (!isLocationBarShownInNTP()) return;
        ViewGroup parent = this;
        while (parent != null) {
            parent.setClipChildren(clip);
            if (!(parent.getParent() instanceof ViewGroup)) break;
            if (parent.getId() == android.R.id.content) break;
            parent = (ViewGroup) parent.getParent();
        }
    }

    private void drawTabSwitcherFadeAnimation(boolean animationFinished, float progress) {
        setAlpha(progress);
        if (animationFinished) {
            mClipRect = null;
        } else if (mClipRect == null) {
            mClipRect = new Rect();
        }
        if (mClipRect != null) mClipRect.set(0, 0, getWidth(), (int) (getHeight() * progress));
    }

    /**
     * When entering and exiting the TabSwitcher mode, we fade out or fade in the browsing
     * mode of the toolbar on top of the TabSwitcher mode version of it.  We do this by
     * drawing all of the browsing mode views on top of the android view.
     */
    private void drawTabSwitcherAnimationOverlay(Canvas canvas, float animationProgress) {
        if (!isNativeLibraryReady()) return;

        float floatAlpha = 1 - animationProgress;
        int rgbAlpha = (int) (255 * floatAlpha);
        canvas.save();
        canvas.translate(0, -animationProgress * mBackgroundOverlayBounds.height());
        canvas.clipRect(mBackgroundOverlayBounds);

        float previousAlpha = 0.f;
        if (mHomeButton.getVisibility() != View.GONE) {
            // Draw the New Tab button used in the URL view.
            previousAlpha = mHomeButton.getAlpha();
            mHomeButton.setAlpha(previousAlpha * floatAlpha);
            drawChild(canvas, mHomeButton, SystemClock.uptimeMillis());
            mHomeButton.setAlpha(previousAlpha);
        }

        // Draw the location/URL bar.
        previousAlpha = mLocationBar.getAlpha();
        mLocationBar.setAlpha(previousAlpha * floatAlpha);
        // If the location bar is now fully transparent, do not bother drawing it.
        if (mLocationBar.getAlpha() != 0) {
            drawChild(canvas, mLocationBar, SystemClock.uptimeMillis());
        }
        mLocationBar.setAlpha(previousAlpha);

        // Draw the tab stack button and associated text.
        translateCanvasToView(this, mToolbarButtonsContainer, canvas);

        if (mTabSwitcherAnimationTabStackDrawable != null && mToggleTabStackButton != null
                && mUrlExpansionPercent != 1f) {
            // Draw the tab stack button image.
            canvas.save();
            translateCanvasToView(mToolbarButtonsContainer, mToggleTabStackButton, canvas);

            int backgroundWidth = mToggleTabStackButton.getDrawable().getIntrinsicWidth();
            int backgroundHeight = mToggleTabStackButton.getDrawable().getIntrinsicHeight();
            int backgroundLeft = (mToggleTabStackButton.getWidth()
                    - mToggleTabStackButton.getPaddingLeft()
                    - mToggleTabStackButton.getPaddingRight() - backgroundWidth) / 2;
            backgroundLeft += mToggleTabStackButton.getPaddingLeft();
            int backgroundTop = (mToggleTabStackButton.getHeight()
                    - mToggleTabStackButton.getPaddingTop()
                    - mToggleTabStackButton.getPaddingBottom() - backgroundHeight) / 2;
            backgroundTop += mToggleTabStackButton.getPaddingTop();
            canvas.translate(backgroundLeft, backgroundTop);

            mTabSwitcherAnimationTabStackDrawable.setAlpha(rgbAlpha);
            mTabSwitcherAnimationTabStackDrawable.draw(canvas);
            canvas.restore();
        }

        // Draw the menu button if necessary.
        if (!mShowMenuBadge && mTabSwitcherAnimationMenuDrawable != null
                && mUrlExpansionPercent != 1f) {
            mTabSwitcherAnimationMenuDrawable.setBounds(
                    mMenuButton.getPaddingLeft(), mMenuButton.getPaddingTop(),
                    mMenuButton.getWidth() - mMenuButton.getPaddingRight(),
                    mMenuButton.getHeight() - mMenuButton.getPaddingBottom());
            translateCanvasToView(mToolbarButtonsContainer, mMenuButton, canvas);
            mTabSwitcherAnimationMenuDrawable.setAlpha(rgbAlpha);
            int color = mUseLightDrawablesForTextureCapture
                    ? mLightModeDefaultColor
                    : mDarkModeDefaultColor;
            mTabSwitcherAnimationMenuDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            mTabSwitcherAnimationMenuDrawable.draw(canvas);
        }

        // Draw the menu badge if necessary.
        Drawable badgeDrawable = mUseLightDrawablesForTextureCapture
                ? mTabSwitcherAnimationMenuBadgeLightDrawable
                        : mTabSwitcherAnimationMenuBadgeDarkDrawable;
        if (mShowMenuBadge && badgeDrawable != null && mUrlExpansionPercent != 1f) {
            badgeDrawable.setBounds(
                    mMenuBadge.getPaddingLeft(), mMenuBadge.getPaddingTop(),
                    mMenuBadge.getWidth() - mMenuBadge.getPaddingRight(),
                    mMenuBadge.getHeight() - mMenuBadge.getPaddingBottom());
            translateCanvasToView(mToolbarButtonsContainer, mMenuBadge, canvas);
            badgeDrawable.setAlpha(rgbAlpha);
            badgeDrawable.draw(canvas);
        }

        mLightDrawablesUsedForLastTextureCapture = mUseLightDrawablesForTextureCapture;

        canvas.restore();
    }

    @Override
    public void doInvalidate() {
        postInvalidateOnAnimation();
    }

    /**
     * Translates the canvas to ensure the specified view's coordinates are at 0, 0.
     *
     * @param from The view the canvas is currently translated to.
     * @param to The view to translate to.
     * @param canvas The canvas to be translated.
     *
     * @throws IllegalArgumentException if {@code from} is not an ancestor of {@code to}.
     */
    private static void translateCanvasToView(View from, View to, Canvas canvas)
            throws IllegalArgumentException {
        assert from != null;
        assert to != null;
        while (to != from) {
            canvas.translate(to.getLeft(), to.getTop());
            if (!(to.getParent() instanceof View)) {
                throw new IllegalArgumentException("View 'to' was not a desendent of 'from'.");
            }
            to = (View) to.getParent();
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == mLocationBar) return drawLocationBar(canvas, drawingTime);
        boolean clipped = false;

        if (mLocationBarBackground != null
                && ((mTabSwitcherState == STATIC_TAB && !mTabSwitcherModeViews.contains(child))
                        || (mTabSwitcherState != STATIC_TAB
                                && mBrowsingModeViews.contains(child)))) {
            canvas.save();

            int translationY = (int) mLocationBar.getTranslationY();
            int clipTop = mLocationBarBackgroundBounds.top - mLocationBarBackgroundPadding.top
                    + translationY;
            if (mUrlExpansionPercent != 0f && clipTop < child.getBottom()) {
                // For other child views, use the inverse clipping of the URL viewport.
                // Only necessary during animations.
                // Hardware mode does not support unioned clip regions, so clip using the
                // appropriate bounds based on whether the child is to the left or right of the
                // location bar.
                boolean isLeft = (child == mNewTabButton
                        || child == mHomeButton) ^ LocalizationUtils.isLayoutRtl();

                int clipBottom = mLocationBarBackgroundBounds.bottom
                        + mLocationBarBackgroundPadding.bottom + translationY;
                boolean verticalClip = false;
                if (translationY > 0f) {
                    clipTop = child.getTop();
                    clipBottom = clipTop;
                    verticalClip = true;
                }

                if (isLeft) {
                    int clipRight = verticalClip ? child.getMeasuredWidth()
                            : mLocationBarBackgroundBounds.left
                                    - mLocationBarBackgroundPadding.left;
                    canvas.clipRect(0, clipTop, clipRight, clipBottom);
                } else {
                    int clipLeft = verticalClip ? 0 : mLocationBarBackgroundBounds.right
                            + mLocationBarBackgroundPadding.right;
                    canvas.clipRect(clipLeft, clipTop, getMeasuredWidth(), clipBottom);
                }
            }
            clipped = true;
        }
        boolean retVal = super.drawChild(canvas, child, drawingTime);
        if (clipped) canvas.restore();
        return retVal;
    }

    private boolean drawLocationBar(Canvas canvas, long drawingTime) {
        boolean clipped = false;

        if (mLocationBarBackground != null
                && (mTabSwitcherState == STATIC_TAB || mTextureCaptureMode)) {
            canvas.save();
            int backgroundAlpha;
            if (mTabSwitcherModeAnimation != null) {
                // Fade out/in the location bar towards the beginning of the animations to avoid
                // large jumps of stark white.
                backgroundAlpha =
                        (int) (Math.pow(mLocationBar.getAlpha(), 3) * mLocationBarBackgroundAlpha);
            } else if (getToolbarDataProvider().isUsingBrandColor()
                    && !mBrandColorTransitionActive) {
                backgroundAlpha = mUnfocusedLocationBarUsesTransparentBg
                        ? (int) (MathUtils.interpolate(LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA,
                                255, mUrlExpansionPercent))
                        : 255;
            } else {
                backgroundAlpha = mLocationBarBackgroundAlpha;
            }
            mLocationBarBackground.setAlpha(backgroundAlpha);

            if ((mLocationBar.getAlpha() > 0 || mForceDrawLocationBarBackground)
                    && !mTextureCaptureMode) {
                mLocationBarBackground.setBounds(
                        mLocationBarBackgroundBounds.left + mLocationBarBackgroundNtpOffset.left
                                - mLocationBarBackgroundPadding.left,
                        mLocationBarBackgroundBounds.top + mLocationBarBackgroundNtpOffset.top
                                - mLocationBarBackgroundPadding.top,
                        mLocationBarBackgroundBounds.right + mLocationBarBackgroundNtpOffset.right
                                + mLocationBarBackgroundPadding.right,
                        mLocationBarBackgroundBounds.bottom + mLocationBarBackgroundNtpOffset.bottom
                                + mLocationBarBackgroundPadding.bottom);
                mLocationBarBackground.draw(canvas);
            }

            float locationBarClipLeft =
                    mLocationBarBackgroundBounds.left + mLocationBarBackgroundNtpOffset.left;
            float locationBarClipRight =
                    mLocationBarBackgroundBounds.right + mLocationBarBackgroundNtpOffset.right;
            float locationBarClipTop =
                    mLocationBarBackgroundBounds.top + mLocationBarBackgroundNtpOffset.top;
            float locationBarClipBottom =
                    mLocationBarBackgroundBounds.bottom + mLocationBarBackgroundNtpOffset.bottom;

            // When unexpanded, the location bar's visible content boundaries are inset from the
            // viewport used to draw the background.  During expansion transitions, compensation
            // is applied to increase the clip regions such that when the location bar converts
            // to the narrower collapsed layout that the visible content is the same.
            if (mUrlExpansionPercent != 1f) {
                int leftDelta = mUnfocusedLocationBarLayoutLeft
                        - getViewBoundsLeftOfLocationBar(mVisualState);
                int rightDelta = getViewBoundsRightOfLocationBar(mVisualState)
                        - mUnfocusedLocationBarLayoutLeft
                        - mUnfocusedLocationBarLayoutWidth;
                float inversePercent = 1f - mUrlExpansionPercent;
                locationBarClipLeft += leftDelta * inversePercent;
                locationBarClipRight -= rightDelta * inversePercent;
            }

            // Clip the location bar child to the URL viewport calculated in onDraw.
            canvas.clipRect(
                    locationBarClipLeft, locationBarClipTop,
                    locationBarClipRight, locationBarClipBottom);
            clipped = true;
        }

        boolean retVal = super.drawChild(canvas, mLocationBar, drawingTime);

        if (clipped) canvas.restore();
        return retVal;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBackgroundOverlayBounds.set(0, 0, w, mToolbarHeightWithoutShadow);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mToolbarShadow = (ImageView) getRootView().findViewById(R.id.toolbar_shadow);

        // This is a workaround for http://crbug.com/574928. Since Jelly Bean is the lowest version
        // we support now and the next deprecation target, we decided to simply workaround.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            mToolbarShadow.setImageDrawable(
                    ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.toolbar_shadow));
        }
    }

    @Override
    public void draw(Canvas canvas) {
        // If capturing a texture of the toolbar, ensure the alpha is set prior to draw(...) being
        // called.  The alpha is being used prior to getting to draw(...), so updating the value
        // after this point was having no affect.
        if (mTextureCaptureMode) assert getAlpha() == 1f;

        // mClipRect can change in the draw call, so cache this value to ensure the canvas is
        // restored correctly.
        boolean shouldClip = !mTextureCaptureMode && mClipRect != null;
        if (shouldClip) {
            canvas.save();
            canvas.clipRect(mClipRect);
        }
        super.draw(canvas);
        if (shouldClip) {
            canvas.restore();

            // Post an invalidate when the clip rect becomes null to ensure another draw pass occurs
            // and the full toolbar is drawn again.
            if (mClipRect == null) postInvalidate();
        }
    }

    @Override
    public void onStateRestored() {
        if (mToggleTabStackButton != null) mToggleTabStackButton.setClickable(true);
    }

    @Override
    public boolean isReadyForTextureCapture() {
        if (mForceTextureCapture) {
            return true;
        }
        return !(mTabSwitcherState == TAB_SWITCHER || mTabSwitcherModeAnimation != null
                || urlHasFocus() || mUrlFocusChangeInProgress);
    }

    @Override
    public boolean setForceTextureCapture(boolean forceTextureCapture) {
        if (forceTextureCapture) {
            setUseLightDrawablesForTextureCapture();
            // Only force a texture capture if the tint for the toolbar drawables is changing.
            mForceTextureCapture = mLightDrawablesUsedForLastTextureCapture
                    != mUseLightDrawablesForTextureCapture;
            return mForceTextureCapture;
        }

        mForceTextureCapture = forceTextureCapture;
        return false;
    }

    @Override
    public void setLayoutUpdateHost(LayoutUpdateHost layoutUpdateHost) {
        mLayoutUpdateHost = layoutUpdateHost;
    }

    @Override
    public void finishAnimations() {
        mClipRect = null;
        if (mTabSwitcherModeAnimation != null) {
            mTabSwitcherModeAnimation.end();
            mTabSwitcherModeAnimation = null;
        }
        if (mDelayedTabSwitcherModeAnimation != null) {
            mDelayedTabSwitcherModeAnimation.end();
            mDelayedTabSwitcherModeAnimation = null;
        }

        // The Android framework calls onAnimationEnd() on listeners before Animator#isRunning()
        // returns false. Sometimes this causes the progress bar visibility to be set incorrectly.
        // Update the visibility now that animations are set to null. (see crbug.com/606419)
        updateProgressBarVisibility();
    }

    @Override
    public void getLocationBarContentRect(Rect outRect) {
        updateLocationBarBackgroundBounds(outRect, VisualState.NORMAL);
    }

    @Override
    protected void onHomeButtonUpdate(boolean homeButtonEnabled) {
        mIsHomeButtonEnabled = homeButtonEnabled;
        updateButtonVisibility();
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateButtonVisibility();
    }

    @Override
    public void updateButtonVisibility() {
        if (mIsHomeButtonEnabled) {
            mHomeButton.setVisibility(urlHasFocus() || isTabSwitcherAnimationRunning()
                    ? INVISIBLE : VISIBLE);
            mBrowsingModeViews.add(mHomeButton);
        } else {
            mHomeButton.setVisibility(GONE);
            mBrowsingModeViews.remove(mHomeButton);
        }
    }

    private ObjectAnimator createEnterTabSwitcherModeAnimation() {
        ObjectAnimator enterAnimation =
                ObjectAnimator.ofFloat(this, mTabSwitcherModePercentProperty, 1.f);
        enterAnimation.setDuration(TAB_SWITCHER_MODE_ENTER_ANIMATION_DURATION_MS);
        enterAnimation.setInterpolator(new LinearInterpolator());
        enterAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // This is to deal with the view going invisible when resuming the activity and
                // running this animation.  The view is still there and clickable but does not
                // render and only a layout triggers a refresh.  See crbug.com/306890.
                if (!mToggleTabStackButton.isEnabled()) requestLayout();
            }
        });

        return enterAnimation;
    }

    private ObjectAnimator createExitTabSwitcherAnimation(
            final boolean animateNormalToolbar) {
        ObjectAnimator exitAnimation =
                ObjectAnimator.ofFloat(this, mTabSwitcherModePercentProperty, 0.f);
        exitAnimation.setDuration(animateNormalToolbar
                ? TAB_SWITCHER_MODE_EXIT_NORMAL_ANIMATION_DURATION_MS
                : TAB_SWITCHER_MODE_EXIT_FADE_ANIMATION_DURATION_MS);
        exitAnimation.setInterpolator(new LinearInterpolator());
        exitAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                updateViewsForTabSwitcherMode();
            }
        });

        return exitAnimation;
    }

    private ObjectAnimator createPostExitTabSwitcherAnimation() {
        ObjectAnimator exitAnimation = ObjectAnimator.ofFloat(
                this, View.TRANSLATION_Y, -getHeight(), 0.f);
        exitAnimation.setDuration(TAB_SWITCHER_MODE_POST_EXIT_ANIMATION_DURATION_MS);
        exitAnimation.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        exitAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                updateViewsForTabSwitcherMode();
                // On older builds, force an update to ensure the new visuals are used
                // when bringing in the toolbar.  crbug.com/404571
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                    requestLayout();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mDelayedTabSwitcherModeAnimation = null;
                updateShadowVisibility();
                updateViewsForTabSwitcherMode();
            }
        });

        return exitAnimation;
    }

    @Override
    public void setTextureCaptureMode(boolean textureMode) {
        assert mTextureCaptureMode != textureMode;
        mTextureCaptureMode = textureMode;
        if (mTextureCaptureMode) {
            mToolbarShadow.setVisibility(VISIBLE);
            mPreTextureCaptureAlpha = getAlpha();
            setAlpha(1);
        } else {
            setAlpha(mPreTextureCaptureAlpha);
            updateShadowVisibility();
            mPreTextureCaptureAlpha = 1f;
        }
    }

    private boolean isTabSwitcherAnimationRunning() {
        return mTabSwitcherState == ENTERING_TAB_SWITCHER
                || mTabSwitcherState == EXITING_TAB_SWITCHER;
    }

    private void updateViewsForTabSwitcherMode() {
        int tabSwitcherViewsVisibility = mTabSwitcherState != STATIC_TAB  ? VISIBLE : INVISIBLE;
        int browsingViewsVisibility = mTabSwitcherState != STATIC_TAB ? INVISIBLE : VISIBLE;

        for (View view : mTabSwitcherModeViews) {
            view.setVisibility(tabSwitcherViewsVisibility);
        }
        for (View view : mBrowsingModeViews) {
            view.setVisibility(browsingViewsVisibility);
        }
        if (mShowMenuBadge) {
            setMenuButtonContentDescription(mTabSwitcherState == STATIC_TAB);
        }

        updateProgressBarVisibility();
        updateVisualsForToolbarState();
    }

    private void updateProgressBarVisibility() {
        getProgressBar().setVisibility(mTabSwitcherState != STATIC_TAB ? INVISIBLE : VISIBLE);
    }

    @Override
    protected void setContentAttached(boolean attached) {
        updateVisualsForToolbarState();
    }

    @Override
    protected void setTabSwitcherMode(
            boolean inTabSwitcherMode, boolean showToolbar, boolean delayAnimation) {
        // If setting tab switcher mode to true and the browser is already animating or in the tab
        // switcher skip.
        if (inTabSwitcherMode && (mTabSwitcherState == TAB_SWITCHER
                || mTabSwitcherState == ENTERING_TAB_SWITCHER)) {
            return;
        }

        // Likewise if exiting the tab switcher.
        if (!inTabSwitcherMode && (mTabSwitcherState == STATIC_TAB
                || mTabSwitcherState == EXITING_TAB_SWITCHER)) {
            return;
        }
        mTabSwitcherState = inTabSwitcherMode ? ENTERING_TAB_SWITCHER : EXITING_TAB_SWITCHER;

        mLocationBar.setUrlBarFocusable(false);

        finishAnimations();

        mDelayingTabSwitcherAnimation = delayAnimation;

        if (inTabSwitcherMode) {
            if (mUrlFocusLayoutAnimator != null && mUrlFocusLayoutAnimator.isRunning()) {
                mUrlFocusLayoutAnimator.end();
                mUrlFocusLayoutAnimator = null;
                // After finishing the animation, force a re-layout of the location bar,
                // so that the final translation position is correct (since onMeasure updates
                // won't happen in tab switcher mode). crbug.com/518795.
                layoutLocationBar(getMeasuredWidth());
                updateUrlExpansionAnimation();
            }
            mNewTabButton.setEnabled(true);
            updateViewsForTabSwitcherMode();
            mTabSwitcherModeAnimation = createEnterTabSwitcherModeAnimation();
        } else {
            if (!mDelayingTabSwitcherAnimation) {
                mTabSwitcherModeAnimation = createExitTabSwitcherAnimation(showToolbar);
            }
        }

        updateButtonsTranslationY();
        mAnimateNormalToolbar = showToolbar;
        if (mTabSwitcherModeAnimation != null) mTabSwitcherModeAnimation.start();

        if (SysUtils.isLowEndDevice()) finishAnimations();

        postInvalidateOnAnimation();
    }

    @Override
    protected void onTabSwitcherTransitionFinished() {
        setAlpha(1.f);
        mClipRect = null;

        // Detect what was being transitioned from and set the new state appropriately.
        if (mTabSwitcherState == EXITING_TAB_SWITCHER) {
            mLocationBar.setUrlBarFocusable(true);
            mTabSwitcherState = STATIC_TAB;
        }
        if (mTabSwitcherState == ENTERING_TAB_SWITCHER) mTabSwitcherState = TAB_SWITCHER;

        mTabSwitcherModePercent = mTabSwitcherState != STATIC_TAB ? 1.0f : 0.0f;

        if (!mAnimateNormalToolbar) {
            finishAnimations();
            updateVisualsForToolbarState();
        }

        if (mDelayingTabSwitcherAnimation) {
            mDelayingTabSwitcherAnimation = false;
            mDelayedTabSwitcherModeAnimation = createPostExitTabSwitcherAnimation();
            mDelayedTabSwitcherModeAnimation.start();
        } else {
            updateViewsForTabSwitcherMode();
        }
    }

    private void updateOverlayDrawables() {
        if (!isNativeLibraryReady()) return;

        VisualState overlayState = computeVisualState(false);
        boolean visualStateChanged = mOverlayDrawablesVisualState != overlayState;

        if (!visualStateChanged && mVisualState == VisualState.BRAND_COLOR
                && getToolbarDataProvider().getPrimaryColor()
                        != mTabSwitcherAnimationBgOverlay.getColor()) {
            visualStateChanged = true;
        }
        if (!visualStateChanged) return;

        mOverlayDrawablesVisualState = overlayState;
        mTabSwitcherAnimationBgOverlay.setColor(getToolbarColorForVisualState(
                mOverlayDrawablesVisualState));

        setTabSwitcherAnimationMenuDrawable();
        setUseLightDrawablesForTextureCapture();

        if (mTabSwitcherState == STATIC_TAB && !mTextureCaptureMode && mLayoutUpdateHost != null) {
            // Request a layout update to trigger a texture capture if the tint color is changing
            // and we're not already in texture capture mode. This is necessary if the tab switcher
            // is entered immediately after a change to the tint color without any user interactions
            // that would normally trigger a texture capture.
            mLayoutUpdateHost.requestUpdate();
        }
    }

    @Override
    public void destroy() {
        dismissTabSwitcherCallout();
    }

    @Override
    public void setOnTabSwitcherClickHandler(OnClickListener listener) {
        mTabSwitcherListener = listener;
    }

    @Override
    public void setOnNewTabClickHandler(OnClickListener listener) {
        mNewTabListener = listener;
    }

    @Override
    public boolean shouldIgnoreSwipeGesture() {
        return super.shouldIgnoreSwipeGesture() || mUrlExpansionPercent > 0f
                || mNtpSearchBoxTranslation.y < 0f;
    }

    private Property<TextView, Integer> buildUrlScrollProperty(
            final View containerView, final boolean isContainerRtl) {
        // If the RTL-ness of the container view changes during an animation, the scroll values
        // become invalid.  If that happens, snap to the ending position and no longer update.
        return new Property<TextView, Integer>(Integer.class, "scrollX") {
            private boolean mRtlStateInvalid;

            @Override
            public Integer get(TextView view) {
                return view.getScrollX();
            }

            @Override
            public void set(TextView view, Integer scrollX) {
                if (mRtlStateInvalid) return;
                boolean rtl = ApiCompatibilityUtils.isLayoutRtl(containerView);
                if (rtl != isContainerRtl) {
                    mRtlStateInvalid = true;
                    if (!rtl || mUrlBar.getLayout() != null) {
                        scrollX = 0;
                        if (rtl) {
                            scrollX = (int) view.getLayout().getPrimaryHorizontal(0);
                            scrollX -= view.getWidth();
                        }
                    }
                }
                view.setScrollX(scrollX);
            }
        };
    }

    private void populateUrlFocusingAnimatorSet(List<Animator> animators) {
        Animator animator = ObjectAnimator.ofFloat(this, mUrlFocusChangePercentProperty, 1f);
        animator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animators.add(animator);

        for (int i = 0; i < mLocationBar.getChildCount(); i++) {
            View childView = mLocationBar.getChildAt(i);
            if (childView == mLocationBar.getFirstViewVisibleWhenFocused()) break;
            animator = ObjectAnimator.ofFloat(childView, ALPHA, 0);
            animator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);
        }

        float density = getContext().getResources().getDisplayMetrics().density;
        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);
        float toolbarButtonTranslationX = MathUtils.flipSignIf(
                URL_FOCUS_TOOLBAR_BUTTONS_TRANSLATION_X_DP, isRtl) * density;

        animator = ObjectAnimator.ofFloat(
                mMenuButtonWrapper, TRANSLATION_X, toolbarButtonTranslationX);
        animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
        animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
        animators.add(animator);

        animator = ObjectAnimator.ofFloat(mMenuButtonWrapper, ALPHA, 0);
        animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
        animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
        animators.add(animator);

        if (mToggleTabStackButton != null) {
            animator = ObjectAnimator.ofFloat(
                    mToggleTabStackButton, TRANSLATION_X, toolbarButtonTranslationX);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
            animators.add(animator);

            animator = ObjectAnimator.ofFloat(mToggleTabStackButton, ALPHA, 0);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
            animators.add(animator);
        }
    }

    private void populateUrlClearFocusingAnimatorSet(List<Animator> animators) {
        Animator animator = ObjectAnimator.ofFloat(this, mUrlFocusChangePercentProperty, 0f);
        animator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animators.add(animator);

        animator = ObjectAnimator.ofFloat(mMenuButtonWrapper, TRANSLATION_X, 0);
        animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
        animator.setStartDelay(URL_CLEAR_FOCUS_MENU_DELAY_MS);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animators.add(animator);

        animator = ObjectAnimator.ofFloat(mMenuButtonWrapper, ALPHA, 1);
        animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
        animator.setStartDelay(URL_CLEAR_FOCUS_MENU_DELAY_MS);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animators.add(animator);

        if (mToggleTabStackButton != null) {
            animator = ObjectAnimator.ofFloat(mToggleTabStackButton, TRANSLATION_X, 0);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setStartDelay(URL_CLEAR_FOCUS_TABSTACK_DELAY_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);

            animator = ObjectAnimator.ofFloat(mToggleTabStackButton, ALPHA, 1);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setStartDelay(URL_CLEAR_FOCUS_TABSTACK_DELAY_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);
        }

        for (int i = 0; i < mLocationBar.getChildCount(); i++) {
            View childView = mLocationBar.getChildAt(i);
            if (childView == mLocationBar.getFirstViewVisibleWhenFocused()) break;
            animator = ObjectAnimator.ofFloat(childView, ALPHA, 1);
            animator.setStartDelay(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setDuration(URL_CLEAR_FOCUS_MENU_DELAY_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);
        }

        if (isLocationBarShownInNTP() && mNtpSearchBoxScrollPercent == 0f) return;

        // The call to getLayout() can return null briefly during text changes, but as it
        // is only needed for RTL calculations, we proceed if the location bar is showing
        // LTR content.
        boolean isLocationBarRtl = ApiCompatibilityUtils.isLayoutRtl(mLocationBar);
        if (!isLocationBarRtl || mUrlBar.getLayout() != null) {
            int urlBarStartScrollX = 0;
            if (isLocationBarRtl) {
                urlBarStartScrollX = (int) mUrlBar.getLayout().getPrimaryHorizontal(0);
                urlBarStartScrollX -= mUrlBar.getWidth();
            }

            // If the scroll position matches the current scroll position, do not trigger
            // this animation as it will cause visible jumps when going from cleared text
            // back to page URLs (despite it continually calling setScrollX with the same
            // number).
            if (mUrlBar.getScrollX() != urlBarStartScrollX) {
                animator = ObjectAnimator.ofInt(mUrlBar,
                        buildUrlScrollProperty(mLocationBar, isLocationBarRtl), urlBarStartScrollX);
                animator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
                animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
                animators.add(animator);
            }
        }
    }

    @Override
    public void onUrlFocusChange(final boolean hasFocus) {
        super.onUrlFocusChange(hasFocus);

        triggerUrlFocusAnimation(hasFocus);

        TransitionDrawable shadowDrawable = (TransitionDrawable) mToolbarShadow.getDrawable();
        if (hasFocus) {
            dismissTabSwitcherCallout();
            shadowDrawable.startTransition(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        } else {
            shadowDrawable.reverseTransition(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        }
    }

    private void triggerUrlFocusAnimation(final boolean hasFocus) {
        if (mUrlFocusLayoutAnimator != null && mUrlFocusLayoutAnimator.isRunning()) {
            mUrlFocusLayoutAnimator.cancel();
            mUrlFocusLayoutAnimator = null;
        }

        List<Animator> animators = new ArrayList<>();
        if (hasFocus) {
            populateUrlFocusingAnimatorSet(animators);
        } else {
            populateUrlClearFocusingAnimatorSet(animators);
        }
        mUrlFocusLayoutAnimator = new AnimatorSet();
        mUrlFocusLayoutAnimator.playTogether(animators);

        mUrlFocusChangeInProgress = true;
        mUrlFocusLayoutAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCanceled;

            @Override
            public void onAnimationStart(Animator animation) {
                if (!hasFocus) {
                    mDisableLocationBarRelayout = true;
                } else {
                    mLayoutLocationBarInFocusedMode = true;
                    requestLayout();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCanceled) return;

                if (!hasFocus) {
                    mDisableLocationBarRelayout = false;
                    mLayoutLocationBarInFocusedMode = false;
                    requestLayout();
                }
                mLocationBar.finishUrlFocusChange(hasFocus);
                mUrlFocusChangeInProgress = false;
            }
        });
        mUrlFocusLayoutAnimator.start();
    }

    @Override
    protected void updateTabCountVisuals(int numberOfTabs) {
        if (mHomeButton != null) mHomeButton.setEnabled(true);

        if (mToggleTabStackButton == null) return;

        mToggleTabStackButton.setEnabled(numberOfTabs >= 1);
        mToggleTabStackButton.setContentDescription(
                getResources().getQuantityString(
                        R.plurals.accessibility_toolbar_btn_tabswitcher_toggle,
                        numberOfTabs, numberOfTabs));
        mTabSwitcherButtonDrawableLight.updateForTabCount(numberOfTabs, isIncognito());
        mTabSwitcherButtonDrawable.updateForTabCount(numberOfTabs, isIncognito());

        int themeColor;
        if (getToolbarDataProvider() != null) {
            themeColor = getToolbarDataProvider().getPrimaryColor();
        } else {
            themeColor = getToolbarColorForVisualState(
                    isIncognito() ? VisualState.INCOGNITO : VisualState.NORMAL);
        }

        boolean useTabStackDrawableLight = isIncognito()
                || ColorUtils.shouldUseLightForegroundOnBackground(themeColor);
        if (mTabSwitcherAnimationTabStackDrawable == null
                || mIsOverlayTabStackDrawableLight != useTabStackDrawableLight) {
            mTabSwitcherAnimationTabStackDrawable =
                    TabSwitcherDrawable.createTabSwitcherDrawable(
                            getResources(), useTabStackDrawableLight);
            int[] stateSet = {android.R.attr.state_enabled};
            mTabSwitcherAnimationTabStackDrawable.setState(stateSet);
            mTabSwitcherAnimationTabStackDrawable.setBounds(
                    mToggleTabStackButton.getDrawable().getBounds());
            mIsOverlayTabStackDrawableLight = useTabStackDrawableLight;
        }

        if (mTabSwitcherAnimationTabStackDrawable != null) {
            mTabSwitcherAnimationTabStackDrawable.updateForTabCount(
                    numberOfTabs, isIncognito());
        }
    }

    @Override
    protected void onTabContentViewChanged() {
        super.onTabContentViewChanged();
        updateNtpAnimationState();
        updateVisualsForToolbarState();
    }

    @Override
    protected void onTabOrModelChanged() {
        super.onTabOrModelChanged();
        updateNtpAnimationState();
        updateVisualsForToolbarState();

        if (mHasCheckedIfTabSwitcherCalloutIsNecessary) {
            dismissTabSwitcherCallout();
        } else {
            mHasCheckedIfTabSwitcherCalloutIsNecessary = true;
            showTabSwitcherCalloutIfNecessary();
        }
    }

    private static boolean isVisualStateValidForBrandColorTransition(VisualState state) {
        return state == VisualState.NORMAL || state == VisualState.BRAND_COLOR;
    }

    @Override
    protected void onPrimaryColorChanged(boolean shouldAnimate) {
        super.onPrimaryColorChanged(shouldAnimate);
        if (mBrandColorTransitionActive) mBrandColorTransitionAnimation.cancel();

        final int initialColor = mToolbarBackground.getColor();
        final int finalColor = getToolbarDataProvider().getPrimaryColor();
        if (initialColor == finalColor) return;

        if (!isVisualStateValidForBrandColorTransition(mVisualState)) return;

        if (!shouldAnimate) {
            updateToolbarBackground(finalColor);
            return;
        }

        boolean shouldUseOpaque = ColorUtils.shouldUseOpaqueTextboxBackground(finalColor);
        final int initialAlpha = mLocationBarBackgroundAlpha;
        final int finalAlpha =
                shouldUseOpaque ? 255 : LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
        final boolean shouldAnimateAlpha = initialAlpha != finalAlpha;
        mBrandColorTransitionAnimation = ValueAnimator.ofFloat(0, 1)
                .setDuration(THEME_COLOR_TRANSITION_DURATION);
        mBrandColorTransitionAnimation.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        mBrandColorTransitionAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                if (shouldAnimateAlpha) {
                    mLocationBarBackgroundAlpha =
                            (int) (MathUtils.interpolate(initialAlpha, finalAlpha, fraction));
                }
                updateToolbarBackground(
                        ColorUtils.getColorWithOverlay(initialColor, finalColor, fraction));
            }
        });
        mBrandColorTransitionAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBrandColorTransitionActive = false;
                updateVisualsForToolbarState();
            }
        });
        mBrandColorTransitionAnimation.start();
        mBrandColorTransitionActive = true;
    }

    private void updateNtpAnimationState() {
        // Store previous NTP scroll before calling reset as that clears this value.
        boolean wasShowingNtp = mVisibleNewTabPage != null;
        float previousNtpScrollPercent = mNtpSearchBoxScrollPercent;

        resetNtpAnimationValues();
        if (mVisibleNewTabPage != null) {
            mVisibleNewTabPage.setSearchBoxScrollListener(null);
            mVisibleNewTabPage = null;
        }
        mVisibleNewTabPage = getToolbarDataProvider().getNewTabPageForCurrentTab();
        if (mVisibleNewTabPage != null && mVisibleNewTabPage.isLocationBarShownInNTP()) {
            mVisibleNewTabPage.setSearchBoxScrollListener(this);
            requestLayout();
        } else if (wasShowingNtp) {
            // Convert the previous NTP scroll percentage to URL focus percentage because that
            // will give a nicer transition animation from the expanded NTP omnibox to the
            // collapsed normal omnibox on other non-NTP pages.
            if (mTabSwitcherState == STATIC_TAB && previousNtpScrollPercent > 0f) {
                mUrlFocusChangePercent =
                        Math.max(previousNtpScrollPercent, mUrlFocusChangePercent);
                triggerUrlFocusAnimation(false);
            }
            requestLayout();
        }
    }

    @Override
    protected void onDefaultSearchEngineChanged() {
        super.onDefaultSearchEngineChanged();
        // Post an update for the toolbar state, which will allow all other listeners
        // for the search engine change to update before we check on the state of the
        // world for a UI update.
        // TODO(tedchoc): Move away from updating based on the search engine change and instead
        //                add the toolbar as a listener to the NewTabPage and udpate only when
        //                it notifies the listeners that it has changed its state.
        post(new Runnable() {
            @Override
            public void run() {
                updateVisualsForToolbarState();
                updateNtpAnimationState();
            }
        });
    }

    @Override
    protected void handleFindToolbarStateChange(boolean showing) {
        setVisibility(showing ? View.GONE : View.VISIBLE);
        TransitionDrawable shadowDrawable = (TransitionDrawable) mToolbarShadow.getDrawable();
        if (showing) {
            shadowDrawable.startTransition(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        } else {
            shadowDrawable.reverseTransition(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        }
    }

    private boolean isLocationBarShownInNTP() {
        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        return ntp != null && ntp.isLocationBarShownInNTP();
    }

    private void updateShadowVisibility() {
        boolean shouldDrawShadow = mTabSwitcherState == STATIC_TAB;
        int shadowVisibility = shouldDrawShadow ? View.VISIBLE : View.INVISIBLE;

        if (mToolbarShadow.getVisibility() != shadowVisibility) {
            mToolbarShadow.setVisibility(shadowVisibility);
        }
    }

    private VisualState computeVisualState(boolean isInTabSwitcherMode) {
        if (isInTabSwitcherMode && isIncognito()) return VisualState.TAB_SWITCHER_INCOGNITO;
        if (isInTabSwitcherMode && !isIncognito()) return VisualState.TAB_SWITCHER_NORMAL;
        if (isLocationBarShownInNTP()) return VisualState.NEW_TAB_NORMAL;
        if (isIncognito()) return VisualState.INCOGNITO;
        if (getToolbarDataProvider().isUsingBrandColor()) return VisualState.BRAND_COLOR;
        return VisualState.NORMAL;
    }

    private void updateVisualsForToolbarState() {
        final boolean isIncognito = isIncognito();

        // These are important for setting visual state while the entering or leaving the tab
        // switcher.
        boolean inOrEnteringStaticTab = mTabSwitcherState == STATIC_TAB
                || mTabSwitcherState == EXITING_TAB_SWITCHER;
        boolean inOrEnteringTabSwitcher = !inOrEnteringStaticTab;

        VisualState newVisualState = computeVisualState(inOrEnteringTabSwitcher);

        // If we are navigating to or from a brand color, allow the transition animation
        // to run to completion as it will handle the triggering this path again and committing
        // the proper visual state when it finishes.  Brand color transitions are only valid
        // between normal non-incognito pages and brand color pages, so if the visual states
        // do not match then cancel the animation below.
        if (mBrandColorTransitionActive
                && isVisualStateValidForBrandColorTransition(mVisualState)
                && isVisualStateValidForBrandColorTransition(newVisualState)) {
            return;
        } else if (mBrandColorTransitionAnimation != null
                && mBrandColorTransitionAnimation.isRunning()) {
            mBrandColorTransitionAnimation.cancel();
        }

        boolean visualStateChanged = mVisualState != newVisualState;

        int currentPrimaryColor = getToolbarDataProvider().getPrimaryColor();
        int themeColorForProgressBar = currentPrimaryColor;

        // If The page is native force the use of the standard theme for the progress bar.
        if (getToolbarDataProvider() != null && getToolbarDataProvider().getTab() != null
                && getToolbarDataProvider().getTab().isNativePage()) {
            VisualState visualState = isIncognito() ? VisualState.INCOGNITO : VisualState.NORMAL;
            themeColorForProgressBar = getToolbarColorForVisualState(visualState);
        }

        if (mVisualState == VisualState.BRAND_COLOR && !visualStateChanged) {
            boolean useLightToolbarDrawables =
                    ColorUtils.shouldUseLightForegroundOnBackground(currentPrimaryColor);
            boolean unfocusedLocationBarUsesTransparentBg =
                    !ColorUtils.shouldUseOpaqueTextboxBackground(currentPrimaryColor);
            if (useLightToolbarDrawables != mUseLightToolbarDrawables
                    || unfocusedLocationBarUsesTransparentBg
                            != mUnfocusedLocationBarUsesTransparentBg) {
                visualStateChanged = true;
            } else {
                updateToolbarBackground(VisualState.BRAND_COLOR);
                getProgressBar().setThemeColor(themeColorForProgressBar, isIncognito());
            }
        }

        mVisualState = newVisualState;

        updateOverlayDrawables();
        updateShadowVisibility();
        updateUrlExpansionAnimation();
        if (!visualStateChanged) {
            if (mVisualState == VisualState.NEW_TAB_NORMAL) {
                updateNtpTransitionAnimation();
            } else {
                resetNtpAnimationValues();
            }
            return;
        }

        mUseLightToolbarDrawables = false;
        mUnfocusedLocationBarUsesTransparentBg = false;
        mLocationBarBackgroundAlpha = 255;
        updateToolbarBackground(mVisualState);
        getProgressBar().setThemeColor(themeColorForProgressBar, isIncognito());

        if (inOrEnteringTabSwitcher) {
            mUseLightToolbarDrawables = true;
            mLocationBarBackgroundAlpha = LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
            getProgressBar().setBackgroundColor(mProgressBackBackgroundColorWhite);
            getProgressBar().setForegroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.progress_bar_foreground_white));
        } else if (isIncognito()) {
            mUseLightToolbarDrawables = true;
            mLocationBarBackgroundAlpha = LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
        } else if (mVisualState == VisualState.BRAND_COLOR) {
            mUseLightToolbarDrawables =
                    ColorUtils.shouldUseLightForegroundOnBackground(currentPrimaryColor);
            mUnfocusedLocationBarUsesTransparentBg =
                    !ColorUtils.shouldUseOpaqueTextboxBackground(currentPrimaryColor);
            mLocationBarBackgroundAlpha = mUnfocusedLocationBarUsesTransparentBg
                    ? LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA
                    : 255;
        }

        if (mToggleTabStackButton != null) {
            mToggleTabStackButton.setImageDrawable(mUseLightToolbarDrawables
                    ? mTabSwitcherButtonDrawableLight : mTabSwitcherButtonDrawable);
            if (mTabSwitcherAnimationTabStackDrawable != null) {
                mTabSwitcherAnimationTabStackDrawable.setTint(
                        mUseLightToolbarDrawables ? mLightModeTint : mDarkModeTint);
            }
        }

        mMenuButton.setTint(mUseLightToolbarDrawables ? mLightModeTint : mDarkModeTint);

        if (mShowMenuBadge && inOrEnteringStaticTab) {
            setAppMenuUpdateBadgeDrawable(mUseLightToolbarDrawables);
        }
        ColorStateList tint = mUseLightToolbarDrawables ? mLightModeTint : mDarkModeTint;
        if (mIsHomeButtonEnabled) mHomeButton.setTint(tint);

        mLocationBar.updateVisualsForState();
        // Remove the side padding for incognito to ensure the badge icon aligns correctly with the
        // background of the location bar.
        if (isIncognito) {
            mLocationBar.setPadding(
                    0, mLocationBarBackgroundPadding.top, 0, mLocationBarBackgroundPadding.bottom);
        } else {
            mLocationBar.setPadding(
                    mLocationBarBackgroundPadding.left, mLocationBarBackgroundPadding.top,
                    mLocationBarBackgroundPadding.right, mLocationBarBackgroundPadding.bottom);
        }

        // We update the alpha before comparing the visual state as we need to change
        // its value when entering and exiting TabSwitcher mode.
        if (isLocationBarShownInNTP() && inOrEnteringStaticTab) {
            updateNtpTransitionAnimation();
        }

        mNewTabButton.setIsIncognito(isIncognito);

        CharSequence newTabContentDescription = getResources().getText(
                isIncognito ? R.string.accessibility_toolbar_btn_new_incognito_tab :
                        R.string.accessibility_toolbar_btn_new_tab);
        if (mNewTabButton != null
                && !newTabContentDescription.equals(mNewTabButton.getContentDescription())) {
            mNewTabButton.setContentDescription(newTabContentDescription);
        }

        getMenuButtonWrapper().setVisibility(View.VISIBLE);
    }

    @Override
    public LocationBar getLocationBar() {
        return mLocationBar;
    }

    @Override
    public void showAppMenuUpdateBadge() {
        super.showAppMenuUpdateBadge();

        // Set up variables.
        if (!mBrowsingModeViews.contains(mMenuBadge)) {
            mBrowsingModeViews.add(mMenuBadge);
        }

        // Finish any in-progress animations and set the TabSwitcherAnimationMenuBadgeDrawables.
        finishAnimations();
        setTabSwitcherAnimationMenuBadgeDrawable();

        // Show the badge.
        if (mTabSwitcherState == STATIC_TAB) {
            if (mUseLightToolbarDrawables) {
                setAppMenuUpdateBadgeDrawable(mUseLightToolbarDrawables);
            }
            setAppMenuUpdateBadgeToVisible(true);
        }
    }

    @Override
    public void removeAppMenuUpdateBadge(boolean animate) {
        super.removeAppMenuUpdateBadge(animate);

        if (mBrowsingModeViews.contains(mMenuBadge)) {
            mBrowsingModeViews.remove(mMenuBadge);
            mTabSwitcherAnimationMenuBadgeDarkDrawable = null;
            mTabSwitcherAnimationMenuBadgeLightDrawable = null;
        }

        mLocationBar.removeAppMenuUpdateBadge(animate);
    }

    private void setTabSwitcherAnimationMenuDrawable() {
        mTabSwitcherAnimationMenuDrawable = ApiCompatibilityUtils.getDrawable(getResources(),
                R.drawable.btn_menu);
        mTabSwitcherAnimationMenuDrawable.mutate();
        mTabSwitcherAnimationMenuDrawable.setColorFilter(
                isIncognito() ? mLightModeDefaultColor : mDarkModeDefaultColor,
                PorterDuff.Mode.SRC_IN);
        ((BitmapDrawable) mTabSwitcherAnimationMenuDrawable).setGravity(Gravity.CENTER);
    }

    private void setTabSwitcherAnimationMenuBadgeDrawable() {
        mTabSwitcherAnimationMenuBadgeDarkDrawable = ApiCompatibilityUtils.getDrawable(
                getResources(), R.drawable.badge_update_dark);
        mTabSwitcherAnimationMenuBadgeDarkDrawable.mutate();
        ((BitmapDrawable) mTabSwitcherAnimationMenuBadgeDarkDrawable).setGravity(Gravity.CENTER);

        mTabSwitcherAnimationMenuBadgeLightDrawable = ApiCompatibilityUtils.getDrawable(
                getResources(), R.drawable.badge_update_light);
        mTabSwitcherAnimationMenuBadgeLightDrawable.mutate();
        ((BitmapDrawable) mTabSwitcherAnimationMenuBadgeLightDrawable).setGravity(Gravity.CENTER);
    }

    @Override
    public void setFullscreenManager(FullscreenManager manager) {
        super.setFullscreenManager(manager);
        mFullscreenManager = manager;
    }

    private void setUseLightDrawablesForTextureCapture() {
        int currentPrimaryColor = getToolbarDataProvider().getPrimaryColor();
        mUseLightDrawablesForTextureCapture =
                isIncognito()
                || (currentPrimaryColor != 0
                           && ColorUtils.shouldUseLightForegroundOnBackground(currentPrimaryColor));
    }

    private void dismissTabSwitcherCallout() {
        if (mTabSwitcherCallout != null) mTabSwitcherCallout.dismiss();
    }

    private void showTabSwitcherCalloutIfNecessary() {
        assert mTabSwitcherCallout == null;
        mTabSwitcherCallout =
                TabSwitcherCallout.showIfNecessary(getContext(), mToggleTabStackButton);
        if (mTabSwitcherCallout == null) return;

        mTabSwitcherCallout.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                if (mFullscreenManager != null) {
                    mFullscreenManager.hideControlsPersistent(mFullscreenCalloutToken);
                    mFullscreenCalloutToken = FullscreenManager.INVALID_TOKEN;
                }
                mTabSwitcherCallout = null;
            }
        });

        if (mFullscreenManager != null) {
            mFullscreenCalloutToken =
                    mFullscreenManager.showControlsPersistentAndClearOldToken(
                            mFullscreenCalloutToken);
        }
    }
}

