// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.policy;

import android.content.Context;

import org.chromium.content_public.browser.WebContents;

/**
 * Base class for policy auditors providing an empty implementation.
 */
public class PolicyAuditor {
    /**
     * Events that a policy administrator may want to track.
     */
    public enum AuditEvent {
        OPEN_URL_SUCCESS,
        OPEN_URL_FAILURE,
        OPEN_URL_BLOCKED,
        OPEN_POPUP_URL_SUCCESS,
        AUTOFILL_SELECTED
    }


    /**
     * Make it non-obvious to accidentally instantiate this outside of ChromeApplication.
     */
    protected PolicyAuditor() {}

    public void notifyAuditEvent(Context context, AuditEvent event, String url, String message) {}

    public void notifyCertificateFailure(int certificateFailure, Context context) {}

    public static native int nativeGetCertificateFailure(WebContents webContents);
}