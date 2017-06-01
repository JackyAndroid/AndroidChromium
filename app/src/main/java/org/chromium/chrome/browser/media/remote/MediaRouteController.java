// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.graphics.Bitmap;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;

/**
 * Each MediaRouteController controls the routes to devices which support remote playback of
 * particular categories of Media elements (e.g. all YouTube media elements, all media elements
 * with simple http source URLs). The MediaRouteController is responsible for configuring
 * and controlling remote playback of the media elements it supports.
 */
public interface MediaRouteController {
    /**
     * Listener for events that are relevant to the state of the media and the media controls
     */
    public interface MediaStateListener {
        /**
         * Called when the first route becomes available, or the last route
         * is removed.
         * @param available whether routes are available.
         */
        void onRouteAvailabilityChanged(boolean available);

        /**
         * Called when the {@link MediaRouteChooserDialog} is closed with no device selected.
         */
        void onRouteDialogCancelled();

        /**
         * Called when an error is detected by the media route controller
         */
        void onError();

        /**
         * Called when a seek completes on the current route
         */
        void onSeekCompleted();

        /**
         * Called when the current route is unselected
         */
        void onRouteUnselected();

        /**
         * Called when the playback state changes (e.g. from Playing to Paused)
         * @param newState the new playback state
         */
        void onPlaybackStateChanged(PlayerState newState);

        String getTitle();

        Bitmap getPosterBitmap();

        void pauseLocal();

        long getLocalPosition();

        /**
         * Tells the rest of Chrome that we are starting to cast, so that user inputs control cast
         * in place of local playback
         */
        void onCastStarting(String routeName);

        /**
         * Tells the rest of Chrome that we're connected to the Cast device and about to resume
         * playback.
         */
        void onCastStarted();

        /**
         * Tells the rest of Chrome that we are no longer casting the video.
         */
        void onCastStopping();

        /**
         * @return the source URL
         */
        String getSourceUrl();

        /**
         * @return the Cookies
         */
        String getCookies();

        /**
         * @return the frame URL
         */
        String getFrameUrl();

        /**
         * @return the start position
         */
        long getStartPositionMillis();

        /**
         * @return true if the user has pressed the pause button (or requested pause some other way)
         */
        boolean isPauseRequested();

        /**
         * @return true if the user has requested a seek
         */
        boolean isSeekRequested();

        /**
         * @return the requested seek location. Only meaningful if isSeekRequested is true.
         */
        long getSeekLocation();
    }

    /**
     * Listener for events that are relevant to the Browser UI.
     */
    public interface UiListener {

        /**
         * Called when a new route is selected
         * @param name the name of the new route
         * @param mediaRouteController the controller that selected the route
         */
        void onRouteSelected(String name, MediaRouteController mediaRouteController);

        /**
         * Called when the current route is unselected
         * @param mediaRouteController the controller that had the route.
         */
        void onRouteUnselected(MediaRouteController mediaRouteController);

        /**
         * Called when the current route is ready to be used
         * @param mediaRouteController the controller that has the route.
         */
        void onPrepared(MediaRouteController mediaRouteController);

        /**
         * Called when an error is detected by the controller
         * @param errorType One of the error types from CastMediaControlIntent
         * @param message The message for the error
         */
        void onError(int errorType, String message);

        /**
         * Called when the Playback state has changed (e.g. from playing to paused)
         * @param newState the new state
         */
        void onPlaybackStateChanged(PlayerState newState);

        /**
         * Called when the duration of the currently playing video changes.
         * @param durationMillis the new duration in ms.
         */
        void onDurationUpdated(long durationMillis);

        /**
         * Called when the media route controller receives new information about the
         * current position in the video.
         * @param positionMillis the current position in the video in ms.
         */
        void onPositionChanged(long positionMillis);

        /**
         * Called if the title of the video changes
         * @param title the new title
         */
        void onTitleChanged(String title);
    }

    /**
     * Interface for returning the result of checking whether the media element is playable
     * remotely.
     */
    static interface MediaValidationCallback {
        /**
         * Function to deliver the result
         * @param isPlayable true if the media element is playable, false if not
         * @param revisedSourceUrl The source url to send to the remote device
         * @param revisedFrameUrl The frame url to send to the remote device
         */
        void onResult(boolean isPlayable, String revisedSourceUrl, String revisedFrameUrl);
    }
    /**
     * Scan routes, and set up the MediaRouter object. This is called at every time we need to reset
     * the state. Because of that, this function is idempotent. If that changes in the future, where
     * this function gets called needs to be re-evaluated.
     *
     * @return false if device doesn't support cast, true otherwise.
     */
    boolean initialize();

    /**
     * Can this mediaRouteController handle a media element?
     * @param sourceUrl the source
     * @param frameUrl
     * @return true if it can, false if it can't.
     */
    boolean canPlayMedia(String sourceUrl, String frameUrl);

    /**
     * @return A new MediaRouteSelector filtering the remote playback devices from all the routes.
     */
    MediaRouteSelector buildMediaRouteSelector();

    /**
     * @return Whether there're remote playback devices available.
     */
    boolean isRemotePlaybackAvailable();

    /**
     * @return Whether the currently selected device supports remote playback
     */
    boolean currentRouteSupportsRemotePlayback();

    /**
     * Setup this object to discover new routes and register the necessary players.
     */
    void prepareMediaRoute();

    /**
     * Add a Listener that will listen to events from this object
     *
     * @param listener the Listener that will receive the events
     */
    void addUiListener(UiListener listener);

    /**
     * Removes a Listener from this object
     *
     * @param listener the Listener to remove
     */
    void removeUiListener(UiListener listener);

    /**
     * @return The currently selected route's friendly name, or null if there is none selected
     */
    String getRouteName();

    /**
     * @return true if this is currently using the default route, false if not.
     */
    boolean routeIsDefaultRoute();

    /**
     * Sets the remote volume of the current route.
     *
     * @param delta The delta value in arbitrary "Android Volume Units".
     */
    void setRemoteVolume(int delta);

    /**
     * Resume paused playback of the current video.
     */
    void resume();

    /**
     * Pauses the currently playing video if any.
     */
    void pause();

    /**
     * Returns the current remote playback position. Estimates the current position by using the
     * last known position and the current time.
     *
     *  TODO(avayvod): Send periodic status update requests to update the position once in several
     * seconds or so.
     *
     * @return The current position of the remote playback in milliseconds.
     */
    long getPosition();

    /**
     * @return The stream duration in milliseconds.
     */
    long getDuration();

    /**
     * @return Whether the video is currently being played.
     */
    boolean isPlaying();

    /**
     * @return Whether the video is being cast (any of playing/paused/loading/stopped).
     */
    boolean isBeingCast();

    /**
     * Initiates a seek request for the remote playback device to the specified position.
     *
     * @param msec The position to seek to, in milliseconds.
     */
    void seekTo(long msec);

    /**
     * Stop the current remote playback completely and release all resources.
     */
    void release();

    /**
     * @param player - the current player using this media route controller.
     */
    void setMediaStateListener(MediaStateListener listener);

    /**
     * @return the current VideoStateListener
     */
    MediaStateListener getMediaStateListener();

    @VisibleForTesting
    PlayerState getDisplayedPlayerState();

    /**
     * Remove an existing media state listener
     * @param listener
     */
    void removeMediaStateListener(MediaStateListener listener);

    /**
     * Add a media state listener
     * @param listener
     */
    void addMediaStateListener(MediaStateListener listener);

    /**
     * Get the poster for the video, if any
     * @return the poster bitmap, or Null.
     */
    Bitmap getPoster();

    /**
     * Called when a new route has been selected
     * @param player The player {@link MediaStateListener} that initiated the connection
     * @param router The MediaRouter.
     * @param route The selected route.
     */
    void onRouteSelected(MediaStateListener player, MediaRouter router, RouteInfo route);

    /**
     * Potentially asynchronous check of whether the media element is playable on remote players.
     * @param sourceUrl the URL of the media element
     * @param frameUrl the URL of the frame
     * @param cookies the cookies for the media element
     * @param userAgent the user agent
     * @param callback the callback through which the result will be returned. The callback will be
     *                 called either from within the call, or later on the UI thread.
     */
    void checkIfPlayableRemotely(String sourceUrl, String frameUrl, String cookies,
            String userAgent, MediaValidationCallback callback);

    /**
     * @return The Uri of the currently playing video
     */
    @VisibleForTesting String getUriPlaying();
}
