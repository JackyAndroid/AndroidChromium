// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * An abstract base class and factory for {@link TransportControl}s that are displayed on the lock
 * screen.
 */
public abstract class LockScreenTransportControl
        extends TransportControl implements MediaRouteController.UiListener {
    private static final String TAG = "LockScreenTransportControl";

    private static LockScreenTransportControl sInstance;

    private MediaRouteController mMediaRouteController = null;

    private static final Object LOCK = new Object();

    private static boolean sDebug;

    // Needed to get around findbugs complaints.
    private static void setSDebug() {
        sDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);
    }

    protected LockScreenTransportControl() {
        setSDebug();
    }

    /**
     * {@link BroadcastReceiver} that receives the media button events from the lock screen and
     * forwards the messages on to the {@link TransportControl}'s listeners.
     *
     * Ideally this class should be private, but public is required to create as a
     * BroadcastReceiver.
     */
    public static class MediaButtonIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (sDebug) Log.d(TAG, "Received intent: " + intent);
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                LockScreenTransportControl control = LockScreenTransportControl.getIfExists();
                if (control == null) {
                    Log.w(TAG, "Event received when no LockScreenTransportControl exists");
                    return;
                }
                Set<Listener> listeners = control.getListeners();

                // Ignore ACTION_DOWN. We'll get an ACTION_UP soon enough!
                if (event.getAction() == KeyEvent.ACTION_DOWN) return;

                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        for (Listener listener : listeners) {
                            if (control.isPlaying()) {
                                listener.onPause();
                            } else {
                                listener.onPlay();
                            }
                        }
                        break;
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        for (Listener listener : listeners) listener.onStop();
                        break;
                    default:
                        Log.w(TAG, "Unrecognized event: " + event);
                }
            }
        }
    }


    /**
     * Get the unique LockScreenTransportControl, creating it if necessary.
     * @param context The context of the activity
     * @param mediaRouteController The current mediaRouteController, if any.
     * @return a {@code LockScreenTransportControl} based on the platform's SDK API or null if the
     *         current platform's SDK API is not supported.
     */
    public static LockScreenTransportControl getOrCreate(Context context,
            @Nullable MediaRouteController mediaRouteController) {
        Log.d(TAG, "getOrCreate called");
        synchronized (LOCK) {
            if (sInstance == null) {

                // TODO(aberent) Investigate disabling the lock screen for Android L. It is
                // supposedly deprecated, but the still seems to be the only way of controlling the
                // wallpaper (which we set to the poster of the current video) when the phone is
                // locked. Also, once the minSdkVersion is updated in the manifest, get rid of the
                // code for older SDK versions.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    sInstance = new LockScreenTransportControlV16(context);
                } else {
                    sInstance = new LockScreenTransportControlV18(context);
                }
            }
            sInstance.setVideoInfo(
                    new RemoteVideoInfo(null, 0, RemoteVideoInfo.PlayerState.STOPPED, 0, null));

            sInstance.mMediaRouteController = mediaRouteController;
            return sInstance;
        }
    }

    protected MediaRouteController getMediaRouteController() {
        return mMediaRouteController;
    }

    /**
     * Internal function for callbacks that need to get the current lock screen statically, but
     * don't want to create a new one.
     *
     * @return the current lock screen, if any.
     */
    @VisibleForTesting
    static LockScreenTransportControl getIfExists() {
        return sInstance;
    }

    @Override
    public void hide() {
        onLockScreenPlaybackStateChanged(null, PlayerState.STOPPED);
        mMediaRouteController.removeUiListener(this);
    }

    @Override
    public void show(PlayerState initialState) {
        mMediaRouteController.addUiListener(this);
        onLockScreenPlaybackStateChanged(null, initialState);
    }

    @Override
    public void setRouteController(MediaRouteController controller) {
        synchronized (LOCK) {
            if (sInstance != null) sInstance.mMediaRouteController = controller;
        }
    }

    @Override
    public void onPlaybackStateChanged(PlayerState oldState, PlayerState newState) {
        onLockScreenPlaybackStateChanged(oldState, newState);
    }

    protected abstract void onLockScreenPlaybackStateChanged(PlayerState oldState,
            PlayerState newState);

    protected abstract boolean isPlaying();
}
