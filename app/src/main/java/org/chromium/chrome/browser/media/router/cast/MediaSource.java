// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.net.Uri;
import android.support.v7.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;

import javax.annotation.Nullable;

/**
 * Abstracts parsing the Cast application id and other parameters from the source URN.
 */
public class MediaSource {
    public static final String AUTOJOIN_CUSTOM_CONTROLLER_SCOPED = "custom_controller_scoped";
    public static final String AUTOJOIN_TAB_AND_ORIGIN_SCOPED = "tab_and_origin_scoped";
    public static final String AUTOJOIN_ORIGIN_SCOPED = "origin_scoped";
    public static final String AUTOJOIN_PAGE_SCOPED = "page_scoped";

    private static final String CAST_SOURCE_ID_HOST = "google.com";
    private static final String CAST_SOURCE_ID_PATH = "/cast";

    private static final String CAST_SOURCE_ID_SEPARATOR = "/";
    private static final String CAST_SOURCE_ID_APPLICATION_ID = "__castAppId__";
    private static final String CAST_SOURCE_ID_CLIENT_ID = "__castClientId__";
    private static final String CAST_SOURCE_ID_AUTOJOIN_POLICY = "__castAutoJoinPolicy__";

    private final String mSourceId;
    private final String mApplicationId;
    private final String mClientId;
    private final String mAutoJoinPolicy;

    /**
     * Initializes the media source from the source id.
     * @param sourceId the source id for the Cast media source (a presentation url).
     * @return an initialized media source if the id is valid, null otherwise.
     */
    @Nullable
    public static MediaSource from(String sourceId) {
        assert sourceId != null;

        Uri sourceUri = Uri.parse(sourceId);
        if (!CAST_SOURCE_ID_HOST.equals(sourceUri.getHost())) return null;
        if (!CAST_SOURCE_ID_PATH.equals(sourceUri.getPath())) return null;

        String uriFragment = sourceUri.getFragment();
        if (uriFragment == null) return null;

        String[] parameters = uriFragment.split(CAST_SOURCE_ID_SEPARATOR);

        String applicationId = extractParameter(parameters, CAST_SOURCE_ID_APPLICATION_ID);
        if (applicationId == null) return null;

        String clientId = extractParameter(parameters, CAST_SOURCE_ID_CLIENT_ID);
        String autoJoinPolicy = extractParameter(parameters, CAST_SOURCE_ID_AUTOJOIN_POLICY);

        return new MediaSource(sourceId, applicationId, clientId, autoJoinPolicy);
    }

    /**
     * Returns a new {@link MediaRouteSelector} to use for Cast device filtering for this
     * particular media source.
     * @return an initialized route selector.
     */
    public MediaRouteSelector buildRouteSelector() {
        return new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(mApplicationId))
                .build();
    }

    /**
     * @return the Cast application id corresponding to the source.
     */
    public String getApplicationId() {
        return mApplicationId;
    }

    /**
     * @return the client id if passed in the source id. Can be null.
     */
    @Nullable
    public String getClientId() {
        return mClientId;
    }

    /**
     * @return the auto join policy which must be one of the AUTOJOIN constants defined above.
     */
    public String getAutoJoinPolicy() {
        return mAutoJoinPolicy;
    }

    /**
     * @return the id identifying the media source
     */
    public String getUrn() {
        return mSourceId;
    }

    private MediaSource(
            String sourceId, String applicationId, String clientId, String autoJoinPolicy) {
        mSourceId = sourceId;
        mApplicationId = applicationId;
        mClientId = clientId;
        mAutoJoinPolicy = autoJoinPolicy == null ? AUTOJOIN_TAB_AND_ORIGIN_SCOPED : autoJoinPolicy;
    }

    @Nullable
    private static String extractParameter(String[] fragments, String key) {
        String keyPrefix = key + "=";
        for (String parameter : fragments) {
            if (parameter.startsWith(keyPrefix)) return parameter.substring(keyPrefix.length());
        }
        return null;
    }
}
