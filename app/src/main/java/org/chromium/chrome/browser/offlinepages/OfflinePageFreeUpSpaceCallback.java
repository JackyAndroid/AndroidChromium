// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

/** Interface for informing the caller that the clean up operation is completed. */
public interface OfflinePageFreeUpSpaceCallback {
    /** Called when clean up operation is completed. */
    void onFreeUpSpaceDone();

    /** Called when clean up operation is canceled. */
    void onFreeUpSpaceCancelled();
}
