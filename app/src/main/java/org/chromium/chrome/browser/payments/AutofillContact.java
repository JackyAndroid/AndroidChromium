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
    @Nullable private String mPayerName;
    @Nullable private String mPayerPhone;
    @Nullable private String mPayerEmail;

    /**
     * Builds contact details.
     *
     * @param profile    The autofill profile where this contact data lives.
     * @param name       The payer name. If not empty, this will be the primary label.
     * @param phone      The phone number. If name is empty, this will be the primary label.
     * @param email      The email address. If name and phone are empty, this will be the primary
     *                   label.
     * @param isComplete Whether the data in this contact can be sent to the merchant as-is. If
     *                   false, user needs to add more information here.
     */
    public AutofillContact(AutofillProfile profile, @Nullable String name,
            @Nullable String phone, @Nullable String email, boolean isComplete) {
        super(profile.getGUID(), null, null, null, null);
        mProfile = profile;
        mIsComplete = isComplete;
        mIsEditable = true;
        setContactInfo(profile.getGUID(), name, phone, email);
    }

    /** @return Payer name. Null if the merchant did not request it or data is incomplete. */
    @Nullable public String getPayerName() {
        return mPayerName;
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
     * Updates the profile guid, payer name, email address, and phone number and marks this
     * information "complete." Called after the user has edited this contact information.
     * Update the identifier, label, sublabel, and tertiarylabel.
     *
     * @param guid  The new identifier to use. Should not be null or empty.
     * @param name  The new payer name to use. If not empty, this will be the primary label.
     * @param phone The new phone number to use. If name is empty, this will be the primary label.
     * @param email The new email address to use. If email and phone are empty, this will be the
     *              primary label.
     */
    public void completeContact(String guid, @Nullable String name,
            @Nullable String phone, @Nullable String email) {
        mIsComplete = true;
        setContactInfo(guid, name, phone, email);
    }

    private void setContactInfo(String guid, @Nullable String name,
            @Nullable String phone, @Nullable String email) {
        mPayerName = TextUtils.isEmpty(name) ? null : name;
        mPayerPhone = TextUtils.isEmpty(phone) ? null : phone;
        mPayerEmail = TextUtils.isEmpty(email) ? null : email;

        if (mPayerName == null) {
            updateIdentifierAndLabels(guid, mPayerPhone == null ? mPayerEmail : mPayerPhone,
                    mPayerPhone == null ? null : mPayerEmail);
        } else {
            updateIdentifierAndLabels(guid, mPayerName,
                    mPayerPhone == null ? mPayerEmail : mPayerPhone,
                    mPayerPhone == null ? null : mPayerEmail);
        }
    }
}
