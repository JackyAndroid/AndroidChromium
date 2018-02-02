// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;

import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.NormalizedAddressRequestDelegate;
import org.chromium.chrome.browser.payments.ui.PaymentOption;
import org.chromium.payments.mojom.PaymentResponse;

/**
 * The helper class to create and prepare a PaymentResponse.
 */
public class PaymentResponseHelper implements NormalizedAddressRequestDelegate {
    /**
     * Observer to be notified when the payment response is completed.
     */
    public interface PaymentResponseRequesterDelegate {
        /*
         * Called when the payment response is ready to be sent to the merchant.
         *
         * @param response The payment response to send to the merchant.
         */
        void onPaymentResponseReady(PaymentResponse response);
    }

    private AutofillAddress mSelectedShippingAddress;
    private PaymentResponse mPaymentResponse;
    private PaymentResponseRequesterDelegate mDelegate;
    private boolean mIsWaitingForShippingNormalization;
    private boolean mIsWaitingForPaymentsDetails = true;

    /**
     * Builds a helper to contruct and fill a PaymentResponse.
     *
     * @param selectedShippingAddress The shipping address picked by the user.
     * @param selectedShippingOption  The shipping option picked by the user.
     * @param selectedContact         The contact info picked by the user.
     * @param delegate                The object that will recieve the completed PaymentResponse.
     */
    public PaymentResponseHelper(PaymentOption selectedShippingAddress,
            PaymentOption selectedShippingOption, PaymentOption selectedContact,
            PaymentResponseRequesterDelegate delegate) {
        mPaymentResponse = new PaymentResponse();

        mDelegate = delegate;

        // Set up the contact section of the response.
        if (selectedContact != null) {
            // Contacts are created in PaymentRequestImpl.init(). These should all be instances of
            // AutofillContact.
            assert selectedContact instanceof AutofillContact;
            mPaymentResponse.payerName = ((AutofillContact) selectedContact).getPayerName();
            mPaymentResponse.payerPhone = ((AutofillContact) selectedContact).getPayerPhone();
            mPaymentResponse.payerEmail = ((AutofillContact) selectedContact).getPayerEmail();
        }

        // Set up the shipping section of the response.
        if (selectedShippingOption != null && selectedShippingOption.getIdentifier() != null) {
            mPaymentResponse.shippingOption = selectedShippingOption.getIdentifier();
        }

        // Set up the shipping address section of the response.
        if (selectedShippingAddress != null) {
            // Shipping addresses are created in PaymentRequestImpl.init(). These should all be
            // instances of AutofillAddress.
            assert selectedShippingAddress instanceof AutofillAddress;
            mSelectedShippingAddress = (AutofillAddress) selectedShippingAddress;

            // Addresses to be sent to the merchant should always be complete.
            assert mSelectedShippingAddress.isComplete();

            // Record the use of the profile.
            PersonalDataManager.getInstance().recordAndLogProfileUse(
                    mSelectedShippingAddress.getProfile().getGUID());

            mPaymentResponse.shippingAddress = mSelectedShippingAddress.toPaymentAddress();

            // The shipping address needs to be normalized before sending the response to the
            // merchant.
            mIsWaitingForShippingNormalization = true;
            PersonalDataManager.getInstance().normalizeAddress(
                    mSelectedShippingAddress.getProfile().getGUID(),
                    AutofillAddress.getCountryCode(mSelectedShippingAddress.getProfile()), this);
        }
    }

    /**
     * Called when the intrument details have started loading. Starts a timeout to stop the shipping
     * address normalization if it takes too long.
     */
    public void onInstrumentsDetailsLoading() {
        if (mIsWaitingForShippingNormalization) {
            // If the normalization is not completed yet, start a timer to cancel it if it takes too
            // long.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    onAddressNormalized(null);
                }
            }, PersonalDataManager.getInstance().getNormalizationTimeoutMS());
        }
    }

    /**
     * Called after the payment instrument's details were received.
     *
     * @param methodName          The method name of the payment instrument.
     * @param stringifiedDetails  A string containing all the details of the payment instrument's
     *                            details.
     */
    public void onInstrumentDetailsReceived(String methodName, String stringifiedDetails) {
        mPaymentResponse.methodName = methodName;
        mPaymentResponse.stringifiedDetails = stringifiedDetails;

        mIsWaitingForPaymentsDetails = false;

        // Wait for the shipping address normalization before sending the response.
        if (!mIsWaitingForShippingNormalization) mDelegate.onPaymentResponseReady(mPaymentResponse);
    }

    @Override
    public void onAddressNormalized(AutofillProfile profile) {
        // Check if a normalization is still required.
        if (!mIsWaitingForShippingNormalization) return;
        mIsWaitingForShippingNormalization = false;

        if (profile != null) {
            // The normalization finished first: use the normalized address.
            mSelectedShippingAddress.completeAddress(profile);
            mPaymentResponse.shippingAddress = mSelectedShippingAddress.toPaymentAddress();
        }

        // Wait for the payment details before sending the response.
        if (!mIsWaitingForPaymentsDetails) mDelegate.onPaymentResponseReady(mPaymentResponse);
    }
}
