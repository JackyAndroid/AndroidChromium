// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.nfc;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Process;
import android.util.Log;

/**
 * Initializes Android Beam (sharing URL via NFC) for devices that have NFC. If user taps their
 * device with another Beam capable device, then Chrome gets the current URL, filters for security
 * and returns the result to Android.
 */
public final class BeamController {
    /**
     * If the device has NFC, construct a BeamCallback and pass it to Android.
     *
     * @param activity Activity that is sending out beam messages.
     * @param provider Provider that returns the URL that should be shared.
     */
    public static void registerForBeam(final Activity activity, final BeamProvider provider) {
        final NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
        if (nfcAdapter == null) return;
        if (activity.checkPermission(Manifest.permission.NFC, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_DENIED) return;
        try {
            final BeamCallback beamCallback = new BeamCallback(activity, provider);
            nfcAdapter.setNdefPushMessageCallback(beamCallback, activity);
            nfcAdapter.setOnNdefPushCompleteCallback(beamCallback, activity);
        } catch (IllegalStateException e) {
            Log.w("BeamController", "NFC registration failure. Can't retry, giving up.");
        }
    }
}
