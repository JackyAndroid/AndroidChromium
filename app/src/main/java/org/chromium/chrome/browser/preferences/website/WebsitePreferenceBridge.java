// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class that interacts with native to retrieve and set website settings.
 */
public abstract class WebsitePreferenceBridge {
    private static final String LOG_TAG = "WebsiteSettingsUtils";

    /**
     * Interface for an object that listens to storage info is cleared callback.
     */
    public interface StorageInfoClearedCallback {
        @CalledByNative("StorageInfoClearedCallback")
        public void onStorageInfoCleared();
    }

    /**
     * @return the list of all origins that have keygen permissions in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<KeygenInfo> getKeygenInfo() {
        ArrayList<KeygenInfo> list = new ArrayList<KeygenInfo>();
        nativeGetKeygenOrigins(list);
        return list;
    }

    @CalledByNative
    private static void insertKeygenInfoIntoList(
            ArrayList<KeygenInfo> list, String origin, String embedder) {
        list.add(new KeygenInfo(origin, embedder, false));
    }

    /**
     * @return whether we've blocked key generation in the current tab.
     */
    @SuppressWarnings("unchecked")
    public static boolean getKeygenBlocked(WebContents webContents) {
        return nativeGetKeygenBlocked(webContents);
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
            HashMap map, String origin, String fullOrigin, long size, boolean important) {
        ((HashMap<String, LocalStorageInfo>) map)
                .put(origin, new LocalStorageInfo(origin, size, important));
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
     * @return the list of all origins that have notification permissions in non-incognito mode.
     */
    @SuppressWarnings("unchecked")
    public static List<NotificationInfo> getNotificationInfo() {
        ArrayList<NotificationInfo> list = new ArrayList<NotificationInfo>();
        nativeGetNotificationOrigins(list);
        return list;
    }

    @CalledByNative
    private static void insertNotificationIntoList(
            ArrayList<NotificationInfo> list, String origin, String embedder) {
        list.add(new NotificationInfo(origin, embedder, false));
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

    public static void fetchLocalStorageInfo(Callback<HashMap> callback) {
        nativeFetchLocalStorageInfo(callback);
    }

    public static void fetchStorageInfo(Callback<ArrayList> callback) {
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
     * Returns the list of all USB device permissions.
     *
     * There will be one UsbInfo instance for each granted permission. That
     * means that if two origin/embedder pairs have permission for the same
     * device there will be two UsbInfo instances.
     */
    public static List<UsbInfo> getUsbInfo() {
        ArrayList<UsbInfo> list = new ArrayList<UsbInfo>();
        nativeGetUsbOrigins(list);
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

    /**
     * Inserts USB device information into a list.
     */
    @CalledByNative
    private static void insertUsbInfoIntoList(
            ArrayList<UsbInfo> list, String origin, String embedder, String name, String object) {
        list.add(new UsbInfo(origin, embedder, name, object));
    }

    private static native void nativeGetGeolocationOrigins(Object list, boolean managedOnly);
    static native int nativeGetGeolocationSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    public static native void nativeSetGeolocationSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetKeygenOrigins(Object list);
    static native int nativeGetKeygenSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetKeygenSettingForOrigin(
            String origin, int value, boolean isIncognito);
    private static native boolean nativeGetKeygenBlocked(Object webContents);
    private static native void nativeGetMidiOrigins(Object list);
    static native int nativeGetMidiSettingForOrigin(
            String origin, String embedder, boolean isIncognito);
    static native void nativeSetMidiSettingForOrigin(
            String origin, String embedder, int value, boolean isIncognito);
    private static native void nativeGetNotificationOrigins(Object list);
    static native int nativeGetNotificationSettingForOrigin(
            String origin, boolean isIncognito);
    static native void nativeSetNotificationSettingForOrigin(
            String origin, int value, boolean isIncognito);
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
            String origin, int value, boolean isIncognito);
    static native void nativeSetCameraSettingForOrigin(
            String origin, int value, boolean isIncognito);
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
    static native void nativeGetUsbOrigins(Object list);
    static native void nativeRevokeUsbPermission(String origin, String embedder, String object);
}
