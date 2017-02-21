// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;
import android.text.TextUtils;

import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.content_public.browser.WebContents;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides access to locally stored user credit cards.
 */
public class AutofillPaymentApp implements PaymentApp {
    private final WebContents mWebContents;

    /**
     * Builds a payment app backed by autofill cards.
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     */
    public AutofillPaymentApp(WebContents webContents) {
        mWebContents = webContents;
    }

    @Override
    public void getInstruments(JSONObject unusedDetails, final InstrumentsCallback callback) {
        PersonalDataManager pdm = PersonalDataManager.getInstance();
        List<CreditCard> cards = pdm.getCreditCardsToSuggest();
        final List<PaymentInstrument> instruments = new ArrayList<>(cards.size());

        for (int i = 0; i < cards.size(); i++) {
            CreditCard card = cards.get(i);
            AutofillProfile billingAddress = TextUtils.isEmpty(card.getBillingAddressId())
                    ? null : pdm.getProfile(card.getBillingAddressId());
            instruments.add(new AutofillPaymentInstrument(mWebContents, card, billingAddress));
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                callback.onInstrumentsReady(AutofillPaymentApp.this, instruments);
            }
        });
    }

    @Override
    public Set<String> getSupportedMethodNames() {
        // https://w3c.github.io/webpayments-methods-card/#method-id
        // The spec also includes more detailed card types, e.g., "visa/credit" and "visa/debit".
        // Autofill does not distinguish between these types of cards, so they are not in the list
        // of supported method names.
        Set<String> methods = new HashSet<>();

        methods.add("visa");
        methods.add("mastercard");
        methods.add("amex");
        methods.add("discover");
        methods.add("diners");
        methods.add("jcb");
        methods.add("unionpay");

        // The spec does not include "generic" card types. That's the type of card for which
        // Chrome cannot determine the type.
        methods.add("generic");

        return methods;
    }

    @Override
    public String getIdentifier() {
        return "Chrome_Autofill_Payment_App";
    }
}
