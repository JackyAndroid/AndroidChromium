// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.media.MediaPlayerBridge;

/**
 * Acts as a proxy between the remotely playing video and the HTMLMediaElement.
 *
 * Note that the only reason this derives from MediaPlayerBridge is that the
 * MediaPlayerListener takes a MediaPlayerBridge in its constructor.
 * TODO(aberent) fix this by creating a MediaPlayerBridgeInterface (or similar).
 */
@JNINamespace("remote_media")
public class RemoteMediaPlayerBridge extends MediaPlayerBridge {
    private final long mStartPositionMillis;
    private long mNativeRemoteMediaPlayerBridge;

    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;

    private final MediaRouteController mRouteController;
    private final String mFrameUrl;
    private final boolean mDebug;
    private final String mSourceUrl;
    private final String mUserAgent;
    private Bitmap mPosterBitmap;
    private String mCookies;
    private boolean mPauseRequested;
    private boolean mSeekRequested;
    private int mSeekLocation;

    // mActive is true when the Chrome is playing, or preparing to play, this player's video
    // remotely.
    private boolean mActive = false;

    private static final String TAG = "RemoteMediaPlayerBridge";

    private final MediaRouteController.MediaStateListener mMediaStateListener =
            new MediaRouteController.MediaStateListener() {
        @Override
        public void onRouteAvailabilityChanged(boolean available) {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            nativeOnRouteAvailabilityChanged(mNativeRemoteMediaPlayerBridge, available);
        }

        @Override
        public void onError() {
            if (mActive && mOnErrorListener != null) {
                @SuppressLint("InlinedApi")
                int errorExtra = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        ? MediaPlayer.MEDIA_ERROR_TIMED_OUT
                        : 0;
                mOnErrorListener.onError(null, MediaPlayer.MEDIA_ERROR_UNKNOWN, errorExtra);
            }
        }

        @Override
        public void onSeekCompleted() {
            if (mActive && mOnSeekCompleteListener != null) {
                mOnSeekCompleteListener.onSeekComplete(null);
            }
        }

        @Override
        public void onRouteUnselected() {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            nativeOnRouteUnselected(mNativeRemoteMediaPlayerBridge);
        }

        @Override
        public void onPlaybackStateChanged(PlayerState newState) {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            if (newState == PlayerState.FINISHED || newState == PlayerState.INVALIDATED) {
                onCompleted();
                nativeOnPlaybackFinished(mNativeRemoteMediaPlayerBridge);
            } else if (newState == PlayerState.PLAYING) {
                nativeOnPlaying(mNativeRemoteMediaPlayerBridge);
            } else if (newState == PlayerState.PAUSED) {
                nativeOnPaused(mNativeRemoteMediaPlayerBridge);
            }
        }

        @Override
        public String getTitle() {
            if (mNativeRemoteMediaPlayerBridge == 0) return null;
            return nativeGetTitle(mNativeRemoteMediaPlayerBridge);
        }

        @Override
        public Bitmap getPosterBitmap() {
            return mPosterBitmap;
        }

        @Override
        public void pauseLocal() {
            nativePauseLocal(mNativeRemoteMediaPlayerBridge);
        }

        @Override
        public int getLocalPosition() {
            return nativeGetLocalPosition(mNativeRemoteMediaPlayerBridge);
        }

        @Override
        public void onCastStarting(String routeName) {
            nativeOnCastStarting(mNativeRemoteMediaPlayerBridge,
                    RemoteMediaPlayerController.instance().getCastingMessage(routeName));
            mActive = true;
        }

        @Override
        public void onCastStopping() {
            nativeOnCastStopping(mNativeRemoteMediaPlayerBridge);
            mActive = false;
        }

        @Override
        public String getSourceUrl() {
            return mSourceUrl;
        }

        @Override
        public String getCookies() {
            return mCookies;
        }

        @Override
        public String getUserAgent() {
            return mUserAgent;
        }

        @Override
        public String getFrameUrl() {
            return mFrameUrl;
        }

        @Override
        public long getStartPositionMillis() {
            return mStartPositionMillis;
        }

        @Override
        public boolean isPauseRequested() {
            return mPauseRequested;
        }

        @Override
        public boolean isSeekRequested() {
            return mSeekRequested;
        }

        @Override
        public int getSeekLocation() {
            return mSeekLocation;
        }
    };

    private RemoteMediaPlayerBridge(long nativeRemoteMediaPlayerBridge, long startPositionMillis,
            String sourceUrl, String frameUrl, String userAgent) {

        mDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);

        if (mDebug) Log.i(TAG, "Creating RemoteMediaPlayerBridge");
        mNativeRemoteMediaPlayerBridge = nativeRemoteMediaPlayerBridge;
        mStartPositionMillis = startPositionMillis;
        mSourceUrl = sourceUrl;
        mFrameUrl = frameUrl;
        mUserAgent = userAgent;
        // This will get null if there isn't a mediaRouteController that can play this media.
        mRouteController = RemoteMediaPlayerController.instance()
                .getMediaRouteController(sourceUrl, frameUrl);
    }

    @CalledByNative
    private static RemoteMediaPlayerBridge create(long nativeRemoteMediaPlayerBridge,
            long startPositionMillis, String sourceUrl, String frameUrl, String userAgent) {
        return new RemoteMediaPlayerBridge(nativeRemoteMediaPlayerBridge, startPositionMillis,
                sourceUrl, frameUrl, userAgent);
    }

    /**
     * Called when a lower layer requests that a video be cast. This will typically be a request
     * from Blink when the cast button is pressed on the default video controls.
     */
    @CalledByNative
    private void requestRemotePlayback() {
        if (mDebug) Log.i(TAG, "requestRemotePlayback");
        if (mRouteController == null) return;
        // Clear out the state
        mPauseRequested = false;
        mSeekRequested = false;
        RemoteMediaPlayerController.instance().requestRemotePlayback(
                mMediaStateListener, mRouteController);
    }

    /**
     * Called when a lower layer requests control of a video that is being cast.
     */
    @CalledByNative
    private void requestRemotePlaybackControl() {
        if (mDebug) Log.i(TAG, "requestRemotePlaybackControl");
        RemoteMediaPlayerController.instance().requestRemotePlaybackControl(mMediaStateListener);
    }

    @CalledByNative
    private void setNativePlayer() {
        if (mDebug) Log.i(TAG, "setNativePlayer");
        if (mRouteController == null) return;
        mRouteController.setMediaStateListener(mMediaStateListener);
        mActive = true;
    }

    @CalledByNative
    private void onPlayerCreated() {
        if (mDebug) Log.i(TAG, "onPlayerCreated");
        if (mRouteController == null) return;
        mRouteController.addMediaStateListener(mMediaStateListener);
    }

    @CalledByNative
    private void onPlayerDestroyed() {
        if (mDebug) Log.i(TAG, "onPlayerDestroyed");
        if (mRouteController == null) return;
        mRouteController.removeMediaStateListener(mMediaStateListener);
    }

    /**
     * @param bitmap The bitmap of the poster for the video, null if no poster image exists.
     *
     *         TODO(cimamoglu): Notify the clients (probably through MediaRouteController.Listener)
     *        of the poster image change. This is necessary for when a web page changes the poster
     *        while the client (i.e. only ExpandedControllerActivity for now) is active.
     */
    @CalledByNative
    private void setPosterBitmap(Bitmap bitmap) {
        if (mRouteController == null) return;
        mPosterBitmap = bitmap;
    }

    @Override
    @CalledByNative
    protected boolean isPlaying() {
        if (mRouteController == null) return false;
        return mRouteController.isPlaying();
    }

    @Override
    @CalledByNative
    protected int getCurrentPosition() {
        if (mRouteController == null) return 0;
        return mRouteController.getPosition();
    }

    @Override
    @CalledByNative
    protected int getDuration() {
        if (mRouteController == null) return 0;
        return mRouteController.getDuration();
    }

    @Override
    @CalledByNative
    protected void release() {
        // Remove the state change listeners. Release does mean that Chrome is no longer interested
        // in events from the media player.
        if (mRouteController != null) mRouteController.setMediaStateListener(null);
        mActive = false;
    }

    @Override
    @CalledByNative
    protected void setVolume(double volume) {
    }

    @Override
    @CalledByNative
    protected void start() throws IllegalStateException {
        mPauseRequested = false;
        if (mRouteController != null && mRouteController.isBeingCast()) mRouteController.resume();
    }

    @Override
    @CalledByNative
    protected void pause() throws IllegalStateException {
        mPauseRequested = true;
        if (mRouteController != null && mRouteController.isBeingCast()) mRouteController.pause();
    }

    @Override
    @CalledByNative
    protected void seekTo(int msec) throws IllegalStateException {
        mSeekRequested = true;
        mSeekLocation = msec;
        if (mRouteController != null && mRouteController.isBeingCast()) {
            mRouteController.seekTo(msec);
        }
    }

    @Override
    protected boolean setDataSource(
            Context context, String url, String cookies, String userAgent, boolean hideUrlLog) {
        return true;
    }

    @Override
    protected void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener listener) {
    }

    @Override
    protected void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    @Override
    protected void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    @Override
    protected void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    @Override
    protected void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
    }

    @Override
    protected void setOnVideoSizeChangedListener(MediaPlayer.OnVideoSizeChangedListener listener) {
    }

    /**
     * Called when the video finishes
     */
    public void onCompleted() {
        if (mActive && mOnCompletionListener != null) {
            mOnCompletionListener.onCompletion(null);
        }
    }

    @Override
    @CalledByNative
    protected void destroy() {
        if (mDebug) Log.i(TAG, "destroy");
        if (mRouteController != null) {
            mRouteController.removeMediaStateListener(mMediaStateListener);
        }
        mNativeRemoteMediaPlayerBridge = 0;
    }

    @CalledByNative
    private boolean takesOverCastDevice() {
        if (mRouteController == null) return false;
        return mRouteController.playerTakesOverCastDevice(mMediaStateListener);
    }

    @CalledByNative
    private void setCookies(String cookies) {
        mCookies = cookies;
    }

    private native String nativeGetFrameUrl(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnPlaying(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnPaused(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnRouteUnselected(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnPlaybackFinished(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnRouteAvailabilityChanged(long nativeRemoteMediaPlayerBridge,
            boolean available);
    private native String nativeGetTitle(long nativeRemoteMediaPlayerBridge);
    private native void nativePauseLocal(long nativeRemoteMediaPlayerBridge);
    private native int nativeGetLocalPosition(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnCastStarting(long nativeRemoteMediaPlayerBridge,
            String castingMessage);
    private native void nativeOnCastStopping(long nativeRemoteMediaPlayerBridge);
}
