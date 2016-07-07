// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;

/**
 * Record statistics on interesting cast events and actions.
 */
@JNINamespace("remote_media")
public class RecordCastAction {

    // UMA histogram values for the device types the user could select.
    // Keep in sync with the enum in uma_record_action.cc
    public static final int DEVICE_TYPE_CAST_GENERIC = 0;
    public static final int DEVICE_TYPE_CAST_YOUTUBE = 1;
    public static final int DEVICE_TYPE_NON_CAST_YOUTUBE = 2;
    public static final int DEVICE_TYPE_COUNT = 3;

    /**
     * Record the type of cast receiver we to which we are casting.
     * @param playerType the type of cast receiver.
     */
    public static void remotePlaybackDeviceSelected(int playerType) {
        assert playerType >= 0
                && playerType < RecordCastAction.DEVICE_TYPE_COUNT;
        if (LibraryLoader.isInitialized()) nativeRecordRemotePlaybackDeviceSelected(playerType);
    }

    /**
     * Record that a remote playback was requested. This is intended to record all playback
     * requests, whether they were user initiated or was an auto-playback resulting from the user
     * selecting the device initially.
     */
    public static void castPlayRequested() {
        if (LibraryLoader.isInitialized()) nativeRecordCastPlayRequested();
    }

    /**
     * Record the result of the cast playback request.
     *
     * @param castSucceeded true if the playback succeeded, false if there was an error
     */
    public static void castDefaultPlayerResult(boolean castSucceeded) {
        if (LibraryLoader.isInitialized()) nativeRecordCastDefaultPlayerResult(castSucceeded);
    }

    /**
     * Record the result of casting a YouTube video.
     *
     * @param castSucceeded true if the playback succeeded, false if there was an error
     */
    public static void castYouTubePlayerResult(boolean castSucceeded) {
        if (LibraryLoader.isInitialized()) nativeRecordCastYouTubePlayerResult(castSucceeded);
    }

    /**
     * Record the amount of time remaining on the video when the remote playback stops.
     *
     * @param videoLengthMs the total length of the video in milliseconds
     * @param timeRemainingMs the remaining time in the video in milliseconds
     */
    public static void castEndedTimeRemaining(int videoLengthMs, int timeRemainingMs) {
        if (LibraryLoader.isInitialized()) {
            nativeRecordCastEndedTimeRemaining(videoLengthMs, timeRemainingMs);
        }
    }

    /**
     * Record the type of the media being cast.
     *
     * @param mediaType the type of the media being casted, see media/base/container_names.h for
     *            possible media types.
     */
    public static void castMediaType(int mediaType) {
        if (LibraryLoader.isInitialized()) nativeRecordCastMediaType(mediaType);
    }

    // Cast sending
    private static native void nativeRecordRemotePlaybackDeviceSelected(int deviceType);
    private static native void nativeRecordCastPlayRequested();
    private static native void nativeRecordCastDefaultPlayerResult(boolean castSucceeded);
    private static native void nativeRecordCastYouTubePlayerResult(boolean castSucceeded);
    private static native void nativeRecordCastEndedTimeRemaining(
            int videoLengthMs, int timeRemainingMs);
    private static native void nativeRecordCastMediaType(int mediaType);
}
