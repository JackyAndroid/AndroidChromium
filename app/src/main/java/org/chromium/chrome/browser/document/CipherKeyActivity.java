// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.chromium.content.browser.crypto.CipherFactory;

/**
 * An activity responsible for retaining the incognito cipher key for the duration of an incognito
 * session. The activity gets brought forward for two reasons: to restore the cipher key before a
 * new incognito window is launched or to save the current cipher key. If brought forward to
 * restore, the activity is passed an intent and options to call after the restore process is
 * complete. Otherwise the activity just puts itself in background causing onSaveInstanceState to
 * get called and save the cipher keys to the activity's bundle.
 */
public class CipherKeyActivity extends Activity {

    /**
     * An intent name for specifying the next intent to invoke.
     */
    public static final String FORWARD_INTENT = "forward_intent";

    /**
     * An options Bundle to use with intent specified by "forward_intent".
     */
    public static final String FORWARD_OPTIONS = "forward_options";

    /**
     * Generates an intent to {@link CipherKeyActivity} to restore cipher keys from the bundle
     * if possible.
     * @param context The context to use.
     * @param forwardIntent The intent cipher key activity will call when it's done.
     * @param options The options to pass into startActivity call of the forward intent.
     * @return The intent.
     */
    public static Intent createIntent(Context context, Intent forwardIntent, Bundle options) {
        Intent intent = new Intent(context, CipherKeyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (forwardIntent != null) intent.putExtra(FORWARD_INTENT, forwardIntent);
        if (options != null) intent.putExtra(FORWARD_OPTIONS, options);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            CipherFactory.getInstance().restoreFromBundle(savedInstanceState);
        }
        Intent nextIntent = (Intent) getIntent().getParcelableExtra(FORWARD_INTENT);
        if (nextIntent != null) {
            startActivity(nextIntent, (Bundle) getIntent().getParcelableExtra(FORWARD_OPTIONS));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        moveTaskToBack(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        CipherFactory.getInstance().saveToBundle(outState);
    }

}
