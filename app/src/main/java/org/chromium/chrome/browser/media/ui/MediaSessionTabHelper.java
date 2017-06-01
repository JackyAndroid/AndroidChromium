// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.blink.mojom.MediaSessionAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.metrics.MediaNotificationUma;
import org.chromium.chrome.browser.metrics.MediaSessionUMA;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.MediaSession;
import org.chromium.content_public.browser.MediaSessionObserver;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.MediaMetadata;
import org.chromium.ui.base.WindowAndroid;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A tab helper responsible for enabling/disabling media controls and passing
 * media actions from the controls to the {@link org.chromium.content.browser.MediaSession}
 */
public class MediaSessionTabHelper implements MediaImageCallback {
    private static final String TAG = "MediaSession";

    private static final String UNICODE_PLAY_CHARACTER = "\u25B6";
    private static final int MINIMAL_FAVICON_SIZE = 114;
    private static final int HIDE_NOTIFICATION_DELAY_MILLIS = 1000;

    private Tab mTab;
    private Bitmap mPageMediaImage = null;
    private Bitmap mFavicon = null;
    private Bitmap mCurrentMediaImage = null;
    private String mOrigin = null;
    private MediaSessionObserver mMediaSessionObserver;
    private int mPreviousVolumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE;
    private MediaNotificationInfo.Builder mNotificationInfoBuilder = null;
    // The fallback title if |mPageMetadata| is null or its title is empty.
    private String mFallbackTitle = null;
    // The metadata set by the page.
    private MediaMetadata mPageMetadata = null;
    // The currently showing metadata.
    private MediaMetadata mCurrentMetadata = null;
    private MediaImageManager mMediaImageManager = null;
    private Set<Integer> mMediaSessionActions = new HashSet<Integer>();
    private Handler mHandler;
    // The delayed task to hide notification. Hiding notification can be immediate or delayed.
    // Delayed hiding will schedule this delayed task to |mHandler|. The task will be canceled when
    // showing or immediate hiding.
    private Runnable mHideNotificationDelayedTask;

    @VisibleForTesting
    @Nullable
    MediaSessionObserver getMediaSessionObserverForTesting() {
        return mMediaSessionObserver;
    }

    private MediaNotificationListener mControlsListener = new MediaNotificationListener() {
        @Override
        public void onPlay(int actionSource) {
            if (isNotificationHiddingOrHidden()) return;

            MediaSessionUMA.recordPlay(
                    MediaSessionTabHelper.convertMediaActionSourceToUMA(actionSource));

            if (mMediaSessionObserver.getMediaSession() != null) {
                mMediaSessionObserver.getMediaSession().resume();
            }
        }

        @Override
        public void onPause(int actionSource) {
            if (isNotificationHiddingOrHidden()) return;

            MediaSessionUMA.recordPause(
                    MediaSessionTabHelper.convertMediaActionSourceToUMA(actionSource));

            if (mMediaSessionObserver.getMediaSession() != null) {
                mMediaSessionObserver.getMediaSession().suspend();
            }
        }

        @Override
        public void onStop(int actionSource) {
            if (isNotificationHiddingOrHidden()) return;

            MediaSessionUMA.recordStop(
                    MediaSessionTabHelper.convertMediaActionSourceToUMA(actionSource));

            if (mMediaSessionObserver.getMediaSession() != null) {
                mMediaSessionObserver.getMediaSession().stop();
            }
        }

        @Override
        public void onMediaSessionAction(int action) {
            if (!MediaSessionAction.isKnownValue(action)) return;
            if (mMediaSessionObserver != null) {
                mMediaSessionObserver.getMediaSession().didReceiveAction(action);
            }
        }
    };

    private void hideNotificationDelayed() {
        if (mTab == null) return;
        if (mHideNotificationDelayedTask != null) return;

        mHideNotificationDelayedTask = new Runnable() {
            @Override
            public void run() {
                mHideNotificationDelayedTask = null;
                hideNotificationInternal();
            }
        };
        mHandler.postDelayed(mHideNotificationDelayedTask, HIDE_NOTIFICATION_DELAY_MILLIS);

        mNotificationInfoBuilder = null;
    }

    private void hideNotificationImmediately() {
        if (mTab == null) return;
        if (mHideNotificationDelayedTask != null) {
            mHandler.removeCallbacks(mHideNotificationDelayedTask);
            mHideNotificationDelayedTask = null;
        }

        hideNotificationInternal();
        mNotificationInfoBuilder = null;
    }

    /**
     * This method performs the common steps for hiding the notification. It should only be called
     * by {@link #hideNotificationDelayed()} and {@link #hideNotificationImmediately()}.
     */
    private void hideNotificationInternal() {
        MediaNotificationManager.hide(mTab.getId(), R.id.media_playback_notification);
        Activity activity = getActivityFromTab(mTab);
        if (activity != null) {
            activity.setVolumeControlStream(mPreviousVolumeControlStream);
        }
    }

    private void showNotification() {
        assert mNotificationInfoBuilder != null;
        if (mHideNotificationDelayedTask != null) {
            mHandler.removeCallbacks(mHideNotificationDelayedTask);
            mHideNotificationDelayedTask = null;
        }
        MediaNotificationManager.show(
                ContextUtils.getApplicationContext(), mNotificationInfoBuilder.build());
    }

    private MediaSessionObserver createMediaSessionObserver(MediaSession mediaSession) {
        return new MediaSessionObserver(mediaSession) {
            @Override
            public void mediaSessionDestroyed() {
                hideNotificationImmediately();
                cleanupMediaSessionObserver();
            }

            @Override
            public void mediaSessionStateChanged(boolean isControllable, boolean isPaused) {
                if (!isControllable) {
                    hideNotificationDelayed();
                    return;
                }

                Intent contentIntent = Tab.createBringTabToFrontIntent(mTab.getId());
                if (contentIntent != null) {
                    contentIntent.putExtra(MediaNotificationUma.INTENT_EXTRA_NAME,
                            MediaNotificationUma.SOURCE_MEDIA);
                }

                if (mFallbackTitle == null) mFallbackTitle = sanitizeMediaTitle(mTab.getTitle());
                mCurrentMetadata = getMetadata();
                mCurrentMediaImage = getNotificationImage();
                mNotificationInfoBuilder =
                        new MediaNotificationInfo.Builder()
                                .setMetadata(mCurrentMetadata)
                                .setPaused(isPaused)
                                .setOrigin(mOrigin)
                                .setTabId(mTab.getId())
                                .setPrivate(mTab.isIncognito())
                                .setIcon(R.drawable.audio_playing)
                                .setLargeIcon(mCurrentMediaImage)
                                .setDefaultLargeIcon(R.drawable.audio_playing_square)
                                .setActions(MediaNotificationInfo.ACTION_PLAY_PAUSE
                                        | MediaNotificationInfo.ACTION_SWIPEAWAY)
                                .setContentIntent(contentIntent)
                                .setId(R.id.media_playback_notification)
                                .setListener(mControlsListener)
                                .setMediaSessionActions(mMediaSessionActions);

                showNotification();
                Activity activity = getActivityFromTab(mTab);
                if (activity != null) {
                    activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
                }
            }

            @Override
            public void mediaSessionMetadataChanged(MediaMetadata metadata) {
                mPageMetadata = metadata;
                if (mPageMetadata != null) {
                    mMediaImageManager.downloadImage(mPageMetadata.getArtwork(),
                            MediaSessionTabHelper.this);
                }
                updateNotificationMetadata();
            }

            @Override
            public void mediaSessionEnabledAction(int action) {
                if (!MediaSessionAction.isKnownValue(action)) return;
                mMediaSessionActions.add(action);
                updateNotificationActions();
            }

            @Override
            public void mediaSessionDisabledAction(int action) {
                if (!MediaSessionAction.isKnownValue(action)) return;
                mMediaSessionActions.remove(action);
                updateNotificationActions();
            }
        };
    }

    private void setWebContents(WebContents webContents) {
        MediaSession mediaSession = MediaSession.fromWebContents(webContents);
        if (mMediaSessionObserver != null
                && mediaSession == mMediaSessionObserver.getMediaSession()) {
            return;
        }

        cleanupMediaSessionObserver();
        if (mediaSession != null) {
            mMediaSessionObserver = createMediaSessionObserver(mediaSession);
        }
        mMediaImageManager.setWebContents(webContents);
    }

    private void cleanupMediaSessionObserver() {
        if (mMediaSessionObserver == null) return;
        mMediaSessionObserver.stopObserving();
        mMediaSessionObserver = null;
        mMediaSessionActions.clear();
    }

    private final TabObserver mTabObserver = new EmptyTabObserver() {
        @Override
        public void onContentChanged(Tab tab) {
            assert tab == mTab;
            setWebContents(tab.getWebContents());
        }

        @Override
        public void onFaviconUpdated(Tab tab, Bitmap icon) {
            assert tab == mTab;

            if (!updateFavicon(icon)) return;

            updateNotificationImage();
        }

        @Override
        public void onUrlUpdated(Tab tab) {
            assert tab == mTab;

            String origin = mTab.getUrl();
            try {
                origin = UrlFormatter.formatUrlForSecurityDisplay(new URI(origin), true);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Unable to parse the origin from the URL. "
                                + "Using the full URL instead.");
            }

            if (mOrigin != null && mOrigin.equals(origin)) return;
            mOrigin = origin;
            mFavicon = null;
            mPageMediaImage = null;

            if (isNotificationHiddingOrHidden()) return;

            mNotificationInfoBuilder.setOrigin(mOrigin);
            mNotificationInfoBuilder.setLargeIcon(mFavicon);
            showNotification();
        }

        @Override
        public void onTitleUpdated(Tab tab) {
            assert tab == mTab;
            String newFallbackTitle = sanitizeMediaTitle(tab.getTitle());
            if (!TextUtils.equals(mFallbackTitle, newFallbackTitle)) {
                mFallbackTitle = newFallbackTitle;
                updateNotificationMetadata();
            }
        }

        @Override
        public void onDestroyed(Tab tab) {
            assert mTab == tab;

            cleanupMediaSessionObserver();

            hideNotificationImmediately();
            mTab.removeObserver(this);
            mTab = null;
        }
    };

    private MediaSessionTabHelper(Tab tab) {
        mTab = tab;
        mTab.addObserver(mTabObserver);
        mMediaImageManager = new MediaImageManager(
            MINIMAL_FAVICON_SIZE, MediaNotificationManager.getMaximumLargeIconSize());
        if (mTab.getWebContents() != null) setWebContents(tab.getWebContents());

        Activity activity = getActivityFromTab(mTab);
        if (activity != null) {
            mPreviousVolumeControlStream = activity.getVolumeControlStream();
        }
        mHandler = new Handler();
    }

    /**
     * Creates the {@link MediaSessionTabHelper} for the given {@link Tab}.
     * @param tab the tab to attach the helper to.
     */
    public static void createForTab(Tab tab) {
        new MediaSessionTabHelper(tab);
    }

    /**
     * Removes all the leading/trailing white spaces and the quite common unicode play character.
     * It improves the visibility of the title in the notification.
     *
     * @param title The original tab title, e.g. "   ▶   Foo - Bar  "
     * @return The sanitized tab title, e.g. "Foo - Bar"
     */
    private String sanitizeMediaTitle(String title) {
        title = title.trim();
        return title.startsWith(UNICODE_PLAY_CHARACTER) ? title.substring(1).trim() : title;
    }

    /**
     * Converts the {@link MediaNotificationListener} action source enum into the
     * {@link MediaSessionUMA} one to ensure matching the histogram values.
     * @param source the source id, must be one of the ACTION_SOURCE_* constants defined in the
     *               {@link MediaNotificationListener} interface.
     * @return the corresponding histogram value.
     */
    public static int convertMediaActionSourceToUMA(int source) {
        if (source == MediaNotificationListener.ACTION_SOURCE_MEDIA_NOTIFICATION) {
            return MediaSessionUMA.MEDIA_SESSION_ACTION_SOURCE_MEDIA_NOTIFICATION;
        } else if (source == MediaNotificationListener.ACTION_SOURCE_MEDIA_SESSION) {
            return MediaSessionUMA.MEDIA_SESSION_ACTION_SOURCE_MEDIA_SESSION;
        } else if (source == MediaNotificationListener.ACTION_SOURCE_HEADSET_UNPLUG) {
            return MediaSessionUMA.MEDIA_SESSION_ACTION_SOURCE_HEADSET_UNPLUG;
        }

        assert false;
        return MediaSessionUMA.MEDIA_SESSION_ACTION_SOURCE_MAX;
    }

    private Activity getActivityFromTab(Tab tab) {
        WindowAndroid windowAndroid = tab.getWindowAndroid();
        if (windowAndroid == null) return null;

        return windowAndroid.getActivity().get();
    }

    /**
     * Updates the best favicon if the given icon is better.
     * @return whether the best favicon is updated.
     */
    private boolean updateFavicon(Bitmap icon) {
        if (icon == null) return false;

        if (icon.getWidth() < MINIMAL_FAVICON_SIZE || icon.getHeight() < MINIMAL_FAVICON_SIZE) {
            return false;
        }
        if (mFavicon != null && (icon.getWidth() < mFavicon.getWidth()
                                        || icon.getHeight() < mFavicon.getHeight())) {
            return false;
        }
        mFavicon = MediaNotificationManager.scaleIconForDisplay(icon);
        return true;
    }

    /**
     * Updates the metadata in media notification. This method should be called whenever
     * |mPageMetadata| or |mFallbackTitle| is changed.
     */
    private void updateNotificationMetadata() {
        if (isNotificationHiddingOrHidden()) return;

        MediaMetadata newMetadata = getMetadata();
        if (mCurrentMetadata.equals(newMetadata)) return;

        mCurrentMetadata = newMetadata;
        mNotificationInfoBuilder.setMetadata(mCurrentMetadata);
        showNotification();
    }

    /**
     * @return The up-to-date MediaSession metadata. Returns the cached object like |mPageMetadata|
     * or |mCurrentMetadata| if it reflects the current state. Otherwise will return a new
     * {@link MediaMetadata} object.
     */
    private MediaMetadata getMetadata() {
        String title = mFallbackTitle;
        String artist = "";
        String album = "";
        if (mPageMetadata != null) {
            if (!TextUtils.isEmpty(mPageMetadata.getTitle())) return mPageMetadata;

            artist = mPageMetadata.getArtist();
            album = mPageMetadata.getAlbum();
        }

        if (mCurrentMetadata != null && TextUtils.equals(title, mCurrentMetadata.getTitle())
                && TextUtils.equals(artist, mCurrentMetadata.getArtist())
                && TextUtils.equals(album, mCurrentMetadata.getAlbum())) {
            return mCurrentMetadata;
        }

        return new MediaMetadata(title, artist, album);
    }

    private void updateNotificationActions() {
        if (isNotificationHiddingOrHidden()) return;

        mNotificationInfoBuilder.setMediaSessionActions(mMediaSessionActions);
        showNotification();
    }

    @Override
    public void onImageDownloaded(Bitmap image) {
        mPageMediaImage = MediaNotificationManager.scaleIconForDisplay(image);
        updateNotificationImage();
    }

    private void updateNotificationImage() {
        Bitmap newMediaImage = getNotificationImage();
        if (mCurrentMediaImage == newMediaImage) return;

        mCurrentMediaImage = newMediaImage;

        if (isNotificationHiddingOrHidden()) return;
        mNotificationInfoBuilder.setLargeIcon(mCurrentMediaImage);
        showNotification();
    }

    private Bitmap getNotificationImage() {
        return (mPageMediaImage != null) ? mPageMediaImage : mFavicon;
    }

    private boolean isNotificationHiddingOrHidden() {
        return mNotificationInfoBuilder == null;
    }
}
