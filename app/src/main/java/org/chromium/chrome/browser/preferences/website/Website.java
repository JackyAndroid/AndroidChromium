// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.chrome.browser.util.MathUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Website is a class for storing information about a website and its associated permissions.
 */
public class Website implements Serializable {

    static final int INVALID_CAMERA_OR_MICROPHONE_ACCESS = 0;
    static final int CAMERA_ACCESS_ALLOWED = 1;
    static final int MICROPHONE_AND_CAMERA_ACCESS_ALLOWED = 2;
    static final int MICROPHONE_ACCESS_ALLOWED = 3;
    static final int CAMERA_ACCESS_DENIED = 4;
    static final int MICROPHONE_AND_CAMERA_ACCESS_DENIED = 5;
    static final int MICROPHONE_ACCESS_DENIED = 6;

    private final WebsiteAddress mOrigin;
    private final WebsiteAddress mEmbedder;

    private ContentSettingException mAutoplayExceptionInfo;
    private ContentSettingException mBackgroundSyncExceptionInfo;
    private CameraInfo mCameraInfo;
    private ContentSettingException mCookieException;
    private FullscreenInfo mFullscreenInfo;
    private GeolocationInfo mGeolocationInfo;
    private ContentSettingException mJavaScriptException;
    private KeygenInfo mKeygenInfo;
    private LocalStorageInfo mLocalStorageInfo;
    private MicrophoneInfo mMicrophoneInfo;
    private MidiInfo mMidiInfo;
    private NotificationInfo mNotificationInfo;
    private ContentSettingException mPopupException;
    private ProtectedMediaIdentifierInfo mProtectedMediaIdentifierInfo;
    private final List<StorageInfo> mStorageInfo = new ArrayList<StorageInfo>();
    private int mStorageInfoCallbacksLeft;
    private final List<UsbInfo> mUsbInfo = new ArrayList<UsbInfo>();

    public Website(WebsiteAddress origin, WebsiteAddress embedder) {
        mOrigin = origin;
        mEmbedder = embedder;
    }

    public WebsiteAddress getAddress() {
        return mOrigin;
    }

    public String getTitle() {
        return mOrigin.getTitle();
    }

    public String getSummary() {
        if (mEmbedder == null) return null;
        return mEmbedder.getTitle();
    }

    /**
     * A comparison function for sorting by address (first by origin and then
     * by embedder).
     */
    public int compareByAddressTo(Website to) {
        if (this == to) return 0;
        int originComparison = mOrigin.compareTo(to.mOrigin);
        if (originComparison == 0) {
            if (mEmbedder == null) return to.mEmbedder == null ? 0 : -1;
            if (to.mEmbedder == null) return 1;
            return mEmbedder.compareTo(to.mEmbedder);
        }
        return originComparison;
    }

    /**
     * A comparison function for sorting by storage (most used first).
     * @return which site uses more storage.
     */
    public int compareByStorageTo(Website to) {
        if (this == to) return 0;
        return MathUtils.compareLongs(to.getTotalUsage(), getTotalUsage());
    }

    /**
     * Returns what permission governs Autoplay access.
     */
    public ContentSetting getAutoplayPermission() {
        return mAutoplayExceptionInfo != null ? mAutoplayExceptionInfo.getContentSetting() : null;
    }

    /**
     * Configure Autoplay permission access setting for this site.
     */
    public void setAutoplayPermission(ContentSetting value) {
        if (mAutoplayExceptionInfo != null) {
            mAutoplayExceptionInfo.setContentSetting(value);
        }
    }

    /**
     * Sets the Autoplay exception info for this Website.
     */
    public void setAutoplayException(ContentSettingException exception) {
        mAutoplayExceptionInfo = exception;
    }

    /**
     * Sets the background sync setting exception info for this website.
     */
    public void setBackgroundSyncException(ContentSettingException exception) {
        mBackgroundSyncExceptionInfo = exception;
    }

    /**
     * @return what permission governs background sync.
     */
    public ContentSetting getBackgroundSyncPermission() {
        return mBackgroundSyncExceptionInfo != null
                ? mBackgroundSyncExceptionInfo.getContentSetting()
                : null;
    }

    /**
     * Configures the background sync setting for this site.
     */
    public void setBackgroundSyncPermission(ContentSetting value) {
        if (mBackgroundSyncExceptionInfo != null) {
            mBackgroundSyncExceptionInfo.setContentSetting(value);
        }
    }

    /**
     * Sets camera capture info class.
     */
    public void setCameraInfo(CameraInfo info) {
        mCameraInfo = info;
    }

    public CameraInfo getCameraInfo() {
        return mCameraInfo;
    }

    /**
     * Returns what setting governs camera capture access.
     */
    public ContentSetting getCameraPermission() {
        return mCameraInfo != null ? mCameraInfo.getContentSetting() : null;
    }

    /**
     * Configure camera capture setting for this site.
     */
    public void setCameraPermission(ContentSetting value) {
        if (mCameraInfo != null) mCameraInfo.setContentSetting(value);
    }

    /**
     * Sets the Cookie exception info for this site.
     */
    public void setCookieException(ContentSettingException exception) {
        mCookieException = exception;
    }

    public ContentSettingException getCookieException() {
        return mCookieException;
    }

    /**
     * Gets the permission that governs cookie preferences.
     */
    public ContentSetting getCookiePermission() {
        return mCookieException != null ? mCookieException.getContentSetting() : null;
    }

    /**
     * Sets the permission that govers cookie preferences for this site.
     */
    public void setCookiePermission(ContentSetting value) {
        if (mCookieException != null) {
            mCookieException.setContentSetting(value);
        }
    }

    /**
     * Set fullscreen permission information class.
     *
     * @param info Fullscreen information about the website.
     */
    public void setFullscreenInfo(FullscreenInfo info) {
        mFullscreenInfo = info;
    }

    /**
     * @return fullscreen information of the site.
     */
    public FullscreenInfo getFullscreenInfo() {
        return mFullscreenInfo;
    }

    /**
     * @return what permission governs fullscreen access.
     */
    public ContentSetting getFullscreenPermission() {
        return mFullscreenInfo != null ? mFullscreenInfo.getContentSetting() : null;
    }

    /**
     * Configure fullscreen setting for this site.
     *
     * @param value Content setting for fullscreen permission.
     */
    public void setFullscreenPermission(ContentSetting value) {
        if (mFullscreenInfo != null) {
            mFullscreenInfo.setContentSetting(value);
        }
    }

    /**
     * Sets the GeoLocationInfo object for this Website.
     */
    public void setGeolocationInfo(GeolocationInfo info) {
        mGeolocationInfo = info;
    }

    public GeolocationInfo getGeolocationInfo() {
        return mGeolocationInfo;
    }

    /**
     * Returns what permission governs geolocation access.
     */
    public ContentSetting getGeolocationPermission() {
        return mGeolocationInfo != null ? mGeolocationInfo.getContentSetting() : null;
    }

    /**
     * Configure geolocation access setting for this site.
     */
    public void setGeolocationPermission(ContentSetting value) {
        if (mGeolocationInfo != null) {
            mGeolocationInfo.setContentSetting(value);
        }
    }

    /**
     * Returns what permission governs JavaScript access.
     */
    public ContentSetting getJavaScriptPermission() {
        return mJavaScriptException != null ? mJavaScriptException.getContentSetting() : null;
    }

    /**
     * Configure JavaScript permission access setting for this site.
     */
    public void setJavaScriptPermission(ContentSetting value) {
        if (mJavaScriptException != null) {
            mJavaScriptException.setContentSetting(value);
        }
    }

    /**
     * Sets the JavaScript exception info for this Website.
     */
    public void setJavaScriptException(ContentSettingException exception) {
        mJavaScriptException = exception;
    }

    /**
     * Sets the KeygenInfo object for this Website.
     */
    public void setKeygenInfo(KeygenInfo info) {
        mKeygenInfo = info;
    }

    public KeygenInfo getKeygenInfo() {
        return mKeygenInfo;
    }

    /**
     * Returns what permission governs keygen access.
     */
    public ContentSetting getKeygenPermission() {
        return mKeygenInfo != null ? mKeygenInfo.getContentSetting() : null;
    }

    /**
     * Configure keygen access setting for this site.
     */
    public void setKeygenPermission(ContentSetting value) {
        if (mKeygenInfo != null) {
            mKeygenInfo.setContentSetting(value);
        }
    }

    /**
     * Sets microphone capture info class.
     */
    public void setMicrophoneInfo(MicrophoneInfo info) {
        mMicrophoneInfo = info;
    }

    public MicrophoneInfo getMicrophoneInfo() {
        return mMicrophoneInfo;
    }

    /**
     * Returns what setting governs microphone capture access.
     */
    public ContentSetting getMicrophonePermission() {
        return mMicrophoneInfo != null ? mMicrophoneInfo.getContentSetting() : null;
    }

    /**
     * Configure microphone capture setting for this site.
     */
    public void setMicrophonePermission(ContentSetting value) {
        if (mMicrophoneInfo != null) mMicrophoneInfo.setContentSetting(value);
    }

    /**
     * Sets the MidiInfo object for this Website.
     */
    public void setMidiInfo(MidiInfo info) {
        mMidiInfo = info;
    }

    public MidiInfo getMidiInfo() {
        return mMidiInfo;
    }

    /**
     * Returns what permission governs MIDI usage access.
     */
    public ContentSetting getMidiPermission() {
        return mMidiInfo != null ? mMidiInfo.getContentSetting() : null;
    }

    /**
     * Configure Midi usage access setting for this site.
     */
    public void setMidiPermission(ContentSetting value) {
        if (mMidiInfo != null) {
            mMidiInfo.setContentSetting(value);
        }
    }

    /**
     * Sets Notification access permission information class.
     */
    public void setNotificationInfo(NotificationInfo info) {
        mNotificationInfo = info;
    }

    public NotificationInfo getNotificationInfo() {
        return mNotificationInfo;
    }

    /**
     * Returns what setting governs notification access.
     */
    public ContentSetting getNotificationPermission() {
        return mNotificationInfo != null ? mNotificationInfo.getContentSetting() : null;
    }

    /**
     * Configure notification setting for this site.
     */
    public void setNotificationPermission(ContentSetting value) {
        if (mNotificationInfo != null) {
            mNotificationInfo.setContentSetting(value);
        }
    }

    /**
     * Sets the Popup exception info for this Website.
     */
    public void setPopupException(ContentSettingException exception) {
        mPopupException = exception;
    }

    public ContentSettingException getPopupException() {
        return mPopupException;
    }

    /**
     * Returns what permission governs Popup permission.
     */
    public ContentSetting getPopupPermission() {
        if (mPopupException != null) return mPopupException.getContentSetting();
        return null;
    }

    /**
     * Configure Popup permission access setting for this site.
     */
    public void setPopupPermission(ContentSetting value) {
        if (mPopupException != null) {
            mPopupException.setContentSetting(value);
        }
    }

    /**
     * Sets protected media identifier access permission information class.
     */
    public void setProtectedMediaIdentifierInfo(ProtectedMediaIdentifierInfo info) {
        mProtectedMediaIdentifierInfo = info;
    }

    public ProtectedMediaIdentifierInfo getProtectedMediaIdentifierInfo() {
        return mProtectedMediaIdentifierInfo;
    }

    /**
     * Returns what permission governs Protected Media Identifier access.
     */
    public ContentSetting getProtectedMediaIdentifierPermission() {
        return mProtectedMediaIdentifierInfo != null
                ? mProtectedMediaIdentifierInfo.getContentSetting() : null;
    }

    /**
     * Configure Protected Media Identifier access setting for this site.
     */
    public void setProtectedMediaIdentifierPermission(ContentSetting value) {
        if (mProtectedMediaIdentifierInfo != null) {
            mProtectedMediaIdentifierInfo.setContentSetting(value);
        }
    }

    public void setLocalStorageInfo(LocalStorageInfo info) {
        mLocalStorageInfo = info;
    }

    public LocalStorageInfo getLocalStorageInfo() {
        return mLocalStorageInfo;
    }

    public void addStorageInfo(StorageInfo info) {
        mStorageInfo.add(info);
    }

    public List<StorageInfo> getStorageInfo() {
        return new ArrayList<StorageInfo>(mStorageInfo);
    }

    public void clearAllStoredData(final StoredDataClearedCallback callback) {
        if (mLocalStorageInfo != null) {
            mLocalStorageInfo.clear();
            mLocalStorageInfo = null;
        }
        mStorageInfoCallbacksLeft = mStorageInfo.size();
        if (mStorageInfoCallbacksLeft > 0) {
            for (StorageInfo info : mStorageInfo) {
                info.clear(new WebsitePreferenceBridge.StorageInfoClearedCallback() {
                    @Override
                    public void onStorageInfoCleared() {
                        if (--mStorageInfoCallbacksLeft == 0) callback.onStoredDataCleared();
                    }
                });
            }
            mStorageInfo.clear();
        } else {
            callback.onStoredDataCleared();
        }
    }

    /**
     * An interface to implement to get a callback when storage info has been cleared.
     */
    public interface StoredDataClearedCallback {
        public void onStoredDataCleared();
    }

    public long getTotalUsage() {
        long usage = 0;
        if (mLocalStorageInfo != null) {
            usage += mLocalStorageInfo.getSize();
        }
        for (StorageInfo info : mStorageInfo) {
            usage += info.getSize();
        }
        return usage;
    }

    /**
     * Add information about a USB device permission to the set stored in this object.
     */
    public void addUsbInfo(UsbInfo info) {
        mUsbInfo.add(info);
    }

    /**
     * Returns the set of USB devices this website has been granted permission to access.
     */
    public List<UsbInfo> getUsbInfo() {
        return new ArrayList<UsbInfo>(mUsbInfo);
    }
}
