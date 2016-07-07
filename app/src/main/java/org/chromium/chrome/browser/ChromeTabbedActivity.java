// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.MemoryPressureListener;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome.OverviewLayoutFactoryDelegate;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChromePhone;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChromeTablet;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.phone.StackLayout;
import org.chromium.chrome.browser.cookies.CookiesFetcher;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.document.DocumentUma;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarkUtils;
import org.chromium.chrome.browser.firstrun.FirstRunActivity;
import org.chromium.chrome.browser.firstrun.FirstRunFlowSequencer;
import org.chromium.chrome.browser.firstrun.FirstRunSignInProcessor;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.ntp.NativePageAssassin;
import org.chromium.chrome.browser.omaha.OmahaClient;
import org.chromium.chrome.browser.omnibox.AutocompleteController;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.ConnectionChangeReceiver;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPreferences;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPromoScreen;
import org.chromium.chrome.browser.signin.SigninPromoScreen;
import org.chromium.chrome.browser.snackbar.undo.UndoBarPopupController;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.TabWindowManager;
import org.chromium.chrome.browser.toolbar.ToolbarControlContainer;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.emptybackground.EmptyBackgroundViewWrapper;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarManager;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.crypto.CipherFactory;
import org.chromium.content.common.ContentSwitches;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.widget.Toast;

/**
 * This is the main activity for ChromeMobile when not running in document mode.  All the tabs
 * are accessible via a chrome specific tab switching UI.
 */
public class ChromeTabbedActivity extends ChromeActivity implements OverviewModeObserver {

    private static final int FIRST_RUN_EXPERIENCE_RESULT = 101;

    private static final String TAG = "ChromeTabbedActivity";

    private static final String HELP_URL_PREFIX = "https://support.google.com/chrome/";

    private static final String FRE_RUNNING = "First run is running";

    private static final String WINDOW_INDEX = "window_index";

    // How long to delay closing the current tab when our app is minimized.  Have to delay this
    // so that we don't show the contents of the next tab while minimizing.
    private static final long CLOSE_TAB_ON_MINIMIZE_DELAY_MS = 500;

    // Maximum delay for initial tab creation. This is for homepage and NTP, not previous tabs
    // restore. This is needed because we do not know when reading PartnerBrowserCustomizations
    // provider will be finished.
    private static final int INITIAL_TAB_CREATION_TIMEOUT_MS = 500;

    /**
     * Sending an intent with this extra sets the app into single process mode.
     * This is only used for testing, when certain tests want to force this behaviour.
     */
    public static final String INTENT_EXTRA_TEST_RENDER_PROCESS_LIMIT = "render_process_limit";

    /**
     * Sending an intent with this action to Chrome will cause it to close all tabs
     * (iff the --enable-test-intents command line flag is set). If a URL is supplied in the
     * intent data, this will be loaded and unaffected by the close all action.
     */
    private static final String ACTION_CLOSE_TABS =
            "com.google.android.apps.chrome.ACTION_CLOSE_TABS";

    private FindToolbarManager mFindToolbarManager;

    private UndoBarPopupController mUndoBarPopupController;

    private LayoutManagerChrome mLayoutManager;

    private ViewGroup mContentContainer;

    private ToolbarControlContainer mControlContainer;

    private TabModelSelectorImpl mTabModelSelectorImpl;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private TabModelObserver mTabModelObserver;

    private ConnectionChangeReceiver mConnectionChangeReceiver;

    private boolean mUIInitialized = false;

    private boolean mIsOnFirstRun = false;

    private Boolean mIsAccessibilityEnabled;

    /**
     * Keeps track of whether or not a specific tab was created based on the startup intent.
     */
    private boolean mCreatedTabOnStartup = false;

    // Whether or not chrome was launched with an intent to open a tab.
    private boolean mIntentWithEffect = false;

    // Time at which an intent was received and handled.
    private long mIntentHandlingTimeMs = 0;

    private class TabbedAssistStatusHandler extends AssistStatusHandler {
        public TabbedAssistStatusHandler(Activity activity) {
            super(activity);
        }

        @Override
        public boolean isAssistSupported() {
            // If we are in the tab switcher and any incognito tabs are present, disable assist.
            if (isInOverviewMode() && mTabModelSelectorImpl != null
                    && mTabModelSelectorImpl.getModel(true).getCount() > 0) {
                return false;
            }
            return super.isAssistSupported();
        }
    }

    @Override
    public void initializeCompositor() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.initializeCompositor");
            super.initializeCompositor();

            mTabModelSelectorImpl.onNativeLibraryReady(getTabContentManager());

            mTabModelObserver = new EmptyTabModelObserver() {
                @Override
                public void didCloseTab(Tab tab) {
                    closeIfNoTabsAndHomepageEnabled();
                }

                @Override
                public void tabPendingClosure(Tab tab) {
                    closeIfNoTabsAndHomepageEnabled();
                }

                private void closeIfNoTabsAndHomepageEnabled() {
                    // If the last tab is closed, and homepage is enabled, then exit Chrome.
                    if (HomepageManager.isHomepageEnabled(getApplicationContext())
                            && getTabModelSelector().getTotalTabCount() == 0) {
                        finish();
                    }
                }

                @Override
                public void didAddTab(Tab tab, TabLaunchType type) {
                    if (type == TabLaunchType.FROM_LONGPRESS_BACKGROUND
                            && !DeviceClassManager.enableAnimations(getApplicationContext())) {
                        Toast.makeText(ChromeTabbedActivity.this,
                                R.string.open_in_new_tab_toast,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            };
            for (TabModel model : mTabModelSelectorImpl.getModels()) {
                model.addObserver(mTabModelObserver);
            }

            Bundle state = getSavedInstanceState();
            if (state != null && state.containsKey(FRE_RUNNING)) {
                mIsOnFirstRun = state.getBoolean(FRE_RUNNING);
            }
        } finally {
            TraceEvent.end("ChromeTabbedActivity.initializeCompositor");
        }
    }

    private void refreshSignIn() {
        if (mIsOnFirstRun) return;
        Log.i(TAG, "in refreshSignIn before starting the sign-in processor");
        FirstRunSignInProcessor.start(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        mIntentHandlingTimeMs = SystemClock.uptimeMillis();
        super.onNewIntent(intent);
    }

    @Override
    public void finishNativeInitialization() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.finishNativeInitialization");

            launchFirstRunExperience();

            ChromePreferenceManager preferenceManager = ChromePreferenceManager.getInstance(this);
            // Promos can only be shown when we start with ACTION_MAIN intent and
            // after FRE is complete.
            if (!mIntentWithEffect && FirstRunStatus.getFirstRunFlowComplete(this)) {
                // Only show promos on the second oppurtunity. This is because we show FRE on the
                // first oppurtunity, and we don't want to show such content back to back.
                if (preferenceManager.getPromosSkippedOnFirstStart()) {
                    // Data reduction promo should be temporarily suppressed if the sign in promo is
                    // shown to avoid nagging users too much.
                    if (!SigninPromoScreen.launchSigninPromoIfNeeded(this)) {
                        DataReductionPromoScreen.launchDataReductionPromo(this);
                    }
                } else {
                    preferenceManager.setPromosSkippedOnFirstStart(true);
                }
            }

            refreshSignIn();

            initializeUI();

            // The dataset has already been created, we need to initialize our state.
            mTabModelSelectorImpl.notifyChanged();

            getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_OFF);

            super.finishNativeInitialization();

            if (getActivityTab() != null) {
                DataReductionPreferences.launchDataReductionSSLInfoBar(
                        this, getActivityTab().getWebContents());
            }
        } finally {
            TraceEvent.end("ChromeTabbedActivity.finishNativeInitialization");
        }
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();
        CookiesFetcher.restoreCookies(this);
        StartupMetrics.getInstance().recordHistogram(false);
    }

    @Override
    public void onPauseWithNative() {
        mTabModelSelectorImpl.commitAllTabClosures();
        CookiesFetcher.persistCookies(this);
        super.onPauseWithNative();
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        mTabModelSelectorImpl.saveState();
        try {
            getConnectionChangeReceiver().unregisterReceiver(ChromeTabbedActivity.this);
        } catch (IllegalArgumentException e) {
            // This may happen when onStop get called very early in UI test.
        }
        StartupMetrics.getInstance().recordHistogram(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        StartupMetrics.getInstance().updateIntent(getIntent());
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        // If we don't have a current tab, show the overview mode.
        if (getActivityTab() == null) mLayoutManager.showOverview(false);

        getConnectionChangeReceiver().registerReceiver(ChromeTabbedActivity.this);

        resetSavedInstanceState();

        if (FeatureUtilities.isDocumentModeEligible(this)) {
            DocumentUma.recordInDocumentMode(false);
        }
    }

    @Override
    public void onNewIntentWithNative(Intent intent) {
        try {
            TraceEvent.begin("ChromeTabbedActivity.onNewIntentWithNative");

            super.onNewIntentWithNative(intent);
            if (CommandLine.getInstance().hasSwitch(ContentSwitches.ENABLE_TEST_INTENTS)) {
                handleDebugIntent(intent);
            }
        } finally {
            TraceEvent.end("ChromeTabbedActivity.onNewIntentWithNative");
        }
    }

    @Override
    public ChromeTabCreator getTabCreator(boolean incognito) {
        TabCreator tabCreator = super.getTabCreator(incognito);
        assert tabCreator instanceof ChromeTabCreator;
        return (ChromeTabCreator) tabCreator;
    }

    @Override
    public ChromeTabCreator getCurrentTabCreator() {
        TabCreator tabCreator = super.getCurrentTabCreator();
        assert tabCreator instanceof ChromeTabCreator;
        return (ChromeTabCreator) tabCreator;
    }

    @Override
    protected AssistStatusHandler createAssistStatusHandler() {
        return new TabbedAssistStatusHandler(this);
    }

    private void handleDebugIntent(Intent intent) {
        if (ACTION_CLOSE_TABS.equals(intent.getAction())) {
            getTabModelSelector().closeAllTabs();
        } else if (MemoryPressureListener.handleDebugIntent(ChromeTabbedActivity.this,
                intent.getAction())) {
            // Handled.
        }
    }

    private static class StackLayoutFactory implements OverviewLayoutFactoryDelegate {
        @Override
        public Layout createOverviewLayout(Context context, LayoutUpdateHost updateHost,
                                           LayoutRenderHost renderHost, EventFilter eventFilter) {
            return new StackLayout(context, updateHost, renderHost, eventFilter);
        }
    }

    private void initializeUI() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.initializeUI");

            CommandLine commandLine = CommandLine.getInstance();

            commandLine.appendSwitch(ContentSwitches.ENABLE_INSTANT_EXTENDED_API);

            CompositorViewHolder compositorViewHolder = getCompositorViewHolder();
            if (DeviceFormFactor.isTablet(this)) {
                boolean enableTabSwitcher =
                        CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_TABLET_TAB_STACK);
                mLayoutManager = new LayoutManagerChromeTablet(compositorViewHolder,
                        enableTabSwitcher ? new StackLayoutFactory() : null);
            } else {
                mLayoutManager = new LayoutManagerChromePhone(compositorViewHolder,
                        new StackLayoutFactory());
            }
            mLayoutManager.setEnableAnimations(DeviceClassManager.enableAnimations(this));
            mLayoutManager.addOverviewModeObserver(this);

            // TODO(yusufo): get rid of findViewById(R.id.url_bar).
            initializeCompositorContent(mLayoutManager, findViewById(R.id.url_bar),
                    mContentContainer, mControlContainer);

            mTabModelSelectorImpl.setOverviewModeBehavior(mLayoutManager);

            mUndoBarPopupController.initialize();

            // Adjust the content container if we're not entering fullscreen mode.
            if (getFullscreenManager() == null) {
                float controlHeight = getResources().getDimension(R.dimen.control_container_height);
                ((FrameLayout.LayoutParams) mContentContainer.getLayoutParams()).topMargin =
                        (int) controlHeight;
            }

            // Bootstrap the first tab as it may have been created before initializing the
            // fullscreen manager.
            if (mTabModelSelectorImpl != null && mTabModelSelectorImpl.getCurrentTab() != null) {
                mTabModelSelectorImpl.getCurrentTab().setFullscreenManager(getFullscreenManager());
            }

            mFindToolbarManager = new FindToolbarManager(this, getTabModelSelector(),
                    getToolbarManager()
                            .getActionModeController().getActionModeCallback());
            if (getContextualSearchManager() != null) {
                getContextualSearchManager().setFindToolbarManager(mFindToolbarManager);
            }

            OnClickListener tabSwitcherClickHandler = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getFullscreenManager() != null
                            && getFullscreenManager().getPersistentFullscreenMode()) {
                        return;
                    }
                    toggleOverview();
                }
            };

            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.bottom_bar_layout);
            linearLayout.getChildAt(linearLayout.getChildCount() - 1).setOnClickListener(tabSwitcherClickHandler);

            OnClickListener newTabClickHandler = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // This assumes that the keyboard can not be seen at the same time as the
                    // newtab button on the toolbar.
                    getCurrentTabCreator().launchNTP();
                }
            };
            OnClickListener bookmarkClickHandler = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    addOrEditBookmark(getActivityTab());
                }
            };

            getToolbarManager().initializeWithNative(mTabModelSelectorImpl, getFullscreenManager(),
                    mFindToolbarManager, mLayoutManager, mLayoutManager,
                    tabSwitcherClickHandler, newTabClickHandler, bookmarkClickHandler, null);

            removeWindowBackground();

            if (isTablet()) {
                EmptyBackgroundViewWrapper bgViewWrapper = new EmptyBackgroundViewWrapper(
                        getTabModelSelector(), getTabCreator(false), ChromeTabbedActivity.this,
                        getAppMenuHandler(), mLayoutManager);
                bgViewWrapper.initialize();
            }

            mLayoutManager.hideOverview(false);

            mUIInitialized = true;
        } finally {
            TraceEvent.end("ChromeTabbedActivity.initializeUI");
        }
    }

    @Override
    public void initializeState() {
        // This method goes through 3 steps:
        // 1. Load the saved tab state (but don't start restoring the tabs yet).
        // 2. Process the Intent that this activity received and if that should result in any
        //    new tabs, create them.  This is done after step 1 so that the new tab gets
        //    created after previous tab state was restored.
        // 3. If no tabs were created in any of the above steps, create an NTP, otherwise
        //    start asynchronous tab restore (loading the previously active tab synchronously
        //    if no new tabs created in step 2).

        // Only look at the original intent if this is not a "restoration" and we are allowed to
        // process intents. Any subsequent intents are carried through onNewIntent.
        try {
            TraceEvent.begin("ChromeTabbedActivity.initializeState");

            super.initializeState();

            Intent intent = getIntent();

            if (!CipherFactory.getInstance().restoreFromBundle(getSavedInstanceState())) {
                mTabModelSelectorImpl.clearEncryptedState();
            }

            boolean noRestoreState =
                    CommandLine.getInstance().hasSwitch(ChromeSwitches.NO_RESTORE_STATE);
            if (noRestoreState) {
                // Clear the state files because they are inconsistent and useless from now on.
                mTabModelSelectorImpl.clearState();
            } else if (!mIsOnFirstRun) {
                // State should be clear when we start first run and hence we do not need to load
                // a previous state. This may change the current Model, watch out for initialization
                // based on the model.
                mTabModelSelectorImpl.loadState();
            }

            mIntentWithEffect = false;
            if ((mIsOnFirstRun || getSavedInstanceState() == null) && intent != null
                    && !mIntentHandler.shouldIgnoreIntent(ChromeTabbedActivity.this, intent)) {
                mIntentWithEffect = mIntentHandler.onNewIntent(ChromeTabbedActivity.this, intent);
            }

            mCreatedTabOnStartup = getCurrentTabModel().getCount() > 0
                    || mTabModelSelectorImpl.getRestoredTabCount() > 0
                    || mIntentWithEffect;

            // We always need to try to restore tabs. The set of tabs might be empty, but at least
            // it will trigger the notification that tab restore is complete which is needed by
            // other parts of Chrome such as sync.
            boolean activeTabBeingRestored = !mIntentWithEffect;
            mTabModelSelectorImpl.restoreTabs(activeTabBeingRestored);

            // Only create an initial tab if no tabs were restored and no intent was handled.
            // Also, check whether the active tab was supposed to be restored and that the total
            // tab count is now non zero.  If this is not the case, tab restore failed and we need
            // to create a new tab as well.
            if (!mCreatedTabOnStartup
                    || (activeTabBeingRestored && getTabModelSelector().getTotalTabCount() == 0)) {
                // If homepage URI is not determined, due to PartnerBrowserCustomizations provider
                // async reading, then create a tab at the async reading finished. If it takes
                // too long, just create NTP.
                PartnerBrowserCustomizations.setOnInitializeAsyncFinished(
                        new Runnable() {
                            @Override
                            public void run() {
                                createInitialTab();
                            }
                        }, INITIAL_TAB_CREATION_TIMEOUT_MS);
            }

            RecordHistogram.recordBooleanHistogram(
                    "MobileStartup.ColdStartupIntent", mIntentWithEffect);
        } finally {
            TraceEvent.end("ChromeTabbedActivity.initializeState");
        }
    }

    /**
     * Create an initial tab for cold start without restored tabs.
     */
    private void createInitialTab() {
        String url = HomepageManager.getHomepageUri(getApplicationContext());
        if (TextUtils.isEmpty(url)) url = UrlConstants.NTP_URL;
        getTabCreator(false).launchUrl(url, TabLaunchType.FROM_MENU_OR_OVERVIEW);
    }

    @Override
    public boolean onActivityResultWithNative(int requestCode, int resultCode, Intent data) {
        if (super.onActivityResultWithNative(requestCode, resultCode, data)) return true;

        if (requestCode == FIRST_RUN_EXPERIENCE_RESULT) {
            mIsOnFirstRun = false;
            if (resultCode == RESULT_OK) {
                refreshSignIn();
            } else {
                if (data != null && data.getBooleanExtra(
                        FirstRunActivity.RESULT_CLOSE_APP, false)) {
                    getTabModelSelector().closeAllTabs(true);
                    finish();
                } else {
                    launchFirstRunExperience();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityModeChanged(boolean enabled) {
        super.onAccessibilityModeChanged(enabled);

        if (mLayoutManager != null) {
            mLayoutManager.setEnableAnimations(
                    DeviceClassManager.enableAnimations(getApplicationContext()));
        }
        if (isTablet()) {
            if (getCompositorViewHolder() != null) {
                getCompositorViewHolder().onAccessibilityStatusChanged(enabled);
            }
        }
        if (mLayoutManager != null && mLayoutManager.overviewVisible()
                && mIsAccessibilityEnabled != enabled) {
            mLayoutManager.hideOverview(false);
            if (getTabModelSelector().getCurrentModel().getCount() == 0) {
                getCurrentTabCreator().launchNTP();
            }
        }
        mIsAccessibilityEnabled = enabled;
    }

    /**
     * Internal class which performs the intent handling operations delegated by IntentHandler.
     */
    private class InternalIntentDelegate implements IntentHandler.IntentHandlerDelegate {

        /**
         * Processes a url view intent.
         *
         * @param url The url from the intent.
         */
        @Override
        public void processUrlViewIntent(String url, String referer, String headers,
                                         TabOpenType tabOpenType, String externalAppId, int tabIdToBringToFront,
                                         boolean hasUserGesture, Intent intent) {
            TabModel tabModel = getCurrentTabModel();
            switch (tabOpenType) {
                case REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB:
                    // Used by the bookmarks application.
                    if (tabModel.getCount() > 0 && mUIInitialized
                            && mLayoutManager.overviewVisible()) {
                        mLayoutManager.hideOverview(true);
                    }
                    mTabModelSelectorImpl.tryToRestoreTabStateForUrl(url);
                    int tabToBeClobberedIndex = TabModelUtils.getTabIndexByUrl(tabModel, url);
                    Tab tabToBeClobbered = tabModel.getTabAt(tabToBeClobberedIndex);
                    if (tabToBeClobbered != null) {
                        TabModelUtils.setIndex(tabModel, tabToBeClobberedIndex);
                        tabToBeClobbered.reload();
                        RecordUserAction.record("MobileTabClobbered");
                    } else {
                        launchIntent(url, referer, headers, externalAppId, true, intent);
                    }
                    RecordUserAction.record("MobileReceivedExternalIntent");
                    int shortcutSource = intent.getIntExtra(
                            ShortcutHelper.EXTRA_SOURCE, ShortcutSource.UNKNOWN);
                    LaunchMetrics.recordHomeScreenLaunchIntoTab(url, shortcutSource);
                    break;
                case REUSE_APP_ID_MATCHING_TAB_ELSE_NEW_TAB:
                    launchIntent(url, referer, headers, externalAppId, false, intent);
                    RecordUserAction.record("MobileReceivedExternalIntent");
                    break;
                case BRING_TAB_TO_FRONT:
                    mTabModelSelectorImpl.tryToRestoreTabStateForId(tabIdToBringToFront);

                    int tabIndex = TabModelUtils.getTabIndexById(tabModel, tabIdToBringToFront);
                    if (tabIndex == TabModel.INVALID_TAB_INDEX) {
                        TabModel otherModel =
                                getTabModelSelector().getModel(!tabModel.isIncognito());
                        tabIndex = TabModelUtils.getTabIndexById(otherModel, tabIdToBringToFront);
                        if (tabIndex != TabModel.INVALID_TAB_INDEX) {
                            getTabModelSelector().selectModel(otherModel.isIncognito());
                            TabModelUtils.setIndex(otherModel, tabIndex);
                        }
                    } else {
                        TabModelUtils.setIndex(tabModel, tabIndex);
                    }
                    RecordUserAction.record("MobileReceivedExternalIntent");
                    break;
                case CLOBBER_CURRENT_TAB:
                    // The browser triggered the intent. This happens when clicking links which
                    // can be handled by other applications (e.g. www.youtube.com links).
                    Tab currentTab = getActivityTab();
                    if (currentTab != null) {
                        currentTab.getTabRedirectHandler().updateIntent(intent);
                        int transitionType = PageTransition.LINK | PageTransition.FROM_API;
                        LoadUrlParams loadUrlParams = new LoadUrlParams(url, transitionType);
                        loadUrlParams.setIntentReceivedTimestamp(mIntentHandlingTimeMs);
                        loadUrlParams.setHasUserGesture(hasUserGesture);
                        currentTab.loadUrl(loadUrlParams);
                        RecordUserAction.record("MobileTabClobbered");
                    } else {
                        launchIntent(url, referer, headers, externalAppId, true, intent);
                    }
                    break;
                case OPEN_NEW_TAB:
                    launchIntent(url, referer, headers, externalAppId, true, intent);
                    RecordUserAction.record("MobileReceivedExternalIntent");
                    break;
                case OPEN_NEW_INCOGNITO_TAB:
                    if (url == null || url.equals(UrlConstants.NTP_URL)) {
                        if (TextUtils.equals(externalAppId, getPackageName())) {
                            // Used by the Account management screen to open a new incognito tab.
                            // Account management screen collects its metrics separately.
                            getTabCreator(true).launchUrl(
                                    UrlConstants.NTP_URL, TabLaunchType.FROM_MENU_OR_OVERVIEW);
                        } else {
                            getTabCreator(true).launchUrl(
                                    UrlConstants.NTP_URL, TabLaunchType.FROM_EXTERNAL_APP);
                            RecordUserAction.record("MobileReceivedExternalIntent");
                        }
                    } else {
                        if (TextUtils.equals(externalAppId, getPackageName())) {
                            getTabCreator(true).launchUrl(
                                    url, TabLaunchType.FROM_LINK, intent, mIntentHandlingTimeMs);
                        } else {
                            getTabCreator(true).launchUrlFromExternalApp(url, referer, headers,
                                    externalAppId, true, intent, mIntentHandlingTimeMs);
                            RecordUserAction.record("MobileReceivedExternalIntent");
                        }
                    }
                    break;
                default:
                    assert false : "Unknown TabOpenType: " + tabOpenType;
                    break;
            }
            getToolbarManager().setUrlBarFocus(false);
        }

        @Override
        public void processWebSearchIntent(String query) {
            Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
            searchIntent.putExtra(SearchManager.QUERY, query);
            startActivity(searchIntent);
        }
    }

    @Override
    public boolean isStartedUpCorrectly(Intent intent) {
        if (FeatureUtilities.isDocumentMode(this)) {
            Log.e(TAG, "Discarding Intent: Starting ChromeTabbedActivity in Document mode");
            return false;
        }
        return true;
    }

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();

        // Decide whether to record startup UMA histograms. This is done  early in the main
        // Activity.onCreate() to avoid recording navigation delays when they require user input to
        // proceed. For example, FRE (First Run Experience) happens before the activity is created,
        // and triggers initialization of the native library. At the moment it seems safe to assume
        // that uninitialized native library is an indication of an application start that is
        // followed by navigation immediately without user input.
        if (!LibraryLoader.isInitialized()) {
            UmaUtils.setRunningApplicationStart(true);
        }

        CommandLine commandLine = CommandLine.getInstance();
        if (commandLine.hasSwitch(ContentSwitches.ENABLE_TEST_INTENTS)
                && getIntent() != null
                && getIntent().hasExtra(
                ChromeTabbedActivity.INTENT_EXTRA_TEST_RENDER_PROCESS_LIMIT)) {
            int value = getIntent().getIntExtra(
                    ChromeTabbedActivity.INTENT_EXTRA_TEST_RENDER_PROCESS_LIMIT, -1);
            if (value != -1) {
                String[] args = new String[1];
                args[0] = "--" + ContentSwitches.RENDER_PROCESS_LIMIT
                        + "=" + Integer.toString(value);
                commandLine.appendSwitchesAndArguments(args);
            }
        }

        commandLine.appendSwitch(ChromeSwitches.ENABLE_HIGH_END_UI_UNDO);

        supportRequestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);

        // We are starting from history with a URL after data has been cleared. On Samsung this
        // can happen after user clears data and clicks on a recents item on pre-L devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                && getIntent().getData() != null
                && (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
                && OmahaClient.isFreshInstallOrDataHasBeenCleared(getApplicationContext())) {
            getIntent().setData(null);
        }
    }

    @Override
    protected int getControlContainerLayoutId() {
        return R.layout.control_container;
    }

    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        // Critical path for startup. Create the minimum objects needed
        // to allow a blank screen draw (without depending on any native code)
        // and then yield ASAP.
        createTabModelSelectorImpl(getSavedInstanceState());

        if (isFinishing()) return;

        // Don't show the keyboard until user clicks in.
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mContentContainer = (ViewGroup) findViewById(android.R.id.content);
        mControlContainer = (ToolbarControlContainer) findViewById(R.id.control_container);

        mUndoBarPopupController = new UndoBarPopupController(this, mTabModelSelectorImpl,
                getSnackbarManager());
    }

    /**
     * Launch the First Run flow to set up Chrome.
     * There are two different pathways that can occur:
     * 1) The First Run Experience activity is run, which walks the user through the ToS, signing
     * in, and turning on UMA reporting.  This happens in most cases.
     * 2) We automatically try to sign-in the user and skip the FRE activity, then ask the user to
     * turn on UMA reporting some time later using an InfoBar.  This happens if Chrome is opened
     * with an Intent to view a URL, or if we're on a Nexus device where the user has already
     * been exposed to the ToS and Privacy Notice.
     */
    private void launchFirstRunExperience() {
        if (mIsOnFirstRun) {
            mTabModelSelectorImpl.clearState();
            return;
        }

        final boolean isIntentActionMain = getIntent() != null
                && TextUtils.equals(getIntent().getAction(), Intent.ACTION_MAIN);
        Log.i(TAG, "begin FirstRunFlowSequencer.checkIfFirstRunIsNecessary");
        final Intent freIntent = FirstRunFlowSequencer.checkIfFirstRunIsNecessary(
                this, isIntentActionMain);
        Log.i(TAG, "end FirstRunFlowSequencer.checkIfFirstRunIsNecessary");
        if (freIntent == null) return;

        mIsOnFirstRun = true;

        // TODO(dtrainor): Investigate this further and revert once Android pushes fix?
        // Posting this due to Android bug where we apparently are stopping a
        // non-resumed activity.  That statement looks incorrect, but need to not hit
        // the runtime exception here.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                startActivityForResult(freIntent, FIRST_RUN_EXPERIENCE_RESULT);
            }
        });
    }

    @Override
    protected void onDeferredStartup() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.onDeferredStartup");
            super.onDeferredStartup();

            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            RecordHistogram.recordSparseSlowlyHistogram(
                    "MemoryAndroid.DeviceMemoryClass", am.getMemoryClass());

            AutocompleteController.nativePrefetchZeroSuggestResults();
        } finally {
            TraceEvent.end("ChromeTabbedActivity.onDeferredStartup");
        }
    }

    private void createTabModelSelectorImpl(Bundle savedInstanceState) {
        // We determine the model as soon as possible so every systems get initialized coherently.
        boolean startIncognito = savedInstanceState != null
                && savedInstanceState.getBoolean("is_incognito_selected", false);
        int index = savedInstanceState != null ? savedInstanceState.getInt(WINDOW_INDEX, 0) : 0;
        mTabModelSelectorImpl = (TabModelSelectorImpl)
                TabWindowManager.getInstance().requestSelector(this, getWindowAndroid(), index);
        if (mTabModelSelectorImpl == null) {
            Toast.makeText(this, getString(R.string.unsupported_number_of_windows),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(mTabModelSelectorImpl) {

            private boolean mIsFirstPageLoadStart = true;

            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                // Discard startup navigation measurements when the user interfered and started the
                // 2nd navigation (in activity lifetime) in parallel.
                if (!mIsFirstPageLoadStart) {
                    UmaUtils.setRunningApplicationStart(false);
                } else {
                    mIsFirstPageLoadStart = false;
                }
            }
        };

        if (startIncognito) mTabModelSelectorImpl.selectModel(true);
        setTabModelSelector(mTabModelSelectorImpl);
    }

    @Override
    public void terminateIncognitoSession() {
        getTabModelSelector().getModel(true).closeAllTabs();
    }

    @Override
    public boolean onMenuOrKeyboardAction(final int id, boolean fromMenu) {
        final Tab currentTab = getActivityTab();
        if (id == R.id.new_tab_menu_id) {
            Tab launchedTab = getTabCreator(false).launchUrl(
                    UrlConstants.NTP_URL,
                    fromMenu ? TabLaunchType.FROM_MENU_OR_OVERVIEW : TabLaunchType.FROM_KEYBOARD);
            RecordUserAction.record("MobileMenuNewTab");
            RecordUserAction.record("MobileNewTabOpened");
            if (isTablet() && !fromMenu && !launchedTab.isHidden()) {
                getToolbarManager().setUrlBarFocus(true);
            }
        } else if (id == R.id.new_incognito_tab_menu_id) {
            if (PrefServiceBridge.getInstance().isIncognitoModeEnabled()) {
                // This action must be recorded before opening the incognito tab since UMA actions
                // are dropped when an incognito tab is open.
                RecordUserAction.record("MobileMenuNewIncognitoTab");
                RecordUserAction.record("MobileNewTabOpened");
                Tab launchedTab = getTabCreator(true).launchUrl(
                        UrlConstants.NTP_URL,
                        fromMenu ? TabLaunchType.FROM_MENU_OR_OVERVIEW
                                : TabLaunchType.FROM_KEYBOARD);
                if (isTablet() && !fromMenu && !launchedTab.isHidden()) {
                    getToolbarManager().setUrlBarFocus(true);
                }
            }
        } else if (id == R.id.all_bookmarks_menu_id) {
            if (currentTab != null) {
                getCompositorViewHolder().hideKeyboard(new Runnable() {
                    @Override
                    public void run() {
                        StartupMetrics.getInstance().recordOpenedBookmarks();
                        if (!EnhancedBookmarkUtils.showEnhancedBookmarkIfEnabled(
                                ChromeTabbedActivity.this)) {
                            currentTab.loadUrl(new LoadUrlParams(
                                    UrlConstants.BOOKMARKS_URL,
                                    PageTransition.AUTO_BOOKMARK));
                        }
                    }
                });
                RecordUserAction.record("MobileMenuAllBookmarks");
            }
        } else if (id == R.id.recent_tabs_menu_id) {
            if (currentTab != null) {
                currentTab.loadUrl(new LoadUrlParams(
                        UrlConstants.RECENT_TABS_URL,
                        PageTransition.AUTO_BOOKMARK));
                RecordUserAction.record("MobileMenuOpenTabs");
            }
        } else if (id == R.id.close_all_tabs_menu_id) {
            // Close both incognito and normal tabs
            getTabModelSelector().closeAllTabs();
            RecordUserAction.record("MobileMenuCloseAllTabs");
        } else if (id == R.id.close_all_incognito_tabs_menu_id) {
            // Close only incognito tabs
            getTabModelSelector().getModel(true).closeAllTabs();
            // TODO(nileshagrawal) Record unique action for this. See bug http://b/5542946.
            RecordUserAction.record("MobileMenuCloseAllTabs");
        } else if (id == R.id.find_in_page_id) {
            mFindToolbarManager.showToolbar();
            if (getContextualSearchManager() != null) {
                getContextualSearchManager().hideContextualSearch(StateChangeReason.UNKNOWN);
            }
            if (fromMenu) {
                RecordUserAction.record("MobileMenuFindInPage");
            } else {
                RecordUserAction.record("MobileShortcutFindInPage");
            }
        } else if (id == R.id.focus_url_bar) {
            boolean isUrlBarVisible = !mLayoutManager.overviewVisible()
                    && (!isTablet() || getCurrentTabModel().getCount() != 0);
            if (isUrlBarVisible) {
                getToolbarManager().setUrlBarFocus(true);
            }
        } else {
            return super.onMenuOrKeyboardAction(id, fromMenu);
        }
        return true;
    }

    @Override
    public boolean handleBackPressed() {
        if (!mUIInitialized) return false;
        final Tab currentTab = getActivityTab();

        if (currentTab == null) {
            if (getToolbarManager().back()) {
                RecordUserAction.record("SystemBackForNavigation");
                RecordUserAction.record("MobileTabClobbered");
            } else {
                moveTaskToBack(true);
            }
            RecordUserAction.record("SystemBack");
            Log.i(TAG, "handleBackPressed() - currentTab is null");
            return true;
        }

        // If we are in overview mode and not a tablet, then leave overview mode on back.
        if (mLayoutManager.overviewVisible() && !isTablet()) {
            mLayoutManager.hideOverview(true);
            // TODO(benm): Record any user metrics in this case?
            Log.i(TAG, "handleBackPressed() - hide overview");
            return true;
        }

        if (exitFullscreenIfShowing()) {
            Log.i(TAG, "handleBackPressed() - exit fullscreen");
            return true;
        }

        if (!getToolbarManager().back()) {
            Log.i(TAG, "handleBackPressed() - no back stack");
            final TabLaunchType type = currentTab.getLaunchType();
            final String associatedApp = currentTab.getAppAssociatedWith();
            final int parentId = currentTab.getParentId();
            final boolean helpUrl = currentTab.getUrl().startsWith(HELP_URL_PREFIX);

            // If the current tab url is HELP_URL, then the back button should close the tab to
            // get back to the previous state. The reason for startsWith check is that the
            // actual redirected URL is a different system language based help url.
            if (type == TabLaunchType.FROM_MENU_OR_OVERVIEW && helpUrl) {
                getCurrentTabModel().closeTab(currentTab);
                Log.i(TAG, "handleBackPressed() - help url");
                return true;
            }

            // [true]: Reached the bottom of the back stack on a tab the user did not explicitly
            // create (i.e. it was created by an external app or opening a link in background, etc).
            // [false]: Reached the bottom of the back stack on a tab that the user explicitly
            // created (e.g. selecting "new tab" from menu).
            final boolean shouldCloseTab = type == TabLaunchType.FROM_LINK
                    || type == TabLaunchType.FROM_EXTERNAL_APP
                    || type == TabLaunchType.FROM_LONGPRESS_FOREGROUND
                    || type == TabLaunchType.FROM_LONGPRESS_BACKGROUND
                    || (type == TabLaunchType.FROM_RESTORE && parentId != Tab.INVALID_TAB_ID);

            // Minimize the app if either:
            // - we decided not to close the tab
            // - we decided to close the tab, but it was opened by an external app, so we will go
            //   exit Chrome on top of closing the tab
            final boolean minimizeApp = !shouldCloseTab || (type == TabLaunchType.FROM_EXTERNAL_APP
                    && !TextUtils.equals(associatedApp, getPackageName()));

            if (minimizeApp) {
                Log.i(TAG, "handleBackPressed() - moveTaskToBack");
                moveTaskToBack(true);
                if (shouldCloseTab) {
                    // In the case of closing a tab upon minimalization, don't allow the close
                    // action to happen until after our app is minimized to make sure we don't get a
                    // brief glimpse of the newly active tab before we exit Chrome.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            getCurrentTabModel().closeTab(currentTab, false, true, false);
                        }
                    }, CLOSE_TAB_ON_MINIMIZE_DELAY_MS);
                }
            } else if (shouldCloseTab) {
                getCurrentTabModel().closeTab(currentTab, true, false, false);
            }
        } else {
            Log.i(TAG, "handleBackPressed() - moving back in navigation");
            RecordUserAction.record("SystemBackForNavigation");
            RecordUserAction.record("MobileTabClobbered");
        }
        RecordUserAction.record("SystemBack");

        return true;
    }

    /**
     * Launch a URL from an intent.
     *
     * @param url           The url from the intent.
     * @param referer       Optional referer URL to be used.
     * @param headers       Optional headers to be sent when opening the URL.
     * @param externalAppId External app id.
     * @param forceNewTab   Whether to force the URL to be launched in a new tab or to fall
     *                      back to the default behavior for making that determination.
     * @param intent        The original intent.
     */
    private void launchIntent(String url, String referer, String headers,
                              String externalAppId, boolean forceNewTab, Intent intent) {
        if (mUIInitialized) {
            mLayoutManager.hideOverview(false);
            getToolbarManager().finishAnimations();
        }
        if (TextUtils.equals(externalAppId, getPackageName())) {
            // If the intent was launched by chrome, open the new tab in the current model.
            // Using FROM_LINK ensures the tab is parented to the current tab, which allows
            // the back button to close these tabs and restore selection to the previous tab.
            getCurrentTabCreator().launchUrl(url, TabLaunchType.FROM_LINK, intent,
                    mIntentHandlingTimeMs);
        } else {
            getTabCreator(false).launchUrlFromExternalApp(url, referer, headers,
                    externalAppId, forceNewTab, intent, mIntentHandlingTimeMs);
        }
    }

    private void toggleOverview() {
        Tab currentTab = getActivityTab();
        ContentViewCore contentViewCore =
                currentTab != null ? currentTab.getContentViewCore() : null;

        if (!mLayoutManager.overviewVisible()) {
            getCompositorViewHolder().hideKeyboard(new Runnable() {
                @Override
                public void run() {
                    mLayoutManager.showOverview(true);
                }
            });
            if (contentViewCore != null) {
                contentViewCore.setAccessibilityState(false);
            }
        } else {
            Layout activeLayout = mLayoutManager.getActiveLayout();
            if (activeLayout instanceof StackLayout) {
                ((StackLayout) activeLayout).commitOutstandingModelState(LayoutManager.time());
            }
            if (getCurrentTabModel().getCount() != 0) {
                // Don't hide overview if current tab stack is empty()
                mLayoutManager.hideOverview(true);

                // hideOverview could change the current tab.  Update the local variables.
                currentTab = getActivityTab();
                contentViewCore = currentTab != null ? currentTab.getContentViewCore() : null;

                if (contentViewCore != null) {
                    contentViewCore.setAccessibilityState(true);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        CipherFactory.getInstance().saveToBundle(outState);
        outState.putBoolean("is_incognito_selected", getCurrentTabModel().isIncognito());
        outState.putBoolean(FRE_RUNNING, mIsOnFirstRun);
        outState.putInt(WINDOW_INDEX,
                TabWindowManager.getInstance().getIndexForWindow(this));
    }

    @Override
    public void onDestroyInternal() {
        if (mLayoutManager != null) mLayoutManager.removeOverviewModeObserver(this);

        if (mTabModelSelectorTabObserver != null) {
            mTabModelSelectorTabObserver.destroy();
            mTabModelSelectorTabObserver = null;
        }

        if (mTabModelObserver != null) {
            for (TabModel model : mTabModelSelectorImpl.getModels()) {
                model.removeObserver(mTabModelObserver);
            }
        }

        if (mUndoBarPopupController != null) {
            mUndoBarPopupController.destroy();
            mUndoBarPopupController = null;
        }

        super.onDestroyInternal();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // The conditions are expressed using ranges to capture intermediate levels possibly added
        // to the API in the future.
        if ((level >= TRIM_MEMORY_RUNNING_LOW && level < TRIM_MEMORY_UI_HIDDEN)
                || level >= TRIM_MEMORY_MODERATE) {
            NativePageAssassin.getInstance().freezeAllHiddenPages();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Boolean result = KeyboardShortcuts.dispatchKeyEvent(event, this, mUIInitialized);
        return result != null ? result : super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mUIInitialized) {
            return super.onKeyDown(keyCode, event);
        }
        boolean isCurrentTabVisible = !mLayoutManager.overviewVisible()
                && (!isTablet() || getCurrentTabModel().getCount() != 0);
        return KeyboardShortcuts.onKeyDown(event, this, isCurrentTabVisible, true)
                || super.onKeyDown(keyCode, event);
    }

    private ConnectionChangeReceiver getConnectionChangeReceiver() {
        if (mConnectionChangeReceiver == null) {
            mConnectionChangeReceiver = new ConnectionChangeReceiver();
        }
        return mConnectionChangeReceiver;
    }

    @VisibleForTesting
    public View getTabsView() {
        return getCompositorViewHolder();
    }

    @VisibleForTesting
    public LayoutManagerChrome getLayoutManager() {
        return (LayoutManagerChrome) getCompositorViewHolder().getLayoutManager();
    }

    @VisibleForTesting
    public Layout getOverviewListLayout() {
        return getLayoutManager().getOverviewListLayout();
    }

    @Override
    public boolean isOverlayVisible() {
        return getCompositorViewHolder() != null && !getCompositorViewHolder().isTabInteractive();
    }

    @Override
    public boolean mayShowUpdateInfoBar() {
        return !isOverlayVisible();
    }

    // App Menu related code -----------------------------------------------------------------------

    @Override
    public boolean shouldShowAppMenu() {
        // The popup menu relies on the model created during the full UI initialization, so do not
        // attempt to show the menu until the UI creation has finished.
        if (!mUIInitialized) return false;

        // Do not show the menu if we are in find in page view.
        if (mFindToolbarManager != null && mFindToolbarManager.isShowing() && !isTablet()) {
            return false;
        }

        return super.shouldShowAppMenu();
    }

    @Override
    protected void showAppMenuForKeyboardEvent() {
        if (!mUIInitialized || isFullscreenVideoPlaying()) return;
        super.showAppMenuForKeyboardEvent();
    }

    private boolean isFullscreenVideoPlaying() {
        View view = ContentVideoView.getContentVideoView();
        return view != null && view.getContext() == this;
    }

    @Override
    public boolean isInOverviewMode() {
        return mLayoutManager != null && mLayoutManager.overviewVisible();
    }

    @Override
    protected IntentHandlerDelegate createIntentHandlerDelegate() {
        return new InternalIntentDelegate();
    }

    @Override
    public void onSceneChange(Layout layout) {
        super.onSceneChange(layout);
        if (!layout.shouldDisplayContentOverlay()) mTabModelSelectorImpl.onTabsViewShown();
    }

    @Override
    public void onOverviewModeStartedShowing(boolean showToolbar) {
        if (mFindToolbarManager != null) mFindToolbarManager.hideToolbar();
        if (getAssistStatusHandler() != null) getAssistStatusHandler().updateAssistState();
        ApiCompatibilityUtils.setStatusBarColor(getWindow(), Color.BLACK);
        StartupMetrics.getInstance().recordOpenedTabSwitcher();
    }

    @Override
    public void onOverviewModeFinishedShowing() {
    }

    @Override
    public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {
    }

    @Override
    public void onOverviewModeFinishedHiding() {
        if (getAssistStatusHandler() != null) getAssistStatusHandler().updateAssistState();
        if (getActivityTab() != null) {
            setStatusBarColor(getActivityTab(), getActivityTab().getThemeColor());
        }
    }

    @Override
    protected void setStatusBarColor(Tab tab, int color) {
        super.setStatusBarColor(tab, isInOverviewMode() ? Color.BLACK : color);
    }
}
