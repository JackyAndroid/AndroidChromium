// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.ui.MediaNotificationInfo;
import org.chromium.chrome.browser.media.ui.MediaNotificationListener;
import org.chromium.chrome.browser.media.ui.MediaNotificationManager;
import org.chromium.chrome.browser.metrics.MediaNotificationUma;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.common.MediaMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A wrapper around the established Cast application session.
 */
public class CastSessionImpl implements MediaNotificationListener, CastSession {
    private static final String TAG = "MediaRouter";

    // The value is borrowed from the Android Cast SDK code to match their behavior.
    private static final double MIN_VOLUME_LEVEL_DELTA = 1e-7;

    private static class CastMessagingChannel implements Cast.MessageReceivedCallback {
        private final CastSession mSession;

        public CastMessagingChannel(CastSessionImpl session) {
            mSession = session;
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "Received message from Cast device: namespace=\"" + namespace
                       + "\" message=\"" + message + "\"");
            mSession.getMessageHandler().onMessageReceived(namespace, message);
        }
    }

    private final CastMessagingChannel mMessageChannel;
    private final CastDevice mCastDevice;
    private final MediaSource mSource;
    private final CastMessageHandler mMessageHandler;
    private final CastMediaRouteProvider mRouteProvider;

    private GoogleApiClient mApiClient;
    private String mSessionId;
    private String mApplicationStatus;
    private ApplicationMetadata mApplicationMetadata;
    private boolean mStoppingApplication;
    private MediaNotificationInfo.Builder mNotificationBuilder;
    private RemoteMediaPlayer mMediaPlayer;

    private Set<String> mNamespaces = new HashSet<String>();

    /**
     * Initializes a new {@link CastSessionImpl} instance.
     * @param apiClient The Google Play Services client used to create the session.
     * @param sessionId The session identifier to use with the Cast SDK.
     * @param origin The origin of the frame requesting the route.
     * @param tabId The id of the tab containing the frame requesting the route.
     * @param isIncognito Whether the route is beging requested from an Incognito profile.
     * @param source The {@link MediaSource} corresponding to this session.
     * @param routeProvider The {@link CastMediaRouteProvider} instance managing this session.
     */
    public CastSessionImpl(
            GoogleApiClient apiClient,
            String sessionId,
            ApplicationMetadata metadata,
            String applicationStatus,
            CastDevice castDevice,
            String origin,
            int tabId,
            boolean isIncognito,
            MediaSource source,
            CastMediaRouteProvider routeProvider) {
        mSessionId = sessionId;
        mRouteProvider = routeProvider;
        mApiClient = apiClient;
        mSource = source;
        mApplicationMetadata = metadata;
        mApplicationStatus = applicationStatus;
        mCastDevice = castDevice;
        mMessageHandler = mRouteProvider.getMessageHandler();
        mMessageChannel = new CastMessagingChannel(this);
        updateNamespaces();

        final Context context = ContextUtils.getApplicationContext();

        if (mNamespaces.contains(CastMessageHandler.MEDIA_NAMESPACE)) {
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
                            MediaNotificationManager.show(context, mNotificationBuilder.build());
                        }
                    });
            mMediaPlayer.setOnMetadataUpdatedListener(
                    new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                        @Override
                        public void onMetadataUpdated() {
                            setNotificationMetadata(mNotificationBuilder);
                            MediaNotificationManager.show(context, mNotificationBuilder.build());
                        }
                    });
        }

        Intent contentIntent = Tab.createBringTabToFrontIntent(tabId);
        if (contentIntent != null) {
            contentIntent.putExtra(MediaNotificationUma.INTENT_EXTRA_NAME,
                    MediaNotificationUma.SOURCE_PRESENTATION);
        }
        mNotificationBuilder = new MediaNotificationInfo.Builder()
                .setPaused(false)
                .setOrigin(origin)
                // TODO(avayvod): the same session might have more than one tab id. Should we track
                // the last foreground alive tab and update the notification with it?
                .setTabId(tabId)
                .setPrivate(isIncognito)
                .setActions(MediaNotificationInfo.ACTION_STOP)
                .setContentIntent(contentIntent)
                .setIcon(R.drawable.ic_notification_media_route)
                .setDefaultLargeIcon(R.drawable.cast_playing_square)
                .setId(R.id.presentation_notification)
                .setListener(this);
        setNotificationMetadata(mNotificationBuilder);
        MediaNotificationManager.show(context, mNotificationBuilder.build());
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
        stopApplication();
        mRouteProvider.onSessionStopAction();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Utility functions.

    /**
     * @param device The {@link CastDevice} queried for it's capabilities.
     * @return The capabilities of the Cast device.
     * TODO(zqzhang): move to a CastUtils class?
     */
    protected static List<String> getCapabilities(CastDevice device) {
        List<String> capabilities = new ArrayList<String>();
        if (device.hasCapability(CastDevice.CAPABILITY_AUDIO_IN)) {
            capabilities.add("audio_in");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)) {
            capabilities.add("audio_out");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_VIDEO_IN)) {
            capabilities.add("video_in");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)) {
            capabilities.add("video_out");
        }
        return capabilities;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Namespace handling.

    private void updateNamespaces() {
        if (mApplicationMetadata == null) return;

        List<String> newNamespaces = mApplicationMetadata.getSupportedNamespaces();

        Set<String> toRemove = new HashSet<String>(mNamespaces);
        toRemove.removeAll(newNamespaces);
        for (String namespaceToRemove : toRemove) unregisterNamespace(namespaceToRemove);

        for (String newNamespace : newNamespaces) {
            if (!mNamespaces.contains(newNamespace)) addNamespace(newNamespace);
        }
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
            mNamespaces.remove(namespace);
        } catch (IOException e) {
            Log.e(TAG, "Failed to remove the namespace listener for %s", namespace, e);
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // CastSession implementations.

    @Override
    public boolean isApiClientInvalid() {
        return mApiClient == null || !mApiClient.isConnected();
    }

    @Override
    public String getSourceId() {
        return mSource.getUrn();
    }

    @Override
    public String getSinkId() {
        return mCastDevice.getDeviceId();
    }

    @Override
    public String getSessionId() {
        return mSessionId;
    }

    @Override
    public Set<String> getNamespaces() {
        return mNamespaces;
    }

    @Override
    public CastMessageHandler getMessageHandler() {
        return mMessageHandler;
    }

    @Override
    public CastSessionInfo getSessionInfo() {
        if (isApiClientInvalid()) return null;

        CastSessionInfo.VolumeInfo.Builder volumeBuilder =
                new CastSessionInfo.VolumeInfo.Builder()
                        .setLevel(Cast.CastApi.getVolume(mApiClient))
                        .setMuted(Cast.CastApi.isMute(mApiClient));

        CastSessionInfo.ReceiverInfo.Builder receiverBuilder =
                new CastSessionInfo.ReceiverInfo.Builder()
                        .setLabel(mCastDevice.getDeviceId())
                        .setFriendlyName(mCastDevice.getFriendlyName())
                        .setVolume(volumeBuilder.build())
                        .setIsActiveInput(Cast.CastApi.getActiveInputState(mApiClient))
                        .setDisplayStatus(null)
                        .setReceiverType("cast")
                        .addCapabilities(getCapabilities(mCastDevice));

        CastSessionInfo.Builder sessionInfoBuilder =
                new CastSessionInfo.Builder()
                        .setSessionId(mSessionId)
                        .setStatusText(mApplicationStatus)
                        .setReceiver(receiverBuilder.build())
                        .setStatus("connected")
                        .setTransportId("web-4")
                        .addNamespaces(mNamespaces);

        if (mApplicationMetadata != null) {
            sessionInfoBuilder.setAppId(mApplicationMetadata.getApplicationId())
                    .setDisplayName(mApplicationMetadata.getName());
        } else {
            sessionInfoBuilder.setAppId(mSource.getApplicationId())
                    .setDisplayName(mCastDevice.getFriendlyName());
        }

        return sessionInfoBuilder.build();
    }

    @Override
    public boolean sendStringCastMessage(
            final String message,
            final String namespace,
            final String clientId,
            final int sequenceNumber) {
        if (isApiClientInvalid()) return false;
        Log.d(TAG, "Sending message to Cast device in namespace %s: %s", namespace, message);

        try {
            Cast.CastApi.sendMessage(mApiClient, namespace, message)
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
                                    if (CastMessageHandler.MEDIA_NAMESPACE
                                            .equals(namespace)) return;

                                    // App messages wait for the empty message with the sequence
                                    // number.
                                    mMessageHandler.onAppMessageSent(clientId, sequenceNumber);
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending message", e);
            return false;
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
    @Override
    public HandleVolumeMessageResult handleVolumeMessage(
            JSONObject volume, final String clientId, final int sequenceNumber)
            throws JSONException {
        if (volume == null) return new HandleVolumeMessageResult(false, false);

        if (isApiClientInvalid()) return new HandleVolumeMessageResult(false, false);

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
            return new HandleVolumeMessageResult(false, false);
        }

        return new HandleVolumeMessageResult(true, waitForVolumeChange);
    }

    @Override
    public void stopApplication() {
        if (mStoppingApplication) return;

        if (isApiClientInvalid()) return;

        mStoppingApplication = true;
        Cast.CastApi.stopApplication(mApiClient, mSessionId)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        mMessageHandler.onApplicationStopped();
                        // TODO(avayvod): handle a failure to stop the application.
                        // https://crbug.com/535577

                        Set<String> namespaces = new HashSet<String>(mNamespaces);
                        for (String namespace : namespaces) unregisterNamespace(namespace);
                        mNamespaces.clear();

                        mSessionId = null;
                        mApiClient = null;

                        mRouteProvider.onSessionClosed();
                        mStoppingApplication = false;

                        MediaNotificationManager.clear(R.id.presentation_notification);
                    }
                });
    }

    @Override
    public void onMediaMessage(String message) {
        if (mMediaPlayer != null) {
            mMediaPlayer.onMessageReceived(
                    mCastDevice, CastMessageHandler.MEDIA_NAMESPACE, message);
        }
    }

    @Override
    public void onVolumeChanged() {
        mMessageHandler.onVolumeChanged();
    }

    @Override
    public void updateSessionStatus() {
        if (isApiClientInvalid()) return;

        try {
            mApplicationStatus = Cast.CastApi.getApplicationStatus(mApiClient);
            mApplicationMetadata = Cast.CastApi.getApplicationMetadata(mApiClient);

            updateNamespaces();

            mMessageHandler.broadcastClientMessage(
                    "update_session", mMessageHandler.buildSessionMessage());
        } catch (IllegalStateException e) {
            Log.e(TAG, "Can't get application status", e);
        }
    }

    @Override
    public void onClientConnected(String clientId) {
        mMessageHandler.sendClientMessageTo(
                clientId, "new_session", mMessageHandler.buildSessionMessage(),
                CastMessageHandler.INVALID_SEQUENCE_NUMBER);

        if (mMediaPlayer != null && !isApiClientInvalid()) mMediaPlayer.requestStatus(mApiClient);
    }

    private void setNotificationMetadata(MediaNotificationInfo.Builder builder) {
        MediaMetadata notificationMetadata = new MediaMetadata("", "", "");
        builder.setMetadata(notificationMetadata);

        if (mCastDevice != null) notificationMetadata.setTitle(mCastDevice.getFriendlyName());

        if (mMediaPlayer == null) return;

        com.google.android.gms.cast.MediaInfo info = mMediaPlayer.getMediaInfo();
        if (info == null) return;

        com.google.android.gms.cast.MediaMetadata metadata = info.getMetadata();
        if (metadata == null) return;

        String title = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE);
        if (title != null) notificationMetadata.setTitle(title);

        String artist = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST);
        if (artist == null) {
            artist = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_ARTIST);
        }
        if (artist != null) notificationMetadata.setArtist(artist);

        String album = metadata.getString(
                com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE);
        if (album != null) notificationMetadata.setAlbum(album);
    }
}
