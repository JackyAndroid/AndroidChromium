// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener;

import org.chromium.base.ObserverList;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.bottombar.OverlayContentDelegate;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelContentViewDelegate;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchBlacklist.BlacklistReason;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchSelectionController.SelectionType;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler.OverrideUrlLoadingResult;
import org.chromium.chrome.browser.externalnav.ExternalNavigationParams;
import org.chromium.chrome.browser.gsa.GSAContextDisplaySelection;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarManager;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarObserver;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContextualSearchClient;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.TopControlsState;
import org.chromium.ui.base.WindowAndroid;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Manager for the Contextual Search feature.
 * This class keeps track of the status of Contextual Search and coordinates the control
 * with the layout.
 */
public class ContextualSearchManager implements ContextualSearchManagementDelegate,
        ContextualSearchTranslateInterface, ContextualSearchNetworkCommunicator,
        ContextualSearchSelectionHandler, ContextualSearchClient {

    private static final boolean ALWAYS_USE_RESOLVED_SEARCH_TERM = true;
    private static final boolean NEVER_USE_RESOLVED_SEARCH_TERM = false;

    private static final String INTENT_URL_PREFIX = "intent:";

    // The animation duration of a URL being promoted to a tab when triggered by an
    // intercept navigation. This is faster than the standard tab promotion animation
    // so that it completes before the navigation.
    private static final long INTERCEPT_NAVIGATION_PROMOTION_ANIMATION_DURATION_MS = 40;

    // We blacklist this URL because malformed URLs may bring up this page.
    private static final String BLACKLISTED_URL = "about:blank";

    private static final Pattern CONTAINS_WHITESPACE_PATTERN = Pattern.compile("\\s");

    private final ObserverList<ContextualSearchObserver> mObservers =
            new ObserverList<ContextualSearchObserver>();

    private final ChromeActivity mActivity;
    private final ContextualSearchTabPromotionDelegate mTabPromotionDelegate;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mOnFocusChangeListener;
    private final TabModelObserver mTabModelObserver;

    private ContextualSearchSelectionController mSelectionController;
    private ContextualSearchNetworkCommunicator mNetworkCommunicator;
    private ContextualSearchPolicy mPolicy;

    @VisibleForTesting
    protected ContextualSearchTranslateController mTranslateController;

    // The Overlay panel.
    private ContextualSearchPanel mSearchPanel;

    // The native manager associated with this object.
    private long mNativeContextualSearchManagerPtr;

    private ViewGroup mParentView;
    private TabRedirectHandler mTabRedirectHandler;
    private OverlayPanelContentViewDelegate mSearchContentViewDelegate;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private FindToolbarManager mFindToolbarManager;
    private FindToolbarObserver mFindToolbarObserver;

    private boolean mDidStartLoadingResolvedSearchRequest;
    private long mLoadedSearchUrlTimeMs;
    // TODO(donnd): consider changing this member's name to indicate "opened" instead of "seen".
    private boolean mWereSearchResultsSeen;
    private boolean mWereInfoBarsHidden;
    private boolean mDidPromoteSearchNavigation;
    private boolean mDidBasePageLoadJustStart;
    private boolean mWasActivatedByTap;
    private boolean mIsInitialized;

    /**
     * This boolean is used for loading content after a long-press when content is not immediately
     * loaded.
     */
    private boolean mShouldLoadDelayedSearch;

    private boolean mIsShowingPeekPromo;
    private boolean mWouldShowPeekPromo;
    private boolean mIsShowingPromo;
    private boolean mIsMandatoryPromo;
    private boolean mDidLogPromoOutcome;

    /**
     * Whether contextual search manager is currently promoting a tab. We should be ignoring hide
     * requests when mIsPromotingTab is set to true.
     */
    private boolean mIsPromotingToTab;

    // TODO(pedrosimonetti): also store selected text, surroundings, url, bounding rect of selected
    // text, and make sure that all states are cleared when starting a new contextual search to
    // avoid having the values in a funky state.
    private ContextualSearchRequest mSearchRequest;
    private ContextualSearchRequest mLastSearchRequestLoaded;

    /**
     * Whether the Accessibility Mode is enabled.
     */
    private boolean mIsAccessibilityModeEnabled;

    /**
     * Tap Experiments and other variable behavior.
     */
    private ContextualSearchHeuristics mHeuristics;
    private QuickAnswersHeuristic mQuickAnswersHeuristic;

    /**
     * The delegate that is responsible for promoting a {@link ContentViewCore} to a {@link Tab}
     * when necessary.
     */
    public interface ContextualSearchTabPromotionDelegate {
        /**
         * Called when {@code searchContentViewCore} should be promoted to a {@link Tab}.
         * @param searchUrl The Search URL to be promoted.
         */
        void createContextualSearchTab(String searchUrl);
    }

    /**
     * Constructs the manager for the given activity, and will attach views to the given parent.
     * @param activity             The {@code ChromeActivity} in use.
     * @param windowAndroid        The {@code WindowAndroid} associated with Chrome.
     * @param tabPromotionDelegate The {@link ContextualSearchTabPromotionDelegate} that is
     *                             responsible for building tabs from contextual search
     *                             {@link ContentViewCore}s.
     */
    public ContextualSearchManager(ChromeActivity activity, WindowAndroid windowAndroid,
            ContextualSearchTabPromotionDelegate tabPromotionDelegate) {
        mActivity = activity;
        mTabPromotionDelegate = tabPromotionDelegate;

        final View controlContainer = mActivity.findViewById(R.id.control_container);
        mOnFocusChangeListener = new OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (controlContainer != null && controlContainer.hasFocus()) {
                    hideContextualSearch(StateChangeReason.UNKNOWN);
                }
            }
        };

        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                if (!mIsPromotingToTab && tab.getId() != lastId
                        || mActivity.getTabModelSelector().isIncognitoSelected()) {
                    hideContextualSearch(StateChangeReason.UNKNOWN);
                    mSelectionController.onTabSelected();
                }
            }

            @Override
            public void didAddTab(Tab tab, TabLaunchType type) {
                // If we're in the process of promoting this tab, just return and don't mess with
                // this state.
                if (tab.getContentViewCore() == mSearchPanel.getContentViewCore()) return;
                hideContextualSearch(StateChangeReason.UNKNOWN);
            }
        };

        mSelectionController = new ContextualSearchSelectionController(activity, this);

        mNetworkCommunicator = this;

        mPolicy = new ContextualSearchPolicy(mActivity, mSelectionController, mNetworkCommunicator);

        mTranslateController = new ContextualSearchTranslateController(activity, mPolicy, this);
    }

    /**
     * Initializes this manager.
     * @param parentView The parent view to attach Contextual Search UX to.
     */
    public void initialize(ViewGroup parentView) {
        mNativeContextualSearchManagerPtr = nativeInit();

        mParentView = parentView;
        mParentView.getViewTreeObserver().addOnGlobalFocusChangeListener(mOnFocusChangeListener);

        mTabRedirectHandler = new TabRedirectHandler(mActivity);

        mIsShowingPromo = false;
        mDidLogPromoOutcome = false;
        mDidStartLoadingResolvedSearchRequest = false;
        mWereSearchResultsSeen = false;
        mIsInitialized = true;

        listenForTabModelSelectorNotifications();
    }

    /**
     * Sets the {@link FindToolbarManager} and attaches an observer that dismisses the Contextual
     * Search panel when the find toolbar is shown.
     *
     * @param findToolbarManager The {@link FindToolbarManager} for the current activity.
     */
    public void setFindToolbarManager(FindToolbarManager findToolbarManager) {
        if (mFindToolbarManager != null) {
            mFindToolbarManager.removeObserver(mFindToolbarObserver);
        }

        mFindToolbarManager = findToolbarManager;

        if (mFindToolbarObserver == null) {
            mFindToolbarObserver = new FindToolbarObserver() {
                @Override
                public void onFindToolbarShown() {
                    hideContextualSearch(StateChangeReason.UNKNOWN);
                }
            };
        }
        mFindToolbarManager.addObserver(mFindToolbarObserver);
    }

    /**
     * Destroys the native Contextual Search Manager.
     * Call this method before orphaning this object to allow it to be garbage collected.
     */
    public void destroy() {
        if (!mIsInitialized) return;

        hideContextualSearch(StateChangeReason.UNKNOWN);
        mParentView.getViewTreeObserver().removeOnGlobalFocusChangeListener(mOnFocusChangeListener);
        nativeDestroy(mNativeContextualSearchManagerPtr);
        stopListeningForHideNotifications();
        mTabRedirectHandler.clear();
        if (mFindToolbarManager != null) {
            mFindToolbarManager.removeObserver(mFindToolbarObserver);
            mFindToolbarManager = null;
            mFindToolbarObserver = null;
        }
    }

    @Override
    public void setContextualSearchPanel(ContextualSearchPanel panel) {
        mSearchPanel = panel;
        mPolicy.setContextualSearchPanel(panel);
    }

    @Override
    public ChromeActivity getChromeActivity() {
        return mActivity;
    }

    /**
     * @return Whether the Search Panel is opened. That is, whether it is EXPANDED or MAXIMIZED.
     */
    public boolean isSearchPanelOpened() {
        return mSearchPanel.isPanelOpened();
    }

    /**
     * @return The Base Page's {@link ContentViewCore}.
     */
    @Nullable private ContentViewCore getBaseContentView() {
        return mSelectionController.getBaseContentView();
    }

    /**
     * Notifies that the base page has started loading a page.
     */
    public void onBasePageLoadStarted() {
        mSelectionController.onBasePageLoadStarted();
    }

    /**
     * Notifies that a Context Menu has been shown.
     */
    void onContextMenuShown() {
        mSelectionController.onContextMenuShown();
    }

    /**
     * Hides the Contextual Search UX.
     * @param reason The {@link StateChangeReason} for hiding Contextual Search.
     */
    public void hideContextualSearch(StateChangeReason reason) {
        if (mSearchPanel == null) return;

        if (mSearchPanel.isShowing()) {
            mSearchPanel.closePanel(reason, false);
        } else {
            if (mSelectionController.getSelectionType() == SelectionType.TAP) {
                mSelectionController.clearSelection();
            }
        }
    }

    @Override
    public void onCloseContextualSearch(StateChangeReason reason) {
        if (mSearchPanel == null) return;

        mSelectionController.onSearchEnded(reason);

        // Show the infobar container if it was visible before Contextual Search was shown.
        if (mWereInfoBarsHidden) {
            mWereInfoBarsHidden = false;
            InfoBarContainer container = getInfoBarContainer();
            if (container != null) {
                container.setIsObscuredByOtherView(false);
            }
        }

        if (!mWereSearchResultsSeen && mLoadedSearchUrlTimeMs != 0L) {
            removeLastSearchVisit();
        }

        // Clear the timestamp. This is to avoid future calls to hideContextualSearch clearing
        // the current URL.
        mLoadedSearchUrlTimeMs = 0L;
        mWereSearchResultsSeen = false;

        mSearchRequest = null;

        if (mIsShowingPeekPromo || mWouldShowPeekPromo) {
            mPolicy.logPeekPromoMetrics(mIsShowingPeekPromo, mWouldShowPeekPromo);
        }

        if (mIsShowingPromo && !mDidLogPromoOutcome && mSearchPanel.wasPromoInteractive()) {
            ContextualSearchUma.logPromoOutcome(mWasActivatedByTap, mIsMandatoryPromo);
            mDidLogPromoOutcome = true;
        }

        mIsShowingPromo = false;
        mSearchPanel.setIsPromoActive(false, false);
        notifyHideContextualSearch();
    }

    /**
     * Called when the system back button is pressed. Will hide the layout.
     */
    public boolean onBackPressed() {
        if (!mIsInitialized || !mSearchPanel.isShowing()) return false;
        hideContextualSearch(StateChangeReason.BACK_PRESS);
        return true;
    }

    /**
     * Shows the Contextual Search UX.
     * Calls back into onGetContextualSearchQueryResponse.
     * @param stateChangeReason The reason explaining the change of state.
     */
    private void showContextualSearch(StateChangeReason stateChangeReason) {
        if (mFindToolbarManager != null) {
            mFindToolbarManager.hideToolbar(false);
        }

        // Dismiss the undo SnackBar if present by committing all tab closures.
        mActivity.getTabModelSelector().commitAllTabClosures();

        if (!mSearchPanel.isShowing()) {
            // If visible, hide the infobar container before showing the Contextual Search panel.
            InfoBarContainer container = getInfoBarContainer();
            if (container != null && container.getVisibility() == View.VISIBLE) {
                mWereInfoBarsHidden = true;
                container.setIsObscuredByOtherView(true);
            }
        }

        // If the user is jumping from one unseen search to another search, remove the last search
        // from history.
        PanelState state = mSearchPanel.getPanelState();
        if (!mWereSearchResultsSeen && mLoadedSearchUrlTimeMs != 0L
                && state != PanelState.UNDEFINED && state != PanelState.CLOSED) {
            removeLastSearchVisit();
        }

        // TODO(pedrosimonetti): Fix for M47. Replace this with a better delayed load approach.
        mSearchPanel.destroyContent();

        boolean isTap = mSelectionController.getSelectionType() == SelectionType.TAP;
        boolean didRequestSurroundings = false;

        if (isTap) {
            // If the user action was not a long-press, immediately start loading content.
            mShouldLoadDelayedSearch = false;
        }
        if (isTap && mPolicy.shouldPreviousTapResolve()) {
            mNetworkCommunicator.startSearchTermResolutionRequest(
                    mSelectionController.getSelectedText());
            didRequestSurroundings = true;
            // Cache the native translate data, so JNI calls won't be made when time-critical.
            mTranslateController.cacheNativeTranslateData();
        } else {
            boolean shouldPrefetch = mPolicy.shouldPrefetchSearchResult();
            mSearchRequest = createContextualSearchRequest(
                    mSelectionController.getSelectedText(), null, null, shouldPrefetch);
            mTranslateController.forceAutoDetectTranslateUnlessDisabled(mSearchRequest);
            mDidStartLoadingResolvedSearchRequest = false;
            mSearchPanel.setSearchTerm(mSelectionController.getSelectedText());
            if (shouldPrefetch) loadSearchUrl();

            // Record metrics for manual refinement of the search term from long-press.
            // TODO(donnd): remove this section once metrics have been analyzed.
            if (mSelectionController.getSelectionType() == SelectionType.LONG_PRESS
                    && mSearchPanel.isPeeking()) {
                boolean isSingleWord =
                        !CONTAINS_WHITESPACE_PATTERN
                                 .matcher(mSelectionController.getSelectedText().trim())
                                 .find();
                RecordUserAction.record(isSingleWord ? "ContextualSearch.ManualRefineSingleWord"
                                                     : "ContextualSearch.ManualRefineMultiWord");
            }
        }

        if (!didRequestSurroundings) {
            // Gather surrounding text for Icing integration, which will make the selection and
            // a shorter version of the surroundings available for Conversational Search.
            // Although the surroundings are extracted, they will not be sent to the server as
            // part of search term resolution, just sent to Icing which keeps them local until
            // the user activates a Voice Search.
            nativeGatherSurroundingText(mNativeContextualSearchManagerPtr,
                    mSelectionController.getSelectedText(), NEVER_USE_RESOLVED_SEARCH_TERM,
                    getBaseContentView().getWebContents(), mPolicy.maySendBasePageUrl());
        }

        mWereSearchResultsSeen = false;

        // Show the Peek Promo only when the Panel wasn't previously visible, provided
        // the policy allows it.
        if (!mSearchPanel.isShowing()) {
            mWouldShowPeekPromo = mPolicy.isPeekPromoConditionSatisfied();
            mIsShowingPeekPromo = mPolicy.isPeekPromoAvailable();
            if (mIsShowingPeekPromo) {
                mSearchPanel.showPeekPromo();
                mPolicy.registerPeekPromoSeen();
            }
        }

        // Note: whether the sprite should be animated or not needs to be set before the call to
        // peekPanel(). If the sprite should be animated, the animation will begin after the panel
        // finishes peeking. If it should not be animated, the icon will be drawn right away.
        mSearchPanel.getImageControl().setShouldAnimateIconSprite(
                mPolicy.shouldAnimateSearchProviderIcon());

        // Note: now that the contextual search has properly started, set the promo involvement.
        if (mPolicy.isPromoAvailable()) {
            mIsShowingPromo = true;
            mIsMandatoryPromo = mPolicy.isMandatoryPromoAvailable();
            mDidLogPromoOutcome = false;
            mSearchPanel.setIsPromoActive(true, mIsMandatoryPromo);
            mSearchPanel.setDidSearchInvolvePromo();
        }

        // TODO(donnd): although we are showing the bar here, we have not yet set the text!
        // Refactor to show the bar and set the text at the same time!
        // TODO(donnd): If there was a previously ongoing contextual search, we should ensure
        // it's registered as closed.
        mSearchPanel.requestPanelShow(stateChangeReason);

        assert mSelectionController.getSelectionType() != SelectionType.UNDETERMINED;
        mWasActivatedByTap = mSelectionController.getSelectionType() == SelectionType.TAP;
    }

    @Override
    public void startSearchTermResolutionRequest(String selection) {
        ContentViewCore baseContentView = getBaseContentView();
        if (baseContentView != null) {
            nativeStartSearchTermResolutionRequest(mNativeContextualSearchManagerPtr, selection,
                    ALWAYS_USE_RESOLVED_SEARCH_TERM, getBaseContentView().getWebContents(),
                    mPolicy.maySendBasePageUrl());
        }
    }

    @Override
    @Nullable public URL getBasePageUrl() {
        ContentViewCore baseContentViewCore = getBaseContentView();
        if (baseContentViewCore == null) return null;

        try {
            return new URL(baseContentViewCore.getWebContents().getUrl());
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * A method that can override the creation of a standard search request. This should only be
     * used for testing.
     * @param term The search term to create the request with.
     * @param altTerm An alternate search term.
     * @param isLowPriorityEnabled Whether the request can be made at low priority.
     */
    protected ContextualSearchRequest createContextualSearchRequest(
            String term, String altTerm, String mid, boolean isLowPriorityEnabled) {
        return new ContextualSearchRequest(term, altTerm, mid, isLowPriorityEnabled);
    }

    /**
     * Accessor for the {@code InfoBarContainer} currently attached to the {@code Tab}.
     */
    private InfoBarContainer getInfoBarContainer() {
        Tab tab = mActivity.getActivityTab();
        return tab == null ? null : tab.getInfoBarContainer();
    }

    /**
     * Listens for notifications that should hide the Contextual Search bar.
     */
    private void listenForTabModelSelectorNotifications() {
        TabModelSelector selector = mActivity.getTabModelSelector();

        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                hideContextualSearch(StateChangeReason.UNKNOWN);
                mDidBasePageLoadJustStart = true;
            }

            @Override
            public void onCrash(Tab tab, boolean sadTabShown) {
                if (sadTabShown) {
                    // Hide contextual search if the foreground tab crashed
                    hideContextualSearch(StateChangeReason.UNKNOWN);
                }
            }

            @Override
            public void onClosingStateChanged(Tab tab, boolean closing) {
                if (closing) hideContextualSearch(StateChangeReason.UNKNOWN);
            }
        };

        for (TabModel tabModel : selector.getModels()) {
            tabModel.addObserver(mTabModelObserver);
        }
    }

    /**
     * Stops listening for notifications that should hide the Contextual Search bar.
     */
    private void stopListeningForHideNotifications() {
        if (mTabModelSelectorTabObserver != null) mTabModelSelectorTabObserver.destroy();

        TabModelSelector selector = mActivity.getTabModelSelector();
        if (selector != null) {
            for (TabModel tabModel : selector.getModels()) {
                tabModel.removeObserver(mTabModelObserver);
            }
        }
    }

    /**
     * Clears our private member referencing the native manager.
     */
    @CalledByNative
    public void clearNativeManager() {
        assert mNativeContextualSearchManagerPtr != 0;
        mNativeContextualSearchManagerPtr = 0;
    }

    /**
     * Sets our private member referencing the native manager.
     * @param nativeManager The pointer to the native Contextual Search manager.
     */
    @CalledByNative
    public void setNativeManager(long nativeManager) {
        assert mNativeContextualSearchManagerPtr == 0;
        mNativeContextualSearchManagerPtr = nativeManager;
    }

    /**
     * Called when surrounding text is available.
     * @param afterText to be shown after the selected word.
     */
    @CalledByNative
    private void onSurroundingTextAvailable(final String afterText) {
        // TODO(donnd): check if panel has been requested to show.
        // We used to call mSearchPanel.isShowing() here, but that's unreliable (crbug.com/669600).
        mSearchPanel.setSearchContext(
                mSelectionController.getSelectedText(), afterText);
    }

    /**
     * Called by native code when a selection is available to share with Icing (for Conversational
     * Search).
     */
    @CalledByNative
    private void onIcingSelectionAvailable(
            final String encoding, final String surroundingText, int startOffset, int endOffset) {
        GSAContextDisplaySelection selection =
                new GSAContextDisplaySelection(encoding, surroundingText, startOffset, endOffset);
        mSearchPanel.setWasSelectionPartOfUrl(
                ContextualSearchSelectionController.isSelectionPartOfUrl(
                        surroundingText, startOffset, endOffset));
        notifyShowContextualSearch(selection);
    }

    /**
     * Called in response to the
     * {@link ContextualSearchManager#nativeStartSearchTermResolutionRequest} method.
     * @param isNetworkUnavailable Indicates if the network is unavailable, in which case all other
     *        parameters should be ignored.
     * @param responseCode The HTTP response code.  If the code is not OK, the query
     *        should be ignored.
     * @param searchTerm The term to use in our subsequent search.
     * @param displayText The text to display in our UX.
     * @param alternateTerm The alternate term to display on the results page.
     * @param mid the MID for an entity to use to trigger a Knowledge Panel, or an empty string.
     *            A MID is a unique identifier for an entity in the Search Knowledge Graph.
     * @param selectionStartAdjust A positive number of characters that the start of the existing
     *        selection should be expanded by.
     * @param selectionEndAdjust A positive number of characters that the end of the existing
     *        selection should be expanded by.
     * @param contextLanguage The language of the original search term, or an empty string.
     * @param thumbnailUrl The URL of the thumbnail to display in our UX.
     * @param caption The caption to display.
     */
    @CalledByNative
    public void onSearchTermResolutionResponse(boolean isNetworkUnavailable, int responseCode,
            final String searchTerm, final String displayText, final String alternateTerm,
            final String mid, boolean doPreventPreload, int selectionStartAdjust,
            int selectionEndAdjust, final String contextLanguage, final String thumbnailUrl,
            final String caption) {
        mNetworkCommunicator.handleSearchTermResolutionResponse(isNetworkUnavailable, responseCode,
                searchTerm, displayText, alternateTerm, mid, doPreventPreload, selectionStartAdjust,
                selectionEndAdjust, contextLanguage, thumbnailUrl, caption);
    }

    @Override
    public void handleSearchTermResolutionResponse(boolean isNetworkUnavailable, int responseCode,
            String searchTerm, String displayText, String alternateTerm, String mid,
            boolean doPreventPreload, int selectionStartAdjust, int selectionEndAdjust,
            String contextLanguage, String thumbnailUrl, String caption) {
        // Show an appropriate message for what to search for.
        String message;
        boolean doLiteralSearch = false;
        if (isNetworkUnavailable) {
            // TODO(donnd): double-check that the network is really unavailable?
            message = mActivity.getResources().getString(
                    R.string.contextual_search_network_unavailable);
        } else if (!isHttpFailureCode(responseCode) && !TextUtils.isEmpty(displayText)) {
            message = displayText;
        } else if (!mPolicy.shouldShowErrorCodeInBar()) {
            message = mSelectionController.getSelectedText();
            doLiteralSearch = true;
        } else {
            message = mActivity.getResources().getString(
                    R.string.contextual_search_error, responseCode);
            doLiteralSearch = true;
        }

        boolean receivedContextualCardsData = !TextUtils.isEmpty(caption)
                || !TextUtils.isEmpty(thumbnailUrl);
        if (ContextualSearchFieldTrial.shouldHideContextualCardsData()) {
            // Clear the thumbnail URL and caption so that they are not displayed in the bar. This
            // is used to determine the CTR on contextual searches where we would have shown
            // contextual cards data had it not been disabled via a field trial.
            thumbnailUrl = "";
            caption = "";
        }

        mSearchPanel.onSearchTermResolved(message, thumbnailUrl);

        if (!TextUtils.isEmpty(caption)) {
            // Call #onSetCaption() to set the caption. For entities, the caption should not be
            // regarded as an answer. In the future, when quick actions are added, doesAnswer will
            // need to be determined rather than always set to false.
            boolean doesAnswer = false;
            onSetCaption(caption, doesAnswer);
        }

        if (ContextualSearchFieldTrial.isContextualCardsBarIntegrationEnabled()) {
            ContextualSearchUma.logContextualCardsDataShown(receivedContextualCardsData);
            mSearchPanel.getPanelMetrics().setWasContextualCardsDataShown(
                    receivedContextualCardsData);
        }

        // If there was an error, fall back onto a literal search for the selection.
        // Since we're showing the panel, there must be a selection.
        if (doLiteralSearch) {
            searchTerm = mSelectionController.getSelectedText();
            alternateTerm = null;
            doPreventPreload = true;
        }
        if (!TextUtils.isEmpty(searchTerm)) {
            // TODO(donnd): Instead of preloading, we should prefetch (ie the URL should not
            // appear in the user's history until the user views it).  See crbug.com/406446.
            boolean shouldPreload = !doPreventPreload && mPolicy.shouldPrefetchSearchResult();
            mSearchRequest =
                    createContextualSearchRequest(searchTerm, alternateTerm, mid, shouldPreload);
            // Trigger translation, if enabled.
            mTranslateController.forceTranslateIfNeeded(mSearchRequest, contextLanguage);
            mDidStartLoadingResolvedSearchRequest = false;
            if (mSearchPanel.isContentShowing()) {
                mSearchRequest.setNormalPriority();
            }
            if (mSearchPanel.isContentShowing() || shouldPreload) {
                loadSearchUrl();
            }
            mPolicy.logSearchTermResolutionDetails(searchTerm);
        }

        if (selectionStartAdjust != 0 || selectionEndAdjust != 0) {
            mSelectionController.adjustSelection(selectionStartAdjust, selectionEndAdjust);
        }
    }

    /**
     * Loads a Search Request in the Contextual Search's Content View.
     */
    private void loadSearchUrl() {
        mLoadedSearchUrlTimeMs = System.currentTimeMillis();
        mLastSearchRequestLoaded = mSearchRequest;
        mSearchPanel.loadUrlInPanel(mSearchRequest.getSearchUrl());
        mDidStartLoadingResolvedSearchRequest = true;

        // TODO(pedrosimonetti): If the user taps on a word and quickly after that taps on the
        // peeking Search Bar, the Search Content View will not be displayed. It seems that
        // calling ContentViewCore.onShow() while it's being created has no effect. Need
        // to coordinate with Chrome-Android folks to come up with a proper fix for this.
        // For now, we force the ContentView to be displayed by calling onShow() again
        // when a URL is being loaded. See: crbug.com/398206
        if (mSearchPanel.isContentShowing()
                && mSearchPanel.getContentViewCore() != null) {
            mSearchPanel.getContentViewCore().onShow();
        }
    }

    /**
     * @return Whether a Tap gesture is currently supported.
     */
    private boolean isTapSupported() {
        // Base page just started navigating away, so taps should be ignored.
        if (mDidBasePageLoadJustStart) return false;

        return mPolicy.isTapSupported();
    }

    /**
     * Called to set a caption. The caption may either be included with the search term resolution
     * response or set by the page through the CS JavaScript API used to notify CS that there is
     * a caption available on the current overlay.
     * @param caption The caption to display.
     * @param doesAnswer Whether the caption should be regarded as an answer such
     *        that the user may not need to open the panel, or whether the caption
     *        is simply informative or descriptive of the answer in the full results.
     */
    @CalledByNative
    private void onSetCaption(String caption, boolean doesAnswer) {
        if (TextUtils.isEmpty(caption)) return;

        // Notify the UI of the caption.
        mSearchPanel.setCaption(caption);
        if (mQuickAnswersHeuristic != null) {
            mQuickAnswersHeuristic.setConditionSatisfied(true);
            mQuickAnswersHeuristic.setDoesAnswer(doesAnswer);
        }

        // Update Tap counters to account for a possible answer.
        mPolicy.updateCountersForQuickAnswer(mWasActivatedByTap, doesAnswer);
    }

    /**
     * Notifies that the Accessibility Mode state has changed.
     *
     * @param enabled Whether the Accessibility Mode is enabled.
     */
    public void onAccessibilityModeChanged(boolean enabled) {
        mIsAccessibilityModeEnabled = enabled;
    }

    /**
     * Notifies that the preference state has changed.
     * @param isEnabled Whether the feature is enabled.
     */
    public void onContextualSearchPrefChanged(boolean isEnabled) {
        mSearchPanel.onContextualSearchPrefChanged(isEnabled);
    }

    // ============================================================================================
    // Observers
    // ============================================================================================

    /**
     * @param observer An observer to notify when the user performs a contextual search.
     */
    public void addObserver(ContextualSearchObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * @param observer An observer to no longer notify when the user performs a contextual search.
     */
    public void removeObserver(ContextualSearchObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Notifies all Contextual Search observers that a search has occurred.
     * @param selectionContext The selection and context that triggered the search.
     */
    private void notifyShowContextualSearch(GSAContextDisplaySelection selectionContext) {
        if (!mPolicy.canSendSurroundings()) selectionContext = null;

        for (ContextualSearchObserver observer : mObservers) {
            observer.onShowContextualSearch(selectionContext);
        }
    }

    /**
     * Notifies all Contextual Search observers that a search ended and is no longer in effect.
     */
    private void notifyHideContextualSearch() {
        for (ContextualSearchObserver observer : mObservers) {
            observer.onHideContextualSearch();
        }
    }

    // ============================================================================================
    // ContextualSearchTranslateInterface
    // ============================================================================================

    @Override
    public String getAcceptLanguages() {
        return nativeGetAcceptLanguages(mNativeContextualSearchManagerPtr);
    }

    @Override
    public String getTranslateServiceTargetLanguage() {
        // TODO(donnd): remove once issue 607127 has been resolved.
        if (mNativeContextualSearchManagerPtr == 0)
            throw new RuntimeException("mNativeContextualSearchManagerPtr is 0!");
        return nativeGetTargetLanguage(mNativeContextualSearchManagerPtr);
    }

    // ============================================================================================
    // OverlayContentDelegate
    // ============================================================================================

    @Override
    public OverlayContentDelegate getOverlayContentDelegate() {
        return new SearchOverlayContentDelegate();
    }

    /**
     * Implementation of OverlayContentDelegate. Made public for testing purposes.
     */
    public class SearchOverlayContentDelegate extends OverlayContentDelegate {

        public SearchOverlayContentDelegate() {}

        @Override
        public void onMainFrameLoadStarted(String url, boolean isExternalUrl) {
            mSearchPanel.updateTopControlsState();

            if (isExternalUrl) {
                onExternalNavigation(url);
            }
        }

        @Override
        public void onMainFrameNavigation(String url, boolean isExternalUrl, boolean isFailure) {
            if (isExternalUrl) {
                if (ContextualSearchFieldTrial.isAmpAsSeparateTabEnabled() && mPolicy.isAmpUrl(url)
                        && mSearchPanel.didTouchContent()) {
                    onExternalNavigation(url);
                }
            } else {
                // Could be just prefetching, check if that failed.
                onContextualSearchRequestNavigation(isFailure);

                // Record metrics for when the prefetched results became viewable.
                if (mSearchRequest != null && mSearchRequest.wasPrefetch()) {
                    boolean didResolve = mPolicy.shouldPreviousTapResolve();
                    mSearchPanel.onPanelNavigatedToPrefetchedSearch(didResolve);
                }
            }
        }

        @Override
        public void onContentLoadStarted(String url) {
            mDidPromoteSearchNavigation = false;
        }

        @Override
        public void onContentLoadFinished() {
            if (mSearchRequest == null) return;

            mSearchPanel.onSearchResultsLoaded(mSearchRequest.wasPrefetch());

            // Any time we place a page in a ContentViewCore, clear history if needed.
            // This prevents error URLs from appearing in the Tab's history stack.
            // Also please note that clearHistory() will not
            // clear the current entry (search results page in this case),
            // and it will not work properly if there are pending navigations.
            // That's why we need to clear the history here, after the navigation
            // is completed.
            boolean shouldClearHistory = mSearchRequest.getHasFailed();
            if (shouldClearHistory && mSearchPanel.getContentViewCore() != null) {
                mSearchPanel.getContentViewCore().getWebContents().getNavigationController()
                        .clearHistory();
            }
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            if (isVisible) {
                mWereSearchResultsSeen = true;
                // If there's no current request, then either a search term resolution
                // is in progress or we should do a verbatim search now.
                if (mSearchRequest == null && mPolicy.shouldCreateVerbatimRequest()) {
                    mSearchRequest = createContextualSearchRequest(
                            mSelectionController.getSelectedText(), null, null, false);
                    mDidStartLoadingResolvedSearchRequest = false;
                }
                if (mSearchRequest != null && (!mDidStartLoadingResolvedSearchRequest
                        || mShouldLoadDelayedSearch)) {
                    // mShouldLoadDelayedSearch is used in the long-press case to load content.
                    // Since content is now created and destroyed for each request, was impossible
                    // to know if content was already loaded or recently needed to be; this is for
                    // the case where it needed to be.
                    mSearchRequest.setNormalPriority();
                    loadSearchUrl();
                }
                mShouldLoadDelayedSearch = true;
                mPolicy.updateCountersForOpen();
            }
        }

        @Override
        public void onContentViewCreated(ContentViewCore contentViewCore) {
            // TODO(donnd): Consider moving to OverlayPanelContent.
            if (mPolicy.isContextualSearchJsApiEnabled()) {
                // Enable the Contextual Search JavaScript API between our service and the new view.
                nativeEnableContextualSearchJsApiForOverlay(
                        mNativeContextualSearchManagerPtr, contentViewCore.getWebContents());
            }

            // TODO(mdjones): Move SearchContentViewDelegate ownership to panel.
            mSearchContentViewDelegate.setOverlayPanelContentViewCore(contentViewCore);
        }

        @Override
        public void onContentViewDestroyed() {
            if (mSearchContentViewDelegate != null) {
                mSearchContentViewDelegate.releaseOverlayPanelContentViewCore();
            }
        }

        @Override
        public void onContentViewSeen() {
            mSearchPanel.setWasSearchContentViewSeen();
        }

        @Override
        public boolean shouldInterceptNavigation(ExternalNavigationHandler externalNavHandler,
                NavigationParams navigationParams) {
            mTabRedirectHandler.updateNewUrlLoading(navigationParams.pageTransitionType,
                    navigationParams.isRedirect,
                    navigationParams.hasUserGesture || navigationParams.hasUserGestureCarryover,
                    mActivity.getLastUserInteractionTime(), TabRedirectHandler.INVALID_ENTRY_INDEX);
            ExternalNavigationParams params = new ExternalNavigationParams.Builder(
                    navigationParams.url, false, navigationParams.referrer,
                    navigationParams.pageTransitionType, navigationParams.isRedirect)
                    .setApplicationMustBeInForeground(true)
                    .setRedirectHandler(mTabRedirectHandler)
                    .setIsMainFrame(navigationParams.isMainFrame)
                    .build();
            if (externalNavHandler.shouldOverrideUrlLoading(params)
                    != OverrideUrlLoadingResult.NO_OVERRIDE) {
                mSearchPanel.maximizePanelThenPromoteToTab(StateChangeReason.TAB_PROMOTION,
                        INTERCEPT_NAVIGATION_PROMOTION_ANIMATION_DURATION_MS);
                return false;
            }
            if (navigationParams.isExternalProtocol) {
                return false;
            }
            return true;
        }
    }

    // ============================================================================================
    // Search Content View
    // ============================================================================================

    /**
     * Sets the {@code OverlayPanelContentViewDelegate} associated with the Content View.
     * @param delegate
     */
    public void setSearchContentViewDelegate(OverlayPanelContentViewDelegate delegate) {
        mSearchContentViewDelegate = delegate;
    }

    /**
     * Removes the last resolved search URL from the Chrome history.
     */
    private void removeLastSearchVisit() {
        if (mLastSearchRequestLoaded != null) {
            // TODO(pedrosimonetti): Consider having this feature builtin into OverlayPanelContent.
            mSearchPanel.removeLastHistoryEntry(mLastSearchRequestLoaded.getSearchUrl(),
                    mLoadedSearchUrlTimeMs);
        }
    }

    /**
     * Called when the Search content view navigates to a contextual search request URL.
     * This navigation could be for a prefetch when the panel is still closed, or
     * a load of a user-visible search result.
     * @param isFailure Whether the navigation failed.
     */
    @Override
    public void onContextualSearchRequestNavigation(boolean isFailure) {
        if (mSearchRequest == null) return;

        if (mSearchRequest.isUsingLowPriority()) {
            ContextualSearchUma.logLowPrioritySearchRequestOutcome(isFailure);
        } else {
            ContextualSearchUma.logNormalPrioritySearchRequestOutcome(isFailure);
            if (mSearchRequest.getHasFailed()) {
                ContextualSearchUma.logFallbackSearchRequestOutcome(isFailure);
            }
        }

        if (isFailure && mSearchRequest.isUsingLowPriority()) {
            // We're navigating to an error page, so we want to stop and retry.
            // Stop loading the page that displays the error to the user.
            if (mSearchPanel.getContentViewCore() != null) {
                // When running tests the Content View might not exist.
                mSearchPanel.getContentViewCore().getWebContents().stop();
            }
            mSearchRequest.setHasFailed();
            mSearchRequest.setNormalPriority();
            // If the content view is showing, load at normal priority now.
            if (mSearchPanel.isContentShowing()) {
                loadSearchUrl();
            } else {
                mDidStartLoadingResolvedSearchRequest = false;
            }
        }
    }

    @Override
    public void logCurrentState() {
        if (ContextualSearchFieldTrial.isEnabled()) {
            mPolicy.logCurrentState();
        }
    }

    /**
     * @return Whether the given HTTP result code represents a failure or not.
     */
    private boolean isHttpFailureCode(int httpResultCode) {
        return httpResultCode <= 0 || httpResultCode >= 400;
    }

    /**
     * @return whether a navigation in the search content view should promote to a separate tab.
     */
    private boolean shouldPromoteSearchNavigation() {
        // A navigation can be due to us loading a URL, or a touch in the search content view.
        // Require a touch, but no recent loading, in order to promote to a separate tab.
        // Note that tapping the opt-in button requires checking for recent loading.
        return mSearchPanel.didTouchContent()
                && !mSearchPanel.isProcessingPendingNavigation();
    }

    /**
     * Called to check if an external navigation is being done and take the appropriate action:
     * Auto-promotes the panel into a separate tab if that's not already being done.
     * @param url The URL we are navigating to.
     */
    public void onExternalNavigation(String url) {
        if (!mDidPromoteSearchNavigation
                && !BLACKLISTED_URL.equals(url)
                && !url.startsWith(INTENT_URL_PREFIX)
                && shouldPromoteSearchNavigation()) {
            // Do not promote to a regular tab if we're loading our Resolved Search
            // URL, otherwise we'll promote it when prefetching the Serp.
            // Don't promote URLs when they are navigating to an intent - this is
            // handled by the InterceptNavigationDelegate which uses a faster
            // maximizing animation.
            mDidPromoteSearchNavigation = true;
            mSearchPanel.maximizePanelThenPromoteToTab(StateChangeReason.SERP_NAVIGATION);
        }
    }

    @Override
    public void openResolvedSearchUrlInNewTab() {
        if (mSearchRequest != null && mSearchRequest.getSearchUrlForPromotion() != null) {
            TabModelSelector tabModelSelector = mActivity.getTabModelSelector();
            tabModelSelector.openNewTab(
                    new LoadUrlParams(mSearchRequest.getSearchUrlForPromotion()),
                    TabLaunchType.FROM_LINK,
                    tabModelSelector.getCurrentTab(),
                    tabModelSelector.isIncognitoSelected());
        }
    }

    @Override
    public boolean isRunningInCompatibilityMode() {
        return SysUtils.isLowEndDevice();
    }

    @Override
    public void promoteToTab() {
        // TODO(pedrosimonetti): Consider removing this member.
        mIsPromotingToTab = true;

        // If the request object is null that means that a Contextual Search has just started
        // and the Search Term Resolution response hasn't arrived yet. In this case, promoting
        // the Panel to a Tab will result in creating a new tab with URL about:blank. To prevent
        // this problem, we are ignoring tap gestures in the Search Bar if we don't know what
        // to search for.
        if (mSearchRequest != null
                && mSearchPanel.getContentViewCore() != null
                && mSearchPanel.getContentViewCore().getWebContents() != null) {
            String url = getContentViewUrl(mSearchPanel.getContentViewCore());

            // If it's a search URL, format it so the SearchBox becomes visible.
            if (mSearchRequest.isContextualSearchUrl(url)) {
                url = mSearchRequest.getSearchUrlForPromotion();
            }

            if (url != null) {
                mTabPromotionDelegate.createContextualSearchTab(url);
                mSearchPanel.closePanel(StateChangeReason.TAB_PROMOTION, false);
            }
        }
        mIsPromotingToTab = false;
    }

    /**
     * Gets the current loaded URL in a ContentViewCore.
     *
     * @param searchContentViewCore The given ContentViewCore.
     * @return The current loaded URL.
     */
    private String getContentViewUrl(ContentViewCore searchContentViewCore) {
        // First, check the pending navigation entry, because there might be an navigation
        // not yet committed being processed. Otherwise, get the URL from the WebContents.
        NavigationEntry entry =
                searchContentViewCore.getWebContents().getNavigationController().getPendingEntry();
        String url = entry != null
                ? entry.getUrl() : searchContentViewCore.getWebContents().getUrl();
        return url;
    }

    @Override
    public void dismissContextualSearchBar() {
        hideContextualSearch(StateChangeReason.UNKNOWN);
    }

    // ============================================================================================
    // ContextualSearchClient -- interface used by ContentViewCore.
    // ============================================================================================

    @Override
    public void onSelectionChanged(String selection) {
        if (!isOverlayVideoMode()) {
            mSelectionController.handleSelectionChanged(selection);
            mSearchPanel.updateTopControlsState(TopControlsState.BOTH, true);
        }
    }

    @Override
    public void onSelectionEvent(int eventType, float posXPix, float posYPix) {
        if (!isOverlayVideoMode()) {
            mSelectionController.handleSelectionEvent(eventType, posXPix, posYPix);
        }
    }

    @Override
    public void showUnhandledTapUIIfNeeded(final int x, final int y) {
        mDidBasePageLoadJustStart = false;
        if (!isOverlayVideoMode()) {
            mSelectionController.handleShowUnhandledTapUIIfNeeded(x, y);
        }
    }

    private boolean isOverlayVideoMode() {
        return mActivity.getFullscreenManager() != null
                && mActivity.getFullscreenManager().isOverlayVideoMode();
    }

    // ============================================================================================
    // Selection
    // ============================================================================================

    /**
     * Returns a new {@code GestureStateListener} that will listen for events in the Base Page.
     * This listener will handle all Contextual Search-related interactions that go through the
     * listener.
     */
    public GestureStateListener getGestureStateListener() {
        return mSelectionController.getGestureStateListener();
    }

    @Override
    public void handleScroll() {
        if (mIsAccessibilityModeEnabled) return;

        hideContextualSearch(StateChangeReason.BASE_PAGE_SCROLL);
    }

    @Override
    public void handleInvalidTap() {
        if (mIsAccessibilityModeEnabled) return;

        hideContextualSearch(StateChangeReason.BASE_PAGE_TAP);
    }

    @Override
    public void handleSuppressedTap() {
        if (mIsAccessibilityModeEnabled) return;

        hideContextualSearch(StateChangeReason.BASE_PAGE_TAP);
    }

    @Override
    public void handleMetricsForWouldSuppressTap(ContextualSearchHeuristics tapHeuristics) {
        mHeuristics = tapHeuristics;
        if (ContextualSearchFieldTrial.isQuickAnswersEnabled()) {
            // TODO(donnd): QuickAnswersHeuristic is getting added to TapSuppressionHeuristics and
            // and getting considered in TapSuppressionHeuristics#shouldSuppressTap(). It should
            // be a part of ContextualSearchHeuristics for logging purposes but not for suppression.
            mQuickAnswersHeuristic = new QuickAnswersHeuristic();
            mHeuristics.add(mQuickAnswersHeuristic);
        }
        mSearchPanel.getPanelMetrics().setResultsSeenExperiments(mHeuristics);
    }

    @Override
    public void handleValidTap() {
        if (mIsAccessibilityModeEnabled) return;

        if (isTapSupported()) {
            // Here we are starting a new Contextual Search with a Tap gesture, therefore
            // we need to clear to properly reflect that a search just started and we don't
            // have the resolved search term yet.
            mSearchRequest = null;

            // Let the policy know that a tap gesture has been received.
            mPolicy.registerTap();

            ContentViewCore baseContentView = getBaseContentView();
            if (baseContentView != null) baseContentView.getWebContents().selectWordAroundCaret();
        }
    }

    @Override
    public void handleSelection(String selection, boolean selectionValid, SelectionType type,
            float x, float y) {
        if (mIsAccessibilityModeEnabled) return;

        if (!selection.isEmpty()) {
            StateChangeReason stateChangeReason = type == SelectionType.TAP
                    ? StateChangeReason.TEXT_SELECT_TAP : StateChangeReason.TEXT_SELECT_LONG_PRESS;
            ContextualSearchUma.logSelectionIsValid(selectionValid);
            if (selectionValid) {
                mSearchPanel.updateBasePageSelectionYPx(y);
                mSearchPanel.getPanelMetrics().onSelectionEstablished(selection);
                showContextualSearch(stateChangeReason);
            } else {
                hideContextualSearch(stateChangeReason);
            }
        }
    }

    @Override
    public void handleSelectionDismissal() {
        if (mIsAccessibilityModeEnabled) return;

        if (mSearchPanel.isShowing()
                && !mIsPromotingToTab
                // If the selection is dismissed when the Panel is not peeking anymore,
                // which means the Panel is at least partially expanded, then it means
                // the selection was cleared by an external source (like JavaScript),
                // so we should not dismiss the UI in here.
                // See crbug.com/516665
                && mSearchPanel.isPeeking()) {
            hideContextualSearch(StateChangeReason.CLEARED_SELECTION);
        }
    }

    @Override
    public void handleSelectionModification(
            String selection, boolean selectionValid, float x, float y) {
        if (mIsAccessibilityModeEnabled) return;

        if (mSearchPanel.isShowing()) {
            if (selectionValid) {
                mSearchPanel.setSearchTerm(selection);
            } else {
                hideContextualSearch(StateChangeReason.BASE_PAGE_TAP);
            }
        }
    }

    @Override
    public void handleSelectionSuppression(BlacklistReason reason) {
        if (mIsAccessibilityModeEnabled) return;

        mSearchPanel.getPanelMetrics().setBlacklistReason(reason);
    }

    // ============================================================================================
    // Test helpers
    // ============================================================================================

    /**
     * Sets the {@link ContextualSearchNetworkCommunicator} to use for server requests.
     * @param networkCommunicator The communicator for all future requests.
     */
    @VisibleForTesting
    void setNetworkCommunicator(ContextualSearchNetworkCommunicator networkCommunicator) {
        mNetworkCommunicator = networkCommunicator;
        mPolicy.setNetworkCommunicator(mNetworkCommunicator);
    }

    /**
     * @return The ContextualSearchPolicy currently being used.
     */
    @VisibleForTesting
    ContextualSearchPolicy getContextualSearchPolicy() {
        return mPolicy;
    }

    /**
     * @param policy The {@link ContextualSearchPolicy} for testing.
     */
    @VisibleForTesting
    void setContextualSearchPolicy(ContextualSearchPolicy policy) {
        mPolicy = policy;
    }

    /**
     * @return The {@link ContextualSearchPanel}, for testing purposes only.
     */
    @VisibleForTesting
    ContextualSearchPanel getContextualSearchPanel() {
        return mSearchPanel;
    }

    /**
     * @return The selection controller, for testing purposes.
     */
    @VisibleForTesting
    ContextualSearchSelectionController getSelectionController() {
        return mSelectionController;
    }

    /**
     * @param controller The {@link ContextualSearchSelectionController}, for testing purposes.
     */
    @VisibleForTesting
    void setSelectionController(ContextualSearchSelectionController controller) {
        mSelectionController = controller;
    }

    /**
     * @return The current search request, or {@code null} if there is none, for testing.
     */
    @VisibleForTesting
    ContextualSearchRequest getRequest() {
        return mSearchRequest;
    }

    // ============================================================================================
    // Native calls
    // ============================================================================================

    private native long nativeInit();
    private native void nativeDestroy(long nativeContextualSearchManager);
    private native void nativeStartSearchTermResolutionRequest(long nativeContextualSearchManager,
            String selection, boolean useResolvedSearchTerm, WebContents baseWebContents,
            boolean maySendBasePageUrl);
    protected native void nativeGatherSurroundingText(long nativeContextualSearchManager,
            String selection, boolean useResolvedSearchTerm, WebContents baseWebContents,
            boolean maySendBasePageUrl);
    private native void nativeEnableContextualSearchJsApiForOverlay(
            long nativeContextualSearchManager, WebContents overlayWebContents);
    // Don't call these directly, instead call the private methods that cache the results.
    private native String nativeGetTargetLanguage(long nativeContextualSearchManager);
    private native String nativeGetAcceptLanguages(long nativeContextualSearchManager);
}
