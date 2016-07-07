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
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BottomTabBtn;
import org.chromium.chrome.browser.compositor.Invalidator;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.omnibox.LocationBarPhone;
import org.chromium.chrome.browser.omnibox.UrlContainer;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
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

    private static final int BRAND_COLOR_TRANSITION_DURATION_MS = 250;

    static final int LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA = 51;

    private LocationBarPhone mPhoneLocationBar;

    private ViewGroup mToolbarButtonsContainer;
    private ImageView mToggleTabStackButton;
    private NewTabButton mNewTabButton;
    private TintedImageButton mHomeButton;
    private TextView mUrlBar;
    private UrlContainer mUrlContainer;
    private View mUrlActionsContainer;
    private ImageView mToolbarShadow;

    private final int mProgressBackBackgroundColor;
    private final int mProgressBackBackgroundColorWhite;

    private ObjectAnimator mTabSwitcherModeAnimation;
    private ObjectAnimator mDelayedTabSwitcherModeAnimation;

    private final List<View> mTabSwitcherModeViews = new ArrayList<View>();
    private final Set<View> mBrowsingModeViews = new HashSet<View>();
    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mInTabSwitcherMode;

    // This determines whether or not the toolbar draws as expected (false) or whether it always
    // draws as if it's showing the non-tabswitcher, non-animating toolbar. This is used in grabbing
    // a bitmap to use as a texture representation of this view.
    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mTextureCaptureMode;

    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mAnimateNormalToolbar;
    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mDelayingTabSwitcherAnimation;

    private ColorDrawable mTabSwitcherAnimationBgOverlay;
    private TabSwitcherDrawable mTabSwitcherAnimationTabStackDrawable;
    private Drawable mTabSwitcherAnimationMenuDrawable;
    // Value that determines the amount of transition from the normal toolbar mode to TabSwitcher
    // mode.  0 = entirely in normal mode and 1.0 = entirely in TabSwitcher mode.  In between values
    // can be used for animating between the two view modes.
    @ViewDebug.ExportedProperty(category = "chrome")
    private float mTabSwitcherModePercent = 0;
    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mUIAnimatingTabSwitcherTransition;

    // Used to clip the toolbar during the fade transition into and out of TabSwitcher mode.  Only
    // used when |mAnimateNormalToolbar| is false.
    @ViewDebug.ExportedProperty(category = "chrome")
    private Rect mClipRect;

    private OnClickListener mTabSwitcherListener;
    private OnClickListener mNewTabListener;

    @ViewDebug.ExportedProperty(category = "chrome")
    private boolean mUrlFocusChangeInProgress;

    /**
     * 1.0 is 100% focused, 0 is completely unfocused
     */
    @ViewDebug.ExportedProperty(category = "chrome")
    private float mUrlFocusChangePercent;

    /**
     * 1.0 is 100% expanded to full width, 0 is original collapsed size.
     */
    @ViewDebug.ExportedProperty(category = "chrome")
    private float mUrlExpansionPercent;
    private AnimatorSet mUrlFocusLayoutAnimator;
    private boolean mDisableLocationBarRelayout;
    private boolean mLayoutLocationBarInFocusedMode;
    private int mUnfocusedLocationBarLayoutWidth;
    private int mUnfocusedLocationBarLayoutLeft;
    private boolean mUnfocusedLocationBarUsesTransparentBg;

    private int mUrlBackgroundAlpha = 255;
    private float mNtpSearchBoxScrollPercent = UNINITIALIZED_PERCENT;
    private ColorDrawable mToolbarBackground;
    private Drawable mLocationBarBackground;
    private boolean mForceDrawLocationBarBackground;
    private TabSwitcherDrawable mTabSwitcherButtonDrawable;
    private TabSwitcherDrawable mTabSwitcherButtonDrawableLight;

    private final int mLightModeDefaultColor;
    private final int mDarkModeDefaultColor;

    private final Rect mUrlViewportBounds = new Rect();
    private final Rect mUrlBackgroundPadding = new Rect();
    private final Rect mBackgroundOverlayBounds = new Rect();
    private final Rect mLocationBarBackgroundOffset = new Rect();

    private final Rect mNtpSearchBoxOriginalBounds = new Rect();
    private final Rect mNtpSearchBoxTransformedBounds = new Rect();

    private final int mLocationBarInsets;
    private final int mToolbarSidePadding;

    private ValueAnimator mBrandColorTransitionAnimation;
    private boolean mBrandColorTransitionActive;

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
     *
     * @param context The Context in which this View object is created.
     * @param attrs   The AttributeSet that was specified with this View.
     */
    public ToolbarPhone(Context context, AttributeSet attrs) {
        super(context, attrs);
        mToolbarSidePadding = getResources().getDimensionPixelOffset(
                R.dimen.toolbar_edge_padding);
        // Insets used for the PhoneLocatioBar background drawable.
        mLocationBarInsets = getResources().getDimensionPixelSize(R.dimen.location_bar_margin_top)
                + getResources().getDimensionPixelSize(R.dimen.location_bar_margin_bottom);
        mProgressBackBackgroundColor =
                ApiCompatibilityUtils.getColor(getResources(), R.color.progress_bar_background);
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
        mPhoneLocationBar = (LocationBarPhone) findViewById(R.id.location_bar);

        mToolbarButtonsContainer = (ViewGroup) findViewById(R.id.toolbar_buttons);

        mHomeButton = (TintedImageButton) findViewById(R.id.home_button);

        mUrlBar = (TextView) findViewById(R.id.url_bar);
        mUrlContainer = (UrlContainer) findViewById(R.id.url_container);

        mUrlActionsContainer = findViewById(R.id.url_action_container);

        mBrowsingModeViews.add(mPhoneLocationBar);

        mToolbarBackground = new ColorDrawable(getToolbarColorForVisualState(VisualState.NORMAL));
        mTabSwitcherAnimationBgOverlay =
                new ColorDrawable(getToolbarColorForVisualState(VisualState.NORMAL));

        mLocationBarBackground =
                ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.inset_textbox);
        mLocationBarBackground.getPadding(mUrlBackgroundPadding);
        mPhoneLocationBar.setPadding(
                mUrlBackgroundPadding.left, mUrlBackgroundPadding.top,
                mUrlBackgroundPadding.right, mUrlBackgroundPadding.bottom);

        setLayoutTransition(null);

        mMenuButton.setVisibility(shouldShowMenuButton() ? View.VISIBLE : View.GONE);
        if (FeatureUtilities.isDocumentMode(getContext())) {
            ApiCompatibilityUtils.setMarginEnd(
                    (MarginLayoutParams) mMenuButton.getLayoutParams(),
                    getResources().getDimensionPixelSize(R.dimen.document_toolbar_menu_offset));
        }

        finishInflateForTabSwitchingResources();

        setWillNotDraw(false);
    }

    private boolean isTabSwitchingEnabled() {
        return !FeatureUtilities.isDocumentMode(getContext());
    }

    private void finishInflateForTabSwitchingResources() {
        mToggleTabStackButton = (ImageView) findViewById(R.id.tab_switcher_button);
        mNewTabButton = (NewTabButton) findViewById(R.id.new_tab_button);

        if (!isTabSwitchingEnabled()) {
            assert mToolbarButtonsContainer.indexOfChild(mToggleTabStackButton) >= 0;
            mToolbarButtonsContainer.removeView(mToggleTabStackButton);
            mToggleTabStackButton = null;
            assert indexOfChild(mNewTabButton) >= 0;
            removeView(mNewTabButton);
            mNewTabButton = null;
        } else {
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
    }

    /**
     * Sets up click and key listeners once we have native library available to handle clicks.
     */
    @Override
    public void onNativeLibraryReady() {
        super.onNativeLibraryReady();
        getLocationBar().onNativeLibraryReady();

        if (isTabSwitchingEnabled()) {
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

        updateVisualsForToolbarState(mInTabSwitcherMode);
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
    public void onClick(View v) {
        if (mToggleTabStackButton == v) {
            // The button is clickable before the native library is loaded
            // and the listener is setup.
            if (mToggleTabStackButton != null && mToggleTabStackButton.isClickable()
                    && mTabSwitcherListener != null) {
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
            if (!mInTabSwitcherMode) setUrlFocusChangePercent(mUrlFocusChangePercent);
            if (!changed) return;
        } else {
            updateUnfocusedLocationBarLayoutParams();
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateUnfocusedLocationBarLayoutParams() {
        boolean hasVisibleViewPriorToUrlBar = false;
        for (int i = 0; i < mPhoneLocationBar.getChildCount(); i++) {
            View child = mPhoneLocationBar.getChildAt(i);
            if (child == mUrlContainer) break;
            if (child.getVisibility() != GONE) {
                hasVisibleViewPriorToUrlBar = true;
                break;
            }
        }

        int leftViewBounds = getViewBoundsLeftOfLocationBar(mVisualState);
        if (!hasVisibleViewPriorToUrlBar) leftViewBounds += mToolbarSidePadding;
        int rightViewBounds = getViewBoundsRightOfLocationBar(mVisualState);

        if (!mPhoneLocationBar.hasVisibleViewsAfterUrlBarWhenUnfocused()) {
            // Add spacing between the end of the URL and the edge of the omnibox drawable.
            // This only applies if there is no end aligned view that should be visible
            // while the omnibox is unfocused.
            if (ApiCompatibilityUtils.isLayoutRtl(mPhoneLocationBar)) {
                leftViewBounds += mToolbarSidePadding;
            } else {
                rightViewBounds -= mToolbarSidePadding;
            }
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
            for (int i = 0; i < mPhoneLocationBar.getChildCount(); i++) {
                View child = mPhoneLocationBar.getChildAt(i);
                if (child == mPhoneLocationBar.getFirstViewVisibleWhenFocused()) break;
                if (child.getVisibility() == GONE) continue;
                priorVisibleWidth += child.getMeasuredWidth();
            }

            width = containerWidth - (2 * mToolbarSidePadding) + priorVisibleWidth;
            if (ApiCompatibilityUtils.isLayoutRtl(mPhoneLocationBar)) {
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
            return mHomeButton.getVisibility() != GONE
                    ? mHomeButton.getMeasuredWidth() : mToolbarSidePadding;
        }
    }

    private int getViewBoundsRightOfLocationBar(VisualState visualState) {
        // Uses getMeasuredWidth()s instead of getRight() because this is called in onMeasure
        // and the layout values have not yet been set.
        if (visualState == VisualState.NEW_TAB_NORMAL) {
            return getMeasuredWidth();
        } else if (ApiCompatibilityUtils.isLayoutRtl(this)) {
            return getMeasuredWidth() - (mHomeButton.getVisibility() != GONE
                    ? mHomeButton.getMeasuredWidth() : mToolbarSidePadding);
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
                && (mPhoneLocationBar.getVisibility() == VISIBLE || mTextureCaptureMode)) {
            updateUrlViewportBounds(mUrlViewportBounds, mVisualState, false);
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
    public void onScrollChanged(float scrollPercentage) {
        if (scrollPercentage == mNtpSearchBoxScrollPercent) return;

        mNtpSearchBoxScrollPercent = scrollPercentage;
        updateUrlExpansionPercent();
        updateUrlExpansionAnimation();
    }

    /**
     * Calculate the bounds for UrlViewport and set them to out rect.
     */
    private void updateUrlViewportBounds(Rect out, VisualState visualState,
                                         boolean ignoreTranslationY) {
        // Calculate the visible boundaries of the left and right most child views
        // of the location bar.
        int leftViewPosition = getViewBoundsLeftOfLocationBar(visualState);
        int rightViewPosition = getViewBoundsRightOfLocationBar(visualState);

        leftViewPosition -= mUrlBackgroundPadding.left;
        if (mUrlExpansionPercent != 0f) {
            leftViewPosition *= (1f - mUrlExpansionPercent);
            leftViewPosition -= mUrlBackgroundPadding.left * mUrlExpansionPercent;
        }

        rightViewPosition += mUrlBackgroundPadding.right;
        if (mUrlExpansionPercent != 0f) {
            rightViewPosition += ((getWidth() - rightViewPosition) * mUrlExpansionPercent);
            rightViewPosition += mUrlBackgroundPadding.right * mUrlExpansionPercent;
        }

        // The bounds are set by the following:
        // - The left most visible location bar child view.
        // - The top of the viewport is aligned with the top of the location bar.
        // - The right most visible location bar child view.
        // - The bottom of the viewport is aligned with the bottom of the location bar.
        // Additional padding can be applied for use during animations.
        out.set(leftViewPosition,
                0,
                rightViewPosition,
                (int) (mPhoneLocationBar.getMeasuredHeight()
                        + (getHeight() - mPhoneLocationBar.getMeasuredHeight()
                        + mUrlBackgroundPadding.bottom + mUrlBackgroundPadding.top)
                        * mUrlExpansionPercent));
        float yOffset = ignoreTranslationY ? mPhoneLocationBar.getTop() : mPhoneLocationBar.getY();

        out.offset(0, (int) (yOffset - (mUrlBackgroundPadding.top * mUrlExpansionPercent)));
    }

    /**
     * Updates percentage of current the URL focus change animation.
     *
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

    private void updateUrlExpansionAnimation() {
        if (mInTabSwitcherMode || isTabSwitcherAnimationRunning()) return;

        mLocationBarBackgroundOffset.setEmpty();

        FrameLayout.LayoutParams locationBarLayoutParams =
                getFrameLayoutParams(mPhoneLocationBar);
        int currentLeftMargin = locationBarLayoutParams.leftMargin;
        int currentWidth = locationBarLayoutParams.width;

        float inversePercent = 1f - mUrlExpansionPercent;
        boolean isLocationBarRtl = ApiCompatibilityUtils.isLayoutRtl(mPhoneLocationBar);
        if (ApiCompatibilityUtils.isLayoutRtl(mPhoneLocationBar)) {
            mPhoneLocationBar.setTranslationX(
                    ((mUnfocusedLocationBarLayoutLeft + mUnfocusedLocationBarLayoutWidth)
                            - (currentLeftMargin + currentWidth)) * inversePercent);
        } else {
            mPhoneLocationBar.setTranslationX(
                    (mUnfocusedLocationBarLayoutLeft - currentLeftMargin) * inversePercent);
            mUrlActionsContainer.setTranslationX(-mPhoneLocationBar.getTranslationX());
        }

        // Negate the location bar translation to keep the URL action container in the same
        // place during the focus expansion.  The check for RTL parity is required because
        // if they do not match then the action container will overlap the URL if we do not
        // allow it to be pushed off.
        if (isLocationBarRtl == ApiCompatibilityUtils.isLayoutRtl(this)) {
            mUrlActionsContainer.setTranslationX(-mPhoneLocationBar.getTranslationX());
        }

        mPhoneLocationBar.setUrlFocusChangePercent(mUrlExpansionPercent);

        // Ensure the buttons are invisible after focusing the omnibox to prevent them from
        // accepting click events.
        int toolbarButtonVisibility = mUrlExpansionPercent == 1f ? INVISIBLE : VISIBLE;
        mToolbarButtonsContainer.setVisibility(toolbarButtonVisibility);
        if (mHomeButton.getVisibility() != GONE) {
            mHomeButton.setVisibility(toolbarButtonVisibility);
        }

        // Force an invalidation of the location bar to properly handle the clipping of the URL
        // bar text as a result of the url action container translations.
        mPhoneLocationBar.invalidate();
        invalidate();

        Tab currentTab = getToolbarDataProvider().getTab();
        if (currentTab == null) return;

        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        // Explicitly use the focus change percentage here because it applies scroll compensation
        // that only applies during focus animations.
        if (ntp != null && mUrlFocusChangeInProgress) {
            ntp.setUrlFocusChangeAnimationPercent(mUrlFocusChangePercent);
        }

        if (!isLocationBarShownInNTP()) {
            // Reset these values in case we transitioned to a different page during the
            // transition.
            resetNtpAnimationValues();
            return;
        }

        updateNtpTransitionAnimation(ntp);
    }

    private void resetNtpAnimationValues() {
        mLocationBarBackgroundOffset.setEmpty();
        mPhoneLocationBar.setTranslationY(0);
        if (!mUrlFocusChangeInProgress) {
            mToolbarButtonsContainer.setTranslationY(0);
            mHomeButton.setTranslationY(0);
        }
        mToolbarShadow.setAlpha(1f);
        mPhoneLocationBar.setAlpha(1);
        mForceDrawLocationBarBackground = false;
        mUrlBackgroundAlpha = isIncognito()
                || (mUnfocusedLocationBarUsesTransparentBg
                && !mUrlFocusChangeInProgress
                && !mPhoneLocationBar.hasFocus())
                ? LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA : 255;
        setAncestorsShouldClipChildren(true);
        mNtpSearchBoxScrollPercent = UNINITIALIZED_PERCENT;
    }

    private void updateNtpTransitionAnimation(NewTabPage ntp) {
        if (mInTabSwitcherMode) return;

        setAncestorsShouldClipChildren(mUrlExpansionPercent == 0f);
        mToolbarShadow.setAlpha(0f);

        float growthPercent = 0f;
        if (mUrlExpansionPercent == 0f || mUrlExpansionPercent == 1f) {
            growthPercent = 1f - mUrlExpansionPercent;
        } else {
            // During the transition from search box to omnibox, keep the omnibox drawing
            // at the same size of the search box for first 40% of the scroll transition.
            growthPercent = mUrlExpansionPercent <= 0.4f
                    ? 1f : Math.min(1f, (1f - mUrlExpansionPercent) * 1.66667f);
        }

        int paddingTop = mPhoneLocationBar.getPaddingTop();
        int paddingBottom = mPhoneLocationBar.getPaddingBottom();

        ntp.getSearchBoxBounds(mNtpSearchBoxOriginalBounds, mNtpSearchBoxTransformedBounds);
        float halfHeightDifference = (mNtpSearchBoxTransformedBounds.height()
                - (mPhoneLocationBar.getMeasuredHeight() - paddingTop - paddingBottom
                + mLocationBarInsets)) / 2f;
        mPhoneLocationBar.setTranslationY(growthPercent == 0f ? 0 : Math.max(0,
                (mNtpSearchBoxTransformedBounds.top - mPhoneLocationBar.getTop()
                        + halfHeightDifference)));
        if (!mUrlFocusChangeInProgress) {
            float searchBoxTranslationY =
                    mNtpSearchBoxTransformedBounds.top - mNtpSearchBoxOriginalBounds.top;
            searchBoxTranslationY = Math.min(searchBoxTranslationY, 0);
            mToolbarButtonsContainer.setTranslationY(searchBoxTranslationY);
            mHomeButton.setTranslationY(searchBoxTranslationY);
        }

        mLocationBarBackgroundOffset.set(
                (int) ((mNtpSearchBoxTransformedBounds.left - mUrlViewportBounds.left
                        - mPhoneLocationBar.getPaddingLeft()) * growthPercent),
                (int) ((-halfHeightDifference - paddingTop) * growthPercent),
                (int) ((mNtpSearchBoxTransformedBounds.right - mUrlViewportBounds.right
                        + mPhoneLocationBar.getPaddingRight()) * growthPercent),
                (int) ((halfHeightDifference - paddingBottom + mLocationBarInsets)
                        * growthPercent));

        // The transparency of the location bar is dependent on how different its size is
        // from the final value.  This is based on how much growth is applied between the
        // desired size of the location bar to its drawn size.  The location bar then only
        // starts becoming opaque once the growth is at least half done.
        if (growthPercent >= 0.5f) {
            mPhoneLocationBar.setAlpha(0);
        } else {
            mPhoneLocationBar.setAlpha(1f - growthPercent * 2);
        }

        // Go from a transparent url background to a fully opaque one in the first 40% of the
        // scroll transition.
        mUrlBackgroundAlpha =
                mUrlExpansionPercent >= 0.4f ? 255 : (int) ((mUrlExpansionPercent * 2.5f) * 255);
        if (mUrlExpansionPercent == 1f) mUrlBackgroundAlpha = 255;
        mForceDrawLocationBarBackground = mUrlExpansionPercent != 0f;
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
        previousAlpha = mPhoneLocationBar.getAlpha();
        mPhoneLocationBar.setAlpha(previousAlpha * floatAlpha);
        // If the location bar is now fully transparent, do not bother drawing it.
        if (mPhoneLocationBar.getAlpha() != 0) {
            drawChild(canvas, mPhoneLocationBar, SystemClock.uptimeMillis());
        }
        mPhoneLocationBar.setAlpha(previousAlpha);

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
        if (mTabSwitcherAnimationMenuDrawable != null
                && mUrlExpansionPercent != 1f) {
            mTabSwitcherAnimationMenuDrawable.setBounds(
                    mMenuButton.getPaddingLeft(), mMenuButton.getPaddingTop(),
                    mMenuButton.getWidth() - mMenuButton.getPaddingRight(),
                    mMenuButton.getHeight() - mMenuButton.getPaddingBottom());
            translateCanvasToView(mToolbarButtonsContainer, mMenuButton, canvas);
            mTabSwitcherAnimationMenuDrawable.setAlpha(rgbAlpha);
            int color = mUseLightToolbarDrawables
                    ? mLightModeDefaultColor
                    : mDarkModeDefaultColor;
            mTabSwitcherAnimationMenuDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            mTabSwitcherAnimationMenuDrawable.draw(canvas);
        }

        canvas.restore();
    }

    @Override
    public void doInvalidate() {
        postInvalidateOnAnimation();
    }

    /**
     * Translates the canvas to ensure the specified view's coordinates are at 0, 0.
     *
     * @param from   The view the canvas is currently translated to.
     * @param to     The view to translate to.
     * @param canvas The canvas to be translated.
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
        if (child == mPhoneLocationBar) return drawLocationBar(canvas, drawingTime);
        boolean clipped = false;

        if (mLocationBarBackground != null
                && ((!mInTabSwitcherMode && !mTabSwitcherModeViews.contains(child))
                || (mInTabSwitcherMode && mBrowsingModeViews.contains(child)))) {
            canvas.save();
            if (mUrlExpansionPercent != 0f && mUrlViewportBounds.top < child.getBottom()) {
                // For other child views, use the inverse clipping of the URL viewport.
                // Only necessary during animations.
                // Hardware mode does not support unioned clip regions, so clip using the
                // appropriate bounds based on whether the child is to the left or right of the
                // location bar.
                boolean isLeft = (child == mNewTabButton || child == mHomeButton)
                        ^ LocalizationUtils.isLayoutRtl();

                int clipTop = mUrlViewportBounds.top;
                int clipBottom = mUrlViewportBounds.bottom;
                boolean verticalClip = false;
                if (mPhoneLocationBar.getTranslationY() > 0f) {
                    clipTop = child.getTop();
                    clipBottom = mUrlViewportBounds.top;
                    verticalClip = true;
                }

                if (isLeft) {
                    canvas.clipRect(
                            0, clipTop,
                            verticalClip ? child.getMeasuredWidth() : mUrlViewportBounds.left,
                            clipBottom);
                } else {
                    canvas.clipRect(
                            verticalClip ? 0 : mUrlViewportBounds.right,
                            clipTop, getMeasuredWidth(), clipBottom);
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
        float locationBarClipLeft = 0;
        float locationBarClipRight = 0;
        float locationBarClipTop = 0;
        float locationBarClipBottom = 0;

        if (mLocationBarBackground != null && (!mInTabSwitcherMode || mTextureCaptureMode)) {
            canvas.save();
            int backgroundAlpha = mUrlBackgroundAlpha;
            if (mTabSwitcherModeAnimation != null) {
                // Fade out/in the location bar towards the beginning of the animations to avoid
                // large jumps of stark white.
                backgroundAlpha =
                        (int) (Math.pow(mPhoneLocationBar.getAlpha(), 3) * backgroundAlpha);
            } else if (getToolbarDataProvider().isUsingBrandColor()
                    && !mBrandColorTransitionActive) {
                int unfocusedAlpha = mUnfocusedLocationBarUsesTransparentBg
                        ? LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA : 255;
                backgroundAlpha =
                        (int) (mUrlExpansionPercent * (255 - unfocusedAlpha) + unfocusedAlpha);
            }
            mLocationBarBackground.setAlpha(backgroundAlpha);

            if ((mPhoneLocationBar.getAlpha() > 0 || mForceDrawLocationBarBackground)
                    && !mTextureCaptureMode) {
                mLocationBarBackground.setBounds(
                        mUrlViewportBounds.left + mLocationBarBackgroundOffset.left,
                        mUrlViewportBounds.top + mLocationBarBackgroundOffset.top,
                        mUrlViewportBounds.right + mLocationBarBackgroundOffset.right,
                        mUrlViewportBounds.bottom + mLocationBarBackgroundOffset.bottom);
                mLocationBarBackground.draw(canvas);
            }

            locationBarClipLeft = mUrlViewportBounds.left + mPhoneLocationBar.getPaddingLeft()
                    + mLocationBarBackgroundOffset.left;
            locationBarClipRight = mUrlViewportBounds.right - mPhoneLocationBar.getPaddingRight()
                    + mLocationBarBackgroundOffset.right;

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

            locationBarClipTop = mUrlViewportBounds.top + mPhoneLocationBar.getPaddingTop()
                    + mLocationBarBackgroundOffset.top;
            locationBarClipBottom = mUrlViewportBounds.bottom - mPhoneLocationBar.getPaddingBottom()
                    + mLocationBarBackgroundOffset.bottom;
            // Clip the location bar child to the URL viewport calculated in onDraw.
            canvas.clipRect(
                    locationBarClipLeft, locationBarClipTop,
                    locationBarClipRight, locationBarClipBottom);
            clipped = true;
        }

        boolean retVal = super.drawChild(canvas, mPhoneLocationBar, drawingTime);

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
        return !(mInTabSwitcherMode || mTabSwitcherModeAnimation != null
                || urlHasFocus() || mUrlFocusChangeInProgress);
    }

    @Override
    protected void onNavigatedToDifferentPage() {
        super.onNavigatedToDifferentPage();
        if (FeatureUtilities.isDocumentMode(getContext())) {
            mUrlContainer.setTrailingTextVisible(true);
        }
    }

    @Override
    public void finishLoadProgress(boolean delayed) {
        super.finishLoadProgress(delayed);
        if (FeatureUtilities.isDocumentMode(getContext())) {
            mUrlContainer.setTrailingTextVisible(false);
        }
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
    }

    @Override
    public void getLocationBarContentRect(Rect outRect) {
        mLocationBarBackground.getPadding(outRect);
        int paddingLeft = outRect.left;
        int paddingTop = outRect.top;
        int paddingRight = outRect.right;
        int paddingBottom = outRect.bottom;

        updateUrlViewportBounds(outRect, VisualState.NORMAL, true);

        outRect.set(outRect.left + paddingLeft,
                outRect.top + paddingTop,
                outRect.right - paddingRight,
                outRect.bottom - paddingBottom);
    }

    @Override
    protected void onHomeButtonUpdate(boolean homeButtonEnabled) {
        if (homeButtonEnabled) {
            mHomeButton.setVisibility(urlHasFocus() || mInTabSwitcherMode ? INVISIBLE : VISIBLE);
            if (!mBrowsingModeViews.contains(mHomeButton)) {
                mBrowsingModeViews.add(mHomeButton);
            }
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
                updateViewsForTabSwitcherMode(mInTabSwitcherMode);
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
                updateViewsForTabSwitcherMode(mInTabSwitcherMode);
                // On older builds, force an update to ensure the new visuals are used
                // when bringing in the toolbar.  crbug.com/404571
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                    requestLayout();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mDelayedTabSwitcherModeAnimation = null;
                updateShadowVisibility(mInTabSwitcherMode);
                updateViewsForTabSwitcherMode(mInTabSwitcherMode);
            }
        });

        return exitAnimation;
    }

    @Override
    public void setTextureCaptureMode(boolean textureMode) {
        assert mTextureCaptureMode != textureMode;
        mTextureCaptureMode = textureMode;
        if (mTextureCaptureMode) {
            mPreTextureCaptureAlpha = getAlpha();
            setAlpha(1);
        } else {
            setAlpha(mPreTextureCaptureAlpha);
            mPreTextureCaptureAlpha = 1f;
        }
    }

    private boolean isTabSwitcherAnimationRunning() {
        return mUIAnimatingTabSwitcherTransition
                || (mTabSwitcherModeAnimation != null && mTabSwitcherModeAnimation.isRunning())
                || (mDelayedTabSwitcherModeAnimation != null
                && mDelayedTabSwitcherModeAnimation.isRunning());
    }

    private void updateViewsForTabSwitcherMode(boolean isInTabSwitcherMode) {
        int tabSwitcherViewsVisibility = isInTabSwitcherMode ? VISIBLE : INVISIBLE;
        int browsingViewsVisibility = isInTabSwitcherMode ? INVISIBLE : VISIBLE;

        for (View view : mTabSwitcherModeViews) {
            view.setVisibility(tabSwitcherViewsVisibility);
        }
        for (View view : mBrowsingModeViews) {
            view.setVisibility(browsingViewsVisibility);
        }
        getProgressBar().setVisibility(
                isInTabSwitcherMode || isTabSwitcherAnimationRunning() ? INVISIBLE : VISIBLE);
        updateVisualsForToolbarState(isInTabSwitcherMode);

    }

    @Override
    protected void setContentAttached(boolean attached) {
        updateVisualsForToolbarState(mInTabSwitcherMode);
    }

    @Override
    protected void setTabSwitcherMode(
            boolean inTabSwitcherMode, boolean showToolbar, boolean delayAnimation) {
        if (mInTabSwitcherMode == inTabSwitcherMode) return;

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
            updateViewsForTabSwitcherMode(true);
            mTabSwitcherModeAnimation = createEnterTabSwitcherModeAnimation();
        } else {
            if (!mDelayingTabSwitcherAnimation) {
                mTabSwitcherModeAnimation = createExitTabSwitcherAnimation(showToolbar);
            }
            mUIAnimatingTabSwitcherTransition = true;
        }

        mAnimateNormalToolbar = showToolbar;
        mInTabSwitcherMode = inTabSwitcherMode;
        if (mTabSwitcherModeAnimation != null) mTabSwitcherModeAnimation.start();

        if (SysUtils.isLowEndDevice()) finishAnimations();

        postInvalidateOnAnimation();
    }

    @Override
    protected void onTabSwitcherTransitionFinished() {
        setAlpha(1.f);
        mClipRect = null;
        mUIAnimatingTabSwitcherTransition = false;
        if (!mAnimateNormalToolbar) {
            finishAnimations();
            updateVisualsForToolbarState(mInTabSwitcherMode);
        }

        if (mDelayingTabSwitcherAnimation) {
            mDelayingTabSwitcherAnimation = false;
            mDelayedTabSwitcherModeAnimation = createPostExitTabSwitcherAnimation();
            mDelayedTabSwitcherModeAnimation.start();
        } else {
            updateViewsForTabSwitcherMode(mInTabSwitcherMode);
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

        if (shouldShowMenuButton()) {
            Resources res = getResources();
            mTabSwitcherAnimationMenuDrawable = ApiCompatibilityUtils.getDrawable(
                    res, R.drawable.btn_menu).mutate();
            mTabSwitcherAnimationMenuDrawable.setColorFilter(
                    isIncognito() ? mLightModeDefaultColor : mDarkModeDefaultColor,
                    PorterDuff.Mode.SRC_IN);
            ((BitmapDrawable) mTabSwitcherAnimationMenuDrawable).setGravity(Gravity.CENTER);
        }
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
        return super.shouldIgnoreSwipeGesture() || mUrlExpansionPercent > 0f;
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

        for (int i = 0; i < mPhoneLocationBar.getChildCount(); i++) {
            View childView = mPhoneLocationBar.getChildAt(i);
            if (childView == mPhoneLocationBar.getFirstViewVisibleWhenFocused()) break;
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
                mMenuButton, TRANSLATION_X, toolbarButtonTranslationX);
        animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
        animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
        animators.add(animator);

        animator = ObjectAnimator.ofFloat(mMenuButton, ALPHA, 0);
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

        animator = ObjectAnimator.ofFloat(mMenuButton, TRANSLATION_X, 0);
        animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
        animator.setStartDelay(URL_CLEAR_FOCUS_MENU_DELAY_MS);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animators.add(animator);

        animator = ObjectAnimator.ofFloat(mMenuButton, ALPHA, 1);
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

        for (int i = 0; i < mPhoneLocationBar.getChildCount(); i++) {
            View childView = mPhoneLocationBar.getChildAt(i);
            if (childView == mPhoneLocationBar.getFirstViewVisibleWhenFocused()) break;
            animator = ObjectAnimator.ofFloat(childView, ALPHA, 1);
            animator.setStartDelay(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setDuration(URL_CLEAR_FOCUS_MENU_DELAY_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);
        }

        if (isLocationBarShownInNTP() && mNtpSearchBoxScrollPercent == 0f) return;

        if (!FeatureUtilities.isDocumentMode(getContext())
                || mPhoneLocationBar.showingQueryInTheOmnibox()) {
            // The call to getLayout() can return null briefly during text changes, but as it
            // is only needed for RTL calculations, we proceed if the location bar is showing
            // LTR content.
            boolean isLocationBarRtl = ApiCompatibilityUtils.isLayoutRtl(mPhoneLocationBar);
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
                    animator = ObjectAnimator.ofInt(
                            mUrlBar,
                            buildUrlScrollProperty(mPhoneLocationBar, isLocationBarRtl),
                            urlBarStartScrollX);
                    animator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
                    animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
                    animators.add(animator);
                }
            }
        }
    }

    @Override
    public void onUrlFocusChange(final boolean hasFocus) {
        super.onUrlFocusChange(hasFocus);

        triggerUrlFocusAnimation(hasFocus);

        TransitionDrawable shadowDrawable = (TransitionDrawable) mToolbarShadow.getDrawable();
        if (hasFocus) {
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

        List<Animator> animators = new ArrayList<Animator>();
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
                mPhoneLocationBar.finishUrlFocusChange(hasFocus);
                mUrlFocusChangeInProgress = false;
            }
        });
        mUrlFocusLayoutAnimator.start();
    }

    @Override
    protected boolean shouldShowMenuButton() {
        // Even in Document mode, the toolbar menu button will be shown while on the NTP.  This
        // allows the menu to translate off the screen on scroll to match the tabbed behavior.
        if (mVisualState == VisualState.NEW_TAB_NORMAL) return true;

        return !mPhoneLocationBar.showMenuButtonInOmnibox() && super.shouldShowMenuButton();
    }

    @Override
    protected void updateTabCountVisuals(int numberOfTabs) {
        if (mHomeButton != null) mHomeButton.setEnabled(true);

        if (mToggleTabStackButton == null) return;

        mToggleTabStackButton.setEnabled(numberOfTabs >= 1);
        mToggleTabStackButton.setContentDescription(
                getResources().getString(R.string.accessibility_toolbar_btn_tabswitcher_toggle,
                        numberOfTabs));
        mTabSwitcherButtonDrawableLight.updateForTabCount(numberOfTabs, isIncognito());
        mTabSwitcherButtonDrawable.updateForTabCount(numberOfTabs, isIncognito());

        boolean useTabStackDrawableLight = isIncognito();
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
        updateVisualsForToolbarState(mInTabSwitcherMode);
    }

    @Override
    protected void onTabOrModelChanged() {
        super.onTabOrModelChanged();
        updateNtpAnimationState();
        updateVisualsForToolbarState(mInTabSwitcherMode);
    }

    private static boolean isVisualStateValidForBrandColorTransition(VisualState state) {
        return state == VisualState.NORMAL || state == VisualState.BRAND_COLOR;
    }

    @Override
    protected void onPrimaryColorChanged(boolean shouldAnimate) {
        super.onPrimaryColorChanged(shouldAnimate);
        if (mBrandColorTransitionActive) mBrandColorTransitionAnimation.cancel();
        if (!shouldAnimate || !isVisualStateValidForBrandColorTransition(mVisualState)) {
            return;
        }
        final int initialColor = mToolbarBackground.getColor();
        final int finalColor = getToolbarDataProvider().getPrimaryColor();
        if (initialColor == finalColor) return;
        boolean shouldUseOpaque = ColorUtils.shouldUseOpaqueTextboxBackground(finalColor);
        final int initialAlpha = mUrlBackgroundAlpha;
        final int finalAlpha =
                shouldUseOpaque ? 255 : LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
        final boolean shouldAnimateAlpha = initialAlpha != finalAlpha;
        mBrandColorTransitionAnimation = ValueAnimator.ofFloat(0, 1)
                .setDuration(BRAND_COLOR_TRANSITION_DURATION_MS);
        mBrandColorTransitionAnimation.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        mBrandColorTransitionAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                int red = (int) (Color.red(initialColor)
                        + fraction * (Color.red(finalColor) - Color.red(initialColor)));
                int green = (int) (Color.green(initialColor)
                        + fraction * (Color.green(finalColor) - Color.green(initialColor)));
                int blue = (int) (Color.blue(initialColor)
                        + fraction * (Color.blue(finalColor) - Color.blue(initialColor)));
                if (shouldAnimateAlpha) {
                    mUrlBackgroundAlpha =
                            (int) (initialAlpha + fraction * (finalAlpha - initialAlpha));
                }
                updateToolbarBackground(Color.rgb(red, green, blue));
            }
        });
        mBrandColorTransitionAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBrandColorTransitionActive = false;
                updateVisualsForToolbarState(mInTabSwitcherMode);
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
            if (!mInTabSwitcherMode && previousNtpScrollPercent > 0f) {
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
                updateVisualsForToolbarState(mInTabSwitcherMode);
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

    private void updateShadowVisibility(boolean isInTabSwitcherMode) {
        boolean shouldDrawShadow = !isInTabSwitcherMode && !isTabSwitcherAnimationRunning();
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

    private void updateVisualsForToolbarState(boolean isInTabSwitcherMode) {
        final boolean isIncognito = isIncognito();

        VisualState newVisualState = computeVisualState(isInTabSwitcherMode);

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
        if (mVisualState == VisualState.BRAND_COLOR && !visualStateChanged) {
            boolean useLightToolbarDrawables =
                    ColorUtils.shoudUseLightForegroundOnBackground(currentPrimaryColor);
            boolean unfocusedLocationBarUsesTransparentBg =
                    !ColorUtils.shouldUseOpaqueTextboxBackground(currentPrimaryColor);
            if (useLightToolbarDrawables != mUseLightToolbarDrawables
                    || unfocusedLocationBarUsesTransparentBg
                    != mUnfocusedLocationBarUsesTransparentBg) {
                visualStateChanged = true;
            } else {
                updateToolbarBackground(VisualState.BRAND_COLOR);
                getProgressBar().setBackgroundColor(
                        ColorUtils.getLightProgressbarBackground(currentPrimaryColor));
            }
        }

        mVisualState = newVisualState;

        updateOverlayDrawables();
        updateShadowVisibility(isInTabSwitcherMode);
        if (!visualStateChanged) {
            if (mVisualState == VisualState.NEW_TAB_NORMAL) {
                updateNtpTransitionAnimation(
                        getToolbarDataProvider().getNewTabPageForCurrentTab());
            }
            return;
        }

        mUseLightToolbarDrawables = false;
        mUnfocusedLocationBarUsesTransparentBg = false;
        mUrlBackgroundAlpha = 255;
        int progressBarBackgroundColor = mProgressBackBackgroundColor;
        updateToolbarBackground(mVisualState);
        if (isInTabSwitcherMode) {
            mUseLightToolbarDrawables = true;
            mUrlBackgroundAlpha = LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
            progressBarBackgroundColor = mProgressBackBackgroundColorWhite;
        } else if (isIncognito()) {
            mUseLightToolbarDrawables = true;
            mUrlBackgroundAlpha = LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA;
            progressBarBackgroundColor = mProgressBackBackgroundColorWhite;
        } else if (mVisualState == VisualState.BRAND_COLOR) {
            mUseLightToolbarDrawables =
                    ColorUtils.shoudUseLightForegroundOnBackground(currentPrimaryColor);
            mUnfocusedLocationBarUsesTransparentBg =
                    !ColorUtils.shouldUseOpaqueTextboxBackground(currentPrimaryColor);
            mUrlBackgroundAlpha = mUnfocusedLocationBarUsesTransparentBg
                    ? LOCATION_BAR_TRANSPARENT_BACKGROUND_ALPHA : 255;
            progressBarBackgroundColor =
                    ColorUtils.getLightProgressbarBackground(currentPrimaryColor);
        }

        getProgressBar().setBackgroundColor(progressBarBackgroundColor);
        int progressBarForegroundColor = ApiCompatibilityUtils.getColor(getResources(),
                mUseLightToolbarDrawables
                        ? R.color.progress_bar_foreground_white
                        : R.color.progress_bar_foreground);
        getProgressBar().setForegroundColor(progressBarForegroundColor);


        if (mToggleTabStackButton != null) {
            mToggleTabStackButton.setImageDrawable(mUseLightToolbarDrawables
                    ? mTabSwitcherButtonDrawableLight : mTabSwitcherButtonDrawable);
            if (mTabSwitcherAnimationTabStackDrawable != null) {
                mTabSwitcherAnimationTabStackDrawable.setTint(
                        mUseLightToolbarDrawables ? mLightModeTint : mDarkModeTint);
            }
        }

        if (shouldShowMenuButton()) {
            mMenuButton.setTint(mUseLightToolbarDrawables ? mLightModeTint : mDarkModeTint);
        }
        if (mHomeButton.getVisibility() != GONE) {
            mHomeButton.setTint(mUseLightToolbarDrawables ? mLightModeTint : mDarkModeTint);
        }

        mPhoneLocationBar.updateVisualsForState();
        // Remove the side padding for incognito to ensure the badge icon aligns correctly with the
        // background of the location bar.
        if (isIncognito) {
            mPhoneLocationBar.setPadding(
                    0, mUrlBackgroundPadding.top, 0, mUrlBackgroundPadding.bottom);
        } else {
            mPhoneLocationBar.setPadding(
                    mUrlBackgroundPadding.left, mUrlBackgroundPadding.top,
                    mUrlBackgroundPadding.right, mUrlBackgroundPadding.bottom);
        }

        // We update the alpha before comparing the visual state as we need to change
        // its value when entering and exiting TabSwitcher mode.
        if (isLocationBarShownInNTP() && !isInTabSwitcherMode) {
            updateNtpTransitionAnimation(
                    getToolbarDataProvider().getNewTabPageForCurrentTab());
        }

        if (isInTabSwitcherMode) mNewTabButton.setIsIncognito(isIncognito);

        CharSequence newTabContentDescription = getResources().getText(
                isIncognito ? R.string.accessibility_toolbar_btn_new_incognito_tab :
                        R.string.accessibility_toolbar_btn_new_tab);
        if (mNewTabButton != null
                && !newTabContentDescription.equals(mNewTabButton.getContentDescription())) {
            mNewTabButton.setContentDescription(newTabContentDescription);
        }

        getMenuButton().setVisibility(shouldShowMenuButton() ? View.VISIBLE : View.GONE);
    }

    @Override
    public LocationBar getLocationBar() {
        return mPhoneLocationBar;
    }
}

