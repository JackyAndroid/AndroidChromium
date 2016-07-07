// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import org.chromium.base.metrics.RecordHistogram;

/**
 * Centralizes UMA data collection for signin promo. All calls must be made from the UI thread.
 */
public final class SigninPromoUma {
    // Signin.AndroidSigninPromo defined in tools/metrics/histograms/histograms.xml.
    public static final int SIGNIN_PROMO_ENABLED = 0;
    public static final int SIGNIN_PROMO_SHOWN = 1;
    public static final int SIGNIN_PROMO_DECLINED = 2;
    public static final int SIGNIN_PROMO_ACCEPTED = 3;
    public static final int SIGNIN_PROMO_ACCEPTED_WITH_ADVANCED = 4;
    public static final int SIGNIN_PROMO_COUNT = 5;

    /**
     * Logs signin promo action to UMA histogram.
     */
    public static void recordAction(int action) {
        assert action >= 0 && action < SIGNIN_PROMO_COUNT;
        RecordHistogram.recordEnumeratedHistogram(
                "Signin.AndroidSigninPromoAction", action, SIGNIN_PROMO_COUNT);
    }
}
