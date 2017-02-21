// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaItemStatus;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;

import com.google.android.gms.cast.CastMediaControlIntent;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.RemovableInRelease;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.ui.widget.Toast;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.Nullable;

/**
 * Class containing the common, connection type independent, code for all MediaRouteControllers.
 */
public abstract class AbstractMediaRouteController implements MediaRouteController {

    /**
     * Callback class for monitoring whether any routes exist, and hence deciding whether to show
     * the cast UI to users.
     */
    private class DeviceDiscoveryCallback extends MediaRouter.Callback {
        @Override
        public void onProviderAdded(MediaRouter router, MediaRouter.ProviderInfo provider) {
            updateRouteAvailability();
        }

        @Override
        public void onProviderChanged(
                MediaRouter router, MediaRouter.ProviderInfo provider) {
            updateRouteAvailability();
        }

        @Override
        public void onProviderRemoved(
                MediaRouter router, MediaRouter.ProviderInfo provider) {
            updateRouteAvailability();
        }

        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            logRoute("Added route", route);
            updateRouteAvailability();
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            logRoute("Removed route", route);
            updateRouteAvailability();
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo route) {
            logRoute("Changed route", route);
            updateRouteAvailability();
        }

        private void updateRouteAvailability() {
            if (mediaRouterInitializationFailed()) return;

            boolean routesAvailable = getMediaRouter().isRouteAvailable(mMediaRouteSelector,
                    MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE);
            if (routesAvailable != mRoutesAvailable) {
                mRoutesAvailable = routesAvailable;
                Log.d(TAG, "Remote media route availability changed, updating listeners");
                for (MediaStateListener listener : mAvailableRouteListeners) {
                    listener.onRouteAvailabilityChanged(routesAvailable);
                }
            }
        }
    }

    /**
     * Callback class for monitoring whether a route has been selected, and the state of the
     * selected route.
     */
    private class DeviceSelectionCallback extends MediaRouter.Callback {
        // Note that this doesn't use onRouteSelected, but instead casting is started directly
        // by the selection dialog. It has to be done that way, since selecting the current route
        // on a new video doesn't call onRouteSelected.

        private Runnable mConnectionFailureNotifier = new Runnable() {
                @Override
            public void run() {
                release();
                mConnectionFailureNotifierQueued = false;
            }
        };

        /** True if we are waiting for the MediaRouter route to connect or reconnect */
        private boolean mConnectionFailureNotifierQueued = false;

        private void clearConnectionFailureCallback() {
            getHandler().removeCallbacks(mConnectionFailureNotifier);
            mConnectionFailureNotifierQueued = false;
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo route) {
            // We only care about changes to the current route.
            if (!route.equals(getCurrentRoute())) return;
            // When there is no wifi connection, this condition becomes true.
            if (route.isConnecting()) {
                // We don't want to post the same Runnable twice.
                if (!mConnectionFailureNotifierQueued) {
                    mConnectionFailureNotifierQueued = true;
                    getHandler().postDelayed(mConnectionFailureNotifier,
                            CONNECTION_FAILURE_NOTIFICATION_DELAY_MS);
                }
            } else {
                // Only cancel the disconnect if we already posted the message. We can get into this
                // situation if we swap the current route provider (for example, switching to a YT
                // video while casting a non-YT video).
                if (mConnectionFailureNotifierQueued) {
                    // We have reconnected, cancel the delayed disconnect.
                    getHandler().removeCallbacks(mConnectionFailureNotifier);
                    mConnectionFailureNotifierQueued = false;
                }
            }
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo route) {
            onRouteUnselectedEvent(router, route);
            if (getCurrentRoute() != null && !getCurrentRoute().isDefault()
                    && route.getId().equals(getCurrentRoute().getId())) {
                RecordCastAction.castEndedTimeRemaining(getDuration(),
                        getDuration() - getPosition());
                release();
            }
        }
    }

    /** Number of ms to wait for reconnection, after which we call the failure callbacks. */
    protected static final long CONNECTION_FAILURE_NOTIFICATION_DELAY_MS = 10000L;
    private static final long END_OF_VIDEO_THRESHOLD_MS = 500L;
    private static final String TAG = "MediaFling";
    private final Set<MediaStateListener> mAvailableRouteListeners;
    private final Context mContext;
    private RouteInfo mCurrentRoute;
    private final DeviceDiscoveryCallback mDeviceDiscoveryCallback;;
    private final DeviceSelectionCallback mDeviceSelectionCallback;

    private final Handler mHandler;
    private boolean mIsPrepared = false;

    private final MediaRouter mMediaRouter;

    private final MediaRouteSelector mMediaRouteSelector;
    /**
     * The media state listener connects to the web page that requested casting. It will be null if
     * that page is no longer in a tab, but closing the page or tab should not stop cast. Cast can
     * still be controlled through the notification even if the page is closed.
     */
    private MediaStateListener mMediaStateListener;

    // There are times when the player state shown to user (e.g. just after pressing the pause
    // button) should update before we receive an update from the Chromecast, so we have to track
    // two player states.
    private PlayerState mRemotePlayerState = PlayerState.FINISHED;
    private PlayerState mDisplayedPlayerState = PlayerState.FINISHED;
    private boolean mRoutesAvailable = false;
    private final Set<UiListener> mUiListeners;
    private boolean mWatchingRouteSelection = false;

    private long mMediaElementAttachedTimestampMs = 0;
    private long mMediaElementDetachedTimestampMs = 0;

    protected AbstractMediaRouteController() {
        mContext = ContextUtils.getApplicationContext();
        assert (getContext() != null);

        mHandler = new Handler();

        mMediaRouteSelector = buildMediaRouteSelector();

        MediaRouter mediaRouter;

        try {
            // Pre-MR1 versions of JB do not have the complete MediaRouter APIs,
            // so getting the MediaRouter instance will throw an exception.
            mediaRouter = MediaRouter.getInstance(getContext());
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "Can't get an instance of MediaRouter, casting is not supported."
                    + " Are you still on JB (JVP15S)?");
            mediaRouter = null;
        }
        mMediaRouter = mediaRouter;

        mAvailableRouteListeners = new HashSet<MediaStateListener>();
        // TODO(aberent): I am unclear why this is accessed from multiple threads, but
        // if I make it a HashSet then it gets ConcurrentModificationExceptions on some
        // types of disconnect. Investigate and fix.
        mUiListeners = new CopyOnWriteArraySet<UiListener>();

        mDeviceDiscoveryCallback = new DeviceDiscoveryCallback();
        mDeviceSelectionCallback = new DeviceSelectionCallback();
    }

    @Override
    public void addMediaStateListener(MediaStateListener listener) {
        if (mediaRouterInitializationFailed()) return;

        if (mAvailableRouteListeners.isEmpty()) {
            getMediaRouter().addCallback(mMediaRouteSelector, mDeviceDiscoveryCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
            Log.d(TAG, "Started device discovery");

            // Get the initial state
            mRoutesAvailable = getMediaRouter().isRouteAvailable(
                    mMediaRouteSelector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE);
        }
        mAvailableRouteListeners.add(listener);
        // Send the current state to the listener.
        listener.onRouteAvailabilityChanged(mRoutesAvailable);
    }

    @Override
    public void addUiListener(UiListener listener) {
        mUiListeners.add(listener);
    }

    protected void clearConnectionFailureCallback() {
        mDeviceSelectionCallback.clearConnectionFailureCallback();
    }

    /**
     * Clear the current playing item (if any) but not the associated session.
     */
    protected void clearItemState() {
        mRemotePlayerState = PlayerState.FINISHED;
        mDisplayedPlayerState = PlayerState.FINISHED;
        updateTitle(null);
    }

    /**
     * Reset the media route to the default
     */
    protected void clearMediaRoute() {
        if (getMediaRouter() != null) {
            getMediaRouter().getDefaultRoute().select();
            registerRoute(getMediaRouter().getDefaultRoute());
        }
    }

    @Override
    public boolean currentRouteSupportsRemotePlayback() {
        return mCurrentRoute != null && mCurrentRoute.supportsControlCategory(
                MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
    }

    protected final Context getContext() {
        return mContext;
    }

    protected final RouteInfo getCurrentRoute() {
        return mCurrentRoute;
    }

    protected final Handler getHandler() {
        return mHandler;
    }

    protected final MediaRouter getMediaRouter() {
        return mMediaRouter;
    }

    @Override
    public final MediaStateListener getMediaStateListener() {
        return mMediaStateListener;
    }

    public final PlayerState getRemotePlayerState() {
        return mRemotePlayerState;
    }

    @Override
    public final PlayerState getDisplayedPlayerState() {
        return mDisplayedPlayerState;
    }

    @Override
    public final String getRouteName() {
        return mCurrentRoute == null ? null : mCurrentRoute.getName();
    }

    protected final Set<UiListener> getUiListeners() {
        return mUiListeners;
    }

    private final boolean isAtEndOfVideo(long positionMs, long videoLengthMs) {
        return videoLengthMs - positionMs < END_OF_VIDEO_THRESHOLD_MS && videoLengthMs > 0;
    }

    @Override
    public final boolean isBeingCast() {
        return (mIsPrepared && mRemotePlayerState != PlayerState.INVALIDATED
                && mRemotePlayerState != PlayerState.ERROR
                && mRemotePlayerState != PlayerState.FINISHED);
    }

    @Override
    public final boolean isPlaying() {
        return mRemotePlayerState == PlayerState.PLAYING
                || mRemotePlayerState == PlayerState.LOADING;
    }

    @Override
    public final boolean isRemotePlaybackAvailable() {
        if (mediaRouterInitializationFailed()) return false;

        return getMediaRouter().getSelectedRoute().getPlaybackType()
                == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE || getMediaRouter().isRouteAvailable(
                mMediaRouteSelector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE);
    }

    protected final boolean mediaRouterInitializationFailed() {
        return getMediaRouter() == null;
    }

    protected final void notifyRouteSelected(RouteInfo route) {
        for (UiListener listener : mUiListeners) {
            listener.onRouteSelected(route.getName(), this);
        }
        if (!canCastMedia()) return;
        if (mMediaStateListener == null) return;
        mMediaStateListener.pauseLocal();
        mMediaStateListener.onCastStarting(route.getName());
        startCastingVideo();
    }

    // This exists for compatibility with old downstream code
    // TODO(aberent) convert to abstract
    protected void startCastingVideo() {
        String url = mMediaStateListener.getSourceUrl();
        Uri uri = url == null ? null : Uri.parse(url);
        setDataSource(uri, mMediaStateListener.getCookies());
        prepareAsync(
                mMediaStateListener.getFrameUrl(), mMediaStateListener.getStartPositionMillis());
    }

    private boolean canCastMedia() {
        return isRemotePlaybackAvailable() && !routeIsDefaultRoute()
                && currentRouteSupportsRemotePlayback();
    }

    protected void onRouteAddedEvent(MediaRouter router, RouteInfo route) {
    };

    // TODO(aberent): Merge with onRouteSelected(). Needs two sided patch for downstream
    // implementations
    protected void onRouteSelectedEvent(MediaRouter router, RouteInfo route) {
    }

    @Override
    public void onRouteSelected(MediaStateListener player, MediaRouter router, RouteInfo route) {
        if (mMediaStateListener != null) mMediaStateListener.onCastStopping();
        setMediaStateListener(player);
        onRouteSelectedEvent(router, route);
    }

    protected abstract void onRouteUnselectedEvent(MediaRouter router, RouteInfo route);

    @Override
    public void prepareMediaRoute() {
        startWatchingRouteSelection();
    }

    @Override
    public void release() {
        recordEndOfSessionUMA();
    }

    private void recordEndOfSessionUMA() {
        long remotePlaybackStoppedTimestampMs = SystemClock.elapsedRealtime();

        // There was no media element ever...
        if (mMediaElementAttachedTimestampMs == 0) return;

        long remotePlaybackIntervalMs =
                remotePlaybackStoppedTimestampMs - mMediaElementAttachedTimestampMs;

        if (mMediaElementDetachedTimestampMs == 0) {
            mMediaElementDetachedTimestampMs = remotePlaybackStoppedTimestampMs;
        }

        int noElementRemotePlaybackTimePercentage =
                (int) ((remotePlaybackStoppedTimestampMs - mMediaElementDetachedTimestampMs) * 100
                        / remotePlaybackIntervalMs);
        RecordCastAction.recordRemoteSessionTimeWithoutMediaElementPercentage(
                noElementRemotePlaybackTimePercentage);
        mMediaElementAttachedTimestampMs = 0;
        mMediaElementDetachedTimestampMs = 0;
    }

    protected final void registerRoute(RouteInfo route) {
        mCurrentRoute = route;
        logRoute("Selected route", route);
    }

    @RemovableInRelease
    private void logRoute(String message, RouteInfo route) {
        if (route != null) {
            Log.d(TAG, message + " " + route.getName() + " " + route.getId());
        }
    }

    protected void removeAllListeners() {
        mUiListeners.clear();
    }

    @Override
    public void removeMediaStateListener(MediaStateListener listener) {
        if (mediaRouterInitializationFailed()) return;

        mAvailableRouteListeners.remove(listener);
        if (mAvailableRouteListeners.isEmpty()) {
            getMediaRouter().removeCallback(mDeviceDiscoveryCallback);
            Log.d(TAG, "Stopped device discovery");
        }
    }

    @Override
    public void removeUiListener(UiListener listener) {
        mUiListeners.remove(listener);
    }

    @Override
    public boolean routeIsDefaultRoute() {
        return mCurrentRoute != null && mCurrentRoute.isDefault();
    }

    protected void sendErrorToListeners(int error) {
        String errorMessage =
                getContext().getString(R.string.cast_error_playing_video, mCurrentRoute.getName());

        for (UiListener listener : mUiListeners) {
            listener.onError(error, errorMessage);
        }

        if (mMediaStateListener != null) mMediaStateListener.onError();
    }

    @Override
    public void setMediaStateListener(MediaStateListener mediaStateListener) {
        if (mMediaStateListener != null && mediaStateListener == null
                    && mMediaElementAttachedTimestampMs != 0) {
            mMediaElementDetachedTimestampMs = SystemClock.elapsedRealtime();
        } else if (mMediaStateListener == null && mediaStateListener != null) {
            // We're switching the videos so let's record the UMA for the previous one.
            if (mMediaElementDetachedTimestampMs != 0) recordEndOfSessionUMA();

            mMediaElementAttachedTimestampMs = SystemClock.elapsedRealtime();
            mMediaElementDetachedTimestampMs = 0;
        }

        mMediaStateListener = mediaStateListener;
    }

    private void onCasting() {
        if (!mIsPrepared) {
            for (UiListener listener : mUiListeners) {
                listener.onPrepared(this);
            }
            if (mMediaStateListener != null) {
                if (mMediaStateListener.isPauseRequested()) pause();
                if (mMediaStateListener.isSeekRequested()) {
                    seekTo(mMediaStateListener.getSeekLocation());
                } else {
                    seekTo(mMediaStateListener.getLocalPosition());
                }
            }
            RecordCastAction.castDefaultPlayerResult(true);
            mIsPrepared = true;
        }
    }

    protected void setUnprepared() {
        mIsPrepared = false;
    }

    protected void showCastError(String routeName) {
        Toast toast = Toast.makeText(
                getContext(),
                getContext().getString(R.string.cast_error_playing_video, routeName),
                Toast.LENGTH_SHORT);
        toast.show();
    }

    private void startWatchingRouteSelection() {
        if (mWatchingRouteSelection || mediaRouterInitializationFailed()) return;

        mWatchingRouteSelection = true;
        // Start listening
        getMediaRouter().addCallback(mMediaRouteSelector, mDeviceSelectionCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        Log.d(TAG, "Started route selection discovery");
    }

    protected void stopWatchingRouteSelection() {
        mWatchingRouteSelection = false;
        if (getMediaRouter() != null) {
            getMediaRouter().removeCallback(mDeviceSelectionCallback);
            Log.d(TAG, "Stopped route selection discovery");
        }
    }

    @VisibleForTesting
    void setPlayerStateForMediaItemState(int state) {
        PlayerState playerState = PlayerState.STOPPED;
        switch (state) {
            case MediaItemStatus.PLAYBACK_STATE_BUFFERING:
                playerState = PlayerState.LOADING;
                break;
            case MediaItemStatus.PLAYBACK_STATE_CANCELED:
                playerState = PlayerState.FINISHED;
                break;
            case MediaItemStatus.PLAYBACK_STATE_ERROR:
                playerState = PlayerState.ERROR;
                break;
            case MediaItemStatus.PLAYBACK_STATE_FINISHED:
                playerState = PlayerState.FINISHED;
                break;
            case MediaItemStatus.PLAYBACK_STATE_INVALIDATED:
                playerState = PlayerState.INVALIDATED;
                break;
            case MediaItemStatus.PLAYBACK_STATE_PAUSED:
                if (isAtEndOfVideo(getPosition(), getDuration())) {
                    playerState = PlayerState.FINISHED;
                } else {
                    playerState = PlayerState.PAUSED;
                }
                break;
            case MediaItemStatus.PLAYBACK_STATE_PENDING:
                playerState = PlayerState.PAUSED;
                break;
            case MediaItemStatus.PLAYBACK_STATE_PLAYING:
                playerState = PlayerState.PLAYING;
                break;
            default:
                break;
        }

        mRemotePlayerState = playerState;
    }

    protected void updateState(int state) {
        Log.d(TAG, "updateState oldState: %s player state: %s", mRemotePlayerState, state);

        PlayerState oldState = mRemotePlayerState;
        setPlayerStateForMediaItemState(state);

        Log.d(TAG, "updateState newState: %s", mRemotePlayerState);

        if (oldState != mRemotePlayerState) {
            setDisplayedPlayerState(mRemotePlayerState);

            switch (mRemotePlayerState) {
                case PLAYING:
                    onCasting();
                    break;
                case PAUSED:
                    onCasting();
                    break;
                case FINISHED:
                    release();
                    break;
                case INVALIDATED:
                    clearItemState();
                    break;
                case ERROR:
                    sendErrorToListeners(CastMediaControlIntent.ERROR_CODE_REQUEST_FAILED);
                    release();
                    break;
                default:
                    break;
            }
        }
    }

    protected void setDisplayedPlayerState(PlayerState state) {
        mDisplayedPlayerState = state;
        for (UiListener listener : mUiListeners) {
            listener.onPlaybackStateChanged(mDisplayedPlayerState);
        }
        if (mMediaStateListener != null) {
            mMediaStateListener.onPlaybackStateChanged(mDisplayedPlayerState);
        }
    }

    protected void updateTitle(@Nullable String newTitle) {
        for (UiListener listener : mUiListeners) {
            listener.onTitleChanged(newTitle);
        }
    }

    @Override
    public Bitmap getPoster() {
        if (getMediaStateListener() == null) return null;
        return getMediaStateListener().getPosterBitmap();
    }

    // This exists for compatibility with old downstream code
    // TODO(aberent) remove
    protected void prepareAsync(String frameUrl, long startPositionMillis){};

    // This exists for compatibility with old downstream code
    // TODO(aberent) remove
    protected void setDataSource(Uri uri, String cookies){};

    protected boolean reconnectAnyExistingRoute() {
        // Temp version to avoid two sided patch while removing
        return false;
    };

    @Override
    public void checkIfPlayableRemotely(String sourceUrl, String frameUrl, String cookies,
            String userAgent, MediaValidationCallback callback) {
        callback.onResult(true, sourceUrl, frameUrl);
    }

    @Override
    public String getUriPlaying() {
        return null;
    }

    // Used by J
    void setPreparedForTesting() {
        mIsPrepared = true;
    }
}
