// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.support.v7.media.MediaRouter;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;

/**
 * An implementation of {@link LockScreenTransportControl} targeting platforms with an API greater
 * than 15 (Jelly Bean, the minimum API version supported by Chrome).
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
//TODO(aberent) This whole class is based upon RemoteControlClient, which is deprecated in
//the Android L SDK. It, however, still seems to be the only way of controlling the
//lock screen wallpaper. We need to investigate whether there is an alternative. See
//LockScreenTransportControl.java.
@SuppressWarnings("deprecation")
class LockScreenTransportControlV16 extends LockScreenTransportControl {

    private final MediaRouter mMediaRouter;
    private final AudioManager mAudioManager;
    private final PendingIntent mMediaPendingIntent;
    private final ComponentName mMediaEventReceiver;
    private final AudioFocusListener mAudioFocusListener;
    private android.media.RemoteControlClient mRemoteControlClient;
    private boolean mIsPlaying;

    private boolean mDebug;
    private static final String TAG = "LockScreenTransportControlV14";

    static class AudioFocusListener implements OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {
            // Do nothing, the listener is only used to later abandon audio focus.
        }
    }

    LockScreenTransportControlV16(Context context) {
        mDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mMediaEventReceiver = new ComponentName(context.getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mMediaEventReceiver);
        mMediaPendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0,
                mediaButtonIntent, 0);
        mAudioFocusListener = new AudioFocusListener();
        mMediaRouter = MediaRouter.getInstance(context);
    }

    @Override
    public void onErrorChanged() {
        if (hasError()) updatePlaybackState(android.media.RemoteControlClient.PLAYSTATE_ERROR);
    }

    @Override
    public void onLockScreenPlaybackStateChanged(PlayerState oldState, PlayerState newState) {
        if (mDebug) Log.d(TAG, "onLockScreenPlaybackStateChanged - new state: " + newState);
        int playbackState = android.media.RemoteControlClient.PLAYSTATE_STOPPED;
        boolean shouldBeRegistered = false;
        if (newState != null) {
            mIsPlaying = false;
            shouldBeRegistered = true;
            switch (newState) {
                case PAUSED:
                    playbackState = android.media.RemoteControlClient.PLAYSTATE_PAUSED;
                    break;
                case ERROR:
                    playbackState = android.media.RemoteControlClient.PLAYSTATE_ERROR;
                    break;
                case PLAYING:
                    playbackState = android.media.RemoteControlClient.PLAYSTATE_PLAYING;
                    mIsPlaying = true;
                    break;
                case LOADING:
                    playbackState = android.media.RemoteControlClient.PLAYSTATE_BUFFERING;
                    break;
                default:
                    shouldBeRegistered = false;
                    break;
            }
        }

        boolean registered = (mRemoteControlClient != null);
        if (registered != shouldBeRegistered) {
            if (shouldBeRegistered) {
                register();
                onVideoInfoChanged();
                onPosterBitmapChanged();
            } else {
                unregister();
            }
        }

        updatePlaybackState(playbackState);
    }

    @Override
    public void onVideoInfoChanged() {
        if (mRemoteControlClient == null) return;

        RemoteVideoInfo videoInfo = getVideoInfo();

        String title = null;
        long duration = 0;
        if (videoInfo != null) {
            title = videoInfo.title;
            duration = videoInfo.durationMillis;
        }

        android.media.RemoteControlClient.MetadataEditor editor = mRemoteControlClient.editMetadata(
                true);
        editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title);
        editor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration);
        updateBitmap(editor);
        editor.apply();
    }

    @Override
    public void onPosterBitmapChanged() {
        if (mRemoteControlClient == null) return;

        android.media.RemoteControlClient.MetadataEditor editor = mRemoteControlClient.editMetadata(
                false);
        updateBitmap(editor);
        editor.apply();
    }

    private void updateBitmap(android.media.RemoteControlClient.MetadataEditor editor) {
        // RemoteControlClient likes to recycle bitmaps that have been passed to it through
        // BITMAP_KEY_ARTWORK. We can't go recycling bitmaps like this since they are also used by
        // {@link ExpandedControllerActivity} and their life cycle is controller by
        // {@link RemoteMediaPlayerController}. See crbug.com/356612
        Bitmap src = getPosterBitmap();
        Bitmap copy = src != null ? src.copy(src.getConfig(), true) : null;
        editor.putBitmap(android.media.RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, copy);
    }

    protected final android.media.RemoteControlClient getRemoteControlClient() {
        return mRemoteControlClient;
    }

    protected void register() {
        if (mDebug) Log.d(TAG, "register called");
        mRemoteControlClient = new android.media.RemoteControlClient(mMediaPendingIntent);
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.AUDIOFOCUS_GAIN);
        mAudioManager.registerMediaButtonEventReceiver(mMediaEventReceiver);
        mAudioManager.registerRemoteControlClient(mRemoteControlClient);
        mRemoteControlClient.setTransportControlFlags(getTransportControlFlags());
        mMediaRouter.addRemoteControlClient(getRemoteControlClient());
    }

    protected void unregister() {
        if (mDebug) Log.d(TAG, "unregister called");
        mMediaRouter.removeRemoteControlClient(getRemoteControlClient());
        mRemoteControlClient.editMetadata(true).apply();
        mRemoteControlClient.setTransportControlFlags(0);
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        mAudioManager.unregisterMediaButtonEventReceiver(mMediaEventReceiver);
        mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);
        mRemoteControlClient = null;
    }

    protected void updatePlaybackState(int state) {
        if (mRemoteControlClient != null) mRemoteControlClient.setPlaybackState(state);
    }

    protected int getTransportControlFlags() {
        return android.media.RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | android.media.RemoteControlClient.FLAG_KEY_MEDIA_STOP;
    }

    @Override
    public void onRouteSelected(String name, MediaRouteController mediaRouteController) {
        setScreenName(name);
    }

    @Override
    public void onRouteUnselected(MediaRouteController mediaRouteController) {
        hide();
    }

    @Override
    public void onPrepared(MediaRouteController mediaRouteController) {
    }

    @Override
    public void onError(int error, String errorMessage) {
        // Stop the session for all errors
        hide();
    }

    @Override
    public void onDurationUpdated(int durationMillis) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.durationMillis = durationMillis;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onPositionChanged(int positionMillis) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.currentTimeMillis = positionMillis;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onTitleChanged(String title) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.title = title;
        setVideoInfo(videoInfo);
    }

    @Override
    protected boolean isPlaying() {
        return mIsPlaying;
    }
}
