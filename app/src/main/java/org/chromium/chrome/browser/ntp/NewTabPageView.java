// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.ntp.MostVisitedItem.MostVisitedItemManager;
import org.chromium.chrome.browser.ntp.NewTabPage.OnSearchBoxScrollListener;
import org.chromium.chrome.browser.ntp.cards.CardsVariationParameters;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.profiles.MostVisitedSites.MostVisitedURLsObserver;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jp.tomorrowkey.android.gifplayer.BaseGifImage;

/**
 * The native new tab page, represented by some basic data such as title and url, and an Android
 * View that displays the page.
 */
public class NewTabPageView extends FrameLayout
        implements MostVisitedURLsObserver, OnLayoutChangeListener {

    private static final int SHADOW_COLOR = 0x11000000;
    private static final long SNAP_SCROLL_DELAY_MS = 30;
    private static final String TAG = "Ntp";

    /**
     * Indicates which UI mode we are using. Should be checked when manipulating some members, as
     * they may be unused or {@code null} depending on the mode.
     */
    private boolean mUseCardsUi;

    // Note: Only one of these will be valid at a time, depending on if we are using the old NTP
    // (NewTabPageScrollView) or the new NTP with cards (NewTabPageRecyclerView).
    private NewTabPageScrollView mScrollView;
    private NewTabPageRecyclerView mRecyclerView;

    private NewTabPageLayout mNewTabPageLayout;
    private LogoView mSearchProviderLogoView;
    private ViewGroup mSearchBoxView;
    private ImageView mVoiceSearchButton;
    private MostVisitedLayout mMostVisitedLayout;
    private View mMostVisitedPlaceholder;
    private View mNoSearchLogoSpacer;

    /** Adapter for {@link #mRecyclerView}. Will be {@code null} when using the old UI */
    private NewTabPageAdapter mNewTabPageAdapter;

    private OnSearchBoxScrollListener mSearchBoxScrollListener;

    private NewTabPageManager mManager;
    private UiConfig mUiConfig;
    private MostVisitedDesign mMostVisitedDesign;
    private MostVisitedItem[] mMostVisitedItems;
    private boolean mFirstShow = true;
    private boolean mSearchProviderHasLogo = true;
    private boolean mHasReceivedMostVisitedSites;
    private boolean mPendingSnapScroll;

    /**
     * The number of asynchronous tasks that need to complete before the page is done loading.
     * This starts at one to track when the view is finished attaching to the window.
     */
    private int mPendingLoadTasks = 1;
    private boolean mLoadHasCompleted;

    private float mUrlFocusChangePercent;
    private boolean mDisableUrlFocusChangeAnimations;

    /** Flag used to request some layout changes after the next layout pass is completed. */
    private boolean mTileCountChanged;
    private boolean mSnapshotMostVisitedChanged;
    private int mSnapshotWidth;
    private int mSnapshotHeight;
    private int mSnapshotScrollY;

    /**
     * Manages the view interaction with the rest of the system.
     */
    public interface NewTabPageManager extends MostVisitedItemManager {
        /** @return Whether the location bar is shown in the NTP. */
        boolean isLocationBarShownInNTP();

        /** @return Whether voice search is enabled and the microphone should be shown. */
        boolean isVoiceSearchEnabled();

        /** @return Whether the omnibox 'Search or type URL' text should be shown. */
        boolean isFakeOmniboxTextEnabledTablet();

        /** @return Whether context menus should allow the option to open a link in a new window. */
        boolean isOpenInNewWindowEnabled();

        /** @return Whether context menus should allow the option to open a link in incognito. */
        boolean isOpenInIncognitoEnabled();

        /** Opens the bookmarks page in the current tab. */
        void navigateToBookmarks();

        /** Opens the recent tabs page in the current tab. */
        void navigateToRecentTabs();

        /** Opens the Download Manager UI in the current tab. */
        void navigateToDownloadManager();

        /**
         * Tracks per-page-load metrics for content suggestions.
         * @param categories The categories of content suggestions.
         * @param suggestionsPerCategory The number of content suggestions in each category.
         */
        void trackSnippetsPageImpression(int[] categories, int[] suggestionsPerCategory);

        /**
         * Tracks impression metrics for a content suggestion.
         * @param article The content suggestion that was shown to the user.
         */
        void trackSnippetImpression(SnippetArticle article);

        /**
         * Tracks impression metrics for the long-press menu for a content suggestion.
         * @param article The content suggestion for which the long-press menu was opened.
         */
        void trackSnippetMenuOpened(SnippetArticle article);

        /**
         * Tracks impression metrics for a category's action button ("More").
         * @param category The category for which the action button was shown.
         * @param position The position of the action button within the category.
         */
        void trackSnippetCategoryActionImpression(int category, int position);

        /**
         * Tracks click metrics for a category's action button ("More").
         * @param category The category for which the action button was clicked.
         * @param position The position of the action button within the category.
         */
        void trackSnippetCategoryActionClick(int category, int position);

        /**
         * Opens a content suggestion and records related metrics.
         * @param windowOpenDisposition How to open (current tab, new tab, new window etc).
         * @param article The content suggestion to open.
         */
        void openSnippet(int windowOpenDisposition, SnippetArticle article);

        /**
         * Animates the search box up into the omnibox and bring up the keyboard.
         * @param beginVoiceSearch Whether to begin a voice search.
         * @param pastedText Text to paste in the omnibox after it's been focused. May be null.
         */
        void focusSearchBox(boolean beginVoiceSearch, String pastedText);

        /**
         * Gets the list of most visited sites.
         * @param observer The observer to be notified with the list of sites.
         * @param numResults The maximum number of sites to retrieve.
         */
        void setMostVisitedURLsObserver(MostVisitedURLsObserver observer, int numResults);

        /**
         * Gets the favicon image for a given URL.
         * @param url The URL of the site whose favicon is being requested.
         * @param size The desired size of the favicon in pixels.
         * @param faviconCallback The callback to be notified when the favicon is available.
         */
        void getLocalFaviconImageForURL(
                String url, int size, FaviconImageCallback faviconCallback);

        /**
         * Gets the large icon (e.g. favicon or touch icon) for a given URL.
         * @param url The URL of the site whose icon is being requested.
         * @param size The desired size of the icon in pixels.
         * @param callback The callback to be notified when the icon is available.
         */
        void getLargeIconForUrl(String url, int size, LargeIconCallback callback);

        /**
         * Checks if an icon with the given URL is available. If not,
         * downloads it and stores it as a favicon/large icon for the given {@code pageUrl}.
         * @param pageUrl The URL of the site whose icon is being requested.
         * @param iconUrl The URL of the favicon/large icon.
         * @param isLargeIcon Whether the {@code iconUrl} represents a large icon or favicon.
         * @param callback The callback to be notified when the favicon has been checked.
         */
        void ensureIconIsAvailable(String pageUrl, String iconUrl, boolean isLargeIcon,
                boolean isTemporary, IconAvailabilityCallback callback);

        /**
         * Checks if the pages with the given URLs are available offline.
         * @param pageUrls The URLs of the sites whose offline availability is requested.
         * @param callback Fired when the results are available.
         */
        void getUrlsAvailableOffline(Set<String> pageUrls, Callback<Set<String>> callback);

        /**
         * Called when the user clicks on the logo.
         * @param isAnimatedLogoShowing Whether the animated GIF logo is playing.
         */
        void onLogoClicked(boolean isAnimatedLogoShowing);

        /**
         * Gets the default search provider's logo and calls logoObserver with the result.
         * @param logoObserver The callback to notify when the logo is available.
         */
        void getSearchProviderLogo(LogoObserver logoObserver);

        /**
         * Called when the NTP has completely finished loading (all views will be inflated
         * and any dependent resources will have been loaded).
         * @param mostVisitedItems The MostVisitedItem shown on the NTP. Used to record metrics.
         */
        void onLoadingComplete(MostVisitedItem[] mostVisitedItems);

        /**
         * Passes a {@link Callback} along to the activity to be called whenever a ContextMenu is
         * closed.
         */
        void addContextMenuCloseCallback(Callback<Menu> callback);

        /**
         * Passes a {@link Callback} along to the activity to be removed from the list of Callbacks
         * called whenever a ContextMenu is closed.
         */
        void removeContextMenuCloseCallback(Callback<Menu> callback);

        /**
         * Makes the {@link Activity} close any open context menu.
         */
        void closeContextMenu();

        /**
         * Handles clicks on the "learn more" link in the footer.
         */
        void onLearnMoreClicked();

        /**
         * Returns the SuggestionsSource or null if it doesn't exist. The SuggestionsSource is
         * invalidated (has destroy() called) when the NewTabPage is destroyed so use this method
         * instead of keeping your own reference.
         */
        @Nullable SuggestionsSource getSuggestionsSource();

        /**
         * Registers a {@link SignInStateObserver}, will handle the de-registration when the New Tab
         * Page goes away.
         */
        void registerSignInStateObserver(SignInStateObserver signInStateObserver);

        /**
         * @return whether the {@link NewTabPage} associated with this manager is the current page
         * displayed to the user.
         */
        boolean isCurrentPage();
    }

    /**
     * Default constructor required for XML inflation.
     */
    public NewTabPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes the NTP. This must be called immediately after inflation, before this object is
     * used in any other way.
     *
     * @param manager NewTabPageManager used to perform various actions when the user interacts
     *                with the page.
     * @param searchProviderHasLogo Whether the search provider has a logo.
     * @param scrollPosition The adapter scroll position to initialize to.
     */
    public void initialize(
            NewTabPageManager manager, boolean searchProviderHasLogo, int scrollPosition) {
        mManager = manager;
        mUiConfig = new UiConfig(this);
        ViewStub stub = (ViewStub) findViewById(R.id.new_tab_page_layout_stub);

        mUseCardsUi = manager.getSuggestionsSource() != null;
        if (mUseCardsUi) {
            stub.setLayoutResource(R.layout.new_tab_page_recycler_view);
            mRecyclerView = (NewTabPageRecyclerView) stub.inflate();

            // Don't attach now, the recyclerView itself will determine when to do it.
            mNewTabPageLayout =
                    (NewTabPageLayout) LayoutInflater.from(getContext())
                            .inflate(R.layout.new_tab_page_layout, mRecyclerView, false);
            mNewTabPageLayout.setUseCardsUiEnabled(mUseCardsUi);
            mRecyclerView.setAboveTheFoldView(mNewTabPageLayout);

            // Tailor the LayoutParams for the snippets UI, as the configuration in the XML is
            // made for the ScrollView UI.
            ViewGroup.LayoutParams params = mNewTabPageLayout.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;

            mRecyclerView.setItemAnimator(new DefaultItemAnimator() {
                @Override
                public void onAnimationFinished(ViewHolder viewHolder) {
                    super.onAnimationFinished(viewHolder);
                    // When removing sections, because the animations are all translations, the
                    // scroll events don't fire and we can get in the situation where the toolbar
                    // buttons disappear.
                    updateSearchBoxOnScroll();
                }
            });
        } else {
            stub.setLayoutResource(R.layout.new_tab_page_scroll_view);
            mScrollView = (NewTabPageScrollView) stub.inflate();
            mScrollView.setBackgroundColor(
                    NtpStyleUtils.getBackgroundColorResource(getResources(), false));
            mScrollView.enableBottomShadow(SHADOW_COLOR);
            mNewTabPageLayout = (NewTabPageLayout) findViewById(R.id.ntp_content);
        }

        mMostVisitedDesign = new MostVisitedDesign(getContext());
        mMostVisitedLayout =
                (MostVisitedLayout) mNewTabPageLayout.findViewById(R.id.most_visited_layout);
        mMostVisitedDesign.initMostVisitedLayout(searchProviderHasLogo);

        mSearchProviderLogoView =
                (LogoView) mNewTabPageLayout.findViewById(R.id.search_provider_logo);
        mSearchBoxView = (ViewGroup) mNewTabPageLayout.findViewById(R.id.search_box);
        mNoSearchLogoSpacer = mNewTabPageLayout.findViewById(R.id.no_search_logo_spacer);

        initializeSearchBoxTextView();
        initializeVoiceSearchButton();
        initializeBottomToolbar();

        mNewTabPageLayout.addOnLayoutChangeListener(this);
        setSearchProviderHasLogo(searchProviderHasLogo);

        mPendingLoadTasks++;
        mManager.setMostVisitedURLsObserver(
                this, mMostVisitedDesign.getNumberOfTiles(searchProviderHasLogo));

        // Set up snippets
        if (mUseCardsUi) {
            mNewTabPageAdapter = NewTabPageAdapter.create(mManager, mNewTabPageLayout, mUiConfig);
            mRecyclerView.setAdapter(mNewTabPageAdapter);
            mRecyclerView.scrollToPosition(scrollPosition);

            if (CardsVariationParameters.isScrollBelowTheFoldEnabled()) {
                int searchBoxHeight = NtpStyleUtils.getSearchBoxHeight(getResources());
                mRecyclerView.getLinearLayoutManager().scrollToPositionWithOffset(
                        mNewTabPageAdapter.getFirstHeaderPosition(), searchBoxHeight);
            }

            // Set up swipe-to-dismiss
            ItemTouchHelper helper =
                    new ItemTouchHelper(mNewTabPageAdapter.getItemTouchCallbacks());
            helper.attachToRecyclerView(mRecyclerView);

            mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                private boolean mScrolledOnce = false;
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState != RecyclerView.SCROLL_STATE_DRAGGING) return;
                    RecordUserAction.record("MobileNTP.Snippets.Scrolled");
                    if (mScrolledOnce) return;
                    mScrolledOnce = true;
                    NewTabPageUma.recordSnippetAction(NewTabPageUma.SNIPPETS_ACTION_SCROLLED);
                }
            });
            initializeSearchBoxRecyclerViewScrollHandling();
        } else {
            initializeSearchBoxScrollHandling();
        }
    }

    /**
     * Sets up the hint text and event handlers for the search box text view.
     */
    private void initializeSearchBoxTextView() {
        final TextView searchBoxTextView = (TextView) mSearchBoxView
                .findViewById(R.id.search_box_text);
        String hintText = getResources().getString(R.string.search_or_type_url);

        if (!DeviceFormFactor.isTablet(getContext()) || mManager.isFakeOmniboxTextEnabledTablet()) {
            searchBoxTextView.setHint(hintText);
        } else {
            searchBoxTextView.setContentDescription(hintText);
        }
        searchBoxTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mManager.focusSearchBox(false, null);
            }
        });
        searchBoxTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) return;
                mManager.focusSearchBox(false, s.toString());
                searchBoxTextView.setText("");
            }
        });
    }

    private void initializeVoiceSearchButton() {
        mVoiceSearchButton = (ImageView) mNewTabPageLayout.findViewById(R.id.voice_search_button);
        mVoiceSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mManager.focusSearchBox(true, null);
            }
        });
    }

    /**
     * Sets up event listeners for the bottom toolbar if it is enabled. Removes the bottom toolbar
     * if it is disabled.
     */
    private void initializeBottomToolbar() {
        NewTabPageToolbar toolbar = (NewTabPageToolbar) findViewById(R.id.ntp_toolbar);
        if (SnippetsConfig.isEnabled()) {
            ((ViewGroup) toolbar.getParent()).removeView(toolbar);
            MarginLayoutParams params = (MarginLayoutParams) getWrapperView().getLayoutParams();
            params.bottomMargin = 0;
        } else {
            toolbar.getRecentTabsButton().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mManager.navigateToRecentTabs();
                }
            });
            toolbar.getBookmarksButton().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mManager.navigateToBookmarks();
                }
            });
        }
    }

    private void updateSearchBoxOnScroll() {
        if (mDisableUrlFocusChangeAnimations) return;

        // When the page changes (tab switching or new page loading), it is possible that events
        // (e.g. delayed RecyclerView change notifications) trigger calls to these methods after
        // the current page changes. We check it again to make sure we don't attempt to update the
        // wrong page.
        if (!mManager.isCurrentPage()) return;

        if (mSearchBoxScrollListener != null) {
            mSearchBoxScrollListener.onNtpScrollChanged(getToolbarTransitionPercentage());
        }
    }

    /**
     * Calculates the percentage (between 0 and 1) of the transition from the search box to the
     * omnibox at the top of the New Tab Page, which is determined by the amount of scrolling and
     * the position of the search box.
     *
     * @return the transition percentage
     */
    private float getToolbarTransitionPercentage() {
        // During startup the view may not be fully initialized, so we only calculate the current
        // percentage if some basic view properties (height of the containing view, position of the
        // search box) are sane.
        if (getWrapperView().getHeight() == 0) return 0f;

        if (mUseCardsUi && !mRecyclerView.isFirstItemVisible()) {
            // getVerticalScroll is valid only for the RecyclerView if the first item is visible.
            // If the first item is not visible, we must have scrolled quite far and we know the
            // toolbar transition should be 100%. This might be the initial scroll position due to
            // the scroll restore feature, so the search box will not have been laid out yet.
            return 1f;
        }

        int searchBoxTop = mSearchBoxView.getTop();
        if (searchBoxTop == 0) return 0f;

        // For all other calculations, add the search box padding, because it defines where the
        // visible "border" of the search box is.
        searchBoxTop += mSearchBoxView.getPaddingTop();

        if (!mUseCardsUi) {
            return MathUtils.clamp(getVerticalScroll() / (float) searchBoxTop, 0f, 1f);
        }

        final int scrollY = getVerticalScroll();
        final float transitionLength =
                getResources().getDimension(R.dimen.ntp_search_box_transition_length);
        // Tab strip height is zero on phones, nonzero on tablets.
        int tabStripHeight = getResources().getDimensionPixelSize(R.dimen.tab_strip_height);

        // |scrollY - searchBoxTop + tabStripHeight| gives the distance the search bar is from the
        // top of the tab.
        return MathUtils.clamp((scrollY - searchBoxTop + transitionLength + tabStripHeight)
                / transitionLength, 0f, 1f);
    }

    private ViewGroup getWrapperView() {
        return mUseCardsUi ? mRecyclerView : mScrollView;
    }

    /**
     * Sets up scrolling when snippets are enabled. It adds scroll listeners and touch listeners to
     * the RecyclerView.
     */
    private void initializeSearchBoxRecyclerViewScrollHandling() {
        final Runnable mSnapScrollRunnable = new Runnable() {
            @Override
            public void run() {
                assert mPendingSnapScroll;
                mPendingSnapScroll = false;

                mRecyclerView.snapScroll(mSearchBoxView, getVerticalScroll(), getHeight());
            }
        };

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (mPendingSnapScroll) {
                    mRecyclerView.removeCallbacks(mSnapScrollRunnable);
                    mRecyclerView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
                }
                updateSearchBoxOnScroll();
                mRecyclerView.updatePeekingCardAndHeader();
            }
        });

        mRecyclerView.setOnTouchListener(new OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                mRecyclerView.removeCallbacks(mSnapScrollRunnable);

                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                        || event.getActionMasked() == MotionEvent.ACTION_UP) {
                    mPendingSnapScroll = true;
                    mRecyclerView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
                } else {
                    mPendingSnapScroll = false;
                }
                return false;
            }
        });
    }

    /**
     * Sets up scrolling when snippets are disabled. It adds scroll and touch listeners to the
     * scroll view.
     */
    private void initializeSearchBoxScrollHandling() {
        final Runnable mSnapScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mPendingSnapScroll) return;
                int scrollY = mScrollView.getScrollY();
                int dividerTop = mMostVisitedLayout.getTop() - mNewTabPageLayout.getPaddingTop();
                if (scrollY > 0 && scrollY < dividerTop) {
                    mScrollView.smoothScrollTo(0, scrollY < (dividerTop / 2) ? 0 : dividerTop);
                }
                mPendingSnapScroll = false;
            }
        };
        mScrollView.setOnScrollListener(new NewTabPageScrollView.OnScrollListener() {
            @Override
            public void onScrollChanged(int l, int t, int oldl, int oldt) {
                if (mPendingSnapScroll) {
                    mScrollView.removeCallbacks(mSnapScrollRunnable);
                    mScrollView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
                }
                updateSearchBoxOnScroll();
            }
        });
        mScrollView.setOnTouchListener(new OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                mScrollView.removeCallbacks(mSnapScrollRunnable);

                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                        || event.getActionMasked() == MotionEvent.ACTION_UP) {
                    mPendingSnapScroll = true;
                    mScrollView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
                } else {
                    mPendingSnapScroll = false;
                }
                return false;
            }
        });
    }

    /**
     * Decrements the count of pending load tasks and notifies the manager when the page load
     * is complete.
     */
    private void loadTaskCompleted() {
        assert mPendingLoadTasks > 0;
        mPendingLoadTasks--;
        if (mPendingLoadTasks == 0) {
            if (mLoadHasCompleted) {
                assert false;
            } else {
                mLoadHasCompleted = true;
                mManager.onLoadingComplete(mMostVisitedItems);
                // Load the logo after everything else is finished, since it's lower priority.
                loadSearchProviderLogo();
            }
        }
    }

    /**
     * Loads the search provider logo (e.g. Google doodle), if any.
     */
    private void loadSearchProviderLogo() {
        mManager.getSearchProviderLogo(new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (logo == null && fromCache) return;
                mSearchProviderLogoView.setMananger(mManager);
                mSearchProviderLogoView.updateLogo(logo);
                mSnapshotMostVisitedChanged = true;
            }
        });
    }

    /**
     * Changes the layout depending on whether the selected search provider (e.g. Google, Bing)
     * has a logo.
     * @param hasLogo Whether the search provider has a logo.
     */
    public void setSearchProviderHasLogo(boolean hasLogo) {
        if (hasLogo == mSearchProviderHasLogo) return;
        mSearchProviderHasLogo = hasLogo;

        mMostVisitedDesign.setSearchProviderHasLogo(mMostVisitedLayout, hasLogo);

        // Hide or show all the views above the Most Visited items.
        int visibility = hasLogo ? View.VISIBLE : View.GONE;
        int childCount = mNewTabPageLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mNewTabPageLayout.getChildAt(i);
            if (child == mMostVisitedLayout) break;
            // Don't change the visibility of a ViewStub as that will automagically inflate it.
            if (child instanceof ViewStub) continue;
            child.setVisibility(visibility);
        }

        updateMostVisitedPlaceholderVisibility();

        onUrlFocusAnimationChanged();

        mSnapshotMostVisitedChanged = true;
    }

    /**
     * Updates whether the NewTabPage should animate on URL focus changes.
     * @param disable Whether to disable the animations.
     */
    void setUrlFocusAnimationsDisabled(boolean disable) {
        if (disable == mDisableUrlFocusChangeAnimations) return;
        mDisableUrlFocusChangeAnimations = disable;
        if (!disable) onUrlFocusAnimationChanged();
    }

    /**
     * Shows a progressbar indicating the animated logo is being downloaded.
     */
    void showLogoLoadingView() {
        mSearchProviderLogoView.showLoadingView();
    }

    /**
     * Starts playing the given animated GIF logo.
     */
    void playAnimatedLogo(BaseGifImage gifImage) {
        mSearchProviderLogoView.playAnimatedLogo(gifImage);
    }

    /**
     * @return Whether URL focus animations are currently disabled.
     */
    boolean urlFocusAnimationsDisabled() {
        return mDisableUrlFocusChangeAnimations;
    }

    /**
     * Specifies the percentage the URL is focused during an animation.  1.0 specifies that the URL
     * bar has focus and has completed the focus animation.  0 is when the URL bar is does not have
     * any focus.
     *
     * @param percent The percentage of the URL bar focus animation.
     */
    void setUrlFocusChangeAnimationPercent(float percent) {
        mUrlFocusChangePercent = percent;
        onUrlFocusAnimationChanged();
    }

    /**
     * @return The percentage that the URL bar is focused during an animation.
     */
    @VisibleForTesting
    float getUrlFocusChangeAnimationPercent() {
        return mUrlFocusChangePercent;
    }

    private void onUrlFocusAnimationChanged() {
        if (mDisableUrlFocusChangeAnimations) return;

        float percent = mSearchProviderHasLogo ? mUrlFocusChangePercent : 0;

        int basePosition = getVerticalScroll() + mNewTabPageLayout.getPaddingTop();
        int target;
        if (mUseCardsUi) {
            // Cards UI: translate so that the search box is at the top, but only upwards.
            target = Math.max(basePosition,
                    mSearchBoxView.getBottom() - mSearchBoxView.getPaddingBottom());
        } else {
            // Otherwise: translate so that Most Visited is right below the omnibox.
            target = mMostVisitedLayout.getTop();
        }
        mNewTabPageLayout.setTranslationY(percent * (basePosition - target));
    }

    /**
     * Updates the opacity of the search box when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchBoxAlpha(float alpha) {
        mSearchBoxView.setAlpha(alpha);

        // Disable the search box contents if it is the process of being animated away.
        for (int i = 0; i < mSearchBoxView.getChildCount(); i++) {
            mSearchBoxView.getChildAt(i).setEnabled(mSearchBoxView.getAlpha() == 1.0f);
        }

    }

    /**
     * Updates the opacity of the search provider logo when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchProviderLogoAlpha(float alpha) {
        mSearchProviderLogoView.setAlpha(alpha);
    }

    /**
     * Get the bounds of the search box in relation to the top level NewTabPage view.
     *
     * @param bounds The current drawing location of the search box.
     * @param translation The translation applied to the search box by the parent view hierarchy up
     *                    to the NewTabPage view.
     */
    void getSearchBoxBounds(Rect bounds, Point translation) {
        int searchBoxX = (int) mSearchBoxView.getX();
        int searchBoxY = (int) mSearchBoxView.getY();

        bounds.set(searchBoxX + mSearchBoxView.getPaddingLeft(),
                searchBoxY + mSearchBoxView.getPaddingTop(),
                searchBoxX + mSearchBoxView.getWidth() - mSearchBoxView.getPaddingRight(),
                searchBoxY + mSearchBoxView.getHeight() - mSearchBoxView.getPaddingBottom());

        translation.set(0, 0);

        View view = mSearchBoxView;
        while (true) {
            view = (View) view.getParent();
            if (view == null) {
                // The |mSearchBoxView| is not a child of this view. This can happen if the
                // RecyclerView detaches the NewTabPageLayout after it has been scrolled out of
                // view. Set the translation to the minimum Y value as an approximation.
                translation.y = Integer.MIN_VALUE;
                break;
            }
            translation.offset(-view.getScrollX(), -view.getScrollY());
            if (view == this) break;
            translation.offset((int) view.getX(), (int) view.getY());
        }
        bounds.offset(translation.x, translation.y);
    }

    /**
     * Sets the listener for search box scroll changes.
     * @param listener The listener to be notified on changes.
     */
    void setSearchBoxScrollListener(OnSearchBoxScrollListener listener) {
        mSearchBoxScrollListener = listener;
        if (mSearchBoxScrollListener != null) updateSearchBoxOnScroll();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        assert mManager != null;

        if (mFirstShow) {
            loadTaskCompleted();
            mFirstShow = false;
        } else {
            // Trigger a scroll update when reattaching the window to signal the toolbar that
            // it needs to reset the NTP state.
            if (mManager.isLocationBarShownInNTP()) updateSearchBoxOnScroll();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setUrlFocusChangeAnimationPercent(0f);
    }

    /**
     * Update the visibility of the voice search button based on whether the feature is currently
     * enabled.
     */
    void updateVoiceSearchButtonVisibility() {
        mVoiceSearchButton.setVisibility(mManager.isVoiceSearchEnabled() ? VISIBLE : GONE);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);

        if (visibility == VISIBLE) {
            updateVoiceSearchButtonVisibility();
        }
    }

    /**
     * @see org.chromium.chrome.browser.compositor.layouts.content.
     *         InvalidationAwareThumbnailProvider#shouldCaptureThumbnail()
     */
    boolean shouldCaptureThumbnail() {
        if (getWidth() == 0 || getHeight() == 0) return false;

        return mSnapshotMostVisitedChanged || getWidth() != mSnapshotWidth
                || getHeight() != mSnapshotHeight || getVerticalScroll() != mSnapshotScrollY;
    }

    /**
     * @see org.chromium.chrome.browser.compositor.layouts.content.
     *         InvalidationAwareThumbnailProvider#captureThumbnail(Canvas)
     */
    void captureThumbnail(Canvas canvas) {
        mSearchProviderLogoView.endFadeAnimation();
        ViewUtils.captureBitmap(this, canvas);
        mSnapshotWidth = getWidth();
        mSnapshotHeight = getHeight();
        mSnapshotScrollY = getVerticalScroll();
        mSnapshotMostVisitedChanged = false;
    }

    // OnLayoutChangeListener overrides

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int oldHeight = oldBottom - oldTop;
        int newHeight = bottom - top;

        if (oldHeight == newHeight && !mTileCountChanged) return;
        mTileCountChanged = false;

        // Re-apply the url focus change amount after a rotation to ensure the views are correctly
        // placed with their new layout configurations.
        onUrlFocusAnimationChanged();
        updateSearchBoxOnScroll();

        if (mUseCardsUi) {
            mRecyclerView.updatePeekingCardAndHeader();
            // The positioning of elements may have been changed (since the elements expand to fill
            // the available vertical space), so adjust the scroll.
            mRecyclerView.snapScroll(mSearchBoxView, getVerticalScroll(), getHeight());
        }
    }

    // MostVisitedURLsObserver implementation

    @Override
    public void onMostVisitedURLsAvailable(final String[] titles, final String[] urls,
            final String[] whitelistIconPaths, final int[] sources) {
        Set<String> urlSet = new HashSet<>(Arrays.asList(urls));

        // If no Most Visited items have been built yet, this is the initial load. Build the Most
        // Visited items immediately so the layout is stable during initial rendering. They can be
        // replaced later if there are offline urls, but that will not affect the layout widths and
        // heights. A stable layout enables reliable scroll position initialization.
        if (!mHasReceivedMostVisitedSites) {
            buildMostVisitedItems(titles, urls, whitelistIconPaths, null, sources);
        }

        // TODO(https://crbug.com/607573): We should show offline-available content in a nonblocking
        // way so that responsiveness of the NTP does not depend on ready availability of offline
        // pages.
        mManager.getUrlsAvailableOffline(urlSet, new Callback<Set<String>>() {
            @Override
            public void onResult(Set<String> offlineUrls) {
                buildMostVisitedItems(titles, urls, whitelistIconPaths, offlineUrls, sources);
            }
        });
    }

    private void buildMostVisitedItems(final String[] titles, final String[] urls,
            final String[] whitelistIconPaths, @Nullable final Set<String> offlineUrls,
            final int[] sources) {
        mMostVisitedLayout.removeAllViews();

        MostVisitedItem[] oldItems = mMostVisitedItems;
        int oldItemCount = oldItems == null ? 0 : oldItems.length;
        mMostVisitedItems = new MostVisitedItem[titles.length];

        final boolean isInitialLoad = !mHasReceivedMostVisitedSites;
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Add the most visited items to the page.
        for (int i = 0; i < titles.length; i++) {
            final String url = urls[i];
            final String title = titles[i];
            final String whitelistIconPath = whitelistIconPaths[i];
            final int source = sources[i];

            boolean offlineAvailable = offlineUrls != null && offlineUrls.contains(url);

            // Look for an existing item to reuse.
            MostVisitedItem item = null;
            for (int j = 0; j < oldItemCount; j++) {
                MostVisitedItem oldItem = oldItems[j];
                if (oldItem != null && TextUtils.equals(url, oldItem.getUrl())
                        && TextUtils.equals(title, oldItem.getTitle())
                        && offlineAvailable == oldItem.isOfflineAvailable()
                        && whitelistIconPath.equals(oldItem.getWhitelistIconPath())) {
                    item = oldItem;
                    item.setIndex(i);
                    oldItems[j] = null;
                    break;
                }
            }

            // If nothing can be reused, create a new item.
            if (item == null) {
                item = new MostVisitedItem(mManager, title, url, whitelistIconPath,
                        offlineAvailable, i, source);
                View view =
                        mMostVisitedDesign.createMostVisitedItemView(inflater, item, isInitialLoad);
                item.initView(view);
            }

            mMostVisitedItems[i] = item;
            mMostVisitedLayout.addView(item.getView());
        }

        mHasReceivedMostVisitedSites = true;
        updateMostVisitedPlaceholderVisibility();

        if (mUrlFocusChangePercent == 1f && oldItemCount != mMostVisitedItems.length) {
            // If the number of NTP Tile rows change while the URL bar is focused, the icons'
            // position will be wrong. Schedule the translation to be updated.
            mTileCountChanged = true;
        }

        if (isInitialLoad) {
            loadTaskCompleted();
            // The page contents are initially hidden; otherwise they'll be drawn centered on the
            // page before the most visited sites are available and then jump upwards to make space
            // once the most visited sites are available.
            mNewTabPageLayout.setVisibility(View.VISIBLE);
        }
        mSnapshotMostVisitedChanged = true;
    }

    @Override
    public void onPopularURLsAvailable(
            String[] urls, String[] faviconUrls, String[] largeIconUrls) {
        for (int i = 0; i < urls.length; i++) {
            final String url = urls[i];
            boolean useLargeIcon = !largeIconUrls[i].isEmpty();
            // Only fetch one of favicon or large icon based on what is required on the NTP.
            // The other will be fetched on visiting the site.
            String iconUrl = useLargeIcon ? largeIconUrls[i] : faviconUrls[i];
            if (iconUrl.isEmpty()) continue;

            IconAvailabilityCallback callback = new IconAvailabilityCallback() {
                @Override
                public void onIconAvailabilityChecked(boolean newlyAvailable) {
                    if (newlyAvailable) {
                        mMostVisitedDesign.onIconUpdated(url);
                    }
                }
            };
            mManager.ensureIconIsAvailable(
                    url, iconUrl, useLargeIcon, /*isTemporary=*/false, callback);
        }
    }

    /**
     * Shows the most visited placeholder ("Nothing to see here") if there are no most visited
     * items and there is no search provider logo.
     */
    private void updateMostVisitedPlaceholderVisibility() {
        boolean showPlaceholder = mHasReceivedMostVisitedSites
                && mMostVisitedLayout.getChildCount() == 0
                && !mSearchProviderHasLogo;

        mNoSearchLogoSpacer.setVisibility(
                (mSearchProviderHasLogo || showPlaceholder) ? View.GONE : View.INVISIBLE);

        if (showPlaceholder) {
            if (mMostVisitedPlaceholder == null) {
                ViewStub mostVisitedPlaceholderStub = (ViewStub) mNewTabPageLayout
                        .findViewById(R.id.most_visited_placeholder_stub);

                mMostVisitedPlaceholder = mostVisitedPlaceholderStub.inflate();
            }
            mMostVisitedLayout.setVisibility(GONE);
            mMostVisitedPlaceholder.setVisibility(VISIBLE);
        } else if (mMostVisitedPlaceholder != null) {
            mMostVisitedLayout.setVisibility(VISIBLE);
            mMostVisitedPlaceholder.setVisibility(GONE);
        }
    }

    /**
     * The design for most visited tiles: each tile shows a large icon and the site's title.
     */
    private class MostVisitedDesign {

        private static final int NUM_TILES = 8;
        private static final int NUM_TILES_NO_LOGO = 12;
        private static final int MAX_ROWS = 2;
        private static final int MAX_ROWS_NO_LOGO = 3;

        private static final int ICON_CORNER_RADIUS_DP = 4;
        private static final int ICON_TEXT_SIZE_DP = 20;
        private static final int ICON_MIN_SIZE_PX = 48;

        private int mMinIconSize;
        private int mDesiredIconSize;
        private RoundedIconGenerator mIconGenerator;

        MostVisitedDesign(Context context) {
            Resources res = context.getResources();
            mDesiredIconSize = res.getDimensionPixelSize(R.dimen.most_visited_icon_size);
            // On ldpi devices, mDesiredIconSize could be even smaller than ICON_MIN_SIZE_PX.
            mMinIconSize = Math.min(mDesiredIconSize, ICON_MIN_SIZE_PX);
            int desiredIconSizeDp = Math.round(
                    mDesiredIconSize / res.getDisplayMetrics().density);
            int iconColor = ApiCompatibilityUtils.getColor(
                    getResources(), R.color.default_favicon_background_color);
            mIconGenerator = new RoundedIconGenerator(context, desiredIconSizeDp, desiredIconSizeDp,
                    ICON_CORNER_RADIUS_DP, iconColor, ICON_TEXT_SIZE_DP);
        }

        public int getNumberOfTiles(boolean searchProviderHasLogo) {
            return searchProviderHasLogo ? NUM_TILES : NUM_TILES_NO_LOGO;
        }

        public void initMostVisitedLayout(boolean searchProviderHasLogo) {
            mMostVisitedLayout.setMaxRows(searchProviderHasLogo ? MAX_ROWS : MAX_ROWS_NO_LOGO);
        }

        public void setSearchProviderHasLogo(View mostVisitedLayout, boolean hasLogo) {
            int paddingTop = getResources().getDimensionPixelSize(hasLogo
                    ? R.dimen.most_visited_layout_padding_top
                    : R.dimen.most_visited_layout_no_logo_padding_top);
            mostVisitedLayout.setPadding(0, paddingTop, 0, mMostVisitedLayout.getPaddingBottom());
        }

        class LargeIconCallbackImpl implements LargeIconCallback {
            private MostVisitedItem mItem;
            private MostVisitedItemView mItemView;
            private boolean mIsInitialLoad;

            public LargeIconCallbackImpl(
                    MostVisitedItem item, MostVisitedItemView itemView, boolean isInitialLoad) {
                mItem = item;
                mItemView = itemView;
                mIsInitialLoad = isInitialLoad;
            }

            @Override
            public void onLargeIconAvailable(
                    Bitmap icon, int fallbackColor, boolean isFallbackColorDefault) {
                if (icon == null) {
                    mIconGenerator.setBackgroundColor(fallbackColor);
                    icon = mIconGenerator.generateIconForUrl(mItem.getUrl());
                    mItemView.setIcon(new BitmapDrawable(getResources(), icon));
                    mItem.setTileType(isFallbackColorDefault ? MostVisitedTileType.ICON_DEFAULT
                                                             : MostVisitedTileType.ICON_COLOR);
                } else {
                    RoundedBitmapDrawable roundedIcon = RoundedBitmapDrawableFactory.create(
                            getResources(), icon);
                    int cornerRadius = Math.round(ICON_CORNER_RADIUS_DP
                            * getResources().getDisplayMetrics().density * icon.getWidth()
                            / mDesiredIconSize);
                    roundedIcon.setCornerRadius(cornerRadius);
                    roundedIcon.setAntiAlias(true);
                    roundedIcon.setFilterBitmap(true);
                    mItemView.setIcon(roundedIcon);
                    mItem.setTileType(MostVisitedTileType.ICON_REAL);
                }
                mSnapshotMostVisitedChanged = true;
                if (mIsInitialLoad) loadTaskCompleted();
            }
        }

        public View createMostVisitedItemView(
                LayoutInflater inflater, MostVisitedItem item, boolean isInitialLoad) {
            final MostVisitedItemView view = (MostVisitedItemView) inflater.inflate(
                    R.layout.most_visited_item, mMostVisitedLayout, false);
            view.setTitle(TitleUtil.getTitleForDisplay(item.getTitle(), item.getUrl()));
            view.setOfflineAvailable(item.isOfflineAvailable());

            LargeIconCallback iconCallback = new LargeIconCallbackImpl(item, view, isInitialLoad);
            if (isInitialLoad) mPendingLoadTasks++;
            if (!loadWhitelistIcon(item, iconCallback)) {
                mManager.getLargeIconForUrl(item.getUrl(), mMinIconSize, iconCallback);
            }

            return view;
        }

        private boolean loadWhitelistIcon(MostVisitedItem item, LargeIconCallback iconCallback) {
            if (item.getWhitelistIconPath().isEmpty()) return false;

            Bitmap bitmap = BitmapFactory.decodeFile(item.getWhitelistIconPath());
            if (bitmap == null) {
                Log.d(TAG, "Image decoding failed: %s", item.getWhitelistIconPath());
                return false;
            }
            iconCallback.onLargeIconAvailable(bitmap, Color.BLACK, false);
            return true;
        }

        public void onIconUpdated(final String url) {
            if (mMostVisitedItems == null) return;
            // Find a matching most visited item.
            for (MostVisitedItem item : mMostVisitedItems) {
                if (item.getUrl().equals(url)) {
                    LargeIconCallback iconCallback = new LargeIconCallbackImpl(
                            item, (MostVisitedItemView) item.getView(), false);
                    mManager.getLargeIconForUrl(url, mMinIconSize, iconCallback);
                    break;
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mNewTabPageLayout != null) {
            mNewTabPageLayout.setParentViewportHeight(MeasureSpec.getSize(heightMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mUseCardsUi) mRecyclerView.updatePeekingCardAndHeader();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // When the viewport configuration changes, we want to update the display style so that the
        // observers are aware of the new available space. Another moment to do this update could
        // be through a OnLayoutChangeListener, but then we get notified of the change after the
        // layout pass, which means that the new style will only be visible after layout happens
        // again. We prefer updating here to avoid having to require that additional layout pass.
        mUiConfig.updateDisplayStyle();

        // Close the Context Menu as it may have moved (https://crbug.com/642688).
        mManager.closeContextMenu();
    }

    private int getVerticalScroll() {
        if (mUseCardsUi) {
            return mRecyclerView.computeVerticalScrollOffset();
        } else {
            return mScrollView.getScrollY();
        }
    }

    /**
     * @return The adapter position the user has scrolled to.
     */
    public int getScrollPosition() {
        if (mUseCardsUi) return mRecyclerView.getScrollPosition();
        return RecyclerView.NO_POSITION;
    }
}
