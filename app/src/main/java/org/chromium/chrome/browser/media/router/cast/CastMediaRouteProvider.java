// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.content.Context;
import android.os.Handler;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.media.router.ChromeMediaRouter;
import org.chromium.chrome.browser.media.router.DiscoveryDelegate;
import org.chromium.chrome.browser.media.router.MediaRoute;
import org.chromium.chrome.browser.media.router.MediaRouteManager;
import org.chromium.chrome.browser.media.router.MediaRouteProvider;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A {@link MediaRouteProvider} implementation for Cast devices and applications.
 */
public class CastMediaRouteProvider implements MediaRouteProvider, DiscoveryDelegate {

    private static final String TAG = "MediaRouter";

    private static final String AUTO_JOIN_PRESENTATION_ID = "auto-join";
    private static final String PRESENTATION_ID_SESSION_ID_PREFIX = "cast-session_";

    private final Context mApplicationContext;
    private final MediaRouter mAndroidMediaRouter;
    private final MediaRouteManager mManager;
    private final CastMessageHandler mMessageHandler;
    private final Map<String, DiscoveryCallback> mDiscoveryCallbacks =
            new HashMap<String, DiscoveryCallback>();
    private final Map<String, MediaRoute> mRoutes = new HashMap<String, MediaRoute>();
    private ClientRecord mLastRemovedRouteRecord;
    private final Map<String, ClientRecord> mClientRecords = new HashMap<String, ClientRecord>();

    // There can be only one Cast session at the same time on Android.
    private CastSession mSession;
    private CreateRouteRequest mPendingCreateRouteRequest;
    private Handler mHandler = new Handler();

    /**
     * Builder class for {@link CastMediaRouteProvider}.
     */
    public static class Builder implements MediaRouteProvider.Builder {
        @Override
        public MediaRouteProvider create(Context applicationContext, MediaRouteManager manager) {
            return CastMediaRouteProvider.create(applicationContext, manager);
        }
    }

    private static class OnSinksReceivedRunnable implements Runnable {

        private final WeakReference<MediaRouteManager> mRouteManager;
        private final MediaRouteProvider mRouteProvider;
        private final String mSourceId;
        private final List<MediaSink> mSinks;

        OnSinksReceivedRunnable(MediaRouteManager manager, MediaRouteProvider routeProvider,
                String sourceId, List<MediaSink> sinks) {
            mRouteManager = new WeakReference<MediaRouteManager>(manager);
            mRouteProvider = routeProvider;
            mSourceId = sourceId;
            mSinks = sinks;
        }

        @Override
        public void run() {
            MediaRouteManager manager = mRouteManager.get();
            if (manager != null) manager.onSinksReceived(mSourceId, mRouteProvider, mSinks);
        }
    }

    /**
     * @param applicationContext The application context to use for this route provider.
     * @return Initialized {@link CastMediaRouteProvider} object or null if it's not supported.
     */
    @Nullable
    public static CastMediaRouteProvider create(
            Context applicationContext, MediaRouteManager manager) {
        assert applicationContext != null;
        MediaRouter androidMediaRouter =
                ChromeMediaRouter.getAndroidMediaRouter(applicationContext);
        if (androidMediaRouter == null) return null;

        return new CastMediaRouteProvider(applicationContext, androidMediaRouter, manager);
    }

    public void onLaunchError() {
        for (String routeId : mRoutes.keySet()) {
            mManager.onRouteClosedWithError(routeId, "Launch error");
        }
        mRoutes.clear();
        mClientRecords.clear();
    }

    public void onSessionStopAction() {
        if (mSession == null) return;

        for (String routeId : mRoutes.keySet()) closeRoute(routeId);
    }

    public void onSessionCreated(CastSession session) {
        mSession = session;
        mMessageHandler.onSessionCreated(mSession);
    }

    public void onSessionClosed() {
        if (mSession == null) return;

        if (mClientRecords.isEmpty()) {
            mRoutes.clear();
        } else {
            mLastRemovedRouteRecord = mClientRecords.values().iterator().next();
            for (ClientRecord client : mClientRecords.values()) {
                mManager.onRouteClosed(client.routeId);

                mRoutes.remove(client.routeId);
            }
            mClientRecords.clear();
        }

        mSession = null;

        if (mPendingCreateRouteRequest != null) {
            launchSession(mPendingCreateRouteRequest);
            mPendingCreateRouteRequest = null;
        } else if (mAndroidMediaRouter != null) {
            mAndroidMediaRouter.selectRoute(mAndroidMediaRouter.getDefaultRoute());
        }
    }

    public void onMessageSentResult(boolean success, int callbackId) {
        mManager.onMessageSentResult(success, callbackId);
    }

    public void onMessage(String clientId, String message) {
        ClientRecord clientRecord = mClientRecords.get(clientId);
        if (clientRecord == null) return;

        if (!clientRecord.isConnected) {
            Log.d(TAG, "Queueing message to client %s: %s", clientId, message);
            clientRecord.pendingMessages.add(message);
            return;
        }

        Log.d(TAG, "Sending message to client %s: %s", clientId, message);
        mManager.onMessage(clientRecord.routeId, message);
    }

    public CastMessageHandler getMessageHandler() {
        return mMessageHandler;
    }

    public Set<String> getClients() {
        return mClientRecords.keySet();
    }

    public Map<String, ClientRecord> getClientRecords() {
        return mClientRecords;
    }

    @Override
    public void onSinksReceived(String sourceId, List<MediaSink> sinks) {
        mHandler.post(new OnSinksReceivedRunnable(mManager, this, sourceId, sinks));
    }

    @Override
    public boolean supportsSource(String sourceId) {
        return MediaSource.from(sourceId) != null;
    }

    @Override
    public void startObservingMediaSinks(String sourceId) {
        if (mAndroidMediaRouter == null) return;

        MediaSource source = MediaSource.from(sourceId);
        if (source == null) return;

        MediaRouteSelector routeSelector = source.buildRouteSelector();
        if (routeSelector == null) {
            // If the application invalid, report no devices available.
            onSinksReceived(sourceId, new ArrayList<MediaSink>());
            return;
        }

        String applicationId = source.getApplicationId();
        DiscoveryCallback callback = mDiscoveryCallbacks.get(applicationId);
        if (callback != null) {
            callback.addSourceUrn(sourceId);
            return;
        }

        List<MediaSink> knownSinks = new ArrayList<MediaSink>();
        for (RouteInfo route : mAndroidMediaRouter.getRoutes()) {
            if (route.matchesSelector(routeSelector)) {
                knownSinks.add(MediaSink.fromRoute(route));
            }
        }

        callback = new DiscoveryCallback(sourceId, knownSinks, this, routeSelector);
        mAndroidMediaRouter.addCallback(
                routeSelector,
                callback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        mDiscoveryCallbacks.put(applicationId, callback);
    }

    @Override
    public void stopObservingMediaSinks(String sourceId) {
        if (mAndroidMediaRouter == null) return;

        MediaSource source = MediaSource.from(sourceId);
        if (source == null) return;

        String applicationId = source.getApplicationId();
        DiscoveryCallback callback = mDiscoveryCallbacks.get(applicationId);
        if (callback == null) return;

        callback.removeSourceUrn(sourceId);

        if (callback.isEmpty()) {
            mAndroidMediaRouter.removeCallback(callback);
            mDiscoveryCallbacks.remove(applicationId);
        }
    }

    @Override
    public void createRoute(String sourceId, String sinkId, String presentationId, String origin,
            int tabId, boolean isIncognito, int nativeRequestId) {
        if (mAndroidMediaRouter == null) {
            mManager.onRouteRequestError("Not supported", nativeRequestId);
            return;
        }

        MediaSink sink = MediaSink.fromSinkId(sinkId, mAndroidMediaRouter);
        if (sink == null) {
            mManager.onRouteRequestError("No sink", nativeRequestId);
            return;
        }

        MediaSource source = MediaSource.from(sourceId);
        if (source == null) {
            mManager.onRouteRequestError("Unsupported presentation URL", nativeRequestId);
            return;
        }

        CreateRouteRequest createRouteRequest = new CreateRouteRequest(
                source, sink, presentationId, origin, tabId, isIncognito, nativeRequestId, this);

        // Since we only have one session, close it before starting a new one.
        if (mSession != null) {
            mPendingCreateRouteRequest = createRouteRequest;
            mSession.stopApplication();
            return;
        }

        launchSession(createRouteRequest);
    }

    private void launchSession(CreateRouteRequest request) {
        MediaSink sink = request.getSink();
        MediaSource source = request.getSource();

        MediaRoute route = new MediaRoute(
                sink.getId(), source.getUrn(), request.getPresentationId());
        addRoute(route, request.getOrigin(), request.getTabId());
        mManager.onRouteCreated(route.id, route.sinkId, request.getNativeRequestId(), this, true);

        if (source.getClientId() != null) {
            ClientRecord clientRecord = mClientRecords.get(source.getClientId());
            if (clientRecord != null) {
                sendReceiverAction(clientRecord.routeId, sink, source.getClientId(), "cast");
            }
        }
        request.start(mApplicationContext);
    }

    @Override
    public void joinRoute(String sourceId, String presentationId, String origin, int tabId,
            int nativeRequestId) {
        MediaSource source = MediaSource.from(sourceId);
        if (source == null || source.getClientId() == null) {
            mManager.onRouteRequestError("Unsupported presentation URL", nativeRequestId);
            return;
        }

        if (mSession == null) {
            mManager.onRouteRequestError("No presentation", nativeRequestId);
            return;
        }

        if (!canJoinExistingSession(presentationId, origin, tabId, source)) {
            mManager.onRouteRequestError("No matching route", nativeRequestId);
            return;
        }

        MediaRoute route = new MediaRoute(mSession.getSinkId(), sourceId, presentationId);
        addRoute(route, origin, tabId);
        mManager.onRouteCreated(route.id, route.sinkId, nativeRequestId, this, false);
    }

    @Override
    public void closeRoute(String routeId) {
        MediaRoute route = mRoutes.get(routeId);

        if (route == null) return;

        if (mSession == null) {
            mRoutes.remove(routeId);
            return;
        }

        ClientRecord client = getClientRecordByRouteId(routeId);
        if (client != null) {
            MediaSink sink = MediaSink.fromSinkId(mSession.getSinkId(), mAndroidMediaRouter);
            if (sink != null) sendReceiverAction(routeId, sink, client.clientId, "stop");
        }

        mSession.stopApplication();
    }

    @Override
    public void detachRoute(String routeId) {
        mRoutes.remove(routeId);

        removeClient(getClientRecordByRouteId(routeId));
    }

    @Override
    public void sendStringMessage(String routeId, String message, int nativeCallbackId) {
        Log.d(TAG, "Received message from client: %s", message);

        if (!mRoutes.containsKey(routeId)) {
            mManager.onMessageSentResult(false, nativeCallbackId);
            return;
        }

        boolean success = false;
        try {
            JSONObject jsonMessage = new JSONObject(message);

            String messageType = jsonMessage.getString("type");
            // TODO(zqzhang): Move the handling of "client_connect", "client_disconnect" and
            // "leave_session" from CastMRP to CastMessageHandler. Also, need to have a
            // ClientManager for client managing.
            if ("client_connect".equals(messageType)) {
                success = handleClientConnectMessage(jsonMessage);
            } else if ("client_disconnect".equals(messageType)) {
                success = handleClientDisconnectMessage(jsonMessage);
            } else if ("leave_session".equals(messageType)) {
                success = handleLeaveSessionMessage(jsonMessage);
            } else if (mSession != null) {
                success = mMessageHandler.handleSessionMessage(jsonMessage);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException while handling internal message: " + e);
            success = false;
        }

        mManager.onMessageSentResult(success, nativeCallbackId);
    }

    @Override
    public void sendBinaryMessage(String routeId, byte[] data, int nativeCallbackId) {
        // TODO(crbug.com/524128): Cast API does not support sending binary message
        // to receiver application. Binary data may be converted to String and send as
        // an app_message within it's own message namespace, using the string version.
        // Sending failure in the result callback for now.
        mManager.onMessageSentResult(false, nativeCallbackId);
    }

    private boolean handleClientConnectMessage(JSONObject jsonMessage) throws JSONException {
        String clientId = jsonMessage.getString("clientId");
        if (clientId == null) return false;

        ClientRecord clientRecord = mClientRecords.get(clientId);
        if (clientRecord == null) return false;

        clientRecord.isConnected = true;
        if (mSession != null) mSession.onClientConnected(clientId);

        if (clientRecord.pendingMessages.size() == 0) return true;
        for (String message : clientRecord.pendingMessages) {
            Log.d(TAG, "Deqeueing message for client %s: %s", clientId, message);
            mManager.onMessage(clientRecord.routeId, message);
        }
        clientRecord.pendingMessages.clear();

        return true;
    }

    private boolean handleClientDisconnectMessage(JSONObject jsonMessage) throws JSONException {
        String clientId = jsonMessage.getString("clientId");
        if (clientId == null) return false;

        ClientRecord client = mClientRecords.get(clientId);
        if (client == null) return false;

        mRoutes.remove(client.routeId);
        removeClient(client);

        mManager.onRouteClosed(client.routeId);

        return true;
    }

    private boolean handleLeaveSessionMessage(JSONObject jsonMessage) throws JSONException {
        String clientId = jsonMessage.getString("clientId");
        if (clientId == null || mSession == null) return false;

        String sessionId = jsonMessage.getString("message");
        if (!mSession.getSessionId().equals(sessionId)) return false;

        ClientRecord leavingClient = mClientRecords.get(clientId);
        if (leavingClient == null) return false;

        int sequenceNumber = jsonMessage.optInt("sequenceNumber", -1);
        onMessage(clientId, buildInternalMessage("leave_session", sequenceNumber, clientId, null));

        // Send a "disconnect_session" message to all the clients that match with the leaving
        // client's auto join policy.
        for (ClientRecord client : mClientRecords.values()) {
            if ((MediaSource.AUTOJOIN_TAB_AND_ORIGIN_SCOPED.equals(leavingClient.autoJoinPolicy)
                            && client.origin.equals(leavingClient.origin)
                            && client.tabId == leavingClient.tabId)
                    || (MediaSource.AUTOJOIN_ORIGIN_SCOPED.equals(leavingClient.autoJoinPolicy)
                            && client.origin.equals(leavingClient.origin))) {
                onMessage(client.clientId,
                        buildInternalMessage("disconnect_session", -1, client.clientId, sessionId));
            }
        }

        return true;
    }

    private String buildInternalMessage(
            String type, int sequenceNumber, String clientId, String message) throws JSONException {
        JSONObject jsonMessage = new JSONObject();
        jsonMessage.put("type", type);
        jsonMessage.put("sequenceNumber", sequenceNumber);
        jsonMessage.put("timeoutMillis", 0);
        jsonMessage.put("clientId", clientId);
        jsonMessage.put("message", message);
        return jsonMessage.toString();
    }

    @VisibleForTesting
    static CastMediaRouteProvider createCastMediaRouteProviderForTest(
            Context applicationContext, MediaRouter androidMediaRouter, MediaRouteManager manager) {
        return new CastMediaRouteProvider(applicationContext, androidMediaRouter, manager);
    }

    @VisibleForTesting
    CastMediaRouteProvider(
            Context applicationContext, MediaRouter androidMediaRouter, MediaRouteManager manager) {
        mApplicationContext = applicationContext;
        mAndroidMediaRouter = androidMediaRouter;
        mManager = manager;
        mMessageHandler = new CastMessageHandler(this);
    }

    @Nullable
    private boolean canAutoJoin(MediaSource source, String origin, int tabId) {
        if (source.getAutoJoinPolicy().equals(MediaSource.AUTOJOIN_PAGE_SCOPED)) return false;

        MediaSource currentSource = MediaSource.from(mSession.getSourceId());
        if (!currentSource.getApplicationId().equals(source.getApplicationId())) return false;

        ClientRecord client = null;
        if (!mClientRecords.isEmpty()) {
            client = mClientRecords.values().iterator().next();
        } else if (mLastRemovedRouteRecord != null) {
            client = mLastRemovedRouteRecord;
            return origin.equals(client.origin) && tabId == client.tabId;
        }

        if (client == null) return false;

        if (source.getAutoJoinPolicy().equals(MediaSource.AUTOJOIN_ORIGIN_SCOPED)) {
            return origin.equals(client.origin);
        } else if (source.getAutoJoinPolicy().equals(MediaSource.AUTOJOIN_TAB_AND_ORIGIN_SCOPED)) {
            return origin.equals(client.origin) && tabId == client.tabId;
        }

        return false;
    }

    private boolean canJoinExistingSession(String presentationId, String origin, int tabId,
            MediaSource source) {
        if (AUTO_JOIN_PRESENTATION_ID.equals(presentationId)) {
            return canAutoJoin(source, origin, tabId);
        } else if (presentationId.startsWith(PRESENTATION_ID_SESSION_ID_PREFIX)) {
            String sessionId = presentationId.substring(PRESENTATION_ID_SESSION_ID_PREFIX.length());

            if (mSession.getSessionId().equals(sessionId)) return true;
        } else {
            for (MediaRoute route : mRoutes.values()) {
                if (route.presentationId.equals(presentationId)) return true;
            }
        }
        return false;
    }

    @Nullable
    private ClientRecord getClientRecordByRouteId(String routeId) {
        for (ClientRecord record : mClientRecords.values()) {
            if (record.routeId.equals(routeId)) return record;
        }
        return null;
    }

    private void addRoute(MediaRoute route, String origin, int tabId) {
        mRoutes.put(route.id, route);

        MediaSource source = MediaSource.from(route.sourceId);
        final String clientId = source.getClientId();

        if (clientId == null || mClientRecords.get(clientId) != null) return;

        mClientRecords.put(clientId,
                new ClientRecord(
                        route.id,
                        clientId,
                        source.getApplicationId(),
                        source.getAutoJoinPolicy(),
                        origin,
                        tabId));
    }

    // TODO(zqzhang): Move this method to CastMessageHandler.
    private void sendReceiverAction(
            String routeId, MediaSink sink, String clientId, String action) {
        try {
            JSONObject jsonReceiver = new JSONObject();
            jsonReceiver.put("label", sink.getId());
            jsonReceiver.put("friendlyName", sink.getName());
            jsonReceiver.put("capabilities", CastSessionImpl.getCapabilities(sink.getDevice()));
            jsonReceiver.put("volume", null);
            jsonReceiver.put("isActiveInput", null);
            jsonReceiver.put("displayStatus", null);
            jsonReceiver.put("receiverType", "cast");

            JSONObject jsonReceiverAction = new JSONObject();
            jsonReceiverAction.put("receiver", jsonReceiver);
            jsonReceiverAction.put("action", action);

            JSONObject json = new JSONObject();
            json.put("type", "receiver_action");
            json.put("sequenceNumber", -1);
            json.put("timeoutMillis", 0);
            json.put("clientId", clientId);
            json.put("message", jsonReceiverAction);

            onMessage(clientId, json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send receiver action message", e);
        }
    }

    private void removeClient(@Nullable ClientRecord client) {
        if (client == null) return;

        mLastRemovedRouteRecord = client;
        mClientRecords.remove(client.clientId);
    }
}
