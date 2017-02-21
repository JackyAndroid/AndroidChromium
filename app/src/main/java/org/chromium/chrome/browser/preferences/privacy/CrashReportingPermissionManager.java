// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

/**
 * Interface for crash reporting permissions.
 */
public interface CrashReportingPermissionManager {
    /**
     * Checks whether this client is in-sample for crashes. See
     * {@link org.chromium.chrome.browser.metrics.UmaUtils#isClientInMetricsSample} for details.
     *
     * @returns boolean Whether client is in-sample.
     */
    public boolean isClientInMetricsSample();

    /**
     * Check whether to allow uploading crash dump now based on user consent and connectivity.
     *
     * @return whether to allow uploading crash dump now.
     */
    public boolean isUploadPermitted();

    /**
     * Check whether to allow UMA uploading.
     *
     * @return whether to allow UMA uploading.
     */
    public boolean isUmaUploadPermitted();

    /**
     * Check whether to allow uploading crash dump now based on command line flag only.
     *
     * @return whether experimental flag doesn't disable uploading crash dump.
     */
    public boolean isUploadCommandLineDisabled();

    /**
     * Check whether to allow uploading crash dump now based on user consent only.
     *
     * @return whether user allows uploading crash dump.
     */
    public boolean isUploadUserPermitted();

    /**
     * Check whether uploading crash dump should be in constrained mode based on user experiments
     * and current connection type. This function shows whether in general uploads should be limited
     * for this user and does not determine whether crash uploads are currently possible or not. Use
     * |isUploadPermitted| function for that before calling |isUploadLimited|.
     *
     * @return whether uploading logic should be constrained.
     */
    public boolean isUploadLimited();

    /**
     * Check whether to ignore all consent and upload, used by test devices to avoid UI dependency.
     *
     * @return whether crash dumps should be uploaded if at all possible.
     */
    public boolean isUploadEnabledForTests();
}
