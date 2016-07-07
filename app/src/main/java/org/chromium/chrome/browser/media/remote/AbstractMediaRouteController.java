// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaItemStatus;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;

import com.google.android.gms.cast.CastMediaControlIntent;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
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
            if (mDebug) Log.d(TAG, "Added route " + route.getName() + " " + route.getId());
            updateRouteAvailability();
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            if (mDebug) {
                Log.d(TAG, "Removed route " + route.getName() + " " + route.getId());
            }
            updateRouteAvailability();
        }

        private void updateRouteAvailability() {
            if (mediaRouterInitializationFailed()) return;

            boolean routesAvailable = getMediaRouter().isRouteAvailable(mMediaRouteSelector,
                    MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE);
            if (routesAvailable != mRoutesAvailable) {
                mRoutesAvailable = routesAvailable;
                if (mDebug) {
                    Log.d(TAG, "Remote media route availability changed, updating listeners");
                }
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
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            onRouteAddedEvent(router, route);
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
        public void onRouteSelected(MediaRouter router, RouteInfo route) {
            onRouteSelectedEvent(router, route);
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
    protected static final int CONNECTION_FAILURE_NOTIFICATION_DELAY_MS = 10000;
    private static final int END_OF_VIDEO_THRESHOLD_MS = 500;
    private static final String TAG = "AbstractMediaRouteController";
    private final Set<MediaStateListener> mAvailableRouteListeners;
    private final Context mContext;
    private RouteInfo mCurrentRoute;
    private boolean mDebug;
    private final DeviceDiscoveryCallback mDeviceDiscoveryCallback;;
    private String mDeviceId;
    private final DeviceSelectionCallback mDeviceSelectionCallback;

    private final Handler mHandler;
    private boolean mIsPrepared = false;

    private final MediaRouter mMediaRouter;

    private final MediaRouteSelector mMediaRouteSelector;
    private MediaStateListener mMediaStateListener;
    private PlayerState mPlaybackState = PlayerState.FINISHED;
    private boolean mRoutesAvailable = false;
    private final Set<UiListener> mUiListeners;
    private boolean mWatchingRouteSelection = false;

    protected AbstractMediaRouteController() {

        mDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);

        mContext = ApplicationStatus.getApplicationContext();
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
            if (mDebug) Log.d(TAG, "Started device discovery");

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
        mPlaybackState = PlayerState.FINISHED;
        updateTitle(null);
    }

    /**
     * Reset the media route to the default
     */
    protected void clearMediaRoute() {
        if (getMediaRouter() != null) {
            getMediaRouter().getDefaultRoute().select();
            registerRoute(getMediaRouter().getDefaultRoute());
            RemotePlaybackSettings.setDeviceId(getContext(), null);
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

    protected final String getDeviceId() {
        return mDeviceId;
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

    @Override
    public final PlayerState getPlayerState() {
        return mPlaybackState;
    }

    @Override
    public final String getRouteName() {
        return mCurrentRoute == null ? null : mCurrentRoute.getName();
    }

    protected final Set<UiListener> getUiListeners() {
        return mUiListeners;
    }

    private final boolean isAtEndOfVideo(int positionMs, int videoLengthMs) {
        return videoLengthMs - positionMs < END_OF_VIDEO_THRESHOLD_MS && videoLengthMs > 0;
    }

    @Override
    public final boolean isBeingCast() {
        return (mPlaybackState != PlayerState.INVALIDATED && mPlaybackState != PlayerState.ERROR
                && mPlaybackState != PlayerState.FINISHED);
    }

    @Override
    public final boolean isPlaying() {
        return mPlaybackState == PlayerState.PLAYING || mPlaybackState == PlayerState.LOADING;
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
        if (mMediaStateListener == null) return;
        if (!canCastMedia()) return;
        startCastingVideo(route);
    }

    private void startCastingVideo(RouteInfo route) {
        mMediaStateListener.pauseLocal();
        mMediaStateListener.onCastStarting(route.getName());
        String url = mMediaStateListener.getSourceUrl();
        Uri uri = url == null ? null : Uri.parse(url);
        setDataSource(uri, mMediaStateListener.getCookies(), mMediaStateListener.getUserAgent());
        prepareAsync(
                mMediaStateListener.getFrameUrl(), mMediaStateListener.getStartPositionMillis());
    }

    private boolean canCastMedia() {
        return isRemotePlaybackAvailable() && !routeIsDefaultRoute()
                && currentRouteSupportsRemotePlayback();
    }

    @Override
    public void onPause() {
        pause();
    }

    @Override
    public void onPlay() {
        resume();
    }

    protected abstract void onRouteAddedEvent(MediaRouter router, RouteInfo route);

    protected abstract void onRouteSelectedEvent(MediaRouter router, RouteInfo route);

    protected abstract void onRouteUnselectedEvent(MediaRouter router, RouteInfo route);

    @Override
    public void onSeek(int position) {
        seekTo(position);
    }

    @Override
    public void onStop() {
        release();
    }

    @Override
    public void prepareMediaRoute() {
        startWatchingRouteSelection();
    }

    protected final void registerRoute(RouteInfo route) {
        mCurrentRoute = route;

        if (route != null) {
            setDeviceId(route.getId());
            if (mDebug) Log.d(TAG, "Selected route " + getDeviceId());
            if (!route.isDefault()) {
                RemotePlaybackSettings.setDeviceId(getContext(), getDeviceId());
            }
        } else {
            RemotePlaybackSettings.setDeviceId(getContext(), null);
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
            if (mDebug) Log.d(TAG, "Stopped device discovery");
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

    protected void setDeviceId(String mDeviceId) {
        this.mDeviceId = mDeviceId;
    }

    @Override
    public void setMediaStateListener(MediaStateListener mediaStateListener) {
        mMediaStateListener = mediaStateListener;
    }

    private void onCasting() {
        if (!mIsPrepared) {
            for (UiListener listener : mUiListeners) {
                listener.onPrepared(this);
            }
            if (mMediaStateListener.isPauseRequested()) pause();
            if (mMediaStateListener.isSeekRequested()) {
                seekTo(mMediaStateListener.getSeekLocation());
            } else {
                seekTo(mMediaStateListener.getLocalPosition());
            }
            RecordCastAction.castDefaultPlayerResult(true);
            mIsPrepared = true;
        }
    }

    protected void setUnprepared() {
        mIsPrepared = false;
    }

    @Override
    public boolean shouldResetState(MediaStateListener newPlayer) {
        return !isBeingCast() || newPlayer != getMediaStateListener();
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
        if (mDebug) Log.d(TAG, "Started route selection discovery");
    }

    protected void stopWatchingRouteSelection() {
        mWatchingRouteSelection = false;
        if (getMediaRouter() != null) {
            getMediaRouter().removeCallback(mDeviceSelectionCallback);
            if (mDebug) Log.d(TAG, "Stopped route selection discovery");
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

        mPlaybackState = playerState;
    }

    protected void updateState(int state) {
        if (mDebug) {
            Log.d(TAG, "updateState oldState: " + mPlaybackState + " newState: " + state);
        }

        PlayerState oldState = mPlaybackState;
        setPlayerStateForMediaItemState(state);

        for (UiListener listener : mUiListeners) {
            listener.onPlaybackStateChanged(oldState, mPlaybackState);
        }

        if (mMediaStateListener != null) mMediaStateListener.onPlaybackStateChanged(mPlaybackState);

        if (oldState != mPlaybackState) {
            // We need to persist our state in case we get killed.
            RemotePlaybackSettings.setLastVideoState(getContext(), mPlaybackState.name());

            switch (mPlaybackState) {
                case PLAYING:
                    RemotePlaybackSettings.setRemainingTime(getContext(),
                            getDuration() - getPosition());
                    RemotePlaybackSettings.setLastPlayedTime(getContext(),
                            System.currentTimeMillis());
                    RemotePlaybackSettings.setShouldReconnectToRemote(getContext(),
                            !mCurrentRoute.isDefault());
                    onCasting();
                    break;
                case PAUSED:
                    RemotePlaybackSettings.setShouldReconnectToRemote(getContext(),
                            !mCurrentRoute.isDefault());
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

    protected void updateTitle(@Nullable String newTitle) {
        for (UiListener listener : mUiListeners) {
            listener.onTitleChanged(newTitle);
        }
    }

    @Override
    public Bitmap getPoster() {
        if (mMediaStateListener == null) return null;
        return mMediaStateListener.getPosterBitmap();
    }

    // TODO(aberent): Temp to change args while avoiding need for two sided patch for YT.
    @Override
    public void setDataSource(Uri uri, String cookies, String userAgent) {
        setDataSource(uri, cookies);
    };

    /**
     * Temp default version to allow override in YouTubeMediaRouteController, while not
     * requiring it in DefaultMediaRouteController.
     * TODO(aberent): Fix YT and remove.
     * @param uri
     * @param cookies
     */
    public void setDataSource(Uri uri, String cookies) {};

    @Override
    public boolean playerTakesOverCastDevice(MediaStateListener mediaStateListener) {
        // Check if this MediaRouteControler is casting something.
        if (!isBeingCast()) return false;
        // Check if we want to cast the new video
        if (!canCastMedia()) return false;
        // Take over the cast device
        if (mMediaStateListener != null) mMediaStateListener.onCastStopping();
        mMediaStateListener = mediaStateListener;
        startCastingVideo(mCurrentRoute);
        return true;
    }
}
