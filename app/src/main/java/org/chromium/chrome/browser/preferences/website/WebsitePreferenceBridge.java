// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class that interacts with native to retrieve and set website settings.
 */
public abstract class WebsitePreferenceBridge {
    private static final String LOG_TAG = "WebsiteSettingsUtils";

    /**
     * Interface for an object that listens to local storage info is ready callback.
     */
    public interface LocalStorageInfoReadyCallback {
        @CalledByNative("LocalStorageInfoReadyCallback")
        public void onLocalStorageInfoReady(HashMap map);
    }

    /**
     * Interface for an object that listens to storage info is ready callback.
     */
    public interface StorageInfoReadyCallback {
        @CalledByNative("StorageInfoReadyCallback")
        public void onStorageInfoReady(ArrayList array);
    }

    /**
     * Interface for an object that listens to storage info is cleared callback.
     */
    public interface StorageInfoClearedCallback {
        @CalledByNative("StorageInfoClearedCallback")
        public void onStorageInfoCleared();
    }

    /**
     * @return the list of all origins that have geolocation permissions in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<GeolocationInfo> getGeolocationInfo() {
        // Location can be managed by the custodian of a supervised account or by enterprise policy.
        boolean managedOnly = !PrefServiceBridge.getInstance().isAllowLocationUserModifiable();
        ArrayList<GeolocationInfo> list = new ArrayList<GeolocationInfo>();
        nativeGetGeolocationOrigins(list, managedOnly);
        return list;
    }

    @CalledByNative
    private static void insertGeolocationInfoIntoList(
            ArrayList<GeolocationInfo> list, String origin, String embedder) {
        list.add(new GeolocationInfo(origin, embedder, false));
    }

    /**
     * @return the list of all origins that have midi permissions in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<MidiInfo> getMidiInfo() {
        ArrayList<MidiInfo> list = new ArrayList<MidiInfo>();
        nativeGetMidiOrigins(list);
        return list;
    }

    @CalledByNative
    private static void insertMidiInfoIntoList(
            ArrayList<MidiInfo> list, String origin, String embedder) {
        list.add(new MidiInfo(origin, embedder, false));
    }

    public static List<CookieInfo> getCookieInfo() {
        boolean managedOnly = PrefServiceBridge.getInstance().isAcceptCookiesManaged();
        ArrayList<CookieInfo> list = new ArrayList<CookieInfo>();
        nativeGetCookieOrigins(list, managedOnly);
        return list;
    }

    @CalledByNative
    private static void insertCookieInfoIntoList(
            ArrayList<CookieInfo> list, String origin, String embedder) {
        list.add(new CookieInfo(origin, embedder, false));
    }

    @CalledByNative
    private static Object createStorageInfoList() {
        return new ArrayList<StorageInfo>();
    }

    @CalledByNative
    private static void insertStorageInfoIntoList(
            ArrayList<StorageInfo> list, String host, int type, long size) {
        list.add(new StorageInfo(host, type, size));
    }

    @CalledByNative
    private static Object createLocalStorageInfoMap() {
        return new HashMap<String, LocalStorageInfo>();
    }

    @SuppressWarnings("unchecked")
    @CalledByNative
    private static void insertLocalStorageInfoIntoMap(
            HashMap map, String origin, String fullOrigin, long size) {
        ((HashMap<String, LocalStorageInfo>) map).put(origin, new LocalStorageInfo(origin, size));
    }

    /**
     * @return the list of all origins that have protected media identifier permissions
     *         in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<ProtectedMediaIdentifierInfo> getProtectedMediaIdentifierInfo() {
        ArrayList<ProtectedMediaIdentifierInfo> list =
                new ArrayList<ProtectedMediaIdentifierInfo>();
        nativeGetProtectedMediaIdentifierOrigins(list);
        return list;
    }

    @CalledByNative
    private static void insertProtectedMediaIdentifierInfoIntoList(
            ArrayList<ProtectedMediaIdentifierInfo> list, String origin, String embedder) {
        list.add(new ProtectedMediaIdentifierInfo(origin, embedder, false));
    }

    /**
     * @return the list of all origins that have push notification permissions in
     *         non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<PushNotificationInfo> getPushNotificationInfo() {
        ArrayList<PushNotificationInfo> list = new ArrayList<PushNotificationInfo>();
        nativeGetPushNotificationOrigins(list);
        return list;
    }

    @CalledByNative
    private static void insertPushNotificationIntoList(
            ArrayList<PushNotificationInfo> list, String origin, String embedder) {
        list.add(new PushNotificationInfo(origin, embedder, false));
    }

    /**
     * @return the list of all origins that have camera permissions in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<CameraInfo> getCameraInfo() {
        ArrayList<CameraInfo> list = new ArrayList<CameraInfo>();
        // Camera can be managed by the custodian of a supervised account or by enterprise policy.
        boolean managedOnly = !PrefServiceBridge.getInstance().isCameraUserModifiable();
        nativeGetCameraOrigins(list, managedOnly);
        return list;
    }

    @CalledByNative
    private static void insertCameraInfoIntoList(
            ArrayList<CameraInfo> list, String origin, String embedder) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getOrigin().equals(origin)
                        && list.get(i).getEmbedder().equals(embedder)) {
                return;
            }
        }
        list.add(new CameraInfo(origin, embedder, false));
    }

    /**
     * @return the list of all origins that have microphone permissions in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<MicrophoneInfo> getMicrophoneInfo() {
        ArrayList<MicrophoneInfo> list =
                new ArrayList<MicrophoneInfo>();
        // Microphone can be managed by the custodian of a supervised account or by enterprise
        // policy.
        boolean managedOnly = !PrefServiceBridge.getInstance().isMicUserModifiable();
        nativeGetMicrophoneOrigins(list, managedOnly);
        return list;
    }

    @CalledByNative
    private static void insertMicrophoneInfoIntoList(
            ArrayList<MicrophoneInfo> list, String origin, String embedder) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getOrigin().equals(origin)
                        && list.get(i).getEmbedder().equals(embedder)) {
                return;
            }
        }
        list.add(new MicrophoneInfo(origin, embedder, false));
    }

    public static List<ContentSettingException> getContentSettingsExceptions(
            int contentSettingsType) {
        List<ContentSettingException> exceptions =
                PrefServiceBridge.getInstance().getContentSettingsExceptions(
                        contentSettingsType);
        if (!PrefServiceBridge.getInstance().isContentSettingManaged(contentSettingsType)) {
            return exceptions;
        }

        List<ContentSettingException> managedExceptions =
                new ArrayList<ContentSettingException>();
        for (ContentSettingException exception : exceptions) {
            if (exception.getSource().equals("policy")) {
                managedExceptions.add(exception);
            }
        }
        return managedExceptions;
    }

    public static void fetchLocalStorageInfo(LocalStorageInfoReadyCallback callback) {
        nativeFetchLocalStorageInfo(callback);
    }

    public static void fetchStorageInfo(StorageInfoReadyCallback callback) {
        nativeFetchStorageInfo(callback);
    }

    /**
     * @return the list of all sites that have fullscreen permissions in non-incognito mode.
     */
    public static List<FullscreenInfo> getFullscreenInfo() {
        boolean managedOnly = PrefServiceBridge.getInstance().isFullscreenManaged();
        ArrayList<FullscreenInfo> list = new ArrayList<FullscreenInfo>();
        nativeGetFullscreenOrigins(list, managedOnly);
        return list;
    }

    /**
     * Inserts fullscreen information into a list.
     */
    @CalledByNative
    private static void insertFullscreenInfoIntoList(
            ArrayList<FullscreenInfo> list, String origin, String embedder) {
        list.add(new FullscreenInfo(origin, embedder, false));
    }

    private static native void nativeGetGeolocationOrigins(Object list, boolean managedOnly);
    static native int nativeGetGeolocationSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    public static native void nativeSetGeolocationSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetMidiOrigins(Object list);
    static native int nativeGetMidiSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetMidiSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetPushNotificationOrigins(Object list);
    static native int nativeGetPushNotificationSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetPushNotificationSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetProtectedMediaIdentifierOrigins(Object list);
    static native int nativeGetProtectedMediaIdentifierSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetProtectedMediaIdentifierSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetCameraOrigins(Object list, boolean managedOnly);
    private static native void nativeGetMicrophoneOrigins(Object list, boolean managedOnly);
    static native int nativeGetMicrophoneSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native int nativeGetCameraSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetMicrophoneSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    static native void nativeSetCameraSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetCookieOrigins(Object list, boolean managedOnly);
    static native int nativeGetCookieSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetCookieSettingForOrigin(
            String origin, String embedder, int setting, boolean isIncognito);
    static native void nativeClearCookieData(String path);
    static native void nativeClearLocalStorageData(String path);
    static native void nativeClearStorageData(String origin, int type, Object callback);
    private static native void nativeFetchLocalStorageInfo(Object callback);
    private static native void nativeFetchStorageInfo(Object callback);
    static native boolean nativeIsContentSettingsPatternValid(String pattern);
    static native boolean nativeUrlMatchesContentSettingsPattern(String url, String pattern);
    private static native void nativeGetFullscreenOrigins(Object list, boolean managedOnly);
    static native int nativeGetFullscreenSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetFullscreenSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
}
