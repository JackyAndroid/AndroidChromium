// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;

import org.chromium.content_public.browser.LoadUrlParams;

/**
 * Interface to handle custom tab calls whenever the session id matched.
 * TODO(yusufo): Add a way to handle mayLaunchUrl as well.
 */
public interface CustomTabContentHandler {
    /**
     * Loads a new url inside the {@link CustomTabContentHandler}, and tracks
     * its load time.
     *
     * @param params The params to use while loading the url.
     * @param timestamp The intent arrival timestamp, as returned by
     *                  {@link SystemClock#elapsedRealtime()}.
     */
    void loadUrlAndTrackFromTimestamp(LoadUrlParams params, long timestamp);

    /**
     * @return The session this {@link CustomTabContentHandler} is associated with.
     */
    IBinder getSession();

    /**
     * Check whether an intent is valid or should be ignored within this content handler.
     * @param intent The intent to check.
     * @return Whether the intent should be ignored.
     */
    boolean shouldIgnoreIntent(Intent intent);

    /**
     * Updates the content of custom action button shown on the toolbar.
     * @return Whether the action button has been updated.
     */
    boolean updateActionButton(Bitmap bitmap, String description);
}
