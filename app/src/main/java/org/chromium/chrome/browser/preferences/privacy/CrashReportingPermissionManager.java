// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

/**
 * Interface for crash reporting permissions.
 */
public interface CrashReportingPermissionManager {
    /**
     * Check whether to allow uploading crash dump now based on user consent and connectivity.
     *
     * @return whether to allow uploading crash dump now.
     */
    public boolean isUploadPermitted();

    /**
     * Check whether uploading crash dump should be in constrained mode based on user experiments
     * and current connection type. This function shows whether in general uploads should be limited
     * for this user and does not determine whether crash uploads are currently possible or not. Use
     * |isUploadPermitted| function for that before calling |isUploadLimited|.
     *
     * @return whether uploading logic should be constrained.
     */
    public boolean isUploadLimited();
}
