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
 * This class allows Java code to get and clear the list of recently closed tabs.
 */
class RecentlyClosedBridge {
    private long mNativeRecentlyClosedTabsBridge;

    /**
     * Callback interface for getting notified when the list of recently closed tabs is updated.
     */
    interface RecentlyClosedCallback {
        /**
         * This method will be called every time the list of recently closed tabs is updated.
         *
         * It's a good place to call {@link RecentlyClosedBridge#getRecentlyClosedTabs()} to get the
         * updated list of tabs.
         */
        @CalledByNative("RecentlyClosedCallback")
        void onUpdated();
    }

    /**
     * Represents a recently closed tab.
     */
    static class RecentlyClosedTab {
        public final int id;
        public final String title;
        public final String url;

        private RecentlyClosedTab(int id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
        }
    }

    @CalledByNative
    private static void pushTab(
            List<RecentlyClosedTab> tabs, int id, String title, String url) {
        RecentlyClosedTab tab = new RecentlyClosedTab(id, title, url);
        tabs.add(tab);
    }

    /**
     * Initializes this class with the given profile.
     * @param profile The Profile whose recently closed tabs will be queried.
     */
    RecentlyClosedBridge(Profile profile) {
        mNativeRecentlyClosedTabsBridge = nativeInit(profile);
    }

    /**
     * Cleans up the C++ side of this class. This instance must not be used after calling destroy().
     */
    void destroy() {
        assert mNativeRecentlyClosedTabsBridge != 0;
        nativeDestroy(mNativeRecentlyClosedTabsBridge);
        mNativeRecentlyClosedTabsBridge = 0;
    }

    /**
     * Sets the callback to be called whenever the list of recently closed tabs changes.
     * @param callback The RecentlyClosedCallback to be notified, or null.
     */
    void setRecentlyClosedCallback(RecentlyClosedCallback callback) {
        nativeSetRecentlyClosedCallback(mNativeRecentlyClosedTabsBridge, callback);
    }

    /**
     * @param maxTabCount The maximum number of recently closed tabs to return.
     * @return The list of recently closed tabs, with up to maxTabCount elements.
     */
    List<RecentlyClosedTab> getRecentlyClosedTabs(int maxTabCount) {
        List<RecentlyClosedTab> tabs = new ArrayList<RecentlyClosedTab>();
        boolean received = nativeGetRecentlyClosedTabs(mNativeRecentlyClosedTabsBridge, tabs,
                maxTabCount);
        return received ? tabs : null;
    }

    /**
     * Opens a recently closed tab in the current tab or a new tab. If opened in the current tab,
     * the current tab's entire history is replaced.
     *
     * @param tab The current Tab.
     * @param recentTab The RecentlyClosedTab to open.
     * @param windowOpenDisposition The WindowOpenDisposition value specifying whether to open in
     *         the current tab or a new tab.
     * @return Whether the tab was successfully opened.
     */
    boolean openRecentlyClosedTab(Tab tab, RecentlyClosedTab recentTab,
            int windowOpenDisposition) {
        return nativeOpenRecentlyClosedTab(mNativeRecentlyClosedTabsBridge, tab, recentTab.id,
                windowOpenDisposition);
    }

    /**
     * Clears all recently closed tabs.
     */
    void clearRecentlyClosedTabs() {
        nativeClearRecentlyClosedTabs(mNativeRecentlyClosedTabsBridge);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeRecentlyClosedTabsBridge);
    private native void nativeSetRecentlyClosedCallback(
            long nativeRecentlyClosedTabsBridge, RecentlyClosedCallback callback);
    private native boolean nativeGetRecentlyClosedTabs(
            long nativeRecentlyClosedTabsBridge, List<RecentlyClosedTab> tabs, int maxTabCount);
    private native boolean nativeOpenRecentlyClosedTab(long nativeRecentlyClosedTabsBridge,
            Tab tab, int recentTabId, int windowOpenDisposition);
    private native void nativeClearRecentlyClosedTabs(long nativeRecentlyClosedTabsBridge);
}
