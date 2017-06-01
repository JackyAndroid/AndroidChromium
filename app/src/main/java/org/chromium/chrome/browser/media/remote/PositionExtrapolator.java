// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.os.SystemClock;

/**
 * Class for extrapolating current playback position. The class occasionally receives updated
 * playback position information from the MediaRoute, and extrapolates the current playback
 * position.
 */
public class PositionExtrapolator {
    private static final String TAG = "MediaFling";

    private long mDuration;
    private long mLastKnownPosition;
    private long mTimestamp;
    private boolean mIsPlaying;

    public PositionExtrapolator() {
        mDuration = 0;
        mLastKnownPosition = 0;
        mTimestamp = 0;
        mIsPlaying = false;
    }

    /**
     * Clears the duration and timestamp.
     */
    public void clear() {
        // Note: do not clear the stream position, since this is still needed so
        // that we can reset the local stream position to match.
        mDuration = 0;
        mTimestamp = 0;
    }

    /**
     * Update the extrapolator with the latest position info.
     * @param duration The new duration.
     * @param position The new playback position.
     * @param timestamp The time stamp of this info, must be directly or indirectly aquired via
     * {@link SystemClock.elapsedRealtime()}. The time stamp from the Cast receiver uses
     * elapsedRealtime, so it can be used here. Don't use {@link SystemClock.uptimeMillis()} since
     * it doesn't include the device sleep time.
     */
    public void onPositionInfoUpdated(
            long duration, long position, long timestamp) {
        mDuration = Math.max(duration, 0);
        mLastKnownPosition = Math.min(mDuration, Math.max(position, 0));
        mTimestamp = timestamp;
    }

    /**
     * Must be called whenever the remote playback is paused. If the playback
     * state changes from playing to paused, the last known stream position will be updated by the
     * extrapolated value.
     */
    public void onPaused() {
        if (!mIsPlaying) return;

        long elapsedTime = SystemClock.elapsedRealtime();
        onPositionInfoUpdated(mDuration, getPositionWithElapsedTime(elapsedTime), elapsedTime);

        mIsPlaying = false;
    }

    /**
     * Must be called whenever the remote playback resumes. If the playback state changes from
     * paused to playing, the timestamp will be updated by the current {@link
     * SystemClock.elapsedRealtime()}.
     */
    public void onResumed() {
        if (mIsPlaying) return;

        onPositionInfoUpdated(mDuration, mLastKnownPosition, SystemClock.elapsedRealtime());
        mIsPlaying = true;
    }

    /**
     * Must be called whenever the remote playback is finished. The last known position will be
     * updated by the duration.
     */
    public void onFinished() {
        onPositionInfoUpdated(mDuration, mDuration, SystemClock.elapsedRealtime());
        mIsPlaying = false;
    }

    /**
     * Must be called whenever the remote playback is seeking. The last known position will be
     * updated by the position to be seeked to, and the extrapolator will assume the playback is
     * paused during seeking.
     * @param position The position to seek to.
     */
    public void onSeek(long position) {
        onPositionInfoUpdated(mDuration, position, SystemClock.elapsedRealtime());
        mIsPlaying = false;
    }

    /**
     * @return The current playback position.
     */
    public long getPosition() {
        return getPositionWithElapsedTime(SystemClock.elapsedRealtime());
    }

    /**
     * @return The duration of the remote media.
     */
    public long getDuration() {
        return mDuration;
    }

    private long getPositionWithElapsedTime(long elapsedTime) {
        if ((mTimestamp == 0) || !mIsPlaying || mLastKnownPosition >= mDuration) {
            return mLastKnownPosition;
        }

        return Math.min(mLastKnownPosition + (elapsedTime - mTimestamp), mDuration);
    }
}
