// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.Bitmap;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;
import org.chromium.chrome.browser.invalidation.InvalidationController;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSession;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSessionCallback;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSessionTab;
import org.chromium.chrome.browser.ntp.RecentTabsPromoView.SyncPromoModel;
import org.chromium.chrome.browser.ntp.RecentlyClosedBridge.RecentlyClosedCallback;
import org.chromium.chrome.browser.ntp.RecentlyClosedBridge.RecentlyClosedTab;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.sync.AndroidSyncSettings.AndroidSyncSettingsObserver;
import org.chromium.sync.signin.ChromeSigninController;

import java.util.Collections;
import java.util.List;

/**
 * Provides the domain logic and data for RecentTabsPage and RecentTabsRowAdapter.
 */
public class RecentTabsManager implements AndroidSyncSettingsObserver, SignInStateObserver,
        SyncPromoModel {

    /**
     * Implement this to receive updates when the page contents change.
     */
    interface UpdatedCallback {
        /**
         * Called when the list of recently closed tabs or foreign sessions changes.
         */
        void onUpdated();
    }

    private static final int RECENTLY_CLOSED_MAX_TAB_COUNT = 5;

    private final Profile mProfile;
    private final Tab mTab;
    private final Context mContext;
    private final ObserverList<AndroidSyncSettingsObserver> mObservers =
            new ObserverList<AndroidSyncSettingsObserver>();

    private FaviconHelper mFaviconHelper;
    private ForeignSessionHelper mForeignSessionHelper;
    private List<ForeignSession> mForeignSessions;
    private List<RecentlyClosedTab> mRecentlyClosedTabs;
    private NewTabPagePrefs mNewTabPagePrefs;
    private RecentlyClosedBridge mRecentlyClosedBridge;
    private SigninManager mSignInManager;
    private UpdatedCallback mUpdatedCallback;
    private ProfileDataCache mProfileDataCache;

    /**
     * Create an RecentTabsManager to be used with RecentTabsPage and RecentTabsRowAdapter.
     *
     * @param tab The Tab that is showing this recent tabs page.
     * @param profile Profile that is associated with the current session.
     * @param context the Android context this manager will work in.
     */
    public RecentTabsManager(Tab tab, Profile profile, Context context) {
        mProfile = profile;
        mTab = tab;
        mForeignSessionHelper = buildForeignSessionHelper(mProfile);
        mNewTabPagePrefs = buildNewTabPagePrefs(mProfile);
        mFaviconHelper = buildFaviconHelper();
        mRecentlyClosedBridge = buildRecentlyClosedBridge(mProfile);
        mSignInManager = SigninManager.get(context);
        mContext = context;

        updateRecentlyClosedTabs();
        registerForForeignSessionUpdates();
        updateForeignSessions();
        mForeignSessionHelper.triggerSessionSync();
        registerForSignInAndSyncNotifications();

        InvalidationController.get(mContext).onRecentTabsPageOpened();
    }

    /**
     * Should be called when this object is no longer needed. Performs necessary listener tear down.
     */
    public void destroy() {
        AndroidSyncSettings.unregisterObserver(mContext, this);

        mSignInManager.removeSignInStateObserver(this);
        mSignInManager = null;

        mFaviconHelper.destroy();
        mFaviconHelper = null;

        mRecentlyClosedBridge.destroy();
        mRecentlyClosedBridge = null;

        mForeignSessionHelper.destroy();
        mForeignSessionHelper = null;

        mUpdatedCallback = null;

        mNewTabPagePrefs.destroy();
        mNewTabPagePrefs = null;

        if (mProfileDataCache != null) {
            mProfileDataCache.destroy();
            mProfileDataCache = null;
        }

        InvalidationController.get(mContext).onRecentTabsPageClosed();
    }

    private static ForeignSessionHelper buildForeignSessionHelper(Profile profile) {
        return new ForeignSessionHelper(profile);
    }

    private static NewTabPagePrefs buildNewTabPagePrefs(Profile profile) {
        return new NewTabPagePrefs(profile);
    }

    private static FaviconHelper buildFaviconHelper() {
        return new FaviconHelper();
    }

    private RecentlyClosedBridge buildRecentlyClosedBridge(Profile profile) {
        RecentlyClosedBridge bridge = new RecentlyClosedBridge(profile);
        bridge.setRecentlyClosedCallback(new RecentlyClosedCallback() {
            @Override
            public void onUpdated() {
                updateRecentlyClosedTabs();
                postUpdate();
            }
        });
        return bridge;
    }

    private void registerForForeignSessionUpdates() {
        mForeignSessionHelper.setOnForeignSessionCallback(new ForeignSessionCallback() {
            @Override
            public void onUpdated() {
                updateForeignSessions();
                postUpdate();
            }
        });
    }

    private void registerForSignInAndSyncNotifications() {
        AndroidSyncSettings.registerObserver(mContext, this);
        mSignInManager.addSignInStateObserver(this);
    }

    protected void updateCurrentlyOpenTabs() {
    }

    private void updateRecentlyClosedTabs() {
        mRecentlyClosedTabs = mRecentlyClosedBridge.getRecentlyClosedTabs(
                RECENTLY_CLOSED_MAX_TAB_COUNT);
    }

    private void updateForeignSessions() {
        mForeignSessions = mForeignSessionHelper.getForeignSessions();
        if (mForeignSessions == null) {
            mForeignSessions = Collections.emptyList();
        }
    }

    /**
     * @return Most up-to-date list of currently open tabs.
     */
    public List<CurrentlyOpenTab> getCurrentlyOpenTabs() {
        return null;
    }

    /**
     * @return Most up-to-date list of foreign sessions.
     */
    public List<ForeignSession> getForeignSessions() {
        return mForeignSessions;
    }

    /**
     * @return Most up-to-date list of recently closed tabs.
     */
    public List<RecentlyClosedTab> getRecentlyClosedTabs() {
        return mRecentlyClosedTabs;
    }

    /**
     * Opens a new tab navigating to ForeignSessionTab.
     *
     * @param session The foreign session that the tab belongs to.
     * @param tab The tab to open.
     * @param windowDisposition The WindowOpenDisposition flag.
     */
    public void openForeignSessionTab(ForeignSession session, ForeignSessionTab tab,
            int windowDisposition) {
        NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_FOREIGN_SESSION);
        mForeignSessionHelper.openForeignSessionTab(mTab, session, tab, windowDisposition);
    }

    /**
     * Restores a recently closed tab.
     *
     * @param tab The tab to open.
     * @param windowDisposition The WindowOpenDisposition value specifying whether the tab should
     *         be restored into the current tab or a new tab.
     */
    public void openRecentlyClosedTab(RecentlyClosedTab tab, int windowDisposition) {
        NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_RECENTLY_CLOSED_ENTRY);
        mRecentlyClosedBridge.openRecentlyClosedTab(mTab, tab, windowDisposition);
    }

    /**
     * Opens the history page.
     */
    public void openHistoryPage() {
        mTab.loadUrl(new LoadUrlParams(UrlConstants.HISTORY_URL));
        StartupMetrics.getInstance().recordOpenedHistory();
    }

    /**
     * Returns a 16x16 favicon for a given synced url.
     *
     * @param url The url to fetch the favicon for.
     * @return 16x16 favicon or null if favicon unavailable.
     */
    public Bitmap getSyncedFaviconImageForURL(String url) {
        return mFaviconHelper.getSyncedFaviconImageForURL(mProfile, url);
    }

    /**
     * Fetches a favicon for snapshot document url which is returned via callback.
     *
     * @param url The url to fetch a favicon for.
     * @param size the desired favicon size.
     * @param faviconCallback the callback to be invoked when the favicon is available.
     *
     * @return may return false if we could not fetch the favicon.
     */
    public boolean getLocalFaviconForUrl(String url, int size,
            FaviconImageCallback faviconCallback) {
        return mFaviconHelper.getLocalFaviconImageForURL(mProfile, url, size, faviconCallback);
    }

    /**
     * Sets a callback to be invoked when recently closed tabs or foreign sessions documents have
     * been updated.
     *
     * @param updatedCallback the listener to be invoked.
     */
    public void setUpdatedCallback(UpdatedCallback updatedCallback) {
        mUpdatedCallback = updatedCallback;
    }

    /**
     * Sets the persistent expanded/collapsed state of the currently open tabs list.
     *
     * @param isCollapsed Whether the currently open tabs list is collapsed.
     */
    public void setCurrentlyOpenTabsCollapsed(boolean isCollapsed) {
        mNewTabPagePrefs.setCurrentlyOpenTabsCollapsed(isCollapsed);
    }

    /**
     * Determine the expanded/collapsed state of the currently open tabs list.
     *
     * @return Whether the currently open tabs list is collapsed.
     */
    public boolean isCurrentlyOpenTabsCollapsed() {
        return mNewTabPagePrefs.getCurrentlyOpenTabsCollapsed();
    }

    /**
     * Sets the state for showing all tabs in the currently open tabs list. This is intended to
     * be overridden in extending classes and set to true when the user clicks the "More" button
     * at the end of the list.
     * @param showingAll Whether the currently open tabs list should start to show all.
     */
    public void setCurrentlyOpenTabsShowAll(boolean showingAll) {
    }

    /**
     * @return Whether the currently open tabs group shows all tabs. If it is not, only a limited
     * number of tabs is shown with a "More" button at the end of the list to show all.
     */
    public boolean isCurrentlyOpenTabsShowingAll() {
        return false;
    }

    /**
     * Closes the specified currently open tab.
     * @param tab Information about the tab that should be closed.
     */
    public void closeTab(CurrentlyOpenTab tab) {
    }

    /**
     * Sets the persistent expanded/collapsed state of a foreign session list.
     *
     * @param session foreign session to collapsed.
     * @param isCollapsed Whether the session is collapsed or expanded.
     */
    public void setForeignSessionCollapsed(ForeignSession session, boolean isCollapsed) {
        mNewTabPagePrefs.setForeignSessionCollapsed(session, isCollapsed);
    }

    /**
     * Determine the expanded/collapsed state of a foreign session list.
     *
     * @param session foreign session whose state to obtain.
     *
     * @return Whether the session is collapsed.
     */
    public boolean getForeignSessionCollapsed(ForeignSession session) {
        return mNewTabPagePrefs.getForeignSessionCollapsed(session);
    }

    /**
     * Sets the persistent expanded/collapsed state of the recently closed tabs list.
     *
     * @param isCollapsed Whether the recently closed tabs list is collapsed.
     */
    public void setRecentlyClosedTabsCollapsed(boolean isCollapsed) {
        mNewTabPagePrefs.setRecentlyClosedTabsCollapsed(isCollapsed);
    }

    /**
     * Determine the expanded/collapsed state of the recently closed tabs list.
     *
     * @return Whether the recently closed tabs list is collapsed.
     */
    public boolean isRecentlyClosedTabsCollapsed() {
        return mNewTabPagePrefs.getRecentlyClosedTabsCollapsed();
    }

   /**
     * Remove Foreign session to display. Note that it might reappear during the next sync if the
     * session is not orphaned.
     *
     * This is mainly for when user wants to delete an orphaned session.
     * @param session Session to be deleted.
     */
    public void deleteForeignSession(ForeignSession session) {
        mForeignSessionHelper.deleteForeignSession(session);
    }

    /**
     * Clears the list of recently closed tabs.
     */
    public void clearRecentlyClosedTabs() {
        mRecentlyClosedBridge.clearRecentlyClosedTabs();
    }

    /**
     * Determine whether the sync promo needs to be displayed.
     *
     * @return Whether sync promo should be displayed.
     */
    public boolean shouldDisplaySyncPromo() {
        if (SigninManager.get(mContext).isSigninDisabledByPolicy()) {
            return false;
        }

        return !AndroidSyncSettings.isSyncEnabled(mContext) || mForeignSessions.isEmpty();
    }

    /**
     * Collapse the sync promo.
     *
     * @param isCollapsed Whether the sync promo is collapsed.
     */
    public void setSyncPromoCollapsed(boolean isCollapsed) {
        mNewTabPagePrefs.setSyncPromoCollapsed(isCollapsed);
    }

    /**
     * Determine whether the sync promo is collapsed.
     *
     * @return Whether the sync promo is collapsed.
     */
    public boolean isSyncPromoCollapsed() {
        return mNewTabPagePrefs.getSyncPromoCollapsed();
    }

    protected void postUpdate() {
        if (mUpdatedCallback != null) {
            mUpdatedCallback.onUpdated();
        }
    }

    // SignInStateObserver
    @Override
    public void onSignedIn() {
        androidSyncSettingsChanged();
    }

    @Override
    public void onSignedOut() {
        androidSyncSettingsChanged();
    }

    // AndroidSyncSettingsObserver
    @Override
    public void androidSyncSettingsChanged() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateForeignSessions();
                postUpdate();
                for (AndroidSyncSettingsObserver observer : mObservers) {
                    observer.androidSyncSettingsChanged();
                }
            }
        });
    }

    // SyncPromoModel
    @Override
    public boolean isSyncEnabled() {
        return AndroidSyncSettings.isSyncEnabled(mContext);
    }

    @Override
    public boolean isSignedIn() {
        return ChromeSigninController.get(mContext).isSignedIn();
    }

    @Override
    public void enableSync() {
        SyncController.get(mContext).start();
    }

    @Override
    public void registerForSyncUpdates(AndroidSyncSettingsObserver changeListener) {
        mObservers.addObserver(changeListener);
    }

    @Override
    public void unregisterForSyncUpdates(AndroidSyncSettingsObserver changeListener) {
        mObservers.removeObserver(changeListener);
    }

    @Override
    public ProfileDataCache getProfileDataCache() {
        if (mProfileDataCache == null) {
            mProfileDataCache = new ProfileDataCache(mContext, Profile.getLastUsedProfile());
        }
        return mProfileDataCache;
    }
}
