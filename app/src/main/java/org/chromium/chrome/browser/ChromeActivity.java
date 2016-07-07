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
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Gravity;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.BaseSwitches;
import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.appmenu.AppMenu;
import org.chromium.chrome.browser.appmenu.AppMenuHandler;
import org.chromium.chrome.browser.appmenu.AppMenuObserver;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.appmenu.ChromeAppMenuPropertiesDelegate;
import org.chromium.chrome.browser.bookmark.ManageBookmarkActivity;
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
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.dom_distiller.DistilledPagePrefsView;
import org.chromium.chrome.browser.dom_distiller.ReaderModeActivityDelegate;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManager;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarkUtils;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarksModel;
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
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.nfc.BeamController;
import org.chromium.chrome.browser.nfc.BeamProvider;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.printing.TabPrinter;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.snackbar.LoFiBarPopupController;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
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
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.webapps.AddToHomescreenDialog;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.components.dom_distiller.core.Theme;
import org.chromium.content.browser.ContentReadbackHandler;
import org.chromium.content.browser.ContentReadbackHandler.GetBitmapCallback;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.common.ContentSwitches;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.readback_types.ReadbackResponse;
import org.chromium.policy.CombinedPolicyProvider.PolicyChangeListener;
import org.chromium.printing.PrintManagerDelegateImpl;
import org.chromium.printing.PrintingController;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;

import java.util.Locale;
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
        public AppMenuHandler get(Activity activity,
                                  AppMenuPropertiesDelegate delegate, int menuResourceId);
    }

    /**
     * No control container to inflate during initialization.
     */
    static final int NO_CONTROL_CONTAINER = -1;

    /**
     * Prevents race conditions when deleting snapshot database.
     */
    private static final Object SNAPSHOT_DATABASE_LOCK = new Object();
    private static final String SNAPSHOT_DATABASE_REMOVED = "snapshot_database_removed";
    private static final String SNAPSHOT_DATABASE_NAME = "snapshots.db";

    /**
     * Delay in ms after first page load finishes before we initiate deferred startup actions.
     */
    private static final int DEFERRED_STARTUP_DELAY_MS = 1000;

    /**
     * Timeout in ms for reading PartnerBrowserCustomizations provider.
     */
    private static final int PARTNER_BROWSER_CUSTOMIZATIONS_TIMEOUT_MS = 10000;
    private static final String TAG = "cr.ChromeActivity";

    private TabModelSelector mTabModelSelector;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private TabCreatorManager.TabCreator mRegularTabCreator;
    private TabCreatorManager.TabCreator mIncognitoTabCreator;
    private TabContentManager mTabContentManager;
    private UmaSessionStats mUmaSessionStats;
    private ContextReporter mContextReporter;
    protected GSAServiceClient mGSAServiceClient;

    private boolean mPartnerBrowserRefreshNeeded = false;

    protected IntentHandler mIntentHandler;

    /**
     * Whether onDeferredStartup() has been run.
     */
    private boolean mDeferredStartupNotified;

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
    private ContextualSearchManager mContextualSearchManager;
    private ReaderModeActivityDelegate mReaderModeActivityDelegate;
    private SnackbarManager mSnackbarManager;
    private LoFiBarPopupController mLoFiBarPopupController;
    private ChromeAppMenuPropertiesDelegate mAppMenuPropertiesDelegate;
    private AppMenuHandler mAppMenuHandler;
    private ToolbarManager mToolbarManager;
    private BookmarkModelObserver mBookmarkObserver;

    // Time in ms that it took took us to inflate the initial layout
    private long mInflateInitialLayoutDurationMs;

    private final Locale mCurrentLocale = Locale.getDefault();

    private AssistStatusHandler mAssistStatusHandler;

    private static AppMenuHandlerFactory sAppMenuHandlerFactory = new AppMenuHandlerFactory() {
        @Override
        public AppMenuHandler get(
                Activity activity, AppMenuPropertiesDelegate delegate, int menuResourceId) {
            return new AppMenuHandler(activity, delegate, menuResourceId);
        }
    };

    // See enableHardwareAcceleration()
    private boolean mSetWindowHWA;

    /**
     * @param {@link AppMenuHandlerFactory} for creating { mAppMenuHandler}
     */
    @VisibleForTesting
    public static void setAppMenuHandlerFactoryForTesting(AppMenuHandlerFactory factory) {
        sAppMenuHandlerFactory = factory;
    }

    private final static int TAG_LEFT = 0;
    private final static int TAG_RIGHT = 1;
    private final static int TAG_MENU = 2;
    private final static int TAG_HOME = 3;
    private int[] bottomBtnTag = {TAG_LEFT, TAG_RIGHT, TAG_MENU, TAG_HOME};

    private final static int TAG_SETTING = 5;
    private final static int TAG_BOOKMARK = 6;
    private final static int TAG_HISTORY = 7;
    private final static int TAG_LOGOUT = 8;
    private int[] menuBtnTag = {TAG_SETTING, TAG_BOOKMARK, TAG_HISTORY, TAG_LOGOUT};

    private int[] bottomBarBtnImg = {
            R.drawable.btn_m_left,
            R.drawable.btn_m_right,
            R.drawable.btn_m_menu,
            R.drawable.btn_m_home
    };

    private LinearLayout bottomBarLayout;

    private int[] menuBtnImg = {
            R.drawable.btn_m_setting,
            R.drawable.btn_m_bookmark,
            R.drawable.btn_m_history,
            R.drawable.btn_m_logout
    };

    private String[] menuBtnName = {
            "设置",
            "书签",
            "历史",
            "退出"
    };

    private PopupWindow popupWindow;

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();
        ApplicationInitialization.enableFullscreenFlags(
                getResources(), this, getControlContainerHeightResource());
        getWindow().setBackgroundDrawable(getBackgroundDrawable());
    }

    @SuppressLint("NewApi")
    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        mWindowAndroid = ((ChromeApplication) getApplicationContext())
                .createActivityWindowAndroid(this);
        mWindowAndroid.restoreInstanceState(getSavedInstanceState());
        mSnackbarManager = new SnackbarManager(getWindow());
        mLoFiBarPopupController = new LoFiBarPopupController(this, getSnackbarManager());

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
        getChromeApplication().addPolicyChangeListener(this);

        // Set up the animation placeholder to be the SurfaceView. This disables the
        // SurfaceView's 'hole' clipping during animations that are notified to the window.
        mWindowAndroid.setAnimationPlaceholderView(mCompositorViewHolder.getSurfaceView());

        // Inform the WindowAndroid of the keyboard accessory view.
        mWindowAndroid.setKeyboardAccessoryView((ViewGroup) findViewById(R.id.keyboard_accessory));
        initMenuLayout();
        initBottomBar();
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
            setContentView(R.layout.main);
            if (controlContainerLayoutId != NO_CONTROL_CONTAINER) {
                ViewStub toolbarContainerStub =
                        ((ViewStub) findViewById(R.id.control_container_stub));
                toolbarContainerStub.setLayoutResource(controlContainerLayoutId);
                toolbarContainerStub.inflate();
            }
        }
        TraceEvent.end("onCreate->setContentView()");
        mInflateInitialLayoutDurationMs = SystemClock.elapsedRealtime() - begin;

        // Set the status bar color to black by default. This is an optimization for
        // Chrome not to draw under status and navigation bars when we use the default
        // black status bar
        ApiCompatibilityUtils.setStatusBarColor(getWindow(), Color.BLACK);

        mCompositorViewHolder = (CompositorViewHolder) findViewById(R.id.compositor_view_holder);
        mCompositorViewHolder.setRootView(getWindow().getDecorView().getRootView());
    }

    /**
     * 初始化菜单布局
     */
    private void initMenuLayout() {
        LinearLayout linearLayout = new LinearLayout(ChromeActivity.this);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup
                .LayoutParams.WRAP_CONTENT));
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setBackgroundColor(Color.parseColor("#f1f1f1"));
        for (int i = 0; i < menuBtnImg.length; i++) {
            TextView textView = new TextView(ChromeActivity.this);
            textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup
                    .LayoutParams.WRAP_CONTENT, 1.0f));
            textView.setTextColor(Color.parseColor("#7d7d7d"));
            textView.setBackgroundResource(R.drawable.bottom_bar_btn_style);
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(0, 20, 0, 20);
            textView.setText(menuBtnName[i]);
            textView.setCompoundDrawablePadding(12);
            Drawable drawable = getResources().getDrawable(menuBtnImg[i]);
            textView.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            textView.setTag(menuBtnTag[i]);
            textView.setOnClickListener(new MyOnClickListener());

            linearLayout.addView(textView);
        }

        popupWindow = new PopupWindow(linearLayout);
        popupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.update();
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new BitmapDrawable());
        popupWindow.setOutsideTouchable(true);
    }

    private void initBottomBar() {
        bottomBarLayout = (LinearLayout) findViewById(R.id.bottom_bar_layout);
        for (int i = 0; i < bottomBarBtnImg.length; i++) {
            ImageView imageView = new ImageView(ChromeActivity.this);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup
                    .LayoutParams.WRAP_CONTENT, 1.0f));
            imageView.setPadding(0, 20, 0, 20);
            imageView.setBackgroundResource(R.drawable.bottom_bar_btn_style);
            imageView.setImageResource(bottomBarBtnImg[i]);
            imageView.setTag(bottomBtnTag[i]);
            imageView.setOnClickListener(new MyOnClickListener());

            bottomBarLayout.addView(imageView);
        }

        BottomTabBtn bottomTabBtn = new BottomTabBtn(ChromeActivity.this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        layoutParams.gravity = Gravity.CENTER;
        bottomTabBtn.setLayoutParams(layoutParams);
        bottomTabBtn.setPadding(0, 20, 0, 20);
        bottomTabBtn.setBackgroundResource(R.drawable.bottom_bar_btn_style);
        bottomBarLayout.addView(bottomTabBtn);
    }

    private class MyOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch ((int) v.getTag()) {
                case TAG_LEFT:
                    Tab tab = getActivityTab();
                    if (tab == null) {
                        return;
                    }
                    if (tab.canGoBack()) {
                        onBackPressed();
                    }
                    break;
                case TAG_RIGHT:
                    onMenuOrKeyboardAction(R.id.forward_menu_id, false);
                    break;
                case TAG_MENU:
                    int[] location = new int[2];
                    v.getLocationOnScreen(location);
                    popupWindow.getContentView().measure(0, 0);
                    int height = popupWindow.getContentView().getMeasuredHeight();
                    popupWindow.showAtLocation(v, Gravity.NO_GRAVITY, 0, location[1] - height);
                    break;
                case TAG_HOME:
                    Tab currentTab = getActivityTab();
                    if (currentTab == null) {
                        return;
                    } else {
                        if (currentTab.isReady()) {
                            currentTab.loadUrl(new LoadUrlParams("chrome-native://newtab/", PageTransition.HOME_PAGE));
                        }
                    }
                    break;
                case TAG_SETTING:
                    onMenuOrKeyboardAction(R.id.preferences_id, false);
                    updatePopupWindowDisplayStatus();
                    break;
                case TAG_BOOKMARK:
                    onMenuOrKeyboardAction(R.id.all_bookmarks_menu_id, false);
                    updatePopupWindowDisplayStatus();
                    break;
                case TAG_HISTORY:
                    onMenuOrKeyboardAction(R.id.open_history_menu_id, false);
                    updatePopupWindowDisplayStatus();
                    break;
                case TAG_LOGOUT:
                    System.exit(0);
                    updatePopupWindowDisplayStatus();
                    break;
            }
        }
    }

    private void updatePopupWindowDisplayStatus() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
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
                if (!isVisible) {
                    mAppMenuPropertiesDelegate.onMenuDismissed();
                }
            }
        });
    }

    /**
     * @return {@link ToolbarManager} that belongs to this activity.
     */
    protected ToolbarManager getToolbarManager() {
        return mToolbarManager;
    }

    /**
     * @return The resource id for the menu to use in {@link AppMenu}. Default is R.menu.main_menu.
     */
    protected int getAppMenuLayoutId() {
        return R.menu.main_menu;
    }

    /**
     * @return {@link ChromeAppMenuPropertiesDelegate} instance that the {@link AppMenuHandler}
     * should be using in this activity.
     */
    protected ChromeAppMenuPropertiesDelegate createAppMenuPropertiesDelegate() {
        return new ChromeAppMenuPropertiesDelegate(this);
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

        if (isContextualSearchAllowed() && ContextualSearchFieldTrial.isEnabled(this)) {
            mContextualSearchManager = new ContextualSearchManager(this, mWindowAndroid, this);
        }

        if (ReaderModeManager.isEnabled(this)) {
            mReaderModeActivityDelegate = new ReaderModeActivityDelegate(this);
        }

        TraceEvent.end("ChromeActivity:CompositorInitialization");
    }

    /**
     * Sets the {@link TabModelSelector} owned by this {@link ChromeActivity}.
     *
     * @param tabModelSelector A {@link TabModelSelector} instance.
     */
    protected void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;

        if (mTabModelSelectorTabObserver != null) mTabModelSelectorTabObserver.destroy();
        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(tabModelSelector) {
            @Override
            public void didFirstVisuallyNonEmptyPaint(Tab tab) {
                if (!tab.isNativePage() && !tab.isIncognito()
                        && DataReductionProxySettings.getInstance().wasLoFiModeActiveOnMainFrame()
                        && DataReductionProxySettings.getInstance().canUseDataReductionProxy(
                        tab.getUrl())) {
                    if (tab.isHidden()) {
                        TabObserver tabObserver = new EmptyTabObserver() {
                            @Override
                            public void onShown(Tab tab) {
                                mLoFiBarPopupController.showLoFiBar(tab);
                                tab.removeObserver(this);
                            }
                        };
                        tab.addObserver(tabObserver);
                        return;
                    }
                    mLoFiBarPopupController.showLoFiBar(tab);
                }
            }

            @Override
            public void onShown(Tab tab) {
                setStatusBarColor(tab, tab.getThemeColor());
            }

            @Override
            public void onHidden(Tab tab) {
                mLoFiBarPopupController.dismissLoFiBar();
            }

            @Override
            public void onDestroyed(Tab tab) {
                mLoFiBarPopupController.dismissLoFiBar();
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                postDeferredStartupIfNeeded();
                showUpdateInfoBarIfNecessary();
            }

            @Override
            public void onPageLoadFinished(Tab tab) {
                postDeferredStartupIfNeeded();
                showUpdateInfoBarIfNecessary();
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
                getToolbarManager().updatePrimaryColor(color);

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
        getChromeApplication().onStartWithNative();
        Tab tab = getActivityTab();
        if (tab != null) tab.onActivityStart();
        FeatureUtilities.setDocumentModeEnabled(FeatureUtilities.isDocumentMode(this));
        WarmupManager.getInstance().clearWebContentsIfNecessary();

        if (GSAState.getInstance(this).isGsaAvailable()) {
            mGSAServiceClient = new GSAServiceClient(this);
            mGSAServiceClient.connect();
            createContextReporterIfNeeded();
        } else {
            ContextReporter.reportStatus(ContextReporter.STATUS_GSA_NOT_AVAILABLE);
        }
        mCompositorViewHolder.resetFlags();
    }

    /**
     * Set device status bar to a given color.
     *
     * @param tab   The tab that is currently showing.
     * @param color The color that the status bar should be set to.
     */
    protected void setStatusBarColor(Tab tab, int color) {
        int statusBarColor = (tab != null && tab.isDefaultThemeColor())
                ? Color.BLACK : ColorUtils.getDarkenedColorForStatusBar(color);
        ApiCompatibilityUtils.setStatusBarColor(getWindow(), statusBarColor);
    }

    private void createContextReporterIfNeeded() {
        if (mContextReporter != null || getActivityTab() == null) return;

        ProfileSyncService syncService = ProfileSyncService.get();

        if (SyncController.get(this).isSyncingUrlsWithKeystorePassphrase()) {
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

        if (mSyncStateChangedListener == null) {
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

        if (getActivityTab() != null) {
            LaunchMetrics.commitLaunchMetrics(getActivityTab().getWebContents());
        }
        FeatureUtilities.setCustomTabVisible(isCustomTab());
    }

    @Override
    public void onPauseWithNative() {
        markSessionEnd();
        super.onPauseWithNative();
    }

    @Override
    public void onStopWithNative() {
        Tab tab = getActivityTab();
        if (tab != null) tab.onActivityStop();
        if (mAppMenuHandler != null) mAppMenuHandler.hideAppMenu();
        if (mGSAServiceClient != null) {
            mGSAServiceClient.disconnect();
            mGSAServiceClient = null;
            if (mSyncStateChangedListener != null) {
                ProfileSyncService syncService = ProfileSyncService.get();
                syncService.removeSyncStateChangedListener(mSyncStateChangedListener);
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

    @Override
    protected void onDeferredStartup() {
        super.onDeferredStartup();
        boolean crashDumpUploadingDisabled =
                CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_CRASH_DUMP_UPLOAD);
        DeferredStartupHandler.getInstance()
                .onDeferredStartup(getChromeApplication(), crashDumpUploadingDisabled);

        BeamController.registerForBeam(this, new BeamProvider() {
            @Override
            public String getTabUrlForBeam() {
                if (isOverlayVisible()) return null;
                if (getActivityTab() == null) return null;
                return getActivityTab().getUrl();
            }
        });

        getChromeApplication().getUpdateInfoBarHelper().checkForUpdateOnBackgroundThread(this);

        removeSnapshotDatabase();
        if (mToolbarManager != null) {
            String simpleName = getClass().getSimpleName();
            RecordHistogram.recordTimesHistogram("MobileStartup.ToolbarInflationTime." + simpleName,
                    mInflateInitialLayoutDurationMs, TimeUnit.MILLISECONDS);
            mToolbarManager.onDeferredStartup(getOnCreateTimestampMs(), simpleName);
        }
    }

    @Override
    public void onStart() {
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

        // Explicitly call checkAccessibility() so things are initialized correctly when Chrome has
        // been re-started after closing due to the last tab being closed when homepage is enabled.
        // See crbug.com/541546.
        checkAccessibility();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mContextReporter != null) mContextReporter.disable();

        // We want to refresh partner browser provider every onStart().
        mPartnerBrowserRefreshNeeded = true;
        if (mCompositorViewHolder != null) mCompositorViewHolder.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSnackbarManager.dismissAllSnackbars(false);
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
        if (mReaderModeActivityDelegate != null) {
            mReaderModeActivityDelegate.destroy();
            mReaderModeActivityDelegate = null;
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

        getChromeApplication().removePolicyChangeListener(this);

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

        super.onDestroy();

        if (!Locale.getDefault().equals(mCurrentLocale)) {
            // This is a hack to relaunch renderer processes. Killing the main process also kills
            // its dependent (renderer) processes, and Android's activity manager service seems to
            // still relaunch the activity even when process dies in onDestroy().
            // TODO(changwan): Implement a more generic and safe relaunch mechanism, like killing
            //                 dependent processes in onDestroy() and launching them at onCreate().
            Log.w(TAG, "Forcefully killing process...");
            Process.killProcess(Process.myPid());
        }
    }

    /**
     * Override this to perform destruction tasks.  Note that by the time this is called, the
     * {@link CompositorViewHolder} will be destroyed, but the {@link WindowAndroid} and
     * {@link TabModelSelector} will not.
     * <p/>
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

    /**
     * Called when the accessibility status of this device changes.  This might be triggered by
     * touch exploration or general accessibility status updates.  It is an aggregate of two other
     * accessibility update methods.
     *
     * @param enabled Whether or not accessibility and touch exploration are currently enabled.
     * @see #onAccessibilityModeChanged(boolean)
     * @see #onTouchExplorationStateChanged(boolean)
     */
    protected void onAccessibilityModeChanged(boolean enabled) {
        InfoBarContainer.setIsAllowedToAutoHide(!enabled);
        if (mToolbarManager != null) mToolbarManager.onAccessibilityStatusChanged(enabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item != null && onMenuOrKeyboardAction(item.getItemId(), true)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Triggered when the share menu item is selected.
     * This creates and shows a share intent picker dialog or starts a share intent directly.
     *
     * @param currentTab    The {@link Tab} a user is watching.
     * @param windowAndroid The {@link WindowAndroid} currentTab is linked to.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param isIncognito   Whether currentTab is incognito.
     */
    public void onShareMenuItemSelected(final Tab currentTab,
                                        final WindowAndroid windowAndroid, final boolean shareDirectly, boolean
                                                isIncognito) {
        if (currentTab == null) return;

        final Activity mainActivity = this;
        ContentReadbackHandler.GetBitmapCallback bitmapCallback =
                new ContentReadbackHandler.GetBitmapCallback() {
                    @Override
                    public void onFinishGetBitmap(Bitmap bitmap, int reponse) {
                        ShareHelper.share(shareDirectly, mainActivity, currentTab.getTitle(),
                                currentTab.getUrl(), bitmap);
                        if (shareDirectly) {
                            RecordUserAction.record("MobileMenuDirectShare");
                        } else {
                            RecordUserAction.record("MobileMenuShare");
                        }
                    }
                };
        ContentReadbackHandler readbackHandler = getContentReadbackHandler();
        if (isIncognito || readbackHandler == null || windowAndroid == null
                || currentTab.getContentViewCore() == null) {
            bitmapCallback.onFinishGetBitmap(null, ReadbackResponse.SURFACE_UNAVAILABLE);
        } else {
            readbackHandler.getContentBitmapAsync(1, new Rect(), currentTab.getContentViewCore(),
                    Bitmap.Config.ARGB_8888, bitmapCallback);
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
        // Do not show the menu if Contextual Search Panel is opened.
        if (mContextualSearchManager != null && mContextualSearchManager.isSearchPanelOpened()) {
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
     *
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
     * @return Whether the update infobar may be shown.
     */
    public boolean mayShowUpdateInfoBar() {
        return true;
    }

    /**
     * Add the specified tab to bookmarks or allows to edit the bookmark if the specified tab is
     * already bookmarked. If a new bookmark is added, a snackbar will be shown.
     *
     * @param tabToBookmark The tab that needs to be bookmarked.
     */
    public void addOrEditBookmark(final Tab tabToBookmark) {
        if (tabToBookmark == null || tabToBookmark.isFrozen()) {
            return;
        }

        assert mToolbarManager.getBookmarksBridge().isEditBookmarksEnabled();

        // Note the use of getUserBookmarkId() over getBookmarkId() here: Managed bookmarks can't be
        // edited. If the current URL is only bookmarked by managed bookmarks, this will return
        // INVALID_BOOKMARK_ID, so the code below will fall back on adding a new bookmark instead.
        // TODO(bauerb): This does not take partner bookmarks into account.
        final long bookmarkId = tabToBookmark.getUserBookmarkId();

        if (EnhancedBookmarkUtils.isEnhancedBookmarkEnabled()) {
            final EnhancedBookmarksModel bookmarkModel = new EnhancedBookmarksModel();
            if (bookmarkModel.isBookmarkModelLoaded()) {
                EnhancedBookmarkUtils.addOrEditBookmark(bookmarkId, bookmarkModel,
                        tabToBookmark, getSnackbarManager(), ChromeActivity.this);
            } else if (mBookmarkObserver == null) {
                mBookmarkObserver = new BookmarkModelObserver() {
                    @Override
                    public void bookmarkModelChanged() {
                    }

                    @Override
                    public void bookmarkModelLoaded() {
                        EnhancedBookmarkUtils.addOrEditBookmark(bookmarkId, bookmarkModel,
                                tabToBookmark, getSnackbarManager(), ChromeActivity.this);
                        bookmarkModel.removeObserver(this);
                    }
                };
                bookmarkModel.addObserver(mBookmarkObserver);
            }
        } else {
            Intent intent = new Intent(this, ManageBookmarkActivity.class);
            if (bookmarkId == ChromeBrowserProviderClient.INVALID_BOOKMARK_ID) {
                intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_IS_FOLDER, false);
                intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_TITLE,
                        tabToBookmark.getTitle());
                intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_URL, tabToBookmark.getUrl());
            } else {
                intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_IS_FOLDER, false);
                intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_ID, bookmarkId);
            }
            startActivity(intent);
        }
    }

    /**
     * Saves an offline copy for the specified tab that is bookmarked.
     *
     * @param tab The tab that needs to save an offline copy.
     */
    public void saveBookmarkOffline(Tab tab) {
        if (tab == null || tab.isFrozen()) {
            return;
        }

        EnhancedBookmarkUtils.saveBookmarkOffline(tab.getUserBookmarkId(),
                new EnhancedBookmarksModel(), tab, getSnackbarManager(), ChromeActivity.this);
    }

    /**
     * {@link TabModelSelector} no longer implements TabModel.  Use getTabModelSelector() or
     * getCurrentTabModel() depending on your needs.
     *
     * @return The {@link TabModelSelector}, possibly null.
     */
    public TabModelSelector getTabModelSelector() {
        return mTabModelSelector;
    }

    @Override
    public TabCreatorManager.TabCreator getTabCreator(boolean incognito) {
        return incognito ? mIncognitoTabCreator : mRegularTabCreator;
    }

    /**
     * Sets the {@link ChromeTabCreator}s owned by this {@link ChromeActivity}.
     *
     * @param regularTabCreator A {@link ChromeTabCreator} instance.
     */
    public void setTabCreators(TabCreatorManager.TabCreator regularTabCreator,
                               TabCreatorManager.TabCreator incognitoTabCreator) {
        mRegularTabCreator = regularTabCreator;
        mIncognitoTabCreator = incognitoTabCreator;
    }

    /**
     * Convenience method that returns a tab creator for the currently selected {@link TabModel}.
     *
     * @return A tab creator for the currently selected {@link TabModel}.
     */
    public TabCreatorManager.TabCreator getCurrentTabCreator() {
        return getTabCreator(getTabModelSelector().isIncognitoSelected());
    }

    /**
     * Gets the {@link TabContentManager} instance which holds snapshots of the tabs in this model.
     *
     * @return The thumbnail cache, possibly null.
     */
    public TabContentManager getTabContentManager() {
        return mTabContentManager;
    }

    /**
     * Sets the {@link TabContentManager} owned by this {@link ChromeActivity}.
     *
     * @param tabContentManager A {@link TabContentManager} instance.
     */
    protected void setTabContentManager(TabContentManager tabContentManager) {
        mTabContentManager = tabContentManager;
    }

    /**
     * Gets the current (inner) TabModel.  This is a convenience function for
     * getModelSelector().getCurrentModel().  It is *not* equivalent to the former getModel()
     *
     * @return Never null, if modelSelector or its field is uninstantiated returns a
     * {@link EmptyTabModel} singleton
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
     * <p/>
     * The default implementation grabs the tab currently selected by the TabModel, which may be
     * null if the Tab does not exist or the system is not initialized.
     */
    public Tab getActivityTab() {
        return TabModelUtils.getCurrentTab(getCurrentTabModel());
    }

    /**
     * @return The current ContentViewCore, or null if the tab does not exist or is not showing a
     * ContentViewCore.
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
     *
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
     * @return The content readback handler, may be null.
     */
    public ContentReadbackHandler getContentReadbackHandler() {
        return mCompositorViewHolder.getContentReadbackHandler();
    }

    /**
     * Starts asynchronously taking the compositor activity screenshot.
     *
     * @param getBitmapCallback The callback to call once the screenshot is taken, or when failed.
     */
    public void startTakingCompositorActivityScreenshot(final GetBitmapCallback getBitmapCallback) {
        ContentReadbackHandler readbackHandler = getContentReadbackHandler();
        if (readbackHandler == null || getWindowAndroid() == null) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getBitmapCallback.onFinishGetBitmap(null, ReadbackResponse.SURFACE_UNAVAILABLE);
                }
            });
        } else {
            readbackHandler.getCompositorBitmapAsync(getWindowAndroid(), getBitmapCallback);
        }
    }

    /**
     * @return The {@code ContextualSearchManager} or {@code null} if none;
     */
    public ContextualSearchManager getContextualSearchManager() {
        return mContextualSearchManager;
    }

    /**
     * @return A {@link ReaderModeActivityDelegate} instance or {@code null} if reader mode is
     * not enabled.
     */
    public ReaderModeActivityDelegate getReaderModeActivityDelegate() {
        return mReaderModeActivityDelegate;
    }

    /**
     * Create a full-screen manager to be used by this activity.
     *
     * @param controlContainer The control container that will be controlled by the full-screen
     *                         manager.
     * @return A {@link ChromeFullscreenManager} instance that's been created.
     */
    protected ChromeFullscreenManager createFullscreenManager(View controlContainer) {
        return new ChromeFullscreenManager(this, controlContainer, getTabModelSelector(),
                getControlContainerHeightResource(), true);
    }

    /**
     * Exits the fullscreen mode, if any. Does nothing if no fullscreen is present.
     *
     * @return Whether the fullscreen mode is currently showing.
     */
    protected boolean exitFullscreenIfShowing() {
        ContentVideoView view = ContentVideoView.getContentVideoView();
        if (view != null && view.getContext() == this) {
            view.exitFullscreen(false);
            return true;
        }
        if (getFullscreenManager().getPersistentFullscreenMode()) {
            getFullscreenManager().setPersistentFullscreenMode(false);
            return true;
        }
        return false;
    }

    /**
     * Initializes the {@link CompositorViewHolder} with the relevant content it needs to properly
     * show content on the screen.
     *
     * @param layoutManager    A {@link LayoutManagerDocument} instance.  This class is
     *                         responsible for driving all high level screen content and
     *                         determines which {@link Layout} is shown when.
     * @param urlBar           The {@link View} representing the URL bar (must be
     *                         focusable) or {@code null} if none exists.
     * @param contentContainer A {@link ViewGroup} that can have content attached by
     *                         {@link Layout}s.
     * @param controlContainer A {@link ControlContainer} instance to draw.
     */
    protected void initializeCompositorContent(
            LayoutManagerDocument layoutManager, View urlBar, ViewGroup contentContainer,
            ControlContainer controlContainer) {
        if (controlContainer != null) {
            mFullscreenManager = createFullscreenManager((View) controlContainer);
        }

        if (mContextualSearchManager != null) {
            mContextualSearchManager.initialize(contentContainer);
            mContextualSearchManager.setSearchContentViewDelegate(layoutManager);
        }

        if (mReaderModeActivityDelegate != null) {
            mReaderModeActivityDelegate.initialize(contentContainer);
            mReaderModeActivityDelegate.setDynamicResourceLoader(
                    mCompositorViewHolder.getDynamicResourceLoader());
        }

        layoutManager.addSceneChangeObserver(this);
        mCompositorViewHolder.setLayoutManager(layoutManager);
        mCompositorViewHolder.setFocusable(false);
        mCompositorViewHolder.setControlContainer(controlContainer);
        mCompositorViewHolder.setFullscreenHandler(mFullscreenManager);
        mCompositorViewHolder.setUrlBar(urlBar);
        mCompositorViewHolder.onFinishNativeInitialization(getTabModelSelector(), this,
                getTabContentManager(), contentContainer, mContextualSearchManager);

        if (controlContainer != null
                && DeviceClassManager.enableToolbarSwipe(FeatureUtilities.isDocumentMode(this))) {
            controlContainer.setSwipeHandler(
                    getCompositorViewHolder().getLayoutManager().getTopSwipeHandler());
        }
    }

    /**
     * Called when the back button is pressed.
     *
     * @return Whether or not the back button was handled.
     */
    protected abstract boolean handleBackPressed();

    @Override
    public void onOrientationChange(int orientation) {
        if (mContextualSearchManager != null) mContextualSearchManager.onOrientationChange();
        if (mToolbarManager != null) mToolbarManager.onOrientationChange();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mAppMenuHandler != null) mAppMenuHandler.hideAppMenu();
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public final void onBackPressed() {
        if (mCompositorViewHolder != null) {
            LayoutManager layoutManager = mCompositorViewHolder.getLayoutManager();
            boolean layoutConsumed = layoutManager != null && layoutManager.onBackPressed();
            if (layoutConsumed || mContextualSearchManager != null
                    && mContextualSearchManager.onBackPressed()) {
                RecordUserAction.record("SystemBack");
                return;
            }
        }
        if (!isSelectActionBarShowing() && handleBackPressed()) {
            return;
        }
        // This will close the select action bar if it is showing, otherwise close the activity.
        super.onBackPressed();
    }

    private boolean isSelectActionBarShowing() {
        Tab tab = getActivityTab();
        if (tab == null) return false;
        ContentViewCore contentViewCore = tab.getContentViewCore();
        if (contentViewCore == null) return false;
        return contentViewCore.isSelectActionBarShowing();
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
    public ChromeAppMenuPropertiesDelegate getAppMenuPropertiesDelegate() {
        return mAppMenuPropertiesDelegate;
    }

    /**
     * Handles menu item selection and keyboard shortcuts.
     *
     * @param id       The ID of the selected menu item (defined in main_menu.xml) or
     *                 keyboard shortcut (defined in values.xml).
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
        } else if (id == R.id.reload_menu_id) {
            if (currentTab.isLoading()) {
                currentTab.stopLoading();
            } else {
                currentTab.reload();
                RecordUserAction.record("MobileToolbarReload");
            }
        } else if (id == R.id.info_menu_id) {
            WebsiteSettingsPopup.show(this, currentTab.getProfile(), currentTab.getWebContents());
        } else if (id == R.id.open_history_menu_id) {
            currentTab.loadUrl(
                    new LoadUrlParams(UrlConstants.HISTORY_URL, PageTransition.AUTO_TOPLEVEL));
            RecordUserAction.record("MobileMenuHistory");
            StartupMetrics.getInstance().recordOpenedHistory();
        } else if (id == R.id.share_menu_id || id == R.id.direct_share_menu_id) {
            onShareMenuItemSelected(currentTab, getWindowAndroid(),
                    id == R.id.direct_share_menu_id, getCurrentTabModel().isIncognito());
        } else if (id == R.id.print_id) {
            PrintingController printingController = getChromeApplication().getPrintingController();
            if (printingController != null && !printingController.isBusy()
                    && PrefServiceBridge.getInstance().isPrintingEnabled()) {
                printingController.startPrint(new TabPrinter(currentTab),
                        new PrintManagerDelegateImpl(this));
                RecordUserAction.record("MobileMenuPrint");
            }
        } else if (id == R.id.add_to_homescreen_id) {
            AddToHomescreenDialog.show(this, currentTab);
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

    private void markSessionResume() {
        // Start new session for UMA.
        if (mUmaSessionStats == null) {
            mUmaSessionStats = new UmaSessionStats(this);
        }

        mUmaSessionStats.updateMetricsServiceState();
        // In DocumentMode we need the application-level TabModelSelector instead of per
        // activity which only manages a single tab.
        if (FeatureUtilities.isDocumentMode(this)) {
            mUmaSessionStats.startNewSession(
                    ChromeApplication.getDocumentTabModelSelector());
        } else {
            mUmaSessionStats.startNewSession(getTabModelSelector());
        }
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
            View view = window.getDecorView();
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

    private final void postDeferredStartupIfNeeded() {
        if (!mDeferredStartupNotified) {
            // We want to perform deferred startup tasks a short time after the first page
            // load completes, but only when the main thread Looper has become idle.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!mDeferredStartupNotified && !isActivityDestroyed()) {
                        mDeferredStartupNotified = true;
                        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                            @Override
                            public boolean queueIdle() {
                                onDeferredStartup();
                                return false;  // Remove this idle handler.
                            }
                        });
                    }
                }
            }, DEFERRED_STARTUP_DELAY_MS);
        }
    }

    private void showUpdateInfoBarIfNecessary() {
        getChromeApplication().getUpdateInfoBarHelper().showUpdateInfobarIfNecessary(this);
    }

    /**
     * Determines whether the ContentView is currently visible and not hidden by an overlay
     *
     * @return true if the ContentView is fully hidden by another view (i.e. the tab stack)
     */
    public boolean isOverlayVisible() {
        return false;
    }

    /**
     * Deletes the snapshot database which is no longer used because the feature has been removed
     * in Chrome M41.
     */
    private void removeSnapshotDatabase() {
        final Context appContext = getApplicationContext();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                synchronized (SNAPSHOT_DATABASE_LOCK) {
                    SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(appContext);
                    if (!prefs.getBoolean(SNAPSHOT_DATABASE_REMOVED, false)) {
                        deleteDatabase(SNAPSHOT_DATABASE_NAME);
                        prefs.edit().putBoolean(SNAPSHOT_DATABASE_REMOVED, true).apply();
                    }
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void terminateIncognitoSession() {
    }

    @Override
    public void onTabSelectionHinted(int tabId) {
    }

    @Override
    public void onSceneChange(Layout layout) {
    }

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

    private void enableHardwareAcceleration() {
        // HW acceleration is disabled in the manifest. Enable it only on high-end devices.
        if (!SysUtils.isLowEndDevice()) {
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

    /**
     * @return the theme ID to use.
     */
    public static int getThemeId() {
        boolean useLowEndTheme =
                SysUtils.isLowEndDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        return (useLowEndTheme ? R.style.MainTheme_LowEnd : R.style.MainTheme);
    }

    private void setLowEndTheme() {
        if (getThemeId() == R.style.MainTheme_LowEnd) setTheme(R.style.MainTheme_LowEnd);
    }
}
