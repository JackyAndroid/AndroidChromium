// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.text.TextUtils;
import android.util.JsonWriter;

import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.chrome.browser.autofill.PersonalDataManager.FullCardRequestDelegate;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentItem;

import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The locally stored credit card payment instrument.
 */
public class AutofillPaymentInstrument
        extends PaymentInstrument implements FullCardRequestDelegate {
    private final WebContents mWebContents;
    private CreditCard mCard;
    private boolean mIsComplete;
    @Nullable private AutofillProfile mBillingAddress;
    @Nullable private DetailsCallback mCallback;

    /**
     * Builds a payment instrument for the given credit card.
     *
     * @param webContents    The web contents where PaymentRequest was invoked.
     * @param card           The autofill card that can be used for payment.
     * @param billingAddress The billing address for the card.
     */
    public AutofillPaymentInstrument(
            WebContents webContents, CreditCard card, @Nullable AutofillProfile billingAddress) {
        super(card.getGUID(), card.getObfuscatedNumber(), card.getName(),
                card.getIssuerIconDrawableId());
        mWebContents = webContents;
        mCard = card;
        mIsComplete = false;
        mBillingAddress = billingAddress;
    }

    @Override
    public String getMethodName() {
        return mCard.getBasicCardPaymentType();
    }

    @Override
    public void getDetails(String unusedMerchantName, String unusedOrigin, PaymentItem unusedTotal,
            List<PaymentItem> unusedCart, JSONObject unusedDetails, DetailsCallback callback) {
        assert mIsComplete;
        assert mCallback == null;
        mCallback = callback;
        PersonalDataManager.getInstance().getFullCard(mWebContents, mCard, this);
    }

    @Override
    public void onFullCardDetails(CreditCard card, String cvc) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter json = new JsonWriter(stringWriter);
        try {
            json.beginObject();

            json.name("cardholderName").value(card.getName());
            json.name("cardNumber").value(card.getNumber());
            json.name("expiryMonth").value(card.getMonth());
            json.name("expiryYear").value(card.getYear());
            json.name("cardSecurityCode").value(cvc);

            json.name("billingAddress").beginObject();

            json.name("country").value(ensureNotNull(mBillingAddress.getCountryCode()));
            json.name("region").value(ensureNotNull(mBillingAddress.getRegion()));
            json.name("city").value(ensureNotNull(mBillingAddress.getLocality()));
            json.name("dependentLocality")
                    .value(ensureNotNull(mBillingAddress.getDependentLocality()));

            json.name("addressLine").beginArray();
            String multipleLines = ensureNotNull(mBillingAddress.getStreetAddress());
            if (!TextUtils.isEmpty(multipleLines)) {
                String[] lines = multipleLines.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    json.value(lines[i]);
                }
            }
            json.endArray();

            json.name("postalCode").value(ensureNotNull(mBillingAddress.getPostalCode()));
            json.name("sortingCode").value(ensureNotNull(mBillingAddress.getSortingCode()));
            json.name("languageCode").value(ensureNotNull(mBillingAddress.getLanguageCode()));
            json.name("organization").value(ensureNotNull(mBillingAddress.getCompanyName()));
            json.name("recipient").value(ensureNotNull(mBillingAddress.getFullName()));
            json.name("phone").value(ensureNotNull(mBillingAddress.getPhoneNumber()));

            json.endObject();

            json.endObject();
        } catch (IOException e) {
            onFullCardError();
            return;
        }

        mCallback.onInstrumentDetailsReady(card.getBasicCardPaymentType(), stringWriter.toString());
    }

    private static String ensureNotNull(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onFullCardError() {
        mCallback.onInstrumentDetailsError();
        mCallback = null;
    }

    @Override
    public void dismiss() {}

    /** @return Whether the card is complete and ready to be sent to the merchant as-is. */
    public boolean isComplete() {
        return mIsComplete;
    }

    /** Marks this card complete and ready to be sent to the merchant without editing first. */
    public void setIsComplete() {
        mIsComplete = true;
    }

    /**
     * Updates the instrument and marks it "complete." Called after the user has edited this
     * instrument.
     *
     * @param card           The new credit card to use. The GUID should not change.
     * @param billingAddress The billing address for the card. The GUID should match the billing
     *                       address ID of the new card to use.
     */
    public void completeInstrument(CreditCard card, AutofillProfile billingAddress) {
        assert card != null;
        assert billingAddress != null;
        assert card.getBillingAddressId() != null;
        assert card.getBillingAddressId().equals(billingAddress.getGUID());

        mCard = card;
        mBillingAddress = billingAddress;
        mIsComplete = true;
        updateIdentifierLabelsAndIcon(card.getGUID(), card.getObfuscatedNumber(), card.getName(),
                null, card.getIssuerIconDrawableId());
    }

    /** @return The credit card represented by this payment instrument. */
    public CreditCard getCard() {
        return mCard;
    }
}
