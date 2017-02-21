// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.rappor.RapporServiceBridge;

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

    // UMA histogram values for the fullscreen controls the user could tap.
    // Keep in sync with the MediaCommand enum in histograms.xml
    public static final int FULLSCREEN_CONTROLS_RESUME = 0;
    public static final int FULLSCREEN_CONTROLS_PAUSE = 1;
    public static final int FULLSCREEN_CONTROLS_SEEK = 2;
    public static final int FULLSCREEN_CONTROLS_COUNT = 3;

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
    public static void castEndedTimeRemaining(long videoLengthMs, long timeRemainingMs) {
        if (LibraryLoader.isInitialized()) {
            nativeRecordCastEndedTimeRemaining((int) videoLengthMs, (int) timeRemainingMs);
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

    /**
     * Record if the remotely played media element is alive when the
     * {@link ExpandedControllerActivity} is shown.
     *
     * @param isMediaElementAlive if the media element is alive.
     */
    public static void recordFullscreenControlsShown(boolean isMediaElementAlive) {
        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordBooleanHistogram(
                    "Cast.Sender.MediaElementPresentWhenShowFullscreenControls",
                    isMediaElementAlive);
        }
    }

    /**
     * Record when an action was taken on the {@link ExpandedControllerActivity} by the user.
     * Notes if the corresponding media element has been alive at that point in time.
     *
     * @param action one of the FULLSCREEN_CONTROLS_* constants defined above.
     * @param isMediaElementAlive if the media element is alive.
     */
    public static void recordFullscreenControlsAction(int action, boolean isMediaElementAlive) {
        if (!LibraryLoader.isInitialized()) return;

        if (isMediaElementAlive) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Cast.Sender.FullscreenControlsActionWithMediaElement",
                    action, FULLSCREEN_CONTROLS_COUNT);
        } else {
            RecordHistogram.recordEnumeratedHistogram(
                    "Cast.Sender.FullscreenControlsActionWithoutMediaElement",
                    action, FULLSCREEN_CONTROLS_COUNT);
        }
    }

    /**
     * Record the domain and registry of the URL of the frame where the user is casting the video
     * from using Rappor.
     *
     * @param url The frame URL to record the domain and registry of.
     */
    public static void castDomainAndRegistry(String url) {
        if (LibraryLoader.isInitialized()) {
            RapporServiceBridge.sampleDomainAndRegistryFromURL("Cast.Sender.MediaFrameUrl", url);
        }
    }

    /**
     * Record the ratio of the time the media element was detached from the remote playback session
     * to the total duration of the session (as from when the element has been attached till when
     * the session stopped or disconnected), in percents.
     *
     * @param percentage The ratio in percents.
     */
    public static void recordRemoteSessionTimeWithoutMediaElementPercentage(int percentage) {
        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordPercentageHistogram(
                    "Cast.Sender.SessionTimeWithoutMediaElementPercentage", percentage);
        }
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
