// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.v4.text.BidiFormatter;
import android.support.v4.view.ViewCompat;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.ntp.DisplayStyleObserver;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.cards.CardViewHolder;
import org.chromium.chrome.browser.ntp.cards.CardsVariationParameters;
import org.chromium.chrome.browser.ntp.cards.DisplayStyleObserverAdapter;
import org.chromium.chrome.browser.ntp.cards.ImpressionTracker;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.ui.mojom.WindowOpenDisposition;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * A class that represents the view for a single card snippet.
 */
public class SnippetArticleViewHolder extends CardViewHolder implements ImpressionTracker.Listener {
    private static final String PUBLISHER_FORMAT_STRING = "%s - %s";
    private static final int FADE_IN_ANIMATION_TIME_MS = 300;
    private static final int[] FAVICON_SERVICE_SUPPORTED_SIZES = {16, 24, 32, 48, 64};
    private static final String FAVICON_SERVICE_FORMAT =
            "https://s2.googleusercontent.com/s2/favicons?domain=%s&src=chrome_newtab_mobile&sz=%d&alt=404";

    // ContextMenu item ids. These must be unique.
    private static final int ID_OPEN_IN_NEW_WINDOW = 0;
    private static final int ID_OPEN_IN_NEW_TAB = 1;
    private static final int ID_OPEN_IN_INCOGNITO_TAB = 2;
    private static final int ID_SAVE_FOR_OFFLINE = 3;
    private static final int ID_REMOVE = 4;

    private final NewTabPageManager mNewTabPageManager;
    private final TextView mHeadlineTextView;
    private final TextView mPublisherTextView;
    private final TextView mArticleSnippetTextView;
    private final ImageView mThumbnailView;

    private FetchImageCallback mImageCallback;
    private SnippetArticle mArticle;
    private int mPublisherFaviconSizePx;

    private final boolean mUseFaviconService;
    private final UiConfig mUiConfig;

    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private ImpressionTracker mImpressionTracker;

    /**
     * Listener for when the context menu is created.
     */
    public interface OnCreateContextMenuListener {
        /** Called when the context menu is created. */
        void onCreateContextMenu();
    }

    private static class ContextMenuItemClickListener implements OnMenuItemClickListener {
        private final SnippetArticle mArticle;
        private final NewTabPageManager mManager;
        private final NewTabPageRecyclerView mRecyclerView;

        public ContextMenuItemClickListener(SnippetArticle article,
                NewTabPageManager newTabPageManager,
                NewTabPageRecyclerView newTabPageRecyclerView) {
            mArticle = article;
            mManager = newTabPageManager;
            mRecyclerView = newTabPageRecyclerView;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            // If the user clicks a snippet then immediately long presses they will create a context
            // menu while the snippet's URL loads in the background. This means that when they press
            // an item on context menu the NTP will not actually be open. We add this check here to
            // prevent taking any action if the user has already left the NTP.
            // https://crbug.com/640468.
            // TODO(peconn): Instead, close the context menu when a snippet is clicked.
            if (!ViewCompat.isAttachedToWindow(mRecyclerView)) return true;

            // The UMA is used to compare how the user views the article linked from a snippet.
            switch (item.getItemId()) {
                case ID_OPEN_IN_NEW_WINDOW:
                    NewTabPageUma.recordOpenSnippetMethod(
                            NewTabPageUma.OPEN_SNIPPET_METHODS_NEW_WINDOW);
                    mManager.openSnippet(WindowOpenDisposition.NEW_WINDOW, mArticle);
                    return true;
                case ID_OPEN_IN_NEW_TAB:
                    NewTabPageUma.recordOpenSnippetMethod(
                            NewTabPageUma.OPEN_SNIPPET_METHODS_NEW_TAB);
                    mManager.openSnippet(WindowOpenDisposition.NEW_FOREGROUND_TAB, mArticle);
                    return true;
                case ID_OPEN_IN_INCOGNITO_TAB:
                    NewTabPageUma.recordOpenSnippetMethod(
                            NewTabPageUma.OPEN_SNIPPET_METHODS_INCOGNITO);
                    mManager.openSnippet(WindowOpenDisposition.OFF_THE_RECORD, mArticle);
                    return true;
                case ID_SAVE_FOR_OFFLINE:
                    NewTabPageUma.recordOpenSnippetMethod(
                            NewTabPageUma.OPEN_SNIPPET_METHODS_SAVE_FOR_OFFLINE);
                    mManager.openSnippet(WindowOpenDisposition.SAVE_TO_DISK, mArticle);
                    return true;
                case ID_REMOVE:
                    // UMA is recorded during dismissal.
                    mRecyclerView.dismissItemWithAnimation(mArticle);
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * Constructs a SnippetCardItemView item used to display snippets
     *
     * @param parent The ViewGroup that is going to contain the newly created view.
     * @param manager The NTPManager object used to open an article
     * @param suggestionsSource The source used to retrieve the thumbnails.
     * @param uiConfig The NTP UI configuration object used to adjust the article UI.
     */
    public SnippetArticleViewHolder(NewTabPageRecyclerView parent, NewTabPageManager manager,
            UiConfig uiConfig) {
        super(R.layout.new_tab_page_snippets_card, parent, uiConfig);

        mNewTabPageManager = manager;
        mThumbnailView = (ImageView) itemView.findViewById(R.id.article_thumbnail);
        mHeadlineTextView = (TextView) itemView.findViewById(R.id.article_headline);
        mPublisherTextView = (TextView) itemView.findViewById(R.id.article_publisher);
        mArticleSnippetTextView = (TextView) itemView.findViewById(R.id.article_snippet);

        mImpressionTracker = new ImpressionTracker(itemView, this);

        mUiConfig = uiConfig;
        new DisplayStyleObserverAdapter(itemView, uiConfig, new DisplayStyleObserver() {
            @Override
            public void onDisplayStyleChanged(@UiConfig.DisplayStyle int newDisplayStyle) {
                updateLayout();
            }
        });

        mUseFaviconService = CardsVariationParameters.isFaviconServiceEnabled();
    }

    @Override
    public void onImpression() {
        if (mArticle != null && mArticle.trackImpression()) {
            mNewTabPageManager.trackSnippetImpression(mArticle);
        }
    }

    @Override
    public void onCardTapped() {
        mNewTabPageManager.openSnippet(WindowOpenDisposition.CURRENT_TAB, mArticle);
        mArticle.trackClick();
    }

    @Override
    protected void createContextMenu(ContextMenu menu) {
        RecordHistogram.recordSparseSlowlyHistogram(
                "NewTabPage.Snippets.CardLongPressed", mArticle.mPosition);
        mArticle.recordAgeAndScore("NewTabPage.Snippets.CardLongPressed");

        OnMenuItemClickListener listener =
                new ContextMenuItemClickListener(mArticle, mNewTabPageManager, getRecyclerView());

        // Create a context menu akin to the one shown for MostVisitedItems.
        if (mNewTabPageManager.isOpenInNewWindowEnabled()) {
            addContextMenuItem(menu, ID_OPEN_IN_NEW_WINDOW,
                    R.string.contextmenu_open_in_other_window, listener);
        }

        addContextMenuItem(
                menu, ID_OPEN_IN_NEW_TAB, R.string.contextmenu_open_in_new_tab, listener);

        if (mNewTabPageManager.isOpenInIncognitoEnabled()) {
            addContextMenuItem(menu, ID_OPEN_IN_INCOGNITO_TAB,
                    R.string.contextmenu_open_in_incognito_tab, listener);
        }

        // TODO(peconn): Only show 'Save for Offline' for appropriate snippet types.
        if (SnippetsConfig.isSaveToOfflineEnabled()
                && OfflinePageBridge.canSavePage(mArticle.mUrl)) {
            addContextMenuItem(
                    menu, ID_SAVE_FOR_OFFLINE, R.string.contextmenu_save_link, listener);
        }

        addContextMenuItem(menu, ID_REMOVE, R.string.remove, listener);

        // Disable touch events on the RecyclerView while the context menu is open. This is to
        // prevent the user long pressing to get the context menu then on the same press scrolling
        // or swiping to dismiss an item (eg. https://crbug.com/638854, 638555, 636296)
        final NewTabPageRecyclerView recyclerView = (NewTabPageRecyclerView) itemView.getParent();
        recyclerView.setTouchEnabled(false);

        mNewTabPageManager.addContextMenuCloseCallback(new Callback<Menu>() {
            @Override
            public void onResult(Menu result) {
                recyclerView.setTouchEnabled(true);
                mNewTabPageManager.removeContextMenuCloseCallback(this);
            }
        });
    }

    /**
     * Convenience method to reduce multi-line function call to single line.
     */
    private static void addContextMenuItem(
            ContextMenu menu, int id, int resourceId, OnMenuItemClickListener listener) {
        menu.add(Menu.NONE, id, Menu.NONE, resourceId).setOnMenuItemClickListener(listener);
    }

    /**
     * Updates the layout taking into account screen dimensions and the type of snippet displayed.
     */
    private void updateLayout() {
        boolean narrow = mUiConfig.getCurrentDisplayStyle() == UiConfig.DISPLAY_STYLE_NARROW;
        boolean minimal = mArticle.mCardLayout == ContentSuggestionsCardLayout.MINIMAL_CARD;

        // If the screen is narrow or we are using the minimal layout, hide the article snippet.
        boolean hideSnippet = narrow || minimal;
        mArticleSnippetTextView.setVisibility(hideSnippet ? View.GONE : View.VISIBLE);

        // If we are using minimal layout, hide the thumbnail.
        boolean hideThumbnail = minimal;
        mThumbnailView.setVisibility(hideThumbnail ? View.GONE : View.VISIBLE);

        // If the screen is narrow, increase the number of lines in the header.
        mHeadlineTextView.setMaxLines(narrow ? 4 : 2);

        // If the screen is narrow, ensure a minimum number of lines to prevent overlap between the
        // publisher and the header.
        mHeadlineTextView.setMinLines((narrow && !hideThumbnail) ? 3 : 1);

        // If we aren't showing the article snippet, reduce the top margin for publisher text.
        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) mPublisherTextView.getLayoutParams();

        int topMargin = mPublisherTextView.getResources().getDimensionPixelSize(
                hideSnippet ? R.dimen.snippets_publisher_margin_top_without_article_snippet
                            : R.dimen.snippets_publisher_margin_top_with_article_snippet);

        params.setMargins(params.leftMargin,
                          topMargin,
                          params.rightMargin,
                          params.bottomMargin);

        mPublisherTextView.setLayoutParams(params);
    }

    public void onBindViewHolder(SnippetArticle article) {
        super.onBindViewHolder();

        mArticle = article;
        updateLayout();

        mHeadlineTextView.setText(mArticle.mTitle);

        // DateUtils.getRelativeTimeSpanString(...) calls through to TimeZone.getDefault(). If this
        // has never been called before it loads the current time zone from disk. In most likelihood
        // this will have been called previously and the current time zone will have been cached,
        // but in some cases (eg instrumentation tests) it will cause a strict mode violation.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            long time = SystemClock.elapsedRealtime();
            CharSequence relativeTimeSpan = DateUtils.getRelativeTimeSpanString(
                    mArticle.mPublishTimestampMilliseconds, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            RecordHistogram.recordTimesHistogram("Android.StrictMode.SnippetUIBuildTime",
                    SystemClock.elapsedRealtime() - time, TimeUnit.MILLISECONDS);

            // We format the publisher here so that having a publisher name in an RTL language
            // doesn't mess up the formatting on an LTR device and vice versa.
            String publisherAttribution = String.format(PUBLISHER_FORMAT_STRING,
                    BidiFormatter.getInstance().unicodeWrap(mArticle.mPublisher), relativeTimeSpan);
            mPublisherTextView.setText(publisherAttribution);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        // The favicon of the publisher should match the textview height.
        int widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mPublisherTextView.measure(widthSpec, heightSpec);
        mPublisherFaviconSizePx = mPublisherTextView.getMeasuredHeight();

        mArticleSnippetTextView.setText(mArticle.mPreviewText);

        // If there's still a pending thumbnail fetch, cancel it.
        cancelImageFetch();

        // If the article has a thumbnail already, reuse it. Otherwise start a fetch.
        // mThumbnailView's visibility is modified in updateLayout().
        if (mThumbnailView.getVisibility() == View.VISIBLE) {
            if (mArticle.getThumbnailBitmap() != null) {
                mThumbnailView.setImageBitmap(mArticle.getThumbnailBitmap());
            } else {
                mThumbnailView.setImageResource(R.drawable.ic_snippet_thumbnail_placeholder);
                mImageCallback = new FetchImageCallback(this, mArticle);
                mNewTabPageManager.getSuggestionsSource()
                        .fetchSuggestionImage(mArticle, mImageCallback);
            }
        }

        // Set the favicon of the publisher.
        try {
            fetchFaviconFromLocalCache(new URI(mArticle.mUrl), true);
        } catch (URISyntaxException e) {
            setDefaultFaviconOnView();
        }
    }

    private static class FetchImageCallback extends Callback<Bitmap> {
        private SnippetArticleViewHolder mViewHolder;
        private final SnippetArticle mSnippet;

        public FetchImageCallback(
                SnippetArticleViewHolder viewHolder, SnippetArticle snippet) {
            mViewHolder = viewHolder;
            mSnippet = snippet;
        }

        @Override
        public void onResult(Bitmap image) {
            if (mViewHolder == null) return;
            mViewHolder.fadeThumbnailIn(mSnippet, image);
        }

        public void cancel() {
            // TODO(treib): Pass the "cancel" on to the actual image fetcher.
            mViewHolder = null;
        }
    }

    private void cancelImageFetch() {
        if (mImageCallback != null) {
            mImageCallback.cancel();
            mImageCallback = null;
        }
    }

    private void fadeThumbnailIn(SnippetArticle snippet, Bitmap thumbnail) {
        mImageCallback = null;
        if (thumbnail == null) return; // Nothing to do, we keep the placeholder.

        // We need to crop and scale the downloaded bitmap, as the ImageView we set it on won't be
        // able to do so when using a TransitionDrawable (as opposed to the straight bitmap).
        // That's a limitation of TransitionDrawable, which doesn't handle layers of varying sizes.
        Resources res = mThumbnailView.getResources();
        int targetSize = res.getDimensionPixelSize(R.dimen.snippets_thumbnail_size);
        Bitmap scaledThumbnail = ThumbnailUtils.extractThumbnail(
                thumbnail, targetSize, targetSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);

        // Store the bitmap to skip the download task next time we display this snippet.
        snippet.setThumbnailBitmap(scaledThumbnail);

        // Cross-fade between the placeholder and the thumbnail.
        Drawable[] layers = {mThumbnailView.getDrawable(),
                new BitmapDrawable(mThumbnailView.getResources(), scaledThumbnail)};
        TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
        mThumbnailView.setImageDrawable(transitionDrawable);
        transitionDrawable.startTransition(FADE_IN_ANIMATION_TIME_MS);
    }

    private void fetchFaviconFromLocalCache(final URI snippetUri, final boolean fallbackToService) {
        mNewTabPageManager.getLocalFaviconImageForURL(
                getSnippetDomain(snippetUri), mPublisherFaviconSizePx, new FaviconImageCallback() {
                    @Override
                    public void onFaviconAvailable(Bitmap image, String iconUrl) {
                        if (image == null && fallbackToService) {
                            fetchFaviconFromService(snippetUri);
                            return;
                        }
                        setFaviconOnView(image);
                    }
                });
    }

    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    private void fetchFaviconFromService(final URI snippetUri) {
        // Show the default favicon immediately.
        setDefaultFaviconOnView();

        if (!mUseFaviconService) return;
        int sizePx = getFaviconServiceSupportedSize();
        if (sizePx == 0) return;

        // Replace the default icon by another one from the service when it is fetched.
        mNewTabPageManager.ensureIconIsAvailable(
                getSnippetDomain(snippetUri), // Store to the cache for the whole domain.
                String.format(FAVICON_SERVICE_FORMAT, snippetUri.getHost(), sizePx),
                /*useLargeIcon=*/false, /*isTemporary=*/true, new IconAvailabilityCallback() {
                    @Override
                    public void onIconAvailabilityChecked(boolean newlyAvailable) {
                        if (!newlyAvailable) return;
                        // The download succeeded, the favicon is in the cache; fetch it.
                        fetchFaviconFromLocalCache(snippetUri, /*fallbackToService=*/false);
                    }
                });
    }

    private int getFaviconServiceSupportedSize() {
        // Take the smallest size larger than mFaviconSizePx.
        for (int size : FAVICON_SERVICE_SUPPORTED_SIZES) {
            if (size > mPublisherFaviconSizePx) return size;
        }
        // Or at least the largest available size (unless too small).
        int largestSize =
                FAVICON_SERVICE_SUPPORTED_SIZES[FAVICON_SERVICE_SUPPORTED_SIZES.length - 1];
        if (mPublisherFaviconSizePx <= largestSize * 1.5) return largestSize;
        return 0;
    }

    private String getSnippetDomain(URI snippetUri) {
        return String.format("%s://%s", snippetUri.getScheme(), snippetUri.getHost());
    }

    private void setDefaultFaviconOnView() {
        setFaviconOnView(ApiCompatibilityUtils.getDrawable(
                mPublisherTextView.getContext().getResources(), R.drawable.default_favicon));
    }

    private void setFaviconOnView(Bitmap image) {
        setFaviconOnView(new BitmapDrawable(mPublisherTextView.getContext().getResources(), image));
    }

    private void setFaviconOnView(Drawable drawable) {
        drawable.setBounds(0, 0, mPublisherFaviconSizePx, mPublisherFaviconSizePx);
        ApiCompatibilityUtils.setCompoundDrawablesRelative(
                mPublisherTextView, drawable, null, null, null);
        mPublisherTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean isDismissable() {
        return !isPeeking();
    }
}
