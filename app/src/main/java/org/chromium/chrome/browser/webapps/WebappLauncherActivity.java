// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.WebappAuthenticator;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.IntentUtils;

import java.lang.ref.WeakReference;

/**
 * Launches web apps.  This was separated from the ChromeLauncherActivity because the
 * ChromeLauncherActivity is not allowed to be excluded from Android's Recents: crbug.com/517426.
 */
public class WebappLauncherActivity extends Activity {
    /**
     * Action fired when an Intent is trying to launch a WebappActivity.
     * Never change the package name or the Intents will fail to launch.
     */
    public static final String ACTION_START_WEBAPP =
            "com.google.android.apps.chrome.webapps.WebappManager.ACTION_START_WEBAPP";

    private static final String TAG = "cr.webapps";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        WebappInfo webappInfo = WebappInfo.create(intent);
        String webappId = webappInfo.id();
        String webappUrl = webappInfo.uri().toString();
        int webappSource = webappInfo.source();

        if (webappId != null && webappUrl != null) {
            String webappMacString = IntentUtils.safeGetStringExtra(
                    intent, ShortcutHelper.EXTRA_MAC);
            byte[] webappMac =
                    webappMacString == null ? null : Base64.decode(webappMacString, Base64.DEFAULT);

            Intent launchIntent = null;
            if (webappMac != null && WebappAuthenticator.isUrlValid(this, webappUrl, webappMac)) {
                LaunchMetrics.recordHomeScreenLaunchIntoStandaloneActivity(webappUrl, webappSource);

                String activityName = WebappActivity.class.getName();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    // Specifically assign the app to a particular WebappActivity instance.
                    int activityIndex = ActivityAssigner.instance(this).assign(webappId);
                    activityName += String.valueOf(activityIndex);
                }

                // Create an intent to launch the Webapp in an unmapped WebappActivity.
                launchIntent = new Intent();
                launchIntent.setClassName(this, activityName);
                webappInfo.setWebappIntentExtras(launchIntent);

                // On L+, firing intents with the exact same data should relaunch a particular
                // Activity.
                launchIntent.setAction(Intent.ACTION_VIEW);
                launchIntent.setData(Uri.parse(WebappActivity.WEBAPP_SCHEME + "://" + webappId));
            } else {
                Log.e(TAG, "Shortcut (" + webappUrl + ") opened in Chrome.");

                // The shortcut data doesn't match the current encoding.  Change the intent action
                // launch the URL with a VIEW Intent in the regular browser.
                launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webappUrl));
                launchIntent.setClassName(getPackageName(), ChromeLauncherActivity.class.getName());
                launchIntent.putExtra(ShortcutHelper.REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB, true);
                launchIntent.putExtra(ShortcutHelper.EXTRA_SOURCE, webappSource);
            }

            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | ApiCompatibilityUtils.getActivityNewDocumentFlag());
            startActivity(launchIntent);
        }

        ApiCompatibilityUtils.finishAndRemoveTask(this);
    }

    /**
     * Brings a live WebappActivity back to the foreground if one exists for the given tab ID.
     * @param tabId ID of the Tab to bring back to the foreground.
     * @return True if a live WebappActivity was found, false otherwise.
     */
    public static boolean bringWebappToFront(int tabId) {
        if (tabId == Tab.INVALID_TAB_ID) return false;

        for (WeakReference<Activity> activityRef : ApplicationStatus.getRunningActivities()) {
            Activity activity = activityRef.get();
            if (activity == null || !(activity instanceof WebappActivity)) continue;

            WebappActivity webappActivity = (WebappActivity) activity;
            if (webappActivity.getActivityTab() != null
                    && webappActivity.getActivityTab().getId() == tabId) {
                Tab tab = webappActivity.getActivityTab();
                tab.getTabWebContentsDelegateAndroid().activateContents();
                return true;
            }
        }

        return false;
    }
}
