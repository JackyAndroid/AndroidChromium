// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.TabLoadStatus;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.WindowDelegate;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.appmenu.AppMenuHandler;
import org.chromium.chrome.browser.appmenu.AppMenuObserver;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge;
import org.chromium.chrome.browser.compositor.Invalidator;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.SceneChangeObserver;
import org.chromium.chrome.browser.fullscreen.BrowserStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.ntp.IncognitoNewTabPage;
import org.chromium.chrome.browser.ntp.NativePageFactory;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.omnibox.UrlFocusChangeListener;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager.HomepageStateListener;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrl;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.toolbar.ActionModeController.ActionBarDelegate;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarManager;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarObserver;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Contains logic for managing the toolbar visual component.  This class manages the interactions
 * with the rest of the application to ensure the toolbar is always visually up to date.
 */
public class ToolbarManager implements ToolbarTabController, UrlFocusChangeListener {

    /**
     * Handle UI updates of menu icons. Only applicable for phones.
     */
    public interface MenuDelegatePhone {

        /**
         * Called when current tab's loading status changes.
         *
         * @param isLoading Whether the current tab is loading.
         */
        public void updateReloadButtonState(boolean isLoading);
    }

    /**
     * The number of ms to wait before reporting to UMA omnibox interaction metrics.
     */
    private static final int RECORD_UMA_PERFORMANCE_METRICS_DELAY_MS = 30000;

    private static final int MIN_FOCUS_TIME_FOR_UMA_HISTOGRAM_MS = 1000;
    private static final int MAX_FOCUS_TIME_FOR_UMA_HISTOGRAM_MS = 30000;

    /**
     * The minimum load progress that can be shown when a page is loading.  This is not 0 so that
     * it's obvious to the user that something is attempting to load.
     */
    public static final int MINIMUM_LOAD_PROGRESS = 5;

    private final ToolbarLayout mToolbar;
    private final ToolbarControlContainer mControlContainer;

    private TabModelSelector mTabModelSelector;
    private TabModelSelectorObserver mTabModelSelectorObserver;
    private TabModelObserver mTabModelObserver;
    private MenuDelegatePhone mMenuDelegatePhone;
    private final ToolbarModelImpl mToolbarModel;
    private Profile mCurrentProfile;
    private BookmarkBridge mBookmarkBridge;
    private TemplateUrlServiceObserver mTemplateUrlObserver;
    private final LocationBar mLocationBar;
    private FindToolbarManager mFindToolbarManager;
    private final AppMenuPropertiesDelegate mAppMenuPropertiesDelegate;

    private final TabObserver mTabObserver;
    private final BookmarkBridge.BookmarkModelObserver mBookmarksObserver;
    private final List<FindToolbarObserver> mFindToolbarObservers;
    private final OverviewModeObserver mOverviewModeObserver;
    private final SceneChangeObserver mSceneChangeObserver;
    private final ActionBarDelegate mActionBarDelegate;
    private final ActionModeController mActionModeController;
    private final LoadProgressSimulator mLoadProgressSimulator;

    private BrowserStateBrowserControlsVisibilityDelegate mControlsVisibilityDelegate;
    private int mFullscreenFocusToken = FullscreenManager.INVALID_TOKEN;
    private int mFullscreenFindInPageToken = FullscreenManager.INVALID_TOKEN;
    private int mFullscreenMenuToken = FullscreenManager.INVALID_TOKEN;

    private int mPreselectedTabId = Tab.INVALID_TAB_ID;

    private boolean mNativeLibraryReady;
    private boolean mTabRestoreCompleted;

    private AppMenuButtonHelper mAppMenuButtonHelper;

    private HomepageStateListener mHomepageStateListener;

    private boolean mInitializedWithNative;

    private boolean mShouldUpdateTabCount = true;
    private boolean mShouldUpdateToolbarPrimaryColor = true;

    /**
     * Creates a ToolbarManager object.
     * @param controlContainer The container of the toolbar.
     * @param menuHandler The handler for interacting with the menu.
     */
    public ToolbarManager(final ChromeActivity activity,
            ToolbarControlContainer controlContainer, final AppMenuHandler menuHandler,
            AppMenuPropertiesDelegate appMenuPropertiesDelegate,
            Invalidator invalidator) {
        mActionBarDelegate = new ActionModeController.ActionBarDelegate() {
            @Override
            public void setControlTopMargin(int margin) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                        mControlContainer.getLayoutParams();
                lp.topMargin = margin;
                mControlContainer.setLayoutParams(lp);
            }

            @Override
            public int getControlTopMargin() {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                        mControlContainer.getLayoutParams();
                return lp.topMargin;
            }

            @Override
            public ActionBar getSupportActionBar() {
                return activity.getSupportActionBar();
            }

            @Override
            public void setActionBarBackgroundVisibility(boolean visible) {
                int visibility = visible ? View.VISIBLE : View.GONE;
                activity.findViewById(R.id.action_bar_black_background).setVisibility(visibility);
                // TODO(tedchoc): Add support for changing the color based on the brand color.
            }
        };

        mToolbarModel = new ToolbarModelImpl();
        mControlContainer = controlContainer;
        assert mControlContainer != null;

        mToolbar = (ToolbarLayout) controlContainer.findViewById(R.id.toolbar);

        mToolbar.setPaintInvalidator(invalidator);

        mActionModeController = new ActionModeController(activity, mActionBarDelegate);
        mActionModeController.setCustomSelectionActionModeCallback(
                new ToolbarActionModeCallback());
        mActionModeController.setTabStripHeight(mToolbar.getTabStripHeight());

        MenuDelegatePhone menuDelegate = new MenuDelegatePhone() {
            @Override
            public void updateReloadButtonState(boolean isLoading) {
                if (mAppMenuPropertiesDelegate != null) {
                    mAppMenuPropertiesDelegate.loadingStateChanged(isLoading);
                    menuHandler.menuItemContentChanged(R.id.icon_row_menu_id);
                }
            }
        };
        setMenuDelegatePhone(menuDelegate);

        mLocationBar = mToolbar.getLocationBar();
        mLocationBar.setToolbarDataProvider(mToolbarModel);
        mLocationBar.setUrlFocusChangeListener(this);
        mLocationBar.setDefaultTextEditActionModeCallback(
                mActionModeController.getActionModeCallback());
        mLocationBar.initializeControls(
                new WindowDelegate(activity.getWindow()),
                mActionModeController.getActionBarDelegate(),
                activity.getWindowAndroid());

        setMenuHandler(menuHandler);
        mToolbar.initialize(mToolbarModel, this, mAppMenuButtonHelper);

        mAppMenuPropertiesDelegate = appMenuPropertiesDelegate;

        mHomepageStateListener = new HomepageStateListener() {
            @Override
            public void onHomepageStateUpdated() {
                mToolbar.onHomeButtonUpdate(
                        HomepageManager.isHomepageEnabled(mToolbar.getContext()));
            }
        };
        HomepageManager.getInstance(mToolbar.getContext()).addListener(mHomepageStateListener);

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                refreshSelectedTab();
                updateTabCount();
            }

            @Override
            public void onTabStateInitialized() {
                mTabRestoreCompleted = true;
                handleTabRestoreCompleted();
            }
        };

        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, TabLaunchType type) {
                updateTabCount();
            }

            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                mPreselectedTabId = Tab.INVALID_TAB_ID;
                refreshSelectedTab();
            }

            @Override
            public void tabClosureUndone(Tab tab) {
                updateTabCount();
                refreshSelectedTab();
            }

            @Override
            public void didCloseTab(int tabId, boolean incognito) {
                mLocationBar.setTitleToPageTitle();
                updateTabCount();
                refreshSelectedTab();
            }

            @Override
            public void tabPendingClosure(Tab tab) {
                updateTabCount();
                refreshSelectedTab();
            }

            @Override
            public void allTabsPendingClosure(List<Integer> tabIds) {
                updateTabCount();
                refreshSelectedTab();
            }

            @Override
            public void tabRemoved(Tab tab) {
                updateTabCount();
                refreshSelectedTab();
            }
        };

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onSSLStateUpdated(Tab tab) {
                super.onSSLStateUpdated(tab);
                assert tab == mToolbarModel.getTab();
                mLocationBar.updateSecurityIcon(tab.getSecurityLevel());
            }

            @Override
            public void onWebContentsInstantSupportDisabled() {
                mLocationBar.setUrlToPageUrl();
            }

            @Override
            public void onDidNavigateMainFrame(Tab tab, String url, String baseUrl,
                    boolean isNavigationToDifferentPage, boolean isFragmentNavigation,
                    int statusCode) {
                if (isNavigationToDifferentPage) {
                    mToolbar.onNavigatedToDifferentPage();
                }
            }

            @Override
            public void onTitleUpdated(Tab tab) {
                mLocationBar.setTitleToPageTitle();
            }

            @Override
            public void onUrlUpdated(Tab tab) {
                // Update the SSL security state as a result of this notification as it will
                // sometimes be the only update we receive.
                updateTabLoadingState(true);

                // A URL update is a decent enough indicator that the toolbar widget is in
                // a stable state to capture its bitmap for use in fullscreen.
                mControlContainer.setReadyForBitmapCapture(true);
            }

            @Override
            public void onCrash(Tab tab, boolean sadTabShown) {
                updateTabLoadingState(false);
                updateButtonStatus();
                finishLoadProgress(false);
            }

            @Override
            public void onLoadStarted(Tab tab, boolean toDifferentDocument) {
                if (!toDifferentDocument) return;
                updateButtonStatus();
                updateTabLoadingState(true);
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                if (!toDifferentDocument) return;
                updateTabLoadingState(true);

                // If we made some progress, fast-forward to complete, otherwise just dismiss any
                // MINIMUM_LOAD_PROGRESS that had been set.
                if (tab.getProgress() > MINIMUM_LOAD_PROGRESS && tab.getProgress() < 100) {
                    updateLoadProgress(100);
                }
                finishLoadProgress(true);
            }

            @Override
            public void onLoadProgressChanged(Tab tab, int progress) {
                if (NativePageFactory.isNativePageUrl(tab.getUrl(), tab.isIncognito())) return;

                // TODO(kkimlabs): Investigate using float progress all the way up to Blink.
                updateLoadProgress(progress);
            }

            @Override
            public void onContentChanged(Tab tab) {
                mToolbar.onTabContentViewChanged();
                if (shouldShowCusrsorInLocationBar()) {
                    mToolbar.getLocationBar().showUrlBarCursorWithoutFocusAnimations();
                }
            }

            @Override
            public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
                if (!didStartLoad) return;
                mLocationBar.setUrlToPageUrl();
                mLocationBar.updateSecurityIcon(tab.getSecurityLevel());
                if (didFinishLoad) {
                    mLoadProgressSimulator.start();
                }
            }

            @Override
            public void onDidStartNavigationToPendingEntry(Tab tab, String url) {
                // Update URL as soon as it becomes available when it's a new tab.
                // But we want to update only when it's a new tab. So we check whether the current
                // navigation entry is initial, meaning whether it has the same target URL as the
                // initial URL of the tab.
                WebContents webContents = tab.getWebContents();
                if (webContents == null) return;
                NavigationController navigationController = webContents.getNavigationController();
                if (navigationController == null) return;
                if (navigationController.isInitialNavigation()) {
                    mLocationBar.setUrlToPageUrl();
                }
            }

            @Override
            public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
                NewTabPage ntp = mToolbarModel.getNewTabPageForCurrentTab();
                if (ntp == null) return;
                if (!NewTabPage.isNTPUrl(params.getUrl())
                        && loadType != TabLoadStatus.PAGE_LOAD_FAILED) {
                    ntp.setUrlFocusAnimationsDisabled(true);
                    mToolbar.onTabOrModelChanged();
                }
            }

            private boolean hasPendingNonNtpNavigation(Tab tab) {
                WebContents webContents = tab.getWebContents();
                if (webContents == null) return false;

                NavigationController navigationController = webContents.getNavigationController();
                if (navigationController == null) return false;

                NavigationEntry pendingEntry = navigationController.getPendingEntry();
                if (pendingEntry == null) return false;

                return !NewTabPage.isNTPUrl(pendingEntry.getUrl());
            }

            @Override
            public void onDidFailLoad(Tab tab, boolean isProvisionalLoad, boolean isMainFrame,
                    int errorCode, String description, String failingUrl) {
                NewTabPage ntp = mToolbarModel.getNewTabPageForCurrentTab();
                if (ntp == null) return;

                // If the load failed due to a different navigation, there is no need to reset the
                // location bar animations.
                if (isProvisionalLoad && isMainFrame && !hasPendingNonNtpNavigation(tab)) {
                    ntp.setUrlFocusAnimationsDisabled(false);
                    mToolbar.onTabOrModelChanged();
                    if (mToolbar.getProgressBar() != null) mToolbar.getProgressBar().finish(false);
                }
            }

            @Override
            public void onContextualActionBarVisibilityChanged(Tab tab, boolean visible) {
                if (visible) RecordUserAction.record("MobileActionBarShown");
                ActionBar actionBar = mActionBarDelegate.getSupportActionBar();
                if (!visible && actionBar != null) actionBar.hide();
                if (DeviceFormFactor.isTablet(activity)) {
                    if (visible) {
                        mActionModeController.startShowAnimation();
                    } else {
                        mActionModeController.startHideAnimation();
                    }
                }
            }

            @Override
            public void onDidStartProvisionalLoadForFrame(
                    Tab tab, boolean isMainFrame, String validatedUrl) {
                // This event is used as the primary trigger for the progress bar because it
                // is the earliest indication that a load has started for a particular frame. In
                // the case of the progress bar, it should only traverse the screen a single time
                // per page load. So if this event states the main frame has started loading the
                // progress bar is started.
                if (!isMainFrame) return;

                if (NativePageFactory.isNativePageUrl(validatedUrl, tab.isIncognito())) {
                    finishLoadProgress(false);
                    return;
                }

                mLoadProgressSimulator.cancel();
                startLoadProgress();
                updateLoadProgress(tab.getProgress());
            }
        };

        mBookmarksObserver = new BookmarkBridge.BookmarkModelObserver() {
            @Override
            public void bookmarkModelChanged() {
                updateBookmarkButtonStatus();
            }
        };

        mFindToolbarObservers = new ArrayList<>();
        addFindToolbarObserver(
                new FindToolbarObserver() {
                    @Override
                    public void onFindToolbarShown() {
                        mToolbar.handleFindToolbarStateChange(true);
                        if (mControlsVisibilityDelegate != null) {
                            mFullscreenFindInPageToken = mControlsVisibilityDelegate
                                    .showControlsPersistentAndClearOldToken(
                                            mFullscreenFindInPageToken);
                        }
                    }

                    @Override
                    public void onFindToolbarHidden() {
                        mToolbar.handleFindToolbarStateChange(false);
                        if (mControlsVisibilityDelegate != null) {
                            mControlsVisibilityDelegate.hideControlsPersistent(
                                    mFullscreenFindInPageToken);
                            mFullscreenFindInPageToken = FullscreenManager.INVALID_TOKEN;
                        }
                    }
                });

        mOverviewModeObserver = new EmptyOverviewModeObserver() {
            @Override
            public void onOverviewModeStartedShowing(boolean showToolbar) {
                mToolbar.setTabSwitcherMode(true, showToolbar, false);
                updateButtonStatus();
            }

            @Override
            public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {
                mToolbar.setTabSwitcherMode(false, showToolbar, delayAnimation);
                updateButtonStatus();
            }

            @Override
            public void onOverviewModeFinishedHiding() {
                mToolbar.onTabSwitcherTransitionFinished();
            }
        };

        mSceneChangeObserver = new SceneChangeObserver() {
            @Override
            public void onTabSelectionHinted(int tabId) {
                mPreselectedTabId = tabId;
                refreshSelectedTab();

                if (mToolbar.setForceTextureCapture(true)) {
                    mControlContainer.invalidateBitmap();
                }
            }

            @Override
            public void onSceneChange(Layout layout) {
                mToolbar.setContentAttached(layout.shouldDisplayContentOverlay());
            }
        };

        mLoadProgressSimulator = new LoadProgressSimulator(this);
    }

    /**
     * Add an observer to the FindToolbarManager.
     * @param observer The observer to add.
     */
    public void addFindToolbarObserver(FindToolbarObserver observer) {
        // Keep a list of all added observers in case the manager does not exist yet.
        mFindToolbarObservers.add(observer);
        if (mFindToolbarManager != null) {
            mFindToolbarManager.addObserver(observer);
        }
    }

    /**
     * Remove an observer from the FindToolbarManager.
     * @param observer The observer to remove.
     */
    public void removeFindToolbarObserver(FindToolbarObserver observer) {
        mFindToolbarObservers.remove(observer);
        if (mFindToolbarManager != null) {
            mFindToolbarManager.removeObserver(observer);
        }
    }

    /**
     * Initialize the manager with the components that had native initialization dependencies.
     * <p>
     * Calling this must occur after the native library have completely loaded.
     *
     * @param tabModelSelector           The selector that handles tab management.
     * @param controlsVisibilityDelegate The delegate to handle visibility of browser controls.
     * @param findToolbarManager         The manager for find in page.
     * @param overviewModeBehavior       The overview mode manager.
     * @param layoutDriver               A {@link LayoutManager} instance used to watch for scene
     *                                   changes.
     */
    public void initializeWithNative(TabModelSelector tabModelSelector,
            BrowserStateBrowserControlsVisibilityDelegate controlsVisibilityDelegate,
            final FindToolbarManager findToolbarManager,
            final OverviewModeBehavior overviewModeBehavior,
            final LayoutManager layoutDriver,
            OnClickListener tabSwitcherClickHandler,
            OnClickListener newTabClickHandler,
            OnClickListener bookmarkClickHandler,
            OnClickListener customTabsBackClickHandler) {
        assert !mInitializedWithNative;
        mTabModelSelector = tabModelSelector;

        mToolbar.getLocationBar().updateVisualsForState();
        mToolbar.getLocationBar().setUrlToPageUrl();
        mToolbar.setBrowserControlsVisibilityDelegate(controlsVisibilityDelegate);
        mToolbar.setOnTabSwitcherClickHandler(tabSwitcherClickHandler);
        mToolbar.setOnNewTabClickHandler(newTabClickHandler);
        mToolbar.setBookmarkClickHandler(bookmarkClickHandler);
        mToolbar.setCustomTabCloseClickHandler(customTabsBackClickHandler);
        mToolbar.setLayoutUpdateHost(layoutDriver);

        mToolbarModel.initializeWithNative();

        mToolbar.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewDetachedFromWindow(View v) {
                Context context = mToolbar.getContext();
                HomepageManager.getInstance(context).removeListener(mHomepageStateListener);
                mTabModelSelector.removeObserver(mTabModelSelectorObserver);
                for (TabModel model : mTabModelSelector.getModels()) {
                    model.removeObserver(mTabModelObserver);
                }
                if (mBookmarkBridge != null) {
                    mBookmarkBridge.destroy();
                    mBookmarkBridge = null;
                }
                if (mTemplateUrlObserver != null) {
                    TemplateUrlService.getInstance().removeObserver(mTemplateUrlObserver);
                    mTemplateUrlObserver = null;
                }

                // Remove all previously attached observers from the FindToolbarManager.
                if (mFindToolbarManager != null) {
                    for (FindToolbarObserver observer : mFindToolbarObservers) {
                        mFindToolbarManager.removeObserver(observer);
                    }
                }

                if (overviewModeBehavior != null) {
                    overviewModeBehavior.removeOverviewModeObserver(mOverviewModeObserver);
                }
                if (layoutDriver != null) {
                    layoutDriver.removeSceneChangeObserver(mSceneChangeObserver);
                }
            }

            @Override
            public void onViewAttachedToWindow(View v) {
                // As we have only just registered for notifications, any that were sent prior to
                // this may have been missed.
                // Calling refreshSelectedTab in case we missed the initial selection notification.
                refreshSelectedTab();
            }
        });

        mFindToolbarManager = findToolbarManager;

        assert controlsVisibilityDelegate != null;
        mControlsVisibilityDelegate = controlsVisibilityDelegate;

        mNativeLibraryReady = false;

        // Add observers to the FindToolbarManager.
        if (mFindToolbarManager != null) {
            for (FindToolbarObserver observer : mFindToolbarObservers) {
                mFindToolbarManager.addObserver(observer);
            }
        }

        if (overviewModeBehavior != null) {
            overviewModeBehavior.addOverviewModeObserver(mOverviewModeObserver);
        }
        if (layoutDriver != null) layoutDriver.addSceneChangeObserver(mSceneChangeObserver);

        onNativeLibraryReady();
        mInitializedWithNative = true;
    }

    /**
     * @return The bookmarks bridge.
     */
    public BookmarkBridge getBookmarkBridge() {
        return mBookmarkBridge;
    }

    /**
     * @return The toolbar interface that this manager handles.
     */
    public Toolbar getToolbar() {
        return mToolbar;
    }

    /**
     * @return The controller for toolbar action mode.
     */
    public ActionModeController getActionModeController() {
        return mActionModeController;
    }

    /**
     * @return Whether the UI has been initialized.
     */
    public boolean isInitialized() {
        return mInitializedWithNative;
    }

    /**
     * @return The view that the pop up menu should be anchored to on the UI.
     */
    public View getMenuAnchor() {
        return mToolbar.getMenuButtonWrapper();
    }

    /**
     * Sets/adds a custom action button to the {@link Toolbar} if it is supported. If there is
     * already an action button, update the button instead.
     * @param drawable The {@link Drawable} to use as the background for the button.
     * @param description The content description for the custom action button.
     * @param listener The {@link OnClickListener} to use for clicks to the button.
     */
    public void setCustomActionButton(Drawable drawable, String description,
            OnClickListener listener) {
        mToolbar.setCustomActionButton(drawable, description, listener);
    }

    /**
     * Call to tear down all of the toolbar dependencies.
     */
    public void destroy() {
        Tab currentTab = mToolbarModel.getTab();
        if (currentTab != null) currentTab.removeObserver(mTabObserver);
        mFindToolbarObservers.clear();
        mToolbar.destroy();
    }

    /**
     * Called when the orientation of the activity has changed.
     */
    public void onOrientationChange() {
        mActionModeController.showControlsOnOrientationChange();
    }

    /**
     * Called when the accessibility enabled state changes.
     * @param enabled Whether accessibility is enabled.
     */
    public void onAccessibilityStatusChanged(boolean enabled) {
        mToolbar.onAccessibilityStatusChanged(enabled);
    }

    private void registerTemplateUrlObserver() {
        final TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
        assert mTemplateUrlObserver == null;
        mTemplateUrlObserver = new TemplateUrlServiceObserver() {
            private TemplateUrl mSearchEngine =
                    templateUrlService.getDefaultSearchEngineTemplateUrl();

            @Override
            public void onTemplateURLServiceChanged() {
                TemplateUrl searchEngine = templateUrlService.getDefaultSearchEngineTemplateUrl();
                if ((mSearchEngine == null && searchEngine == null)
                        || (mSearchEngine != null && mSearchEngine.equals(searchEngine))) {
                    return;
                }

                mSearchEngine = searchEngine;
                mToolbar.onDefaultSearchEngineChanged();
            }
        };
        templateUrlService.addObserver(mTemplateUrlObserver);
    }

    private void onNativeLibraryReady() {
        mNativeLibraryReady = true;
        mToolbar.onNativeLibraryReady();

        final TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
        TemplateUrlService.LoadListener mTemplateServiceLoadListener =
                new TemplateUrlService.LoadListener() {
                    @Override
                    public void onTemplateUrlServiceLoaded() {
                        registerTemplateUrlObserver();
                        templateUrlService.unregisterLoadListener(this);
                    }
                };
        templateUrlService.registerLoadListener(mTemplateServiceLoadListener);
        if (templateUrlService.isLoaded()) {
            mTemplateServiceLoadListener.onTemplateUrlServiceLoaded();
        } else {
            templateUrlService.load();
        }

        mTabModelSelector.addObserver(mTabModelSelectorObserver);
        for (TabModel model : mTabModelSelector.getModels()) model.addObserver(mTabModelObserver);

        refreshSelectedTab();
        if (mTabModelSelector.isTabStateInitialized()) mTabRestoreCompleted = true;
        handleTabRestoreCompleted();
    }

    private void handleTabRestoreCompleted() {
        if (!mTabRestoreCompleted || !mNativeLibraryReady) return;
        mToolbar.onStateRestored();
        updateTabCount();
    }

    /**
     * Sets the handler for any special case handling related with the menu button.
     * @param menuHandler The handler to be used.
     */
    private void setMenuHandler(AppMenuHandler menuHandler) {
        menuHandler.addObserver(new AppMenuObserver() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                if (mControlsVisibilityDelegate == null) return;
                if (isVisible) {
                    mFullscreenMenuToken =
                            mControlsVisibilityDelegate.showControlsPersistentAndClearOldToken(
                                    mFullscreenMenuToken);
                } else {
                    mControlsVisibilityDelegate.hideControlsPersistent(mFullscreenMenuToken);
                    mFullscreenMenuToken = FullscreenManager.INVALID_TOKEN;
                }
            }
        });
        mAppMenuButtonHelper = new AppMenuButtonHelper(menuHandler);
        mAppMenuButtonHelper.setOnAppMenuShownListener(new Runnable() {
            @Override
            public void run() {
                RecordUserAction.record("MobileToolbarShowMenu");
            }
        });
        mLocationBar.setMenuButtonHelper(mAppMenuButtonHelper);
    }

    /**
     * Set the delegate that will handle updates from toolbar driven state changes.
     * @param menuDelegatePhone The menu delegate to be updated (only applicable to phones).
     */
    public void setMenuDelegatePhone(MenuDelegatePhone menuDelegatePhone) {
        mMenuDelegatePhone = menuDelegatePhone;
    }

    @Override
    public boolean back() {
        Tab tab = mToolbarModel.getTab();
        if (tab != null && tab.canGoBack()) {
            tab.goBack();
            updateButtonStatus();
            return true;
        }
        return false;
    }

    @Override
    public boolean forward() {
        Tab tab = mToolbarModel.getTab();
        if (tab != null && tab.canGoForward()) {
            tab.goForward();
            updateButtonStatus();
            return true;
        }
        return false;
    }

    @Override
    public void stopOrReloadCurrentTab() {
        Tab currentTab = mToolbarModel.getTab();
        if (currentTab != null) {
            if (currentTab.isLoading()) {
                currentTab.stopLoading();
                RecordUserAction.record("MobileToolbarStop");
            } else {
                currentTab.reload();
                RecordUserAction.record("MobileToolbarReload");
            }
        }
        updateButtonStatus();
    }

    @Override
    public void openHomepage() {
        RecordUserAction.record("Home");

        Tab currentTab = mToolbarModel.getTab();
        if (currentTab == null) return;
        Context context = mToolbar.getContext();
        String homePageUrl = HomepageManager.getHomepageUri(context);
        if (TextUtils.isEmpty(homePageUrl)) {
            homePageUrl = UrlConstants.NTP_URL;
        }
        currentTab.loadUrl(new LoadUrlParams(homePageUrl, PageTransition.HOME_PAGE));
    }

    /**
     * Triggered when the URL input field has gained or lost focus.
     * @param hasFocus Whether the URL field has gained focus.
     */
    @Override
    public void onUrlFocusChange(boolean hasFocus) {
        mToolbar.onUrlFocusChange(hasFocus);

        if (mFindToolbarManager != null && hasFocus) mFindToolbarManager.hideToolbar();

        if (mControlsVisibilityDelegate == null) return;
        if (hasFocus) {
            mFullscreenFocusToken = mControlsVisibilityDelegate
                    .showControlsPersistentAndClearOldToken(mFullscreenFocusToken);
        } else {
            mControlsVisibilityDelegate.hideControlsPersistent(mFullscreenFocusToken);
            mFullscreenFocusToken = FullscreenManager.INVALID_TOKEN;
        }
    }

    /**
     * Updates the primary color used by the model to the given color.
     * @param color The primary color for the current tab.
     * @param shouldAnimate Whether the change of color should be animated.
     */
    public void updatePrimaryColor(int color, boolean shouldAnimate) {
        if (!mShouldUpdateToolbarPrimaryColor) return;

        boolean colorChanged = mToolbarModel.getPrimaryColor() != color;
        if (!colorChanged) return;

        mToolbarModel.setPrimaryColor(color);
        mToolbar.onPrimaryColorChanged(shouldAnimate);
    }

    /**
     * @param shouldUpdate Whether we should be updating the toolbar primary color based on updates
     *                     from the Tab.
     */
    public void setShouldUpdateToolbarPrimaryColor(boolean shouldUpdate) {
        mShouldUpdateToolbarPrimaryColor = shouldUpdate;
    }

    /**
     * Prevents the shadow from being rendered.
     */
    public void disableShadow() {
        View toolbarShadow = mControlContainer.findViewById(R.id.toolbar_shadow);
        if (toolbarShadow != null) UiUtils.removeViewFromParent(toolbarShadow);
    }

    /**
     * Sets the drawable that the close button shows.
     */
    public void setCloseButtonDrawable(Drawable drawable) {
        mToolbar.setCloseButtonImageResource(drawable);
    }

    /**
     * Sets whether a title should be shown within the Toolbar.
     * @param showTitle Whether a title should be shown.
     */
    public void setShowTitle(boolean showTitle) {
        mLocationBar.setShowTitle(showTitle);
    }

    /**
     * @see ToolbarLayout#setUrlBarHidden(boolean)
     */
    public void setUrlBarHidden(boolean hidden) {
        mToolbar.setUrlBarHidden(hidden);
    }

    /**
     * @see ToolbarLayout#getContentPublisher()
     */
    public String getContentPublisher() {
        return mToolbar.getContentPublisher();
    }

    /**
     * Focuses or unfocuses the URL bar.
     *
     * If you request focus and the UrlBar was already focused, this will select all of the text.
     *
     * @param focused Whether URL bar should be focused.
     */
    public void setUrlBarFocus(boolean focused) {
        if (!isInitialized()) return;
        boolean wasFocused = mToolbar.getLocationBar().isUrlBarFocused();
        mToolbar.getLocationBar().setUrlBarFocus(focused);
        if (wasFocused && focused) {
            mToolbar.getLocationBar().selectAll();
        }
    }

    /**
     * Reverts any pending edits of the location bar and reset to the page state.  This does not
     * change the focus state of the location bar.
     */
    public void revertLocationBarChanges() {
        mLocationBar.revertChanges();
    }

    /**
     * Handle all necessary tasks that can be delayed until initialization completes.
     * @param activityCreationTimeMs The time of creation for the activity this toolbar belongs to.
     * @param activityName Simple class name for the activity this toolbar belongs to.
     */
    public void onDeferredStartup(final long activityCreationTimeMs,
            final String activityName) {
        // Record startup performance statistics
        long elapsedTime = SystemClock.elapsedRealtime() - activityCreationTimeMs;
        if (elapsedTime < RECORD_UMA_PERFORMANCE_METRICS_DELAY_MS) {
            ThreadUtils.postOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    onDeferredStartup(activityCreationTimeMs, activityName);
                }
            }, RECORD_UMA_PERFORMANCE_METRICS_DELAY_MS - elapsedTime);
        }
        RecordHistogram.recordTimesHistogram("MobileStartup.ToolbarFirstDrawTime." + activityName,
                mToolbar.getFirstDrawTime() - activityCreationTimeMs, TimeUnit.MILLISECONDS);

        long firstFocusTime = mToolbar.getLocationBar().getFirstUrlBarFocusTime();
        if (firstFocusTime != 0) {
            RecordHistogram.recordCustomTimesHistogram(
                    "MobileStartup.ToolbarFirstFocusTime." + activityName,
                    firstFocusTime - activityCreationTimeMs, MIN_FOCUS_TIME_FOR_UMA_HISTOGRAM_MS,
                    MAX_FOCUS_TIME_FOR_UMA_HISTOGRAM_MS, TimeUnit.MILLISECONDS, 50);
        }
    }

    /**
     * Finish any toolbar animations.
     */
    public void finishAnimations() {
        if (isInitialized()) mToolbar.finishAnimations();
    }

    /**
     * Updates the current number of Tabs based on the TabModel this Toolbar contains.
     */
    private void updateTabCount() {
        if (!mTabRestoreCompleted || !mShouldUpdateTabCount) return;
        mToolbar.updateTabCountVisuals(mTabModelSelector.getCurrentModel().getCount());
    }

    /**
     * Updates the current button states and calls appropriate abstract visibility methods, giving
     * inheriting classes the chance to update the button visuals as well.
     */
    private void updateButtonStatus() {
        Tab currentTab = mToolbarModel.getTab();
        boolean tabCrashed = currentTab != null && currentTab.isShowingSadTab();

        mToolbar.updateButtonVisibility();
        mToolbar.updateBackButtonVisibility(currentTab != null && currentTab.canGoBack());
        mToolbar.updateForwardButtonVisibility(currentTab != null && currentTab.canGoForward());
        updateReloadState(tabCrashed);
        updateBookmarkButtonStatus();

        mToolbar.getMenuButtonWrapper().setVisibility(View.VISIBLE);
    }

    private void updateBookmarkButtonStatus() {
        Tab currentTab = mToolbarModel.getTab();
        boolean isBookmarked = currentTab != null
                && currentTab.getBookmarkId() != Tab.INVALID_BOOKMARK_ID;
        boolean editingAllowed = currentTab == null || mBookmarkBridge == null
                || mBookmarkBridge.isEditBookmarksEnabled();
        mToolbar.updateBookmarkButton(isBookmarked, editingAllowed);
    }

    private void updateReloadState(boolean tabCrashed) {
        Tab currentTab = mToolbarModel.getTab();
        boolean isLoading = false;
        if (!tabCrashed) {
            isLoading = (currentTab != null && currentTab.isLoading()) || !mNativeLibraryReady;
        }
        mToolbar.updateReloadButtonVisibility(isLoading);
        if (mMenuDelegatePhone != null) mMenuDelegatePhone.updateReloadButtonState(isLoading);
    }

    /**
     * Triggered when the selected tab has changed.
     */
    private void refreshSelectedTab() {
        Tab tab = null;
        if (mPreselectedTabId != Tab.INVALID_TAB_ID) {
            tab = mTabModelSelector.getTabById(mPreselectedTabId);
        }
        if (tab == null) tab = mTabModelSelector.getCurrentTab();

        boolean wasIncognito = mToolbarModel.isIncognito();
        Tab previousTab = mToolbarModel.getTab();

        boolean isIncognito =
                tab != null ? tab.isIncognito() : mTabModelSelector.isIncognitoSelected();
        mToolbarModel.setTab(tab, isIncognito);

        updateCurrentTabDisplayStatus();
        if (previousTab != tab || wasIncognito != isIncognito) {
            if (previousTab != tab) {
                if (previousTab != null) {
                    previousTab.removeObserver(mTabObserver);
                    previousTab.setIsAllowedToReturnToExternalApp(false);
                }
                if (tab != null) tab.addObserver(mTabObserver);
            }
            int defaultPrimaryColor = isIncognito
                    ? ApiCompatibilityUtils.getColor(mToolbar.getResources(),
                            R.color.incognito_primary_color)
                    : ApiCompatibilityUtils.getColor(mToolbar.getResources(),
                            R.color.default_primary_color);
            int primaryColor = tab != null ? tab.getThemeColor() : defaultPrimaryColor;
            updatePrimaryColor(primaryColor, false);

            mToolbar.onTabOrModelChanged();

            if (tab != null && tab.getWebContents() != null
                    && tab.getWebContents().isLoadingToDifferentDocument()) {
                mToolbar.onNavigatedToDifferentPage();
            }

            // Ensure the URL bar loses focus if the tab it was interacting with is changed from
            // underneath it.
            setUrlBarFocus(false);

            // Place the cursor in the Omnibox if applicable.  We always clear the focus above to
            // ensure the shield placed over the content is dismissed when switching tabs.  But if
            // needed, we will refocus the omnibox and make the cursor visible here.
            if (shouldShowCusrsorInLocationBar()) {
                mToolbar.getLocationBar().showUrlBarCursorWithoutFocusAnimations();
            }
        }

        Profile profile = mTabModelSelector.getModel(isIncognito).getProfile();
        if (mCurrentProfile != profile) {
            if (mBookmarkBridge != null) mBookmarkBridge.destroy();
            mBookmarkBridge = new BookmarkBridge(profile);
            mBookmarkBridge.addObserver(mBookmarksObserver);
            mAppMenuPropertiesDelegate.setBookmarkBridge(mBookmarkBridge);
            mLocationBar.setAutocompleteProfile(profile);
            mCurrentProfile = profile;
        }

        updateButtonStatus();
    }

    private void updateCurrentTabDisplayStatus() {
        Tab tab = mToolbarModel.getTab();
        mLocationBar.setUrlToPageUrl();

        updateTabLoadingState(true);

        if (tab == null) {
            finishLoadProgress(false);
            return;
        }

        mLoadProgressSimulator.cancel();

        if (tab.isLoading()) {
            if (NativePageFactory.isNativePageUrl(tab.getUrl(), tab.isIncognito())) {
                finishLoadProgress(false);
            } else {
                startLoadProgress();
                updateLoadProgress(tab.getProgress());
            }
        } else {
            finishLoadProgress(false);
        }
    }

    private void updateTabLoadingState(boolean updateUrl) {
        mLocationBar.updateLoadingState(updateUrl);
        if (updateUrl) updateButtonStatus();
    }

    private void updateLoadProgress(int progress) {
        // If it's a native page, progress bar is already hidden or being hidden, so don't update
        // the value.
        // TODO(kkimlabs): Investigate back/forward navigation with native page & web content and
        //                 figure out the correct progress bar presentation.
        Tab tab = mToolbarModel.getTab();
        if (NativePageFactory.isNativePageUrl(tab.getUrl(), tab.isIncognito())) return;

        progress = Math.max(progress, MINIMUM_LOAD_PROGRESS);
        mToolbar.setLoadProgress(progress / 100f);
        if (progress == 100) finishLoadProgress(true);
    }

    private void finishLoadProgress(boolean delayed) {
        mLoadProgressSimulator.cancel();
        mToolbar.finishLoadProgress(delayed);
    }

    /**
     * Only start showing the progress bar if it is not already started.
     */
    private void startLoadProgress() {
        if (mToolbar.isProgressStarted()) return;
        mToolbar.startLoadProgress();
    }

    private boolean shouldShowCusrsorInLocationBar() {
        Tab tab = mToolbarModel.getTab();
        if (tab == null) return false;
        NativePage nativePage = tab.getNativePage();
        if (!(nativePage instanceof NewTabPage) && !(nativePage instanceof IncognitoNewTabPage)) {
            return false;
        }

        Context context = mToolbar.getContext();
        return DeviceFormFactor.isTablet(context)
                && context.getResources().getConfiguration().keyboard
                == Configuration.KEYBOARD_QWERTY;
    }

    private static class LoadProgressSimulator {
        private static final int MSG_ID_UPDATE_PROGRESS = 1;

        private static final int PROGRESS_INCREMENT = 10;
        private static final int PROGRESS_INCREMENT_DELAY_MS = 10;

        private final ToolbarManager mToolbarManager;
        private final Handler mHandler;

        private int mProgress;

        public LoadProgressSimulator(ToolbarManager toolbar) {
            mToolbarManager = toolbar;
            mHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    assert msg.what == MSG_ID_UPDATE_PROGRESS;
                    mProgress = Math.min(100, mProgress += PROGRESS_INCREMENT);
                    mToolbarManager.updateLoadProgress(mProgress);

                    if (mProgress == 100) {
                        mToolbarManager.mToolbar.finishLoadProgress(true);
                        return;
                    }
                    sendEmptyMessageDelayed(MSG_ID_UPDATE_PROGRESS, PROGRESS_INCREMENT_DELAY_MS);
                }
            };
        }

        /**
         * Start simulating load progress from a baseline of 0.
         */
        public void start() {
            mProgress = 0;
            mToolbarManager.mToolbar.startLoadProgress();
            mToolbarManager.updateLoadProgress(mProgress);
            mHandler.sendEmptyMessage(MSG_ID_UPDATE_PROGRESS);
        }

        /**
         * Cancels simulating load progress.
         */
        public void cancel() {
            mHandler.removeMessages(MSG_ID_UPDATE_PROGRESS);
        }
    }
}
