// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.content.Context;
import android.os.Handler;
import android.util.SparseIntArray;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.router.ChromeMediaRouter;
import org.chromium.chrome.browser.media.router.RouteController;
import org.chromium.chrome.browser.media.router.RouteDelegate;
import org.chromium.chrome.browser.media.ui.MediaNotificationInfo;
import org.chromium.chrome.browser.media.ui.MediaNotificationListener;
import org.chromium.chrome.browser.media.ui.MediaNotificationManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A wrapper around the established Cast application session.
 */
public class CastRouteController implements RouteController, MediaNotificationListener {
    private static final String TAG = "cr_MediaRouter";

    private static final String MEDIA_NAMESPACE = "urn:x-cast:com.google.cast.media";
    private static final String GAMES_NAMESPACE = "urn:x-cast:com.google.cast.games";

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

    // Sequence number used when no sequence number is required or was initially passed.
    private static final int INVALID_SEQUENCE_NUMBER = -1;

    // Map associating types that have a different names outside of the media namespace and inside.
    // In other words, some types are sent as MEDIA_FOO or FOO_MEDIA by the client by the Cast
    // expect them to be named FOO. The reason being that FOO might exist in multiple namespaces
    // but the client isn't aware of namespacing.
    private static Map<String, String> sMediaOverloadedMessageTypes;

    // Lock used to lazy initialize sMediaOverloadedMessageTypes.
    private static final Object INIT_LOCK = new Object();

    // The value is borrowed from the Android Cast SDK code to match their behavior.
    private static final double MIN_VOLUME_LEVEL_DELTA = 1e-7;

    private static class CastMessagingChannel implements Cast.MessageReceivedCallback {
        private final CastRouteController mSession;

        public CastMessagingChannel(CastRouteController session) {
            mSession = session;
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "Received message from Cast device: namespace=\"" + namespace
                       + "\" message=\"" + message + "\"");

            int sequenceNumber = INVALID_SEQUENCE_NUMBER;
            try {
                JSONObject jsonMessage = new JSONObject(message);
                int requestId = jsonMessage.getInt("requestId");
                if (mSession.mRequests.indexOfKey(requestId) >= 0) {
                    sequenceNumber = mSession.mRequests.get(requestId);
                    mSession.mRequests.delete(requestId);
                }
            } catch (JSONException e) {
            }

            if (MEDIA_NAMESPACE.equals(namespace)) {
                mSession.onMediaMessage(message, sequenceNumber);
                return;
            }

            mSession.onAppMessage(message, namespace, sequenceNumber);
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

    private final String mMediaRouteId;
    private final String mOrigin;
    private final int mTabId;
    private final CastMessagingChannel mMessageChannel;
    private final RouteDelegate mRouteDelegate;
    private final CastDevice mCastDevice;
    private final MediaSource mSource;

    // Ids of the connected Cast clients.
    private Set<String> mClients = new HashSet<String>();
    private Set<String> mNamespaces = new HashSet<String>();
    private GoogleApiClient mApiClient;
    private String mSessionId;
    private String mApplicationStatus;
    private ApplicationMetadata mApplicationMetadata;
    private boolean mStoppingApplication;
    private boolean mDetached;
    private MediaNotificationInfo.Builder mNotificationBuilder;
    private RemoteMediaPlayer mMediaPlayer;

    private SparseIntArray mRequests;
    private Queue<Integer> mVolumeRequestSequenceNumbers = new ArrayDeque<Integer>();

    private Handler mHandler;

    /**
     * Initializes a new {@link CastRouteController} instance.
     * @param apiClient The Google Play Services client used to create the session.
     * @param sessionId The session identifier to use with the Cast SDK.
     * @param mediaRouteId The media route identifier associated with this session.
     * @param origin The origin of the frame requesting the route.
     * @param tabId the id of the tab containing the frame requesting the route.
     * @param source The {@link MediaSource} corresponding to this session.
     * @param mediaRouter The {@link ChromeMediaRouter} instance managing this session.
     */
    public CastRouteController(
            GoogleApiClient apiClient,
            String sessionId,
            ApplicationMetadata metadata,
            String applicationStatus,
            CastDevice castDevice,
            String mediaRouteId,
            String origin,
            int tabId,
            MediaSource source,
            RouteDelegate delegate) {
        mApiClient = apiClient;
        mSessionId = sessionId;
        mMediaRouteId = mediaRouteId;
        mOrigin = origin;
        mTabId = tabId;
        mSource = source;
        mRouteDelegate = delegate;
        mApplicationMetadata = metadata;
        mApplicationStatus = applicationStatus;
        mCastDevice = castDevice;
        mRequests = new SparseIntArray();
        mHandler = new Handler();

        mMessageChannel = new CastMessagingChannel(this);
        addNamespace(MEDIA_NAMESPACE);
        addNamespace(GAMES_NAMESPACE);

        final Context context = ApplicationStatus.getApplicationContext();

        if (mNamespaces.contains(MEDIA_NAMESPACE)) {
            mMediaPlayer = new RemoteMediaPlayer();
            mMediaPlayer.setOnStatusUpdatedListener(
                    new RemoteMediaPlayer.OnStatusUpdatedListener() {
                        @Override
                        public void onStatusUpdated() {
                            MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
                            if (mediaStatus == null) return;

                            int playerState = mediaStatus.getPlayerState();
                            if (playerState == MediaStatus.PLAYER_STATE_PAUSED
                                    || playerState == MediaStatus.PLAYER_STATE_PLAYING) {
                                mNotificationBuilder.setPaused(
                                        playerState != MediaStatus.PLAYER_STATE_PLAYING);
                                mNotificationBuilder.setActions(MediaNotificationInfo.ACTION_STOP
                                        | MediaNotificationInfo.ACTION_PLAY_PAUSE);
                            } else {
                                mNotificationBuilder.setActions(MediaNotificationInfo.ACTION_STOP);
                            }
                            MediaNotificationManager.show(context, mNotificationBuilder);
                        }
                    });
        }

        mNotificationBuilder = new MediaNotificationInfo.Builder()
                .setTitle(mCastDevice.getFriendlyName())
                .setPaused(false)
                .setOrigin(origin)
                .setTabId(tabId)
                // TODO(avayvod): pass true here if initiated from the incognito mode.
                // MediaRouter is disabled for Incognito mode for now, see https://crbug.com/525215
                .setPrivate(false)
                .setIcon(R.drawable.ic_notification_media_route)
                .setActions(MediaNotificationInfo.ACTION_STOP)
                .setId(R.id.presentation_notification)
                .setListener(this);
        MediaNotificationManager.show(context, mNotificationBuilder);

        synchronized (INIT_LOCK) {
            if (sMediaOverloadedMessageTypes == null) {
                sMediaOverloadedMessageTypes = new HashMap<String, String>();
                sMediaOverloadedMessageTypes.put("STOP_MEDIA", "STOP");
                sMediaOverloadedMessageTypes.put("MEDIA_SET_VOLUME", "SET_VOLUME");
                sMediaOverloadedMessageTypes.put("MEDIA_GET_STATUS", "GET_STATUS");
            }
        }
    }

    public CastRouteController createJoinedController(String mediaRouteId, String origin, int tabId,
            MediaSource source) {
        return new CastRouteController(mApiClient, mSessionId, mApplicationMetadata,
                mApplicationStatus, mCastDevice, mediaRouteId, origin, tabId, source,
                mRouteDelegate);
    }

    /**
     * @return the id of the Cast session controlled by the route.
     */
    public String getSessionId() {
        return mSessionId;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // RouteController implementation.

    @Override
    public void close() {
        stopApplication(INVALID_SEQUENCE_NUMBER);
    }

    @Override
    public void sendStringMessage(String message, int callbackId) {
        if (handleInternalMessage(message, callbackId)) return;

        // TODO(avayvod): figure out what to do with custom namespace messages.
        mRouteDelegate.onMessageSentResult(false, callbackId);
    }

    @Override
    public void sendBinaryMessage(byte[] data, int callbackId) {
        // TODO(crbug.com/524128): Implement this.
    }

    @Override
    public String getSourceId() {
        return mSource.getUrn();
    }

    @Override
    public String getRouteId() {
        return mMediaRouteId;
    }

    @Override
    public String getSinkId() {
        return mCastDevice.getDeviceId();
    }

    @Override
    public String getOrigin() {
        return mOrigin;
    }

    @Override
    public int getTabId() {
        return mTabId;
    }

    @Override
    public void markDetached() {
        mDetached = true;
    }

    @Override
    public boolean isDetached() {
        return mDetached;
    }


    /////////////////////////////////////////////////////////////////////////////////////////////
    // MediaNotificationListener implementation.

    @Override
    public void onPlay(int actionSource) {
        if (mMediaPlayer == null || isApiClientInvalid()) return;

        mMediaPlayer.play(mApiClient);
    }

    @Override
    public void onPause(int actionSource) {
        if (mMediaPlayer == null || isApiClientInvalid()) return;

        mMediaPlayer.pause(mApiClient);
    }

    @Override
    public void onStop(int actionSource) {
        stopApplication(INVALID_SEQUENCE_NUMBER);
    }


    /**
     * Forwards the media message to the page via the media router.
     * @param message The media that's being send by the receiver.
     * @param sequenceNumber The sequence number of the message this one is responding to.
     */
    public void onMediaMessage(String message, int sequenceNumber) {
        if (mMediaPlayer != null) {
            mMediaPlayer.onMessageReceived(mCastDevice, MEDIA_NAMESPACE, message);
        }

        sendMessageToClients("v2_message", message, sequenceNumber);
    }

    /**
     * Forwards the application specific message to the page via the media router.
     * @param message The message within the namespace that's being send by the receiver.
     * @param namespace The application specific namespace this message belongs to.
     * @param sequenceNumber The sequence number of the message this one is responding to.
     */
    public void onAppMessage(String message, String namespace, int sequenceNumber) {
        try {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("sessionId", mSessionId);
            jsonMessage.put("namespaceName", namespace);
            jsonMessage.put("message", message);
            sendMessageToClients("app_message", jsonMessage.toString(), sequenceNumber);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create the message wrapper", e);
        }
    }

    private void stopApplication(final int sequenceNumber) {
        if (mStoppingApplication) return;

        if (isApiClientInvalid()) return;

        mStoppingApplication = true;
        Cast.CastApi.stopApplication(mApiClient, mSessionId)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        sendMessageToClients("remove_session", mSessionId, sequenceNumber);

                        // TODO(avayvod): handle a failure to stop the application.
                        // https://crbug.com/535577

                        for (String namespace : mNamespaces) unregisterNamespace(namespace);
                        mNamespaces.clear();

                        mClients.clear();
                        mSessionId = null;
                        mApiClient = null;

                        mRouteDelegate.onRouteClosed(CastRouteController.this);
                        mStoppingApplication = false;

                        // The detached route will be closed only if another route joined
                        // the same session so it will take over the notification.
                        if (!mDetached) {
                            MediaNotificationManager.hide(
                                    mTabId, R.id.presentation_notification);
                        }
                    }
                });
    }

    private void addNamespace(String namespace) {
        assert !mNamespaces.contains(namespace);

        if (isApiClientInvalid()) return;

        // If application metadata is null, register the callback anyway.
        if (mApplicationMetadata != null && !mApplicationMetadata.isNamespaceSupported(namespace)) {
            return;
        }

        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, namespace, mMessageChannel);
            mNamespaces.add(namespace);
        } catch (IOException e) {
            Log.e(TAG, "Failed to register namespace listener for %s", namespace, e);
        }
    }

    private void unregisterNamespace(String namespace) {
        assert mNamespaces.contains(namespace);

        if (isApiClientInvalid()) return;

        try {
            Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, namespace);
        } catch (IOException e) {
            Log.e(TAG, "Failed to remove the namespace listener for %s", namespace, e);
        }
    }

    private boolean handleInternalMessage(String message, int callbackId) {
        Log.d(TAG, "Received message from client: %s", message);
        boolean success = true;
        try {
            JSONObject jsonMessage = new JSONObject(message);

            String messageType = jsonMessage.getString("type");
            if ("client_connect".equals(messageType)) {
                success = handleClientConnectMessage(jsonMessage);
            } else if ("v2_message".equals(messageType)) {
                success = handleCastV2Message(jsonMessage);
            } else if ("app_message".equals(messageType)) {
                success = handleAppMessage(jsonMessage);
            } else {
                Log.e(TAG, "Unsupported message: %s", message);
                return false;
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException while handling internal message: " + e);
            return false;
        }

        mRouteDelegate.onMessageSentResult(success, callbackId);
        return true;
    }

    private boolean handleClientConnectMessage(JSONObject jsonMessage)
            throws JSONException {
        String clientId = jsonMessage.getString("clientId");

        if (mClients.contains(clientId)) return false;

        mClients.add(clientId);

        mRouteDelegate.onMessage(mMediaRouteId, buildInternalMessage(
                "new_session", buildSessionMessage(), clientId, INVALID_SEQUENCE_NUMBER));
        return true;
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
    private boolean handleCastV2Message(JSONObject jsonMessage)
            throws JSONException {
        assert "v2_message".equals(jsonMessage.getString("type"));

        String clientId = jsonMessage.getString("clientId");
        if (!mClients.contains(clientId)) return false;

        JSONObject jsonCastMessage = jsonMessage.getJSONObject("message");
        String messageType = jsonCastMessage.getString("type");
        int sequenceNumber = jsonMessage.optInt("sequenceNumber", INVALID_SEQUENCE_NUMBER);

        if ("STOP".equals(messageType)) {
            stopApplication(sequenceNumber);
            return true;
        }

        if ("SET_VOLUME".equals(messageType)) {
            return handleVolumeMessage(jsonCastMessage.getJSONObject("volume"), sequenceNumber);
        }

        if (Arrays.asList(MEDIA_MESSAGE_TYPES).contains(messageType)) {
            if (sMediaOverloadedMessageTypes.containsKey(messageType)) {
                messageType = sMediaOverloadedMessageTypes.get(messageType);
                jsonCastMessage.put("type", messageType);
            }
            return sendCastMessage(jsonCastMessage, MEDIA_NAMESPACE, sequenceNumber);
        }

        return true;
    }

    // SET_VOLUME messages have a |level| and |muted| properties. One of them is
    // |null| and the other one isn't. |muted| is expected to be a boolean while
    // |level| is a float from 0.0 to 1.0.
    // Example:
    // {
    //   "volume" {
    //     "level": 0.9,
    //     "muted": null
    //   }
    // }
    private boolean handleVolumeMessage(JSONObject volume, final int sequenceNumber)
            throws JSONException {
        if (volume == null) return false;

        if (isApiClientInvalid()) return false;

        boolean waitForVolumeChange = false;
        try {
            if (!volume.isNull("muted")) {
                boolean newMuted = volume.getBoolean("muted");
                if (Cast.CastApi.isMute(mApiClient) != newMuted) {
                    Cast.CastApi.setMute(mApiClient, newMuted);
                    waitForVolumeChange = true;
                }
            }
            if (!volume.isNull("level")) {
                double newLevel = volume.getDouble("level");
                double currentLevel = Cast.CastApi.getVolume(mApiClient);
                if (!Double.isNaN(currentLevel)
                        && Math.abs(currentLevel - newLevel) > MIN_VOLUME_LEVEL_DELTA) {
                    Cast.CastApi.setVolume(mApiClient, newLevel);
                    waitForVolumeChange = true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to send volume command: " + e);
            return false;
        }

        // For each successful volume message we need to respond with an empty "v2_message" so the
        // Cast Web SDK can call the success callback of the page.
        // If we expect the volume to change as the result of the command, we're relying on
        // {@link Cast.CastListener#onVolumeChanged} to get called by the Android Cast SDK when the
        // receiver status is updated. We keep the sequence number until then.
        // If the volume doesn't change as the result of the command, we won't get notified by the
        // Android SDK when the status update is received so we respond to the volume message
        // immediately.
        if (waitForVolumeChange) {
            mVolumeRequestSequenceNumbers.add(sequenceNumber);
        } else {
            // It's usually bad to have request and response on the same call stack so post the
            // response to the Android message loop.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendMessageToClients("v2_message", null, sequenceNumber);
                }
            });
        }

        return true;
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
    private boolean handleAppMessage(JSONObject jsonMessage) throws JSONException {
        assert "app_message".equals(jsonMessage.getString("type"));

        String clientId = jsonMessage.getString("clientId");
        if (!mClients.contains(clientId)) return false;

        JSONObject jsonAppMessageWrapper = jsonMessage.getJSONObject("message");

        JSONObject actualMessage = jsonAppMessageWrapper.getJSONObject("message");
        if (actualMessage == null) return false;

        if (!mSessionId.equals(jsonAppMessageWrapper.getString("sessionId"))) return false;

        String namespaceName = jsonAppMessageWrapper.getString("namespaceName");
        if (namespaceName == null || namespaceName.isEmpty()) return false;

        if (!mNamespaces.contains(namespaceName)) addNamespace(namespaceName);

        int sequenceNumber = jsonMessage.optInt("sequenceNumber", INVALID_SEQUENCE_NUMBER);
        return sendCastMessage(actualMessage, namespaceName, sequenceNumber);
    }

    private boolean sendCastMessage(
            JSONObject message,
            final String namespace,
            final int sequenceNumber) throws JSONException {
        if (isApiClientInvalid()) return false;

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
            mRequests.append(requestId, sequenceNumber);
        }

        Log.d(TAG, "Sending message to Cast device in namespace %s: %s", namespace, message);

        try {
            Cast.CastApi.sendMessage(mApiClient, namespace, message.toString())
                    .setResultCallback(
                            new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    if (!result.isSuccess()) {
                                        // TODO(avayvod): should actually report back to the page.
                                        // See https://crbug.com/550445.
                                        Log.e(TAG, "Failed to send the message: " + result);
                                        return;
                                    }

                                    // Media commands wait for the media status update as a result.
                                    if (MEDIA_NAMESPACE.equals(namespace)) return;

                                    // App messages wait for the empty message with the sequence
                                    // number.
                                    sendMessageToClients("app_message", null, sequenceNumber);
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending message", e);
            return false;
        }
        return true;
    }

    /**
     * Modifies the received MediaStatus message to match the format expected by the client.
     */
    private void sanitizeMediaStatusMessage(JSONObject object) throws JSONException {
        object.put("sessionId", mSessionId);

        JSONArray mediaStatus = object.getJSONArray("status");
        for (int i = 0; i < mediaStatus.length(); ++i) {
            JSONObject status = mediaStatus.getJSONObject(i);
            status.put("sessionId", mSessionId);
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

    private String buildInternalMessage(
            String type, String message, String clientId, int sequenceNumber) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("sequenceNumber", sequenceNumber);
            json.put("timeoutMillis", 0);
            json.put("clientId", clientId);

            // TODO(mlamouri): we should have a more reliable way to handle string, null and Object
            // messages.
            if ("remove_session".equals(type) || message == null) {
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

        Log.d(TAG, "Sending message to client: " + json);

        return json.toString();
    }

    public void updateSessionStatus() {
        if (isApiClientInvalid()) return;

        try {
            mApplicationStatus = Cast.CastApi.getApplicationStatus(mApiClient);
            mApplicationMetadata = Cast.CastApi.getApplicationMetadata(mApiClient);

            sendMessageToClients("update_session", buildSessionMessage(), INVALID_SEQUENCE_NUMBER);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Can't get application status", e);
        }
    }

    public void onVolumeChanged() {
        updateSessionStatus();

        if (mVolumeRequestSequenceNumbers.isEmpty()) return;

        for (int sequenceNumber : mVolumeRequestSequenceNumbers) {
            sendMessageToClients("v2_message", null, sequenceNumber);
        }
        mVolumeRequestSequenceNumbers.clear();
    }

    private String buildSessionMessage() {
        if (isApiClientInvalid()) return "{}";

        try {
            // "volume" is a part of "receiver" initialized below.
            JSONObject jsonVolume = new JSONObject();
            jsonVolume.put("level", Cast.CastApi.getVolume(mApiClient));
            jsonVolume.put("muted", Cast.CastApi.isMute(mApiClient));

            // "receiver" is a part of "message" initialized below.
            JSONObject jsonReceiver = new JSONObject();
            jsonReceiver.put("label", mCastDevice.getDeviceId());
            jsonReceiver.put("friendlyName", mCastDevice.getFriendlyName());
            jsonReceiver.put("capabilities", getCapabilities(mCastDevice));
            jsonReceiver.put("volume", jsonVolume);
            jsonReceiver.put("isActiveInput", Cast.CastApi.getActiveInputState(mApiClient));
            jsonReceiver.put("displayStatus", null);
            jsonReceiver.put("receiverType", "cast");

            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("sessionId", mSessionId);
            jsonMessage.put("appId", mApplicationMetadata.getApplicationId());
            jsonMessage.put("displayName", mApplicationMetadata.getName());
            jsonMessage.put("statusText", mApplicationStatus);
            jsonMessage.put("receiver", jsonReceiver);
            jsonMessage.put("namespaces", extractNamespaces(mApplicationMetadata));
            jsonMessage.put("media", new JSONArray());
            jsonMessage.put("status", "connected");
            jsonMessage.put("transportId", "web-4");

            return jsonMessage.toString();
        } catch (JSONException e) {
            Log.w(TAG, "Building session message failed", e);
            return "{}";
        }
    }

    private void sendMessageToClients(String type, String message, int sequenceNumber) {
        for (String client : mClients) {
            mRouteDelegate.onMessage(mMediaRouteId,
                    buildInternalMessage(type, message, client, sequenceNumber));
        }
    }

    private JSONArray getCapabilities(CastDevice device) {
        JSONArray jsonCapabilities = new JSONArray();
        if (device.hasCapability(CastDevice.CAPABILITY_AUDIO_IN)) {
            jsonCapabilities.put("audio_in");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)) {
            jsonCapabilities.put("audio_out");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_VIDEO_IN)) {
            jsonCapabilities.put("video_in");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)) {
            jsonCapabilities.put("video_out");
        }
        return jsonCapabilities;
    }

    private JSONArray extractNamespaces(ApplicationMetadata metadata) throws JSONException {
        JSONArray jsonNamespaces = new JSONArray();
        // TODO(avayvod): Need a way to retrieve all the supported namespaces (e.g. YouTube).
        // See crbug.com/529680.
        for (String namespace : mNamespaces) {
            JSONObject jsonNamespace = new JSONObject();
            jsonNamespace.put("name", namespace);
            jsonNamespaces.put(jsonNamespace);
        }
        return jsonNamespaces;
    }

    private boolean isApiClientInvalid() {
        return mApiClient == null || (!mApiClient.isConnected() && !mApiClient.isConnecting());
    }
}
