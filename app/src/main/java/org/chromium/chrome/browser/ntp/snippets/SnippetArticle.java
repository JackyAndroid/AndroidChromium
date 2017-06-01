// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsCardLayout.ContentSuggestionsCardLayoutEnum;

import java.io.File;

/**
 * Represents the data for an article card on the NTP.
 */
public class SnippetArticle {
    /** The category of this article. */
    public final int mCategory;

    /** The identifier for this article within the category - not necessarily unique globally. */
    public final String mIdWithinCategory;

    /** The title of this article. */
    public final String mTitle;

    /** The canonical publisher name (e.g., New York Times). */
    public final String mPublisher;

    /** The snippet preview text. */
    public final String mPreviewText;

    /** The URL of this article. */
    public final String mUrl;

    /** the AMP url for this article (possible for this to be empty). */
    public final String mAmpUrl;

    /** The time when this article was published. */
    public final long mPublishTimestampMilliseconds;

    /** The score expressing relative quality of the article for the user. */
    public final float mScore;

    /** The position of this article within its section. */
    public final int mPosition;

    /** The position of this article in the complete list. Populated by NewTabPageAdapter. */
    public int mGlobalPosition = -1;

    /** The layout that should be used to display the snippet. */
    @ContentSuggestionsCardLayoutEnum
    public final int mCardLayout;

    /** Bitmap of the thumbnail, fetched lazily, when the RecyclerView wants to show the snippet. */
    private Bitmap mThumbnailBitmap;

    /** Stores whether impression of this article has been tracked already. */
    private boolean mImpressionTracked;

    /** To be run when the offline status of the article or AMP article changes. */
    private Runnable mOfflineStatusChangeRunnable;

    /** Whether the linked article represents a downloaded asset. */
    public boolean mIsDownloadedAsset;

    /** The path to the downloaded asset (only for download asset articles). */
    private String mDownloadAssetPath;

    /** The downloaded asset (only for download asset articles). */
    private File mFile;

    /** The mime type of the downloaded asset (only for download asset articles). */
    private String mDownloadAssetMimeType;

    /** The tab id of the corresponding tab (only for recent tab articles). */
    private String mRecentTabId;

    /** The offline id of the corresponding offline page, if any. */
    private Long mOfflinePageOfflineId;

    /**
     * Creates a SnippetArticleListItem object that will hold the data.
     */
    public SnippetArticle(int category, String idWithinCategory, String title, String publisher,
            String previewText, String url, String ampUrl, long timestamp, float score,
            int position, @ContentSuggestionsCardLayoutEnum int cardLayout) {
        mCategory = category;
        mIdWithinCategory = idWithinCategory;
        mTitle = title;
        mPublisher = publisher;
        mPreviewText = previewText;
        mUrl = url;
        mAmpUrl = ampUrl;
        mPublishTimestampMilliseconds = timestamp;
        mScore = score;
        mPosition = position;
        mCardLayout = cardLayout;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SnippetArticle)) return false;
        SnippetArticle rhs = (SnippetArticle) other;
        return mCategory == rhs.mCategory && mIdWithinCategory.equals(rhs.mIdWithinCategory);
    }

    @Override
    public int hashCode() {
        return mCategory ^ mIdWithinCategory.hashCode();
    }

    /**
     * Returns this article's thumbnail as a {@link Bitmap}. Can return {@code null} as it is
     * initially unset.
     */
    public Bitmap getThumbnailBitmap() {
        return mThumbnailBitmap;
    }

    /** Sets the thumbnail bitmap for this article. */
    public void setThumbnailBitmap(Bitmap bitmap) {
        mThumbnailBitmap = bitmap;
    }

    /** Returns whether to track an impression for this article. */
    public boolean trackImpression() {
        // Track UMA only upon the first impression per life-time of this object.
        if (mImpressionTracked) return false;
        mImpressionTracked = true;
        return true;
    }

    /**
     * Sets the {@link Runnable} to be run when the article's offline status changes.
     * Pass null to wipe.
     */
    public void setOfflineStatusChangeRunnable(Runnable runnable) {
        mOfflineStatusChangeRunnable = runnable;
    }

    /** @return whether a snippet is either offline page or asset download. */
    public boolean isDownload() {
        return mCategory == KnownCategories.DOWNLOADS;
    }

    /**
     * @return the downloaded asset. May only be called if mIsDownloadedAsset is {@code true} and
     * this snippet belongs to DOWNLOADS category.
     */
    public File getDownloadAssetFile() {
        assert isDownload();
        if (mFile == null) mFile = new File(mDownloadAssetPath);
        return mFile;
    }

    /**
     * @return the mime type of the download asset. May only be called if mIsDownloadAsset is
     * {@code true} and this snippet belongs to DOWNLOADS category.
     */
    public String getDownloadAssetMimeType() {
        assert isDownload();
        return mDownloadAssetMimeType;
    }

    /**
     * Marks the article suggestion as a download asset with the given path and mime type. May only
     * be called if this snippet belongs to DOWNLOADS category.
     */
    public void setDownloadAssetData(String filePath, String mimeType) {
        assert isDownload();
        mIsDownloadedAsset = true;
        mDownloadAssetPath = filePath;
        mDownloadAssetMimeType = mimeType;
    }

    /**
     * Marks the article suggestion as a download offline page with the given id. May only be called
     * if this snippet belongs to DOWNLOADS category.
     */
    public void setDownloadOfflinePageData(long offlinePageId) {
        assert isDownload();
        mIsDownloadedAsset = false;
        setOfflinePageOfflineId(offlinePageId);
    }

    /**
    * @return whether a snippet has to be matched with the exact offline page or with the most
    * recent offline page found by the snippet's URL.
    */
    public boolean requiresExactOfflinePage() {
        return isDownload() || isRecentTab();
    }

    public boolean isRecentTab() {
        return mCategory == KnownCategories.RECENT_TABS;
    }

    /**
     * @return the corresponding recent tab id. May only be called if this snippet is a recent tab
     * article.
     */
    public String getRecentTabId() {
        assert isRecentTab();
        return mRecentTabId;
    }

    /**
     * Sets tab id and offline page id for recent tab articles. May only be called if this snippet
     * is a recent tab article.
     */
    public void setRecentTabData(String tabId, long offlinePageId) {
        assert isRecentTab();
        mRecentTabId = tabId;
        setOfflinePageOfflineId(offlinePageId);
    }

    /** Sets offline id of the corresponding to the snippet offline page. Null to clear.*/
    public void setOfflinePageOfflineId(@Nullable Long offlineId) {
        Long previous = mOfflinePageOfflineId;
        mOfflinePageOfflineId = offlineId;

        if (mOfflineStatusChangeRunnable == null) return;
        if ((previous == null) ? (mOfflinePageOfflineId != null)
                               : !previous.equals(mOfflinePageOfflineId)) {
            mOfflineStatusChangeRunnable.run();
        }
    }

    /**
     * Gets offline id of the corresponding to the snippet offline page.
     * Null if there is no corresponding offline page.
     */
    @Nullable
    public Long getOfflinePageOfflineId() {
        return mOfflinePageOfflineId;
    }

    @Override
    public String toString() {
        // For debugging purposes. Displays the first 42 characters of the title.
        return String.format("{%s, %1.42s}", getClass().getSimpleName(), mTitle);
    }
}
