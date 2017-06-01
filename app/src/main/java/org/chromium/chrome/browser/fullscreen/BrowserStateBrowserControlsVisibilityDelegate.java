// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * Determines the desired visibility of the browser controls based on the current state of the
 * running activity.
 */
public class BrowserStateBrowserControlsVisibilityDelegate
        implements BrowserControlsVisibilityDelegate {

    private static final int TRANSIENT_SHOW_MSG_ID = 1;
    /** Minimum duration (in milliseconds) that the controls are shown when requested. */
    protected static final long MINIMUM_SHOW_DURATION_MS = 3000;

    private static boolean sDisableOverridesForTesting;

    private final Set<Integer> mPersistentControlTokens = new HashSet<Integer>();
    private final Handler mHandler;
    private final Runnable mStateChangedCallback;

    private long mCurrentShowTime;
    private int mPersistentControlsCurrentToken;

    // This static inner class holds a WeakReference to the outer object, to avoid triggering the
    // lint HandlerLeak warning.
    private static class VisibilityDelegateHandler extends Handler {
        private final WeakReference<BrowserStateBrowserControlsVisibilityDelegate> mDelegateRef;

        public VisibilityDelegateHandler(BrowserStateBrowserControlsVisibilityDelegate delegate) {
            mDelegateRef = new WeakReference<>(delegate);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg == null) return;
            BrowserStateBrowserControlsVisibilityDelegate delegate = mDelegateRef.get();
            if (delegate == null) return;
            if (msg.what != TRANSIENT_SHOW_MSG_ID) {
                assert false;
                return;
            }
            delegate.releaseToken(msg.arg1);
        }
    }

    /**
     * Constructs a BrowserControlsVisibilityDelegate designed to deal with overrides driven by
     * the browser UI (as opposed to the state of the tab).
     *
     * @param stateChangedCallback The callback to be triggered when the fullscreen state should be
     *                             updated based on the state of the browser visibility override.
     */
    public BrowserStateBrowserControlsVisibilityDelegate(Runnable stateChangedCallback) {
        mHandler = new VisibilityDelegateHandler(this);
        mStateChangedCallback = stateChangedCallback;
    }

    private void ensureControlsVisibleForMinDuration() {
        if (mHandler.hasMessages(TRANSIENT_SHOW_MSG_ID)) return;

        long timeDelta = SystemClock.uptimeMillis() - mCurrentShowTime;
        if (timeDelta >= MINIMUM_SHOW_DURATION_MS) return;

        Message msg = mHandler.obtainMessage(TRANSIENT_SHOW_MSG_ID);
        msg.arg1 = generateToken();
        mHandler.sendMessageDelayed(msg, Math.max(MINIMUM_SHOW_DURATION_MS - timeDelta, 0));
    }

    private int generateToken() {
        int token = mPersistentControlsCurrentToken++;
        mPersistentControlTokens.add(token);
        if (mPersistentControlTokens.size() == 1) mStateChangedCallback.run();
        return token;
    }

    private void releaseToken(int token) {
        if (mPersistentControlTokens.remove(token)
                && mPersistentControlTokens.isEmpty()) {
            mStateChangedCallback.run();
        }
    }

    /**
     * Trigger a temporary showing of the browser controls.
     */
    public void showControlsTransient() {
        if (mPersistentControlTokens.isEmpty()) mCurrentShowTime = SystemClock.uptimeMillis();

        ensureControlsVisibleForMinDuration();
    }

    /**
     * Trigger a permanent showing of the browser controls until requested otherwise.
     *
     * @return The token that determines whether the requester still needs persistent controls to
     *         be present on the screen.
     * @see #hideControlsPersistent(int)
     */
    public int showControlsPersistent() {
        if (mPersistentControlTokens.isEmpty()) mCurrentShowTime = SystemClock.uptimeMillis();
        return generateToken();
    }

    /**
     * Same behavior as {@link #showControlsPersistent()} but also handles removing a previously
     * requested token if necessary.
     *
     * @param oldToken The old fullscreen token to be cleared.
     * @return The fullscreen token as defined in {@link #showControlsPersistent()}.
     */
    public int showControlsPersistentAndClearOldToken(int oldToken) {
        int newToken = showControlsPersistent();
        if (oldToken != FullscreenManager.INVALID_TOKEN) releaseToken(oldToken);
        return newToken;
    }

    /**
     * Notify the manager that the browser controls are no longer required for the given token.
     *
     * @param token The fullscreen token returned from {@link #showControlsPersistent()}.
     */
    public void hideControlsPersistent(int token) {
        if (mPersistentControlTokens.isEmpty()) return;
        if (mPersistentControlTokens.size() == 1 && mPersistentControlTokens.contains(token)) {
            ensureControlsVisibleForMinDuration();
        }
        releaseToken(token);
    }

    @Override
    public boolean isShowingBrowserControlsEnabled() {
        return true;
    }

    @Override
    public boolean isHidingBrowserControlsEnabled() {
        return sDisableOverridesForTesting || mPersistentControlTokens.isEmpty();
    }

    /**
     * Disable any browser visibility overrides for testing.
     */
    public static void disableForTesting() {
        sDisableOverridesForTesting = true;
    }
}
