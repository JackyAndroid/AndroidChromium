// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.Intent;
import android.os.SystemClock;

import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.webapps.ActivityAssigner;

/**
 * Manages the state of separate task Custom Tabs for Android versions prior to L.
 *
 * L provides the ability to force tasks to be in separate stacks via the NEW_DOCUMENT flag, but
 * no corresponding flag is available in KitKat and before.  To work around this, we predefine an
 * limited number of activities that we will cycle through.
 */
public class SeparateTaskManagedCustomTabActivity extends SeparateTaskCustomTabActivity {
    private static final String FORCE_FINISH = "CCT.ForceFinish";

    // Time at which an intent was received and handled.
    private long mIntentHandlingTimeMs = 0;

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();

        if (!isFinishing()) {
            ActivityAssigner.instance(ActivityAssigner.SEPARATE_TASK_CCT_NAMESPACE)
                    .markActivityUsed(getActivityIndex(), getIntent().getData().getAuthority());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        mIntentHandlingTimeMs = SystemClock.uptimeMillis();
        if (intent != null && intent.getBooleanExtra(FORCE_FINISH, false)) {
            finish();
            return;
        }
        super.onNewIntent(intent);
    }

    @Override
    public ChromeTabCreator getTabCreator(boolean incognito) {
        TabCreator tabCreator = super.getTabCreator(incognito);
        assert tabCreator instanceof ChromeTabCreator;
        return (ChromeTabCreator) tabCreator;
    }

    @Override
    protected IntentHandlerDelegate createIntentHandlerDelegate() {
        return new IntentHandlerDelegate() {
            @Override
            public void processWebSearchIntent(String query) {
            }

            @Override
            public void processUrlViewIntent(String url, String referer, String headers,
                    TabOpenType tabOpenType, String externalAppId, int tabIdToBringToFront,
                    boolean hasUserGesture, Intent intent) {
                Tab currentTab = getTabCreator(false).launchUrlFromExternalApp(
                        url, referer, headers, externalAppId, true, intent, mIntentHandlingTimeMs);

                // Close all existing tabs from the previous session.
                TabModel tabModel = getTabModelSelector().getModel(false);
                for (int i = tabModel.getCount() - 1; i >= 0; i--) {
                    if (tabModel.getTabAt(i).equals(currentTab)) continue;
                    tabModel.closeTab(tabModel.getTabAt(i), false, false, false);
                }
            }
        };
    }

    private int getActivityIndex() {
        // Cull out the activity index from the class name.
        String baseClassName = SeparateTaskCustomTabActivity.class.getSimpleName();
        String className = this.getClass().getSimpleName();
        assert className.matches("^" + baseClassName + "[0-9]+$");
        String indexString = className.substring(baseClassName.length());
        return Integer.parseInt(indexString);
    }

    @Override
    protected void handleFinishAndClose() {
        Intent intent = new Intent(getIntent());
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.putExtra(FORCE_FINISH, true);
        startActivity(intent);
    }
}
