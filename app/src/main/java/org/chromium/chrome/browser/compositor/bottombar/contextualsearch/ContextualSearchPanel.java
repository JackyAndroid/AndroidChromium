// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.bottombar.OverlayContentProgressObserver;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelContent;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelManager;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelManager.PanelPriority;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPromoControl.ContextualSearchPromoHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilterHost;
import org.chromium.chrome.browser.compositor.scene_layer.ContextualSearchSceneLayer;
import org.chromium.chrome.browser.compositor.scene_layer.SceneOverlayLayer;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.resources.ResourceManager;

/**
 * Controls the Contextual Search Panel.
 */
public class ContextualSearchPanel extends OverlayPanel {

    /**
     * The delay after which the hide progress will be hidden.
     */
    private static final long HIDE_PROGRESS_BAR_DELAY = 1000 / 60 * 4;

    /**
     * Used for logging state changes.
     */
    private final ContextualSearchPanelMetrics mPanelMetrics;

    /**
     * The height of the bar shadow, in pixels.
     */
    private final float mBarShadowHeightPx;

    /**
     * Whether the Panel should be promoted to a new tab after being maximized.
     */
    private boolean mShouldPromoteToTabAfterMaximizing;

    /**
     * The object for handling global Contextual Search management duties
     */
    private ContextualSearchManagementDelegate mManagementDelegate;

    /**
     * Whether the content view has been touched.
     */
    private boolean mHasContentBeenTouched;

    /**
     * The compositor layer used for drawing the panel.
     */
    private ContextualSearchSceneLayer mSceneLayer;

    /**
     * The distance of the divider from the end of the bar, in dp.
     */
    private final float mEndButtonWidthDp;

    // ============================================================================================
    // Constructor
    // ============================================================================================

    /**
     * @param context The current Android {@link Context}.
     * @param updateHost The {@link LayoutUpdateHost} used to request updates in the Layout.
     * @param eventHost The {@link EventFilterHost} for propagating events.
     * @param panelManager The object managing the how different panels are shown.
     */
    public ContextualSearchPanel(Context context, LayoutUpdateHost updateHost,
                EventFilterHost eventHost, OverlayPanelManager panelManager) {
        super(context, updateHost, eventHost, panelManager);
        mSceneLayer = createNewContextualSearchSceneLayer();
        mPanelMetrics = new ContextualSearchPanelMetrics();

        mBarShadowHeightPx = ApiCompatibilityUtils.getDrawable(mContext.getResources(),
                R.drawable.contextual_search_bar_shadow).getIntrinsicHeight();
        mEndButtonWidthDp = mPxToDp * (float) mContext.getResources().getDimensionPixelSize(
                R.dimen.contextual_search_end_button_width);
    }

    @Override
    public OverlayPanelContent createNewOverlayPanelContent() {
        return new OverlayPanelContent(mManagementDelegate.getOverlayContentDelegate(),
                new PanelProgressObserver(), mActivity);
    }

    /**
     * Default loading animation for a panel.
     */
    public class PanelProgressObserver extends OverlayContentProgressObserver {

        @Override
        public void onProgressBarStarted() {
            setProgressBarCompletion(0);
            setProgressBarVisible(true);
            requestUpdate();
        }

        @Override
        public void onProgressBarUpdated(int progress) {
            setProgressBarCompletion(progress);
            requestUpdate();
        }

        @Override
        public void onProgressBarFinished() {
            // Hides the Progress Bar after a delay to make sure it is rendered for at least
            // a few frames, otherwise its completion won't be visually noticeable.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setProgressBarVisible(false);
                    requestUpdate();
                }
            }, HIDE_PROGRESS_BAR_DELAY);
        }
    }

    // ============================================================================================
    // Scene Overlay
    // ============================================================================================

    /**
     * Create a new scene layer for this panel. This should be overridden by tests as necessary.
     */
    protected ContextualSearchSceneLayer createNewContextualSearchSceneLayer() {
        return new ContextualSearchSceneLayer(mContext.getResources().getDisplayMetrics().density);
    }

    @Override
    public SceneOverlayLayer getUpdatedSceneOverlayTree(LayerTitleCache layerTitleCache,
            ResourceManager resourceManager, float yOffset) {
        mSceneLayer.update(resourceManager, this,
                getSearchBarControl(),
                getPeekPromoControl(),
                getPromoControl(),
                getImageControl());

        return mSceneLayer;
    }

    // ============================================================================================
    // Contextual Search Manager Integration
    // ============================================================================================

    /**
     * Sets the {@code ContextualSearchManagementDelegate} associated with this panel.
     * @param delegate The {@code ContextualSearchManagementDelegate}.
     */
    public void setManagementDelegate(ContextualSearchManagementDelegate delegate) {
        if (mManagementDelegate != delegate) {
            mManagementDelegate = delegate;
            if (delegate != null) {
                setChromeActivity(mManagementDelegate.getChromeActivity());
            }
        }
    }

    /**
     * Notifies that the preference state has changed.
     * @param isEnabled Whether the feature is enabled.
     */
    public void onContextualSearchPrefChanged(boolean isEnabled) {
        if (!isShowing()) return;

        getPromoControl().onContextualSearchPrefChanged(isEnabled);
    }

    // ============================================================================================
    // Panel State
    // ============================================================================================

    @Override
    public void setPanelState(PanelState toState, StateChangeReason reason) {
        PanelState fromState = getPanelState();

        mPanelMetrics.onPanelStateChanged(fromState, toState, reason);

        if (toState == PanelState.PEEKED
                && (fromState == PanelState.CLOSED || fromState == PanelState.UNDEFINED)) {
            // If the Peek Promo is visible, it should animate when the SearchBar peeks.
            if (getPeekPromoControl().isVisible()) {
                getPeekPromoControl().animateAppearance();
            }
            if (getImageControl().getIconSpriteControl().shouldAnimateAppearance()) {
                mPanelMetrics.setWasIconSpriteAnimated(true);
                getImageControl().getIconSpriteControl().animateApperance();
            } else {
                mPanelMetrics.setWasIconSpriteAnimated(false);
            }
        }

        if (fromState == PanelState.PEEKED
                && (toState == PanelState.EXPANDED || toState == PanelState.MAXIMIZED)) {
            // After opening the Panel to either expanded or maximized state,
            // the promo should disappear.
            getPeekPromoControl().hide();
        }

        super.setPanelState(toState, reason);
    }

    @Override
    protected boolean isSupportedState(PanelState state) {
        return canDisplayContentInPanel() || state != PanelState.MAXIMIZED;
    }

    @Override
    protected float getExpandedHeight() {
        if (canDisplayContentInPanel()) {
            return super.getExpandedHeight();
        } else {
            return getBarHeightPeeking() + getPromoHeightPx() * mPxToDp;
        }
    }

    @Override
    protected PanelState getProjectedState(float velocity) {
        PanelState projectedState = super.getProjectedState(velocity);

        // Prevent the fling gesture from moving the Panel from PEEKED to MAXIMIZED. This is to
        // make sure the Promo will be visible, considering that the EXPANDED state is the only
        // one that will show the Promo.
        if (getPromoControl().isVisible()
                && projectedState == PanelState.MAXIMIZED
                && getPanelState() == PanelState.PEEKED) {
            projectedState = PanelState.EXPANDED;
        }

        return projectedState;
    }

    // ============================================================================================
    // Contextual Search Manager Integration
    // ============================================================================================

    @Override
    protected void onClosed(StateChangeReason reason) {
        // Must be called before destroying Content because unseen visits should be removed from
        // history, and if the Content gets destroyed there won't be a ContentViewCore to do that.
        mManagementDelegate.onCloseContextualSearch(reason);

        setProgressBarCompletion(0);
        setProgressBarVisible(false);
        getImageControl().hideStaticImage(false);

        super.onClosed(reason);

        if (mSceneLayer != null) mSceneLayer.hideTree();
    }

    // ============================================================================================
    // Generic Event Handling
    // ============================================================================================

    private boolean isCoordinateInsideActionTarget(float x) {
        if (LocalizationUtils.isLayoutRtl()) {
            return x >= getContentX() + mEndButtonWidthDp;
        } else {
            return x <= getContentX() + getWidth() - mEndButtonWidthDp;
        }
    }

    /**
     * Handles a bar click. The position is given in dp.
     */
    @Override
    public void handleBarClick(long time, float x, float y) {
        getSearchBarControl().onSearchBarClick(x);

        if (isPeeking()) {
            if (getSearchBarControl().getQuickActionControl().hasQuickAction()
                    && isCoordinateInsideActionTarget(x)) {
                mPanelMetrics.setWasQuickActionClicked();
                getSearchBarControl().getQuickActionControl().sendIntent();
            } else {
                // super takes care of expanding the Panel when peeking.
                super.handleBarClick(time, x, y);
            }
        } else if (isExpanded() || isMaximized()) {
            if (isCoordinateInsideCloseButton(x)) {
                closePanel(StateChangeReason.CLOSE_BUTTON, true);
            } else if (canPromoteToNewTab()) {
                mManagementDelegate.promoteToTab();
            }
        }
    }

    @Override
    public boolean onInterceptBarClick() {
        return onInterceptOpeningPanel();
    }

    @Override
    public boolean onInterceptBarSwipe() {
        return onInterceptOpeningPanel();
    }

    /**
     * @return True if the event on the bar was intercepted.
     */
    private boolean onInterceptOpeningPanel() {
        if (mManagementDelegate.isRunningInCompatibilityMode()) {
            mManagementDelegate.openResolvedSearchUrlInNewTab();
            return true;
        }
        return false;
    }

    @Override
    public void onShowPress(float x, float y) {
        if (isCoordinateInsideBar(x, y)) getSearchBarControl().onShowPress(x);
        super.onShowPress(x, y);
    }

    // ============================================================================================
    // Panel base methods
    // ============================================================================================

    @Override
    protected void destroyComponents() {
        super.destroyComponents();
        destroyPromoControl();
        destroyPeekPromoControl();
        destroySearchBarControl();
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        super.onActivityStateChange(activity, newState);
        if (newState == ActivityState.PAUSED) {
            mManagementDelegate.logCurrentState();
        }
    }

    @Override
    public PanelPriority getPriority() {
        return PanelPriority.HIGH;
    }

    @Override
    public boolean canBeSuppressed() {
        // The selected text on the page is lost when the panel is closed, thus, this panel cannot
        // be restored if it is suppressed.
        return false;
    }

    @Override
    public void notifyBarTouched(float x) {
        if (canDisplayContentInPanel()) {
            getOverlayPanelContent().showContent();
        }
    }

    @Override
    public float getContentY() {
        return getOffsetY() + getBarContainerHeight() + getPromoHeightPx() * mPxToDp;
    }

    @Override
    public float getBarContainerHeight() {
        return getBarHeight() + getPeekPromoControl().getHeightPx();
    }

    @Override
    protected float getPeekedHeight() {
        return getBarHeightPeeking() + getPeekPromoControl().getHeightPeekingPx() * mPxToDp;
    }

    @Override
    protected float calculateBarShadowOpacity() {
        float barShadowOpacity = 0.f;
        if (getPromoHeightPx() > 0.f) {
            float threshold = 2 * mBarShadowHeightPx;
            barShadowOpacity = getPromoHeightPx() > mBarShadowHeightPx ? 1.f
                    : MathUtils.interpolate(0.f, 1.f, getPromoHeightPx() / threshold);
        }
        return barShadowOpacity;
    }

    // ============================================================================================
    // Animation Handling
    // ============================================================================================

    @Override
    protected void onAnimationFinished() {
        super.onAnimationFinished();

        if (mShouldPromoteToTabAfterMaximizing && getPanelState() == PanelState.MAXIMIZED) {
            mShouldPromoteToTabAfterMaximizing = false;
            mManagementDelegate.promoteToTab();
        }
    }

    // ============================================================================================
    // Contextual Search Panel API
    // ============================================================================================

    /**
     * Notify the panel that the ContentViewCore was seen.
     */
    public void setWasSearchContentViewSeen() {
        mPanelMetrics.setWasSearchContentViewSeen();
    }

    /**
     * @param isActive Whether the promo is active.
     */
    public void setIsPromoActive(boolean isActive, boolean isMandatory) {
        if (isActive) {
            getPromoControl().show(isMandatory);
        } else {
            getPromoControl().hide();
        }

        mPanelMetrics.setIsPromoActive(isActive);
    }

    /**
     * Shows the peek promo.
     */
    public void showPeekPromo() {
        getPeekPromoControl().show();
    }

    /**
     * @return Whether the Peek Promo is visible.
     */
    @VisibleForTesting
    public boolean isPeekPromoVisible() {
        return getPeekPromoControl().isVisible();
    }

    /**
     * Called when the SERP finishes loading, this records the duration of loading the SERP from
     * the time the panel was opened until the present.
     * @param wasPrefetch Whether the request was prefetch-enabled.
     */
    public void onSearchResultsLoaded(boolean wasPrefetch) {
        mPanelMetrics.onSearchResultsLoaded(wasPrefetch);
    }

    /**
     * Called after the panel has navigated to prefetched Search Results.
     * If the user has the panel open then they will see the prefetched result starting to load.
     * Currently this just logs the time between the start of the search until the results start to
     * render in the Panel.
     * @param didResolve Whether the search required the Search Term to be resolved.
     */
    public void onPanelNavigatedToPrefetchedSearch(boolean didResolve) {
        mPanelMetrics.onPanelNavigatedToPrefetchedSearch(didResolve);
    }

    /**
     * Maximizes the Contextual Search Panel, then promotes it to a regular Tab.
     * @param reason The {@code StateChangeReason} behind the maximization and promotion to tab.
     */
    public void maximizePanelThenPromoteToTab(StateChangeReason reason) {
        mShouldPromoteToTabAfterMaximizing = true;
        maximizePanel(reason);
    }

    /**
     * Maximizes the Contextual Search Panel, then promotes it to a regular Tab.
     * @param reason The {@code StateChangeReason} behind the maximization and promotion to tab.
     * @param duration The animation duration in milliseconds.
     */
    public void maximizePanelThenPromoteToTab(StateChangeReason reason, long duration) {
        mShouldPromoteToTabAfterMaximizing = true;
        animatePanelToState(PanelState.MAXIMIZED, reason, duration);
    }

    @Override
    public void peekPanel(StateChangeReason reason) {
        super.peekPanel(reason);

        if (getPanelState() == PanelState.CLOSED || getPanelState() == PanelState.PEEKED) {
            mHasContentBeenTouched = false;
        }

        if (getPanelState() == PanelState.CLOSED) mPanelMetrics.onPanelTriggered();
    }

    @Override
    public void closePanel(StateChangeReason reason, boolean animate) {
        super.closePanel(reason, animate);
        mHasContentBeenTouched = false;
    }

    @Override
    public PanelState getPanelState() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getPanelState();
    }

    /**
     * Gets whether a touch on the content view has been done yet or not.
     */
    public boolean didTouchContent() {
        return mHasContentBeenTouched;
    }

    /**
     * Sets the search term to display in the SearchBar.
     * This should be called when the search term is set without search term resolution, or
     * after search term resolution completed.
     * @param searchTerm The string that represents the search term.
     */
    public void setSearchTerm(String searchTerm) {
        getImageControl().hideStaticImage(true);
        getSearchBarControl().setSearchTerm(searchTerm);
        mPanelMetrics.onSearchRequestStarted();
    }

    /**
     * Sets the search context to display in the SearchBar.
     * @param selection The portion of the context that represents the user's selection.
     * @param end The portion of the context from the selection to its end.
     */
    public void setSearchContext(String selection, String end) {
        getImageControl().hideStaticImage(true);
        getSearchBarControl().setSearchContext(selection, end);
        mPanelMetrics.onSearchRequestStarted();
    }

    /**
     * Sets the caption to display in the SearchBar.
     * When the caption is displayed, the Search Term is pushed up and the caption shows below.
     * @param caption The string to show in as the caption.
     */
    public void setCaption(String caption) {
        getSearchBarControl().setCaption(caption);
    }

    /**
     * Handles showing the resolved search term in the SearchBar.
     * @param searchTerm The string that represents the search term.
     * @param thumbnailUrl The URL of the thumbnail to display.
     * @param quickActionUri The URI for the intent associated with the quick action.
     * @param quickActionCategory The {@link QuickActionCategory} for the quick action.
     */
    public void onSearchTermResolved(String searchTerm, String thumbnailUrl, String quickActionUri,
            int quickActionCategory) {
        mPanelMetrics.onSearchTermResolved();
        getSearchBarControl().setSearchTerm(searchTerm);
        getSearchBarControl().animateSearchTermResolution();
        getSearchBarControl().setQuickAction(quickActionUri, quickActionCategory);
        getImageControl().setThumbnailUrl(thumbnailUrl);
    }

    // ============================================================================================
    // Panel Metrics
    // ============================================================================================

    // TODO(pedrosimonetti): replace proxy methods with direct PanelMetrics usage

    /**
     * @return The {@link ContextualSearchPanelMetrics}.
     */
    public ContextualSearchPanelMetrics getPanelMetrics() {
        return mPanelMetrics;
    }

    /**
     * Sets that the contextual search involved the promo.
     */
    public void setDidSearchInvolvePromo() {
        mPanelMetrics.setDidSearchInvolvePromo();
    }

    /**
     * @param wasPartOfUrl Whether the selected text was part of a URL.
     */
    public void setWasSelectionPartOfUrl(boolean wasPartOfUrl) {
        mPanelMetrics.setWasSelectionPartOfUrl(wasPartOfUrl);
    }

    // ============================================================================================
    // Panel Rendering
    // ============================================================================================

    // TODO(pedrosimonetti): generalize the dispatching of panel updates.

    @Override
    protected void updatePanelForCloseOrPeek(float percentage) {
        super.updatePanelForCloseOrPeek(percentage);

        getPromoControl().onUpdateFromCloseToPeek(percentage);
        getPeekPromoControl().onUpdateFromCloseToPeek(percentage);
        getSearchBarControl().onUpdateFromCloseToPeek(percentage);
    }

    @Override
    protected void updatePanelForExpansion(float percentage) {
        super.updatePanelForExpansion(percentage);

        getPromoControl().onUpdateFromPeekToExpand(percentage);
        getPeekPromoControl().onUpdateFromPeekToExpand(percentage);
        getSearchBarControl().onUpdateFromPeekToExpand(percentage);
    }

    @Override
    protected void updatePanelForMaximization(float percentage) {
        super.updatePanelForMaximization(percentage);

        getPromoControl().onUpdateFromExpandToMaximize(percentage);
        getPeekPromoControl().onUpdateFromExpandToMaximize(percentage);
    }

    @Override
    protected void updatePanelForSizeChange() {
        if (getPromoControl().isVisible()) {
            getPromoControl().invalidate(true);
        }

        // NOTE(pedrosimonetti): We cannot tell where the selection will be after the
        // orientation change, so we are setting the selection position to zero, which
        // means the base page will be positioned in its original state and we won't
        // try to keep the selection in view.
        updateBasePageSelectionYPx(0.f);
        updateBasePageTargetY();

        super.updatePanelForSizeChange();
    }

    // ============================================================================================
    // Selection position
    // ============================================================================================

    /** The approximate Y coordinate of the selection in pixels. */
    private float mBasePageSelectionYPx = -1.f;

    /**
     * Updates the coordinate of the existing selection.
     * @param y The y coordinate of the selection in pixels.
     */
    public void updateBasePageSelectionYPx(float y) {
        mBasePageSelectionYPx = y;
    }

    @Override
    protected float calculateBasePageDesiredOffset() {
        float offset = 0.f;
        if (mBasePageSelectionYPx > 0.f) {
            // Convert from px to dp.
            final float selectionY = mBasePageSelectionYPx * mPxToDp;

            // Calculate the offset to center the selection on the available area.
            final float availableHeight = getTabHeight() - getExpandedHeight();
            offset = -selectionY + availableHeight / 2;
        }
        return offset;
    }

    // ============================================================================================
    // ContextualSearchBarControl
    // ============================================================================================

    private ContextualSearchBarControl mSearchBarControl;

    /**
     * Creates the ContextualSearchBarControl, if needed. The Views are set to INVISIBLE, because
     * they won't actually be displayed on the screen (their snapshots will be displayed instead).
     */
    public ContextualSearchBarControl getSearchBarControl() {
        if (mSearchBarControl == null) {
            mSearchBarControl =
                    new ContextualSearchBarControl(this, mContext, mContainerView, mResourceLoader);
        }
        return mSearchBarControl;
    }

    /**
     * Destroys the ContextualSearchBarControl.
     */
    protected void destroySearchBarControl() {
        if (mSearchBarControl != null) {
            mSearchBarControl.destroy();
            mSearchBarControl = null;
        }
    }

    // ============================================================================================
    // Image Control
    // ============================================================================================
    /**
     * @return The {@link ContextualSearchImageControl} for the panel.
     */
    public ContextualSearchImageControl getImageControl() {
        return getSearchBarControl().getImageControl();
    }

    // ============================================================================================
    // Peek Promo
    // ============================================================================================

    private ContextualSearchPeekPromoControl mPeekPromoControl;

    /**
     * Creates the ContextualSearchPeekPromoControl, if needed.
     */
    private ContextualSearchPeekPromoControl getPeekPromoControl() {
        if (mPeekPromoControl == null) {
            mPeekPromoControl =
                    new ContextualSearchPeekPromoControl(this, mContext, mContainerView,
                            mResourceLoader);
        }
        return mPeekPromoControl;
    }

    /**
     * Destroys the ContextualSearchPeekPromoControl.
     */
    private void destroyPeekPromoControl() {
        if (mPeekPromoControl != null) {
            mPeekPromoControl.destroy();
            mPeekPromoControl = null;
        }
    }

    // ============================================================================================
    // Promo
    // ============================================================================================

    private ContextualSearchPromoControl mPromoControl;
    private ContextualSearchPromoHost mPromoHost;

    /**
     * @return Whether the Promo reached a state in which it could be interacted.
     */
    public boolean wasPromoInteractive() {
        return getPromoControl().wasInteractive();
    }

    /**
     * @return Height of the promo in pixels.
     */
    private float getPromoHeightPx() {
        return getPromoControl().getHeightPx();
    }

    /**
     * Creates the ContextualSearchPromoControl, if needed.
     */
    private ContextualSearchPromoControl getPromoControl() {
        if (mPromoControl == null) {
            mPromoControl =
                    new ContextualSearchPromoControl(this, getContextualSearchPromoHost(),
                            mContext, mContainerView, mResourceLoader);
        }
        return mPromoControl;
    }

    /**
     * Destroys the ContextualSearchPromoControl.
     */
    private void destroyPromoControl() {
        if (mPromoControl != null) {
            mPromoControl.destroy();
            mPromoControl = null;
        }
    }

    /**
     * @return An implementation of {@link ContextualSearchPromoHost}.
     */
    private ContextualSearchPromoHost getContextualSearchPromoHost() {
        if (mPromoHost == null) {
            mPromoHost = new ContextualSearchPromoHost() {
                @Override
                public void onPromoOptIn(boolean wasMandatory) {
                    if (wasMandatory) {
                        getOverlayPanelContent().showContent();
                        expandPanel(StateChangeReason.OPTIN);
                    }
                }

                @Override
                public void onPromoOptOut() {
                    closePanel(OverlayPanel.StateChangeReason.OPTOUT, true);
                }

                @Override
                public void onUpdatePromoAppearance() {
                    ContextualSearchPanel.this.updateBarShadow();
                }
            };
        }

        return mPromoHost;
    }

    // ============================================================================================
    // Panel Content
    // ============================================================================================

    /**
     * @return Whether the content can be displayed in the panel.
     */
    public boolean canDisplayContentInPanel() {
        // TODO(pedrosimonetti): add svelte support.
        return !getPromoControl().isMandatory();
    }

    @Override
    public void onTouchSearchContentViewAck() {
        mHasContentBeenTouched = true;
    }

    /**
     * Destroy the current content in the panel.
     * NOTE(mdjones): This should not be exposed. The only use is in ContextualSearchManager for a
     * bug related to loading new panel content.
     */
    public void destroyContent() {
        super.destroyOverlayPanelContent();
    }

    /**
     * @return Whether the panel content can be displayed in a new tab.
     */
    boolean canPromoteToNewTab() {
        return !mActivity.isCustomTab() && canDisplayContentInPanel();
    }

    // ============================================================================================
    // Testing Support
    // ============================================================================================

    /**
     * Simulates a tap on the panel's end button.
     */
    @VisibleForTesting
    public void simulateTapOnEndButton() {
        // Finish all currently running animations.
        onUpdateAnimation(System.currentTimeMillis(), true);

        // Determine the x-position for the simulated tap.
        float xPosition;
        if (LocalizationUtils.isLayoutRtl()) {
            xPosition = getContentX() + (mEndButtonWidthDp / 2);
        } else {
            xPosition = getContentX() + getWidth() - (mEndButtonWidthDp / 2);
        }

        // Determine the y-position for the simulated tap.
        float yPosition = getOffsetY() + (getHeight() / 2);

        // Simulate the tap.
        handleClick(System.currentTimeMillis(), xPosition, yPosition);
    }
}
