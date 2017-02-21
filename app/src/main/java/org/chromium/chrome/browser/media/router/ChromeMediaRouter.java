// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.content.Context;
import android.support.v7.media.MediaRouter;

import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.media.router.cast.CastMediaRouteProvider;
import org.chromium.chrome.browser.media.router.cast.MediaSink;
import org.chromium.chrome.browser.media.router.cast.MediaSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Implements the JNI interface called from the C++ Media Router implementation on Android.
 * Owns a list of {@link MediaRouteProvider} implementations and dispatches native calls to them.
 */
@JNINamespace("media_router")
public class ChromeMediaRouter implements MediaRouteManager {

    private static final String TAG = "MediaRouter";

    private static MediaRouteProvider.Builder sRouteProviderBuilder =
            new CastMediaRouteProvider.Builder();

    // The pointer to the native object. Can be null only during tests.
    private final long mNativeMediaRouterAndroid;
    private final List<MediaRouteProvider> mRouteProviders = new ArrayList<MediaRouteProvider>();
    private final Map<String, MediaRouteProvider> mRouteIdsToProviders =
            new HashMap<String, MediaRouteProvider>();
    private final Map<String, Map<MediaRouteProvider, List<MediaSink>>> mSinksPerSourcePerProvider =
            new HashMap<String, Map<MediaRouteProvider, List<MediaSink>>>();
    private final Map<String, List<MediaSink>> mSinksPerSource =
            new HashMap<String, List<MediaSink>>();

    @VisibleForTesting
    public static void setRouteProviderBuilderForTest(MediaRouteProvider.Builder builder) {
        sRouteProviderBuilder = builder;
    }

    @VisibleForTesting
    protected List<MediaRouteProvider> getRouteProvidersForTest() {
        return mRouteProviders;
    }

    @VisibleForTesting
    protected Map<String, MediaRouteProvider> getRouteIdsToProvidersForTest() {
        return mRouteIdsToProviders;
    }

    @VisibleForTesting
    protected Map<String, Map<MediaRouteProvider, List<MediaSink>>>
            getSinksPerSourcePerProviderForTest() {
        return mSinksPerSourcePerProvider;
    }

    @VisibleForTesting
    protected Map<String, List<MediaSink>> getSinksPerSourceForTest() {
        return mSinksPerSource;
    }

    /**
     * Obtains the {@link MediaRouter} instance given the application context.
     * @param applicationContext The context to get the Android media router service for.
     * @return Null if the media router API is not supported, the service instance otherwise.
     */
    @Nullable
    public static MediaRouter getAndroidMediaRouter(Context applicationContext) {
        try {
            // Pre-MR1 versions of JB do not have the complete MediaRouter APIs,
            // so getting the MediaRouter instance will throw an exception.
            return MediaRouter.getInstance(applicationContext);
        } catch (NoSuchMethodError e) {
            return null;
        } catch (NoClassDefFoundError e) {
            // TODO(mlamouri): happens with Robolectric.
            return null;
        }
    }

    @Override
    public void onSinksReceived(
            String sourceId, MediaRouteProvider provider, List<MediaSink> sinks) {
        if (!mSinksPerSourcePerProvider.containsKey(sourceId)) {
            mSinksPerSourcePerProvider.put(
                    sourceId, new HashMap<MediaRouteProvider, List<MediaSink>>());
        }

        // Replace the sinks found by this provider with the new list.
        Map<MediaRouteProvider, List<MediaSink>> sinksPerProvider =
                mSinksPerSourcePerProvider.get(sourceId);
        sinksPerProvider.put(provider, sinks);

        List<MediaSink> allSinksPerSource = new ArrayList<MediaSink>();
        for (List<MediaSink> s : sinksPerProvider.values()) allSinksPerSource.addAll(s);

        mSinksPerSource.put(sourceId, allSinksPerSource);
        if (mNativeMediaRouterAndroid != 0) {
            nativeOnSinksReceived(mNativeMediaRouterAndroid, sourceId, allSinksPerSource.size());
        }
    }

    @Override
    public void onRouteCreated(
            String mediaRouteId, String mediaSinkId, int requestId, MediaRouteProvider provider,
            boolean wasLaunched) {
        mRouteIdsToProviders.put(mediaRouteId, provider);
        if (mNativeMediaRouterAndroid != 0) {
            nativeOnRouteCreated(mNativeMediaRouterAndroid, mediaRouteId, mediaSinkId, requestId,
                    wasLaunched);
        }
    }

    @Override
    public void onRouteRequestError(String errorText, int requestId) {
        if (mNativeMediaRouterAndroid != 0) {
            nativeOnRouteRequestError(mNativeMediaRouterAndroid, errorText, requestId);
        }
    }

    @Override
    public void onRouteClosed(String mediaRouteId) {
        if (mNativeMediaRouterAndroid != 0) {
            nativeOnRouteClosed(mNativeMediaRouterAndroid, mediaRouteId);
        }
        mRouteIdsToProviders.remove(mediaRouteId);
    }

    @Override
    public void onRouteClosedWithError(String mediaRouteId, String message) {
        if (mNativeMediaRouterAndroid != 0) {
            nativeOnRouteClosedWithError(mNativeMediaRouterAndroid, mediaRouteId, message);
        }
        mRouteIdsToProviders.remove(mediaRouteId);
    }

    @Override
    public void onMessageSentResult(boolean success, int callbackId) {
        nativeOnMessageSentResult(mNativeMediaRouterAndroid, success, callbackId);
    }

    @Override
    public void onMessage(String mediaRouteId, String message) {
        nativeOnMessage(mNativeMediaRouterAndroid, mediaRouteId, message);
    }

    /**
     * Initializes the media router and its providers.
     * @param nativeMediaRouterAndroid the handler for the native counterpart of this instance
     * @param applicationContext the application context to use to obtain system APIs
     * @return an initialized {@link ChromeMediaRouter} instance
     */
    @CalledByNative
    public static ChromeMediaRouter create(long nativeMediaRouterAndroid,
            Context applicationContext) {
        ChromeMediaRouter router = new ChromeMediaRouter(nativeMediaRouterAndroid);
        MediaRouteProvider provider = sRouteProviderBuilder.create(applicationContext, router);
        if (provider != null) router.addMediaRouteProvider(provider);

        return router;
    }

    /**
     * Starts background monitoring for available media sinks compatible with the given
     * |sourceUrn| if the device is in a state that allows it.
     * @param sourceId a URL to use for filtering of the available media sinks
     * @return whether the monitoring started (ie. was allowed).
     */
    @CalledByNative
    public boolean startObservingMediaSinks(String sourceId) {
        if (SysUtils.isLowEndDevice()) return false;

        for (MediaRouteProvider provider : mRouteProviders) {
            provider.startObservingMediaSinks(sourceId);
        }

        return true;
    }

    /**
     * Stops background monitoring for available media sinks compatible with the given
     * |sourceUrn|
     * @param sourceId a URL passed to {@link #startObservingMediaSinks(String)} before.
     */
    @CalledByNative
    public void stopObservingMediaSinks(String sourceId) {
        for (MediaRouteProvider provider : mRouteProviders) {
            provider.stopObservingMediaSinks(sourceId);
        }
        mSinksPerSource.remove(sourceId);
        mSinksPerSourcePerProvider.remove(sourceId);
    }

    /**
     * Returns the URN of the media sink corresponding to the given source URN
     * and an index. Essentially a way to access the corresponding {@link MediaSink}'s
     * list via JNI.
     * @param sourceUrn The URN to get the sink for.
     * @param index The index of the sink in the current sink array.
     * @return the corresponding sink URN if found or null.
     */
    @CalledByNative
    public String getSinkUrn(String sourceUrn, int index) {
        return getSink(sourceUrn, index).getUrn();
    }

    /**
     * Returns the name of the media sink corresponding to the given source URN
     * and an index. Essentially a way to access the corresponding {@link MediaSink}'s
     * list via JNI.
     * @param sourceUrn The URN to get the sink for.
     * @param index The index of the sink in the current sink array.
     * @return the corresponding sink name if found or null.
     */
    @CalledByNative
    public String getSinkName(String sourceUrn, int index) {
        return getSink(sourceUrn, index).getName();
    }

    /**
     * Initiates route creation with the given parameters. Notifies the native client of success
     * and failure.
     * @param sourceId the id of the {@link MediaSource} to route to the sink.
     * @param sinkId the id of the {@link MediaSink} to route the source to.
     * @param presentationId the id of the presentation to be used by the page.
     * @param origin the origin of the frame requesting a new route.
     * @param tabId the id of the tab the requesting frame belongs to.
     * @param isIncognito whether the route is being requested from an Incognito profile.
     * @param requestId the id of the route creation request tracked by the native side.
     */
    @CalledByNative
    public void createRoute(
            String sourceId,
            String sinkId,
            String presentationId,
            String origin,
            int tabId,
            boolean isIncognito,
            int requestId) {
        MediaRouteProvider provider = getProviderForSource(sourceId);
        if (provider == null) {
            onRouteRequestError("No provider supports createRoute with source: " + sourceId
                                + " and sink: " + sinkId, requestId);
            return;
        }

        provider.createRoute(
                sourceId, sinkId, presentationId, origin, tabId, isIncognito, requestId);
    }

    /**
     * Initiates route joining with the given parameters. Notifies the native client of success
     * or failure.
     * @param sourceId the id of the {@link MediaSource} to route to the sink.
     * @param sinkId the id of the {@link MediaSink} to route the source to.
     * @param presentationId the id of the presentation to be used by the page.
     * @param origin the origin of the frame requesting a new route.
     * @param tabId the id of the tab the requesting frame belongs to.
     * @param requestId the id of the route creation request tracked by the native side.
     */
    @CalledByNative
    public void joinRoute(
            String sourceId,
            String presentationId,
            String origin,
            int tabId,
            int requestId) {
        MediaRouteProvider provider = getProviderForSource(sourceId);
        if (provider == null) {
            onRouteRequestError("Route not found.", requestId);
            return;
        }

        provider.joinRoute(sourceId, presentationId, origin, tabId, requestId);
    }

    /**
     * Closes the route specified by the id.
     * @param routeId the id of the route to close.
     */
    @CalledByNative
    public void closeRoute(String routeId) {
        MediaRouteProvider provider = mRouteIdsToProviders.get(routeId);
        if (provider == null) return;

        provider.closeRoute(routeId);
    }

    /**
     * Notifies the specified route that it's not attached to the web page anymore.
     * @param routeId the id of the route that was detached.
     */
    @CalledByNative
    public void detachRoute(String routeId) {
        MediaRouteProvider provider = mRouteIdsToProviders.get(routeId);
        if (provider == null) return;

        provider.detachRoute(routeId);
        mRouteIdsToProviders.remove(routeId);
    }

    /**
     * Sends a string message to the specified route.
     * @param routeId The id of the route to send the message to.
     * @param message The message to send.
     * @param callbackId The id of the result callback tracked by the native side.
     */
    @CalledByNative
    public void sendStringMessage(String routeId, String message, int callbackId) {
        MediaRouteProvider provider = mRouteIdsToProviders.get(routeId);
        if (provider == null) {
            nativeOnMessageSentResult(mNativeMediaRouterAndroid, false, callbackId);
            return;
        }

        provider.sendStringMessage(routeId, message, callbackId);
    }

    /**
     * Sends a binary message to the specified route.
     * @param routeId The id of the route to send the message to.
     * @param data The binary message to send.
     * @param callbackId The id of the result callback tracked by the native side.
     */
    @CalledByNative
    public void sendBinaryMessage(String routeId, byte[] data, int callbackId) {
        MediaRouteProvider provider = mRouteIdsToProviders.get(routeId);
        if (provider == null) {
            nativeOnMessageSentResult(mNativeMediaRouterAndroid, false, callbackId);
            return;
        }

        provider.sendBinaryMessage(routeId, data, callbackId);
    }

    @VisibleForTesting
    protected ChromeMediaRouter(long nativeMediaRouter) {
        mNativeMediaRouterAndroid = nativeMediaRouter;
    }

    @VisibleForTesting
    protected void addMediaRouteProvider(MediaRouteProvider provider) {
        mRouteProviders.add(provider);
    }

    private MediaSink getSink(String sourceId, int index) {
        assert mSinksPerSource.containsKey(sourceId);
        return mSinksPerSource.get(sourceId).get(index);
    }

    private MediaRouteProvider getProviderForSource(String sourceId) {
        for (MediaRouteProvider provider : mRouteProviders) {
            if (provider.supportsSource(sourceId)) return provider;
        }
        return null;
    }

    native void nativeOnSinksReceived(
            long nativeMediaRouterAndroid, String sourceUrn, int count);
    native void nativeOnRouteCreated(
            long nativeMediaRouterAndroid,
            String mediaRouteId,
            String mediaSinkId,
            int createRouteRequestId,
            boolean wasLaunched);
    native void nativeOnRouteRequestError(
            long nativeMediaRouterAndroid, String errorText, int createRouteRequestId);
    native void nativeOnRouteClosed(long nativeMediaRouterAndroid, String mediaRouteId);
    native void nativeOnRouteClosedWithError(
            long nativeMediaRouterAndroid, String mediaRouteId, String message);
    native void nativeOnMessageSentResult(
            long nativeMediaRouterAndroid, boolean success, int callbackId);
    native void nativeOnMessage(long nativeMediaRouterAndroid, String mediaRouteId, String message);
}
