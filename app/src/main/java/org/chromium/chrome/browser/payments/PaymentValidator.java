// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.payments.mojom.PaymentDetails;

import java.nio.ByteBuffer;

/**
 * Static class to represent a JNI interface to a C++ validation library.
 */
@JNINamespace("payments")
public class PaymentValidator {
    public static boolean validatePaymentDetails(PaymentDetails details) {
        if (details == null) {
            return false;
        }
        return nativeValidatePaymentDetails(details.serialize());
    }

    private static native boolean nativeValidatePaymentDetails(ByteBuffer buffer);
};
