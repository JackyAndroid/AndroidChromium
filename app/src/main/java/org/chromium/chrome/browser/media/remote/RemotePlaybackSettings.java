// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Maintains the persistent settings that the app needs for casting.
 */
public class RemotePlaybackSettings {
    private static final String DEVICE_ID_KEY = "remote_device_id";
    private static final String RECONNECT_KEY = "reconnect_remote_device";
    private static final String LAST_VIDEO_TIME_REMAINING_KEY = "last_video_time_remaining";
    private static final String LAST_VIDEO_TIME_PLAYED_KEY = "last_video_time_played";
    private static final String LAST_VIDEO_STATE_KEY = "last_video_state";
    private static final String PLAYER_IN_USE_KEY = "player_in_use";
    private static final String URI_PLAYING = "uri playing";
    private static final String SESSION_ID = "session_id";

    public static String getDeviceId(Context context) {
        return getPreferences(context).getString(DEVICE_ID_KEY, null);
    }

    public static void setDeviceId(Context context, String deviceId) {
        putValue(getPreferences(context), DEVICE_ID_KEY, deviceId);
    }

    public static String getSessionId(Context context) {
        return getPreferences(context).getString(SESSION_ID, null);
    }

    public static void setSessionId(Context context, String sessionId) {
        putValue(getPreferences(context), SESSION_ID, sessionId);
    }

    public static boolean getShouldReconnectToRemote(Context context) {
        return getPreferences(context).getBoolean(RECONNECT_KEY, false);
    }

    public static void setShouldReconnectToRemote(Context context, boolean reconnect) {
        putValue(getPreferences(context), RECONNECT_KEY, reconnect);
    }

    public static long getRemainingTime(Context context) {
        return getPreferences(context).getLong(LAST_VIDEO_TIME_REMAINING_KEY, 0);
    }

    public static void setRemainingTime(Context context, long time) {
        putValue(getPreferences(context), LAST_VIDEO_TIME_REMAINING_KEY, time);
    }

    public static long getLastPlayedTime(Context context) {
        return getPreferences(context).getLong(LAST_VIDEO_TIME_PLAYED_KEY, 0);
    }

    public static void setLastPlayedTime(Context context, long time) {
        putValue(getPreferences(context), LAST_VIDEO_TIME_PLAYED_KEY, time);
    }

    public static String getLastVideoState(Context context) {
        return getPreferences(context).getString(LAST_VIDEO_STATE_KEY, null);
    }

    public static void setLastVideoState(Context context, String title) {
        putValue(getPreferences(context), LAST_VIDEO_STATE_KEY, title);
    }

    public static String getPlayerInUse(Context context) {
        return getPreferences(context).getString(PLAYER_IN_USE_KEY, null);
    }

    public static void setPlayerInUse(Context context, String player) {
        putValue(getPreferences(context), PLAYER_IN_USE_KEY, player);
    }

    public static String getUriPlaying(Context context) {
        return getPreferences(context).getString(URI_PLAYING, null);
    }

    public static void setUriPlaying(Context context, String url) {
        putValue(getPreferences(context), URI_PLAYING, url);
    }

    private static void putValue(SharedPreferences preferences, String key, String value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private static void putValue(SharedPreferences preferences, String key, boolean value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private static void putValue(SharedPreferences preferences, String key, long value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
