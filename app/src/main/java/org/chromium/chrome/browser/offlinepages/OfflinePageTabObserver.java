// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.net.NetworkChangeNotifier;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that observes events for a tab which has an associated offline page. It is created when
 * the first offline page is loaded in any tab. When additional offline pages are opened, they are
 * all watched by the same observer. This observer will decide when to show a reload snackbar for
 * those tabs. The following conditions need to be met to show the snackbar:
 * <ul>
 *   <li>Tab has to be shown,</li>
 *   <li>Offline page has to be loaded,</li>
 *   <li>Chrome is connected to the web,</li>
 *   <li>Unless triggering condition is change in network, snackbar hasn't been shown for that
 *   tab.</li>
 * </ul>
 * When the last tab with offline page is closed or navigated away from, this observer stops
 * listening to network changes.
 */
public class OfflinePageTabObserver
        extends EmptyTabObserver implements NetworkChangeNotifier.ConnectionTypeObserver {
    private static final String TAG = "OfflinePageTO";

    /** Class for keeping the state of observed tabs. */
    private static class TabState {
        /** Whether content in a tab finished loading. */
        public boolean isLoaded;
        /** Whether a snackbar was shown for the tab. */
        public boolean wasSnackbarSeen;

        public TabState(boolean isLoaded) {
            this.isLoaded = isLoaded;
            this.wasSnackbarSeen = false;
        }
    }

    private Context mContext;
    private SnackbarManager mSnackbarManager;
    private SnackbarController mSnackbarController;

    /** Map of observed tabs. */
    private final Map<Integer, TabState> mObservedTabs = new HashMap<>();
    private boolean mIsObservingNetworkChanges;

    /** Current tab, kept track of for the network change notification. */
    private Tab mCurrentTab;

    private static OfflinePageTabObserver sInstance;

    static void init(Context context, SnackbarManager manager, SnackbarController controller) {
        if (sInstance == null) {
            sInstance = new OfflinePageTabObserver(context, manager, controller);
            return;
        }
        sInstance.reinitialize(context, manager, controller);
    }

    static OfflinePageTabObserver getInstance() {
        return sInstance;
    }

    @VisibleForTesting
    static void setInstanceForTesting(OfflinePageTabObserver instance) {
        sInstance = instance;
    }

    /**
     * Create and attach a tab observer if we don't already have one, otherwise update it.
     * @param tab The tab we are adding an observer for.
     */
    public static void addObserverForTab(Tab tab) {
        assert getInstance() != null;
        getInstance().startObservingTab(tab);
        getInstance().maybeShowReloadSnackbar(tab, false);
    }

    /**
     * Builds a new OfflinePageTabObserver.
     * @param context Android context.
     * @param snackbarManager The snackbar manager to show and dismiss snackbars.
     * @param snackbarController Controller to use to build the snackbar.
     */
    OfflinePageTabObserver(Context context, SnackbarManager snackbarManager,
            SnackbarController snackbarController) {
        reinitialize(context, snackbarManager, snackbarController);

        // The first time observer is created snackbar has net yet been shown.
        mIsObservingNetworkChanges = false;
    }

    // Methods from EmptyTabObserver
    @Override
    public void onPageLoadFinished(Tab tab) {
        Log.d(TAG, "onPageLoadFinished");
        if (isObservingTab(tab)) {
            mObservedTabs.get(tab.getId()).isLoaded = true;
            maybeShowReloadSnackbar(tab, false);
        }
    }

    @Override
    public void onShown(Tab tab) {
        Log.d(TAG, "onShow");
        maybeShowReloadSnackbar(tab, false);
        mCurrentTab = tab;
    }

    @Override
    public void onHidden(Tab hiddenTab) {
        Log.d(TAG, "onHidden");
        mCurrentTab = null;
        mSnackbarManager.dismissSnackbars(mSnackbarController);
    }

    @Override
    public void onDestroyed(Tab tab) {
        Log.d(TAG, "onDestroyed");
        stopObservingTab(tab);
        mSnackbarManager.dismissSnackbars(mSnackbarController);
    }

    @Override
    public void onUrlUpdated(Tab tab) {
        Log.d(TAG, "onUrlUpdated");
        if (!tab.isOfflinePage()) {
            stopObservingTab(tab);
        } else {
            if (isObservingTab(tab)) {
                mObservedTabs.get(tab.getId()).isLoaded = false;
                mObservedTabs.get(tab.getId()).wasSnackbarSeen = false;
            }
        }
        // In case any snackbars are showing, dismiss them before we navigate away.
        mSnackbarManager.dismissSnackbars(mSnackbarController);
    }

    void startObservingTab(Tab tab) {
        if (!tab.isOfflinePage()) return;

        mCurrentTab = tab;

        // If we are not observing the tab yet, let's.
        if (!isObservingTab(tab)) {
            // Adding a tab happens from inside of onPageLoadFinished, therefore if this is the time
            // we start observing the tab, the page inside of it is already loaded.
            mObservedTabs.put(tab.getId(), new TabState(true));
            tab.addObserver(this);
        }

        // If we are not observing network changes yet, let's.
        if (!isObservingNetworkChanges()) {
            startObservingNetworkChanges();
            mIsObservingNetworkChanges = true;
        }
    }

    /**
     * Removes the observer for a tab with the specified tabId.
     * @param tab tab that was observed.
     */
    void stopObservingTab(Tab tab) {
        // If we are observing the tab, stop.
        if (isObservingTab(tab)) {
            mObservedTabs.remove(tab.getId());
            tab.removeObserver(this);
        }

        // If there are not longer any tabs being observed, stop listening for network changes.
        if (mObservedTabs.isEmpty() && isObservingNetworkChanges()) {
            stopObservingNetworkChanges();
            mIsObservingNetworkChanges = false;
        }
    }

    // Methods from ConnectionTypeObserver.
    @Override
    public void onConnectionTypeChanged(int connectionType) {
        Log.d(TAG, "Got connectivity event, connectionType: " + connectionType + ", is connected: "
                        + isConnected() + ", controller: " + mSnackbarController);
        maybeShowReloadSnackbar(mCurrentTab, true);

        // Since we are loosing the connection, next time we connect, we still want to show a
        // snackbar. This works in event that onConnectionTypeChanged happens, while Chrome is not
        // visible. Making it visible after that would not trigger the snackbar, even though
        // connection state changed. See http://crbug.com/651410
        if (!isConnected()) {
            for (TabState tabState : mObservedTabs.values()) {
                tabState.wasSnackbarSeen = false;
            }
        }
    }

    @VisibleForTesting
    boolean isObservingTab(Tab tab) {
        return mObservedTabs.containsKey(tab.getId());
    }

    @VisibleForTesting
    boolean isLoadedTab(Tab tab) {
        return isObservingTab(tab) && mObservedTabs.get(tab.getId()).isLoaded;
    }

    @VisibleForTesting
    boolean wasSnackbarSeen(Tab tab) {
        return isObservingTab(tab) && mObservedTabs.get(tab.getId()).wasSnackbarSeen;
    }

    @VisibleForTesting
    boolean isObservingNetworkChanges() {
        return mIsObservingNetworkChanges;
    }

    @VisibleForTesting
    boolean isConnected() {
        return OfflinePageUtils.isConnected();
    }

    @VisibleForTesting
    boolean isShowingOfflinePreview(Tab tab) {
        return OfflinePageUtils.isShowingOfflinePreview(tab);
    }

    void maybeShowReloadSnackbar(Tab tab, boolean isNetworkEvent) {
        // Exclude Offline Previews, as there is a seperate UI for previews.
        if (tab == null || tab.isFrozen() || tab.isHidden() || !tab.isOfflinePage()
                || isShowingOfflinePreview(tab) || !isConnected() || !isLoadedTab(tab)
                || (wasSnackbarSeen(tab) && !isNetworkEvent)) {
            // Conditions to show a snackbar are not met.
            return;
        }

        showReloadSnackbar(tab);
        mObservedTabs.get(tab.getId()).wasSnackbarSeen = true;
    }

    @VisibleForTesting
    void showReloadSnackbar(Tab tab) {
        OfflinePageUtils.showReloadSnackbar(
                mContext, mSnackbarManager, mSnackbarController, tab.getId());
    }

    @VisibleForTesting
    void startObservingNetworkChanges() {
        NetworkChangeNotifier.addConnectionTypeObserver(this);
    }

    @VisibleForTesting
    void stopObservingNetworkChanges() {
        NetworkChangeNotifier.removeConnectionTypeObserver(this);
    }

    boolean isCurrentContext(Context context) {
        return mContext == context;
    }

    void reinitialize(Context context, SnackbarManager manager, SnackbarController controller) {
        // TODO(fgorski): Work out if we need to also update network changes observer with the
        // context change.
        mContext = context;
        mSnackbarManager = manager;
        mSnackbarController = controller;
    }
}
