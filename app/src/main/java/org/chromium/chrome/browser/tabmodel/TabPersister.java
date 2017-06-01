// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import android.util.Log;

import org.chromium.chrome.browser.TabState;

import java.io.File;

/**
 * Interacts with the file system to persist Tab and TabModel data.
 * TODO(dfalcantara): Flesh this out by moving common subclass functions into it.
 */
public abstract class TabPersister {
    private static final String TAG = "TabPersister";

    /** @return File representing the directory that is used to store Tab state information. */
    protected abstract File getStateDirectory();

    /**
     * Returns a file pointing at the TabState corresponding to the given Tab.
     * @param tabId ID of the TabState to locate.
     * @param encrypted Whether or not the tab is encrypted.
     * @return File pointing at the TabState for the Tab.
     */
    protected File getTabStateFile(int tabId, boolean encrypted) {
        return TabState.getTabStateFile(getStateDirectory(), tabId, encrypted);
    }

    /**
     * Saves the TabState with the given ID.
     * @param tabId ID of the Tab.
     * @param encrypted Whether or not the TabState is encrypted.
     * @param state TabState for the Tab.
     */
    public boolean saveTabState(int tabId, boolean encrypted, TabState state) {
        if (state == null) return false;

        try {
            TabState.saveState(getTabStateFile(tabId, encrypted), state, encrypted);
            return true;
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "Out of memory error while attempting to save tab state.  Erasing.");
            deleteTabState(tabId, encrypted);
        }

        return false;
    }

    /**
     * Deletes the TabState corresponding to the given Tab.
     * @param id ID of the TabState to delete.
     * @param encrypted Whether or not the tab is encrypted.
     */
    public void deleteTabState(int id, boolean encrypted) {
        TabState.deleteTabState(getStateDirectory(), id, encrypted);
    }
}