// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * Encapsulates the state of a recent Tap gesture; x, y position and if suppressed.
 * Instances of this class are immutable.
 */
class ContextualSearchTapState {
    private final float mX;
    private final float mY;
    private final long mTapTimeNanoseconds;
    private final boolean mWasSuppressed;

    /**
     * Constructs a Tap at the given x,y position and indicates if the tap was suppressed or not.
     * @param x The x coordinate of the current tap.
     * @param y The y coordinate of the current tap.
     * @param tapTimeNanoseconds The timestamp when the Tap occurred.
     * @param wasSuppressed Whether this tap was suppressed for any reason.
     */
    ContextualSearchTapState(float x, float y, long tapTimeNanoseconds, boolean wasSuppressed) {
        mX = x;
        mY = y;
        mTapTimeNanoseconds = tapTimeNanoseconds;
        mWasSuppressed = wasSuppressed;
    }

    /**
     * @return The x coordinate of the Tap.
     */
    float getX() {
        return mX;
    }

    /**
     * @return The y coordinate of the Tap.
     */
    float getY() {
        return mY;
    }

    /**
     * @return The time of the Tap in nanoseconds.
     */
    long tapTimeNanoseconds() {
        return mTapTimeNanoseconds;
    }

    /**
     * @return Whether this Tap was suppressed.
     */
    boolean wasSuppressed() {
        return mWasSuppressed;
    }
}
