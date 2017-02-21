// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.chrome.browser.media.ui.MediaNotificationInfo;
import org.chromium.chrome.browser.media.ui.MediaNotificationListener;
import org.chromium.chrome.browser.media.ui.MediaNotificationManager;
import org.chromium.chrome.browser.metrics.MediaNotificationUma;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.common.MediaMetadata;

import javax.annotation.Nullable;

/**
 * Notification control controls the cast notification and lock screen, using
 * {@link MediaNotificationManager}
 */
public class CastNotificationControl implements MediaRouteController.UiListener,
        MediaNotificationListener, AudioManager.OnAudioFocusChangeListener {

    private static CastNotificationControl sInstance;

    private Bitmap mPosterBitmap;
    protected MediaRouteController mMediaRouteController = null;
    private MediaNotificationInfo.Builder mNotificationBuilder;
    private Context mContext;
    private PlayerState mState;
    private String mTitle = "";
    private AudioManager mAudioManager;

    private boolean mIsShowing = false;

    private static final Object LOCK = new Object();


    private CastNotificationControl(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }
    /**
     * Get the unique NotificationControl, creating it if necessary.
     * @param context The context of the activity
     * @param mediaRouteController The current mediaRouteController, if any.
     * @return a {@code LockScreenTransportControl} based on the platform's SDK API or null if the
     *         current platform's SDK API is not supported.
     */
    public static CastNotificationControl getOrCreate(Context context,
            @Nullable MediaRouteController mediaRouteController) {
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = new CastNotificationControl(context);
            }
            sInstance.setRouteController(mediaRouteController);
            return sInstance;
        }
    }

    /**
     * @return the poster bitmap previously assigned with {@link #setPosterBitmap(Bitmap)}, or
     * {@code null} if the poster bitmap has not yet been assigned.
     */
    public final Bitmap getPosterBitmap() {
        return mPosterBitmap;
    }

    /**
     * Sets the poster bitmap to display on the TransportControl.
     */
    public final void setPosterBitmap(Bitmap posterBitmap) {
        if (mPosterBitmap == posterBitmap
                || (mPosterBitmap != null && mPosterBitmap.sameAs(posterBitmap))) {
            return;
        }
        mPosterBitmap = posterBitmap;
        if (mNotificationBuilder == null || mMediaRouteController == null) return;
        mNotificationBuilder.setLargeIcon(mMediaRouteController.getPoster());
        updateNotification();
    }

    public void hide() {
        mIsShowing = false;
        MediaNotificationManager.hide(Tab.INVALID_TAB_ID, R.id.remote_notification);
        mAudioManager.abandonAudioFocus(this);
        mMediaRouteController.removeUiListener(this);
    }

    public void show(PlayerState initialState) {
        mMediaRouteController.addUiListener(this);
        // TODO(aberent): investigate why this is necessary, and whether we are handling
        // it correctly. Also add code to restore it when Chrome is resumed.
        mAudioManager.requestAudioFocus(this, AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.AUDIOFOCUS_GAIN);
        Intent contentIntent = new Intent(mContext, ExpandedControllerActivity.class);
        contentIntent.putExtra(MediaNotificationUma.INTENT_EXTRA_NAME,
                MediaNotificationUma.SOURCE_MEDIA_FLING);
        mNotificationBuilder = new MediaNotificationInfo.Builder()
                .setPaused(false)
                .setPrivate(false)
                .setIcon(R.drawable.ic_notification_media_route)
                .setContentIntent(contentIntent)
                .setLargeIcon(mMediaRouteController.getPoster())
                .setDefaultLargeIcon(R.drawable.cast_playing_square)
                .setId(R.id.remote_notification)
                .setListener(this);
        mState = initialState;
        updateNotification();
        mIsShowing = true;
    }

    public void setRouteController(MediaRouteController controller) {
        if (mMediaRouteController != null) mMediaRouteController.removeUiListener(this);
        mMediaRouteController = controller;
        if (controller != null) controller.addUiListener(this);
    }

    private void updateNotification() {
        // Nothing shown yet, nothing to update.
        if (mNotificationBuilder == null) return;

        mNotificationBuilder.setMetadata(new MediaMetadata(mTitle, "", ""));
        if (mState == PlayerState.PAUSED || mState == PlayerState.PLAYING) {
            mNotificationBuilder.setPaused(mState != PlayerState.PLAYING);
            mNotificationBuilder.setActions(MediaNotificationInfo.ACTION_STOP
                    | MediaNotificationInfo.ACTION_PLAY_PAUSE);
            MediaNotificationManager.show(mContext, mNotificationBuilder.build());
        } else if (mState == PlayerState.LOADING) {
            mNotificationBuilder.setActions(MediaNotificationInfo.ACTION_STOP);
            MediaNotificationManager.show(mContext, mNotificationBuilder.build());
        } else {
            hide();
        }
    }

    // TODO(aberent) at the moment this is only called from a test, but it should be called if the
    // poster changes.
    public void onPosterBitmapChanged() {
        if (mNotificationBuilder == null || mMediaRouteController == null) return;
        mNotificationBuilder.setLargeIcon(mMediaRouteController.getPoster());
        updateNotification();
    }

    // MediaRouteController.UiListener implementation.
    @Override
    public void onPlaybackStateChanged(PlayerState newState) {
        if (!mIsShowing
                && (newState == PlayerState.PLAYING || newState == PlayerState.LOADING
                        || newState == PlayerState.PAUSED)) {
            show(newState);
            return;
        }

        if (mState == newState
                || mState == PlayerState.PAUSED && newState == PlayerState.LOADING && mIsShowing) {
            return;
        }

        mState = newState;
        updateNotification();
    }

    @Override
    public void onRouteSelected(String name, MediaRouteController mediaRouteController) {
    }

    @Override
    public void onRouteUnselected(MediaRouteController mediaRouteController) {
        hide();
    }

    @Override
    public void onPrepared(MediaRouteController mediaRouteController) {
    }

    @Override
    public void onError(int errorType, String message) {
    }

    @Override
    public void onDurationUpdated(long durationMillis) {
    }

    @Override
    public void onPositionChanged(long positionMillis) {
    }

    @Override
    public void onTitleChanged(String title) {
        if (title == null || title.equals(mTitle)) return;

        mTitle = title;
        updateNotification();
    }

    // MediaNotificationListener methods
    @Override
    public void onPlay(int actionSource) {
        mMediaRouteController.resume();
    }

    @Override
    public void onPause(int actionSource) {
        mMediaRouteController.pause();
    }

    @Override
    public void onStop(int actionSource) {
        mMediaRouteController.release();
    }

    // AudioManager.OnAudioFocusChangeListener methods
    @Override
    public void onAudioFocusChange(int focusChange) {
        // Do nothing. The remote player should keep playing.
    }

    @VisibleForTesting
    static CastNotificationControl getForTests() {
        return sInstance;
    }

    @VisibleForTesting
    boolean isShowingForTests() {
        return mIsShowing;
    }
}
