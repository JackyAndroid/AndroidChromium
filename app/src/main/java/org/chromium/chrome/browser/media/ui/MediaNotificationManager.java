// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;

/**
 * A class for notifications that provide information and optional media controls for a given media.
 * Internally implements a Service for transforming notification Intents into
 * {@link MediaNotificationListener} calls for all registered listeners.
 * There's one service started for a distinct notification id.
 */
public class MediaNotificationManager {

    // We're always used on the UI thread but the LOCK is required by lint when creating the
    // singleton.
    private static final Object LOCK = new Object();

    // Maps the notification ids to their corresponding notification managers.
    private static SparseArray<MediaNotificationManager> sManagers;

    /**
     * Service used to transform intent requests triggered from the notification into
     * {@code MediaNotificationListener} callbacks. We have to create a separate derived class for
     * each type of notification since one class corresponds to one instance of the service only.
     */
    private abstract static class ListenerService extends Service {
        private static final String ACTION_PLAY =
                "MediaNotificationManager.ListenerService.PLAY";
        private static final String ACTION_PAUSE =
                "MediaNotificationManager.ListenerService.PAUSE";
        private static final String ACTION_STOP =
                "MediaNotificationManager.ListenerService.STOP";
        private static final String EXTRA_NOTIFICATION_ID =
                "MediaNotificationManager.ListenerService.NOTIFICATION_ID";

        // The notification id this service instance corresponds to.
        private int mNotificationId = MediaNotificationInfo.INVALID_ID;

        private PendingIntent getPendingIntent(String action) {
            Intent intent = getIntent(this, mNotificationId).setAction(action);
            return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            MediaNotificationManager manager = getManager(mNotificationId);
            if (manager == null) return;

            manager.onServiceDestroyed(mNotificationId);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (!processIntent(intent)) stopSelf();

            return START_NOT_STICKY;
        }

        private boolean processIntent(Intent intent) {
            if (intent == null) return false;

            mNotificationId = intent.getIntExtra(
                    EXTRA_NOTIFICATION_ID, MediaNotificationInfo.INVALID_ID);
            if (mNotificationId == MediaNotificationInfo.INVALID_ID) return false;

            MediaNotificationManager manager = getManager(mNotificationId);
            if (manager == null || manager.mMediaNotificationInfo == null) return false;

            manager.onServiceStarted(this);

            processAction(intent, manager);
            return true;
        }

        private void processAction(Intent intent, MediaNotificationManager manager) {
            String action = intent.getAction();

            // Before Android L, instead of using the MediaSession callback, the system will fire
            // ACTION_MEDIA_BUTTON intents which stores the information about the key event.
            if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                assert Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null) return;
                if (event.getAction() != KeyEvent.ACTION_DOWN) return;

                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        manager.onPlay(
                                MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        manager.onPause(
                                MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION);
                        break;
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        if (manager.mMediaNotificationInfo.isPaused) {
                            manager.onPlay(MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION);
                        } else {
                            manager.onPause(
                                    MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION);
                        }
                        break;
                    default:
                        break;
                }
            } else if (ACTION_STOP.equals(action)) {
                manager.onStop(
                        MediaNotificationListener.ACTION_SOURCE_MEDIA_NOTIFICATION);
                stopSelf();
            } else if (ACTION_PLAY.equals(action)) {
                manager.onPlay(MediaNotificationListener.ACTION_SOURCE_MEDIA_NOTIFICATION);
            } else if (ACTION_PAUSE.equals(action)) {
                manager.onPause(MediaNotificationListener.ACTION_SOURCE_MEDIA_NOTIFICATION);
            }
        }
    }

    /**
     * This class is used internally but have to be public to be able to launch the service.
     */
    public static final class PlaybackListenerService extends ListenerService {
        private static final int NOTIFICATION_ID = R.id.media_playback_notification;
    }

    /**
     * This class is used internally but have to be public to be able to launch the service.
     */
    public static final class PresentationListenerService extends ListenerService {
        private static final int NOTIFICATION_ID = R.id.presentation_notification;
    }

    private static Intent getIntent(Context context, int notificationId) {
        Intent intent = null;
        if (notificationId == PlaybackListenerService.NOTIFICATION_ID) {
            intent = new Intent(context, PlaybackListenerService.class);
        } else if (notificationId == PresentationListenerService.NOTIFICATION_ID) {
            intent = new Intent(context, PresentationListenerService.class);
        } else {
            return null;
        }
        return intent.putExtra(ListenerService.EXTRA_NOTIFICATION_ID, notificationId);
    }

    /**
     * Shows the notification with media controls with the specified media info. Replaces/updates
     * the current notification if already showing. Does nothing if |mediaNotificationInfo| hasn't
     * changed from the last one.
     *
     * @param applicationContext context to create the notification with
     * @param notificationInfoBuilder information to show in the notification
     */
    public static void show(Context applicationContext,
                            MediaNotificationInfo.Builder notificationInfoBuilder) {
        synchronized (LOCK) {
            if (sManagers == null) {
                sManagers = new SparseArray<MediaNotificationManager>();
            }
        }

        MediaNotificationInfo notificationInfo = notificationInfoBuilder.build();
        MediaNotificationManager manager = sManagers.get(notificationInfo.id);
        if (manager == null) {
            manager = new MediaNotificationManager(applicationContext);
            sManagers.put(notificationInfo.id, manager);
        }

        manager.mNotificationInfoBuilder = notificationInfoBuilder;
        manager.showNotification(notificationInfo);
    }

    /**
     * Hides the notification for the specified tabId and notificationId
     *
     * @param tabId the id of the tab that showed the notification or invalid tab id.
     * @param notificationId the id of the notification to hide for this tab.
     */
    public static void hide(int tabId, int notificationId) {
        MediaNotificationManager manager = getManager(notificationId);
        if (manager == null) return;

        manager.hideNotification(tabId);
    }

    /**
     * Hides notifications with the specified id for all tabs if shown.
     *
     * @param notificationId the id of the notification to hide for all tabs.
     */
    public static void clear(int notificationId) {
        MediaNotificationManager manager = getManager(notificationId);
        if (manager == null) return;

        manager.clearNotification();
        sManagers.remove(notificationId);
    }

    /**
     * Hides notifications with all known ids for all tabs if shown.
     */
    public static void clearAll() {
        if (sManagers == null) return;

        for (int i = 0; i < sManagers.size(); ++i) {
            MediaNotificationManager manager = sManagers.valueAt(i);
            manager.clearNotification();
        }
        sManagers.clear();
    }

    private static MediaNotificationManager getManager(int notificationId) {
        if (sManagers == null) return null;

        return sManagers.get(notificationId);
    }

    private final Context mContext;

    // ListenerService running for the notification. Only non-null when showing.
    private ListenerService mService;

    private final String mPlayDescription;
    private final String mPauseDescription;
    private final String mStopDescription;

    private NotificationCompat.Builder mNotificationBuilder;

    private Bitmap mNotificationIcon;

    private final Bitmap mMediaSessionIcon;

    private MediaNotificationInfo mMediaNotificationInfo;
    private MediaNotificationInfo.Builder mNotificationInfoBuilder;

    private MediaSessionCompat mMediaSession;

    private static final class MediaSessionCallback extends MediaSessionCompat.Callback {
        private final MediaNotificationManager mManager;

        private MediaSessionCallback(MediaNotificationManager manager) {
            mManager = manager;
        }

        @Override
        public void onPlay() {
            mManager.onPlay(MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION);
        }

        @Override
        public void onPause() {
            mManager.onPause(MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION);
        }
    }

    private final MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback(this);

    private MediaNotificationManager(Context context) {
        mContext = context;
        mPlayDescription = context.getResources().getString(R.string.accessibility_play);
        mPauseDescription = context.getResources().getString(R.string.accessibility_pause);
        mStopDescription = context.getResources().getString(R.string.accessibility_stop);

        // The MediaSession icon is a plain color.
        int size = context.getResources().getDimensionPixelSize(R.dimen.media_session_icon_size);
        mMediaSessionIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        mMediaSessionIcon.eraseColor(ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.media_session_icon_color));
    }

    /**
     * Registers the started {@link Service} with the manager and creates the notification.
     *
     * @param service the service that was started
     */
    private void onServiceStarted(ListenerService service) {
        mService = service;
        updateNotification();
    }

    /**
     * Handles the service destruction destruction.
     */
    private void onServiceDestroyed(int notificationId) {
        if (mService == null) return;

        if (notificationId != -1) clear(notificationId);

        mNotificationBuilder = null;
        mService = null;
    }

    private void onPlay(int actionSource) {
        if (!mMediaNotificationInfo.isPaused) return;

        mMediaNotificationInfo = mNotificationInfoBuilder.setPaused(false).build();
        updateNotification();

        mMediaNotificationInfo.listener.onPlay(actionSource);
    }

    private void onPause(int actionSource) {
        if (mMediaNotificationInfo.isPaused) return;

        mMediaNotificationInfo = mNotificationInfoBuilder.setPaused(true).build();
        updateNotification();

        mMediaNotificationInfo.listener.onPause(actionSource);
    }

    private void onStop(int actionSource) {
        // hideNotification() below will clear |mMediaNotificationInfo| but {@link
        // MediaNotificationListener}.onStop() might also clear it so keep the listener and call it
        // later.
        // TODO(avayvod): make the notification delegate update its state, not the notification
        // manager. See https://crbug.com/546981
        MediaNotificationListener listener = mMediaNotificationInfo.listener;

        hideNotification(mMediaNotificationInfo.tabId);

        listener.onStop(actionSource);
    }

    private void showNotification(MediaNotificationInfo mediaNotificationInfo) {
        mContext.startService(getIntent(mContext, mediaNotificationInfo.id));

        if (mediaNotificationInfo.equals(mMediaNotificationInfo)) return;

        mMediaNotificationInfo = mediaNotificationInfo;
        updateNotification();
    }

    private void clearNotification() {
        if (mMediaNotificationInfo == null) return;

        int notificationId = mMediaNotificationInfo.id;

        NotificationManagerCompat manager = NotificationManagerCompat.from(mContext);
        manager.cancel(notificationId);

        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        mContext.stopService(getIntent(mContext, notificationId));
        mMediaNotificationInfo = null;
    }

    private void hideNotification(int tabId) {
        if (mMediaNotificationInfo == null || tabId != mMediaNotificationInfo.tabId) return;
        clearNotification();
    }

    private RemoteViews createContentView() {
        RemoteViews contentView =
                new RemoteViews(mContext.getPackageName(), R.layout.playback_notification_bar);

        // By default, play/pause button is the only one.
        int playPauseButtonId = R.id.button1;
        // On Android pre-L, dismissing the notification when the service is no longer in foreground
        // doesn't work. Instead, a STOP button is shown.
        if (mMediaNotificationInfo.supportsSwipeAway()
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || mMediaNotificationInfo.supportsStop()) {
            contentView.setOnClickPendingIntent(R.id.button1,
                    mService.getPendingIntent(ListenerService.ACTION_STOP));
            contentView.setContentDescription(R.id.button1, mStopDescription);

            // If the play/pause needs to be shown, it moves over to the second button from the end.
            playPauseButtonId = R.id.button2;
        }

        contentView.setTextViewText(R.id.title, mMediaNotificationInfo.title);
        contentView.setTextViewText(R.id.status, mMediaNotificationInfo.origin);
        if (mNotificationIcon != null) {
            contentView.setImageViewBitmap(R.id.icon, mNotificationIcon);
        } else {
            contentView.setImageViewResource(R.id.icon, mMediaNotificationInfo.icon);
        }

        if (mMediaNotificationInfo.supportsPlayPause()) {
            if (mMediaNotificationInfo.isPaused) {
                contentView.setImageViewResource(playPauseButtonId, R.drawable.ic_vidcontrol_play);
                contentView.setContentDescription(playPauseButtonId, mPlayDescription);
                contentView.setOnClickPendingIntent(playPauseButtonId,
                        mService.getPendingIntent(ListenerService.ACTION_PLAY));
            } else {
                // If we're here, the notification supports play/pause button and is playing.
                contentView.setImageViewResource(playPauseButtonId, R.drawable.ic_vidcontrol_pause);
                contentView.setContentDescription(playPauseButtonId, mPauseDescription);
                contentView.setOnClickPendingIntent(playPauseButtonId,
                        mService.getPendingIntent(ListenerService.ACTION_PAUSE));
            }

            contentView.setViewVisibility(playPauseButtonId, View.VISIBLE);
        } else {
            contentView.setViewVisibility(playPauseButtonId, View.GONE);
        }

        return contentView;
    }

    private MediaMetadataCompat createMetadata() {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                    mMediaNotificationInfo.title);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                    mMediaNotificationInfo.origin);
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
                    mMediaSessionIcon);
        } else {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                    mMediaNotificationInfo.title);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                    mMediaNotificationInfo.origin);
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, mMediaSessionIcon);
        }

        return metadataBuilder.build();
    }

    private void updateNotification() {
        if (mService == null) return;

        if (mMediaNotificationInfo == null) {
            // Notification was hidden before we could update it.
            assert mNotificationBuilder == null;
            return;
        }

        // Android doesn't badge the icons for RemoteViews automatically when
        // running the app under the Work profile.
        if (mNotificationIcon == null) {
            Drawable notificationIconDrawable = ApiCompatibilityUtils.getUserBadgedIcon(
                    mContext, mMediaNotificationInfo.icon);
            mNotificationIcon = drawableToBitmap(notificationIconDrawable);
        }

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new NotificationCompat.Builder(mContext)
                .setSmallIcon(mMediaNotificationInfo.icon)
                .setAutoCancel(false)
                .setLocalOnly(true)
                .setDeleteIntent(mService.getPendingIntent(ListenerService.ACTION_STOP));
        }

        if (mMediaNotificationInfo.supportsSwipeAway()) {
            mNotificationBuilder.setOngoing(!mMediaNotificationInfo.isPaused);
        }

        int tabId = mMediaNotificationInfo.tabId;
        Intent tabIntent = Tab.createBringTabToFrontIntent(tabId);
        if (tabIntent != null) {
            mNotificationBuilder
                    .setContentIntent(PendingIntent.getActivity(mContext, tabId, tabIntent, 0));
        }

        mNotificationBuilder.setContent(createContentView());
        mNotificationBuilder.setVisibility(
                mMediaNotificationInfo.isPrivate ? NotificationCompat.VISIBILITY_PRIVATE
                                                 : NotificationCompat.VISIBILITY_PUBLIC);

        if (mMediaNotificationInfo.supportsPlayPause()) {
            if (mMediaSession == null) mMediaSession = createMediaSession();

            mMediaSession.setMetadata(createMetadata());

            PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE);
            if (mMediaNotificationInfo.isPaused) {
                playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            } else {
                // If notification only supports stop, still pretend
                playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            }
            mMediaSession.setPlaybackState(playbackStateBuilder.build());
        }

        Notification notification = mNotificationBuilder.build();

        // We keep the service as a foreground service while the media is playing. When it is not,
        // the service isn't stopped but is no longer in foreground, thus at a lower priority.
        // While the service is in foreground, the associated notification can't be swipped away.
        // Moving it back to background allows the user to remove the notification.
        if (mMediaNotificationInfo.supportsSwipeAway() && mMediaNotificationInfo.isPaused) {
            mService.stopForeground(false /* removeNotification */);

            NotificationManagerCompat manager = NotificationManagerCompat.from(mContext);
            manager.notify(mMediaNotificationInfo.id, notification);
        } else {
            mService.startForeground(mMediaNotificationInfo.id, notification);
        }
    }

    private MediaSessionCompat createMediaSession() {
        MediaSessionCompat mediaSession = new MediaSessionCompat(
                mContext,
                mContext.getString(R.string.app_name),
                new ComponentName(mContext.getPackageName(),
                        MediaButtonReceiver.class.getName()),
                null);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(mMediaSessionCallback);

        // TODO(mlamouri): the following code is to work around a bug that hopefully
        // MediaSessionCompat will handle directly. see b/24051980.
        try {
            mediaSession.setActive(true);
        } catch (NullPointerException e) {
            // Some versions of KitKat do not support AudioManager.registerMediaButtonIntent
            // with a PendingIntent. They will throw a NullPointerException, in which case
            // they should be able to activate a MediaSessionCompat with only transport
            // controls.
            mediaSession.setActive(false);
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setActive(true);
        }
        return mediaSession;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (!(drawable instanceof BitmapDrawable)) return null;

        BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
        return bitmapDrawable.getBitmap();
    }
}
