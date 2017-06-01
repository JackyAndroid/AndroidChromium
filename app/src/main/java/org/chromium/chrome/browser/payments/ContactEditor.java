// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Patterns;

import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel.EditorFieldValidator;
import org.chromium.chrome.browser.payments.ui.EditorModel;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Contact information editor.
 */
public class ContactEditor extends EditorBase<AutofillContact> {
    private final boolean mRequestPayerName;
    private final boolean mRequestPayerPhone;
    private final boolean mRequestPayerEmail;
    private final Set<CharSequence> mPayerNames;
    private final Set<CharSequence> mPhoneNumbers;
    private final Set<CharSequence> mEmailAddresses;
    @Nullable private EditorFieldValidator mPhoneValidator;
    @Nullable private EditorFieldValidator mEmailValidator;

    /**
     * Builds a contact information editor.
     *
     * @param requestPayerName Whether to request the user's name.
     * @param requestPayerPhone Whether to request the user's phone number.
     * @param requestPayerEmail Whether to request the user's email address.
     */
    public ContactEditor(boolean requestPayerName,
            boolean requestPayerPhone, boolean requestPayerEmail) {
        assert requestPayerName || requestPayerPhone || requestPayerEmail;
        mRequestPayerName = requestPayerName;
        mRequestPayerPhone = requestPayerPhone;
        mRequestPayerEmail = requestPayerEmail;
        mPayerNames = new HashSet<>();
        mPhoneNumbers = new HashSet<>();
        mEmailAddresses = new HashSet<>();
    }

    /**
     * Returns whether the following contact information can be sent to the merchant as-is without
     * editing first.
     *
     * @param name  The payer name to check.
     * @param phone The phone number to check.
     * @param email The email address to check.
     * @return Whether the contact information is complete.
     */
    public boolean isContactInformationComplete(
            @Nullable String name, @Nullable String phone, @Nullable String email) {
        return (!mRequestPayerName || !TextUtils.isEmpty(name))
                && (!mRequestPayerPhone || getPhoneValidator().isValid(phone))
                && (!mRequestPayerEmail || getEmailValidator().isValid(email));
    }

    /**
     * Adds the given payer name to the autocomplete set, if it's valid.
     *
     * @param payerName The payer name to possibly add.
     */
    public void addPayerNameIfValid(@Nullable CharSequence payerName) {
        if (!TextUtils.isEmpty(payerName)) mPayerNames.add(payerName);
    }

    /**
     * Adds the given phone number to the autocomplete set, if it's valid.
     *
     * @param phoneNumber The phone number to possibly add.
     */
    public void addPhoneNumberIfValid(@Nullable CharSequence phoneNumber) {
        if (getPhoneValidator().isValid(phoneNumber)) mPhoneNumbers.add(phoneNumber);
    }

    /**
     * Adds the given email address to the autocomplete set, if it's valid.
     *
     * @param emailAddress The email address to possibly add.
     */
    public void addEmailAddressIfValid(@Nullable CharSequence emailAddress) {
        if (getEmailValidator().isValid(emailAddress)) mEmailAddresses.add(emailAddress);
    }

    @Override
    public void edit(@Nullable AutofillContact toEdit, final Callback<AutofillContact> callback) {
        super.edit(toEdit, callback);

        final AutofillContact contact = toEdit == null
                ? new AutofillContact(new AutofillProfile(), null, null, null, false) : toEdit;

        final EditorFieldModel nameField = mRequestPayerName
                ? EditorFieldModel.createTextInput(EditorFieldModel.INPUT_TYPE_HINT_PERSON_NAME,
                          mContext.getString(R.string.payments_name_field_in_contact_details),
                          mPayerNames, null, null,
                          mContext.getString(R.string.payments_field_required_validation_message),
                          null, contact.getPayerName())
                : null;

        final EditorFieldModel phoneField = mRequestPayerPhone
                ? EditorFieldModel.createTextInput(EditorFieldModel.INPUT_TYPE_HINT_PHONE,
                          mContext.getString(R.string.autofill_profile_editor_phone_number),
                          mPhoneNumbers, getPhoneValidator(), null,
                          mContext.getString(R.string.payments_field_required_validation_message),
                          mContext.getString(R.string.payments_phone_invalid_validation_message),
                          contact.getPayerPhone())
                : null;

        final EditorFieldModel emailField = mRequestPayerEmail
                ? EditorFieldModel.createTextInput(EditorFieldModel.INPUT_TYPE_HINT_EMAIL,
                          mContext.getString(R.string.autofill_profile_editor_email_address),
                          mEmailAddresses, getEmailValidator(), null,
                          mContext.getString(R.string.payments_field_required_validation_message),
                          mContext.getString(R.string.payments_email_invalid_validation_message),
                          contact.getPayerEmail())
                : null;

        EditorModel editor = new EditorModel(
                mContext.getString(toEdit == null ? R.string.payments_add_contact_details_label
                                                  : R.string.payments_edit_contact_details_label));
        if (nameField != null) editor.addField(nameField);
        if (phoneField != null) editor.addField(phoneField);
        if (emailField != null) editor.addField(emailField);

        editor.setCancelCallback(new Runnable() {
            @Override
            public void run() {
                callback.onResult(null);
            }
        });

        editor.setDoneCallback(new Runnable() {
            @Override
            public void run() {
                String name = null;
                String phone = null;
                String email = null;
                AutofillProfile profile = contact.getProfile();

                if (nameField != null) {
                    name = nameField.getValue().toString();
                    profile.setFullName(name);
                }

                if (phoneField != null) {
                    phone = phoneField.getValue().toString();
                    profile.setPhoneNumber(phone);
                }

                if (emailField != null) {
                    email = emailField.getValue().toString();
                    profile.setEmailAddress(email);
                }

                profile.setGUID(PersonalDataManager.getInstance().setProfileToLocal(profile));
                profile.setIsLocal(true);
                contact.completeContact(profile.getGUID(), name, phone, email);
                callback.onResult(contact);
            }
        });

        mEditorView.show(editor);
    }

    private EditorFieldValidator getPhoneValidator() {
        if (mPhoneValidator == null) {
            mPhoneValidator = new EditorFieldValidator() {
                @Override
                public boolean isValid(@Nullable CharSequence value) {
                    return value != null
                            && PhoneNumberUtils.isGlobalPhoneNumber(
                                       PhoneNumberUtils.stripSeparators(value.toString()));
                }
            };
        }
        return mPhoneValidator;
    }

    private EditorFieldValidator getEmailValidator() {
        if (mEmailValidator == null) {
            mEmailValidator = new EditorFieldValidator() {
                @Override
                public boolean isValid(@Nullable CharSequence value) {
                    return value != null && Patterns.EMAIL_ADDRESS.matcher(value).matches();
                }
            };
        }
        return mEmailValidator;
    }
}
