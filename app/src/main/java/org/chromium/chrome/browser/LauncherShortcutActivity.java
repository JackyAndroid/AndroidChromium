// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Browser;

import org.chromium.chrome.browser.document.ChromeLauncherActivity;

/**
 * A helper activity for routing launcher shortcut intents.
 */
public class LauncherShortcutActivity extends Activity {
    private static final String ACTION_OPEN_NEW_TAB = "chromium.shortcut.action.OPEN_NEW_TAB";
    private static final String ACTION_OPEN_NEW_INCOGNITO_TAB =
            "chromium.shortcut.action.OPEN_NEW_INCOGNITO_TAB";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String intentAction = getIntent().getAction();

        // Exit early if the original intent action isn't for opening a new tab.
        if (!intentAction.equals(ACTION_OPEN_NEW_TAB)
                && !intentAction.equals(ACTION_OPEN_NEW_INCOGNITO_TAB)) {
            finish();
            return;
        }

        Intent newIntent = new Intent();
        newIntent.setAction(Intent.ACTION_VIEW);
        newIntent.setData(Uri.parse(UrlConstants.NTP_URL));
        newIntent.setClass(this, ChromeLauncherActivity.class);
        newIntent.putExtra(IntentHandler.EXTRA_INVOKED_FROM_SHORTCUT, true);
        newIntent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
        newIntent.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
        IntentHandler.addTrustedIntentExtras(newIntent, this);

        if (intentAction.equals(ACTION_OPEN_NEW_INCOGNITO_TAB)) {
            newIntent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
        }

        // This system call is often modified by OEMs and not actionable. http://crbug.com/619646.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            startActivity(newIntent);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        finish();
    }
}
