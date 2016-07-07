// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

/**
 * An implementation of {@link LockScreenTransportControl} targeting platforms with an API greater
 * than 17. Extends {@link LockScreenTransportControlV16}, adding support for seeking.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
// This whole class is based upon RemoteControlClient, which is deprecated (and non-functional) in
// the Android L SDK. Once the L SDK is released we will add code in LockSceenTransportControl to
// prevent the use of this class on Android L. See {@link LockScreenTransportControl}.
@SuppressWarnings("deprecation")
class LockScreenTransportControlV18 extends LockScreenTransportControlV16 {

    private final PlaybackPositionUpdateListener mPlaybackPositionUpdateListener;
    private final GetPlaybackPositionUpdateListener mGetPlaybackPositionUpdateListener;

    LockScreenTransportControlV18(Context context) {
        super(context);
        mPlaybackPositionUpdateListener = new PlaybackPositionUpdateListener();
        mGetPlaybackPositionUpdateListener = new GetPlaybackPositionUpdateListener();
    }

    @Override
    protected void register() {
        super.register();
        getRemoteControlClient().setPlaybackPositionUpdateListener(mPlaybackPositionUpdateListener);
        getRemoteControlClient().setOnGetPlaybackPositionListener(
                mGetPlaybackPositionUpdateListener);
    }

    @Override
    protected void unregister() {
        getRemoteControlClient().setOnGetPlaybackPositionListener(null);
        getRemoteControlClient().setPlaybackPositionUpdateListener(null);
        super.unregister();
    }

    @Override
    protected void updatePlaybackState(int state) {
        RemoteVideoInfo videoInfo = getVideoInfo();
        if (videoInfo != null && getRemoteControlClient() != null) {
            getRemoteControlClient().setPlaybackState(state, videoInfo.currentTimeMillis, 1.0f);
        } else {
            super.updatePlaybackState(state);
        }
    }

    @Override
    protected int getTransportControlFlags() {
        return super.getTransportControlFlags()
                | android.media.RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;
    }

    private class GetPlaybackPositionUpdateListener implements
            android.media.RemoteControlClient.OnGetPlaybackPositionListener {

        @Override
        public long onGetPlaybackPosition() {
            RemoteVideoInfo videoInfo = getVideoInfo();
            return videoInfo == null ? 0 : videoInfo.currentTimeMillis;
        }
    }

    private class PlaybackPositionUpdateListener implements
            android.media.RemoteControlClient.OnPlaybackPositionUpdateListener {

        @Override
        public void onPlaybackPositionUpdate(long position) {
            for (Listener listener : getListeners()) listener.onSeek((int) position);
        }
    }
}
