// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsSessionToken;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RemoteViews;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.ExternalAppId;
import org.chromium.chrome.browser.KeyboardShortcuts;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerDocument;
import org.chromium.chrome.browser.datausage.DataUseTabUIManager;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.metrics.PageLoadMetrics;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.pageinfo.WebsiteSettingsPopup;
import org.chromium.chrome.browser.rappor.RapporServiceBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabIdManager;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.browser.tabmodel.TabPersistencePolicy;
import org.chromium.chrome.browser.toolbar.ToolbarControlContainer;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarManager;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;

/**
 * The activity for custom tabs. It will be launched on top of a client's task.
 */
public class CustomTabActivity extends ChromeActivity {

    private static final String TAG = "CustomTabActivity";
    private static final String LAST_URL_PREF = "pref_last_custom_tab_url";

    private static CustomTabContentHandler sActiveContentHandler;

    private FindToolbarManager mFindToolbarManager;
    private CustomTabIntentDataProvider mIntentDataProvider;
    private CustomTabsSessionToken mSession;
    private CustomTabContentHandler mCustomTabContentHandler;
    private Tab mMainTab;
    private CustomTabBottomBarDelegate mBottomBarDelegate;

    // This is to give the right package name while using the client's resources during an
    // overridePendingTransition call.
    // TODO(ianwen, yusufo): Figure out a solution to extract external resources without having to
    // change the package name.
    private boolean mShouldOverridePackage;

    private boolean mHasCreatedTabEarly;
    private boolean mIsInitialStart = true;
    // Whether there is any prerender associated with the session.
    private boolean mHasPrerender;
    private CustomTabObserver mTabObserver;

    private String mPrerenderedUrl;
    // Whether a prerender is being used.
    private boolean mHasPrerendered;

    private boolean mIsClosing;

    private static class PageLoadMetricsObserver implements PageLoadMetrics.Observer {
        private final CustomTabsConnection mConnection;
        private final CustomTabsSessionToken mSession;
        private final Tab mTab;

        public PageLoadMetricsObserver(CustomTabsConnection connection,
                CustomTabsSessionToken session, Tab tab) {
            mConnection = connection;
            mSession = session;
            mTab = tab;
        }

        @Override
        public void onFirstContentfulPaint(WebContents webContents, long firstContentfulPaintMs) {
            if (webContents != mTab.getWebContents()) return;

            mConnection.notifyPageLoadMetric(
                    mSession, PageLoadMetrics.FIRST_CONTENTFUL_PAINT, firstContentfulPaintMs);
        }
    }

    private static class CustomTabCreator extends ChromeTabCreator {
        private final boolean mSupportsUrlBarHiding;
        private final boolean mIsOpenedByChrome;

        public CustomTabCreator(
                ChromeActivity activity, WindowAndroid nativeWindow, boolean incognito,
                boolean supportsUrlBarHiding, boolean isOpenedByChrome) {
            super(activity, nativeWindow, incognito);
            mSupportsUrlBarHiding = supportsUrlBarHiding;
            mIsOpenedByChrome = isOpenedByChrome;
        }

        @Override
        public TabDelegateFactory createDefaultTabDelegateFactory() {
            return new CustomTabDelegateFactory(mSupportsUrlBarHiding, mIsOpenedByChrome);
        }
    }

    private PageLoadMetricsObserver mMetricsObserver;

    // Only the normal tab model is observed because there is no incognito tabmodel in Custom Tabs.
    private TabModelObserver mTabModelObserver = new EmptyTabModelObserver() {
        @Override
        public void didAddTab(Tab tab, TabLaunchType type) {
            PageLoadMetrics.addObserver(mMetricsObserver);
            tab.addObserver(mTabObserver);
        }

        @Override
        public void didCloseTab(int tabId, boolean incognito) {
            PageLoadMetrics.removeObserver(mMetricsObserver);
            // Finish the activity after we intent out.
            if (getTabModelSelector().getCurrentModel().getCount() == 0) finish();
        }
    };

    /**
     * Sets the currently active {@link CustomTabContentHandler} in focus.
     * @param contentHandler {@link CustomTabContentHandler} to set.
     */
    public static void setActiveContentHandler(CustomTabContentHandler contentHandler) {
        sActiveContentHandler = contentHandler;
    }

    /**
     * Used to check whether an incoming intent can be handled by the
     * current {@link CustomTabContentHandler}.
     * @return Whether the active {@link CustomTabContentHandler} has handled the intent.
     */
    public static boolean handleInActiveContentIfNeeded(Intent intent) {
        if (sActiveContentHandler == null) return false;

        if (sActiveContentHandler.shouldIgnoreIntent(intent)) {
            Log.w(TAG, "Incoming intent to Custom Tab was ignored.");
            return false;
        }

        CustomTabsSessionToken session = CustomTabsSessionToken.getSessionTokenFromIntent(intent);
        if (session == null || !session.equals(sActiveContentHandler.getSession())) return false;

        String url = IntentHandler.getUrlFromIntent(intent);
        if (TextUtils.isEmpty(url)) return false;
        sActiveContentHandler.loadUrlAndTrackFromTimestamp(new LoadUrlParams(url),
                IntentHandler.getTimestampFromIntent(intent));
        return true;
    }

    /**
     * Checks whether the active {@link CustomTabContentHandler} belongs to the given session, and
     * if true, update toolbar's custom button.
     * @param session     The {@link IBinder} that the calling client represents.
     * @param bitmap      The new icon for action button.
     * @param description The new content description for the action button.
     * @return Whether the update is successful.
     */
    static boolean updateCustomButton(
            CustomTabsSessionToken session, int id, Bitmap bitmap, String description) {
        ThreadUtils.assertOnUiThread();
        // Do nothing if there is no activity or the activity does not belong to this session.
        if (sActiveContentHandler == null || !sActiveContentHandler.getSession().equals(session)) {
            return false;
        }
        return sActiveContentHandler.updateCustomButton(id, bitmap, description);
    }

    /**
     * Checks whether the active {@link CustomTabContentHandler} belongs to the given session, and
     * if true, updates {@link RemoteViews} on the secondary toolbar.
     * @return Whether the update is successful.
     */
    static boolean updateRemoteViews(
            CustomTabsSessionToken session, RemoteViews remoteViews, int[] clickableIDs,
            PendingIntent pendingIntent) {
        ThreadUtils.assertOnUiThread();
        // Do nothing if there is no activity or the activity does not belong to this session.
        if (sActiveContentHandler == null || !sActiveContentHandler.getSession().equals(session)) {
            return false;
        }
        return sActiveContentHandler.updateRemoteViews(remoteViews, clickableIDs, pendingIntent);
    }

    @Override
    public boolean isCustomTab() {
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsClosing = false;
        CustomTabsConnection.getInstance(getApplication())
                .keepAliveForSession(mIntentDataProvider.getSession(),
                        mIntentDataProvider.getKeepAliveServiceIntent());
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomTabsConnection.getInstance(getApplication())
                .dontKeepAliveForSession(mIntentDataProvider.getSession());
    }

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();
        mIntentDataProvider = new CustomTabIntentDataProvider(getIntent(), this);
        mSession = mIntentDataProvider.getSession();
        supportRequestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
        mHasPrerender = !TextUtils.isEmpty(
                CustomTabsConnection.getInstance(getApplication()).getPrerenderedUrl(mSession));
        if (getSavedInstanceState() == null
                && CustomTabsConnection.hasWarmUpBeenFinished(getApplication())) {
            mMainTab = createMainTab();
            loadUrlInTab(mMainTab, new LoadUrlParams(getUrlToLoad()),
                    IntentHandler.getTimestampFromIntent(getIntent()));
            mHasCreatedTabEarly = true;
        }
    }

    @Override
    public boolean shouldAllocateChildConnection() {
        return !mHasCreatedTabEarly && !mHasPrerender
                && !WarmupManager.getInstance().hasSpareWebContents();
    }

    @Override
    public void postInflationStartup() {
        super.postInflationStartup();
        TabPersistencePolicy persistencePolicy = new CustomTabTabPersistencePolicy(
                getTaskId(), getSavedInstanceState() != null);
        setTabModelSelector(new TabModelSelectorImpl(
                this, persistencePolicy, getWindowAndroid(), false));
        setTabCreators(
                new CustomTabCreator(
                        this, getWindowAndroid(), false,
                        mIntentDataProvider.shouldEnableUrlBarHiding(),
                        mIntentDataProvider.isOpenedByChrome()),
                new CustomTabCreator(
                        this, getWindowAndroid(), true,
                        mIntentDataProvider.shouldEnableUrlBarHiding(),
                        mIntentDataProvider.isOpenedByChrome()));

        getToolbarManager().setCloseButtonDrawable(mIntentDataProvider.getCloseButtonDrawable());
        getToolbarManager().setShowTitle(mIntentDataProvider.getTitleVisibilityState()
                == CustomTabsIntent.SHOW_PAGE_TITLE);
        if (CustomTabsConnection.getInstance(getApplication())
                .shouldHideDomainForSession(mSession)) {
            getToolbarManager().setUrlBarHidden(true);
        }
        int toolbarColor = mIntentDataProvider.getToolbarColor();
        getToolbarManager().updatePrimaryColor(toolbarColor, false);
        if (!mIntentDataProvider.isOpenedByChrome()) {
            getToolbarManager().setShouldUpdateToolbarPrimaryColor(false);
        }
        if (toolbarColor != ApiCompatibilityUtils.getColor(
                getResources(), R.color.default_primary_color)) {
            ApiCompatibilityUtils.setStatusBarColor(getWindow(),
                    ColorUtils.getDarkenedColorForStatusBar(toolbarColor));
        }

        // Setting task title and icon to be null will preserve the client app's title and icon.
        ApiCompatibilityUtils.setTaskDescription(this, null, null, toolbarColor);
        showCustomButtonOnToolbar();
        mBottomBarDelegate = new CustomTabBottomBarDelegate(this, mIntentDataProvider);
        mBottomBarDelegate.showBottomBarIfNecessary();
    }

    @Override
    public void finishNativeInitialization() {
        final CustomTabsConnection connection = CustomTabsConnection.getInstance(getApplication());
        // If extra headers have been passed, cancel any current prerender, as
        // prerendering doesn't support extra headers.
        if (IntentHandler.getExtraHeadersFromIntent(getIntent()) != null) {
            connection.cancelPrerender(mSession);
        }

        getTabModelSelector().getModel(false).addObserver(mTabModelObserver);

        boolean successfulStateRestore = false;
        // Attempt to restore the previous tab state if applicable.
        if (getSavedInstanceState() != null) {
            assert mMainTab == null;
            getTabModelSelector().loadState(true);
            getTabModelSelector().restoreTabs(true);
            mMainTab = getTabModelSelector().getCurrentTab();
            successfulStateRestore = mMainTab != null;
            if (successfulStateRestore) initializeMainTab(mMainTab);
        }

        // If no tab was restored, create a new tab.
        if (!successfulStateRestore) {
            if (mHasCreatedTabEarly) {
                // When the tab is created early, we don't have the TabContentManager connected,
                // since compositor related controllers were not initialized at that point.
                mMainTab.attachTabContentManager(getTabContentManager());
            } else {
                mMainTab = createMainTab();
            }
            getTabModelSelector().getModel(false).addTab(mMainTab, 0, mMainTab.getLaunchType());
        }

        ToolbarControlContainer controlContainer = (ToolbarControlContainer) findViewById(
                R.id.control_container);
        LayoutManagerDocument layoutDriver = new CustomTabLayoutManager(getCompositorViewHolder());
        initializeCompositorContent(layoutDriver, findViewById(R.id.url_bar),
                (ViewGroup) findViewById(android.R.id.content), controlContainer);
        mFindToolbarManager = new FindToolbarManager(this,
                getToolbarManager().getActionModeController().getActionModeCallback());
        if (getContextualSearchManager() != null) {
            getContextualSearchManager().setFindToolbarManager(mFindToolbarManager);
        }
        getToolbarManager().initializeWithNative(getTabModelSelector(), getFullscreenManager(),
                mFindToolbarManager, null, layoutDriver, null, null, null,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        RecordUserAction.record("CustomTabs.CloseButtonClicked");
                        finishAndClose();
                    }
                });

        mMainTab.setFullscreenManager(getFullscreenManager());
        mCustomTabContentHandler = new CustomTabContentHandler() {
            @Override
            public void loadUrlAndTrackFromTimestamp(LoadUrlParams params, long timestamp) {
                if (!TextUtils.isEmpty(params.getUrl())) {
                    params.setUrl(DataReductionProxySettings.getInstance()
                            .maybeRewriteWebliteUrl(params.getUrl()));
                }
                loadUrlInTab(getActivityTab(), params, timestamp);
            }

            @Override
            public CustomTabsSessionToken getSession() {
                return mSession;
            }

            @Override
            public boolean shouldIgnoreIntent(Intent intent) {
                return mIntentHandler.shouldIgnoreIntent(CustomTabActivity.this, intent);
            }

            @Override
            public boolean updateCustomButton(int id, Bitmap bitmap, String description) {
                CustomButtonParams params = mIntentDataProvider.getButtonParamsForId(id);
                if (params == null) return false;

                params.update(bitmap, description);
                if (params.showOnToolbar()) {
                    if (!CustomButtonParams.doesIconFitToolbar(CustomTabActivity.this, bitmap)) {
                        return false;
                    }
                    showCustomButtonOnToolbar();
                } else {
                    if (mBottomBarDelegate != null) {
                        mBottomBarDelegate.updateBottomBarButtons(params);
                    }
                }
                return true;
            }

            @Override
            public boolean updateRemoteViews(RemoteViews remoteViews, int[] clickableIDs,
                    PendingIntent pendingIntent) {
                if (mBottomBarDelegate == null) return false;
                return mBottomBarDelegate.updateRemoteViews(remoteViews, clickableIDs,
                        pendingIntent);
            }
        };
        recordClientPackageName();
        connection.showSignInToastIfNecessary(mSession, getIntent());
        String url = getUrlToLoad();
        String packageName = connection.getClientPackageNameForSession(mSession);
        if (TextUtils.isEmpty(packageName)) {
            packageName = connection.extractCreatorPackage(getIntent());
        }
        DataUseTabUIManager.onCustomTabInitialNavigation(mMainTab, packageName, url);

        if (!mHasCreatedTabEarly && !successfulStateRestore) {
            loadUrlInTab(mMainTab, new LoadUrlParams(url),
                    IntentHandler.getTimestampFromIntent(getIntent()));
        }

        // Put Sync in the correct state by calling tab state initialized. crbug.com/581811.
        getTabModelSelector().markTabStateInitialized();
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        String lastUrl = preferences.getString(LAST_URL_PREF, null);
        if (lastUrl != null && lastUrl.equals(getUrlToLoad())) {
            RecordUserAction.record("CustomTabsMenuOpenSameUrl");
        } else {
            preferences.edit().putString(LAST_URL_PREF, getUrlToLoad()).apply();
        }
        super.finishNativeInitialization();
    }

    private Tab createMainTab() {
        CustomTabsConnection customTabsConnection =
                CustomTabsConnection.getInstance(getApplication());
        String url = getUrlToLoad();
        // Get any referrer that has been explicitly set by the app.
        String referrerUrl = IntentHandler.getReferrerUrlIncludingExtraHeaders(getIntent(), this);
        if (referrerUrl == null) {
            Referrer referrer = customTabsConnection.getReferrerForSession(mSession);
            if (referrer != null) referrerUrl = referrer.getUrl();
        }
        Tab tab = new Tab(TabIdManager.getInstance().generateValidId(Tab.INVALID_TAB_ID),
                Tab.INVALID_TAB_ID, false, this, getWindowAndroid(),
                TabLaunchType.FROM_EXTERNAL_APP, null, null);
        tab.setAppAssociatedWith(customTabsConnection.getClientPackageNameForSession(mSession));

        mPrerenderedUrl = customTabsConnection.getPrerenderedUrl(mSession);
        WebContents webContents =
                customTabsConnection.takePrerenderedUrl(mSession, url, referrerUrl);
        mHasPrerendered = webContents != null;
        if (!mHasPrerendered) {
            webContents = WarmupManager.getInstance().takeSpareWebContents(false, false);
        }
        if (webContents == null) webContents = WebContentsFactory.createWebContents(false, false);
        tab.initialize(
                webContents, getTabContentManager(),
                new CustomTabDelegateFactory(
                        mIntentDataProvider.shouldEnableUrlBarHiding(),
                        mIntentDataProvider.isOpenedByChrome()),
                false, false);
        initializeMainTab(tab);
        return tab;
    }

    private void initializeMainTab(Tab tab) {
        tab.getTabRedirectHandler().updateIntent(getIntent());
        tab.getView().requestFocus();
        mTabObserver = new CustomTabObserver(
                getApplication(), mSession, mIntentDataProvider.isOpenedByChrome());

        mMetricsObserver = new PageLoadMetricsObserver(
                CustomTabsConnection.getInstance(getApplication()), mSession, tab);
        tab.addObserver(mTabObserver);
    }

    @Override
    public void initializeCompositor() {
        super.initializeCompositor();
        getTabModelSelector().onNativeLibraryReady(getTabContentManager());
    }

    private void recordClientPackageName() {
        String clientName = CustomTabsConnection.getInstance(getApplication())
                .getClientPackageNameForSession(mSession);
        if (TextUtils.isEmpty(clientName)) clientName = mIntentDataProvider.getClientPackageName();
        final String packageName = clientName;
        if (TextUtils.isEmpty(packageName) || packageName.contains(getPackageName())) return;
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RapporServiceBridge.sampleString(
                        "CustomTabs.ServiceClient.PackageName", packageName);
            }
        });
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        setActiveContentHandler(mCustomTabContentHandler);

        if (getSavedInstanceState() != null || !mIsInitialStart) {
            if (mIntentDataProvider.isOpenedByChrome()) {
                RecordUserAction.record("ChromeGeneratedCustomTab.StartedReopened");
            } else {
                RecordUserAction.record("CustomTabs.StartedReopened");
            }
        } else {
            if (mIntentDataProvider.isOpenedByChrome()) {
                RecordUserAction.record("ChromeGeneratedCustomTab.StartedInitially");
            } else {
                ExternalAppId externalId =
                        IntentHandler.determineExternalIntentSource(getPackageName(), getIntent());
                RecordHistogram.recordEnumeratedHistogram("CustomTabs.ClientAppId",
                        externalId.ordinal(), ExternalAppId.INDEX_BOUNDARY.ordinal());

                RecordUserAction.record("CustomTabs.StartedInitially");
            }
        }
        if (mHasCreatedTabEarly && !mMainTab.isLoading()) postDeferredStartupIfNeeded();
        mIsInitialStart = false;
    }

    @Override
    public void onPauseWithNative() {
        super.onPauseWithNative();
        CustomTabsConnection.getInstance(getApplication()).notifyNavigationEvent(
                mSession, CustomTabsCallback.TAB_HIDDEN);
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        setActiveContentHandler(null);
        if (!mIsClosing) getTabModelSelector().saveState();
    }

    /**
     * Loads the current tab with the given load params while taking client
     * referrer and extra headers into account.
     */
    private void loadUrlInTab(final Tab tab, final LoadUrlParams params, long timeStamp) {
        Intent intent = getIntent();
        String url = getUrlToLoad();
        if (mHasPrerendered && UrlUtilities.urlsFragmentsDiffer(mPrerenderedUrl, url)) {
            mHasPrerendered = false;
            LoadUrlParams temporaryParams = new LoadUrlParams(mPrerenderedUrl);
            IntentHandler.addReferrerAndHeaders(temporaryParams, intent, this);
            tab.loadUrl(temporaryParams);
            params.setShouldReplaceCurrentEntry(true);
        }

        IntentHandler.addReferrerAndHeaders(params, intent, this);
        if (params.getReferrer() == null) {
            params.setReferrer(CustomTabsConnection.getInstance(getApplication())
                    .getReferrerForSession(mSession));
        }
        // See ChromeTabCreator#getTransitionType(). This marks the navigation chain as starting
        // from an external intent (unless otherwise specified by an extra in the intent).
        params.setTransitionType(IntentHandler.getTransitionTypeFromIntent(this, intent,
                PageTransition.LINK | PageTransition.FROM_API));
        mTabObserver.trackNextPageLoadFromTimestamp(timeStamp);
        tab.loadUrl(params);
    }

    @Override
    public void createContextualSearchTab(String searchUrl) {
        if (getActivityTab() == null) return;
        getActivityTab().loadUrl(new LoadUrlParams(searchUrl));
    }

    @Override
    public TabModelSelectorImpl getTabModelSelector() {
        return (TabModelSelectorImpl) super.getTabModelSelector();
    }

    @Override
    public Tab getActivityTab() {
        Tab tab = super.getActivityTab();
        if (tab == null) tab = mMainTab;
        return tab;
    }

    @Override
    protected AppMenuPropertiesDelegate createAppMenuPropertiesDelegate() {
        return new CustomTabAppMenuPropertiesDelegate(this, mIntentDataProvider.getMenuTitles(),
                mIntentDataProvider.shouldShowShareMenuItem(),
                mIntentDataProvider.isOpenedByChrome(),
                mIntentDataProvider.isMediaViewer());
    }

    @Override
    protected int getAppMenuLayoutId() {
        return R.menu.custom_tabs_menu;
    }

    @Override
    protected int getControlContainerLayoutId() {
        return R.layout.custom_tabs_control_container;
    }

    @Override
    public int getControlContainerHeightResource() {
        return R.dimen.custom_tabs_control_container_height;
    }

    @Override
    public String getPackageName() {
        if (mShouldOverridePackage) return mIntentDataProvider.getClientPackageName();
        return super.getPackageName();
    }

    @Override
    public void finish() {
        // Prevent the menu window from leaking.
        if (getAppMenuHandler() != null) getAppMenuHandler().hideAppMenu();

        super.finish();
        if (mIntentDataProvider != null && mIntentDataProvider.shouldAnimateOnFinish()) {
            mShouldOverridePackage = true;
            overridePendingTransition(mIntentDataProvider.getAnimationEnterRes(),
                    mIntentDataProvider.getAnimationExitRes());
            mShouldOverridePackage = false;
        } else if (mIntentDataProvider != null && mIntentDataProvider.isOpenedByChrome()) {
            overridePendingTransition(R.anim.no_anim, R.anim.activity_close_exit);
        }
    }

    /**
     * Finishes the activity and removes the reference from the Android recents.
     */
    public final void finishAndClose() {
        mIsClosing = true;
        handleFinishAndClose();
    }

    /**
     * Internal implementation that finishes the activity and removes the references from Android
     * recents.
     */
    protected void handleFinishAndClose() {
        // When on top of another app, finish is all that is required.
        finish();
    }

    @Override
    protected boolean handleBackPressed() {
        RecordUserAction.record("CustomTabs.SystemBack");

        if (getActivityTab() == null) return false;

        if (exitFullscreenIfShowing()) return true;

        if (!getToolbarManager().back()) {
            if (getCurrentTabModel().getCount() > 1) {
                getCurrentTabModel().closeTab(getActivityTab(), false, false, false);
            } else {
                finishAndClose();
            }
        }
        return true;
    }

    /**
     * Configures the custom button on toolbar. Does nothing if invalid data is provided by clients.
     */
    private void showCustomButtonOnToolbar() {
        final CustomButtonParams params = mIntentDataProvider.getCustomButtonOnToolbar();
        if (params == null) return;
        getToolbarManager().setCustomActionButton(
                params.getIcon(getResources()),
                params.getDescription(),
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mIntentDataProvider.sendButtonPendingIntentWithUrl(
                                getApplicationContext(), getActivityTab().getUrl());
                        RecordUserAction.record("CustomTabsCustomActionButtonClick");
                    }
                });
    }

    @Override
    public boolean shouldShowAppMenu() {
        return getActivityTab() != null && getToolbarManager().isInitialized();
    }

    @Override
    protected void showAppMenuForKeyboardEvent() {
        if (!shouldShowAppMenu()) return;
        super.showAppMenuForKeyboardEvent();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuIndex = getAppMenuPropertiesDelegate().getIndexOfMenuItem(item);
        if (menuIndex >= 0) {
            mIntentDataProvider.clickMenuItemWithUrl(this, menuIndex,
                    getTabModelSelector().getCurrentTab().getUrl());
            RecordUserAction.record("CustomTabsMenuCustomMenuItem");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Boolean result = KeyboardShortcuts.dispatchKeyEvent(event, this,
                getToolbarManager().isInitialized());
        return result != null ? result : super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!getToolbarManager().isInitialized()) {
            return super.onKeyDown(keyCode, event);
        }
        return KeyboardShortcuts.onKeyDown(event, this, true, false)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onMenuOrKeyboardAction(int id, boolean fromMenu) {
        // Disable creating new tabs, bookmark, history, print, help, focus_url, etc.
        if (id == R.id.focus_url_bar || id == R.id.all_bookmarks_menu_id
                || id == R.id.help_id || id == R.id.recent_tabs_menu_id
                || id == R.id.new_incognito_tab_menu_id || id == R.id.new_tab_menu_id
                || id == R.id.open_history_menu_id) {
            return true;
        } else if (id == R.id.open_in_browser_id) {
            openCurrentUrlInBrowser(false);
            RecordUserAction.record("CustomTabsMenuOpenInChrome");
            return true;
        } else if (id == R.id.info_menu_id) {
            if (getTabModelSelector().getCurrentTab() == null) return false;
            WebsiteSettingsPopup.show(
                    this, getTabModelSelector().getCurrentTab(),
                    getToolbarManager().getContentPublisher(),
                    WebsiteSettingsPopup.OPENED_FROM_MENU);
            return true;
        }
        return super.onMenuOrKeyboardAction(id, fromMenu);
    }

    @Override
    protected void setStatusBarColor(Tab tab, int color) {
        // Intentionally do nothing as CustomTabActivity explicitly sets status bar color.  Except
        // for Custom Tabs opened by Chrome.
        if (mIntentDataProvider.isOpenedByChrome()) super.setStatusBarColor(tab, color);
    }

    /**
     * @return The {@link AppMenuPropertiesDelegate} associated with this activity. For test
     *         purposes only.
     */
    @VisibleForTesting
    @Override
    public CustomTabAppMenuPropertiesDelegate getAppMenuPropertiesDelegate() {
        return (CustomTabAppMenuPropertiesDelegate) super.getAppMenuPropertiesDelegate();
    }

    @Override
    public void onCheckForUpdate(boolean updateAvailable) {
    }

    /**
     * @return The {@link CustomTabIntentDataProvider} for this {@link CustomTabActivity}. For test
     *         purposes only.
     */
    @VisibleForTesting
    CustomTabIntentDataProvider getIntentDataProvider() {
        return mIntentDataProvider;
    }

    /**
     * Opens the URL currently being displayed in the Custom Tab in the regular browser.
     * @param forceReparenting Whether tab reparenting should be forced for testing.
     *
     * @return Whether or not the tab was sent over successfully.
     */
    boolean openCurrentUrlInBrowser(boolean forceReparenting) {
        Tab tab = getActivityTab();
        if (tab == null) return false;

        String url = tab.getUrl();
        if (DomDistillerUrlUtils.isDistilledPage(url)) {
            url = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
        }
        if (TextUtils.isEmpty(url)) url = getUrlToLoad();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ChromeLauncherActivity.EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT, false);

        boolean willChromeHandleIntent = getIntentDataProvider().isOpenedByChrome();
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            willChromeHandleIntent |= ExternalNavigationDelegateImpl
                    .willChromeHandleIntent(this, intent, true);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        Bundle startActivityOptions = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.abc_fade_in, R.anim.abc_fade_out).toBundle();
        if (willChromeHandleIntent || forceReparenting) {
            Runnable finalizeCallback = new Runnable() {
                @Override
                public void run() {
                    finishAndClose();
                }
            };

            mMainTab = null;
            tab.detachAndStartReparenting(intent, startActivityOptions, finalizeCallback);
        } else {
            // Temporarily allowing disk access while fixing. TODO: http://crbug.com/581860
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();
            try {
                startActivity(intent, startActivityOptions);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
        return true;
    }

    /**
     * @return The URL that should be used from this intent. If it is a WebLite url, it may be
     *         overridden if the Data Reduction Proxy is using Lo-Fi previews.
     */
    private String getUrlToLoad() {
        String url = IntentHandler.getUrlFromIntent(getIntent());
        if (!TextUtils.isEmpty(url)) {
            url = DataReductionProxySettings.getInstance().maybeRewriteWebliteUrl(url);
        }
        return url;
    }
}
