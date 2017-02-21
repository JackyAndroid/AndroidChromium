// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.TransportMediator;
import android.support.v4.media.TransportPerformer;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.cast.CastMediaControlIntent;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.chrome.browser.metrics.MediaNotificationUma;
import org.chromium.third_party.android.media.MediaController;

/**
 * The activity that's opened by clicking the video flinging (casting) notification.
 *
 * TODO(aberent): Refactor to merge some common logic with {@link CastNotificationControl}.
 */
public class ExpandedControllerActivity
        extends FragmentActivity implements MediaRouteController.UiListener {
    private static final int PROGRESS_UPDATE_PERIOD_IN_MS = 1000;
    // The alpha value for the poster/placeholder image, an integer between 0 and 256 (opaque).
    private static final int POSTER_IMAGE_ALPHA = 200;

    private Handler mHandler;
    // We don't use the standard android.media.MediaController, but a custom one.
    // See the class itself for details.
    private MediaController mMediaController;
    private FullscreenMediaRouteButton mMediaRouteButton;
    private MediaRouteController mMediaRouteController;
    private RemoteVideoInfo mVideoInfo;
    private String mScreenName;
    private TransportMediator mTransportMediator;

    /**
     * Handle actions from on-screen media controls.
     */
    private TransportPerformer mTransportPerformer = new TransportPerformer() {
        @Override
        public void onStart() {
            if (mMediaRouteController == null) return;
            mMediaRouteController.resume();
            RecordCastAction.recordFullscreenControlsAction(
                    RecordCastAction.FULLSCREEN_CONTROLS_RESUME,
                    mMediaRouteController.getMediaStateListener() != null);
        }

        @Override
        public void onStop() {
            if (mMediaRouteController == null) return;
            onPause();
            mMediaRouteController.release();
        }

        @Override
        public void onPause() {
            if (mMediaRouteController == null) return;
            mMediaRouteController.pause();
            RecordCastAction.recordFullscreenControlsAction(
                    RecordCastAction.FULLSCREEN_CONTROLS_PAUSE,
                    mMediaRouteController.getMediaStateListener() != null);
        }

        @Override
        public long onGetDuration() {
            if (mMediaRouteController == null) return 0;
            return mMediaRouteController.getDuration();
        }

        @Override
        public long onGetCurrentPosition() {
            if (mMediaRouteController == null) return 0;
            return mMediaRouteController.getPosition();
        }

        @Override
        public void onSeekTo(long pos) {
            if (mMediaRouteController == null) return;
            mMediaRouteController.seekTo(pos);
            RecordCastAction.recordFullscreenControlsAction(
                    RecordCastAction.FULLSCREEN_CONTROLS_SEEK,
                    mMediaRouteController.getMediaStateListener() != null);
        }

        @Override
        public boolean onIsPlaying() {
            if (mMediaRouteController == null) return false;
            return mMediaRouteController.isPlaying();
        }

        @Override
        public int onGetTransportControlFlags() {
            int flags = TransportMediator.FLAG_KEY_MEDIA_REWIND
                    | TransportMediator.FLAG_KEY_MEDIA_FAST_FORWARD;
            if (mMediaRouteController != null && mMediaRouteController.isPlaying()) {
                flags |= TransportMediator.FLAG_KEY_MEDIA_PAUSE;
            } else {
                flags |= TransportMediator.FLAG_KEY_MEDIA_PLAY;
            }
            return flags;
        }
    };

    private Runnable mProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mMediaRouteController.isPlaying()) {
                mMediaController.updateProgress();
                mHandler.postDelayed(this, PROGRESS_UPDATE_PERIOD_IN_MS);
            } else {
                mHandler.removeCallbacks(this);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MediaNotificationUma.recordClickSource(getIntent());

        mMediaRouteController =
                RemoteMediaPlayerController.instance().getCurrentlyPlayingMediaRouteController();

        if (mMediaRouteController == null || mMediaRouteController.routeIsDefaultRoute()) {
            // We don't want to do anything for the default (local) route
            finish();
            return;
        }

        // Make the activity full screen.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // requestWindowFeature must be called before adding content.
        setContentView(R.layout.expanded_cast_controller);
        mHandler = new Handler();

        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        rootView.setBackgroundColor(Color.BLACK);

        mMediaRouteController.addUiListener(this);

        // Create transport controller to control video, giving the callback
        // interface to receive actions from.
        mTransportMediator = new TransportMediator(this, mTransportPerformer);

        // Create and initialize the media control UI.
        mMediaController = (MediaController) findViewById(R.id.cast_media_controller);
        mMediaController.setMediaPlayer(mTransportMediator);

        View button = getLayoutInflater().inflate(R.layout.cast_controller_media_route_button,
                rootView, false);

        if (button instanceof FullscreenMediaRouteButton) {
            mMediaRouteButton = (FullscreenMediaRouteButton) button;
            rootView.addView(mMediaRouteButton);
            mMediaRouteButton.bringToFront();
            mMediaRouteButton.initialize(mMediaRouteController);
        } else {
            mMediaRouteButton = null;
        }

        // Initialize the video info.
        setVideoInfo(new RemoteVideoInfo(null, 0, RemoteVideoInfo.PlayerState.STOPPED, 0, null));

        mMediaController.refresh();

        scheduleProgressUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoInfo.state == PlayerState.FINISHED) finish();
        if (mMediaRouteController == null) return;

        // Lifetime of the media element is bound to that of the {@link MediaStateListener}
        // of the {@link MediaRouteController}.
        RecordCastAction.recordFullscreenControlsShown(
                mMediaRouteController.getMediaStateListener() != null);

        mMediaRouteController.prepareMediaRoute();

        ImageView iv = (ImageView) findViewById(R.id.cast_background_image);
        if (iv == null) return;
        Bitmap posterBitmap = mMediaRouteController.getPoster();
        if (posterBitmap != null) iv.setImageBitmap(posterBitmap);
        iv.setImageAlpha(POSTER_IMAGE_ALPHA);
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if ((keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP)
                || mVideoInfo.state == PlayerState.FINISHED) {
            return super.dispatchKeyEvent(event);
        }

        return handleVolumeKeyEvent(mMediaRouteController, event);
    }

    private void cleanup() {
        if (mHandler != null) mHandler.removeCallbacks(mProgressUpdater);
        if (mMediaRouteController != null) mMediaRouteController.removeUiListener(this);
        mMediaRouteController = null;
        mProgressUpdater = null;
    }

    /**
     * Sets the remote's video information to display.
     */
    private final void setVideoInfo(RemoteVideoInfo videoInfo) {
        if ((mVideoInfo == null) ? (videoInfo == null) : mVideoInfo.equals(videoInfo)) return;

        mVideoInfo = videoInfo;
        onVideoInfoChanged();
    }

    private void scheduleProgressUpdate() {
        mHandler.removeCallbacks(mProgressUpdater);
        if (mMediaRouteController.isPlaying()) {
            mHandler.post(mProgressUpdater);
        }
    }

    /**
     * Sets the name to display for the device.
     */
    private void setScreenName(String screenName) {
        if (TextUtils.equals(mScreenName, screenName)) return;

        mScreenName = screenName;
        onScreenNameChanged();
    }

    private void onVideoInfoChanged() {
        updateUi();
    }

    private void onScreenNameChanged() {
        updateUi();
    }

    private void updateUi() {
        if (mMediaController == null || mMediaRouteController == null) return;

        String deviceName = mMediaRouteController.getRouteName();
        String castText = "";
        if (deviceName != null) {
            castText = getResources().getString(R.string.cast_casting_video, deviceName);
        }
        TextView castTextView = (TextView) findViewById(R.id.cast_screen_title);
        castTextView.setText(castText);

        mMediaController.refresh();
    }

    @Override
    public void onRouteSelected(String name, MediaRouteController mediaRouteController) {
        setScreenName(name);
    }

    @Override
    public void onRouteUnselected(MediaRouteController mediaRouteController) {
        finish();
    }

    @Override
    public void onPrepared(MediaRouteController mediaRouteController) {
        // No implementation.
    }

    @Override
    public void onError(int error, String message) {
        if (error == CastMediaControlIntent.ERROR_CODE_SESSION_START_FAILED) finish();
    }

    @Override
    public void onPlaybackStateChanged(PlayerState newState) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(mVideoInfo);
        videoInfo.state = newState;
        setVideoInfo(videoInfo);

        scheduleProgressUpdate();

        if (newState == PlayerState.FINISHED || newState == PlayerState.INVALIDATED) {
            // If we are switching to a finished state, stop the notifications.
            finish();
        }
    }

    @Override
    public void onDurationUpdated(long durationMillis) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(mVideoInfo);
        videoInfo.durationMillis = durationMillis;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onPositionChanged(long positionMillis) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(mVideoInfo);
        videoInfo.currentTimeMillis = positionMillis;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onTitleChanged(String title) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(mVideoInfo);
        videoInfo.title = title;
        setVideoInfo(videoInfo);
    }

    /**
     * Modify remote volume by handling volume keys.
     *
     * @param controller The remote controller through which the volume will be modified.
     * @param event The key event. Its keycode needs to be either {@code KEYCODE_VOLUME_DOWN} or
     *              {@code KEYCODE_VOLUME_UP} otherwise this method will return false.
     * @return True if the event is handled.
     */
    private boolean handleVolumeKeyEvent(MediaRouteController controller, KeyEvent event) {
        if (!controller.isBeingCast()) return false;

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        // Intercept the volume keys to affect only remote volume.
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) controller.setRemoteVolume(-1);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) controller.setRemoteVolume(1);
                return true;
            default:
                return false;
        }
    }

    /**
     * Launches the ExpandedControllerActivity as a new task.
     *
     * @param context the Context to start this activity within.
     */
    public static void startActivity(Context context) {
        if (context == null) return;

        Intent intent = new Intent(context, ExpandedControllerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
