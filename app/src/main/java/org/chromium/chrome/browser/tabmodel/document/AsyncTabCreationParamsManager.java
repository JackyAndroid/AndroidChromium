// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.util.SparseArray;

import org.chromium.base.ThreadUtils;

/**
 * Data that will be used later when a tab is opened via an intent. Often only the necessary
 * subset of the data will be set. All data is removed once the tab finishes initializing.
 */
public class AsyncTabCreationParamsManager {
    /** A map of tab IDs to AsyncTabCreationParams consumed by Activities started asynchronously. */
    private static SparseArray<AsyncTabCreationParams> sAsyncTabCreationParams;

    /**
     * Stores AsyncTabCreationParams used when the tab with the given ID is launched via intent.
     * @param tabId The ID of the tab that will be launched via intent.
     * @param params The AsyncTabCreationParams to use when creating the Tab.
     */
    public static void add(int tabId, AsyncTabCreationParams params) {
        ensureInitialized();
        sAsyncTabCreationParams.put(tabId, params);
    }

    /**
     * @return Retrieves and removes AsyncTabCreationParams for a particular tab id.
     */
    public static AsyncTabCreationParams remove(int tabId) {
        ensureInitialized();
        AsyncTabCreationParams data = sAsyncTabCreationParams.get(tabId);
        sAsyncTabCreationParams.remove(tabId);
        return data;
    }

    private static void ensureInitialized() {
        ThreadUtils.assertOnUiThread();
        if (sAsyncTabCreationParams == null) {
            sAsyncTabCreationParams = new SparseArray<AsyncTabCreationParams>();
        }
    }

    private AsyncTabCreationParamsManager() {
    }
}