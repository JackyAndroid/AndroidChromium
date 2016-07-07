// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.net.Uri;

import org.chromium.chrome.browser.search_engines.TemplateUrlService;

import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.Nullable;


/**
 * Bundles a Search Request URL with a low-priority version of the URL, helps manage the
 * fall-back when the low-priority version fails, and tracks which one is in use.
 */
class ContextualSearchRequest {

    private final Uri mLowPriorityUri;
    private final Uri mNormalPriorityUri;
    private final boolean mWasPrefetch;

    private boolean mIsLowPriority;
    private boolean mHasFailedLowPriorityLoad;

    private static final String CTXS_PARAM_PATTERN = "(ctxs=[^&]+)";
    private static final String CTXR_PARAM = "ctxr";

    /**
     * Creates a search request for the given search term without any alternate term and
     * for normal-priority loading capability only.
     * @param searchTerm The resolved search term.
     */
    ContextualSearchRequest(String searchTerm) {
        this(searchTerm, null, false);
    }

    /**
     * Creates a search request for the given search term with the given alternate term and
     * low-priority loading capability.
     * @param searchTerm The resolved search term.
     * @param alternateTerm The alternate search term.
     * @param isLowPriorityEnabled Whether the request can be made at a low priority.
     */
    ContextualSearchRequest(String searchTerm, @Nullable String alternateTerm,
            boolean isLowPriorityEnabled) {
        mWasPrefetch = isLowPriorityEnabled;
        mNormalPriorityUri = getUriTemplate(searchTerm, alternateTerm, false);
        if (isLowPriorityEnabled) {
            // TODO(donnd): Call TemplateURL once we have an API for 3rd-party providers.
            Uri baseLowPriorityUri = getUriTemplate(searchTerm, alternateTerm, true);
            mLowPriorityUri = baseLowPriorityUri.buildUpon()
                .path("s")
                .appendQueryParameter("sns", "1")
                .build();
            mIsLowPriority = true;
        } else {
            mIsLowPriority = false;
            mLowPriorityUri = null;
        }
    }

    /**
     * Sets an indicator that the normal-priority URL should be used for this search request.
     */
    void setNormalPriority() {
        mIsLowPriority = false;
    }

    /**
     * @return whether the low priority URL is being used.
     */
    boolean isUsingLowPriority() {
        return mIsLowPriority;
    }

    /**
     * @return whether this request started as a prefetch request.
     */
    boolean wasPrefetch() {
        return mWasPrefetch;
    }

    /**
     * Sets that this search request has failed.
     */
    void setHasFailed() {
        mHasFailedLowPriorityLoad = true;
    }

    /**
     * @return whether the load has failed for this search request or not.
     */
    boolean getHasFailed() {
        return mHasFailedLowPriorityLoad;
    }

    /**
     * Gets the search URL for this request.
     * @return either the low-priority or normal-priority URL for this search request.
     */
    String getSearchUrl() {
        if (mIsLowPriority && mLowPriorityUri != null) {
            return mLowPriorityUri.toString();
        } else {
            return mNormalPriorityUri.toString();
        }
    }

    /**
     * Returns whether the given URL is the current Contextual Search URL.
     * @param url The given URL.
     * @return Whether it is the current Contextual Search URL.
     */
    boolean isContextualSearchUrl(String url) {
        return url.equals(getSearchUrl());
    }

    /**
     * Returns the formatted Search URL, replacing the ctxs param with the ctxr param, so that
     * the SearchBox will becomes visible, while preserving the Answers Mode.
     *
     * @return The formatted Search URL.
     */
    String getSearchUrlForPromotion() {
        String searchUrl = getSearchUrl();

        URL url;
        try {
            url = new URL(searchUrl.replaceAll(CTXS_PARAM_PATTERN, CTXR_PARAM));
        } catch (MalformedURLException e) {
            url = null;
        }

        return url != null ? url.toString() : null;
    }

    /**
     * Uses TemplateUrlService to generate the url for the given query
     * {@link String} for {@code query} with the contextual search version param set.
     * @param query The search term to use as the main query in the returned search url.
     * @param alternateTerm The alternate search term to use as an alternate suggestion.
     * @param shouldPrefetch Whether the returned url should include a prefetch parameter.
     * @return      A {@link String} that contains the url of the default search engine with
     *              {@code query} and {@code alternateTerm} inserted as parameters and contextual
     *              search and prefetch parameters conditionally set.
     */
    private Uri getUriTemplate(String query, @Nullable String alternateTerm,
            boolean shouldPrefetch) {
        return Uri.parse(TemplateUrlService.getInstance().getUrlForContextualSearchQuery(
                query, alternateTerm, shouldPrefetch));
    }
}
