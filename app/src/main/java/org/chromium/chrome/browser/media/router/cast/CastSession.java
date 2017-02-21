// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * The interface for talking to the GMS core. We need to isolate this
 * interface from its implementation so that we can mock it with out
 * dependencies on the GMS core. Otherwise junit tests will fail on
 * the bots since they don't support GMS core for junit tests. For
 * example, see: https://crbug.com/588758
 */
public interface CastSession {
    /**
     * The return type for {@link handleVolumeMessage}.
     */
    static class HandleVolumeMessageResult {
        public final boolean mSucceeded;
        public final boolean mShouldWaitForVolumeChange;

        /**
         * Initializes a {@link HandleVolumeMessageResult}.
         */
        public HandleVolumeMessageResult(boolean succeeded, boolean shouldWaitForVolumeChange) {
            mSucceeded = succeeded;
            mShouldWaitForVolumeChange = shouldWaitForVolumeChange;
        }
    }

    /**
     * @return If the gms core api client is invalid.
     */
    boolean isApiClientInvalid();

    /**
     * @return The sink id of the CastSession.
     */
    String getSinkId();

    /**
     * @return The source id of the CastSession.
     */
    String getSourceId();

    /**
     * @return The id of the CastSession.
     */
    String getSessionId();

    /**
     * @return The namespaces supported by the CastSession.
     */
    Set<String> getNamespaces();

    /**
     * @return The message handler of the CastSession.
     */
    CastMessageHandler getMessageHandler();

    /**
     * @return The session information.
     */
    CastSessionInfo getSessionInfo();

    /**
     * Sends the string message to the Cast device through the Cast SDK.
     * @param message        The message to send.
     * @param namespace      The namespace of the message.
     * @param clientId       The id of the client sending the message.
     * @param sequenceNumber The sequence number of the message, which is used for matching
     *                       responses to requests.
     */
    boolean sendStringCastMessage(
            String message, String namespace, String clientId, int sequenceNumber);

    /**
     * Handles SET_VOLUME messages, and sets the volume of the Cast device through the Cast SDK.
     * @param volume         A JSONObject containing the volume information.
     *                       Example:
     *                       {
     *                         "volume" {
     *                           "level": 0.9,
     *                           "muted": null
     *                         }
     *                       }
     * @param clientId       The id of the client sending the message.
     * @param sequenceNumber The sequence number of the message, which is used for matching
     *                       responses to requests.
     */
    HandleVolumeMessageResult handleVolumeMessage(
            JSONObject volume, String clientId, int sequenceNumber)
            throws JSONException;

    /**
     * Stops the application. The methods tells the Cast SDK to stop the application and on
     * response, it will notify all the clients through the message ahndler.
     */
    void stopApplication();

    /**
     * Perform proper actions when a client is connected to the session.
     */
    void onClientConnected(String clientId);

    /**
     * When a media message is received from the Cast device, forwards the message to the
     * MediaPlayer.
     * @param message The media message received from the Cast device.
     */
    void onMediaMessage(String message);

    /**
     * Perform proper actions when the Cast device volume has changed.
     */
    void onVolumeChanged();

    /**
     * Updates the session info when it changes and broadcast the change.
     */
    void updateSessionStatus();
}
