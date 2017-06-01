// Copyright 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;

/**
 * Interface to implement to handle swipe from edge of the screen.
 */
public interface EdgeSwipeHandler {
    /**
     * Called when the swipe animation get initiated. It gives a chance to initialize everything.
     * @param direction The direction the swipe is in.
     * @param x         The horizontal coordinate the swipe started at in dp.
     * @param y         The vertical coordinate the swipe started at in dp.
     */
    public void swipeStarted(ScrollDirection direction, float x, float y);

    /**
     * Called each time the swipe gets a new event updating the swipe position.
     * @param x  The horizontal coordinate the swipe is currently at in dp.
     * @param y  The vertical coordinate the swipe is currently at in dp.
     * @param dx The horizontal delta since the last update in dp.
     * @param dy The vertical delta since the last update in dp.
     * @param tx The horizontal difference between the start and the current position in dp.
     * @param ty The vertical difference between the start and the current position in dp.
     */
    public void swipeUpdated(float x, float y, float dx, float dy, float tx, float ty);

    /**
     * Called when the swipe ends; most likely on finger up event. It gives a chance to start
     * an ending animation to exit the mode gracefully.
     */
    public void swipeFinished();

    /**
     * Called when a fling happens while in a swipe.
     * @param x  The horizontal coordinate the swipe is currently at in dp.
     * @param y  The vertical coordinate the swipe is currently at in dp.
     * @param tx The horizontal difference between the start and the current position in dp.
     * @param ty The vertical difference between the start and the current position in dp.
     * @param vx The horizontal velocity of the fling.
     * @param vy The vertical velocity of the fling.
     */
    public void swipeFlingOccurred(float x, float y, float tx, float ty, float vx, float vy);

    /**
     * Gives the handler a chance to determine whether or not this type of swipe is currently
     * allowed.
     * @param direction The direction of the swipe.
     * @return Whether or not the swipe is allowed.
     */
    public boolean isSwipeEnabled(ScrollDirection direction);
}
