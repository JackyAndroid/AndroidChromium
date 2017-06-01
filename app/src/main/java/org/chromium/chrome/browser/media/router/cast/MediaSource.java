// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.net.Uri;
import android.support.v7.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Abstracts parsing the Cast application id and other parameters from the source URN.
 */
public class MediaSource {
    public static final String AUTOJOIN_CUSTOM_CONTROLLER_SCOPED = "custom_controller_scoped";
    public static final String AUTOJOIN_TAB_AND_ORIGIN_SCOPED = "tab_and_origin_scoped";
    public static final String AUTOJOIN_ORIGIN_SCOPED = "origin_scoped";
    public static final String AUTOJOIN_PAGE_SCOPED = "page_scoped";

    private static final String CAST_SOURCE_ID_SEPARATOR = "/";
    private static final String CAST_SOURCE_ID_APPLICATION_ID = "__castAppId__";
    private static final String CAST_SOURCE_ID_CLIENT_ID = "__castClientId__";
    private static final String CAST_SOURCE_ID_AUTOJOIN_POLICY = "__castAutoJoinPolicy__";
    private static final String CAST_APP_CAPABILITIES_PREFIX = "(";
    private static final String CAST_APP_CAPABILITIES_SUFFIX = ")";
    private static final String CAST_APP_CAPABILITIES_SEPARATOR = ",";
    private static final String CAST_APP_CAPABILITIES[] = {
        "video_out",
        "audio_out",
        "video_in",
        "audio_in",
        "multizone_group"
    };

    /**
     * The original presentation URL that the {@link MediaSource} object was created from.
     */
    private final String mSourceId;

    /**
     * The Cast application id, can be invalid in which case {@link CastMediaRouteProvider}
     * will explicitly report no sinks available.
     */
    private final String mApplicationId;

    /**
     * A numeric identifier for the Cast Web SDK, unique for the frame providing the
     * presentation URL. Can be null.
     */
    private final String mClientId;

    /**
     * Defines Cast-specific behavior for {@link CastMediaRouteProvider#joinRoute}. Defaults to
     * {@link MediaSource#AUTOJOIN_TAB_AND_ORIGIN_SCOPED}.
     */
    private final String mAutoJoinPolicy;

    /**
     * Defines the capabilities of the particular application id. Can be null.
     */
    private final String[] mCapabilities;

    /**
     * Initializes the media source from the source id.
     * @param sourceId the source id for the Cast media source (a presentation url).
     * @return an initialized media source if the id is valid, null otherwise.
     */
    @Nullable
    public static MediaSource from(String sourceId) {
        assert sourceId != null;

        Uri sourceUri = Uri.parse(sourceId);

        String uriFragment = sourceUri.getFragment();
        if (uriFragment == null) return null;

        String[] parameters = uriFragment.split(CAST_SOURCE_ID_SEPARATOR);

        String applicationId = extractParameter(parameters, CAST_SOURCE_ID_APPLICATION_ID);
        if (applicationId == null) return null;

        String[] capabilities = null;
        int capabilitiesIndex = applicationId.indexOf(CAST_APP_CAPABILITIES_PREFIX);
        if (capabilitiesIndex != -1) {
            capabilities = extractCapabilities(applicationId.substring(capabilitiesIndex));
            if (capabilities == null) return null;

            applicationId = applicationId.substring(0, capabilitiesIndex);
        }

        String clientId = extractParameter(parameters, CAST_SOURCE_ID_CLIENT_ID);
        String autoJoinPolicy = extractParameter(parameters, CAST_SOURCE_ID_AUTOJOIN_POLICY);

        return new MediaSource(sourceId, applicationId, clientId, autoJoinPolicy, capabilities);
    }

    /**
     * Returns a new {@link MediaRouteSelector} to use for Cast device filtering for this
     * particular media source or null if the application id is invalid.
     *
     * @return an initialized route selector or null.
     */
    public MediaRouteSelector buildRouteSelector() {
        try {
            return new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(mApplicationId))
                    .build();
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    /**
     * @return application capabilities
     */
    public String[] getCapabilities() {
        return mCapabilities == null ? null : Arrays.copyOf(mCapabilities, mCapabilities.length);
    }

    private MediaSource(
            String sourceId,
            String applicationId,
            String clientId,
            String autoJoinPolicy,
            String[] capabilities) {
        mSourceId = sourceId;
        mApplicationId = applicationId;
        mClientId = clientId;
        mAutoJoinPolicy = autoJoinPolicy == null ? AUTOJOIN_TAB_AND_ORIGIN_SCOPED : autoJoinPolicy;
        mCapabilities = capabilities;
    }

    @Nullable
    private static String extractParameter(String[] fragments, String key) {
        String keyPrefix = key + "=";
        for (String parameter : fragments) {
            if (parameter.startsWith(keyPrefix)) return parameter.substring(keyPrefix.length());
        }
        return null;
    }

    @Nullable
    private static String[] extractCapabilities(String capabilitiesParameter) {
        if (capabilitiesParameter.length()
                < CAST_APP_CAPABILITIES_PREFIX.length() + CAST_APP_CAPABILITIES_SUFFIX.length()) {
            return null;
        }

        if (!capabilitiesParameter.startsWith(CAST_APP_CAPABILITIES_PREFIX)
                || !capabilitiesParameter.endsWith(CAST_APP_CAPABILITIES_SUFFIX)) {
            return null;
        }

        List<String> supportedCapabilities = Arrays.asList(CAST_APP_CAPABILITIES);

        String capabilitiesList = capabilitiesParameter.substring(
                CAST_APP_CAPABILITIES_PREFIX.length(),
                capabilitiesParameter.length() - CAST_APP_CAPABILITIES_SUFFIX.length());
        String[] capabilities = capabilitiesList.split(CAST_APP_CAPABILITIES_SEPARATOR);
        for (String capability : capabilities) {
            if (!supportedCapabilities.contains(capability)) return null;
        }
        return capabilities;
    }
}
