// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RemoteViews;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * A class for notifications that provide information and optional transport controls for a given
 * remote control. Internally implements a Service for transforming notification Intents into
 * {@link TransportControl.Listener} calls for all registered listeners.
 */
public class NotificationTransportControl
        extends TransportControl implements MediaRouteController.UiListener {
    /**
      * Service used to transform intent requests triggered from the notification into
      * {@code Listener} callbacks. Ideally this class should be protected, but public is required
      * to create as a service.
      */
    public static class ListenerService extends Service {
        private static final String ACTION_PREFIX = ListenerService.class.getName() + ".";

        // Constants used by intent actions
        public static final int ACTION_ID_PLAY = 0;
        public static final int ACTION_ID_PAUSE = 1;
        public static final int ACTION_ID_SEEK = 2;
        public static final int ACTION_ID_STOP = 3;
        public static final int ACTION_ID_SELECT = 4;

        // Intent parameters
        public static final String SEEK_POSITION = "SEEK_POSITION";

        // Must be kept in sync with the ACTION_ID_XXX constants above
        private static final String[] ACTION_VERBS = {"PLAY", "PAUSE", "SEEK", "STOP", "SELECT"};

        private PendingIntent[] mPendingIntents;

        private Notification mNotification;

        @VisibleForTesting
        PendingIntent getPendingIntent(int id) {
            return mPendingIntents[id];
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onCreate() {
            checkState(sInstance != null);
            checkState(sInstance.mService == null);
            super.onCreate();

            sInstance.mService = this;

            // Create all the PendingIntents
            int actionCount = ACTION_VERBS.length;
            mPendingIntents = new PendingIntent[actionCount];
            for (int i = 0; i < actionCount; ++i) {
                Intent intent = new Intent(this, ListenerService.class);
                intent.setAction(ACTION_PREFIX + ACTION_VERBS[i]);
                mPendingIntents[i] = PendingIntent.getService(this, 0, intent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
            }
        }

        @Override
        public void onDestroy() {
            checkState(sInstance != null);
            checkState(sInstance.mService != null);
            stop();
            sInstance.mService = null;
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            stop();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            checkState(sInstance != null);
            if (intent != null) {
                String action = intent.getAction();
                if (action != null && action.startsWith(ACTION_PREFIX)) {
                    Set<Listener> listeners = sInstance.getListeners();

                    // Strip the prefix for matching the verb
                    action = action.substring(ACTION_PREFIX.length());
                    if (ACTION_VERBS[ACTION_ID_PLAY].equals(action)) {
                        for (Listener listener : listeners) {
                            listener.onPlay();
                        }
                    } else if (ACTION_VERBS[ACTION_ID_PAUSE].equals(action)) {
                        for (Listener listener : listeners) {
                            listener.onPause();
                        }
                    } else if (ACTION_VERBS[ACTION_ID_SEEK].equals(action)) {
                        int seekPosition = intent.getIntExtra(SEEK_POSITION, 0);
                        for (Listener listener : listeners) {
                            listener.onSeek(seekPosition);
                        }
                    } else if (ACTION_VERBS[ACTION_ID_STOP].equals(action)) {
                        stop();
                    } else if (ACTION_VERBS[ACTION_ID_SELECT].equals(action)) {
                        ExpandedControllerActivity.startActivity(this);
                    }
                }
            }

            updateNotification();
            return START_NOT_STICKY;
        }

        private void stop() {
            for (Listener listener : sInstance.getListeners()) listener.onStop();
            stopSelf();
        }

        private void createNotification() {
            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(this)
                            .setDefaults(0)
                            .setSmallIcon(R.drawable.ic_notification_media_route)
                            .setLocalOnly(true)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setOnlyAlertOnce(true)
                            .setOngoing(true)
                            .setContent(createContentView())
                            .setContentIntent(getPendingIntent(ListenerService.ACTION_ID_SELECT))
                            .setDeleteIntent(getPendingIntent(ListenerService.ACTION_ID_STOP));
            mNotification = notificationBuilder.build();
        }

        private void updateNotification() {
            RemoteVideoInfo videoInfo = sInstance.getVideoInfo();
            if (!sInstance.isShowing() || videoInfo == null
                    || videoInfo.state == PlayerState.STOPPED
                    || videoInfo.state == PlayerState.FINISHED
                    || videoInfo.state == PlayerState.INVALIDATED) {
                stopForeground(true);
                mNotification = null;
            } else {
                if (mNotification == null) createNotification();
                RemoteViews contentView = createContentView();
                contentView.setTextViewText(R.id.title, sInstance.getScreenName());
                contentView.setTextViewText(R.id.status, getStatus());
                if (sInstance.mIcon != null) {
                    contentView.setImageViewBitmap(R.id.icon, sInstance.mIcon);
                } else {
                    contentView.setImageViewResource(
                            R.id.icon, R.drawable.ic_notification_media_route);
                }

                boolean showPlayPause = false;
                boolean showProgress = false;
                switch (videoInfo.state) {
                    case PLAYING:
                        showProgress = true;
                        showPlayPause = true;
                        contentView.setProgressBar(R.id.progress, videoInfo.durationMillis,
                                videoInfo.currentTimeMillis, false);
                        contentView.setImageViewResource(
                                R.id.playpause, R.drawable.ic_vidcontrol_pause);
                        contentView.setContentDescription(
                                R.id.playpause, sInstance.mPauseDescription);
                        contentView.setOnClickPendingIntent(
                                R.id.playpause, getPendingIntent(ListenerService.ACTION_ID_PAUSE));
                        scheduleProgressUpdate();
                        break;
                    case PAUSED:
                        showProgress = true;
                        showPlayPause = true;
                        contentView.setProgressBar(R.id.progress, videoInfo.durationMillis,
                                videoInfo.currentTimeMillis, false);
                        contentView.setImageViewResource(
                                R.id.playpause, R.drawable.ic_vidcontrol_play);
                        contentView.setContentDescription(
                                R.id.playpause, sInstance.mPlayDescription);
                        contentView.setOnClickPendingIntent(
                                R.id.playpause, getPendingIntent(ListenerService.ACTION_ID_PLAY));
                        break;
                    case LOADING:
                        showProgress = true;
                        contentView.setProgressBar(R.id.progress, 0, 0, true);
                        break;
                    case ERROR:
                        showProgress = true;
                        break;
                    default:
                        break;
                }

                contentView.setViewVisibility(
                        R.id.playpause, showPlayPause ? View.VISIBLE : View.INVISIBLE);
                // We use GONE instead of INVISIBLE for this because the notification looks funny
                // with a large gap in the middle if we have no duration. Setting it to GONE forces
                // the layout to squeeze tighter to the middle.
                contentView.setViewVisibility(R.id.progress,
                        (showProgress && videoInfo.durationMillis > 0) ? View.VISIBLE : View.GONE);
                contentView.setViewVisibility(
                        R.id.stop, showPlayPause ? View.VISIBLE : View.INVISIBLE);

                mNotification.contentView = contentView;

                startForeground(R.id.remote_notification, mNotification);
            }
        }

        private RemoteViews createContentView() {
            RemoteViews contentView =
                    new RemoteViews(getPackageName(), R.layout.remote_notification_bar);
            contentView.setOnClickPendingIntent(
                    R.id.stop, getPendingIntent(ListenerService.ACTION_ID_STOP));

            return contentView;
        }

        private String getStatus() {
            if (sInstance.hasError()) return sInstance.getError();
            RemoteVideoInfo videoInfo = sInstance.getVideoInfo();
            // TODO(bclayton): Is there something better to display here?
            if (videoInfo == null) return "";
            String videoTitle = videoInfo.title;
            switch (videoInfo.state) {
                case PLAYING:
                    return videoTitle != null
                            ? getString(R.string.cast_notification_playing_for_video, videoTitle)
                            : getString(R.string.cast_notification_playing);
                case LOADING:
                    return videoTitle != null
                            ? getString(R.string.cast_notification_loading_for_video, videoTitle)
                            : getString(R.string.cast_notification_loading);
                case PAUSED:
                    return videoTitle != null
                            ? getString(R.string.cast_notification_paused_for_video, videoTitle)
                            : getString(R.string.cast_notification_paused);
                case STOPPED:
                    return getString(R.string.cast_notification_stopped);
                case FINISHED:
                case INVALIDATED:
                    return videoTitle != null
                            ? getString(R.string.cast_notification_finished_for_video, videoTitle)
                            : getString(R.string.cast_notification_finished);
                case ERROR:
                default:
                    return videoInfo.errorMessage;
            }
        }

        private void scheduleProgressUpdate() {
            sInstance.mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sInstance.onPositionChanged(sInstance.mMediaRouteController.getPosition());
                }
            }, sInstance.mProgressUpdateInterval);
        }
    }

    private static NotificationTransportControl sInstance = null;
    private static final Object LOCK = new Object();

    private static final int MINIMUM_PROGRESS_UPDATE_INTERVAL_MS = 1000;

    private boolean mShowing = false;

    private final String mPlayDescription;

    private final String mPauseDescription;

    private final Context mContext;

    private MediaRouteController mMediaRouteController;

    // ListenerService running for the notification.
    private ListenerService mService;

    private Bitmap mIcon;

    private Handler mHandler;

    private int mProgressUpdateInterval = MINIMUM_PROGRESS_UPDATE_INTERVAL_MS;

    /**
     * Returns the singleton NotificationTransportControl object.
     *
     * @param context The Context that the notification service needs to be created in.
     * @param mrc The MediaRouteController object to use.
     * @return A {@code NotificationTransportControl} object that uses the given
     *         MediaRouteController object.
     */
    public static NotificationTransportControl getOrCreate(Context context,
            @Nullable MediaRouteController mrc) {
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = new NotificationTransportControl(context);
                sInstance.setVideoInfo(
                        new RemoteVideoInfo(null, 0, RemoteVideoInfo.PlayerState.STOPPED, 0, null));
            }

            sInstance.setMediaRouteController(mrc);
            return sInstance;
        }
    }

    @VisibleForTesting
    static NotificationTransportControl getIfExists() {
        return sInstance;
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @throws IllegalStateException if {@code expression} is false
     */
    private static void checkState(boolean expression) {
        if (!expression) {
            throw new IllegalStateException();
        }
    }

    /**
     * Scale the specified bitmap to the desired with and height while preserving aspect ratio.
     */
    private Bitmap scaleBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap == null) {
            return null;
        }

        float scaleX = 1.0f;
        float scaleY = 1.0f;
        if (bitmap.getWidth() > maxWidth) {
            scaleX = maxWidth / (float) bitmap.getWidth();
        }
        if (bitmap.getHeight() > maxHeight) {
            scaleY = maxHeight / (float) bitmap.getHeight();
        }
        float scale = Math.min(scaleX, scaleY);
        int width = (int) (bitmap.getWidth() * scale);
        int height = (int) (bitmap.getHeight() * scale);
        return Bitmap.createScaledBitmap(bitmap, width, height, false);
    }


    private NotificationTransportControl(Context context) {
        this.mContext = context;
        mHandler = new Handler(context.getMainLooper());
        mPlayDescription = context.getResources().getString(R.string.accessibility_play);
        mPauseDescription = context.getResources().getString(R.string.accessibility_pause);
    }

    @Override
    public void hide() {
        mShowing = false;
        mContext.stopService(new Intent(mContext, ListenerService.class));
    }

    /**
     * @return true if the notification is currently visible to the user.
     */
    @VisibleForTesting
    boolean isShowing() {
        return mShowing;
    }

    @Override
    public void onDurationUpdated(int durationMillis) {
        // Set the progress update interval based on the screen height/width, since there's no point
        // in updating the progress bar more frequently than what the user can see.
        // getDisplayMetrics() is dependent on the current orientation, so we need to get the max
        // of both height and width so that both portrait and landscape notifications are covered.
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        float density = metrics.density;
        float dpHeight = metrics.heightPixels / density;
        float dpWidth = metrics.widthPixels / density;

        float maxDimen = Math.max(dpHeight, dpWidth);

        mProgressUpdateInterval = Math.max(MINIMUM_PROGRESS_UPDATE_INTERVAL_MS,
                Math.round(durationMillis / maxDimen));

        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.durationMillis = durationMillis;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onError(int error, String errorMessage) {
        // Stop the session for all errors
        hide();
    }

    @Override
    public void onPlaybackStateChanged(PlayerState oldState, PlayerState newState) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.state = newState;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onPositionChanged(int positionMillis) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.currentTimeMillis = positionMillis;
        setVideoInfo(videoInfo);
    }

    @Override
    public void onPrepared(MediaRouteController mediaRouteController) {
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
    public void onTitleChanged(String title) {
        RemoteVideoInfo videoInfo = new RemoteVideoInfo(getVideoInfo());
        videoInfo.title = title;
        setVideoInfo(videoInfo);
    }

    @Override
    public void setRouteController(MediaRouteController controller) {
        setMediaRouteController(controller);
    }

    @Override
    public void show(PlayerState initialState) {
        mShowing = true;
        RemoteVideoInfo newVideoInfo = new RemoteVideoInfo(mVideoInfo);
        newVideoInfo.state = initialState;
        setVideoInfo(newVideoInfo);
    }

    @VisibleForTesting
    Notification getNotification() {
        // TODO(aberent). This is only used by the tests, which should actually simply be using
        // isShowing(); but the tests are still downstream, so this needs to be changed in stages.
        if (getService() == null) return null;
        return getService().mNotification;
    }

    @VisibleForTesting
    final ListenerService getService() {
        // TODO(aberent). This is only used by the tests, which should actually simply be using
        // isShowing(); but the tests are still downstream, so this needs to be changed in stages.
        if (!isShowing()) return null;
        return mService;
    }

    @Override
    protected void onErrorChanged() {
        mContext.startService(new Intent(mContext, ListenerService.class));
    }

    @Override
    protected void onPosterBitmapChanged() {
        Bitmap posterBitmap = getPosterBitmap();
        mIcon = scaleBitmapForIcon(posterBitmap);
        super.onPosterBitmapChanged();
    }

    @Override
    protected void onScreenNameChanged() {
        mContext.startService(new Intent(mContext, ListenerService.class));
    }

    @Override
    protected void onVideoInfoChanged() {
        mContext.startService(new Intent(mContext, ListenerService.class));
    }

    /**
     * Scale the specified bitmap to properly fit as a notification icon. If the argument is null
     * the function returns null.
     */
    private Bitmap scaleBitmapForIcon(Bitmap bitmap) {
        Resources res = mContext.getResources();
        float maxWidth = res.getDimension(R.dimen.remote_notification_logo_max_width);
        float maxHeight = res.getDimension(R.dimen.remote_notification_logo_max_height);
        return scaleBitmap(bitmap, (int) maxWidth, (int) maxHeight);
    }

    /**
     * Sets the MediaRouteController the notification should be using to get the data from.
     *
     * @param mrc the MediaRouteController object to use. If null, the previous MediaRouteController
     *            object will not be overwritten.
     */
    private void setMediaRouteController(@Nullable MediaRouteController mrc) {
        if (mrc == null || mMediaRouteController == mrc) return;

        if (mMediaRouteController != null) {
            mMediaRouteController.removeUiListener(this);
        }

        mMediaRouteController = mrc;
        mMediaRouteController.addUiListener(this);
    }

}
