// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.graphics.drawable.Drawable;

import org.chromium.chrome.browser.payments.ui.PaymentOption;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.List;

/**
 * The base class for a single payment instrument, e.g., a credit card.
 */
public abstract class PaymentInstrument extends PaymentOption {
    /**
     * The interface for the requester of instrument details.
     */
    public interface InstrumentDetailsCallback {
        /**
         * Called by the credit card payment instrument when CVC has been unmasked, but billing
         * address has not been normalized yet.
         */
        void loadingInstrumentDetails();

        /**
         * Called after retrieving instrument details.
         *
         * @param methodName         Method name. For example, "visa".
         * @param stringifiedDetails JSON-serialized object. For example, {"card": "123"}.
         */
        void onInstrumentDetailsReady(String methodName, String stringifiedDetails);

        /**
         * Called if unable to retrieve instrument details.
         */
        void onInstrumentDetailsError();
    }

    protected PaymentInstrument(String id, String label, String sublabel, Drawable icon) {
        super(id, label, sublabel, icon);
    }

    /**
     * Returns a method name for this instrument, e.g., "visa" or "mastercard" in basic card
     * payments: https://w3c.github.io/webpayments-methods-card/#method-id
     *
     * @return The method names for this instrument.
     */
    public abstract String getInstrumentMethodName();

    /**
     * Asynchronously retrieves the instrument details and invokes the callback with the result.
     *
     * @param merchantName The name of the merchant.
     * @param origin       The origin of this merchant.
     * @param total        The total amount.
     * @param items        The shopping cart items.
     * @param details      The payment-method specific data, e.g., whether the app should be invoked
     *                     in test or production key, a merchant identifier, or a public key.
     * @param callback     The object that will receive the instrument details.
     */
    public abstract void getInstrumentDetails(String merchantName, String origin, PaymentItem total,
            List<PaymentItem> cart, PaymentMethodData details, InstrumentDetailsCallback callback);

    /**
     * Cleans up any resources held by the payment instrument. For example, closes server
     * connections.
     */
    public abstract void dismissInstrument();
}
