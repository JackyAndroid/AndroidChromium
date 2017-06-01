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
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.ContextMenuManager.Delegate;
import org.chromium.chrome.browser.ntp.DisplayStyleObserver;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.cards.CardViewHolder;
import org.chromium.chrome.browser.ntp.cards.CardsVariationParameters;
import org.chromium.chrome.browser.ntp.cards.DisplayStyleObserverAdapter;
import org.chromium.chrome.browser.ntp.cards.ImpressionTracker;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.ui.mojom.WindowOpenDisposition;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * A class that represents the view for a single card snippet.
 */
public class SnippetArticleViewHolder
        extends CardViewHolder implements ImpressionTracker.Listener, ContextMenuManager.Delegate {
    private static final String PUBLISHER_FORMAT_STRING = "%s - %s";
    private static final int FADE_IN_ANIMATION_TIME_MS = 300;
    private static final int[] FAVICON_SERVICE_SUPPORTED_SIZES = {16, 24, 32, 48, 64};
    private static final String FAVICON_SERVICE_FORMAT =
            "https://s2.googleusercontent.com/s2/favicons?domain=%s&src=chrome_newtab_mobile&sz=%d&alt=404";

    private final NewTabPageManager mNewTabPageManager;
    private final TextView mHeadlineTextView;
    private final TextView mPublisherTextView;
    private final TextView mArticleSnippetTextView;
    private final ImageView mThumbnailView;
    private final ImageView mOfflineBadge;
    private final View mPublisherBar;

    private FetchImageCallback mImageCallback;
    private SnippetArticle mArticle;
    private int mPublisherFaviconSizePx;

    private final boolean mUseFaviconService;
    private final UiConfig mUiConfig;

    /**
     * Constructs a {@link SnippetArticleViewHolder} item used to display snippets.
     *
     * @param parent The NewTabPageRecyclerView that is going to contain the newly created view.
     * @param manager The NewTabPageManager object used to open an article.
     * @param uiConfig The NTP UI configuration object used to adjust the article UI.
     */
    public SnippetArticleViewHolder(NewTabPageRecyclerView parent, NewTabPageManager manager,
            UiConfig uiConfig) {
        super(R.layout.new_tab_page_snippets_card, parent, uiConfig, manager);

        mNewTabPageManager = manager;
        mThumbnailView = (ImageView) itemView.findViewById(R.id.article_thumbnail);
        mHeadlineTextView = (TextView) itemView.findViewById(R.id.article_headline);
        mPublisherTextView = (TextView) itemView.findViewById(R.id.article_publisher);
        mArticleSnippetTextView = (TextView) itemView.findViewById(R.id.article_snippet);
        mPublisherBar = itemView.findViewById(R.id.publisher_bar);
        mOfflineBadge = (ImageView) itemView.findViewById(R.id.offline_icon);

        new ImpressionTracker(itemView, this);

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
            mRecyclerView.onSnippetImpression();
        }
    }

    @Override
    public void onCardTapped() {
        mNewTabPageManager.openSnippet(WindowOpenDisposition.CURRENT_TAB, mArticle);
    }

    @Override
    public void openItem(int windowDisposition) {
        mNewTabPageManager.openSnippet(windowDisposition, mArticle);
    }

    @Override
    public void removeItem() {
        getRecyclerView().dismissItemWithAnimation(this);
    }

    @Override
    public String getUrl() {
        return mArticle.mUrl;
    }

    @Override
    public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
        if (mArticle.isDownload()) {
            if (menuItemId == ContextMenuManager.ID_OPEN_IN_INCOGNITO_TAB) return false;
            if (menuItemId == ContextMenuManager.ID_SAVE_FOR_OFFLINE) return false;
        }
        return true;
    }

    @Override
    protected Delegate getContextMenuDelegate() {
        return this;
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
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mPublisherBar.getLayoutParams();

        int topMargin = mPublisherBar.getResources().getDimensionPixelSize(
                hideSnippet ? R.dimen.snippets_publisher_margin_top_without_article_snippet
                            : R.dimen.snippets_publisher_margin_top_with_article_snippet);

        params.setMargins(params.leftMargin,
                          topMargin,
                          params.rightMargin,
                          params.bottomMargin);

        mPublisherBar.setLayoutParams(params);
    }

    public void onBindViewHolder(SnippetArticle article) {
        super.onBindViewHolder();

        // No longer listen for offline status changes to the old article.
        if (mArticle != null) mArticle.setOfflineStatusChangeRunnable(null);

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

        // The favicon of the publisher should match the TextView height.
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

        mOfflineBadge.setVisibility(View.GONE);
        if (SnippetsConfig.isOfflineBadgeEnabled()) {
            Runnable offlineChecker = new Runnable() {
                @Override
                public void run() {
                    if (mArticle.getOfflinePageOfflineId() != null || mArticle.mIsDownloadedAsset) {
                        mOfflineBadge.setVisibility(View.VISIBLE);
                    }
                }
            };
            mArticle.setOfflineStatusChangeRunnable(offlineChecker);
            offlineChecker.run();
        }

        mRecyclerView.onSnippetBound(itemView);
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
