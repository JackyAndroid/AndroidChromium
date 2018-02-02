// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.MostVisitedSites;
import org.chromium.chrome.browser.profiles.MostVisitedSites.MostVisitedURLsObserver;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.sync.SyncSessionsMetrics;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.common.Referrer;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.mojom.WindowOpenDisposition;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jp.tomorrowkey.android.gifplayer.BaseGifImage;

/**
 * Provides functionality when the user interacts with the NTP.
 */
public class NewTabPage
        implements NativePage, InvalidationAwareThumbnailProvider, TemplateUrlServiceObserver {
    private static final String TAG = "NewTabPage";

    // UMA enum constants. CTA means the "click-to-action" icon.
    private static final String LOGO_SHOWN_UMA_NAME = "NewTabPage.LogoShown";
    private static final int STATIC_LOGO_SHOWN = 0;
    private static final int CTA_IMAGE_SHOWN = 1;

    private static final String LOGO_CLICK_UMA_NAME = "NewTabPage.LogoClick";
    private static final int STATIC_LOGO_CLICKED = 0;
    private static final int CTA_IMAGE_CLICKED = 1;
    private static final int ANIMATED_LOGO_CLICKED = 2;

    // Key for the scroll position data that may be stored in a navigation entry.
    private static final String NAVIGATION_ENTRY_SCROLL_POSITION_KEY = "NewTabPageScrollPosition";

    private static final String CHROME_CONTENT_SUGGESTIONS_REFERRER =
            "https://www.googleapis.com/auth/chrome-content-suggestions";

    private static MostVisitedSites sMostVisitedSitesForTests;
    private static SuggestionsSource sSuggestionsSourceForTests;

    private final Tab mTab;
    private final TabModelSelector mTabModelSelector;
    private final ChromeActivity mActivity;

    private final Profile mProfile;
    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;
    private final NewTabPageView mNewTabPageView;

    private TabObserver mTabObserver;
    private MostVisitedSites mMostVisitedSites;
    private SnackbarController mMostVisitedItemRemovedController;
    private FaviconHelper mFaviconHelper;
    private LargeIconBridge mLargeIconBridge;
    private LogoBridge mLogoBridge;
    private boolean mSearchProviderHasLogo;
    private String mOnLogoClickUrl;
    private String mAnimatedLogoUrl;
    private FakeboxDelegate mFakeboxDelegate;
    private SnippetsBridge mSnippetsBridge;

    // The timestamp at which the constructor was called.
    private final long mConstructedTimeNs;

    // The timestamp at which this NTP was last shown to the user.
    private long mLastShownTimeNs;

    private boolean mIsLoaded;

    // Whether destroy() has been called.
    private boolean mIsDestroyed;

    private final ObserverList<DestructionObserver> mDestructionObservers = new ObserverList<>();

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
        void onNtpScrollChanged(float scrollPercentage);
    }

    /**
     * Object that registered through the {@link NewTabPageManager}, and that will be notified when
     * the {@link NewTabPage} is destroyed.
     * @see NewTabPageManager#setDestructionObserver(DestructionObserver)
     */
    public interface DestructionObserver { void onDestroy(); }

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
         * @return Whether the URL bar is currently focused.
         */
        boolean isUrlBarFocused();

        /**
         * Focuses the URL bar when the user taps the fakebox, types in the fakebox, or pastes text
         * into the fakebox.
         *
         * @param pastedText The text that was pasted or typed into the fakebox, or null if the user
         *                   just tapped the fakebox.
         */
        void requestUrlFocusFromFakebox(String pastedText);

        /**
         * @return whether the provided native page is the one currently displayed to the user.
         */
        boolean isCurrentPage(NativePage nativePage);
    }

    /**
     * @param url The URL to check whether it is for the NTP.
     * @return Whether the passed in URL is used to render the NTP.
     */
    public static boolean isNTPUrl(String url) {
        // Also handle the legacy chrome://newtab URL since that will redirect to
        // chrome-native://newtab natively.
        return url != null
                && (url.startsWith(UrlConstants.NTP_URL) || url.startsWith("chrome://newtab"));
    }

    @VisibleForTesting
    public static void setMostVisitedSitesForTests(MostVisitedSites mostVisitedSitesForTests) {
        sMostVisitedSitesForTests = mostVisitedSitesForTests;
    }

    @VisibleForTesting
    public static void setSuggestionsSourceForTests(SuggestionsSource suggestionsSource) {
        sSuggestionsSourceForTests = suggestionsSource;
    }

    private final NewTabPageManager mNewTabPageManager = new NewTabPageManager() {
        private static final String NTP_OFFLINE_PAGES_FEATURE_NAME = "NTPOfflinePages";

        private boolean isNtpOfflinePagesEnabled() {
            return ChromeFeatureList.isEnabled(NTP_OFFLINE_PAGES_FEATURE_NAME);
        }

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

        @Override
        public boolean isFakeOmniboxTextEnabledTablet() {
            return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_FAKE_OMNIBOX_TEXT);
        }

        private void recordOpenedMostVisitedItem(MostVisitedItem item) {
            if (mIsDestroyed) return;
            NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_MOST_VISITED_ENTRY);
            NewTabPageUma.recordExplicitUserNavigation(
                    item.getUrl(), NewTabPageUma.RAPPOR_ACTION_VISITED_SUGGESTED_TILE);
            RecordHistogram.recordMediumTimesHistogram("NewTabPage.MostVisitedTime",
                    System.nanoTime() - mLastShownTimeNs, TimeUnit.NANOSECONDS);
            mMostVisitedSites.recordOpenedMostVisitedItem(
                    item.getIndex(), item.getTileType(), item.getSource());
        }

        @Override
        public void openMostVisitedItem(int windowDisposition, MostVisitedItem item) {
            if (mIsDestroyed) return;

            String url = item.getUrl();

            // TODO(treib): Should we call recordOpenedMostVisitedItem here?
            if (windowDisposition != WindowOpenDisposition.NEW_WINDOW) {
                recordOpenedMostVisitedItem(item);
            }

            if (windowDisposition == WindowOpenDisposition.CURRENT_TAB
                    && switchToExistingTab(url)) {
                return;
            }

            openUrl(windowDisposition, new LoadUrlParams(url, PageTransition.AUTO_BOOKMARK));
        }

        @Override
        public void removeMostVisitedItem(MostVisitedItem item) {
            mMostVisitedSites.addBlacklistedUrl(item.getUrl());
            showMostVisitedItemRemovedSnackbar(item.getUrl());
        }

        @Override
        public void onLearnMoreClicked() {
            if (mIsDestroyed) return;
            NewTabPageUma.recordAction(NewTabPageUma.ACTION_CLICKED_LEARN_MORE);
            String url = "https://support.google.com/chrome/?p=new_tab";
            // TODO(mastiz): Change this to LINK?
            openUrl(WindowOpenDisposition.CURRENT_TAB,
                    new LoadUrlParams(url, PageTransition.AUTO_BOOKMARK));
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private boolean switchToExistingTab(String url) {
            String matchPattern = CommandLine.getInstance().getSwitchValue(
                    ChromeSwitches.NTP_SWITCH_TO_EXISTING_TAB);
            boolean matchByHost;
            if ("url".equals(matchPattern)) {
                matchByHost = false;
            } else if ("host".equals(matchPattern)) {
                matchByHost = true;
            } else {
                return false;
            }

            TabModel tabModel = mTabModelSelector.getModel(false);
            for (int i = tabModel.getCount() - 1; i >= 0; --i) {
                if (matchURLs(tabModel.getTabAt(i).getUrl(), url, matchByHost)) {
                    TabModelUtils.setIndex(tabModel, i);
                    return true;
                }
            }
            return false;
        }

        private boolean matchURLs(String url1, String url2, boolean matchByHost) {
            if (url1 == null || url2 == null) return false;
            return matchByHost ? UrlUtilities.sameHost(url1, url2) : url1.equals(url2);
        }

        @Override
        public void trackSnippetsPageImpression(int[] categories, int[] suggestionsPerCategory) {
            mSnippetsBridge.onPageShown(categories, suggestionsPerCategory);
        }

        @Override
        public void trackSnippetImpression(SnippetArticle article) {
            mSnippetsBridge.onSuggestionShown(article);
        }

        @Override
        public void trackSnippetMenuOpened(SnippetArticle article) {
            mSnippetsBridge.onSuggestionMenuOpened(article);
        }

        @Override
        public void trackSnippetCategoryActionImpression(int category, int position) {
            mSnippetsBridge.onMoreButtonShown(category, position);
        }

        @Override
        public void trackSnippetCategoryActionClick(int category, int position) {
            mSnippetsBridge.onMoreButtonClicked(category, position);
        }

        @Override
        public void openSnippet(int windowOpenDisposition, SnippetArticle article) {
            mSnippetsBridge.onSuggestionOpened(article, windowOpenDisposition);
            NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_SNIPPET);

            if (article.mIsDownloadedAsset) {
                assert windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB
                        || windowOpenDisposition == WindowOpenDisposition.NEW_WINDOW
                        || windowOpenDisposition == WindowOpenDisposition.NEW_FOREGROUND_TAB;
                DownloadUtils.openFile(
                        article.getDownloadAssetFile(), article.getDownloadAssetMimeType(), false);
                return;
            }

            if (article.isRecentTab()) {
                assert windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB;
                // TODO(vitaliii): Add a debug check that the result is true after crbug.com/662924
                // is resolved.
                openRecentTabSnippet(article);
                return;
            }

            // TODO(treib): Also track other dispositions. crbug.com/665915
            if (windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB) {
                NewTabPageUma.monitorContentSuggestionVisit(mTab, article.mCategory);
            }

            LoadUrlParams loadUrlParams;
            // We explicitly open an offline page only for offline page downloads. For all other
            // sections the URL is opened and it is up to Offline Pages whether to open its offline
            // page (e.g. when offline).
            if (article.isDownload() && !article.mIsDownloadedAsset) {
                assert article.getOfflinePageOfflineId() != null;
                assert windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB
                        || windowOpenDisposition == WindowOpenDisposition.NEW_WINDOW
                        || windowOpenDisposition == WindowOpenDisposition.NEW_FOREGROUND_TAB;
                loadUrlParams = OfflinePageUtils.getLoadUrlParamsForOpeningOfflineVersion(
                        article.mUrl, article.getOfflinePageOfflineId());
                // Extra headers are not read in loadUrl, but verbatim headers are.
                loadUrlParams.setVerbatimHeaders(loadUrlParams.getExtraHeadersString());
            } else {
                loadUrlParams = new LoadUrlParams(article.mUrl, PageTransition.AUTO_BOOKMARK);
            }

            // For article suggestions, we set the referrer. This is exploited
            // to filter out these history entries for NTP tiles.
            // TODO(mastiz): Extend this with support for other categories.
            if (article.mCategory == KnownCategories.ARTICLES) {
                loadUrlParams.setReferrer(new Referrer(
                        CHROME_CONTENT_SUGGESTIONS_REFERRER, Referrer.REFERRER_POLICY_ALWAYS));
            }

            openUrl(windowOpenDisposition, loadUrlParams);
        }

        private boolean openRecentTabSnippet(SnippetArticle recentTabArticle) {
            TabModel tabModel = mTabModelSelector.getModel(false);
            int tabId = Integer.parseInt(recentTabArticle.getRecentTabId());
            int tabIndex = TabModelUtils.getTabIndexById(tabModel, tabId);
            if (tabIndex == TabModel.INVALID_TAB_INDEX) return false;
            TabModelUtils.setIndex(tabModel, tabIndex);
            OfflinePageUtils.openInExistingTab(recentTabArticle.mUrl,
                    recentTabArticle.getOfflinePageOfflineId(), tabModel.getTabAt(tabIndex));
            return true;
        }

        private void openUrl(int windowOpenDisposition, LoadUrlParams loadUrlParams) {
            assert !mIsDestroyed;
            switch (windowOpenDisposition) {
                case WindowOpenDisposition.CURRENT_TAB:
                    mTab.loadUrl(loadUrlParams);
                    break;
                case WindowOpenDisposition.NEW_FOREGROUND_TAB:
                    openUrlInNewTab(loadUrlParams, false);
                    break;
                case WindowOpenDisposition.OFF_THE_RECORD:
                    openUrlInNewTab(loadUrlParams, true);
                    break;
                case WindowOpenDisposition.NEW_WINDOW:
                    openUrlInNewWindow(loadUrlParams);
                    break;
                case WindowOpenDisposition.SAVE_TO_DISK:
                    saveUrlForOffline(loadUrlParams.getUrl());
                    break;
                default:
                    assert false;
            }
        }

        @Override
        public boolean isOpenInNewWindowEnabled() {
            return MultiWindowUtils.getInstance().isOpenInOtherWindowSupported(mActivity);
        }

        @Override
        public boolean isOpenInIncognitoEnabled() {
            return PrefServiceBridge.getInstance().isIncognitoModeEnabled();
        }

        private void openUrlInNewWindow(LoadUrlParams loadUrlParams) {
            TabDelegate tabDelegate = new TabDelegate(false);
            tabDelegate.createTabInOtherWindow(loadUrlParams, mActivity, mTab.getParentId());
        }

        private void openUrlInNewTab(LoadUrlParams loadUrlParams, boolean incognito) {
            mTabModelSelector.openNewTab(
                    loadUrlParams, TabLaunchType.FROM_LONGPRESS_BACKGROUND, mTab, incognito);
        }

        private void saveUrlForOffline(String url) {
            OfflinePageBridge.getForProfile(mProfile)
                    .savePageLater(url, "ntp_suggestions", true /* userRequested */);
        }

        @Override
        public void navigateToBookmarks() {
            if (mIsDestroyed) return;
            RecordUserAction.record("MobileNTPSwitchToBookmarks");
            BookmarkUtils.showBookmarkManager(mActivity);
        }

        @Override
        public void navigateToRecentTabs() {
            if (mIsDestroyed) return;
            RecordUserAction.record("MobileNTPSwitchToOpenTabs");
            mTab.loadUrl(new LoadUrlParams(UrlConstants.RECENT_TABS_URL));
        }

        @Override
        public void navigateToDownloadManager() {
            if (mIsDestroyed) return;
            assert DownloadUtils.isDownloadHomeEnabled();
            RecordUserAction.record("MobileNTPSwitchToDownloadManager");
            DownloadUtils.showDownloadManager(mActivity, mTab);
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
                boolean isTemporary, IconAvailabilityCallback callback) {
            if (mIsDestroyed) return;
            if (mFaviconHelper == null) mFaviconHelper = new FaviconHelper();
            mFaviconHelper.ensureIconIsAvailable(mProfile, mTab.getWebContents(), pageUrl, iconUrl,
                    isLargeIcon, isTemporary, callback);
        }

        private boolean isLocalUrl(String url) {
            return "file".equals(Uri.parse(url).getScheme());
        }

        @Override
        public void getUrlsAvailableOffline(
                Set<String> pageUrls, final Callback<Set<String>> callback) {
            final Set<String> urlsAvailableOffline = new HashSet<>();
            if (mIsDestroyed || !isNtpOfflinePagesEnabled()) {
                callback.onResult(urlsAvailableOffline);
                return;
            }

            HashSet<String> urlsToCheckForOfflinePage = new HashSet<>();

            for (String pageUrl : pageUrls) {
                if (isLocalUrl(pageUrl)) {
                    urlsAvailableOffline.add(pageUrl);
                } else {
                    urlsToCheckForOfflinePage.add(pageUrl);
                }
            }

            final long offlineQueryStartTime = SystemClock.elapsedRealtime();

            OfflinePageBridge offlinePageBridge = OfflinePageBridge.getForProfile(mProfile);

            // TODO(dewittj): Remove this code by making the NTP badging available after the NTP is
            // fully loaded.
            if (offlinePageBridge == null || !offlinePageBridge.isOfflinePageModelLoaded()) {
                // Posting a task to avoid potential re-entrancy issues.
                ThreadUtils.postOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(urlsAvailableOffline);
                    }
                });
                return;
            }

            offlinePageBridge.checkPagesExistOffline(
                    urlsToCheckForOfflinePage, new Callback<Set<String>>() {
                        @Override
                        public void onResult(Set<String> urlsWithOfflinePages) {
                            urlsAvailableOffline.addAll(urlsWithOfflinePages);
                            callback.onResult(urlsAvailableOffline);
                            RecordHistogram.recordTimesHistogram("NewTabPage.OfflineUrlsLoadTime",
                                    SystemClock.elapsedRealtime() - offlineQueryStartTime,
                                    TimeUnit.MILLISECONDS);
                        }
                    });
        }

        @Override
        public void onLogoClicked(boolean isAnimatedLogoShowing) {
            if (mIsDestroyed) return;

            if (!isAnimatedLogoShowing && mAnimatedLogoUrl != null) {
                RecordHistogram.recordSparseSlowlyHistogram(LOGO_CLICK_UMA_NAME, CTA_IMAGE_CLICKED);
                mNewTabPageView.showLogoLoadingView();
                mLogoBridge.getAnimatedLogo(new LogoBridge.AnimatedLogoCallback() {
                    @Override
                    public void onAnimatedLogoAvailable(BaseGifImage animatedLogoImage) {
                        if (mIsDestroyed) return;
                        mNewTabPageView.playAnimatedLogo(animatedLogoImage);
                    }
                }, mAnimatedLogoUrl);
            } else if (mOnLogoClickUrl != null) {
                RecordHistogram.recordSparseSlowlyHistogram(LOGO_CLICK_UMA_NAME,
                        isAnimatedLogoShowing ? ANIMATED_LOGO_CLICKED : STATIC_LOGO_CLICKED);
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
                    if (logo != null) {
                        RecordHistogram.recordSparseSlowlyHistogram(LOGO_SHOWN_UMA_NAME,
                                logo.animatedLogoUrl == null ? STATIC_LOGO_SHOWN : CTA_IMAGE_SHOWN);
                    }
                    logoObserver.onLogoAvailable(logo, fromCache);
                }
            };
            mLogoBridge.getCurrentLogo(wrapperCallback);
        }

        @Override
        public void onLoadingComplete(MostVisitedItem[] items) {
            if (mIsDestroyed) return;

            long loadTimeMs = (System.nanoTime() - mConstructedTimeNs) / 1000000;
            RecordHistogram.recordTimesHistogram(
                    "Tab.NewTabOnload", loadTimeMs, TimeUnit.MILLISECONDS);
            mIsLoaded = true;
            StartupMetrics.getInstance().recordOpenedNTP();
            NewTabPageUma.recordNTPImpression(NewTabPageUma.NTP_IMPRESSION_REGULAR);
            // If not visible when loading completes, wait until onShown is received.
            if (!mTab.isHidden()) recordNTPShown();

            int tileTypes[] = new int[items.length];
            int sources[] = new int[items.length];

            for (int i = 0; i < items.length; i++) {
                tileTypes[i] = items[i].getTileType();
                sources[i] = items[i].getSource();
            }

            mMostVisitedSites.recordTileTypeMetrics(tileTypes, sources);

            if (isNtpOfflinePagesEnabled()) {
                final int maxNumTiles = 12;
                for (int i = 0; i < items.length; i++) {
                    if (items[i].isOfflineAvailable()) {
                        RecordHistogram.recordEnumeratedHistogram(
                                "NewTabPage.TileOfflineAvailable", i, maxNumTiles);
                    }
                }
            }
            SyncSessionsMetrics.recordYoungestForeignTabAgeOnNTP();
        }

        @Override
        public SuggestionsSource getSuggestionsSource() {
            if (sSuggestionsSourceForTests != null) return sSuggestionsSourceForTests;
            return mSnippetsBridge;
        }

        @Override
        public void addDestructionObserver(DestructionObserver destructionObserver) {
            if (mIsDestroyed) return;
            mDestructionObservers.addObserver(destructionObserver);
        }

        @Override
        public boolean isCurrentPage() {
            if (mIsDestroyed) return false;
            if (mFakeboxDelegate == null) return false;
            return mFakeboxDelegate.isCurrentPage(NewTabPage.this);
        }

        @Override
        public ContextMenuManager getContextMenuManager() {
            assert !mIsDestroyed;
            return mNewTabPageView.getContextMenuManager();
        }
    };

    /**
     * Constructs a NewTabPage.
     * @param activity The activity used for context to create the new tab page's View.
     * @param tab The Tab that is showing this new tab page.
     * @param tabModelSelector The TabModelSelector used to open tabs.
     */
    public NewTabPage(ChromeActivity activity, Tab tab, TabModelSelector tabModelSelector) {
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

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab) {
                // Showing the NTP is only meaningful when the page has been loaded already.
                if (mIsLoaded) recordNTPShown();
            }

            @Override
            public void onHidden(Tab tab) {
                if (mIsLoaded) recordNTPInteractionTime();
            }

            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                int scrollPosition = mNewTabPageView.getScrollPosition();
                if (scrollPosition == RecyclerView.NO_POSITION) return;

                if (mTab.getWebContents() == null) return;

                NavigationController controller = mTab.getWebContents().getNavigationController();
                int index = controller.getLastCommittedEntryIndex();
                NavigationEntry entry = controller.getEntryAtIndex(index);
                if (entry == null) return;

                // At least under test conditions this method may be called initially for the load
                // of the NTP itself, at which point the last committed entry is not for the NTP
                // yet. This method will then be called a second time when the user navigates away,
                // at which point the last committed entry is for the NTP. The extra data must only
                // be set in the latter case.
                if (!isNTPUrl(entry.getUrl())) return;

                controller.setEntryExtraData(index, NAVIGATION_ENTRY_SCROLL_POSITION_KEY,
                        Integer.toString(scrollPosition));
            }
        };
        mTab.addObserver(mTabObserver);
        mMostVisitedSites = buildMostVisitedSites(mProfile);
        mLogoBridge = new LogoBridge(mProfile);
        updateSearchProviderHasLogo();

        if (SnippetsConfig.isEnabled()) {
            mSnippetsBridge = new SnippetsBridge(mProfile);
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        mNewTabPageView = (NewTabPageView) inflater.inflate(R.layout.new_tab_page_view, null);
        mNewTabPageView.initialize(mNewTabPageManager, mActivity, mSearchProviderHasLogo,
                getScrollPositionFromNavigationEntry());

        DownloadManagerService.getDownloadManagerService(ContextUtils.getApplicationContext())
                .checkForExternallyRemovedDownloads(/*isOffRecord=*/false);

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

    private void showMostVisitedItemRemovedSnackbar(String url) {
        if (mMostVisitedItemRemovedController == null) {
            mMostVisitedItemRemovedController = new SnackbarController() {
                @Override
                public void onDismissNoAction(Object actionData) {}

                /** Undoes the most visited item removal. */
                @Override
                public void onAction(Object actionData) {
                    if (mIsDestroyed) return;
                    String url = (String) actionData;
                    mMostVisitedSites.removeBlacklistedUrl(url);
                }
            };
        }
        Context context = mNewTabPageView.getContext();
        Snackbar snackbar = Snackbar
                .make(context.getString(R.string.most_visited_item_removed),
                        mMostVisitedItemRemovedController, Snackbar.TYPE_ACTION,
                        Snackbar.UMA_NTP_MOST_VISITED_DELETE_UNDO)
                .setAction(context.getString(R.string.undo), url);
        mTab.getSnackbarManager().showSnackbar(snackbar);
    }

    /** @return The view container for the new tab page. */
    @VisibleForTesting
    public NewTabPageView getNewTabPageView() {
        return mNewTabPageView;
    }

    /** @return whether the NTP is using the cards UI. */
    public boolean isCardsUiEnabled() {
        return SnippetsConfig.isEnabled();
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

        return mSearchProviderHasLogo;
    }

    private void updateSearchProviderHasLogo() {
        mSearchProviderHasLogo = TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
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
     * @param bounds The current drawing location of the search box.
     * @param translation The translation applied to the search box by the parent view hierarchy up
     *                    to the NewTabPage view.
     */
    public void getSearchBoxBounds(Rect bounds, Point translation) {
        mNewTabPageView.getSearchBoxBounds(bounds, translation);
    }

    /**
     * Updates the opacity of the search box when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchBoxAlpha(float alpha) {
        mNewTabPageView.setSearchBoxAlpha(alpha);
    }

    /**
     * Updates the opacity of the search provider logo when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchProviderLogoAlpha(float alpha) {
        mNewTabPageView.setSearchProviderLogoAlpha(alpha);
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

            // The toolbar can't get the reference to the native page until its initialization is
            // finished, so we can't cache it here and transfer it to the view later. We pull that
            // state from the location bar when we get a reference to it as a workaround.
            mNewTabPageView.setUrlFocusChangeAnimationPercent(
                    fakeboxDelegate.isUrlBarFocused() ? 1f : 0f);
        }
    }

    /**
     * Records UMA for the NTP being shown. This includes a fresh page load or being brought to the
     * foreground.
     */
    private void recordNTPShown() {
        mLastShownTimeNs = System.nanoTime();
        RecordUserAction.record("MobileNTPShown");
    }

    private void recordNTPInteractionTime() {
        RecordHistogram.recordMediumTimesHistogram(
                "NewTabPage.TimeSpent", System.nanoTime() - mLastShownTimeNs, TimeUnit.NANOSECONDS);
    }

    /**
     * Returns the value of the adapter scroll position that was stored in the last committed
     * navigation entry. Returns {@code RecyclerView.NO_POSITION} if there is no last committed
     * navigation entry, or if no data is found.
     * @return The adapter scroll position.
     */
    private int getScrollPositionFromNavigationEntry() {
        if (mTab.getWebContents() == null) return RecyclerView.NO_POSITION;

        NavigationController controller = mTab.getWebContents().getNavigationController();
        int index = controller.getLastCommittedEntryIndex();
        String scrollPositionData =
                controller.getEntryExtraData(index, NAVIGATION_ENTRY_SCROLL_POSITION_KEY);
        if (TextUtils.isEmpty(scrollPositionData)) return RecyclerView.NO_POSITION;

        try {
            return Integer.parseInt(scrollPositionData);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Bad data found for scroll position: %s", scrollPositionData, e);
            return RecyclerView.NO_POSITION;
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
        assert !ViewCompat
                .isAttachedToWindow(getView()) : "Destroy called before removed from window";
        if (mIsLoaded && !mTab.isHidden()) recordNTPInteractionTime();

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
        if (mSnippetsBridge != null) {
            mSnippetsBridge.destroy();
            mSnippetsBridge = null;
        }
        if (mMostVisitedItemRemovedController != null) {
            mTab.getSnackbarManager().dismissSnackbars(mMostVisitedItemRemovedController);
        }
        for (DestructionObserver observer : mDestructionObservers) {
            observer.onDestroy();
        }
        mDestructionObservers.clear();
        TemplateUrlService.getInstance().removeObserver(this);
        mTab.removeObserver(mTabObserver);
        mTabObserver = null;
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
        return isLocationBarShownInNTP() ? mBackgroundColor : mThemeColor;
    }

    @Override
    public boolean needsToolbarShadow() {
        return !mSearchProviderHasLogo;
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
}
