// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.instantapps;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Used to allow the final intent recipient to verify the caller (startActivityForResult allows
 * them to do so) without launching the final activity in the same task as Chrome.
 */
public class AuthenticatedProxyActivity extends Activity {
    /**
     * The intent extra we expect to receive with the intent that we want to forward.
     */
    public static final String AUTHENTICATED_INTENT_EXTRA =
            "org.chromium.chrome.browser.instantapps.AUTH_INTENT";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent forwardIntent = (Intent) getIntent().getParcelableExtra(AUTHENTICATED_INTENT_EXTRA);
        if (forwardIntent != null) {
            startActivityForResult(forwardIntent, -1);
        }
        finish();
    }
}
