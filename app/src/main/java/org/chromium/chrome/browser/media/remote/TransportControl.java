// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * TransportControl is an abstract base class for various UI views that are intended to control
 * video playback. TransportControl contains a number of setters that will update the
 * TransportControl's UI.
 * Call {@code TransportControl#addListener} with an implementation of
 * {@code TransportControl.Listener} to receive messages when the user interacts with the
 * TransportControl.
 */
public abstract class TransportControl {

    /**
     * Base interface for classes that need to listen to transport control events.
     */
    public static interface Listener {
        void onPlay();
        void onPause();
        void onSeek(int position);
        void onStop();
    }

    // Initialized lazily to simplify testing. Should only ever be accessed through getListeners to
    // ensure correct initialization.
    private Set<Listener> mListeners;
    private String mScreenName;
    private String mError;
    protected RemoteVideoInfo mVideoInfo;
    private Bitmap mPosterBitmap;

    /**
     * @return the screen name previously assigned with {@link #setScreenName(String)}.
     */
    public final String getScreenName() {
        return mScreenName;
    }

    /**
     * Sets the name to display for the device on the TransportControl.
     */
    public final void setScreenName(String screenName) {
        if (TextUtils.equals(mScreenName, screenName)) {
            return;
        }

        mScreenName = screenName;
        onScreenNameChanged();
    }

    /**
     * @return the error message previously assigned with {@link #setError(String)}, or
     * {@code null} if {@link #hasError()} returns {@code false}.
     */
    public final String getError() {
        return mError;
    }

    /**
     * Sets the error message to display on the TransportControl.
     * Calling this method with {@code null} or an empty string is equivalent to calling
     * {@link #clearError()}
     */
    public final void setError(String message) {
        String newError = TextUtils.isEmpty(message) ? null : message;
        if (TextUtils.equals(mError, newError)) {
            return;
        }

        mError = newError;
        onErrorChanged();
    }

    /**
     * @return true if an error message is assigned to this TransportControl, otherwise false.
     */
    public final boolean hasError() {
        return mError != null;
    }

    /**
     * Clears the error message previously assigned by {@code #setError(String)}.
     */
    public final void clearError() {
        setError(null);
    }

    /**
     * @return the remote's video information previously assigned with
     * {@link #setVideoInfo(RemoteVideoInfo)}, or {@code null} if the {@link RemoteVideoInfo}
     * has not yet been assigned.
     */
    public final RemoteVideoInfo getVideoInfo() {
        return mVideoInfo;
    }

    /**
     * Sets the remote's video information to display on the TransportControl.
     * @param videoInfo the video information to use.
     */
    public final void setVideoInfo(RemoteVideoInfo videoInfo) {
        if (equal(mVideoInfo, videoInfo)) {
            return;
        }

        mVideoInfo = videoInfo;
        onVideoInfoChanged();
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
        // Note that equality of bitmaps is simply an object comparison, so a copy will be treated
        // as a new bitmap
        if (equal(mPosterBitmap, posterBitmap)) {
            return;
        }

        mPosterBitmap = posterBitmap;
        onPosterBitmapChanged();
    }

    /**
     * Registers a {@link Listener} with the TransportControl.
     * @param listener the Listener to be registered.
     */
    public void addListener(Listener listener) {
        getListeners().add(listener);
    }

    /**
     * Unregisters a {@link Listener} previously registered with {@link #addListener(Listener)}.
     * @param listener the Listener to be removed.
     */
    public void removeListener(Listener listener) {
        getListeners().remove(listener);
    }

    /**
     * Displays the TransportControl to the user.
     * @param initialState the state of the player when this is called
     */
    public abstract void show(PlayerState initialState);

    /**
     * Hides the TransportControl.
     */
    public abstract void hide();

    /**
     * Updates the corresponding route controller.
     */
    public abstract void setRouteController(MediaRouteController controller);

    /**
     * @return the current list of listeners.
     */
    protected final Set<Listener> getListeners() {
        if (mListeners == null) mListeners = new CopyOnWriteArraySet<Listener>();
        return mListeners;
    }

    protected void onScreenNameChanged() {}
    protected void onErrorChanged() {}
    protected void onVideoInfoChanged() {}
    protected void onPosterBitmapChanged() {}

    private static boolean equal(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}