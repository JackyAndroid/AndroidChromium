// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.content.Context;
import android.os.Handler;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;

import org.chromium.chrome.browser.media.router.ChromeMediaRouter;
import org.chromium.chrome.browser.media.router.DiscoveryDelegate;
import org.chromium.chrome.browser.media.router.MediaRouteManager;
import org.chromium.chrome.browser.media.router.MediaRouteProvider;
import org.chromium.chrome.browser.media.router.RouteController;
import org.chromium.chrome.browser.media.router.RouteDelegate;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A {@link MediaRouteProvider} implementation for Cast devices and applications.
 */
public class CastMediaRouteProvider
        implements MediaRouteProvider, DiscoveryDelegate, RouteDelegate {

    private static final String TAG = "cr_MediaRouter";

    private static final String AUTO_JOIN_PRESENTATION_ID = "auto-join";
    private static final String PRESENTATION_ID_SESSION_ID_PREFIX = "cast-session_";

    private final Context mApplicationContext;
    private final MediaRouter mAndroidMediaRouter;
    private final MediaRouteManager mManager;
    private final Map<String, DiscoveryCallback> mDiscoveryCallbacks =
            new HashMap<String, DiscoveryCallback>();
    private final Map<String, CastRouteController> mRoutes =
            new HashMap<String, CastRouteController>();
    private final Map<String, String> mClientIdsToRouteIds = new HashMap<String, String>();

    private CreateRouteRequest mPendingCreateRouteRequest;
    private Handler mHandler = new Handler();

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
    };

    @Override
    public void onSinksReceived(String sourceId, List<MediaSink> sinks) {
        mHandler.post(new OnSinksReceivedRunnable(mManager, this, sourceId, sinks));
    }

    @Override
    public void onRouteCreated(int requestId, RouteController route, boolean wasLaunched) {
        assert route instanceof CastRouteController;

        String routeId = route.getRouteId();

        mRoutes.put(routeId, (CastRouteController) route);
        mClientIdsToRouteIds.put(MediaSource.from(route.getSourceId()).getClientId(), routeId);

        mManager.onRouteCreated(routeId, route.getSinkId(), requestId, this, wasLaunched);
    }

    @Override
    public void onRouteRequestError(String message, int requestId) {
        mManager.onRouteRequestError(message, requestId);
    }

    @Override
    public void onRouteClosed(RouteController route) {
        mClientIdsToRouteIds.remove(MediaSource.from(route.getSourceId()).getClientId());

        if (mPendingCreateRouteRequest != null) {
            mPendingCreateRouteRequest.start(mApplicationContext);
            mPendingCreateRouteRequest = null;
        } else if (mAndroidMediaRouter != null) {
            mAndroidMediaRouter.selectRoute(mAndroidMediaRouter.getDefaultRoute());
        }
        mManager.onRouteClosed(route.getRouteId());
    }

    @Override
    public void onMessageSentResult(boolean success, int callbackId) {
        mManager.onMessageSentResult(success, callbackId);
    }

    @Override
    public void onMessage(String routeId, String message) {
        mManager.onMessage(routeId, message);
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

    @Override
    public boolean supportsSource(String sourceId) {
        return MediaSource.from(sourceId) != null;
    }

    @Override
    public void startObservingMediaSinks(String sourceId) {
        if (mAndroidMediaRouter == null) return;

        MediaSource source = MediaSource.from(sourceId);
        if (source == null) return;

        // If the source is a Cast source but invalid, report no sinks available.
        MediaRouteSelector routeSelector;
        try {
            routeSelector = source.buildRouteSelector();
        } catch (IllegalArgumentException e) {
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

        callback = new DiscoveryCallback(sourceId, knownSinks, this);
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
    public void createRoute(String sourceId, String sinkId, String routeId, String origin,
            int tabId, int nativeRequestId) {
        if (mAndroidMediaRouter == null) {
            mManager.onRouteRequestError("Not supported", nativeRequestId);
            return;
        }

        MediaSource source = MediaSource.from(sourceId);
        if (source == null || source.getClientId() == null) {
            mManager.onRouteRequestError("Unsupported presentation URL", nativeRequestId);
            return;
        }

        MediaSink sink = MediaSink.fromSinkId(sinkId, mAndroidMediaRouter);
        if (sink == null) {
            mManager.onRouteRequestError("No sink", nativeRequestId);
            return;
        }

        CreateRouteRequest createRouteRequest = new CreateRouteRequest(
                source, sink, routeId, origin, tabId, nativeRequestId, this);
        String existingRouteId = mClientIdsToRouteIds.get(source.getClientId());
        if (existingRouteId == null) {
            createRouteRequest.start(mApplicationContext);
            return;
        }

        mPendingCreateRouteRequest = createRouteRequest;
        closeRoute(existingRouteId);
    }

    @Override
    public void joinRoute(String sourceId, String presentationId, String origin, int tabId,
            int nativeRequestId) {
        MediaSource source = MediaSource.from(sourceId);
        if (source == null || source.getClientId() == null) {
            mManager.onRouteRequestError("Unsupported presentation URL", nativeRequestId);
            return;
        }

        CastRouteController routeToJoin = null;
        if (AUTO_JOIN_PRESENTATION_ID.equals(presentationId)) {
            routeToJoin = autoJoinRoute(source, origin, tabId);
        } else if (presentationId.startsWith(PRESENTATION_ID_SESSION_ID_PREFIX)) {
            String sessionId = presentationId.substring(PRESENTATION_ID_SESSION_ID_PREFIX.length());
            for (CastRouteController route : mRoutes.values()) {
                if (sessionId.equals(route.getSessionId())) {
                    routeToJoin = route;
                    break;
                }
            }
        } else {
            for (CastRouteController route : mRoutes.values()) {
                String[] routeIdComponents = ChromeMediaRouter
                        .parseMediaRouteId(route.getRouteId());
                assert routeIdComponents != null;

                if (presentationId.equals(routeIdComponents[0])) {
                    routeToJoin = route;
                    break;
                }
            }
        }

        if (routeToJoin == null) {
            mManager.onRouteRequestError("No matching route", nativeRequestId);
            return;
        }

        String mediaRouteId = ChromeMediaRouter.createMediaRouteId(
                presentationId, routeToJoin.getSinkId(), sourceId);
        CastRouteController joinedController = routeToJoin.createJoinedController(mediaRouteId,
                origin, tabId, MediaSource.from(sourceId));
        mRoutes.put(mediaRouteId, joinedController);

        this.onRouteCreated(nativeRequestId, joinedController, false);

        if (routeToJoin.isDetached()) mManager.onRouteClosed(routeToJoin.getRouteId());
    }

    @Override
    public void closeRoute(String routeId) {
        RouteController route = mRoutes.remove(routeId);
        if (route == null) return;

        route.close();
    }

    @Override
    public void detachRoute(String routeId) {
        RouteController route = mRoutes.get(routeId);
        if (route == null) return;

        route.markDetached();
    }

    @Override
    public void sendStringMessage(String routeId, String message, int nativeCallbackId) {
        RouteController route = mRoutes.get(routeId);
        if (route == null) {
            mManager.onMessageSentResult(false, nativeCallbackId);
            return;
        }

        route.sendStringMessage(message, nativeCallbackId);
    }

    @Override
    public void sendBinaryMessage(String routeId, byte[] data, int nativeCallbackId) {
        // TODO(crbug.com/524128): Cast API does not support sending binary message
        // to receiver application. Binary data may be converted to String and send as
        // an app_message within it's own message namespace, using the string version.
        // Sending failure in the result callback for now.
        mManager.onMessageSentResult(false, nativeCallbackId);
    }

    private CastMediaRouteProvider(
            Context applicationContext, MediaRouter androidMediaRouter, MediaRouteManager manager) {
        mApplicationContext = applicationContext;
        mAndroidMediaRouter = androidMediaRouter;
        mManager = manager;
    }
    @Nullable
    private CastRouteController autoJoinRoute(MediaSource source, String origin, int tabId) {
        CastRouteController matchingRoute = null;
        for (CastRouteController route : mRoutes.values()) {
            MediaSource routeSource = MediaSource.from(route.getSourceId());
            if (routeSource.getApplicationId().equals(source.getApplicationId())) {
                matchingRoute = route;
                break;
            }
        }

        if (matchingRoute == null) return null;

        String autoJoinPolicy = source.getAutoJoinPolicy();

        if (MediaSource.AUTOJOIN_ORIGIN_SCOPED.equals(autoJoinPolicy)) {
            if (!matchingRoute.getOrigin().equals(origin)) return null;
        } else if (MediaSource.AUTOJOIN_TAB_AND_ORIGIN_SCOPED.equals(autoJoinPolicy)) {
            if (!matchingRoute.getOrigin().equals(origin)
                    || matchingRoute.getTabId() != tabId) {
                return null;
            }
        }

        return matchingRoute;
    }
}
