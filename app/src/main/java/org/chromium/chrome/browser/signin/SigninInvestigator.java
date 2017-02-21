// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

/**
 * A bridge to call shared investigator logic.
 */
public final class SigninInvestigator {
    private SigninInvestigator() {}

    /**
     * Calls into native code to investigate potential ramifications of a
     * successful signin from the account corresponding to the given email.
     *
     * @returns int value that corresponds to enum InvestigatedScenario.
     */
    public static int investigate(String currentEmail) {
        return nativeInvestigate(currentEmail);
    }

    // Native methods
    private static native int nativeInvestigate(String currentEmail);
}
