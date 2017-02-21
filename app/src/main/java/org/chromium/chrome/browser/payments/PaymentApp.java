// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.json.JSONObject;

import java.util.List;
import java.util.Set;

/**
 * The interface that a payment app implements. A payment app can get its data from Chrome autofill,
 * Android Pay, or third party apps.
 */
public interface PaymentApp {
    /**
     * The interface for the requester of instruments.
     */
    public interface InstrumentsCallback {
        /**
         * Called by this app to provide a list of instruments asynchronously.
         *
         * @param app         The calling app.
         * @param instruments The instruments from this app.
         */
        void onInstrumentsReady(PaymentApp app, List<PaymentInstrument> instruments);
    }

    /**
     * Provides a list of all payment instruments in this app. For example, this can be all credit
     * cards for the current profile. Can return null or empty list, e.g., if user has no locally
     * stored credit cards.
     *
     * @param details  The payment-method specific data, e.g., whether the app should be invoked in
     *                 test or production mode, merchant identifier, or a public key.
     * @param callback The object that will receive the list of instruments.
     */
    void getInstruments(JSONObject details, InstrumentsCallback callback);

    /**
     * Returns a list of all payment method names that this app supports. For example, ["visa",
     * "mastercard"] in basic card payments. Should return a list of at least one method name.
     * https://w3c.github.io/browser-payment-api/specs/basic-card-payment.html#method-id
     *
     * @return The list of all payment method names that this app supports.
     */
    Set<String> getSupportedMethodNames();

    /**
     * Returns the identifier for this payment app to be saved in user preferences. For example,
     * this can be "autofill", "https://android.com/pay", or "com.example.app.ExamplePaymentApp".
     *
     * @return The identifier for this payment app.
     */
    String getIdentifier();
}
