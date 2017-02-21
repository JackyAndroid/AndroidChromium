// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.cards.NewTabPageItem;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsCardLayout.ContentSuggestionsCardLayoutEnum;

/**
 * Represents the data for an article card on the NTP.
 */
public class SnippetArticle implements NewTabPageItem {
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

    /** The position of this article in the complete list. Populated by NewTabPageAdapter.*/
    public int mGlobalPosition = -1;

    /** The layout that should be used to display the snippet. */
    @ContentSuggestionsCardLayoutEnum
    public final int mCardLayout;

    /** Bitmap of the thumbnail, fetched lazily, when the RecyclerView wants to show the snippet. */
    private Bitmap mThumbnailBitmap;

    /** Stores whether impression of this article has been tracked already. */
    private boolean mImpressionTracked;

    /** Specifies ranges of positions for which we store position-specific sub-histograms. */
    private static final int[] HISTOGRAM_FOR_POSITIONS = {0, 2, 4, 9};

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

    @Override
    public int getType() {
        return NewTabPageItem.VIEW_TYPE_SNIPPET;
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof SnippetArticleViewHolder;
        ((SnippetArticleViewHolder) holder).onBindViewHolder(this);
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

    /** Tracks click on this NTP snippet in UMA. */
    public void trackClick() {
        // To compare against NewTabPage.Snippets.CardShown for each position.
        RecordHistogram.recordSparseSlowlyHistogram("NewTabPage.Snippets.CardClicked", mPosition);
        // To compare against all snippets actions.
        NewTabPageUma.recordSnippetAction(NewTabPageUma.SNIPPETS_ACTION_CLICKED);
        // To compare how the user views the article linked to from a snippet (eg. as opposed to
        // opening in a new tab).
        NewTabPageUma.recordOpenSnippetMethod(NewTabPageUma.OPEN_SNIPPET_METHODS_PLAIN_CLICK);
        // To see how users left the NTP.
        NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_SNIPPET);
        // To see whether users click on more recent snippets and whether our suggestion algorithm
        // is accurate.
        recordAgeAndScore("NewTabPage.Snippets.CardClicked");
    }

    /** Tracks impression of this NTP snippet. */
    public boolean trackImpression() {
        // Track UMA only upon the first impression per life-time of this object.
        if (mImpressionTracked) return false;

        RecordHistogram.recordSparseSlowlyHistogram("NewTabPage.Snippets.CardShown", mPosition);
        recordAgeAndScore("NewTabPage.Snippets.CardShown");
        mImpressionTracked = true;
        return true;
    }

    /** Returns whether impression of this SnippetArticleListItem has already been tracked. */
    public boolean impressionTracked() {
        return mImpressionTracked;
    }

    public void recordAgeAndScore(String histogramPrefix) {
        // Track how the (approx.) position relates to age / score of the snippet that is clicked.
        int ageInMinutes =
                (int) ((System.currentTimeMillis() - mPublishTimestampMilliseconds) / 60000L);
        String histogramAge = histogramPrefix + "Age";
        String histogramScore = histogramPrefix + "ScoreNew";

        recordAge(histogramAge, ageInMinutes);
        recordScore(histogramScore, mScore);
        int startPosition = 0;
        for (int endPosition : HISTOGRAM_FOR_POSITIONS) {
            if (mPosition >= startPosition && mPosition <= endPosition) {
                String suffix = "_" + startPosition + "_" + endPosition;
                recordAge(histogramAge + suffix, ageInMinutes);
                recordScore(histogramScore + suffix, mScore);
                break;
            }
            startPosition = endPosition + 1;
        }
    }

    private static void recordAge(String histogramName, int ageInMinutes) {
        // Negative values (when the time of the device is set inappropriately) provide no value.
        if (ageInMinutes >= 0) {
            // If the max value below (72 hours) were to be changed, the histogram should be renamed
            // since it will change the shape of buckets.
            RecordHistogram.recordCustomCountHistogram(histogramName, ageInMinutes, 1, 72 * 60, 50);
        }
    }

    private static void recordScore(String histogramName, float score) {
        int recordedScore = Math.min((int) Math.ceil(score), 100000);
        RecordHistogram.recordCustomCountHistogram(histogramName, recordedScore, 1, 100000, 50);
    }

    @Override
    public String toString() {
        // For debugging purposes. Displays the first 42 characters of the title.
        return String.format("{%s, %1.42s}", getClass().getSimpleName(), mTitle);
    }
}
