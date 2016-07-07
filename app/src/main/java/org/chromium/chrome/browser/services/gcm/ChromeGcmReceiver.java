// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services.gcm;

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.gcm.GcmListenerService;
import com.google.android.gms.gcm.GcmReceiver;

import org.chromium.base.Log;

/**
 * The {@link GcmReceiver} occasionally crashes with a SecurityException when dispatching intents
 * to start the {@link GcmListenerService}. This is suspected to be caused by an Android bug which
 * resolves the intent to a different package.
 * See crbug/528219.
 *
 * This is a temporary workaround to catch the exception to keep the application from crashing and
 * record the error.
 * TODO(khushalsagar): Switch to the GcmReceiver in the GCM client library once GMS is upgraded to
 * Urda.
 */
public class ChromeGcmReceiver extends GcmReceiver {

    private static final String TAG = "GCM";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            super.onReceive(context, intent);
            GcmUma.recordGcmReceiverHistogram(context, GcmUma.UMA_GCM_RECEIVER_SUCCESS);
        } catch (SecurityException exception) {
            GcmUma.recordGcmReceiverHistogram(context,
                    GcmUma.UMA_GCM_RECEIVER_ERROR_SECURITY_EXCEPTION);
            Log.e(TAG, "Failure in starting GcmReceiver : %s", exception);
        }
    }

}
