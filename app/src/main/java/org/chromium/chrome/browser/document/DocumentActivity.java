// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegate;

/**
 * Deprecated class for running Chrome in document mode.  Kept around to force users into the
 * correct {@link Activity}.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class DocumentActivity extends ChromeActivity {
    // Legacy class names to match Chrome pre-44 activity names. See crbug.com/503807
    public static final String LEGACY_CLASS_NAME =
            "com.google.android.apps.chrome.document.DocumentActivity";
    public static final String LEGACY_INCOGNITO_CLASS_NAME =
            "com.google.android.apps.chrome.document.IncognitoDocumentActivity";

    private static final String TAG = "DocumentActivity";

    @Override
    protected boolean isStartedUpCorrectly(Intent intent) {
        int tabId = ActivityDelegate.getTabIdFromIntent(getIntent());

        // Fire a MAIN Intent to send the user back through ChromeLauncherActivity.
        Log.e(TAG, "User shouldn't be here.  Sending back to ChromeLauncherActivity.");

        // Try to bring this tab forward after migration.
        Intent tabbedIntent = null;
        if (tabId != Tab.INVALID_TAB_ID) tabbedIntent = Tab.createBringTabToFrontIntent(tabId);

        if (tabbedIntent == null) {
            tabbedIntent = new Intent(Intent.ACTION_MAIN);
            tabbedIntent.setPackage(getPackageName());
        }

        // Launch the other Activity in its own task so it stays when this one finishes.
        tabbedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tabbedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);

        startActivity(tabbedIntent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        Log.e(TAG, "Discarding Intent: Tab = " + tabId);
        return false;
    }

    @Override
    protected boolean handleBackPressed() {
        return false;
    }

    /**
     * Determines whether the given class can be classified as a DocumentActivity (this includes
     * both regular document activity and incognito document activity).
     * @param className The class name to inspect.
     * @return Whether the className is that of a document activity.
     */
    public static boolean isDocumentActivity(String className) {
        return TextUtils.equals(className, IncognitoDocumentActivity.class.getName())
                || TextUtils.equals(className, DocumentActivity.class.getName())
                || TextUtils.equals(className, LEGACY_CLASS_NAME)
                || TextUtils.equals(className, LEGACY_INCOGNITO_CLASS_NAME);
    }

    @Override
    protected TabModelSelector createTabModelSelector() {
        return null;
    }

    @Override
    protected Pair<TabCreator, TabCreator> createTabCreators() {
        return null;
    }

    @Override
    protected ChromeFullscreenManager createFullscreenManager() {
        return null;
    }
}
