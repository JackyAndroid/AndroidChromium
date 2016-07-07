// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.rappor;

import org.chromium.base.annotations.JNINamespace;

/**
 * JNI bridge to the native Rappor service from Java.
 */
@JNINamespace("rappor")
public final class RapporServiceBridge {
    private RapporServiceBridge() {
        // Only for static use.
    }

    public static void sampleString(String metric, String sampleValue) {
        nativeSampleString(metric, sampleValue);
    }

    public static void sampleDomainAndRegistryFromURL(String metric, String url) {
        nativeSampleDomainAndRegistryFromURL(metric, url);
    }

    private static native void nativeSampleDomainAndRegistryFromURL(String metric, String url);
    private static native void nativeSampleString(String metric, String sampleValue);
}
