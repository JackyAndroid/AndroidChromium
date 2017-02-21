// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.os.Handler;
import android.support.v4.util.ArrayMap;
import android.util.SparseArray;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * The handler for cast messages. It receives events between the Cast SDK and the page, process and
 * dispatch the messages accordingly. The handler talks to the Cast SDK via CastSession, and
 * talks to the pages via the media router.
 */
public class CastMessageHandler {
    private static final String TAG = "MediaRouter";

    // Sequence number used when no sequence number is required or was initially passed.
    static final int INVALID_SEQUENCE_NUMBER = -1;
    static final String MEDIA_NAMESPACE = "urn:x-cast:com.google.cast.media";
    static final String GAMES_NAMESPACE = "urn:x-cast:com.google.cast.games";

    private static final String MEDIA_MESSAGE_TYPES[] = {
            "PLAY",
            "LOAD",
            "PAUSE",
            "SEEK",
            "STOP_MEDIA",
            "MEDIA_SET_VOLUME",
            "MEDIA_GET_STATUS",
            "EDIT_TRACKS_INFO",
            "QUEUE_LOAD",
            "QUEUE_INSERT",
            "QUEUE_UPDATE",
            "QUEUE_REMOVE",
            "QUEUE_REORDER",
    };

    private static final String MEDIA_SUPPORTED_COMMANDS[] = {
            "pause",
            "seek",
            "stream_volume",
            "stream_mute",
    };

    // Lock used to lazy initialize sMediaOverloadedMessageTypes.
    private static final Object INIT_LOCK = new Object();

    // Map associating types that have a different names outside of the media namespace and inside.
    // In other words, some types are sent as MEDIA_FOO or FOO_MEDIA by the client by the Cast
    // expect them to be named FOO. The reason being that FOO might exist in multiple namespaces
    // but the client isn't aware of namespacing.
    private static Map<String, String> sMediaOverloadedMessageTypes;

    private SparseArray<RequestRecord> mRequests;
    private ArrayMap<String, Queue<Integer>> mStopRequests;
    private Queue<RequestRecord> mVolumeRequests;

    // The reference to CastSession, only valid after calling {@link onSessionCreated}, and will be
    // reset to null when calling {@link onApplicationStopped}.
    private CastSession mSession = null;
    private final CastMediaRouteProvider mRouteProvider;
    private Handler mHandler;

    /**
     * The record for client requests. {@link CastMessageHandler} uses this class to manage the
     * client requests and match responses to the requests.
     */
    static class RequestRecord {
        public final String clientId;
        public final int sequenceNumber;

        public RequestRecord(String clientId, int sequenceNumber) {
            this.clientId = clientId;
            this.sequenceNumber = sequenceNumber;
        }
    }

    /**
     * Initializes a new {@link CastMessageHandler} instance.
     * @param session  The {@link CastSession} for communicating with the Cast SDK.
     * @param provider The {@link CastMediaRouteProvider} for communicating with the page.
     */
    public CastMessageHandler(CastMediaRouteProvider provider) {
        mRouteProvider = provider;
        mRequests = new SparseArray<RequestRecord>();
        mStopRequests = new ArrayMap<String, Queue<Integer>>();
        mVolumeRequests = new ArrayDeque<RequestRecord>();
        mHandler = new Handler();

        synchronized (INIT_LOCK) {
            if (sMediaOverloadedMessageTypes == null) {
                sMediaOverloadedMessageTypes = new HashMap<String, String>();
                sMediaOverloadedMessageTypes.put("STOP_MEDIA", "STOP");
                sMediaOverloadedMessageTypes.put("MEDIA_SET_VOLUME", "SET_VOLUME");
                sMediaOverloadedMessageTypes.put("MEDIA_GET_STATUS", "GET_STATUS");
            }
        }
    }

    @VisibleForTesting
    static String[] getMediaMessageTypesForTest() {
        return MEDIA_MESSAGE_TYPES;
    }

    @VisibleForTesting
    static Map<String, String> getMediaOverloadedMessageTypesForTest() {
        return sMediaOverloadedMessageTypes;
    }

    @VisibleForTesting
    SparseArray<RequestRecord> getRequestsForTest() {
        return mRequests;
    }

    @VisibleForTesting
    Queue<RequestRecord> getVolumeRequestsForTest() {
        return mVolumeRequests;
    }

    @VisibleForTesting
    Map<String, Queue<Integer>> getStopRequestsForTest() {
        return mStopRequests;
    }

    /**
     * Set the session when a session is created, and notify all clients that are not connected.
     * @param session The newly created session.
     */
    public void onSessionCreated(CastSession session) {
        mSession = session;
        for (ClientRecord client : mRouteProvider.getClientRecords().values()) {
            if (!client.isConnected) continue;

            mSession.onClientConnected(client.clientId);
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Functions for handling messages from the page to the Cast device.

    /**
     * Handles messages related to the cast session, i.e. messages happening on a established
     * connection. All these messages are sent from the page to the Cast SDK.
     * @param message The JSONObject message to be handled.
     */
    public boolean handleSessionMessage(JSONObject message) throws JSONException {
        String messageType = message.getString("type");
        if ("v2_message".equals(messageType)) {
            return handleCastV2Message(message);
        } else if ("app_message".equals(messageType)) {
            return handleAppMessage(message);
        } else {
            Log.e(TAG, "Unsupported message: %s", message);
            return false;
        }
    }

    // An example of the Cast V2 message:
    //    {
    //        "type": "v2_message",
    //        "message": {
    //          "type": "...",
    //          ...
    //        },
    //        "sequenceNumber": 0,
    //        "timeoutMillis": 0,
    //        "clientId": "144042901280235697"
    //    }
    @VisibleForTesting
    boolean handleCastV2Message(JSONObject jsonMessage)
            throws JSONException {
        assert "v2_message".equals(jsonMessage.getString("type"));

        final String clientId = jsonMessage.getString("clientId");
        if (clientId == null || !mRouteProvider.getClients().contains(clientId)) return false;

        JSONObject jsonCastMessage = jsonMessage.getJSONObject("message");
        String messageType = jsonCastMessage.getString("type");
        final int sequenceNumber = jsonMessage.optInt("sequenceNumber", INVALID_SEQUENCE_NUMBER);

        if ("STOP".equals(messageType)) {
            handleStopMessage(clientId, sequenceNumber);
            return true;
        }

        if ("SET_VOLUME".equals(messageType)) {
            CastSession.HandleVolumeMessageResult result =
                    mSession.handleVolumeMessage(
                    jsonCastMessage.getJSONObject("volume"), clientId, sequenceNumber);
            if (!result.mSucceeded) return false;

            // For each successful volume message we need to respond with an empty "v2_message" so
            // the Cast Web SDK can call the success callback of the page.  If we expect the volume
            // to change as the result of the command, we're relying on {@link
            // Cast.CastListener#onVolumeChanged} to get called by the Android Cast SDK when the
            // receiver status is updated. We keep the sequence number until then.  If the volume
            // doesn't change as the result of the command, we won't get notified by the Android SDK
            // when the status update is received so we respond to the volume message immediately.
            if (result.mShouldWaitForVolumeChange) {
                mVolumeRequests.add(new RequestRecord(clientId, sequenceNumber));
            } else {
                // It's usually bad to have request and response on the same call stack so post the
                // response to the Android message loop.
                mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onVolumeChanged(clientId, sequenceNumber);
                        }
                    });
            }
            return true;
        }

        if (Arrays.asList(MEDIA_MESSAGE_TYPES).contains(messageType)) {
            if (sMediaOverloadedMessageTypes.containsKey(messageType)) {
                messageType = sMediaOverloadedMessageTypes.get(messageType);
                jsonCastMessage.put("type", messageType);
            }
            return sendJsonCastMessage(jsonCastMessage, MEDIA_NAMESPACE, clientId, sequenceNumber);
        }

        return true;
    }

    @VisibleForTesting
    void handleStopMessage(String clientId, int sequenceNumber) {
        Queue<Integer> sequenceNumbersForClient = mStopRequests.get(clientId);
        if (sequenceNumbersForClient == null) {
            sequenceNumbersForClient = new ArrayDeque<Integer>();
            mStopRequests.put(clientId, sequenceNumbersForClient);
        }
        sequenceNumbersForClient.add(sequenceNumber);

        mSession.stopApplication();
    }

    // An example of the Cast application message:
    // {
    //   "type":"app_message",
    //   "message": {
    //     "sessionId":"...",
    //     "namespaceName":"...",
    //     "message": ...
    //   },
    //   "sequenceNumber":0,
    //   "timeoutMillis":3000,
    //   "clientId":"14417311915272175"
    // }
    @VisibleForTesting
    boolean handleAppMessage(JSONObject jsonMessage) throws JSONException {
        assert "app_message".equals(jsonMessage.getString("type"));

        String clientId = jsonMessage.getString("clientId");
        if (clientId == null || !mRouteProvider.getClients().contains(clientId)) return false;

        JSONObject jsonAppMessageWrapper = jsonMessage.getJSONObject("message");

        if (!mSession.getSessionId().equals(jsonAppMessageWrapper.getString("sessionId"))) {
            return false;
        }

        String namespaceName = jsonAppMessageWrapper.getString("namespaceName");
        if (namespaceName == null || namespaceName.isEmpty()) return false;

        if (!mSession.getNamespaces().contains(namespaceName)) return false;

        int sequenceNumber = jsonMessage.optInt("sequenceNumber", INVALID_SEQUENCE_NUMBER);

        Object actualMessageObject = jsonAppMessageWrapper.get("message");
        if (actualMessageObject == null) return false;

        if (actualMessageObject instanceof String) {
            String actualMessage = jsonAppMessageWrapper.getString("message");
            return mSession.sendStringCastMessage(
                    actualMessage, namespaceName, clientId, sequenceNumber);
        }

        JSONObject actualMessage = jsonAppMessageWrapper.getJSONObject("message");
        return sendJsonCastMessage(actualMessage, namespaceName, clientId, sequenceNumber);
    }

    @VisibleForTesting
    boolean sendJsonCastMessage(
            JSONObject message,
            final String namespace,
            final String clientId,
            final int sequenceNumber) throws JSONException {
        if (mSession.isApiClientInvalid()) return false;

        removeNullFields(message);

        // Map the request id to a valid sequence number only.
        if (sequenceNumber != INVALID_SEQUENCE_NUMBER) {
            // If for some reason, there is already a requestId other than 0, it
            // is kept. Otherwise, one is generated. In all cases it's associated with the
            // sequenceNumber passed by the client.
            int requestId = message.optInt("requestId", 0);
            if (requestId == 0) {
                requestId = CastRequestIdGenerator.getNextRequestId();
                message.put("requestId", requestId);
            }
            mRequests.append(requestId, new RequestRecord(clientId, sequenceNumber));
        }

        return mSession.sendStringCastMessage(
                message.toString(), namespace, clientId, sequenceNumber);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Functions for handling messages from the Cast device to the pages.

    /**
     * Forwards the messages from the Cast device to the clients, and perform proper actions if it
     * is media message.
     * @param namespace The application specific namespace this message belongs to.
     * @param message The message within the namespace that's being sent by the receiver
     */
    public void onMessageReceived(String namespace, String message) {
        RequestRecord request = null;
        try {
            JSONObject jsonMessage = new JSONObject(message);
            int requestId = jsonMessage.getInt("requestId");
            if (mRequests.indexOfKey(requestId) >= 0) {
                request = mRequests.get(requestId);
                mRequests.delete(requestId);
            }
        } catch (JSONException e) {
        }

        if (MEDIA_NAMESPACE.equals(namespace)) {
            onMediaMessage(message, request);
            return;
        }

        onAppMessage(message, namespace, request);
    }

    /**
     * Forwards the media message to the page via the media router.
     * The MEDIA_STATUS message needs to be sent to all the clients.
     * @param message The media that's being send by the receiver.
     * @param request The information about the client and the sequence number to respond with.
     */
    @VisibleForTesting
    void onMediaMessage(String message, RequestRecord request) {
        mSession.onMediaMessage(message);

        if (isMediaStatusMessage(message)) {
            // MEDIA_STATUS needs to be sent to all the clients.
            for (String clientId : mRouteProvider.getClients()) {
                if (request != null && clientId.equals(request.clientId)) continue;

                sendClientMessageTo(
                        clientId, "v2_message", message, INVALID_SEQUENCE_NUMBER);
            }
        }
        if (request != null) {
            sendClientMessageTo(
                    request.clientId, "v2_message", message, request.sequenceNumber);
        }
    }

    /**
     * Forwards the application specific message to the page via the media router.
     * @param message The message within the namespace that's being sent by the receiver.
     * @param namespace The application specific namespace this message belongs to.
     * @param request The information about the client and the sequence number to respond with.
     */
    @VisibleForTesting
    void onAppMessage(String message, String namespace, RequestRecord request) {
        try {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("sessionId", mSession.getSessionId());
            jsonMessage.put("namespaceName", namespace);
            jsonMessage.put("message", message);
            if (request != null) {
                sendClientMessageTo(request.clientId, "app_message",
                        jsonMessage.toString(), request.sequenceNumber);
            } else {
                broadcastClientMessage("app_message", jsonMessage.toString());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create the message wrapper", e);
        }
    }

    /**
     * Notifies the application has stopped to all requesting clients.
     */
    public void onApplicationStopped() {
        for (String clientId : mRouteProvider.getClients()) {
            Queue<Integer> sequenceNumbersForClient = mStopRequests.get(clientId);
            if (sequenceNumbersForClient == null) {
                sendClientMessageTo(
                        clientId, "remove_session", mSession.getSessionId(),
                        INVALID_SEQUENCE_NUMBER);
                continue;
            }

            for (int sequenceNumber : sequenceNumbersForClient) {
                sendClientMessageTo(
                        clientId, "remove_session", mSession.getSessionId(), sequenceNumber);
            }
            mStopRequests.remove(clientId);
        }
        mSession = null;
    }

    /**
     * When the Cast device volume really changed, updates the session status and notify all
     * requesting clients.
     */
    public void onVolumeChanged() {
        mSession.updateSessionStatus();

        if (mVolumeRequests.isEmpty()) return;

        for (RequestRecord r : mVolumeRequests) onVolumeChanged(r.clientId, r.sequenceNumber);
        mVolumeRequests.clear();
    }

    @VisibleForTesting
    void onVolumeChanged(String clientId, int sequenceNumber) {
        sendClientMessageTo(clientId, "v2_message", null, sequenceNumber);
    }

    /**
     * Notifies a client that an app message has been sent.
     * @param clientId The client id the message is sent from.
     * @param sequenceNumber The sequence number of the message.
     */
    public void onAppMessageSent(String clientId, int sequenceNumber) {
        sendClientMessageTo(clientId, "app_message", null, sequenceNumber);
    }

    /**
     * Broadcasts the message to all clients.
     * @param type    The type of the message.
     * @param message The message to broadcast.
     */
    public void broadcastClientMessage(String type, String message) {
        for (String clientId : mRouteProvider.getClients()) {
            sendClientMessageTo(clientId, type, message, INVALID_SEQUENCE_NUMBER);
        }
    }

    /**
     * Sends a message to a specific client.
     * @param clientId The id of the receiving client.
     * @param type     The type of the message.
     * @param message  The message to be sent.
     * @param sequenceNumber The sequence number for matching requesting and responding messages.
     */
    public void sendClientMessageTo(
            String clientId, String type, String message, int sequenceNumber) {
        mRouteProvider.onMessage(clientId,
                buildInternalMessage(type, message, clientId, sequenceNumber));
    }

    @VisibleForTesting
    String buildInternalMessage(
            String type, String message, String clientId, int sequenceNumber) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("sequenceNumber", sequenceNumber);
            json.put("timeoutMillis", 0);
            json.put("clientId", clientId);

            // TODO(mlamouri): we should have a more reliable way to handle string, null and Object
            // messages.
            if (message == null
                    || "remove_session".equals(type)
                    || "disconnect_session".equals(type)) {
                json.put("message", message);
            } else {
                JSONObject jsonMessage = new JSONObject(message);
                if ("v2_message".equals(type)
                        && "MEDIA_STATUS".equals(jsonMessage.getString("type"))) {
                    sanitizeMediaStatusMessage(jsonMessage);
                }
                json.put("message", jsonMessage);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build the reply: " + e);
        }

        return json.toString();
    }

    /**
     * @return A message containing the information of the {@link CastSession}.
     */
    public String buildSessionMessage() {
        if (mSession == null) return "{}";

        CastSessionInfo sessionInfo = mSession.getSessionInfo();
        if (sessionInfo == null) return "{}";

        try {
            // "volume" is a part of "receiver" initialized below.
            JSONObject jsonVolume = new JSONObject();
            jsonVolume.put("level", sessionInfo.receiver.volume.level);
            jsonVolume.put("muted", sessionInfo.receiver.volume.muted);

            // "receiver" is a part of "message" initialized below.
            JSONObject jsonReceiver = new JSONObject();
            jsonReceiver.put("label", sessionInfo.receiver.label);
            jsonReceiver.put("friendlyName", sessionInfo.receiver.friendlyName);
            jsonReceiver.put("capabilities", toJSONArray(sessionInfo.receiver.capabilities));
            jsonReceiver.put("volume", jsonVolume);
            jsonReceiver.put("isActiveInput", sessionInfo.receiver.isActiveInput);
            jsonReceiver.put("displayStatus", sessionInfo.receiver.displayStatus);
            jsonReceiver.put("receiverType", sessionInfo.receiver.receiverType);

            JSONArray jsonNamespaces = new JSONArray();
            for (String namespace : sessionInfo.namespaces) {
                JSONObject jsonNamespace = new JSONObject();
                jsonNamespace.put("name", namespace);
                jsonNamespaces.put(jsonNamespace);
            }

            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("sessionId", sessionInfo.sessionId);
            jsonMessage.put("statusText", sessionInfo.statusText);
            jsonMessage.put("receiver", jsonReceiver);
            jsonMessage.put("namespaces", jsonNamespaces);
            jsonMessage.put("media", toJSONArray(sessionInfo.media));
            jsonMessage.put("status", sessionInfo.status);
            jsonMessage.put("transportId", sessionInfo.transportId);
            jsonMessage.put("appId", sessionInfo.appId);
            jsonMessage.put("displayName", sessionInfo.displayName);

            return jsonMessage.toString();
        } catch (JSONException e) {
            Log.w(TAG, "Building session message failed", e);
            return "{}";
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Utility functions

    /**
     * Modifies the received MediaStatus message to match the format expected by the client.
     */
    private void sanitizeMediaStatusMessage(JSONObject object) throws JSONException {
        object.put("sessionId", mSession.getSessionId());

        JSONArray mediaStatus = object.getJSONArray("status");
        for (int i = 0; i < mediaStatus.length(); ++i) {
            JSONObject status = mediaStatus.getJSONObject(i);
            status.put("sessionId", mSession.getSessionId());
            if (!status.has("supportedMediaCommands")) continue;

            JSONArray commands = new JSONArray();
            int bitfieldCommands = status.getInt("supportedMediaCommands");
            for (int j = 0; j < 4; ++j) {
                if ((bitfieldCommands & (1 << j)) != 0) {
                    commands.put(MEDIA_SUPPORTED_COMMANDS[j]);
                }
            }

            status.put("supportedMediaCommands", commands);  // Removes current entry.
        }
    }

    /**
     * Remove 'null' fields from a JSONObject. This method calls itself recursively until all the
     * fields have been looked at.
     * TODO(mlamouri): move to some util class?
     */
    private static void removeNullFields(Object object) throws JSONException {
        if (object instanceof JSONArray) {
            JSONArray array = (JSONArray) object;
            for (int i = 0; i < array.length(); ++i) removeNullFields(array.get(i));
        } else if (object instanceof JSONObject) {
            JSONObject json = (JSONObject) object;
            JSONArray names = json.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); ++i) {
                String key = names.getString(i);
                if (json.isNull(key)) {
                    json.remove(key);
                } else {
                    removeNullFields(json.get(key));
                }
            }
        }
    }

    @VisibleForTesting
    boolean isMediaStatusMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            return "MEDIA_STATUS".equals(jsonMessage.getString("type"));
        } catch (JSONException e) {
            return false;
        }
    }

    private JSONArray toJSONArray(List<String> from) throws JSONException {
        JSONArray result = new JSONArray();
        for (String entry : from) {
            result.put(entry);
        }
        return result;
    }
}
