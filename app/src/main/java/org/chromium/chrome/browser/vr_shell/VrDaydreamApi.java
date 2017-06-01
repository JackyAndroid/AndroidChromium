// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;

/**
 * Abstract away DaydreamImpl class, which may or may not be present at runtime depending on compile
 * flags.
 */
public interface VrDaydreamApi {
    /**
     * @return Whether the current device is Daydream Ready.
     */
    boolean isDaydreamReadyDevice();

    /**
     * Register the intent to launch after phone inserted into a Daydream viewer.
     * @return false if unable to acquire DaydreamApi instance.
     */
    boolean registerDaydreamIntent(final PendingIntent pendingIntent);

    /**
     * Unregister the intent if any.
     * @return false if unable to acquire DaydreamApi instance.
     */
    boolean unregisterDaydreamIntent();

    /**
     * Create an Intent to launch a VR activity with the given component name.
     */
    Intent createVrIntent(final ComponentName componentName);

    /**
     * Launch the given Intent in VR mode.
     * @return false if unable to acquire DaydreamApi instance.
     */
    boolean launchInVr(final PendingIntent pendingIntent);

    /**
     * @param requestCode The requestCode used by startActivityForResult.
     * @param intent The data passed to VrCore as part of the exit request.
     * @return false if unable to acquire DaydreamApi instance.
     */
    boolean exitFromVr(int requestCode, final Intent intent);

    /**
     * Sets VR Mode to |enabled|.
     */
    void setVrModeEnabled(boolean enabled);

    /**
     * @return Whether the current Viewer is a Daydream Viewer.
     */
    Boolean isDaydreamCurrentViewer();

    /**
     * Launch the stereoscopic, 3D VR launcher homescreen.
     */
    void launchVrHomescreen();
}
