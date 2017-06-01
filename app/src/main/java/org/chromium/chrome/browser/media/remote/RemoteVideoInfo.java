// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.text.TextUtils;

/**
 * Exposes information about the current video to the external clients.
 */
public class RemoteVideoInfo {

    /**
     * This lists all the states that the remote video can be in.
     */
    public enum PlayerState {
        /** The remote player is currently stopped. */
        STOPPED,

        /** The remote player is buffering this video. */
        LOADING,

        /** The remote player is playing this video. */
        PLAYING,

        /** The remote player is paused. */
        PAUSED,

        /** The remote player is in an error state. */
        ERROR,

        /** The remote player has been replaced by another player (so the current session has
         * finished) */
        INVALIDATED,

        /** The remote video has completed playing. */
        FINISHED
    }

    /**
     * The title of the video
     */
    public String title;
    /**
     * The duration of the video
     */
    public long durationMillis;
    /**
     * The current state of the video
     */
    public PlayerState state;
    /**
     * The last known position in the video
     */
    public long currentTimeMillis;
    /**
     * The current error message, if any
     */
    // TODO(aberent) At present nothing sets this to anything other than Null.
    public String errorMessage;

    /**
     * Create a new RemoteVideoInfo
     * @param title
     * @param durationMillis
     * @param state
     * @param currentTimeMillis
     * @param errorMessage
     */
    public RemoteVideoInfo(String title, long durationMillis, PlayerState state,
            long currentTimeMillis, String errorMessage) {
        this.title = title;
        this.durationMillis = durationMillis;
        this.state = state;
        this.currentTimeMillis = currentTimeMillis;
        this.errorMessage = errorMessage;
    }

    /**
     * Copy a remote video info
     * @param other the source.
     */
    public RemoteVideoInfo(RemoteVideoInfo other) {
        this(other.title, other.durationMillis, other.state, other.currentTimeMillis,
                other.errorMessage);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof RemoteVideoInfo)) {
            return false;
        }

        RemoteVideoInfo other = (RemoteVideoInfo) obj;
        return durationMillis == other.durationMillis
               && currentTimeMillis == other.currentTimeMillis
               && state == other.state
               && TextUtils.equals(title, other.title)
               && TextUtils.equals(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
        int result = (int) durationMillis;
        result = 31 * result + (int) (durationMillis >> 32);
        result = 31 * result + (int) currentTimeMillis;
        result = 31 * result + (int) (currentTimeMillis >> 32);
        result = 31 * result + (title == null ? 0 : title.hashCode());
        result = 31 * result + (state == null ? 0 : state.hashCode());
        result = 31 * result + (errorMessage == null ? 0 : errorMessage.hashCode());
        return result;
    }
}
