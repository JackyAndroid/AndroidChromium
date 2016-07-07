// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.document.DocumentMetricIds;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarkUtils;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.ntp.BookmarksPage.BookmarkSelectedListener;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.preferences.DocumentModeManager;
import org.chromium.chrome.browser.preferences.DocumentModePreference;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.profiles.MostVisitedSites;
import org.chromium.chrome.browser.profiles.MostVisitedSites.MostVisitedURLsObserver;
import org.chromium.chrome.browser.profiles.MostVisitedSites.ThumbnailCallback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;

import java.util.concurrent.TimeUnit;

import jp.tomorrowkey.android.gifplayer.BaseGifImage;

/**
 * Provides functionality when the user interacts with the NTP.
 */
public class NewTabPage
        implements NativePage, InvalidationAwareThumbnailProvider, TemplateUrlServiceObserver {

    // The number of times that the document-mode opt-out promo will be shown.
    private static final int MAX_OPT_OUT_PROMO_COUNT = 10;

    // MostVisitedItem Context menu item IDs.
    static final int ID_OPEN_IN_NEW_TAB = 0;
    static final int ID_OPEN_IN_INCOGNITO_TAB = 1;
    static final int ID_REMOVE = 2;

    private static MostVisitedSites sMostVisitedSitesForTests;

    private final Tab mTab;
    private final TabModelSelector mTabModelSelector;
    private final Activity mActivity;

    private final Profile mProfile;
    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;
    private final NewTabPageView mNewTabPageView;

    private MostVisitedSites mMostVisitedSites;
    private FaviconHelper mFaviconHelper;
    private LargeIconBridge mLargeIconBridge;
    private LogoBridge mLogoBridge;
    private boolean mSearchProviderHasLogo;
    private boolean mIsIconMode;
    private final boolean mOptOutPromoShown;
    private String mOnLogoClickUrl;
    private String mAnimatedLogoUrl;
    private FakeboxDelegate mFakeboxDelegate;

    // The timestamp at which the constructor was called.
    private final long mConstructedTimeNs;

    private boolean mIsLoaded;

    // Whether destroy() has been called.
    private boolean mIsDestroyed;

    /**
     * Allows clients to listen for updates to the scroll changes of the search box on the
     * NTP.
     */
    public interface OnSearchBoxScrollListener {
        /**
         * Callback to be notified when the scroll position of the search box on the NTP has
         * changed.  A scroll percentage of 0, means the search box has no scroll applied and
         * is in it's natural resting position.  A value of 1 means the search box is scrolled
         * entirely to the top of the screen viewport.
         *
         * @param scrollPercentage The percentage the search box has been scrolled off the page.
         */
        void onScrollChanged(float scrollPercentage);
    }

    /**
     * Handles user interaction with the fakebox (the URL bar in the NTP).
     */
    public interface FakeboxDelegate {
        /**
         * Shows the voice recognition dialog. Called when the user taps the microphone icon.
         */
        void startVoiceRecognition();

        /**
         * @return Whether voice search is currently enabled.
         */
        boolean isVoiceSearchEnabled();

        /**
         * Focuses the URL bar when the user taps the fakebox, types in the fakebox, or pastes text
         * into the fakebox.
         *
         * @param pastedText The text that was pasted or typed into the fakebox, or null if the user
         *                   just tapped the fakebox.
         */
        void requestUrlFocusFromFakebox(String pastedText);
    }

    /**
     * @param url The URL to check whether it is for the NTP.
     * @return Whether the passed in URL is used to render the NTP.
     */
    public static boolean isNTPUrl(String url) {
        return url != null && url.startsWith(UrlConstants.NTP_URL);
    }

    public static void launchBookmarksDialog(Activity activity, Tab tab,
            TabModelSelector tabModelSelector) {
        if (!EnhancedBookmarkUtils.showEnhancedBookmarkIfEnabled(activity)) {
            BookmarkDialogSelectedListener listener = new BookmarkDialogSelectedListener(tab);
            NativePage page = BookmarksPage.buildPageInDocumentMode(
                    activity, tab, tabModelSelector, Profile.getLastUsedProfile(),
                    listener);
            page.updateForUrl(UrlConstants.BOOKMARKS_URL);
            Dialog dialog = new NativePageDialog(activity, page);
            listener.setDialog(dialog);
            dialog.show();
        }
    }

    public static void launchRecentTabsDialog(Activity activity, Tab tab) {
        DocumentRecentTabsManager manager = new DocumentRecentTabsManager(tab, activity);
        NativePage page = new RecentTabsPage(activity, manager);
        page.updateForUrl(UrlConstants.RECENT_TABS_URL);
        Dialog dialog = new NativePageDialog(activity, page);
        manager.setDialog(dialog);
        dialog.show();
    }

    @VisibleForTesting
    static void setMostVisitedSitesForTests(MostVisitedSites mostVisitedSitesForTests) {
        sMostVisitedSitesForTests = mostVisitedSitesForTests;
    }

    private final NewTabPageManager mNewTabPageManager = new NewTabPageManager() {
        @Override
        public boolean isLocationBarShownInNTP() {
            if (mIsDestroyed) return false;
            Context context = mNewTabPageView.getContext();
            return isInSingleUrlBarMode(context)
                    && !mNewTabPageView.urlFocusAnimationsDisabled();
        }

        @Override
        public boolean isVoiceSearchEnabled() {
            return mFakeboxDelegate != null && mFakeboxDelegate.isVoiceSearchEnabled();
        }

        private void recordOpenedMostVisitedItem(MostVisitedItem item) {
            if (mIsDestroyed) return;
            NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_MOST_VISITED_ENTRY);
            NewTabPageUma.recordExplicitUserNavigation(
                    item.getUrl(), NewTabPageUma.RAPPOR_ACTION_VISITED_SUGGESTED_TILE);
            RecordHistogram.recordMediumTimesHistogram("NewTabPage.MostVisitedTime",
                    System.nanoTime() - mConstructedTimeNs, TimeUnit.NANOSECONDS);
            mMostVisitedSites.recordOpenedMostVisitedItem(item.getIndex(), item.getTileType());
        }

        private void recordDocumentOptOutPromoClick(int which) {
            RecordHistogram.recordEnumeratedHistogram("DocumentActivity.OptOutClick", which,
                    DocumentMetricIds.OPT_OUT_CLICK_COUNT);
        }

        @Override
        public boolean shouldShowOptOutPromo() {
            if (!FeatureUtilities.isDocumentMode(mActivity)) return false;
            DocumentModeManager documentModeManager = DocumentModeManager.getInstance(mActivity);
            return !documentModeManager.isOptOutPromoDismissed()
                    && (documentModeManager.getOptOutShownCount() < MAX_OPT_OUT_PROMO_COUNT);
        }

        @Override
        public void optOutPromoShown() {
            assert FeatureUtilities.isDocumentMode(mActivity);
            DocumentModeManager.getInstance(mActivity).incrementOptOutShownCount();
            RecordUserAction.record("DocumentActivity_OptOutShownOnHome");
        }

        @Override
        public void optOutPromoClicked(boolean settingsClicked) {
            assert FeatureUtilities.isDocumentMode(mActivity);
            if (settingsClicked) {
                recordDocumentOptOutPromoClick(DocumentMetricIds.OPT_OUT_CLICK_SETTINGS);
                PreferencesLauncher.launchSettingsPage(mActivity,
                        DocumentModePreference.class.getName());
            } else {
                recordDocumentOptOutPromoClick(DocumentMetricIds.OPT_OUT_CLICK_GOT_IT);
                DocumentModeManager documentModeManager = DocumentModeManager.getInstance(
                        mActivity);
                documentModeManager.setOptedOutState(DocumentModeManager.OPT_OUT_PROMO_DISMISSED);
            }
        }

        @Override
        public void open(MostVisitedItem item) {
            if (mIsDestroyed) return;
            recordOpenedMostVisitedItem(item);
            mTab.loadUrl(new LoadUrlParams(item.getUrl(), PageTransition.AUTO_BOOKMARK));
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, OnMenuItemClickListener listener) {
            if (mIsDestroyed) return;
            menu.add(Menu.NONE, ID_OPEN_IN_NEW_TAB, Menu.NONE, R.string.contextmenu_open_in_new_tab)
                    .setOnMenuItemClickListener(listener);
            if (PrefServiceBridge.getInstance().isIncognitoModeEnabled()) {
                menu.add(Menu.NONE, ID_OPEN_IN_INCOGNITO_TAB, Menu.NONE,
                        R.string.contextmenu_open_in_incognito_tab).setOnMenuItemClickListener(
                        listener);
            }
            menu.add(Menu.NONE, ID_REMOVE, Menu.NONE, R.string.remove)
                    .setOnMenuItemClickListener(listener);
        }

        @Override
        public boolean onMenuItemClick(int menuId, MostVisitedItem item) {
            if (mIsDestroyed) return false;
            switch (menuId) {
                case ID_OPEN_IN_NEW_TAB:
                    recordOpenedMostVisitedItem(item);
                    mTabModelSelector.openNewTab(new LoadUrlParams(item.getUrl(),
                            PageTransition.AUTO_BOOKMARK), TabLaunchType.FROM_LONGPRESS_BACKGROUND,
                            mTab, false);
                    return true;
                case ID_OPEN_IN_INCOGNITO_TAB:
                    recordOpenedMostVisitedItem(item);
                    mTabModelSelector.openNewTab(new LoadUrlParams(item.getUrl(),
                            PageTransition.AUTO_BOOKMARK), TabLaunchType.FROM_LONGPRESS_FOREGROUND,
                            mTab, true);
                    return true;
                case ID_REMOVE:
                    mMostVisitedSites.blacklistUrl(item.getUrl());
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void navigateToBookmarks() {
            if (mIsDestroyed) return;
            RecordUserAction.record("MobileNTPSwitchToBookmarks");
            if (FeatureUtilities.isDocumentMode(mActivity)) {
                launchBookmarksDialog(mActivity, mTab, mTabModelSelector);
            } else if (!EnhancedBookmarkUtils.showEnhancedBookmarkIfEnabled(mActivity)) {
                mTab.loadUrl(new LoadUrlParams(UrlConstants.BOOKMARKS_URL));
            }
        }

        @Override
        public void navigateToRecentTabs() {
            if (mIsDestroyed) return;
            RecordUserAction.record("MobileNTPSwitchToOpenTabs");
            if (FeatureUtilities.isDocumentMode(mActivity)) {
                launchRecentTabsDialog(mActivity, mTab);
            } else {
                mTab.loadUrl(new LoadUrlParams(UrlConstants.RECENT_TABS_URL));
            }
        }

        @Override
        public void focusSearchBox(boolean beginVoiceSearch, String pastedText) {
            if (mIsDestroyed) return;
            if (mFakeboxDelegate != null) {
                if (beginVoiceSearch) {
                    mFakeboxDelegate.startVoiceRecognition();
                } else {
                    mFakeboxDelegate.requestUrlFocusFromFakebox(pastedText);
                }
            }
        }

        @Override
        public void setMostVisitedURLsObserver(MostVisitedURLsObserver observer, int numResults) {
            if (mIsDestroyed) return;
            mMostVisitedSites.setMostVisitedURLsObserver(observer, numResults);
        }

        @Override
        public void getURLThumbnail(String url, ThumbnailCallback thumbnailCallback) {
            if (mIsDestroyed) return;
            mMostVisitedSites.getURLThumbnail(url, thumbnailCallback);
        }

        @Override
        public void getLocalFaviconImageForURL(
                String url, int size, FaviconImageCallback faviconCallback) {
            if (mIsDestroyed) return;
            if (mFaviconHelper == null) mFaviconHelper = new FaviconHelper();
            mFaviconHelper.getLocalFaviconImageForURL(mProfile, url, size, faviconCallback);
        }

        @Override
        public void getLargeIconForUrl(String url, int size, LargeIconCallback callback) {
            if (mIsDestroyed) return;
            if (mLargeIconBridge == null) mLargeIconBridge = new LargeIconBridge(mProfile);
            mLargeIconBridge.getLargeIconForUrl(url, size, callback);
        }

        @Override
        public void ensureIconIsAvailable(String pageUrl, String iconUrl, boolean isLargeIcon,
                IconAvailabilityCallback callback) {
            if (mIsDestroyed) return;
            if (mFaviconHelper == null) mFaviconHelper = new FaviconHelper();
            mFaviconHelper.ensureIconIsAvailable(
                    mProfile, mTab.getWebContents(), pageUrl, iconUrl, isLargeIcon, callback);
        }

        @Override
        public void onLogoClicked(boolean isAnimatedLogoShowing) {
            if (mIsDestroyed) return;

            if (!isAnimatedLogoShowing && mAnimatedLogoUrl != null) {
                mNewTabPageView.showLogoLoadingView();
                mLogoBridge.getAnimatedLogo(new LogoBridge.AnimatedLogoCallback() {
                    @Override
                    public void onAnimatedLogoAvailable(BaseGifImage animatedLogoImage) {
                        if (mIsDestroyed) return;
                        mNewTabPageView.playAnimatedLogo(animatedLogoImage);
                    }
                }, mAnimatedLogoUrl);
            } else if (mOnLogoClickUrl != null) {
                mTab.loadUrl(new LoadUrlParams(mOnLogoClickUrl, PageTransition.LINK));
            }
        }

        @Override
        public void getSearchProviderLogo(final LogoObserver logoObserver) {
            if (mIsDestroyed) return;
            LogoObserver wrapperCallback = new LogoObserver() {
                @Override
                public void onLogoAvailable(Logo logo, boolean fromCache) {
                    if (mIsDestroyed) return;
                    mOnLogoClickUrl = logo != null ? logo.onClickUrl : null;
                    mAnimatedLogoUrl = logo != null ? logo.animatedLogoUrl : null;
                    logoObserver.onLogoAvailable(logo, fromCache);
                }
            };
            mLogoBridge.getCurrentLogo(wrapperCallback);
        }

        @Override
        public void onLoadingComplete(MostVisitedItem[] items) {
            long loadTimeMs = (System.nanoTime() - mConstructedTimeNs) / 1000000;
            RecordHistogram.recordTimesHistogram(
                    "Tab.NewTabOnload", loadTimeMs, TimeUnit.MILLISECONDS);
            mIsLoaded = true;
            StartupMetrics.getInstance().recordOpenedNTP();

            if (mIsDestroyed) return;

            int tileTypes[] = new int[items.length];
            for (int i = 0; i < items.length; i++) {
                tileTypes[i] = items[i].getTileType();
            }
            mMostVisitedSites.recordTileTypeMetrics(tileTypes, mIsIconMode);
        }
    };

    /**
     * Constructs a NewTabPage.
     * @param activity The activity used for context to create the new tab page's View.
     * @param tab The Tab that is showing this new tab page.
     * @param tabModelSelector The TabModelSelector used to open tabs.
     */
    public NewTabPage(Activity activity, Tab tab, TabModelSelector tabModelSelector) {
        mConstructedTimeNs = System.nanoTime();

        mTab = tab;
        mActivity = activity;
        mTabModelSelector = tabModelSelector;
        mProfile = tab.getProfile();

        mTitle = activity.getResources().getString(R.string.button_new_tab);
        mBackgroundColor = ApiCompatibilityUtils.getColor(activity.getResources(), R.color.ntp_bg);
        mThemeColor = ApiCompatibilityUtils.getColor(
                activity.getResources(), R.color.default_primary_color);
        TemplateUrlService.getInstance().addObserver(this);

        // Whether to show the promo can change within the lifetime of a single NTP instance
        // because the user can dismiss the promo.  To ensure the UI is consistent, cache the
        // value initially and ignore further updates.
        mOptOutPromoShown = mNewTabPageManager.shouldShowOptOutPromo();

        mMostVisitedSites = buildMostVisitedSites(mProfile);
        mLogoBridge = new LogoBridge(mProfile);
        updateSearchProviderHasLogo();

        LayoutInflater inflater = LayoutInflater.from(activity);
        mNewTabPageView = (NewTabPageView) inflater.inflate(R.layout.new_tab_page, null);
        // TODO(newt): delete thumbnail mode once we're sure that icon mode has stuck for good.
        mIsIconMode = true;
        mNewTabPageView.initialize(mNewTabPageManager, isInSingleUrlBarMode(activity),
                mSearchProviderHasLogo, mIsIconMode);

        RecordHistogram.recordBooleanHistogram(
                "NewTabPage.MobileIsUserOnline", NetworkChangeNotifier.isOnline());
    }

    private static MostVisitedSites buildMostVisitedSites(Profile profile) {
        if (sMostVisitedSitesForTests != null) {
            return sMostVisitedSitesForTests;
        } else {
            return new MostVisitedSites(profile);
        }
    }

    /** @return The view container for the new tab page. */
    @VisibleForTesting
    NewTabPageView getNewTabPageView() {
        return mNewTabPageView;
    }

    /**
     * Updates whether the NewTabPage should animate on URL focus changes.
     * @param disable Whether to disable the animations.
     */
    public void setUrlFocusAnimationsDisabled(boolean disable) {
        mNewTabPageView.setUrlFocusAnimationsDisabled(disable);
    }

    private boolean isInSingleUrlBarMode(Context context) {
        if (DeviceFormFactor.isTablet(context)) return false;
        if (mOptOutPromoShown) return false;

        return mSearchProviderHasLogo;
    }

    private void updateSearchProviderHasLogo() {
        mSearchProviderHasLogo = !mOptOutPromoShown
                && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
    }

    private void onSearchEngineUpdated() {
        // TODO(newt): update this if other search providers provide logos.
        updateSearchProviderHasLogo();
        mNewTabPageView.setSearchProviderHasLogo(mSearchProviderHasLogo);
    }

    /**
     * Specifies the percentage the URL is focused during an animation.  1.0 specifies that the URL
     * bar has focus and has completed the focus animation.  0 is when the URL bar is does not have
     * any focus.
     *
     * @param percent The percentage of the URL bar focus animation.
     */
    public void setUrlFocusChangeAnimationPercent(float percent) {
        mNewTabPageView.setUrlFocusChangeAnimationPercent(percent);
    }

    /**
     * Get the bounds of the search box in relation to the top level NewTabPage view.
     *
     * @param originalBounds The bounding region of the search box without external transforms
     *                       applied.  The delta between this and the transformed bounds determines
     *                       the amount of scroll applied to this view.
     * @param transformedBounds The bounding region of the search box including any transforms
     *                          applied by the parent view hierarchy up to the NewTabPage view.
     *                          This more accurately reflects the current drawing location of the
     *                          search box.
     */
    public void getSearchBoxBounds(Rect originalBounds, Rect transformedBounds) {
        mNewTabPageView.getSearchBoxBounds(originalBounds, transformedBounds);
    }

    /**
     * @return Whether the location bar is shown in the NTP.
     */
    public boolean isLocationBarShownInNTP() {
        return mNewTabPageManager.isLocationBarShownInNTP();
    }

    /**
     * Sets the listener for search box scroll changes.
     * @param listener The listener to be notified on changes.
     */
    public void setSearchBoxScrollListener(OnSearchBoxScrollListener listener) {
        mNewTabPageView.setSearchBoxScrollListener(listener);
    }

    /**
     * Sets the FakeboxDelegate that this pages interacts with.
     */
    public void setFakeboxDelegate(FakeboxDelegate fakeboxDelegate) {
        mFakeboxDelegate = fakeboxDelegate;
        if (mFakeboxDelegate != null) {
            mNewTabPageView.updateVoiceSearchButtonVisibility();
        }
    }

    /**
     * @return Whether the NTP has finished loaded.
     */
    @VisibleForTesting
    public boolean isLoadedForTests() {
        return mIsLoaded;
    }

    // TemplateUrlServiceObserver overrides

    @Override
    public void onTemplateURLServiceChanged() {
        onSearchEngineUpdated();
    }

    // NativePage overrides

    @Override
    public void destroy() {
        assert !mIsDestroyed;
        assert getView().getParent() == null : "Destroy called before removed from window";
        if (mFaviconHelper != null) {
            mFaviconHelper.destroy();
            mFaviconHelper = null;
        }
        if (mLargeIconBridge != null) {
            mLargeIconBridge.destroy();
            mLargeIconBridge = null;
        }
        if (mMostVisitedSites != null) {
            mMostVisitedSites.destroy();
            mMostVisitedSites = null;
        }
        if (mLogoBridge != null) {
            mLogoBridge.destroy();
            mLogoBridge = null;
        }
        TemplateUrlService.getInstance().removeObserver(this);
        mIsDestroyed = true;
    }

    @Override
    public String getUrl() {
        return UrlConstants.NTP_URL;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return isLocationBarShownInNTP() ? Color.WHITE : mThemeColor;
    }

    @Override
    public View getView() {
        return mNewTabPageView;
    }

    @Override
    public String getHost() {
        return UrlConstants.NTP_HOST;
    }

    @Override
    public void updateForUrl(String url) {
    }

    // InvalidationAwareThumbnailProvider

    @Override
    public boolean shouldCaptureThumbnail() {
        return mNewTabPageView.shouldCaptureThumbnail();
    }

    @Override
    public void captureThumbnail(Canvas canvas) {
        mNewTabPageView.captureThumbnail(canvas);
    }

    private static class BookmarkDialogSelectedListener implements BookmarkSelectedListener {
        private Dialog mDialog;
        private final Tab mTab;

        public BookmarkDialogSelectedListener(Tab tab) {
            mTab = tab;
        }

        @Override
        public void onNewTabOpened() {
            if (mDialog != null) mDialog.dismiss();
        }

        @Override
        public void onBookmarkSelected(String url, String title, Bitmap favicon) {
            if (mDialog != null) mDialog.dismiss();
            mTab.loadUrl(new LoadUrlParams(url));
        }

        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
    }
}
