// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.datausage.DataUseTabUIManager;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler.OverrideUrlLoadingResult;
import org.chromium.chrome.browser.externalnav.ExternalNavigationParams;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.ConsoleMessageLevel;

/**
 * Class that controls navigations and allows to intercept them. It is used on Android to 'convert'
 * certain navigations to Intents to 3rd party applications and to "pause" navigations when data use
 * tracking has ended.
 */
public class InterceptNavigationDelegateImpl implements InterceptNavigationDelegate {
    private final Tab mTab;
    private final ExternalNavigationHandler mExternalNavHandler;
    private final AuthenticatorNavigationInterceptor mAuthenticatorHelper;
    private ExternalNavigationHandler.OverrideUrlLoadingResult mLastOverrideUrlLoadingResult =
            ExternalNavigationHandler.OverrideUrlLoadingResult.NO_OVERRIDE;

    /**
     * Whether forward history should be cleared after navigation is committed.
     */
    private boolean mClearAllForwardHistoryRequired;
    private boolean mShouldClearRedirectHistoryForTabClobbering;

    /**
     * Default constructor of {@link InterceptNavigationDelegateImpl}.
     */
    public InterceptNavigationDelegateImpl(Tab tab) {
        this(new ExternalNavigationHandler(tab), tab);
    }

    /**
     * Constructs a new instance of {@link InterceptNavigationDelegateImpl} with the given
     * {@link ExternalNavigationHandler}.
     */
    public InterceptNavigationDelegateImpl(ExternalNavigationHandler externalNavHandler, Tab tab) {
        mTab = tab;
        mExternalNavHandler = externalNavHandler;
        mAuthenticatorHelper = ((ChromeApplication) mTab.getApplicationContext())
                .createAuthenticatorNavigationInterceptor(mTab);
    }

    public boolean shouldIgnoreNewTab(String url, boolean incognito) {
        if (mAuthenticatorHelper != null && mAuthenticatorHelper.handleAuthenticatorUrl(url)) {
            return true;
        }

        ExternalNavigationParams params = new ExternalNavigationParams.Builder(url, incognito)
                .setTab(mTab)
                .setOpenInNewTab(true)
                .build();
        return mExternalNavHandler.shouldOverrideUrlLoading(params)
                != ExternalNavigationHandler.OverrideUrlLoadingResult.NO_OVERRIDE;
    }

    @VisibleForTesting
    public OverrideUrlLoadingResult getLastOverrideUrlLoadingResultForTests() {
        return mLastOverrideUrlLoadingResult;
    }

    @Override
    public boolean shouldIgnoreNavigation(NavigationParams navigationParams) {
        String url = navigationParams.url;

        if (mAuthenticatorHelper != null && mAuthenticatorHelper.handleAuthenticatorUrl(url)) {
            return true;
        }

        TabRedirectHandler tabRedirectHandler = null;
        if (navigationParams.isMainFrame) {
            tabRedirectHandler = mTab.getTabRedirectHandler();
        } else if (navigationParams.isExternalProtocol) {
            // Only external protocol navigations are intercepted for iframe navigations.  Since
            // we do not see all previous navigations for the iframe, we can not build a complete
            // redirect handler for each iframe.  Nor can we use the top level redirect handler as
            // that has the potential to incorrectly give access to the navigation due to previous
            // main frame gestures.
            //
            // By creating a new redirect handler for each external navigation, we are specifically
            // not covering the case where a gesture is carried over via a redirect.  This is
            // currently not feasible because we do not see all navigations for iframes and it is
            // better to error on the side of caution and require direct user gestures for iframes.
            tabRedirectHandler = new TabRedirectHandler(mTab.getActivity());
        } else {
            assert false;
            return false;
        }
        tabRedirectHandler.updateNewUrlLoading(navigationParams.pageTransitionType,
                navigationParams.isRedirect,
                navigationParams.hasUserGesture || navigationParams.hasUserGestureCarryover,
                mTab.getActivity().getLastUserInteractionTime(), getLastCommittedEntryIndex());

        boolean shouldCloseTab = shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent();
        ExternalNavigationParams params = buildExternalNavigationParams(navigationParams,
                tabRedirectHandler,
                shouldCloseTab).build();
        OverrideUrlLoadingResult result = mExternalNavHandler.shouldOverrideUrlLoading(params);
        mLastOverrideUrlLoadingResult = result;

        RecordHistogram.recordEnumeratedHistogram("Android.TabNavigationInterceptResult",
                result.ordinal(), OverrideUrlLoadingResult.values().length);
        switch (result) {
            case OVERRIDE_WITH_EXTERNAL_INTENT:
                assert mExternalNavHandler.canExternalAppHandleUrl(url);
                if (navigationParams.isMainFrame) {
                    onOverrideUrlLoadingAndLaunchIntent();
                }
                return true;
            case OVERRIDE_WITH_CLOBBERING_TAB:
                mShouldClearRedirectHistoryForTabClobbering = true;
                return true;
            case OVERRIDE_WITH_ASYNC_ACTION:
                if (!shouldCloseTab && navigationParams.isMainFrame) {
                    onOverrideUrlLoadingAndLaunchIntent();
                }
                return true;
            case NO_OVERRIDE:
            default:
                if (navigationParams.isExternalProtocol) {
                    logBlockedNavigationToDevToolsConsole(url);
                    return true;
                }
                return DataUseTabUIManager.shouldOverrideUrlLoading(mTab.getActivity(), mTab, url,
                        navigationParams.pageTransitionType, navigationParams.referrer);
        }
    }

    /**
     * Returns ExternalNavigationParams.Builder to generate ExternalNavigationParams for
     * ExternalNavigationHandler#shouldOverrideUrlLoading().
     */
    public ExternalNavigationParams.Builder buildExternalNavigationParams(
            NavigationParams navigationParams, TabRedirectHandler tabRedirectHandler,
            boolean shouldCloseTab) {
        boolean isInitialTabLaunchInBackground =
                mTab.getLaunchType() == TabLaunchType.FROM_LONGPRESS_BACKGROUND && shouldCloseTab;
        // http://crbug.com/448977: If a new tab is closed by this overriding, we should open an
        // Intent in a new tab when Chrome receives it again.
        return new ExternalNavigationParams
                .Builder(navigationParams.url, mTab.isIncognito(), navigationParams.referrer,
                        navigationParams.pageTransitionType, navigationParams.isRedirect)
                .setTab(mTab)
                .setApplicationMustBeInForeground(true)
                .setRedirectHandler(tabRedirectHandler)
                .setOpenInNewTab(shouldCloseTab)
                .setIsBackgroundTabNavigation(mTab.isHidden() && !isInitialTabLaunchInBackground)
                .setIsMainFrame(navigationParams.isMainFrame)
                .setHasUserGesture(navigationParams.hasUserGesture)
                .setShouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent(
                        shouldCloseTab && navigationParams.isMainFrame);
    }

    /**
     * Updates navigation history if navigation is canceled due to intent handler. We go back to the
     * last committed entry index which was saved before the navigation, and remove the empty
     * entries from the navigation history. See crbug.com/426679
     */
    public void maybeUpdateNavigationHistory() {
        WebContents webContents = mTab.getWebContents();
        if (mClearAllForwardHistoryRequired && webContents != null) {
            NavigationController navigationController =
                    webContents.getNavigationController();
            int lastCommittedEntryIndex = getLastCommittedEntryIndex();
            while (navigationController.canGoForward()) {
                boolean ret = navigationController.removeEntryAtIndex(
                        lastCommittedEntryIndex + 1);
                assert ret;
            }
        } else if (mShouldClearRedirectHistoryForTabClobbering
                && webContents != null) {
            // http://crbug/479056: Even if we clobber the current tab, we want to remove
            // redirect history to be consistent.
            NavigationController navigationController =
                    webContents.getNavigationController();
            int indexBeforeRedirection = mTab.getTabRedirectHandler()
                    .getLastCommittedEntryIndexBeforeStartingNavigation();
            int lastCommittedEntryIndex = getLastCommittedEntryIndex();
            for (int i = lastCommittedEntryIndex - 1; i > indexBeforeRedirection; --i) {
                boolean ret = navigationController.removeEntryAtIndex(i);
                assert ret;
            }
        }
        mClearAllForwardHistoryRequired = false;
        mShouldClearRedirectHistoryForTabClobbering = false;
    }

    AuthenticatorNavigationInterceptor getAuthenticatorNavigationInterceptor() {
        return mAuthenticatorHelper;
    }

    private int getLastCommittedEntryIndex() {
        if (mTab.getWebContents() == null) return -1;
        return mTab.getWebContents().getNavigationController().getLastCommittedEntryIndex();
    }

    private boolean shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent() {
        if (mTab.getWebContents() == null) return false;
        if (!mTab.getWebContents().getNavigationController().canGoToOffset(0)) return true;

        // http://crbug/415948 : if the last committed entry index which was saved before this
        // navigation is invalid, it means that this navigation is the first one since this tab was
        // created.
        // In such case, we would like to close this tab.
        if (mTab.getTabRedirectHandler().isOnNavigation()) {
            return mTab.getTabRedirectHandler().getLastCommittedEntryIndexBeforeStartingNavigation()
                    == TabRedirectHandler.INVALID_ENTRY_INDEX;
        }
        return false;
    }

    /**
     * Called when Chrome decides to override URL loading and show an intent picker.
     */
    private void onOverrideUrlLoadingAndLaunchIntent() {
        if (mTab.getWebContents() == null) return;

        // Before leaving Chrome, close the empty child tab.
        // If a new tab is created through JavaScript open to load this
        // url, we would like to close it as we will load this url in a
        // different Activity.
        if (shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent()) {
            if (mTab.getLaunchType() == TabLaunchType.FROM_EXTERNAL_APP) {
                // Moving task back before closing the tab allows back button to function better
                // when Chrome was an intermediate link redirector between two apps.
                // crbug.com/487938.
                mTab.getActivity().moveTaskToBack(false);
            }
            mTab.getTabModelSelector().closeTab(mTab);
        } else if (mTab.getTabRedirectHandler().isOnNavigation()) {
            int lastCommittedEntryIndexBeforeNavigation = mTab.getTabRedirectHandler()
                    .getLastCommittedEntryIndexBeforeStartingNavigation();
            if (getLastCommittedEntryIndex() > lastCommittedEntryIndexBeforeNavigation) {
                // http://crbug/426679 : we want to go back to the last committed entry index which
                // was saved before this navigation, and remove the empty entries from the
                // navigation history.
                mClearAllForwardHistoryRequired = true;
                mTab.getWebContents().getNavigationController().goToNavigationIndex(
                        lastCommittedEntryIndexBeforeNavigation);
            }
        }
    }

    private void logBlockedNavigationToDevToolsConsole(String url) {
        int resId = mExternalNavHandler.canExternalAppHandleUrl(url)
                ? R.string.blocked_navigation_warning
                : R.string.unreachable_navigation_warning;
        mTab.getWebContents().addMessageToDevToolsConsole(
                ConsoleMessageLevel.WARNING, mTab.getApplicationContext().getString(resId, url));
    }
}
