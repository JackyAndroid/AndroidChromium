// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.metrics.MediaNotificationUma;
import org.chromium.chrome.browser.metrics.MediaSessionUMA;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.content_public.common.MediaMetadata;
import org.chromium.ui.base.WindowAndroid;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A tab helper responsible for enabling/disabling media controls and passing
 * media actions from the controls to the {@link org.chromium.content.browser.MediaSession}
 */
public class MediaSessionTabHelper {
    private static final String TAG = "MediaSession";

    private static final String UNICODE_PLAY_CHARACTER = "\u25B6";
    private static final int MINIMAL_FAVICON_SIZE = 114;

    private Tab mTab;
    private Bitmap mFavicon = null;
    private String mOrigin = null;
    private WebContents mWebContents;
    private WebContentsObserver mWebContentsObserver;
    private int mPreviousVolumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE;
    private MediaNotificationInfo.Builder mNotificationInfoBuilder = null;
    private MediaMetadata mFallbackMetadata;

    private MediaNotificationListener mControlsListener = new MediaNotificationListener() {
        @Override
        public void onPlay(int actionSource) {
            MediaSessionUMA
                    .recordPlay(MediaSessionTabHelper.convertMediaActionSourceToUMA(actionSource));

            mWebContents.resumeMediaSession();
        }

        @Override
        public void onPause(int actionSource) {
            MediaSessionUMA.recordPause(
                    MediaSessionTabHelper.convertMediaActionSourceToUMA(actionSource));

            mWebContents.suspendMediaSession();
        }

        @Override
        public void onStop(int actionSource) {
            MediaSessionUMA
                    .recordStop(MediaSessionTabHelper.convertMediaActionSourceToUMA(actionSource));

            mWebContents.stopMediaSession();
        }
    };

    void hideNotification() {
        if (mTab == null) {
            return;
        }
        MediaNotificationManager.hide(mTab.getId(), R.id.media_playback_notification);
        Activity activity = getActivityFromTab(mTab);
        if (activity != null) {
            activity.setVolumeControlStream(mPreviousVolumeControlStream);
        }
        mNotificationInfoBuilder = null;
    }

    private WebContentsObserver createWebContentsObserver(WebContents webContents) {
        return new WebContentsObserver(webContents) {
            @Override
            public void destroy() {
                hideNotification();
                super.destroy();
            }

            @Override
            public void mediaSessionStateChanged(boolean isControllable, boolean isPaused,
                    MediaMetadata metadata) {
                if (!isControllable) {
                    hideNotification();
                    return;
                }

                mFallbackMetadata = null;

                // The page's title is used as a placeholder if no title is specified in the
                // metadata.
                if (metadata == null || TextUtils.isEmpty(metadata.getTitle())) {
                    mFallbackMetadata = new MediaMetadata(
                            sanitizeMediaTitle(mTab.getTitle()),
                            metadata == null ? "" : metadata.getArtist(),
                            metadata == null ? "" : metadata.getAlbum());
                    metadata = mFallbackMetadata;
                }

                Intent contentIntent = Tab.createBringTabToFrontIntent(mTab.getId());
                if (contentIntent != null) {
                    contentIntent.putExtra(MediaNotificationUma.INTENT_EXTRA_NAME,
                            MediaNotificationUma.SOURCE_MEDIA);
                }

                mNotificationInfoBuilder =
                        new MediaNotificationInfo.Builder()
                                .setMetadata(metadata)
                                .setPaused(isPaused)
                                .setOrigin(mOrigin)
                                .setTabId(mTab.getId())
                                .setPrivate(mTab.isIncognito())
                                .setIcon(R.drawable.audio_playing)
                                .setLargeIcon(mFavicon)
                                .setDefaultLargeIcon(R.drawable.audio_playing_square)
                                .setActions(MediaNotificationInfo.ACTION_PLAY_PAUSE
                                        | MediaNotificationInfo.ACTION_SWIPEAWAY)
                                .setContentIntent(contentIntent)
                                .setId(R.id.media_playback_notification)
                                .setListener(mControlsListener);

                MediaNotificationManager.show(ContextUtils.getApplicationContext(),
                        mNotificationInfoBuilder.build());

                Activity activity = getActivityFromTab(mTab);
                if (activity != null) {
                    activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
                }
            }
        };
    }

    private void setWebContents(WebContents webContents) {
        if (mWebContents == webContents) return;

        cleanupWebContents();
        mWebContents = webContents;
        if (mWebContents != null) mWebContentsObserver = createWebContentsObserver(mWebContents);
    }

    private void cleanupWebContents() {
        if (mWebContentsObserver != null) mWebContentsObserver.destroy();
        mWebContentsObserver = null;
        mWebContents = null;
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

            if (mNotificationInfoBuilder == null) return;

            mNotificationInfoBuilder.setLargeIcon(mFavicon);
            MediaNotificationManager.show(
                    ContextUtils.getApplicationContext(), mNotificationInfoBuilder.build());
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

            if (mNotificationInfoBuilder == null) return;

            mNotificationInfoBuilder.setOrigin(mOrigin);
            mNotificationInfoBuilder.setLargeIcon(mFavicon);
            MediaNotificationManager.show(
                    ContextUtils.getApplicationContext(), mNotificationInfoBuilder.build());
        }

        @Override
        public void onTitleUpdated(Tab tab) {
            assert tab == mTab;
            if (mNotificationInfoBuilder == null || mFallbackMetadata == null) return;

            mFallbackMetadata = new MediaMetadata(mFallbackMetadata);
            mFallbackMetadata.setTitle(sanitizeMediaTitle(mTab.getTitle()));
            mNotificationInfoBuilder.setMetadata(mFallbackMetadata);

            MediaNotificationManager.show(ContextUtils.getApplicationContext(),
                    mNotificationInfoBuilder.build());
        }

        @Override
        public void onDestroyed(Tab tab) {
            assert mTab == tab;

            cleanupWebContents();

            hideNotification();
            mTab.removeObserver(this);
            mTab = null;
        }
    };

    private MediaSessionTabHelper(Tab tab) {
        mTab = tab;
        mTab.addObserver(mTabObserver);
        if (mTab.getWebContents() != null) setWebContents(tab.getWebContents());

        Activity activity = getActivityFromTab(mTab);
        if (activity != null) {
            mPreviousVolumeControlStream = activity.getVolumeControlStream();
        }
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
}
