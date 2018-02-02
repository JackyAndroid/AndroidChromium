// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.content.Context;

/**
 * An interface components providing media sinks and routes need to implement to hooks up into
 * {@link ChromeMediaRouter}.
 */
public interface MediaRouteProvider {
    /**
     * Builder for {@link MediaRouteProvider}.
     */
    interface Builder {
        MediaRouteProvider create(Context applicationContext, MediaRouteManager manager);
    }
    /**
     * @param sourceId The id of the source to check.
     * @return if the specified source is supported by this route provider.
     */
    boolean supportsSource(String sourceId);

    /**
     * Initiates the discovery of media sinks corresponding to the given source id. Does nothing if
     * the source id is not supported by the MRP.
     * @param sourceId The id of the source to discover the media sinks for.
     */
    void startObservingMediaSinks(String sourceId);

    /**
     * Stops the discovery of media sinks corresponding to the given source id. Does nothing if
     * the source id is not supported by the MRP.
     * @param sourceId The id of the source to discover the media sinks for.
     */
    void stopObservingMediaSinks(String sourceId);

    /**
     * Tries to create a media route from the given media source to the media sink.
     * @param sourceId The source to create the route for.
     * @param sinkId The sink to create the route for.
     * @param presentationId The presentation id generated for this route.
     * @param origin The origin of the frame initiating the request.
     * @param tabId The id of the tab containing the frame initiating the request.
     * @param isIncognito Whether the route is being requested from an Incognito profile.
     * @param nativeRequestId The id of the request tracked by the native side.
     */
    void createRoute(String sourceId, String sinkId, String presentationId, String origin,
            int tabId, boolean isIncognito, int nativeRequestId);

    /**
     * Tries to join an existing media route for the given media source and presentation id.
     * @param sourceId The source of the route to join.
     * @param presentationId The presentation id for the route to join.
     * @param origin The origin of the frame initiating the request.
     * @param tabId The id of the tab containing the frame initiating the request.
     * @param nativeRequestId The id of the request tracked by the native side.
     */
    void joinRoute(String sourceId, String presentationId, String origin, int tabId,
            int nativeRequestId);

    /**
     * Closes the media route with the given id. The route must be created by this provider.
     * @param routeId The id of the route to close.
     */
    void closeRoute(String routeId);

    /**
     * Notifies the route that the page is not attached to it any longer. The route must be created
     * by this provider.
     * @param routeId The id of the route.
     */
    void detachRoute(String routeId);

    /**
     * Sends a message to the route with the given id. The route must be created by this provider.
     * @param routeId The id of the route to send the message to.
     * @param message The message to send.
     * @param nativeCallbackId The id of the result callback tracked by the native side.
     */
    void sendStringMessage(String routeId, String message, int nativeCallbackId);

    /**
     * Sends a binary message to the route with the given id. The route must be created by this
     * provider.
     * @param routeId The id of the route to send the message to.
     * @param data The binary message to send.
     * @param nativeCallbackId The id of the result callback tracked by the native side.
     */
    void sendBinaryMessage(String routeId, byte[] data, int nativeCallbackId);
}
