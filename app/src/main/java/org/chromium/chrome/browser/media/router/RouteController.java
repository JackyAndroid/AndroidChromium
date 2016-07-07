// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

/**
 * Interface defining operations related to a single {@link MediaRoute}.
 */
public interface RouteController {
    /**
     * Close the route.
     */
    void close();

    /**
     * Send a string message to the route and invokes the {@link RouteDelegate} with the
     * passed callback id on success or failure.
     * @param message The message to send.
     * @param nativeCallbackId The id of the callback handling the result.
     */
    void sendStringMessage(String message, int nativeCallbackId);

    /**
    * Sends a binary message to the route and invokes the {@link RouteDelegate} with the
    * passed callback id on success or failure.
    * @param data The binary message to send.
    * @param nativeCallbackId The id of the callback handling the result.
    */
    void sendBinaryMessage(byte[] data, int nativeCallbackId);

    /**
     * @return the source id for the route.
     */
    String getSourceId();

    /**
     * @return the route id
     */
    String getRouteId();

    /**
     * @return the media sink id for the route.
     */
    String getSinkId();

    /**
     * @return the origin of the frame that requested the route.
     */
    String getOrigin();

    /**
     * @return the id of the tab hosting the frame that requested the route.
     */
    int getTabId();

    /**
     * Marks the route as detached from the web page.
     */
    void markDetached();

    /**
     * @return if the route has been detached.
     */
    boolean isDetached();
}
