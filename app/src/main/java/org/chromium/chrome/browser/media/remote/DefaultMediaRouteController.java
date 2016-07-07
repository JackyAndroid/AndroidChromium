// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaItemMetadata;
import android.support.v7.media.MediaItemStatus;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.support.v7.media.MediaSessionStatus;
import android.util.Log;

import com.google.android.gms.cast.CastMediaControlIntent;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.ui.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Nullable;

/**
 * Class that abstracts all communication to and from the Android MediaRoutes. This class is
 * responsible for connecting to the MRs as well as sending commands and receiving status updates
 * from the remote player.
 *
 *  We have three main scenarios for Cast:
 *
 *  - the first cast: user plays the first video on the Chromecast so we start a new session with
 * the player and fling the video
 *
 *  - the consequent cast: users plays another video while the previous one is still playing
 * remotely meaning that we don't have to start the session but to replace the current video with
 * the new one
 *
 *  - the reconnect: if Clank crashes, we need to try to reconnect to the existing session and
 * continue controlling the currently playing video.
 *
 *  Casting the first video takes three intents sent to the selected media route:
 * ACTION_START_SESSION, ACTION_SYNC_STATUS and ACTION_PLAY. The first one is sent before anything
 * else. We get the session id from the result bundle of the intent but need to wait until the
 * session becomes active before continuing to the next step. Then we send the ACTION_SYNC_STATUS
 * intent to update the media item status and pass the PendingIntent for the media item status
 * events to the Cast MRP. Finally we send the video URL via the ACTION_PLAY intent.
 *
 *  Casting the second video should only take one ACTION_PLAY intent if the session is still active.
 * Otherwise, the scenario is the same as for the first video. However, due to the crbug.com/336188
 * we need to restart the session for each ACTION_PLAY so we go through the same process as above.
 *
 *  In order to reconnect, we need to programmatically select the previously selected media route.
 * To do this we send an ACTION_START_SESSION with the old session ID. This is not clearly
 * documented in the Android documentation, but seems to only succeed if the session still exists.
 * Otherwise we need to start a new session.
 *
 *  Note that, if the Chrome cast notification restarts following a crash, instances of this class
 * may exist before the C++ library has been loaded. As such this class should avoid using anything
 * that might use the C++ library (almost anything else in Chrome) or check that the library is
 * loaded before using them (as it does for recording UMA statistics).
 */
public class DefaultMediaRouteController extends AbstractMediaRouteController {

    /**
     * Interface for MediaRouter intents result handlers.
     */
    protected interface ResultBundleHandler {
        void onResult(Bundle data);

        void onError(String message, Bundle data);
    }

    private static final String TAG = "DefaultMediaRouteController";

    private static final String ACTION_RECEIVE_SESSION_STATUS_UPDATE =
            "com.google.android.apps.chrome.videofling.RECEIVE_SESSION_STATUS_UPDATE";
    private static final String ACTION_RECEIVE_MEDIA_STATUS_UPDATE =
            "com.google.android.apps.chrome.videofling.RECEIVE_MEDIA_STATUS_UPDATE";
    private static final String MIME_TYPE = "video/mp4";
    private boolean mDebug;
    private String mCurrentSessionId;
    private String mCurrentItemId;
    private int mStreamPositionTimestamp;
    private int mLastKnownStreamPosition;
    private int mStreamDuration;
    private boolean mSeeking;
    private final String mIntentCategory;
    private PendingIntent mSessionStatusUpdateIntent;
    private BroadcastReceiver mSessionStatusBroadcastReceiver;
    private PendingIntent mMediaStatusUpdateIntent;
    private BroadcastReceiver mMediaStatusBroadcastReceiver;
    private boolean mReconnecting = false;

    private Uri mVideoUriToStart;
    private String mPreferredTitle;
    private long mStartPositionMillis;

    private Uri mLocalVideoUri;

    private String mLocalVideoCookies;

    private MediaUrlResolver mMediaUrlResolver;

    private int mSessionState = MediaSessionStatus.SESSION_STATE_INVALIDATED;

    private final ApplicationStatus.ApplicationStateListener
            mApplicationStateListener = new ApplicationStatus.ApplicationStateListener() {
                @Override
                public void onApplicationStateChange(int newState) {
                    switch (newState) {
                    // HAS_DESTROYED_ACTIVITIES means all Chrome activities have been destroyed.
                        case ApplicationState.HAS_DESTROYED_ACTIVITIES:
                            onActivitiesDestroyed();
                            break;
                        default:
                            break;
                    }
                }
            };

    private final MediaUrlResolver.Delegate
            mMediaUrlResolverDelegate = new MediaUrlResolver.Delegate() {
                @Override
                public Uri getUri() {
                    return mLocalVideoUri;
                }

                @Override
                public String getCookies() {
                    return mLocalVideoCookies;
                }

                @Override
                public void setUri(Uri uri, boolean playable) {
                    if (playable) {
                        mLocalVideoUri = uri;
                        playMedia();
                        return;
                    }
                    mLocalVideoUri = null;
                    showMessageToast(
                            getContext().getString(R.string.cast_permission_error_playing_video));
                    release();
                }
            };

    private String mUserAgent;

    /**
     * Default and only constructor.
     */
    public DefaultMediaRouteController() {
        mDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);
        mIntentCategory = getContext().getPackageName();
    }

    @Override
    public boolean initialize() {
        if (mediaRouterInitializationFailed()) return false;

        ApplicationStatus.registerApplicationStateListener(mApplicationStateListener);

        if (mSessionStatusUpdateIntent == null) {
            Intent sessionStatusUpdateIntent = new Intent(ACTION_RECEIVE_SESSION_STATUS_UPDATE);
            sessionStatusUpdateIntent.addCategory(mIntentCategory);
            mSessionStatusUpdateIntent = PendingIntent.getBroadcast(getContext(), 0,
                    sessionStatusUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        if (mMediaStatusUpdateIntent == null) {
            Intent mediaStatusUpdateIntent = new Intent(ACTION_RECEIVE_MEDIA_STATUS_UPDATE);
            mediaStatusUpdateIntent.addCategory(mIntentCategory);
            mMediaStatusUpdateIntent = PendingIntent.getBroadcast(getContext(), 0,
                    mediaStatusUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return true;
    }

    @Override
    public boolean canPlayMedia(String sourceUrl, String frameUrl) {

        if (mediaRouterInitializationFailed()) return false;

        if (sourceUrl == null) return false;

        try {
            String scheme = new URI(sourceUrl).getScheme();
            if (scheme == null) return false;
            return scheme.equals("http") || scheme.equals("https");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public void setRemoteVolume(int delta) {
        boolean canChangeRemoteVolume = (getCurrentRoute().getVolumeHandling()
                == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE);
        if (currentRouteSupportsRemotePlayback() && canChangeRemoteVolume) {
            getCurrentRoute().requestUpdateVolume(delta);
        }
    }

    @Override
    public MediaRouteSelector buildMediaRouteSelector() {
        return new MediaRouteSelector.Builder().addControlCategory(
                CastMediaControlIntent.categoryForRemotePlayback(getCastReceiverId())).build();
    }

    protected String getCastReceiverId() {
        return CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
    }

    @Override
    public boolean reconnectAnyExistingRoute() {
        String deviceId = RemotePlaybackSettings.getDeviceId(getContext());
        RouteInfo defaultRoute = getMediaRouter().getDefaultRoute();
        if (deviceId == null || deviceId.equals(defaultRoute.getId()) || !shouldReconnect()) {
            RemotePlaybackSettings.setShouldReconnectToRemote(getContext(), false);
            return false;
        }
        mReconnecting = true;
        selectDevice(deviceId);
        getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mReconnecting) {
                    Log.d(TAG, "Reconnection timed out");
                    // We have been trying to reconnect for too long. Give up and save battery.
                    mReconnecting = false;
                    release();
                }
            }
        }, CONNECTION_FAILURE_NOTIFICATION_DELAY_MS);
        return true;
    }

    private boolean shouldReconnect() {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_CAST_RECONNECTION)) {
            if (mDebug) Log.d(TAG, "Cast reconnection disabled");
            return false;
        }
        boolean reconnect = false;
        if (RemotePlaybackSettings.getShouldReconnectToRemote(getContext())) {
            String lastState = RemotePlaybackSettings.getLastVideoState(getContext());
            if (lastState != null) {
                PlayerState state = PlayerState.valueOf(lastState);
                if (state == PlayerState.PLAYING || state == PlayerState.LOADING) {
                    // If we were playing when we got killed, check the time to
                    // see if it's still
                    // plausible that the remote video is playing currently
                    long remainingPlaytime = RemotePlaybackSettings.getRemainingTime(getContext());
                    long lastPlayedTime = RemotePlaybackSettings.getLastPlayedTime(getContext());
                    long currentTime = System.currentTimeMillis();
                    if (currentTime < lastPlayedTime + remainingPlaytime) {
                        reconnect = true;
                    }
                } else if (state == PlayerState.PAUSED) {
                    reconnect = true;
                }
            }
        }
        if (mDebug) Log.d(TAG, "shouldReconnect returning: " + reconnect);
        return reconnect;
    }

    /**
     * Tries to select a device with the given device ID. The device ID is cached so that if the
     * route does not exist yet, we will connect to it as soon as it comes back up again
     *
     * @param deviceId the ID of the device to connect to
     */
    private void selectDevice(String deviceId) {
        if (deviceId == null) {
            release();
            return;
        }

        setDeviceId(deviceId);

        if (mDebug) Log.d(TAG, "Trying to select " + getDeviceId());

        // See if we can select the device at this point.
        if (getMediaRouter() != null) {
            for (MediaRouter.RouteInfo route : getMediaRouter().getRoutes()) {
                if (deviceId.equals(route.getId())) {
                    route.select();
                    break;
                }
            }
        }
    }

    @Override
    public void resume() {
        if (mCurrentItemId == null) return;

        Intent intent = new Intent(MediaControlIntent.ACTION_RESUME);
        intent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, mCurrentSessionId);
        sendIntentToRoute(intent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                processMediaStatusBundle(data);
            }

            @Override
            public void onError(String message, Bundle data) {
                release();
            }
        });

        updateState(MediaItemStatus.PLAYBACK_STATE_BUFFERING);
    }

    @Override
    public void pause() {
        if (mCurrentItemId == null) return;

        Intent intent = new Intent(MediaControlIntent.ACTION_PAUSE);
        intent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, mCurrentSessionId);
        sendIntentToRoute(intent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                processMediaStatusBundle(data);
            }

            @Override
            public void onError(String message, Bundle data) {
                // Do not release the player just because of a failed pause
                // request. This can happen when pausing more than once for
                // example.
            }
        });

        // Update the last known position to the current one so that we don't
        // jump back in time discarding whatever we extrapolated from the last
        // time the position was updated.
        mLastKnownStreamPosition = getPosition();
        updateState(MediaItemStatus.PLAYBACK_STATE_PAUSED);
    }

    /**
     * Plays the given Uri on the currently selected player. This will replace any currently playing
     * video
     *
     * @param videoUri Uri of the video to play
     * @param preferredTitle the preferred title of the current playback session to display
     * @param startPositionMillis from which to start playing.
     */
    private void playUri(final Uri videoUri,
            @Nullable final String preferredTitle, final long startPositionMillis) {

        RecordCastAction.castMediaType(MediaUrlResolver.getMediaType(videoUri.toString()));
        installBroadcastReceivers();

        // Check if we are reconnecting or have reconnected and are playing the same video
        if ((mReconnecting || mCurrentSessionId != null)
                && videoUri.toString().equals(RemotePlaybackSettings.getUriPlaying(getContext()))) {
            return;
        }

        // If the session is already started (meaning we are casting a video already), we simply
        // load the new URL with one ACTION_PLAY intent.
        if (mCurrentSessionId != null) {
            if (mDebug) Log.d(TAG, "Playing a new url: " + videoUri);

            RemotePlaybackSettings.setUriPlaying(getContext(), videoUri.toString());

            // We keep the same session so only clear the playing item status.
            clearItemState();
            startPlayback(videoUri, preferredTitle, startPositionMillis);
            return;
        }

        RemotePlaybackSettings.setPlayerInUse(getContext(), getCastReceiverId());
        if (mDebug) {
            Log.d(TAG, "Sending stream to app: " + getCastReceiverId());
            Log.d(TAG, "Url: " + videoUri);
        }

        startSession(true, null, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                configureNewSession(data);

                mVideoUriToStart = videoUri;
                RemotePlaybackSettings.setUriPlaying(getContext(), videoUri.toString());
                mPreferredTitle = preferredTitle;
                mStartPositionMillis = startPositionMillis;
                // Make sure we get a session status. If the session becomes active
                // immediately then the broadcast session status can arrive before we have
                // the session id, so this ensures we get it whatever happens.
                getSessionStatus(mCurrentSessionId);
            }

            @Override
            public void onError(String message, Bundle data) {
                release();
                RecordCastAction.castDefaultPlayerResult(false);
            }
        });
    }

    /**
     * Send a start session intent.
     *
     * @param relaunch Whether we should relaunch the cast application.
     * @param resultBundleHandler BundleHandler to handle reply.
     */
    private void startSession(boolean relaunch, String sessionId,
            ResultBundleHandler resultBundleHandler) {
        Intent intent = new Intent(MediaControlIntent.ACTION_START_SESSION);
        intent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);

        intent.putExtra(CastMediaControlIntent.EXTRA_CAST_STOP_APPLICATION_WHEN_SESSION_ENDS, true);
        intent.putExtra(MediaControlIntent.EXTRA_SESSION_STATUS_UPDATE_RECEIVER,
                mSessionStatusUpdateIntent);
        intent.putExtra(CastMediaControlIntent.EXTRA_CAST_APPLICATION_ID, getCastReceiverId());
        intent.putExtra(CastMediaControlIntent.EXTRA_CAST_RELAUNCH_APPLICATION, relaunch);
        if (sessionId != null) intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, sessionId);

        if (mDebug) intent.putExtra(CastMediaControlIntent.EXTRA_DEBUG_LOGGING_ENABLED, true);

        sendIntentToRoute(intent, resultBundleHandler);
    }

    private void getSessionStatus(String sessionId) {
        Intent intent = new Intent(MediaControlIntent.ACTION_GET_SESSION_STATUS);
        intent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);

        intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, sessionId);

        sendIntentToRoute(intent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                if (mDebug) Log.d(TAG, "getSessionStatus result : " + bundleToString(data));

                processSessionStatusBundle(data);
            }

            @Override
            public void onError(String message, Bundle data) {
                release();
            }
        });
    }

    private void startPlayback(final Uri videoUri, @Nullable final String preferredTitle,
            final long startPositionMillis) {
        setUnprepared();
        Intent intent = new Intent(MediaControlIntent.ACTION_PLAY);
        intent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        intent.setDataAndType(videoUri, MIME_TYPE);
        intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, mCurrentSessionId);
        intent.putExtra(MediaControlIntent.EXTRA_ITEM_STATUS_UPDATE_RECEIVER,
                mMediaStatusUpdateIntent);
        intent.putExtra(MediaControlIntent.EXTRA_ITEM_CONTENT_POSITION, startPositionMillis);

        if (preferredTitle != null) {
            Bundle metadata = new Bundle();
            metadata.putString(MediaItemMetadata.KEY_TITLE, preferredTitle);
            intent.putExtra(MediaControlIntent.EXTRA_ITEM_METADATA, metadata);
        }

        sendIntentToRoute(intent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                mCurrentItemId = data.getString(MediaControlIntent.EXTRA_ITEM_ID);
                processMediaStatusBundle(data);
                RecordCastAction.castDefaultPlayerResult(true);
            }

            @Override
            public void onError(String message, Bundle data) {
                release();
                RecordCastAction.castDefaultPlayerResult(false);
            }
        });
    }

    @Override
    public int getPosition() {
        boolean paused = (getPlayerState() != PlayerState.PLAYING);
        if ((mStreamPositionTimestamp != 0) && !mSeeking && !paused
                && (mLastKnownStreamPosition < mStreamDuration)) {

            long extrapolatedStreamPosition = mLastKnownStreamPosition
                    + (SystemClock.uptimeMillis() - mStreamPositionTimestamp);
            if (extrapolatedStreamPosition > mStreamDuration) {
                extrapolatedStreamPosition = mStreamDuration;
            }
            return (int) extrapolatedStreamPosition;
        }
        return mLastKnownStreamPosition;
    }

    @Override
    public int getDuration() {
        return mStreamDuration;
    }

    @Override
    public void seekTo(int msec) {
        if (msec == getPosition()) return;
        // Update the position now since the MRP will update it only once the video is playing
        // remotely. In particular, if the video is paused, the MRP doesn't send the command until
        // the video is resumed.
        mLastKnownStreamPosition = msec;
        mSeeking = true;
        Intent intent = new Intent(MediaControlIntent.ACTION_SEEK);
        intent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, mCurrentSessionId);
        intent.putExtra(MediaControlIntent.EXTRA_ITEM_ID, mCurrentItemId);
        intent.putExtra(MediaControlIntent.EXTRA_ITEM_CONTENT_POSITION, (long) msec);
        sendIntentToRoute(intent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                if (getMediaStateListener() != null) getMediaStateListener().onSeekCompleted();
                processMediaStatusBundle(data);
            }

            @Override
            public void onError(String message, Bundle data) {
                release();
            }
        });
    }

    @Override
    public void release() {
        for (UiListener listener : getUiListeners()) {
            listener.onRouteUnselected(this);
        }
        if (getMediaStateListener() != null) getMediaStateListener().onRouteUnselected();
        setMediaStateListener(null);

        stopAndDisconnect();
    }

    /**
     * Stop the current remote playback and release all associated resources. Resources will be
     * released even if the stop operation fails.
     */
    private void stopAndDisconnect() {
        if (mediaRouterInitializationFailed()) return;
        if (mCurrentSessionId == null) {
            // This can happen if we disconnect after a failure (because the
            // media could not be casted).
            disconnect(true);
            return;
        }

        Intent stopIntent = new Intent(MediaControlIntent.ACTION_STOP);
        stopIntent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        stopIntent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, mCurrentSessionId);

        sendIntentToRoute(stopIntent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                processMediaStatusBundle(data);
            }

            @Override
            public void onError(String message, Bundle data) {}
        });

        Intent endSessionIntent = new Intent(MediaControlIntent.ACTION_END_SESSION);
        endSessionIntent.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        endSessionIntent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, mCurrentSessionId);

        sendIntentToRoute(endSessionIntent, new ResultBundleHandler() {
            @Override
            public void onResult(Bundle data) {
                if (mDebug) {
                    MediaSessionStatus status = MediaSessionStatus.fromBundle(
                            data.getBundle(MediaControlIntent.EXTRA_SESSION_STATUS));
                    int sessionState = status.getSessionState();
                    Log.d(TAG, "Session state after ending session: " + sessionState);
                }

                for (UiListener listener : getUiListeners()) {
                    listener.onPlaybackStateChanged(getPlayerState(), PlayerState.FINISHED);
                }

                if (getMediaStateListener() != null) {
                    getMediaStateListener().onPlaybackStateChanged(PlayerState.FINISHED);
                }
                RecordCastAction.castEndedTimeRemaining(mStreamDuration,
                        mStreamDuration - getPosition());
                disconnect(true);
            }

            @Override
            public void onError(String message, Bundle data) {
                disconnect(true);
            }
        });
    }

    /**
     * Disconnect from the remote screen without stopping the media playing. use release() for
     * disconnect + stop.
     *
     * @param finishedWithRoute true if finished with route and remote device, false if just
     *        shutting down Chrome.
     */
    private void disconnect(boolean finishedWithRoute) {
        if (finishedWithRoute) {
            clearStreamState();
            clearMediaRoute();
        }

        if (mSessionStatusBroadcastReceiver != null) {
            getContext().unregisterReceiver(mSessionStatusBroadcastReceiver);
            mSessionStatusBroadcastReceiver = null;
        }
        if (mMediaStatusBroadcastReceiver != null) {
            getContext().unregisterReceiver(mMediaStatusBroadcastReceiver);
            mMediaStatusBroadcastReceiver = null;
        }
        clearConnectionFailureCallback();

        stopWatchingRouteSelection();
        removeAllListeners();
    }

    @Override
    protected void onRouteAddedEvent(MediaRouter router, RouteInfo route) {
        if (mDebug) Log.d(TAG, "Added route " + route);
        if (getDeviceId() != null && getDeviceId().equals(route.getId())) {
            // This is the route we are waiting to connect to, select it.
            if (mDebug) Log.d(TAG, "Selecting Added Device " + route.getName());
            route.select();
        }
    }

    @Override
    protected void onRouteSelectedEvent(MediaRouter router, RouteInfo route) {
        if (mDebug) Log.d(TAG, "Selected route " + route);
        if (!route.isSelected()) return;

        RecordCastAction.remotePlaybackDeviceSelected(
                RecordCastAction.DEVICE_TYPE_CAST_GENERIC);
        installBroadcastReceivers();

        if (getMediaStateListener() == null) {
            showCastError(route.getName());
            release();
            return;
        }

        registerRoute(route);
        if (shouldReconnect()) {
            startSession(false, RemotePlaybackSettings.getSessionId(getContext()),
                    new ResultBundleHandler() {
                        @Override
                        public void onResult(Bundle data) {
                            configureNewSession(data);
                            setUnprepared();
                            mReconnecting = false;
                            // Make sure we get a session status. If the session becomes active
                            // immediately then the broadcast session status can arrive before we
                            // have the session id, so this ensures we get it whatever happens.
                            getSessionStatus(mCurrentSessionId);
                        }

                        @Override
                        public void onError(String message, Bundle data) {
                            // Ignore errors, the connection sometimes is bouncy on reconnection,
                            // and the reconnection timer is still running so will tidy up if
                            // we never manage to connect.
                        }
                    });
        } else {
            clearStreamState();
            mReconnecting = false;
        }

        notifyRouteSelected(route);
    }

    /*
     * Although our custom implementation of the disconnect button doesn't need this, it is
     * needed when the route is released due to, for example, another application stealing the
     * route, or when we switch to a YouTube video on the same device.
     */
    @Override
    protected void onRouteUnselectedEvent(MediaRouter router, RouteInfo route) {
        if (mDebug) Log.d(TAG, "Unselected route " + route);
        // Preserve our best guess as to the final position; this is needed to reset the
        // local position while switching back to local playback.
        mLastKnownStreamPosition = getPosition();
        if (getCurrentRoute() != null && route.getId().equals(getCurrentRoute().getId())) {
            clearStreamState();
        }
    }

    private void installBroadcastReceivers() {
        if (mSessionStatusBroadcastReceiver == null) {
            mSessionStatusBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mDebug) {
                        dumpIntentToLog("Got a session broadcast intent from the MRP: ", intent);
                    }
                    Bundle statusBundle = intent.getExtras();

                    // Ignore null status bundles.
                    if (statusBundle == null) return;

                    // Ignore the status of old sessions.
                    String sessionId = statusBundle.getString(MediaControlIntent.EXTRA_SESSION_ID);
                    if (mCurrentSessionId == null || !mCurrentSessionId.equals(sessionId)) return;

                    processSessionStatusBundle(statusBundle);
                }
            };
            IntentFilter sessionBroadcastIntentFilter =
                    new IntentFilter(ACTION_RECEIVE_SESSION_STATUS_UPDATE);
            sessionBroadcastIntentFilter.addCategory(mIntentCategory);
            getContext().registerReceiver(mSessionStatusBroadcastReceiver,
                    sessionBroadcastIntentFilter);
        }

        if (mMediaStatusBroadcastReceiver == null) {
            mMediaStatusBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mDebug) dumpIntentToLog("Got a broadcast intent from the MRP: ", intent);

                    processMediaStatusBundle(intent.getExtras());
                }
            };
            IntentFilter mediaBroadcastIntentFilter =
                    new IntentFilter(ACTION_RECEIVE_MEDIA_STATUS_UPDATE);
            mediaBroadcastIntentFilter.addCategory(mIntentCategory);
            getContext().registerReceiver(mMediaStatusBroadcastReceiver,
                    mediaBroadcastIntentFilter);
        }
    }

    /**
     * Called when the main activity receives an onDestroy() call.
     */
    protected void onActivitiesDestroyed() {
        ApplicationStatus.unregisterApplicationStateListener(mApplicationStateListener);
        // It is important to not clear the stream state here to let Chrome
        // reconnect to a session upon startup.
        disconnect(false);
    }

    /**
     * Clear the session and the currently playing item (if any).
     */
    protected void clearStreamState() {
        mVideoUriToStart = null;
        mLocalVideoUri = null;
        mCurrentSessionId = null;
        clearItemState();

        if (getContext() != null) {
            RemotePlaybackSettings.setShouldReconnectToRemote(getContext(), false);
            RemotePlaybackSettings.setUriPlaying(getContext(), null);
        }
    }

    @Override
    protected void clearItemState() {
        // Note: do not clear the stream position, since this is still needed so
        // that we can reset the local stream position to match.
        super.clearItemState();
        mCurrentItemId = null;
        mStreamPositionTimestamp = 0;
        mStreamDuration = 0;
        mSeeking = false;
    }

    private void syncStatus(String sessionId, ResultBundleHandler bundleHandler) {
        if (sessionId == null) return;
        Intent intent = new Intent(CastMediaControlIntent.ACTION_SYNC_STATUS);
        intent.addCategory(CastMediaControlIntent.categoryForRemotePlayback());
        intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, sessionId);
        intent.putExtra(MediaControlIntent.EXTRA_ITEM_STATUS_UPDATE_RECEIVER,
                mMediaStatusUpdateIntent);
        sendIntentToRoute(intent, bundleHandler);
    }

    private void processSessionStatusBundle(Bundle statusBundle) {
        MediaSessionStatus status = MediaSessionStatus.fromBundle(
                statusBundle.getBundle(MediaControlIntent.EXTRA_SESSION_STATUS));
        int sessionState = status.getSessionState();

        // If no change do nothing
        if (sessionState == mSessionState) return;
        mSessionState  = sessionState;

        switch (sessionState) {
            case MediaSessionStatus.SESSION_STATE_ACTIVE:
                // TODO(aberent): This should not be needed. Remove this once b/12921924 is fixed.
                syncStatus(mCurrentSessionId, new ResultBundleHandler() {
                    @Override
                    public void onResult(Bundle data) {
                        processMediaStatusBundle(data);
                        if (mVideoUriToStart != null) {
                            startPlayback(mVideoUriToStart, mPreferredTitle, mStartPositionMillis);
                            mVideoUriToStart = null;
                        }
                    }

                    @Override
                    public void onError(String message, Bundle data) {
                        release();
                    }
                });
                break;

            case MediaSessionStatus.SESSION_STATE_ENDED:
            case MediaSessionStatus.SESSION_STATE_INVALIDATED:
                for (UiListener listener : getUiListeners()) {
                    listener.onPlaybackStateChanged(getPlayerState(), PlayerState.INVALIDATED);
                }
                if (getMediaStateListener() != null) {
                    getMediaStateListener().onPlaybackStateChanged(PlayerState.INVALIDATED);
                }
                // Set the current session id to null so we don't send the stop intent.
                mCurrentSessionId = null;
                release();
                break;

            default:
                break;
        }
    }

    private void processMediaStatusBundle(Bundle statusBundle) {
        if (statusBundle == null) return;

        if (mDebug) Log.d(TAG, "processMediaStatusBundle: " + bundleToString(statusBundle));

        String itemId = statusBundle.getString(MediaControlIntent.EXTRA_ITEM_ID);
        if (itemId == null || !itemId.equals(mCurrentItemId)) return;

        // Extract item metadata, if available.
        if (statusBundle.containsKey(MediaControlIntent.EXTRA_ITEM_METADATA)) {
            Bundle metadataBundle =
                    (Bundle) statusBundle.getParcelable(MediaControlIntent.EXTRA_ITEM_METADATA);
            updateTitle(metadataBundle.getString(MediaItemMetadata.KEY_TITLE));
        }

        // Extract the item status, if available.
        if (statusBundle.containsKey(MediaControlIntent.EXTRA_ITEM_STATUS)) {
            Bundle itemStatusBundle =
                    (Bundle) statusBundle.getParcelable(MediaControlIntent.EXTRA_ITEM_STATUS);
            MediaItemStatus itemStatus = MediaItemStatus.fromBundle(itemStatusBundle);

            if (mDebug) Log.d(TAG, "Received item status: " + bundleToString(itemStatusBundle));

            updateState(itemStatus.getPlaybackState());

            if ((getPlayerState() == PlayerState.PAUSED)
                    || (getPlayerState() == PlayerState.PLAYING)
                    || (getPlayerState() == PlayerState.LOADING)) {

                this.mCurrentItemId = itemId;

                int duration = (int) itemStatus.getContentDuration();
                // duration can possibly be -1 if it's unknown, so cap to 0
                updateDuration(Math.max(duration, 0));

                // update the position using the remote player's position
                mLastKnownStreamPosition = (int) itemStatus.getContentPosition();
                mStreamPositionTimestamp = (int) itemStatus.getTimestamp();
                updatePosition();

                if (mSeeking) {
                    mSeeking = false;
                    if (getMediaStateListener() != null) getMediaStateListener().onSeekCompleted();
                }
            }

            Bundle extras = itemStatus.getExtras();
            if (mDebug && extras != null) {
                if (extras.containsKey(MediaItemStatus.EXTRA_HTTP_STATUS_CODE)) {
                    int httpStatus = extras.getInt(MediaItemStatus.EXTRA_HTTP_STATUS_CODE);
                    Log.d(TAG, "HTTP status: " + httpStatus);
                }
                if (extras.containsKey(MediaItemStatus.EXTRA_HTTP_RESPONSE_HEADERS)) {
                    Bundle headers = extras.getBundle(MediaItemStatus.EXTRA_HTTP_RESPONSE_HEADERS);
                    Log.d(TAG, "HTTP headers: " + headers);
                }
            }
        }
    }

    /**
     * Send the given intent to the current route. The result will be returned in the given
     * ResultBundleHandler. This function will also check to see if the current route can handle the
     * intent before sending it.
     *
     * @param intent the intent to send to the current route.
     * @param bundleHandler contains the result of sending the intent
     */
    private void sendIntentToRoute(final Intent intent, final ResultBundleHandler bundleHandler) {
        if (getCurrentRoute() == null) {
            if (mDebug) {
                dumpIntentToLog("sendIntentToRoute ", intent);
                Log.d(TAG, "The current route is null.");
            }
            if (bundleHandler != null) bundleHandler.onError(null, null);
            return;
        }

        if (!getCurrentRoute().supportsControlRequest(intent)) {
            if (mDebug) {
                dumpIntentToLog("sendIntentToRoute ", intent);
                Log.d(TAG, "The intent is not supported by the route: " + getCurrentRoute());
            }
            if (bundleHandler != null) bundleHandler.onError(null, null);
            return;
        }

        sendControlIntent(intent, bundleHandler);
    }

    private void sendControlIntent(final Intent intent, final ResultBundleHandler bundleHandler) {

        if (mDebug) {
            Log.d(TAG,
                    "Sending intent to " + getCurrentRoute().getName() + " "
                    + getCurrentRoute().getId());
            dumpIntentToLog("sendControlIntent ", intent);
        }
        if (getCurrentRoute().isDefault()) {
            if (mDebug) Log.d(TAG, "Route is default, not sending");
            return;
        }

        getCurrentRoute().sendControlRequest(intent, new MediaRouter.ControlRequestCallback() {
            @Override
            public void onResult(Bundle data) {
                if (data != null && bundleHandler != null) bundleHandler.onResult(data);
            }

            @Override
            public void onError(String message, Bundle data) {
                if (mDebug) {
                    // The intent may contain some PII so we don't want to log it in the released
                    // version by default.
                    Log.e(TAG, String.format(
                            "Error sending control request %s %s. Data: %s Error: %s", intent,
                            bundleToString(intent.getExtras()), bundleToString(data), message));
                }

                int errorCode = 0;
                if (data != null) {
                    errorCode = data.getInt(CastMediaControlIntent.EXTRA_ERROR_CODE);
                }

                sendErrorToListeners(errorCode);

                if (bundleHandler != null) bundleHandler.onError(message, data);
            }
        });
    }

    private void updateDuration(int durationMillis) {
        mStreamDuration = durationMillis;

        for (UiListener listener : getUiListeners()) {
            listener.onDurationUpdated(durationMillis);
        }
    }

    private void updatePosition() {
        for (UiListener listener : getUiListeners()) {
            listener.onPositionChanged(getPosition());
        }
    }

    private void dumpIntentToLog(String prefix, Intent intent) {
        Log.d(TAG, prefix + intent + " extras: " + bundleToString(intent.getExtras()));
    }

    private String bundleToString(Bundle bundle) {
        if (bundle == null) return "";

        StringBuilder extras = new StringBuilder();
        extras.append("[");
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            String valueText = value == null ? "null" : value.toString();
            if (value instanceof Bundle) valueText = bundleToString((Bundle) value);
            extras.append(key).append("=").append(valueText).append(",");
        }
        extras.append("]");
        return extras.toString();
    }

    @Override
    public void setDataSource(Uri uri, String cookies, String userAgent) {
        if (mDebug) Log.d(TAG, "setDataSource called, uri = " + uri);
        mLocalVideoUri = uri;
        mLocalVideoCookies = cookies;
        mUserAgent = userAgent;
    }

    @Override
    public void prepareAsync(String frameUrl, long startPositionMillis) {
        if (mDebug) Log.d(TAG, "prepareAsync called, mLocalVideoUri = " + mLocalVideoUri);
        if (mLocalVideoUri == null) return;

        RecordCastAction.castPlayRequested();

        // Cancel the previous task for URL resolving so that we don't get an old URI set.
        if (mMediaUrlResolver != null) mMediaUrlResolver.cancel(true);

        // Create a new MediaUrlResolver since the previous one may still be running despite the
        // cancel() call.
        mMediaUrlResolver = new MediaUrlResolver(mMediaUrlResolverDelegate, mUserAgent);

        mStartPositionMillis = startPositionMillis;
        mMediaUrlResolver.execute();
    }

    private void playMedia() {
        String title = null;
        if (getMediaStateListener() != null) title = getMediaStateListener().getTitle();
        playUri(mLocalVideoUri, title, mStartPositionMillis);
    }

    private void showMessageToast(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void configureNewSession(Bundle data) {
        mCurrentSessionId = data.getString(MediaControlIntent.EXTRA_SESSION_ID);
        mSessionState = MediaSessionStatus.SESSION_STATE_INVALIDATED;
        RemotePlaybackSettings.setSessionId(getContext(), mCurrentSessionId);
        if (mDebug) Log.d(TAG, "Got a session id: " + mCurrentSessionId);
    }
}
