// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.nfc;

/**
 * Interface for Activities that use Beam (sharing URL via NFC) controller.
 */
public interface BeamProvider {
    /**
     * @return URL of the current tab, null otherwise (e.g. user is in tab switcher).
     */
    String getTabUrlForBeam();
}
