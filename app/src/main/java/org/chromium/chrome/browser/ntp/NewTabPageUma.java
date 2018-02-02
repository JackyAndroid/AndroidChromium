// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.os.SystemClock;
import android.support.annotation.IntDef;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.rappor.RapporServiceBridge;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Records UMA stats for which actions the user takes on the NTP in the
 * "NewTabPage.ActionAndroid" histogram.
 */
public final class NewTabPageUma {
    private NewTabPageUma() {}

    // Possible actions taken by the user on the NTP. These values are also defined in
    // histograms.xml. WARNING: these values must stay in sync with histograms.xml.

    // User performed a search using the omnibox
    private static final int ACTION_SEARCHED_USING_OMNIBOX = 0;
    // User navigated to Google search homepage using the omnibox
    private static final int ACTION_NAVIGATED_TO_GOOGLE_HOMEPAGE = 1;
    // User navigated to any other page using the omnibox
    private static final int ACTION_NAVIGATED_USING_OMNIBOX = 2;
    // User opened a most visited page
    public static final int ACTION_OPENED_MOST_VISITED_ENTRY = 3;
    // User opened a recently closed tab
    public static final int ACTION_OPENED_RECENTLY_CLOSED_ENTRY = 4;
    // User opened a bookmark
    public static final int ACTION_OPENED_BOOKMARK = 5;
    // User opened a foreign session (from recent tabs section)
    public static final int ACTION_OPENED_FOREIGN_SESSION = 6;
    // User navigated to the webpage for a snippet shown on the NTP.
    public static final int ACTION_OPENED_SNIPPET = 7;
    // User clicked on the "learn more" link in the footer.
    public static final int ACTION_CLICKED_LEARN_MORE = 9;
    // User clicked on the "Refresh" button in the "all dismissed" state.
    public static final int ACTION_CLICKED_ALL_DISMISSED_REFRESH = 10;
    // The number of possible actions
    private static final int NUM_ACTIONS = 11;

    // User navigated to a page using the omnibox.
    private static final int RAPPOR_ACTION_NAVIGATED_USING_OMNIBOX = 0;
    // User navigated to a page using one of the suggested tiles.
    public static final int RAPPOR_ACTION_VISITED_SUGGESTED_TILE = 1;

    // Regular NTP impression (usually when a new tab is opened)
    public static final int NTP_IMPRESSION_REGULAR = 0;

    // Potential NTP impressions (instead of blank page if no tab is open)
    public static final int NTP_IMPESSION_POTENTIAL_NOTAB = 1;

    // The number of possible NTP impression types
    private static final int NUM_NTP_IMPRESSION = 2;

    /** Possible results when sizing the NewTabPageLayout.
     * Do not remove or change existing values other than NUM_NTP_LAYOUT_RESULTS. */
    @IntDef({NTP_LAYOUT_DOES_NOT_FIT, NTP_LAYOUT_FITS_WITHOUT_FIELD_TRIAL,
             NTP_LAYOUT_FITS_WITH_FIELD_TRIAL, NUM_NTP_LAYOUT_RESULTS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NTPLayoutResult {}
    /** The NewTabPageLayout does not fit above the fold and it is displayed as is. */
    public static final int NTP_LAYOUT_DOES_NOT_FIT = 0;
    /** The NewTabPageLayout does not fit above the fold, but we added some extra space so that
     * Most Likely is cut off, indicating to the user they can scroll. */
    public static final int NTP_LAYOUT_DOES_NOT_FIT_PUSH_MOST_LIKELY = 1;
    /** The NewTabPageLayout fits above the fold, the field trial is not enabled. */
    public static final int NTP_LAYOUT_FITS_NO_FIELD_TRIAL = 2;
    /** The NewTabPageLayout fits above the fold, but cannot allow space for the field trial
     * experiment. */
    public static final int NTP_LAYOUT_FITS_WITHOUT_FIELD_TRIAL = 3;
    /** The NewTabPageLayout fits above the fold allowing space for the field trial experiment. */
    public static final int NTP_LAYOUT_FITS_WITH_FIELD_TRIAL = 4;
    /** The number of possible results for the NewTabPageLayout calculations. */
    public static final int NUM_NTP_LAYOUT_RESULTS = 5;

    /**
     * Records an action taken by the user on the NTP.
     * @param action One of the ACTION_* values defined in this class.
     */
    public static void recordAction(int action) {
        assert action >= 0;
        assert action < NUM_ACTIONS;
        switch (action) {
            case ACTION_OPENED_MOST_VISITED_ENTRY:
                RecordUserAction.record("MobileNTPMostVisited");
                break;
            case ACTION_OPENED_RECENTLY_CLOSED_ENTRY:
                RecordUserAction.record("MobileNTPRecentlyClosed");
                break;
            case ACTION_OPENED_BOOKMARK:
                RecordUserAction.record("MobileNTPBookmark");
                break;
            case ACTION_OPENED_FOREIGN_SESSION:
                RecordUserAction.record("MobileNTPForeignSession");
                break;
            default:
                // No UMA action associated with this type.
                break;
        }
        RecordHistogram.recordEnumeratedHistogram("NewTabPage.ActionAndroid", action, NUM_ACTIONS);
    }

    /**
     * Record that the user has navigated away from the NTP using the omnibox.
     * @param destinationUrl The URL to which the user navigated.
     * @param transitionType The transition type of the navigation, from PageTransition.java.
     */
    public static void recordOmniboxNavigation(String destinationUrl, int transitionType) {
        if ((transitionType & PageTransition.CORE_MASK) == PageTransition.GENERATED) {
            recordAction(ACTION_SEARCHED_USING_OMNIBOX);
        } else {
            if (UrlUtilities.nativeIsGoogleHomePageUrl(destinationUrl)) {
                recordAction(ACTION_NAVIGATED_TO_GOOGLE_HOMEPAGE);
            } else {
                recordAction(ACTION_NAVIGATED_USING_OMNIBOX);
            }
            recordExplicitUserNavigation(destinationUrl, RAPPOR_ACTION_NAVIGATED_USING_OMNIBOX);
        }
    }

    /**
     * Record the eTLD+1 for a website explicitly visited by the user, using Rappor.
     */
    public static void recordExplicitUserNavigation(String destinationUrl, int rapporMetric) {
        switch (rapporMetric) {
            case RAPPOR_ACTION_NAVIGATED_USING_OMNIBOX:
                RapporServiceBridge.sampleDomainAndRegistryFromURL(
                        "NTP.ExplicitUserAction.PageNavigation.OmniboxNonSearch", destinationUrl);
                return;
            case RAPPOR_ACTION_VISITED_SUGGESTED_TILE:
                RapporServiceBridge.sampleDomainAndRegistryFromURL(
                        "NTP.ExplicitUserAction.PageNavigation.NTPTileClick", destinationUrl);
                return;
            default:
                return;
        }
    }

    /**
     * Records how the NewTabPageLayout fits on the user's screen.
     * @param result result key, one of {@link NTPLayoutResult}'s values.
     */
    public static void recordNTPLayoutResult(@NTPLayoutResult int result) {
        RecordHistogram.recordEnumeratedHistogram(
                "NewTabPage.Layout", result, NUM_NTP_LAYOUT_RESULTS);
    }

    /**
     * Record a NTP impression (even potential ones to make informed product decisions).
     * @param impressionType Type of the impression from NewTabPageUma.java
     */
    public static void recordNTPImpression(int impressionType) {
        assert impressionType >= 0;
        assert impressionType < NUM_NTP_IMPRESSION;
        RecordHistogram.recordEnumeratedHistogram(
                "Android.NTP.Impression", impressionType, NUM_NTP_IMPRESSION);
    }

    /**
     * Records stats related to content suggestion visits, such as the time spent on the website, or
     * if the user comes back to the NTP.
     * @param tab Tab opened to load a content suggestion.
     * @param category The category of the content suggestion.
     */
    public static void monitorContentSuggestionVisit(Tab tab, int category) {
        tab.addObserver(new SnippetVisitRecorder(category));
    }

    /**
     * Records stats related to content suggestion visits, such as the time spent on the website, or
     * if the user comes back to the NTP. Use through
     * {@link NewTabPageUma#monitorContentSuggestionVisit(Tab, int)}.
     */
    private static class SnippetVisitRecorder extends EmptyTabObserver {
        private final int mCategory;
        private final long mStartTimeMs = SystemClock.elapsedRealtime();

        private SnippetVisitRecorder(int category) {
            mCategory = category;
        }

        @Override
        public void onHidden(Tab tab) {
            endRecording(tab);
        }

        @Override
        public void onDestroyed(Tab tab) {
            endRecording(null);
        }

        @Override
        public void onUpdateUrl(Tab tab, String url) {
            // onLoadUrl below covers many exit conditions to stop recording but not all,
            // such as navigating back. We therefore stop recording if a URL change
            // indicates some non-Web page was visited.
            if (!url.startsWith(UrlConstants.CHROME_SCHEME)
                    && !url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME)) {
                assert !NewTabPage.isNTPUrl(url);
                return;
            }
            if (NewTabPage.isNTPUrl(url)) {
                RecordUserAction.record("MobileNTP.Snippets.VisitEndBackInNTP");
            }
            endRecording(tab);
        }

        @Override
        public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
            // End recording if a new URL gets loaded e.g. after entering a new query in
            // the omnibox. This doesn't cover the navigate-back case so we also need
            // onUpdateUrl.
            int transitionTypeMask = PageTransition.FROM_ADDRESS_BAR | PageTransition.HOME_PAGE
                    | PageTransition.CHAIN_START | PageTransition.CHAIN_END;

            if ((params.getTransitionType() & transitionTypeMask) != 0) endRecording(tab);
        }

        private void endRecording(Tab removeObserverFromTab) {
            if (removeObserverFromTab != null) removeObserverFromTab.removeObserver(this);
            RecordUserAction.record("MobileNTP.Snippets.VisitEnd");
            long visitTimeMs = SystemClock.elapsedRealtime() - mStartTimeMs;
            SnippetsBridge.onSuggestionTargetVisited(mCategory, visitTimeMs);
        }
    }
}
