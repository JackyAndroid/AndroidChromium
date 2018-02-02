// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import org.chromium.chrome.browser.media.router.cast.MediaSink;

import java.util.List;

/**
 * An interface provided to DiscoveryCallback in order to receive sinks.
 */
public interface DiscoveryDelegate {

    /**
     * Called when a new information about sinks availability becomes known.
     * @param sourceId The id of the source the sinks were found for.
     * @param sinks The list of sinks found, can be empty.
     */
    void onSinksReceived(String sourceId, List<MediaSink> sinks);
}
