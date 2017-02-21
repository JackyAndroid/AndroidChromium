// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.text.TextUtils;

import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.payments.ui.PaymentOption;

import javax.annotation.Nullable;

/**
 * The locally stored contact details.
 */
public class AutofillContact extends PaymentOption {
    private final AutofillProfile mProfile;
    @Nullable private String mPayerPhone;
    @Nullable private String mPayerEmail;

    /**
     * Builds contact details.
     *
     * @param profile    The autofill profile where this contact data lives.
     * @param phone      The phone number. If not empty, this will be the primary label.
     * @param email      The email address. If phone is empty, this will be the primary label.
     * @param isComplete Whether the data in this contact can be sent to the merchant as-is. If
     *                   false, user needs to add more information here.
     */
    public AutofillContact(AutofillProfile profile, @Nullable String phone, @Nullable String email,
            boolean isComplete) {
        super(profile.getGUID(), null, null, PaymentOption.NO_ICON);
        mProfile = profile;
        mIsComplete = isComplete;
        setGuidPhoneEmail(profile.getGUID(), phone, email);
    }

    /** @return Email address. Null if the merchant did not request it or data is incomplete. */
    @Nullable public String getPayerEmail() {
        return mPayerEmail;
    }

    /** @return Phone number. Null if the merchant did not request it or data is incomplete. */
    @Nullable public String getPayerPhone() {
        return mPayerPhone;
    }

    /** @return The autofill profile where this contact data lives. */
    public AutofillProfile getProfile() {
        return mProfile;
    }

    /**
     * Updates the profile guid, email address, and phone number and marks this information
     * "complete." Called after the user has edited this contact information. Updates the
     * identifier, label, and sublabel.
     *
     * @param guid  The new identifier to use. Should not be null or empty.
     * @param phone The new phone number to use. If not empty, this will be the primary label.
     * @param email The new email address to use. If phone is empty, this will be the primary label.
     */
    public void completeContact(String guid, @Nullable String phone, @Nullable String email) {
        mIsComplete = true;
        setGuidPhoneEmail(guid, phone, email);
    }

    private void setGuidPhoneEmail(String guid, @Nullable String phone, @Nullable String email) {
        mPayerPhone = TextUtils.isEmpty(phone) ? null : phone;
        mPayerEmail = TextUtils.isEmpty(email) ? null : email;
        updateIdentifierAndLabels(guid, mPayerPhone == null ? mPayerEmail : mPayerPhone,
                mPayerPhone == null ? null : mPayerEmail);
    }
}
