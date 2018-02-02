// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * This class exposes to Java information about sessions, windows, and tabs on the user's synced
 * devices.
 */
class ForeignSessionHelper {
    private long mNativeForeignSessionHelper;

    /**
     * Callback interface for getting notified when foreign session sync is updated.
     */
    interface ForeignSessionCallback {
        /**
         * This method will be called every time foreign session sync is updated.
         *
         * It's a good place to call {@link ForeignSessionHelper#getForeignSessions()} to get the
         * updated information.
         */
        @CalledByNative("ForeignSessionCallback")
        void onUpdated();
    }

    /**
     * Represents synced foreign session.
     */
    static class ForeignSession {
        // Please keep in sync with synced_session.h
        static final int DEVICE_TYPE_UNSET = 0;
        static final int DEVICE_TYPE_WIN = 1;
        static final int DEVICE_TYPE_MACOSX = 2;
        static final int DEVICE_TYPE_LINUX = 3;
        static final int DEVICE_TYPE_CHROMEOS = 4;
        static final int DEVICE_TYPE_OTHER = 5;
        static final int DEVICE_TYPE_PHONE = 6;
        static final int DEVICE_TYPE_TABLET = 7;

        public final String tag;
        public final String name;
        public final int deviceType;
        public final long modifiedTime;
        public final List<ForeignSessionWindow> windows = new ArrayList<ForeignSessionWindow>();

        private ForeignSession(String tag, String name, int deviceType, long modifiedTime) {
            this.tag = tag;
            this.name = name;
            this.deviceType = deviceType;
            this.modifiedTime = modifiedTime;
        }
    }

    /**
     * Represents synced foreign window. Note that desktop Chrome can have multiple windows in a
     * session.
     */
    static class ForeignSessionWindow {
        public final long timestamp;
        public final int sessionId;
        public final List<ForeignSessionTab> tabs = new ArrayList<ForeignSessionTab>();

        private ForeignSessionWindow(long timestamp, int sessionId) {
            this.timestamp = timestamp;
            this.sessionId = sessionId;
        }
    }

    /**
     * Represents synced foreign tab.
     */
    static class ForeignSessionTab {
        public final String url;
        public final String title;
        public final long timestamp;
        public final int id;

        private ForeignSessionTab(String url, String title, long timestamp, int id) {
            this.url = url;
            this.title = title;
            this.timestamp = timestamp;
            this.id = id;
        }
    }

    @CalledByNative
    private static ForeignSession pushSession(
            List<ForeignSession> sessions, String tag, String name, int deviceType,
            long modifiedTime) {
        ForeignSession session = new ForeignSession(tag, name, deviceType, modifiedTime);
        sessions.add(session);
        return session;
    }

    @CalledByNative
    private static ForeignSessionWindow pushWindow(
            ForeignSession session, long timestamp, int sessionId) {
        ForeignSessionWindow window = new ForeignSessionWindow(timestamp, sessionId);
        session.windows.add(window);
        return window;
    }

    @CalledByNative
    private static void pushTab(
            ForeignSessionWindow window, String url, String title, long timestamp, int sessionId) {
        ForeignSessionTab tab = new ForeignSessionTab(url, title, timestamp, sessionId);
        window.tabs.add(tab);
    }

    /**
     * Initialize this class with the given profile.
     * @param profile Profile that will be used for syncing.
     */
    ForeignSessionHelper(Profile profile) {
        mNativeForeignSessionHelper = nativeInit(profile);
    }

    /**
     * Clean up the C++ side of this class. After the call, this class instance shouldn't be used.
     */
    void destroy() {
        assert mNativeForeignSessionHelper != 0;
        nativeDestroy(mNativeForeignSessionHelper);
        mNativeForeignSessionHelper = 0;
    }

    /**
     * @return {@code True} iff Tab sync is enabled.
     */
    boolean isTabSyncEnabled() {
        return nativeIsTabSyncEnabled(mNativeForeignSessionHelper);
    }

    /**
     * Force a sync for sessions.
     */
    void triggerSessionSync() {
        nativeTriggerSessionSync(mNativeForeignSessionHelper);
    }

    /**
     * Sets callback instance that will be called on every foreign session sync update.
     * @param callback The callback to be invoked.
     */
    void setOnForeignSessionCallback(ForeignSessionCallback callback) {
        nativeSetOnForeignSessionCallback(mNativeForeignSessionHelper, callback);
    }

    /**
     * @return The list of synced foreign sessions. {@code null} iff it fails to get them for some
     *         reason.
     */
    List<ForeignSession> getForeignSessions() {
        if (!isTabSyncEnabled()) {
            return null;
        }
        List<ForeignSession> result = new ArrayList<ForeignSession>();
        boolean received = nativeGetForeignSessions(mNativeForeignSessionHelper, result);
        if (!received) {
            result = null;
        }

        return result;
    }

    /**
     * Opens the given foreign tab in a new tab.
     * @param tab Tab to load the session into.
     * @param session Session that the target tab belongs to.
     * @param foreignTab Target tab to open.
     * @param windowOpenDisposition The WindowOpenDisposition flag.
     * @return {@code True} iff the tab is successfully opened.
     */
    boolean openForeignSessionTab(Tab tab, ForeignSession session,
            ForeignSessionTab foreignTab, int windowOpenDisposition) {
        return nativeOpenForeignSessionTab(mNativeForeignSessionHelper, tab, session.tag,
                foreignTab.id, windowOpenDisposition);
    }

    /**
     * Remove Foreign session to display. Note that it will be reappear on the next sync.
     *
     * This is mainly for when user wants to delete very old session that won't be used or syned in
     * the future.
     * @param session Session to be deleted.
     */
    void deleteForeignSession(ForeignSession session) {
        nativeDeleteForeignSession(mNativeForeignSessionHelper, session.tag);
    }

    private static native long nativeInit(Profile profile);
    private static native void nativeDestroy(long nativeForeignSessionHelper);
    private static native boolean nativeIsTabSyncEnabled(long nativeForeignSessionHelper);
    private static native void nativeTriggerSessionSync(long nativeForeignSessionHelper);
    private static native void nativeSetOnForeignSessionCallback(
            long nativeForeignSessionHelper, ForeignSessionCallback callback);
    private static native boolean nativeGetForeignSessions(long nativeForeignSessionHelper,
            List<ForeignSession> resultSessions);
    private static native boolean nativeOpenForeignSessionTab(
            long nativeForeignSessionHelper, Tab tab, String sessionTag, int tabId,
            int disposition);
    private static native void nativeDeleteForeignSession(
            long nativeForeignSessionHelper, String sessionTag);
}
