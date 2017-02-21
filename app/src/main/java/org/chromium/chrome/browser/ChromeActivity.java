// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.app.assist.AssistContent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.BaseSwitches;
import org.chromium.base.Callback;
import org.chromium.base.CommandLine;
import org.chromium.base.ObserverList;
import org.chromium.base.SysUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.appmenu.AppMenu;
import org.chromium.chrome.browser.appmenu.AppMenuHandler;
import org.chromium.chrome.browser.appmenu.AppMenuObserver;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.blimp.ChromeBlimpClientContextDelegate;
import org.chromium.chrome.browser.bookmarks.BookmarkModel;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerDocument;
import org.chromium.chrome.browser.compositor.layouts.SceneChangeObserver;
import org.chromium.chrome.browser.compositor.layouts.content.ContentOffsetProvider;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchFieldTrial;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManager;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManager.ContextualSearchTabPromotionDelegate;
import org.chromium.chrome.browser.datausage.DataUseTabUIManager;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.dom_distiller.DistilledPagePrefsView;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManager;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.gsa.ContextReporter;
import org.chromium.chrome.browser.gsa.GSAServiceClient;
import org.chromium.chrome.browser.gsa.GSAState;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.init.AsyncInitializationActivity;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.metrics.UmaSessionStats;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.nfc.BeamController;
import org.chromium.chrome.browser.nfc.BeamProvider;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.omaha.UpdateMenuItemHelper;
import org.chromium.chrome.browser.pageinfo.WebsiteSettingsPopup;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.printing.PrintShareActivity;
import org.chromium.chrome.browser.printing.TabPrinter;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.snackbar.DataUseSnackbarController;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.AsyncTabParamsManager;
import org.chromium.chrome.browser.tabmodel.EmptyTabModel;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.TabWindowManager;
import org.chromium.chrome.browser.toolbar.Toolbar;
import org.chromium.chrome.browser.toolbar.ToolbarControlContainer;
import org.chromium.chrome.browser.toolbar.ToolbarManager;
import org.chromium.chrome.browser.util.ChromeFileProvider;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.webapps.AddToHomescreenManager;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.common.ContentSwitches;
import org.chromium.content_public.browser.ContentBitmapCallback;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.readback_types.ReadbackResponse;
import org.chromium.policy.CombinedPolicyProvider;
import org.chromium.policy.CombinedPolicyProvider.PolicyChangeListener;
import org.chromium.printing.PrintManagerDelegateImpl;
import org.chromium.printing.PrintingController;
import org.chromium.printing.PrintingControllerImpl;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link AsyncInitializationActivity} that builds and manages a {@link CompositorViewHolder}
 * and associated classes.
 */
public abstract class ChromeActivity extends AsyncInitializationActivity
        implements TabCreatorManager, AccessibilityStateChangeListener, PolicyChangeListener,
        ContextualSearchTabPromotionDelegate, SnackbarManageable, SceneChangeObserver {
    /**
     * Factory which creates the AppMenuHandler.
     */
    public interface AppMenuHandlerFactory {
        /**
         * @return AppMenuHandler for the given activity and menu resource id.
         */
        public AppMenuHandler get(Activity activity, AppMenuPropertiesDelegate delegate,
                int menuResourceId);
    }

    /**
     * No control container to inflate during initialization.
     */
    static final int NO_CONTROL_CONTAINER = -1;

    private static final int RECORD_MULTI_WINDOW_SCREEN_WIDTH_DELAY_MS = 5000;

    /**
     * Timeout in ms for reading PartnerBrowserCustomizations provider.
     */
    private static final int PARTNER_BROWSER_CUSTOMIZATIONS_TIMEOUT_MS = 10000;
    private static final String TAG = "ChromeActivity";
    private static final Rect EMPTY_RECT = new Rect();

    private TabModelSelector mTabModelSelector;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private TabCreatorManager.TabCreator mRegularTabCreator;
    private TabCreatorManager.TabCreator mIncognitoTabCreator;
    private TabContentManager mTabContentManager;
    private UmaSessionStats mUmaSessionStats;
    private ContextReporter mContextReporter;
    protected GSAServiceClient mGSAServiceClient;

    private boolean mPartnerBrowserRefreshNeeded;

    protected IntentHandler mIntentHandler;

    private boolean mDeferredStartupPosted;

    // The class cannot implement TouchExplorationStateChangeListener,
    // because it is only available for Build.VERSION_CODES.KITKAT and later.
    // We have to instantiate the TouchExplorationStateChangeListner object in the code.
    @SuppressLint("NewApi")
    private TouchExplorationStateChangeListener mTouchExplorationStateChangeListener;

    // Observes when sync becomes ready to create the mContextReporter.
    private ProfileSyncService.SyncStateChangedListener mSyncStateChangedListener;

    private ActivityWindowAndroid mWindowAndroid;
    private ChromeFullscreenManager mFullscreenManager;
    private CompositorViewHolder mCompositorViewHolder;
    private InsetObserverView mInsetObserverView;
    private ContextualSearchManager mContextualSearchManager;
    private ReaderModeManager mReaderModeManager;
    private SnackbarManager mSnackbarManager;
    private DataUseSnackbarController mDataUseSnackbarController;
    private AppMenuPropertiesDelegate mAppMenuPropertiesDelegate;
    private AppMenuHandler mAppMenuHandler;
    private ToolbarManager mToolbarManager;

    // Time in ms that it took took us to inflate the initial layout
    private long mInflateInitialLayoutDurationMs;

    private int mScreenWidthDp;
    private Runnable mRecordMultiWindowModeScreenWidthRunnable;

    private AssistStatusHandler mAssistStatusHandler;

    // A set of views obscuring all tabs. When this set is nonempty,
    // all tab content will be hidden from the accessibility tree.
    private List<View> mViewsObscuringAllTabs = new ArrayList<>();

    // Callbacks to be called when a context menu is closed.
    private final ObserverList<Callback<Menu>> mContextMenuCloseObservers = new ObserverList<>();

    private static AppMenuHandlerFactory sAppMenuHandlerFactory = new AppMenuHandlerFactory() {
        @Override
        public AppMenuHandler get(
                Activity activity, AppMenuPropertiesDelegate delegate, int menuResourceId) {
            return new AppMenuHandler(activity, delegate, menuResourceId);
        }
    };

    // See enableHardwareAcceleration()
    private boolean mSetWindowHWA;

    // Chrome delegate that includes functionalities needed by Blimp client.
    private ChromeBlimpClientContextDelegate mBlimpClientContextDelegate;

    // Skips capturing screenshot for testing purpose.
    private boolean mScreenshotCaptureSkippedForTesting;

    /**
     * @param The {@link AppMenuHandlerFactory} for creating {@link mAppMenuHandler}
     */
    @VisibleForTesting
    public static void setAppMenuHandlerFactoryForTesting(AppMenuHandlerFactory factory) {
        sAppMenuHandlerFactory = factory;
    }

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();

        // Force a partner customizations refresh if it has yet to be initialized.  This can happen
        // if Chrome is killed and you refocus a previous activity from Android recents, which does
        // not go through ChromeLauncherActivity that would have normally triggered this.
        mPartnerBrowserRefreshNeeded = !PartnerBrowserCustomizations.isInitialized();

        ApplicationInitialization.enableFullscreenFlags(
                getResources(), this, getControlContainerHeightResource());
        getWindow().setBackgroundDrawable(getBackgroundDrawable());
        mWindowAndroid = new ChromeWindow(this);
        mWindowAndroid.restoreInstanceState(getSavedInstanceState());
    }

    @SuppressLint("NewApi")
    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        mSnackbarManager = new SnackbarManager(this);
        mDataUseSnackbarController = new DataUseSnackbarController(this, getSnackbarManager());

        mAssistStatusHandler = createAssistStatusHandler();
        if (mAssistStatusHandler != null) {
            if (mTabModelSelector != null) {
                mAssistStatusHandler.setTabModelSelector(mTabModelSelector);
            }
            mAssistStatusHandler.updateAssistState();
        }

        // If a user had ALLOW_LOW_END_DEVICE_UI explicitly set to false then we manually override
        // SysUtils.isLowEndDevice() with a switch so that they continue to see the normal UI. This
        // is only the case for grandfathered-in svelte users. We no longer do so for newer users.
        if (!ChromePreferenceManager.getInstance(this).getAllowLowEndDeviceUi()) {
            CommandLine.getInstance().appendSwitch(
                    BaseSwitches.DISABLE_LOW_END_DEVICE_MODE);
        }

        AccessibilityManager manager = (AccessibilityManager)
                getBaseContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        manager.addAccessibilityStateChangeListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mTouchExplorationStateChangeListener = new TouchExplorationStateChangeListener() {
                @Override
                public void onTouchExplorationStateChanged(boolean enabled) {
                    checkAccessibility();
                }
            };
            manager.addTouchExplorationStateChangeListener(mTouchExplorationStateChangeListener);
        }

        // Make the activity listen to policy change events
        CombinedPolicyProvider.get().addPolicyChangeListener(this);

        // Set up the animation placeholder to be the SurfaceView. This disables the
        // SurfaceView's 'hole' clipping during animations that are notified to the window.
        mWindowAndroid.setAnimationPlaceholderView(mCompositorViewHolder.getSurfaceView());

        // Inform the WindowAndroid of the keyboard accessory view.
        mWindowAndroid.setKeyboardAccessoryView((ViewGroup) findViewById(R.id.keyboard_accessory));
        initializeToolbar();
    }

    @Override
    protected View getViewToBeDrawnBeforeInitializingNative() {
        View controlContainer = findViewById(R.id.control_container);
        return controlContainer != null ? controlContainer
                : super.getViewToBeDrawnBeforeInitializingNative();
    }

    /**
     * This function builds the {@link CompositorViewHolder}.  Subclasses *must* call
     * super.setContentView() before using {@link #getTabModelSelector()} or
     * {@link #getCompositorViewHolder()}.
     */
    @Override
    protected final void setContentView() {
        final long begin = SystemClock.elapsedRealtime();
        TraceEvent.begin("onCreate->setContentView()");

        enableHardwareAcceleration();
        setLowEndTheme();
        int controlContainerLayoutId = getControlContainerLayoutId();
        WarmupManager warmupManager = WarmupManager.getInstance();
        if (warmupManager.hasBuiltOrClearViewHierarchyWithToolbar(controlContainerLayoutId)) {
            View placeHolderView = new View(this);
            setContentView(placeHolderView);
            ViewGroup contentParent = (ViewGroup) placeHolderView.getParent();
            WarmupManager.getInstance().transferViewHierarchyTo(contentParent);
            contentParent.removeView(placeHolderView);
        } else {
            // Allow disk access for the content view and toolbar container setup.
            // On certain android devices this setup sequence results in disk writes outside
            // of our control, so we have to disable StrictMode to work. See crbug.com/639352.
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
            try {
                setContentView(R.layout.main);
                if (controlContainerLayoutId != NO_CONTROL_CONTAINER) {
                    ViewStub toolbarContainerStub =
                            ((ViewStub) findViewById(R.id.control_container_stub));
                    toolbarContainerStub.setLayoutResource(controlContainerLayoutId);
                    toolbarContainerStub.inflate();
                }
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
        TraceEvent.end("onCreate->setContentView()");
        mInflateInitialLayoutDurationMs = SystemClock.elapsedRealtime() - begin;

        // Set the status bar color to black by default. This is an optimization for
        // Chrome not to draw under status and navigation bars when we use the default
        // black status bar
        ApiCompatibilityUtils.setStatusBarColor(getWindow(), Color.BLACK);

        ViewGroup rootView = (ViewGroup) getWindow().getDecorView().getRootView();
        mCompositorViewHolder = (CompositorViewHolder) findViewById(R.id.compositor_view_holder);
        mCompositorViewHolder.setRootView(rootView);

        // Setting fitsSystemWindows to false ensures that the root view doesn't consume the insets.
        rootView.setFitsSystemWindows(false);

        // Add a custom view right after the root view that stores the insets to access later.
        // ContentViewCore needs the insets to determine the portion of the screen obscured by
        // non-content displaying things such as the OSK.
        mInsetObserverView = InsetObserverView.create(this);
        rootView.addView(mInsetObserverView, 0);
    }

    /**
     * Constructs {@link ToolbarManager} and the handler necessary for controlling the menu on the
     * {@link Toolbar}. Extending classes can override this call to avoid creating the toolbar.
     */
    protected void initializeToolbar() {
        final View controlContainer = findViewById(R.id.control_container);
        assert controlContainer != null;
        ToolbarControlContainer toolbarContainer = (ToolbarControlContainer) controlContainer;
        mAppMenuPropertiesDelegate = createAppMenuPropertiesDelegate();
        mAppMenuHandler = sAppMenuHandlerFactory.get(this, mAppMenuPropertiesDelegate,
                getAppMenuLayoutId());
        mToolbarManager = new ToolbarManager(this, toolbarContainer, mAppMenuHandler,
                mAppMenuPropertiesDelegate, getCompositorViewHolder().getInvalidator());
        mAppMenuHandler.addObserver(new AppMenuObserver() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                if (isVisible && !isInOverviewMode()) {
                    // The app menu badge should be removed the first time the menu is opened.
                    if (mToolbarManager.getToolbar().isShowingAppMenuUpdateBadge()) {
                        mToolbarManager.getToolbar().removeAppMenuUpdateBadge(true);
                        mCompositorViewHolder.requestRender();
                    }
                }
                if (!isVisible) {
                    mAppMenuPropertiesDelegate.onMenuDismissed();
                    MenuItem updateMenuItem = mAppMenuHandler.getAppMenu().getMenu().findItem(
                            R.id.update_menu_id);
                    if (updateMenuItem != null && updateMenuItem.isVisible()) {
                        UpdateMenuItemHelper.getInstance().onMenuDismissed();
                    }
                }
            }
        });
    }

    /**
     * @return {@link ToolbarManager} that belongs to this activity.
     */
    @VisibleForTesting
    public ToolbarManager getToolbarManager() {
        return mToolbarManager;
    }

    /**
     * @return The resource id for the menu to use in {@link AppMenu}. Default is R.menu.main_menu.
     */
    protected int getAppMenuLayoutId() {
        return R.menu.main_menu;
    }

    /**
     * @return {@link AppMenuPropertiesDelegate} instance that the {@link AppMenuHandler}
     *         should be using in this activity.
     */
    protected AppMenuPropertiesDelegate createAppMenuPropertiesDelegate() {
        return new AppMenuPropertiesDelegate(this);
    }

    /**
     * @return The assist handler for this activity.
     */
    protected AssistStatusHandler getAssistStatusHandler() {
        return mAssistStatusHandler;
    }

    /**
     * @return A newly constructed assist handler for this given activity type.
     */
    protected AssistStatusHandler createAssistStatusHandler() {
        return new AssistStatusHandler(this);
    }

    /**
     * @return The resource id for the layout to use for {@link ControlContainer}. 0 by default.
     */
    protected int getControlContainerLayoutId() {
        return NO_CONTROL_CONTAINER;
    }

    /**
     * @return Whether contextual search is allowed for this activity or not.
     */
    protected boolean isContextualSearchAllowed() {
        return true;
    }

    @Override
    public void initializeState() {
        super.initializeState();
        mBlimpClientContextDelegate =
                ChromeBlimpClientContextDelegate.createAndSetDelegateForContext(
                        Profile.getLastUsedProfile().getOriginalProfile());

        IntentHandler.setTestIntentsEnabled(
                CommandLine.getInstance().hasSwitch(ContentSwitches.ENABLE_TEST_INTENTS));
        mIntentHandler = new IntentHandler(createIntentHandlerDelegate(), getPackageName());
    }

    @Override
    public void initializeCompositor() {
        TraceEvent.begin("ChromeActivity:CompositorInitialization");
        super.initializeCompositor();

        setTabContentManager(new TabContentManager(this, getContentOffsetProvider(),
                DeviceClassManager.enableSnapshots()));
        mCompositorViewHolder.onNativeLibraryReady(mWindowAndroid, getTabContentManager());

        if (isContextualSearchAllowed() && ContextualSearchFieldTrial.isEnabled()) {
            mContextualSearchManager = new ContextualSearchManager(this, mWindowAndroid, this);
        }

        if (ReaderModeManager.isEnabled(this)) {
            mReaderModeManager = new ReaderModeManager(getTabModelSelector(), this);
            if (mToolbarManager != null) {
                mToolbarManager.addFindToolbarObserver(
                        mReaderModeManager.getFindToolbarObserver());
            }
        }

        TraceEvent.end("ChromeActivity:CompositorInitialization");
    }

    /**
     * Sets the {@link TabModelSelector} owned by this {@link ChromeActivity}.
     * @param tabModelSelector A {@link TabModelSelector} instance.
     */
    protected void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;

        if (mTabModelSelectorTabObserver != null) mTabModelSelectorTabObserver.destroy();
        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(tabModelSelector) {
            @Override
            public void didFirstVisuallyNonEmptyPaint(Tab tab) {
                if (DataUseTabUIManager.checkAndResetDataUseTrackingStarted(tab)
                        && DataUseTabUIManager.shouldShowDataUseStartedUI()) {
                    mDataUseSnackbarController.showDataUseTrackingStartedBar();
                } else if (DataUseTabUIManager.shouldShowDataUseEndedUI()
                        && DataUseTabUIManager.shouldShowDataUseEndedSnackbar(
                                   getApplicationContext())
                        && DataUseTabUIManager.checkAndResetDataUseTrackingEnded(tab)) {
                    mDataUseSnackbarController.showDataUseTrackingEndedBar();
                }
            }

            @Override
            public void onShown(Tab tab) {
                setStatusBarColor(tab, tab.getThemeColor());
            }

            @Override
            public void onHidden(Tab tab) {
                mDataUseSnackbarController.dismissDataUseBar();
            }

            @Override
            public void onDestroyed(Tab tab) {
                mDataUseSnackbarController.dismissDataUseBar();
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                postDeferredStartupIfNeeded();
            }

            @Override
            public void onPageLoadFinished(Tab tab) {
                postDeferredStartupIfNeeded();
                OfflinePageUtils.showOfflineSnackbarIfNecessary(ChromeActivity.this, tab);
            }

            @Override
            public void onCrash(Tab tab, boolean sadTabShown) {
                postDeferredStartupIfNeeded();
            }

            @Override
            public void onDidChangeThemeColor(Tab tab, int color) {
                if (getActivityTab() != tab) return;
                setStatusBarColor(tab, color);

                if (getToolbarManager() == null) return;
                getToolbarManager().updatePrimaryColor(color, true);

                ControlContainer controlContainer =
                        (ControlContainer) findViewById(R.id.control_container);
                controlContainer.getToolbarResourceAdapter().invalidate(null);
            }
        };

        if (mAssistStatusHandler != null) {
            mAssistStatusHandler.setTabModelSelector(tabModelSelector);
        }
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        UpdateMenuItemHelper.getInstance().onStart();
        ChromeActivitySessionTracker.getInstance().onStartWithNative();

        if (GSAState.getInstance(this).isGsaAvailable()) {
            mGSAServiceClient = new GSAServiceClient(this);
            mGSAServiceClient.connect();
            createContextReporterIfNeeded();
        } else {
            ContextReporter.reportStatus(ContextReporter.STATUS_GSA_NOT_AVAILABLE);
        }
        mCompositorViewHolder.resetFlags();

        // postDeferredStartupIfNeeded() is called in TabModelSelectorTabObsever#onLoadStopped(),
        // #onPageLoadFinished() and #onCrash(). If we are not actively loading a tab (e.g.
        // in Android N multi-instance, which is created by re-parenting an existing tab),
        // ensure onDeferredStartup() gets called by calling postDeferredStartupIfNeeded() here.
        if (getActivityTab() == null || !getActivityTab().isLoading()) {
            postDeferredStartupIfNeeded();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Tab tab = getActivityTab();
        if (tab == null) return;
        if (hasFocus) {
            tab.onActivityShown();
        } else {
            boolean stopped = ApplicationStatus.getStateForActivity(this) == ActivityState.STOPPED;
            if (stopped) tab.onActivityHidden();
        }
    }

    /**
     * Set device status bar to a given color.
     * @param tab The tab that is currently showing.
     * @param color The color that the status bar should be set to.
     */
    protected void setStatusBarColor(Tab tab, int color) {
        int statusBarColor = (tab != null && tab.isDefaultThemeColor())
                ? Color.BLACK : ColorUtils.getDarkenedColorForStatusBar(color);
        ApiCompatibilityUtils.setStatusBarColor(getWindow(), statusBarColor);
    }

    private void createContextReporterIfNeeded() {
        if (mContextReporter != null || getActivityTab() == null) return;

        final SyncController syncController = SyncController.get(this);
        final ProfileSyncService syncService = ProfileSyncService.get();

        if (syncController != null && syncController.isSyncingUrlsWithKeystorePassphrase()) {
            assert syncService != null;
            mContextReporter = ((ChromeApplication) getApplicationContext()).createGsaHelper()
                    .getContextReporter(this);

            if (mSyncStateChangedListener != null) {
                syncService.removeSyncStateChangedListener(mSyncStateChangedListener);
                mSyncStateChangedListener = null;
            }

            return;
        } else {
            ContextReporter.reportSyncStatus(syncService);
        }

        if (mSyncStateChangedListener == null && syncService != null) {
            mSyncStateChangedListener = new ProfileSyncService.SyncStateChangedListener() {
                @Override
                public void syncStateChanged() {
                    createContextReporterIfNeeded();
                }
            };
            syncService.addSyncStateChangedListener(mSyncStateChangedListener);
        }
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();
        markSessionResume();
        RecordUserAction.record("MobileComeToForeground");

        if (getActivityTab() != null) {
            LaunchMetrics.commitLaunchMetrics(getActivityTab().getWebContents());
        }
        FeatureUtilities.setCustomTabVisible(isCustomTab());
        FeatureUtilities.setIsInMultiWindowMode(
                MultiWindowUtils.getInstance().isInMultiWindowMode(this));
    }

    @Override
    public void onPauseWithNative() {
        RecordUserAction.record("MobileGoToBackground");
        Tab tab = getActivityTab();
        if (tab != null) {
            getTabContentManager().cacheTabThumbnail(tab);
        }
        markSessionEnd();
        super.onPauseWithNative();
    }

    @Override
    public void onStopWithNative() {
        Tab tab = getActivityTab();
        if (tab != null && !hasWindowFocus()) tab.onActivityHidden();
        if (mAppMenuHandler != null) mAppMenuHandler.hideAppMenu();
        if (mGSAServiceClient != null) {
            mGSAServiceClient.disconnect();
            mGSAServiceClient = null;
            if (mSyncStateChangedListener != null) {
                ProfileSyncService syncService = ProfileSyncService.get();
                if (syncService != null) {
                    syncService.removeSyncStateChangedListener(mSyncStateChangedListener);
                }
                mSyncStateChangedListener = null;
            }
        }
        super.onStopWithNative();
    }

    @Override
    public void onNewIntentWithNative(Intent intent) {
        super.onNewIntentWithNative(intent);
        if (mIntentHandler.shouldIgnoreIntent(this, intent)) return;

        mIntentHandler.onNewIntent(this, intent);
    }

    /**
     * @return Whether the given activity contains a CustomTab.
     */
    public boolean isCustomTab() {
        return false;
    }

    /**
     * Overriding methods should queue tasks on the DeferredStartupHandler before or after calling
     * super depending on whether the tasks should run before or after these ones.
     */
    @Override
    protected void onDeferredStartup() {
        super.onDeferredStartup();
        initDeferredStartupForActivity();
        DeferredStartupHandler.getInstance().initDeferredStartupForApp();
        DeferredStartupHandler.getInstance().queueDeferredTasksOnIdleHandler();
    }

    /**
     * All deferred startup tasks that require the activity rather than the app should go here.
     */
    private void initDeferredStartupForActivity() {
        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                if (isActivityDestroyed()) return;
                BeamController.registerForBeam(ChromeActivity.this, new BeamProvider() {
                    @Override
                    public String getTabUrlForBeam() {
                        if (isOverlayVisible()) return null;
                        if (getActivityTab() == null) return null;
                        return getActivityTab().getUrl();
                    }
                });

                UpdateMenuItemHelper.getInstance().checkForUpdateOnBackgroundThread(
                        ChromeActivity.this);
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                if (isActivityDestroyed()) return;
                if (mToolbarManager != null) {
                    String simpleName = getClass().getSimpleName();
                    RecordHistogram.recordTimesHistogram(
                            "MobileStartup.ToolbarInflationTime." + simpleName,
                            mInflateInitialLayoutDurationMs, TimeUnit.MILLISECONDS);
                    mToolbarManager.onDeferredStartup(getOnCreateTimestampMs(), simpleName);
                }

                if (MultiWindowUtils.getInstance().isInMultiWindowMode(ChromeActivity.this)) {
                    onDeferredStartupForMultiWindowMode();
                }
            }
        });
    }

    /**
     * Actions that may be run at some point after startup for Android N multi-window mode. Should
     * be called from #onDeferredStartup() if the activity is in multi-window mode.
     */
    protected void onDeferredStartupForMultiWindowMode() {
        // If the Activity was launched in multi-window mode, record a user action and the screen
        // width.
        recordMultiWindowModeChangedUserAction(true);
        recordMultiWindowModeScreenWidth();
    }

    @Override
    public void onStart() {
        if (AsyncTabParamsManager.hasParamsWithTabToReparent()) {
            mCompositorViewHolder.prepareForTabReparenting();
        }
        super.onStart();
        if (mContextReporter != null) mContextReporter.enable();

        if (mPartnerBrowserRefreshNeeded) {
            mPartnerBrowserRefreshNeeded = false;
            PartnerBrowserCustomizations.initializeAsync(getApplicationContext(),
                    PARTNER_BROWSER_CUSTOMIZATIONS_TIMEOUT_MS);
            PartnerBrowserCustomizations.setOnInitializeAsyncFinished(new Runnable() {
                @Override
                public void run() {
                    if (PartnerBrowserCustomizations.isIncognitoDisabled()) {
                        terminateIncognitoSession();
                    }
                }
            });
        }
        if (mCompositorViewHolder != null) mCompositorViewHolder.onStart();
        mSnackbarManager.onStart();

        // Explicitly call checkAccessibility() so things are initialized correctly when Chrome has
        // been re-started after closing due to the last tab being closed when homepage is enabled.
        // See crbug.com/541546.
        checkAccessibility();

        mScreenWidthDp = getResources().getConfiguration().screenWidthDp;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mContextReporter != null) mContextReporter.disable();

        // We want to refresh partner browser provider every onStart().
        mPartnerBrowserRefreshNeeded = true;
        if (mCompositorViewHolder != null) mCompositorViewHolder.onStop();
        mSnackbarManager.onStop();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onProvideAssistContent(AssistContent outContent) {
        if (getAssistStatusHandler() == null || !getAssistStatusHandler().isAssistSupported()) {
            // No information is provided in incognito mode.
            return;
        }
        Tab tab = getActivityTab();
        if (tab != null && !isInOverviewMode()) {
            outContent.setWebUri(Uri.parse(tab.getUrl()));
        }
    }

    @Override
    public long getOnCreateTimestampMs() {
        return super.getOnCreateTimestampMs();
    }

    /**
     * This cannot be overridden in order to preserve destruction order.  Override
     * {@link #onDestroyInternal()} instead to perform clean up tasks.
     */
    @SuppressLint("NewApi")
    @Override
    protected final void onDestroy() {
        if (mReaderModeManager != null) {
            mReaderModeManager.destroy();
            mReaderModeManager = null;
        }

        if (mContextualSearchManager != null) {
            mContextualSearchManager.destroy();
            mContextualSearchManager = null;
        }

        if (mTabModelSelectorTabObserver != null) {
            mTabModelSelectorTabObserver.destroy();
            mTabModelSelectorTabObserver = null;
        }

        if (mCompositorViewHolder != null) {
            if (mCompositorViewHolder.getLayoutManager() != null) {
                mCompositorViewHolder.getLayoutManager().removeSceneChangeObserver(this);
            }
            mCompositorViewHolder.shutDown();
            mCompositorViewHolder = null;
        }

        onDestroyInternal();

        if (mToolbarManager != null) {
            mToolbarManager.destroy();
            mToolbarManager = null;
        }

        TabModelSelector selector = getTabModelSelector();
        if (selector != null) selector.destroy();

        if (mWindowAndroid != null) {
            mWindowAndroid.destroy();
            mWindowAndroid = null;
        }

        CombinedPolicyProvider.get().removePolicyChangeListener(this);

        if (mTabContentManager != null) {
            mTabContentManager.destroy();
            mTabContentManager = null;
        }

        AccessibilityManager manager = (AccessibilityManager)
                getBaseContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        manager.removeAccessibilityStateChangeListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            manager.removeTouchExplorationStateChangeListener(mTouchExplorationStateChangeListener);
        }

        if (mBlimpClientContextDelegate != null) {
            mBlimpClientContextDelegate.destroy();
            mBlimpClientContextDelegate = null;
        }

        super.onDestroy();
    }

    /**
     * Override this to perform destruction tasks.  Note that by the time this is called, the
     * {@link CompositorViewHolder} will be destroyed, but the {@link WindowAndroid} and
     * {@link TabModelSelector} will not.
     * <p>
     * After returning from this, the {@link TabModelSelector} will be destroyed followed
     * by the {@link WindowAndroid}.
     */
    protected void onDestroyInternal() {
    }

    /**
     * This will handle passing {@link Intent} results back to the {@link WindowAndroid}.  It will
     * return whether or not the {@link WindowAndroid} has consumed the event or not.
     */
    @Override
    public boolean onActivityResultWithNative(int requestCode, int resultCode, Intent intent) {
        if (super.onActivityResultWithNative(requestCode, resultCode, intent)) return true;
        return mWindowAndroid.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (mWindowAndroid != null) {
            if (mWindowAndroid.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
                return;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWindowAndroid.saveInstanceState(outState);
    }

    /**
     * @return The unified manager for all snackbar related operations.
     */
    @Override
    public SnackbarManager getSnackbarManager() {
        return mSnackbarManager;
    }

    protected Drawable getBackgroundDrawable() {
        return new ColorDrawable(
                ApiCompatibilityUtils.getColor(getResources(), R.color.light_background_color));
    }

    @Override
    public void finishNativeInitialization() {
        // The window background color is used as the resizing background color in Android N+
        // multi-window mode. See crbug.com/602366.
        if (Build.VERSION.CODENAME.equals("N") || Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            getWindow().setBackgroundDrawable(new ColorDrawable(
                    ApiCompatibilityUtils.getColor(getResources(),
                            R.color.resizing_background_color)));
        } else {
            removeWindowBackground();
        }
        DownloadManagerService.getDownloadManagerService(
                getApplicationContext()).onActivityLaunched();

        super.finishNativeInitialization();
    }

    /**
     * Called when the accessibility status of this device changes.  This might be triggered by
     * touch exploration or general accessibility status updates.  It is an aggregate of two other
     * accessibility update methods.
     * @see #onAccessibilityModeChanged(boolean)
     * @see #onTouchExplorationStateChanged(boolean)
     * @param enabled Whether or not accessibility and touch exploration are currently enabled.
     */
    protected void onAccessibilityModeChanged(boolean enabled) {
        InfoBarContainer.setIsAllowedToAutoHide(!enabled);
        if (mToolbarManager != null) mToolbarManager.onAccessibilityStatusChanged(enabled);
        if (mContextualSearchManager != null) {
            mContextualSearchManager.onAccessibilityModeChanged(enabled);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item != null && onMenuOrKeyboardAction(item.getItemId(), true)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @VisibleForTesting
    public void setScreenshotCaptureSkippedForTesting(boolean value) {
        mScreenshotCaptureSkippedForTesting = value;
    }

    /**
     * Triggered when the share menu item is selected.
     * This creates and shows a share intent picker dialog or starts a share intent directly.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param isIncognito Whether currentTab is incognito.
     */
    @VisibleForTesting
    public void onShareMenuItemSelected(final boolean shareDirectly, final boolean isIncognito) {
        final Tab currentTab = getActivityTab();
        if (currentTab == null) return;

        PrintingController printingController = PrintingControllerImpl.getInstance();
        if (printingController != null && !currentTab.isNativePage() && !printingController.isBusy()
                && PrefServiceBridge.getInstance().isPrintingEnabled()) {
            PrintShareActivity.enablePrintShareOption(this, new Runnable() {
                @Override
                public void run() {
                    triggerShare(currentTab, shareDirectly, isIncognito);
                }
            });
            return;
        }

        triggerShare(currentTab, shareDirectly, isIncognito);
    }

    private void triggerShare(
            final Tab currentTab, final boolean shareDirectly, boolean isIncognito) {
        final Activity mainActivity = this;
        WebContents webContents = currentTab.getWebContents();

        RecordHistogram.recordBooleanHistogram(
                "OfflinePages.SharedPageWasOffline", currentTab.isOfflinePage());
        boolean canShareOfflinePage = OfflinePageBridge.isPageSharingEnabled();

        // Share an empty blockingUri in place of screenshot file. The file ready notification is
        // sent by onScreenshotReady call below when the file is written.
        final Uri blockingUri = (isIncognito || webContents == null)
                ? null
                : ChromeFileProvider.generateUriAndBlockAccess(mainActivity);
        if (canShareOfflinePage) {
            OfflinePageUtils.shareOfflinePage(shareDirectly, true, mainActivity, null,
                    blockingUri, null, currentTab);
        } else {
            ShareHelper.share(shareDirectly, true, mainActivity, currentTab.getTitle(), null,
                    currentTab.getUrl(), null, blockingUri, null);
            if (shareDirectly) {
                RecordUserAction.record("MobileMenuDirectShare");
            } else {
                RecordUserAction.record("MobileMenuShare");
            }
        }

        if (blockingUri == null) return;

        // Start screenshot capture and notify the provider when it is ready.
        ContentBitmapCallback callback = new ContentBitmapCallback() {
            @Override
            public void onFinishGetBitmap(Bitmap bitmap, int response) {
                ShareHelper.saveScreenshotToDisk(bitmap, mainActivity,
                        new Callback<Uri>() {
                            @Override
                            public void onResult(Uri result) {
                                // Unblock the file once it is saved to disk.
                                ChromeFileProvider.notifyFileReady(blockingUri, result);
                            }
                        });
            }
        };
        if (!mScreenshotCaptureSkippedForTesting) {
            webContents.getContentBitmapAsync(Bitmap.Config.ARGB_8888, 1.f, EMPTY_RECT, callback);
        } else {
            callback.onFinishGetBitmap(null, ReadbackResponse.SURFACE_UNAVAILABLE);
        }
    }

    /**
     * @return Whether the activity is in overview mode.
     */
    public boolean isInOverviewMode() {
        return false;
    }

    /**
     * @return Whether the app menu should be shown.
     */
    public boolean shouldShowAppMenu() {
        // Do not show the menu if Contextual Search or Reader Mode panel is opened.
        // TODO(mdjones): This could potentially be handled by the OverlayPanelManager or as
        // an event if the panels were SceneOverlays.
        if ((mContextualSearchManager != null && mContextualSearchManager.isSearchPanelOpened())
                || (mReaderModeManager != null && mReaderModeManager.isPanelOpened())) {
            return false;
        }

        return true;
    }

    /**
    * Shows the app menu (if possible) for a key press on the keyboard with the correct anchor view
    * chosen depending on device configuration and the visible menu button to the user.
    */
    protected void showAppMenuForKeyboardEvent() {
        if (getAppMenuHandler() == null) return;

        boolean hasPermanentMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();
        getAppMenuHandler().showAppMenu(
                hasPermanentMenuKey ? null : getToolbarManager().getMenuAnchor(), false);
    }

    /**
     * Allows Activities that extend ChromeActivity to do additional hiding/showing of menu items.
     * @param menu Menu that is going to be shown when the menu button is pressed.
     */
    public void prepareMenu(Menu menu) {
    }

    protected IntentHandlerDelegate createIntentHandlerDelegate() {
        return new IntentHandlerDelegate() {
            @Override
            public void processWebSearchIntent(String query) {
                Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
                searchIntent.putExtra(SearchManager.QUERY, query);
                startActivity(searchIntent);
            }

            @Override
            public void processUrlViewIntent(String url, String referer, String headers,
                    TabOpenType tabOpenType, String externalAppId, int tabIdToBringToFront,
                    boolean hasUserGesture, Intent intent) {
            }
        };
    }

    /**
     * @return The resource id that contains how large the top controls are.
     */
    public int getControlContainerHeightResource() {
        return R.dimen.control_container_height;
    }

    @Override
    public final void onAccessibilityStateChanged(boolean enabled) {
        checkAccessibility();
    }

    private void checkAccessibility() {
        onAccessibilityModeChanged(DeviceClassManager.isAccessibilityModeEnabled(this));
    }

    /**
     * @return A casted version of {@link #getApplication()}.
     */
    public ChromeApplication getChromeApplication() {
        return (ChromeApplication) getApplication();
    }

    /**
     * Add the specified tab to bookmarks or allows to edit the bookmark if the specified tab is
     * already bookmarked. If a new bookmark is added, a snackbar will be shown.
     * @param tabToBookmark The tab that needs to be bookmarked.
     */
    public void addOrEditBookmark(final Tab tabToBookmark) {
        if (tabToBookmark == null || tabToBookmark.isFrozen()) {
            return;
        }

        // Defense in depth against the UI being erroneously enabled.
        if (!mToolbarManager.getBookmarkBridge().isEditBookmarksEnabled()) {
            assert false;
            return;
        }

        // Note the use of getUserBookmarkId() over getBookmarkId() here: Managed bookmarks can't be
        // edited. If the current URL is only bookmarked by managed bookmarks, this will return
        // INVALID_BOOKMARK_ID, so the code below will fall back on adding a new bookmark instead.
        // TODO(bauerb): This does not take partner bookmarks into account.
        final long bookmarkId = tabToBookmark.getUserBookmarkId();

        final BookmarkModel bookmarkModel = new BookmarkModel();
        bookmarkModel.runAfterBookmarkModelLoaded(new Runnable() {
            @Override
            public void run() {
                // Gives up the bookmarking if the tab is being destroyed.
                if (!tabToBookmark.isClosing() && tabToBookmark.isInitialized()) {
                    // The BookmarkModel will be destroyed by BookmarkUtils#addOrEditBookmark() when
                    // done.
                    BookmarkId newBookmarkId =
                            BookmarkUtils.addOrEditBookmark(bookmarkId, bookmarkModel,
                                    tabToBookmark, getSnackbarManager(), ChromeActivity.this);
                    // If a new bookmark was created, try to save an offline page for it.
                    if (newBookmarkId != null && newBookmarkId.getId() != bookmarkId) {
                        OfflinePageUtils.saveBookmarkOffline(newBookmarkId, tabToBookmark);
                    }
                } else {
                    bookmarkModel.destroy();
                }
            }
        });
    }

    /**
     * {@link TabModelSelector} no longer implements TabModel.  Use getTabModelSelector() or
     * getCurrentTabModel() depending on your needs.
     * @return The {@link TabModelSelector}, possibly null.
     */
    public TabModelSelector getTabModelSelector() {
        return mTabModelSelector;
    }

    /**
     * Returns the {@link InsetObserverView} that has the current system window
     * insets information.
     * @return The {@link InsetObserverView}, possibly null.
     */
    public InsetObserverView getInsetObserverView() {
        return mInsetObserverView;
    }

    @Override
    public TabCreatorManager.TabCreator getTabCreator(boolean incognito) {
        return incognito ? mIncognitoTabCreator : mRegularTabCreator;
    }

    /**
     * Sets the {@link org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator}s owned by
     * this {@link ChromeActivity}.
     * @param regularTabCreator The creator for normal tabs.
     * @param incognitoTabCreator The creator for incognito tabs.
     */
    protected void setTabCreators(TabCreatorManager.TabCreator regularTabCreator,
            TabCreatorManager.TabCreator incognitoTabCreator) {
        mRegularTabCreator = regularTabCreator;
        mIncognitoTabCreator = incognitoTabCreator;
    }

    /**
     * Convenience method that returns a tab creator for the currently selected {@link TabModel}.
     * @return A tab creator for the currently selected {@link TabModel}.
     */
    public TabCreatorManager.TabCreator getCurrentTabCreator() {
        return getTabCreator(getTabModelSelector().isIncognitoSelected());
    }

    /**
     * Gets the {@link TabContentManager} instance which holds snapshots of the tabs in this model.
     * @return The thumbnail cache, possibly null.
     */
    public TabContentManager getTabContentManager() {
        return mTabContentManager;
    }

    /**
     * Sets the {@link TabContentManager} owned by this {@link ChromeActivity}.
     * @param tabContentManager A {@link TabContentManager} instance.
     */
    protected void setTabContentManager(TabContentManager tabContentManager) {
        mTabContentManager = tabContentManager;
    }

    /**
     * Gets the current (inner) TabModel.  This is a convenience function for
     * getModelSelector().getCurrentModel().  It is *not* equivalent to the former getModel()
     * @return Never null, if modelSelector or its field is uninstantiated returns a
     *         {@link EmptyTabModel} singleton
     */
    public TabModel getCurrentTabModel() {
        TabModelSelector modelSelector = getTabModelSelector();
        if (modelSelector == null) return EmptyTabModel.getInstance();
        return modelSelector.getCurrentModel();
    }

    /**
     * Returns the tab being displayed by this ChromeActivity instance. This allows differentiation
     * between ChromeActivity subclasses that swap between multiple tabs (e.g. ChromeTabbedActivity)
     * and subclasses that only display one Tab (e.g. FullScreenActivity and DocumentActivity).
     *
     * The default implementation grabs the tab currently selected by the TabModel, which may be
     * null if the Tab does not exist or the system is not initialized.
     */
    public Tab getActivityTab() {
        return TabModelUtils.getCurrentTab(getCurrentTabModel());
    }

    /**
     * @return The current ContentViewCore, or null if the tab does not exist or is not showing a
     *         ContentViewCore.
     */
    public ContentViewCore getCurrentContentViewCore() {
        return TabModelUtils.getCurrentContentViewCore(getCurrentTabModel());
    }

    /**
     * @return A {@link WindowAndroid} instance.
     */
    public WindowAndroid getWindowAndroid() {
        return mWindowAndroid;
    }

    /**
     * @return A {@link CompositorViewHolder} instance.
     */
    public CompositorViewHolder getCompositorViewHolder() {
        return mCompositorViewHolder;
    }

    /**
     * Gets the full screen manager.
     * @return The fullscreen manager, possibly null
     */
    public ChromeFullscreenManager getFullscreenManager() {
        return mFullscreenManager;
    }

    /**
     * @return The content offset provider, may be null.
     */
    public ContentOffsetProvider getContentOffsetProvider() {
        return mCompositorViewHolder.getContentOffsetProvider();
    }

    /**
     * @return The {@code ContextualSearchManager} or {@code null} if none;
     */
    public ContextualSearchManager getContextualSearchManager() {
        return mContextualSearchManager;
    }

    /**
     * @return The {@code ReaderModeManager} or {@code null} if none;
     */
    @VisibleForTesting
    public ReaderModeManager getReaderModeManager() {
        return mReaderModeManager;
    }

    /**
     * Create a full-screen manager to be used by this activity.
     * @param controlContainer The control container that will be controlled by the full-screen
     *                         manager.
     * @return A {@link ChromeFullscreenManager} instance that's been created.
     */
    protected ChromeFullscreenManager createFullscreenManager(ControlContainer controlContainer) {
        return new ChromeFullscreenManager(this, controlContainer, getTabModelSelector(),
                getControlContainerHeightResource(), true);
    }

    /**
     * Exits the fullscreen mode, if any. Does nothing if no fullscreen is present.
     * @return Whether the fullscreen mode is currently showing.
     */
    protected boolean exitFullscreenIfShowing() {
        ContentVideoView view = ContentVideoView.getContentVideoView();
        if (view != null && view.getContext() == this) {
            view.exitFullscreen(false);
            return true;
        }
        if (getFullscreenManager() != null
                && getFullscreenManager().getPersistentFullscreenMode()) {
            getFullscreenManager().setPersistentFullscreenMode(false);
            return true;
        }
        return false;
    }

    /**
     * Initializes the {@link CompositorViewHolder} with the relevant content it needs to properly
     * show content on the screen.
     * @param layoutManager             A {@link LayoutManagerDocument} instance.  This class is
     *                                  responsible for driving all high level screen content and
     *                                  determines which {@link Layout} is shown when.
     * @param urlBar                    The {@link View} representing the URL bar (must be
     *                                  focusable) or {@code null} if none exists.
     * @param contentContainer          A {@link ViewGroup} that can have content attached by
     *                                  {@link Layout}s.
     * @param controlContainer          A {@link ControlContainer} instance to draw.
     */
    protected void initializeCompositorContent(
            LayoutManagerDocument layoutManager, View urlBar, ViewGroup contentContainer,
            ControlContainer controlContainer) {
        if (controlContainer != null) {
            mFullscreenManager = createFullscreenManager(controlContainer);
        }

        if (mContextualSearchManager != null) {
            mContextualSearchManager.initialize(contentContainer);
            mContextualSearchManager.setSearchContentViewDelegate(layoutManager);
        }

        layoutManager.addSceneChangeObserver(this);
        mCompositorViewHolder.setLayoutManager(layoutManager);
        mCompositorViewHolder.setFocusable(false);
        mCompositorViewHolder.setControlContainer(controlContainer);
        mCompositorViewHolder.setFullscreenHandler(mFullscreenManager);
        mCompositorViewHolder.setUrlBar(urlBar);
        mCompositorViewHolder.onFinishNativeInitialization(getTabModelSelector(), this,
                getTabContentManager(), contentContainer, mContextualSearchManager,
                mReaderModeManager);

        if (controlContainer != null
                && DeviceClassManager.enableToolbarSwipe()) {
            controlContainer.setSwipeHandler(
                    getCompositorViewHolder().getLayoutManager().getTopSwipeHandler());
        }
    }

    /**
     * Called when the back button is pressed.
     * @return Whether or not the back button was handled.
     */
    protected abstract boolean handleBackPressed();

    @Override
    public void onOrientationChange(int orientation) {
        if (mToolbarManager != null) mToolbarManager.onOrientationChange();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mAppMenuHandler != null) mAppMenuHandler.hideAppMenu();
        super.onConfigurationChanged(newConfig);

        if (newConfig.screenWidthDp != mScreenWidthDp) {
            mScreenWidthDp = newConfig.screenWidthDp;
            final Activity activity = this;

            if (mRecordMultiWindowModeScreenWidthRunnable != null) {
                mHandler.removeCallbacks(mRecordMultiWindowModeScreenWidthRunnable);
            }

            // When exiting Android N multi-window mode, onConfigurationChanged() gets called before
            // isInMultiWindowMode() returns false. Delay to avoid recording width when exiting
            // multi-window mode. This also ensures that we don't record intermediate widths seen
            // only for a brief period of time.
            mRecordMultiWindowModeScreenWidthRunnable = new Runnable() {
                @Override
                public void run() {
                    mRecordMultiWindowModeScreenWidthRunnable = null;
                    if (MultiWindowUtils.getInstance().isInMultiWindowMode(activity)) {
                        recordMultiWindowModeScreenWidth();
                    }
                }
            };
            mHandler.postDelayed(mRecordMultiWindowModeScreenWidthRunnable,
                    RECORD_MULTI_WINDOW_SCREEN_WIDTH_DELAY_MS);
        }
    }

    /**
     * Called by the system when the activity changes from fullscreen mode to multi-window mode
     * and visa-versa.
     * @param isInMultiWindowMode True if the activity is in multi-window mode.
     */
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        recordMultiWindowModeChangedUserAction(isInMultiWindowMode);

        if (!isInMultiWindowMode
                && ApplicationStatus.getStateForActivity(this) == ActivityState.RESUMED) {
            // Start a new UMA session when exiting multi-window mode if the activity is currently
            // resumed. When entering multi-window Android recents gains focus, so ChromeActivity
            // will get a call to onPauseWithNative(), ending the current UMA session. When exiting
            // multi-window, however, if ChromeActivity is resumed it stays in that state.
            markSessionEnd();
            markSessionResume();
            FeatureUtilities.setIsInMultiWindowMode(
                    MultiWindowUtils.getInstance().isInMultiWindowMode(this));
        }

        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    /**
     * Records user actions associated with entering and exiting Android N multi-window mode
     * @param isInMultiWindowMode True if the activity is in multi-window mode.
     */
    protected void recordMultiWindowModeChangedUserAction(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode) {
            RecordUserAction.record("Android.MultiWindowMode.Enter");
        } else {
            RecordUserAction.record("Android.MultiWindowMode.Exit");
        }
    }

    @Override
    public final void onBackPressed() {
        RecordUserAction.record("SystemBack");
        if (mCompositorViewHolder != null) {
            LayoutManager layoutManager = mCompositorViewHolder.getLayoutManager();
            if (layoutManager != null && layoutManager.onBackPressed()) return;
        }

        ContentViewCore contentViewCore = getContentViewCore();
        if (contentViewCore != null && contentViewCore.isSelectActionBarShowing()) {
            contentViewCore.clearSelection();
            return;
        }

        if (mContextualSearchManager != null && mContextualSearchManager.onBackPressed()) return;

        if (handleBackPressed()) return;

        super.onBackPressed();
    }


    private ContentViewCore getContentViewCore() {
        Tab tab = getActivityTab();
        if (tab == null) return null;
        return tab.getContentViewCore();
    }

    @Override
    public void createContextualSearchTab(String searchUrl) {
        Tab currentTab = getActivityTab();
        if (currentTab == null) return;

        TabCreator tabCreator = getTabCreator(currentTab.isIncognito());
        if (tabCreator == null) return;

        tabCreator.createNewTab(
                new LoadUrlParams(searchUrl, PageTransition.LINK),
                TabModel.TabLaunchType.FROM_LINK, getActivityTab());
    }

    /**
     * @return The {@link AppMenuHandler} associated with this activity.
     */
    @VisibleForTesting
    public AppMenuHandler getAppMenuHandler() {
        return mAppMenuHandler;
    }

    /**
     * @return The {@link AppMenuPropertiesDelegate} associated with this activity.
     */
    @VisibleForTesting
    public AppMenuPropertiesDelegate getAppMenuPropertiesDelegate() {
        return mAppMenuPropertiesDelegate;
    }

    /**
     * Callback after UpdateMenuItemHelper#checkForUpdateOnBackgroundThread is complete.
     * @param updateAvailable Whether an update is available.
     */
    public void onCheckForUpdate(boolean updateAvailable) {
        if (UpdateMenuItemHelper.getInstance().shouldShowToolbarBadge(this)) {
            mToolbarManager.getToolbar().showAppMenuUpdateBadge();
            mCompositorViewHolder.requestRender();
        } else {
            mToolbarManager.getToolbar().removeAppMenuUpdateBadge(false);
        }
    }

    /**
     * Handles menu item selection and keyboard shortcuts.
     *
     * @param id The ID of the selected menu item (defined in main_menu.xml) or
     *           keyboard shortcut (defined in values.xml).
     * @param fromMenu Whether this was triggered from the menu.
     * @return Whether the action was handled.
     */
    public boolean onMenuOrKeyboardAction(int id, boolean fromMenu) {
        if (id == R.id.preferences_id) {
            PreferencesLauncher.launchSettingsPage(this, null);
            RecordUserAction.record("MobileMenuSettings");
        } else if (id == R.id.show_menu) {
            showAppMenuForKeyboardEvent();
        }

        if (id == R.id.update_menu_id) {
            UpdateMenuItemHelper.getInstance().onMenuItemClicked(this);
            return true;
        }

        // All the code below assumes currentTab is not null, so return early if it is null.
        final Tab currentTab = getActivityTab();
        if (currentTab == null) {
            return false;
        } else if (id == R.id.forward_menu_id) {
            if (currentTab.canGoForward()) {
                currentTab.goForward();
                RecordUserAction.record("MobileMenuForward");
                RecordUserAction.record("MobileTabClobbered");
            }
        } else if (id == R.id.bookmark_this_page_id) {
            addOrEditBookmark(currentTab);
            RecordUserAction.record("MobileMenuAddToBookmarks");
        } else if (id == R.id.offline_page_id) {
            DownloadUtils.downloadOfflinePage(this, currentTab);
            RecordUserAction.record("MobileMenuDownloadPage");
        } else if (id == R.id.reload_menu_id) {
            if (currentTab.isLoading()) {
                currentTab.stopLoading();
                RecordUserAction.record("MobileMenuStop");
            } else {
                currentTab.reload();
                RecordUserAction.record("MobileMenuReload");
            }
        } else if (id == R.id.info_menu_id) {
            WebsiteSettingsPopup.show(
                    this, currentTab, null, WebsiteSettingsPopup.OPENED_FROM_MENU);
        } else if (id == R.id.open_history_menu_id) {
            currentTab.loadUrl(
                    new LoadUrlParams(UrlConstants.HISTORY_URL, PageTransition.AUTO_TOPLEVEL));
            RecordUserAction.record("MobileMenuHistory");
            StartupMetrics.getInstance().recordOpenedHistory();
        } else if (id == R.id.share_menu_id || id == R.id.direct_share_menu_id) {
            onShareMenuItemSelected(id == R.id.direct_share_menu_id,
                    getCurrentTabModel().isIncognito());
        } else if (id == R.id.print_id) {
            PrintingController printingController = PrintingControllerImpl.getInstance();
            if (printingController != null && !printingController.isBusy()
                    && PrefServiceBridge.getInstance().isPrintingEnabled()) {
                printingController.startPrint(new TabPrinter(currentTab),
                        new PrintManagerDelegateImpl(this));
                RecordUserAction.record("MobileMenuPrint");
            }
        } else if (id == R.id.add_to_homescreen_id) {
            AddToHomescreenManager addToHomescreenManager =
                    new AddToHomescreenManager(this, currentTab);
            addToHomescreenManager.start();
            RecordUserAction.record("MobileMenuAddToHomescreen");
        } else if (id == R.id.request_desktop_site_id) {
            final boolean reloadOnChange = !currentTab.isNativePage();
            final boolean usingDesktopUserAgent = currentTab.getUseDesktopUserAgent();
            currentTab.setUseDesktopUserAgent(!usingDesktopUserAgent, reloadOnChange);
            RecordUserAction.record("MobileMenuRequestDesktopSite");
        } else if (id == R.id.reader_mode_prefs_id) {
            if (currentTab.getWebContents() != null) {
                RecordUserAction.record("DomDistiller_DistilledPagePrefsOpened");
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(this, R.style.AlertDialogTheme);
                builder.setView(DistilledPagePrefsView.create(this));
                builder.show();
            }
        } else if (id == R.id.help_id) {
            // Since reading back the compositor is asynchronous, we need to do the readback
            // before starting the GoogleHelp.
            String helpContextId = HelpAndFeedback.getHelpContextIdFromUrl(
                    this, currentTab.getUrl(), getCurrentTabModel().isIncognito());
            HelpAndFeedback.getInstance(this)
                    .show(this, helpContextId, currentTab.getProfile(), currentTab.getUrl());
            RecordUserAction.record("MobileMenuFeedback");
        } else {
            return false;
        }
        return true;
    }

    /**
     * Tests if VR Shell (the mode displaying browser UI and tab contents in VR) is currently
     * enabled.
     */
    public boolean isVrShellEnabled() {
        return false;
    }

    /**
     * Add a view to the set of views that obscure the content of all tabs for
     * accessibility. As long as this set is nonempty, all tabs should be
     * hidden from the accessibility tree.
     *
     * @param view The view that obscures the contents of all tabs.
     */
    public void addViewObscuringAllTabs(View view) {
        mViewsObscuringAllTabs.add(view);

        Tab tab = getActivityTab();
        if (tab != null) tab.updateAccessibilityVisibility();
    }

    /**
     * Remove a view that previously obscured the content of all tabs.
     *
     * @param view The view that no longer obscures the contents of all tabs.
     */
    public void removeViewObscuringAllTabs(View view) {
        mViewsObscuringAllTabs.remove(view);

        Tab tab = getActivityTab();
        if (tab != null) tab.updateAccessibilityVisibility();
    }

    /**
     * Returns whether or not any views obscure all tabs.
     */
    public boolean isViewObscuringAllTabs() {
        return !mViewsObscuringAllTabs.isEmpty();
    }

    private void markSessionResume() {
        // Start new session for UMA.
        if (mUmaSessionStats == null) {
            mUmaSessionStats = new UmaSessionStats(this);
        }

        mUmaSessionStats.updateMetricsServiceState();
        mUmaSessionStats.startNewSession(getTabModelSelector());
    }

    /**
     * Mark that the UMA session has ended.
     */
    private void markSessionEnd() {
        if (mUmaSessionStats == null) {
            // If you hit this assert, please update crbug.com/172653 on how you got there.
            assert false;
            return;
        }
        // Record session metrics.
        mUmaSessionStats.logMultiWindowStats(windowArea(), displayArea(),
                TabWindowManager.getInstance().getNumberOfAssignedTabModelSelectors());
        mUmaSessionStats.logAndEndSession();
    }

    private int windowArea() {
        Window window = getWindow();
        if (window != null) {
            View view =  window.getDecorView();
            return view.getWidth() * view.getHeight();
        }
        return -1;
    }

    private int displayArea() {
        if (getResources() != null && getResources().getDisplayMetrics() != null) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            return metrics.heightPixels * metrics.widthPixels;
        }
        return -1;
    }

    protected final void postDeferredStartupIfNeeded() {
        if (!mDeferredStartupPosted) {
            mDeferredStartupPosted = true;
            RecordHistogram.recordLongTimesHistogram(
                    "UMA.Debug.EnableCrashUpload.PostDeferredStartUptime2",
                    SystemClock.uptimeMillis() - UmaUtils.getForegroundStartTime(),
                    TimeUnit.MILLISECONDS);
            onDeferredStartup();
        }
    }

    /**
     * Determines whether the ContentView is currently visible and not hidden by an overlay
     * @return true if the ContentView is fully hidden by another view (i.e. the tab stack)
     */
    public boolean isOverlayVisible() {
        return false;
    }

    @Override
    public void terminateIncognitoSession() {}

    @Override
    public void onTabSelectionHinted(int tabId) { }

    @Override
    public void onSceneChange(Layout layout) { }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // See enableHardwareAcceleration()
        if (mSetWindowHWA) {
            mSetWindowHWA = false;
            getWindow().setWindowManager(
                    getWindow().getWindowManager(),
                    getWindow().getAttributes().token,
                    getComponentName().flattenToString(),
                    true /* hardwareAccelerated */);
        }
    }

    /**
     * Adds a {@link Callback} that will be triggered whenever a ContextMenu is closed.
     */
    public void addContextMenuCloseCallback(Callback<Menu> callback) {
        mContextMenuCloseObservers.addObserver(callback);
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        for (Callback<Menu> callback : mContextMenuCloseObservers) {
            callback.onResult(menu);
        }
    }

    /**
     * Removes a {@link Callback} from the list of callbacks that will be triggered when a
     * ContextMenu is closed.
     */
    public void removeContextMenuCloseCallback(Callback<Menu> callback) {
        mContextMenuCloseObservers.removeObserver(callback);
    }

    private boolean shouldDisableHardwareAcceleration() {
        // Low end devices should disable hardware acceleration for memory gains.
        if (SysUtils.isLowEndDevice()) return true;

        // Turning off hardware acceleration reduces crash rates. See http://crbug.com/651918
        // GT-S7580 on JDQ39 accounts for 42% of crashes in libPowerStretch.so on dev and beta.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1
                && Build.MODEL.equals("GT-S7580")) {
            return true;
        }
        // SM-N9005 on JSS15J accounts for 44% of crashes in libPowerStretch.so on stable channel.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR2
                && Build.MODEL.equals("SM-N9005")) {
            return true;
        }
        return false;
    }

    private void enableHardwareAcceleration() {
        // HW acceleration is disabled in the manifest and may be re-enabled here.
        if (!shouldDisableHardwareAcceleration()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

            // When HW acceleration is enabled manually for an activity, child windows (e.g.
            // dialogs) don't inherit HW acceleration state. However, when HW acceleration is
            // enabled in the manifest, child windows do inherit HW acceleration state. That
            // looks like a bug, so I filed b/23036374
            //
            // In the meanwhile the workaround is to call
            //   window.setWindowManager(..., hardwareAccelerated=true)
            // to let the window know that it's HW accelerated. However, since there is no way
            // to know 'appToken' argument until window's view is attached to the window (!!),
            // we have to do the workaround in onAttachedToWindow()
            mSetWindowHWA = true;
        }
    }

    /** @return the theme ID to use. */
    public static int getThemeId() {
        boolean useLowEndTheme =
                SysUtils.isLowEndDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        return (useLowEndTheme ? R.style.MainTheme_LowEnd : R.style.MainTheme);
    }

    private void setLowEndTheme() {
        if (getThemeId() == R.style.MainTheme_LowEnd) setTheme(R.style.MainTheme_LowEnd);
    }

    /**
     * Records UMA histograms for the current screen width. Should only be called when the activity
     * is in Android N multi-window mode.
     */
    protected void recordMultiWindowModeScreenWidth() {
        if (!DeviceFormFactor.isTablet(this)) return;

        RecordHistogram.recordBooleanHistogram(
                "Android.MultiWindowMode.IsTabletScreenWidthBelow600",
                mScreenWidthDp < DeviceFormFactor.MINIMUM_TABLET_WIDTH_DP);

        if (mScreenWidthDp < DeviceFormFactor.MINIMUM_TABLET_WIDTH_DP) {
            RecordHistogram.recordLinearCountHistogram(
                    "Android.MultiWindowMode.TabletScreenWidth", mScreenWidthDp, 1,
                    DeviceFormFactor.MINIMUM_TABLET_WIDTH_DP, 50);
        }
    }
}
