// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;

/**
 * A class used to record metrics for the Payment Request feature.
 */
public final class PaymentRequestMetrics {

    // PaymentRequestRequestedInformation defined in tools/metrics/histograms/histograms.xml.
    @VisibleForTesting
    public static final int REQUESTED_INFORMATION_NONE = 0;
    @VisibleForTesting
    public static final int REQUESTED_INFORMATION_EMAIL = 1 << 0;
    @VisibleForTesting
    public static final int REQUESTED_INFORMATION_PHONE = 1 << 1;
    @VisibleForTesting
    public static final int REQUESTED_INFORMATION_SHIPPING = 1 << 2;
    @VisibleForTesting
    public static final int REQUESTED_INFORMATION_NAME = 1 << 3;
    @VisibleForTesting
    public static final int REQUESTED_INFORMATION_MAX = 16;

    // PaymentRequestAbortReason defined in tools/metrics/histograms/histograms.xml.
    @VisibleForTesting
    public static final int ABORT_REASON_ABORTED_BY_USER = 0;
    @VisibleForTesting
    public static final int ABORT_REASON_ABORTED_BY_MERCHANT = 1;
    @VisibleForTesting
    public static final int ABORT_REASON_INVALID_DATA_FROM_RENDERER = 2;
    @VisibleForTesting
    public static final int ABORT_REASON_MOJO_CONNECTION_ERROR = 3;
    @VisibleForTesting
    public static final int ABORT_REASON_MOJO_RENDERER_CLOSING = 4;
    @VisibleForTesting
    public static final int ABORT_REASON_INSTRUMENT_DETAILS_ERROR = 5;
    @VisibleForTesting
    public static final int ABORT_REASON_NO_MATCHING_PAYMENT_METHOD = 6;
    @VisibleForTesting
    public static final int ABORT_REASON_NO_SUPPORTED_PAYMENT_METHOD = 7;
    @VisibleForTesting
    public static final int ABORT_REASON_OTHER = 8;
    @VisibleForTesting
    public static final int ABORT_REASON_MAX = 9;

    // PaymentRequestPaymentMethods defined in tools/metrics/histograms/histograms.xml.
    @VisibleForTesting
    public static final int SELECTED_METHOD_CREDIT_CARD = 0;
    @VisibleForTesting
    public static final int SELECTED_METHOD_ANDROID_PAY = 1;
    @VisibleForTesting
    public static final int SELECTED_METHOD_OTHER_PAYMENT_APP = 2;
    @VisibleForTesting
    public static final int SELECTED_METHOD_MAX = 3;

    // There should be no instance of PaymentRequestMetrics created.
    private PaymentRequestMetrics() {}

    /*
     * Records the metric that keeps track of what user information are requested by merchants to
     * complete a payment request.
     *
     * @param requestEmail    Whether the merchant requested an email address.
     * @param requestPhone    Whether the merchant requested a phone number.
     * @param requestShipping Whether the merchant requested a shipping address.
     * @param requestName     Whether the merchant requestes a name.
     */
    public static void recordRequestedInformationHistogram(boolean requestEmail,
            boolean requestPhone, boolean requestShipping, boolean requestName) {
        int requestInformation =
                (requestEmail ? REQUESTED_INFORMATION_EMAIL : 0)
                | (requestPhone ? REQUESTED_INFORMATION_PHONE : 0)
                | (requestShipping ? REQUESTED_INFORMATION_SHIPPING : 0)
                | (requestName ? REQUESTED_INFORMATION_NAME : 0);
        RecordHistogram.recordEnumeratedHistogram("PaymentRequest.RequestedInformation",
                requestInformation, REQUESTED_INFORMATION_MAX);
    }

    /*
     * Records the metric that keeps track of what payment method was used to complete a Payment
     * Request transaction.
     *
     * @param paymentMethod The payment method that was used to complete the current transaction.
     */
    public static void recordSelectedPaymentMethodHistogram(int paymentMethod) {
        assert paymentMethod < SELECTED_METHOD_MAX;
        RecordHistogram.recordEnumeratedHistogram("PaymentRequest.SelectedPaymentMethod",
                paymentMethod, SELECTED_METHOD_MAX);
    }
}
