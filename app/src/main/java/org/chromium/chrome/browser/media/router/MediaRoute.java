// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

/**
 * Contains all the info about the media route created by any {@link MediaRouteProvider}.
 */
public class MediaRoute {
    private static final String MEDIA_ROUTE_ID_PREFIX = "route:";
    private static final String MEDIA_ROUTE_ID_SEPARATOR = "/";

    /**
     * The unique id of the route, assigned by the {@link ChromeMediaRouter}.
     */
    public final String id;

    /**
     * The {@link MediaRouteProvider} unique id of the sink the route was created for.
     */
    public final String sinkId;

    /**
     * The presentation URL that the route was created for.
     */
    public final String sourceId;

    /**
     * The presentation id that was assigned to the route.
     */
    public final String presentationId;

    public MediaRoute(String sinkId, String sourceId, String presentationId) {
        this.id = createMediaRouteId(presentationId, sinkId, sourceId);
        this.sinkId = sinkId;
        this.sourceId = sourceId;
        this.presentationId = presentationId;
    }

    private static String createMediaRouteId(
            String presentationId, String sinkId, String sourceUrn) {
        StringBuilder builder = new StringBuilder();
        builder.append(MEDIA_ROUTE_ID_PREFIX);
        builder.append(presentationId);
        builder.append(MEDIA_ROUTE_ID_SEPARATOR);
        builder.append(sinkId);
        builder.append(MEDIA_ROUTE_ID_SEPARATOR);
        builder.append(sourceUrn);
        return builder.toString();
    }
}
