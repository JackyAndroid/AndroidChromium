// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel.EditorFieldValidator;
import org.chromium.chrome.browser.payments.ui.EditorModel;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressField;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressUiComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * An address editor. Can be used for either shipping or billing address editing.
 */
public class AddressEditor extends EditorBase<AutofillAddress> {
    private final Handler mHandler = new Handler();
    private final Map<Integer, EditorFieldModel> mAddressFields = new HashMap<>();
    private final Set<CharSequence> mPhoneNumbers = new HashSet<>();
    @Nullable private AutofillProfileBridge mAutofillProfileBridge;
    @Nullable private EditorFieldModel mCountryField;
    @Nullable private EditorFieldModel mPhoneField;
    @Nullable private EditorFieldValidator mPhoneValidator;
    @Nullable private List<AddressUiComponent> mAddressUiComponents;

    /**
     * Returns whether the given profile can be sent to the merchant as-is without editing first. If
     * the country code is not set or invalid, but all fields for the default locale's country code
     * are present, then the profile is deemed "complete." AutoflllAddress.toPaymentAddress() will
     * use the default locale to fill in a blank country code before sending the address to the
     * renderer.
     *
     * @param profile The profile to check.
     * @return Whether the profile is complete.
     */
    public boolean isProfileComplete(@Nullable AutofillProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.getFullName())
                || !getPhoneValidator().isValid(profile.getPhoneNumber())) {
            return false;
        }

        List<Integer> requiredFields = AutofillProfileBridge.getRequiredAddressFields(
                AutofillAddress.getCountryCode(profile));
        for (int i = 0; i < requiredFields.size(); i++) {
            if (TextUtils.isEmpty(getProfileField(profile, requiredFields.get(i)))) return false;
        }

        return true;
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
     * Builds and shows an editor model with the following fields.
     *
     * [ country dropdown   ] <----- country dropdown is always present.
     * [ an address field   ] \
     * [ an address field   ]  \
     *         ...               <-- field order, presence, required, and labels depend on country.
     * [ an address field   ]  /
     * [ an address field   ] /
     * [ phone number field ] <----- phone is always present and required.
     */
    @Override
    public void edit(@Nullable AutofillAddress toEdit, final Callback<AutofillAddress> callback) {
        super.edit(toEdit, callback);

        if (mAutofillProfileBridge == null) mAutofillProfileBridge = new AutofillProfileBridge();

        // If |toEdit| is null, we're creating a new autofill profile with the country code of the
        // default locale on this device.
        boolean isNewAddress = toEdit == null;

        // Ensure that |address| and |profile| are always not null.
        final AutofillAddress address = isNewAddress
                ? new AutofillAddress(new AutofillProfile(), false)
                : toEdit;
        final AutofillProfile profile = address.getProfile();

        // The title of the editor depends on whether we're adding a new address or editing an
        // existing address.
        final EditorModel editor = new EditorModel(mContext.getString(isNewAddress
                ? R.string.autofill_create_profile
                : R.string.autofill_edit_profile));

        // The country dropdown is always present on the editor.
        if (mCountryField == null) {
            mCountryField = EditorFieldModel.createDropdown(
                    mContext.getString(R.string.autofill_profile_editor_country),
                    AutofillProfileBridge.getSupportedCountries());
        }

        // Changing the country will update which fields are in the model. The actual fields are not
        // discarded, so their contents are preserved.
        mCountryField.setDropdownCallback(new Callback<Pair<String, Runnable>>() {
            @Override
            public void onResult(Pair<String, Runnable> eventData) {
                editor.removeAllFields();
                editor.addField(mCountryField);
                addAddressTextFieldsToEditor(editor, eventData.first,
                        Locale.getDefault().getLanguage());
                editor.addField(mPhoneField);

                // Notify EditorView that the fields in the model have changed. EditorView should
                // re-read the model and update the UI accordingly.
                mHandler.post(eventData.second);
            }
        });

        // Country dropdown is cached, so the selected item needs to be updated for the new profile
        // that's being edited. This will not fire the dropdown callback.
        mCountryField.setValue(AutofillAddress.getCountryCode(profile));
        editor.addField(mCountryField);

        // There's a finite number of fields for address editing. Changing the country will re-order
        // and relabel the fields. The meaning of each field remains the same.
        if (mAddressFields.isEmpty()) {
            // City, dependent locality, and organization don't have any special formatting hints.
            mAddressFields.put(AddressField.LOCALITY, EditorFieldModel.createTextInput());
            mAddressFields.put(AddressField.DEPENDENT_LOCALITY, EditorFieldModel.createTextInput());
            mAddressFields.put(AddressField.ORGANIZATION, EditorFieldModel.createTextInput());

            // State should be formatted in all capitals.
            mAddressFields.put(AddressField.ADMIN_AREA, EditorFieldModel.createTextInput(
                        EditorFieldModel.INPUT_TYPE_HINT_REGION));

            // Sorting code and postal code (a.k.a. ZIP code) should show both letters and digits on
            // the keyboard, if possible.
            mAddressFields.put(AddressField.SORTING_CODE, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_ALPHA_NUMERIC));
            mAddressFields.put(AddressField.POSTAL_CODE, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_ALPHA_NUMERIC));

            // Street line field can contain \n to indicate line breaks.
            mAddressFields.put(AddressField.STREET_ADDRESS, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_STREET_LINES));

            // Android has special formatting rules for names.
            mAddressFields.put(AddressField.RECIPIENT, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_PERSON_NAME));
        }

        // Address fields are cached, so their values need to be updated for every new profile
        // that's being edited.
        for (Map.Entry<Integer, EditorFieldModel> entry : mAddressFields.entrySet()) {
            entry.getValue().setValue(getProfileField(profile, entry.getKey()));
        }

        // Both country code and language code dictate which fields should be added to the editor.
        // For example, "US" will not add dependent locality to the editor. A "JP" address will
        // start with a person's full name or a with a prefecture name, depending on whether the
        // language code is "ja-Latn" or "ja".
        addAddressTextFieldsToEditor(editor, profile.getCountryCode(), profile.getLanguageCode());

        // Phone number is present and required for all countries.
        if (mPhoneField == null) {
            mPhoneField = EditorFieldModel.createTextInput(EditorFieldModel.INPUT_TYPE_HINT_PHONE,
                    mContext.getString(R.string.autofill_profile_editor_phone_number),
                    mPhoneNumbers, getPhoneValidator(),
                    mContext.getString(R.string.payments_field_required_validation_message),
                    mContext.getString(R.string.payments_phone_invalid_validation_message), null);
        }

        // Phone number field is cached, so its value needs to be updated for every new profile
        // that's being edited.
        mPhoneField.setValue(profile.getPhoneNumber());
        editor.addField(mPhoneField);

        // If the user clicks [Cancel], send a null address back to the caller.
        editor.setCancelCallback(new Runnable() {
            @Override
            public void run() {
                callback.onResult(null);
            }
        });

        // If the user clicks [Done], save changes on disk, mark the address "complete," and send it
        // back to the caller.
        editor.setDoneCallback(new Runnable() {
            @Override
            public void run() {
                commitChanges(profile);
                address.completeAddress(profile);
                callback.onResult(address);
            }
        });

        mEditorView.show(editor);
    }

    /** Saves the edited profile on disk. */
    private void commitChanges(AutofillProfile profile) {
        // Country code and phone number are always required and are always collected from the
        // editor model.
        profile.setCountryCode(mCountryField.getValue().toString());
        profile.setPhoneNumber(mPhoneField.getValue().toString());

        // Autofill profile bridge normalizes the language code for the autofill profile.
        profile.setLanguageCode(mAutofillProfileBridge.getCurrentBestLanguageCode());

        // Collect data from all visible fields and store it in the autofill profile.
        Set<Integer> visibleFields = new HashSet<>();
        for (int i = 0; i < mAddressUiComponents.size(); i++) {
            AddressUiComponent component = mAddressUiComponents.get(i);
            visibleFields.add(component.id);
            if (component.id != AddressField.COUNTRY) {
                setProfileField(profile, component.id, mAddressFields.get(component.id).getValue());
            }
        }

        // Clear the fields that are hidden from the user interface, so
        // AutofillAddress.toPaymentAddress() will send them to the renderer as empty strings.
        for (Map.Entry<Integer, EditorFieldModel> entry : mAddressFields.entrySet()) {
            if (!visibleFields.contains(entry.getKey())) {
                setProfileField(profile, entry.getKey(), "");
            }
        }

        // Calculate the label for this profile. The label's format depends on the country and
        // language code for the profile.
        PersonalDataManager pdm = PersonalDataManager.getInstance();
        profile.setLabel(pdm.getAddressLabelForPaymentRequest(profile));

        // Save the edited autofill profile.
        profile.setGUID(pdm.setProfile(profile));
    }

    /** @return The given autofill profile field. */
    private static String getProfileField(AutofillProfile profile, int field) {
        assert profile != null;
        switch (field) {
            case AddressField.COUNTRY:
                return profile.getCountryCode();
            case AddressField.ADMIN_AREA:
                return profile.getRegion();
            case AddressField.LOCALITY:
                return profile.getLocality();
            case AddressField.DEPENDENT_LOCALITY:
                return profile.getDependentLocality();
            case AddressField.SORTING_CODE:
                return profile.getSortingCode();
            case AddressField.POSTAL_CODE:
                return profile.getPostalCode();
            case AddressField.STREET_ADDRESS:
                return profile.getStreetAddress();
            case AddressField.ORGANIZATION:
                return profile.getCompanyName();
            case AddressField.RECIPIENT:
                return profile.getFullName();
        }

        assert false;
        return null;
    }

    /** Writes the given value into the specified autofill profile field. */
    private static void setProfileField(
            AutofillProfile profile, int field, @Nullable CharSequence value) {
        assert profile != null;
        switch (field) {
            case AddressField.COUNTRY:
                profile.setCountryCode(ensureNotNull(value));
                return;
            case AddressField.ADMIN_AREA:
                profile.setRegion(ensureNotNull(value));
                return;
            case AddressField.LOCALITY:
                profile.setLocality(ensureNotNull(value));
                return;
            case AddressField.DEPENDENT_LOCALITY:
                profile.setDependentLocality(ensureNotNull(value));
                return;
            case AddressField.SORTING_CODE:
                profile.setSortingCode(ensureNotNull(value));
                return;
            case AddressField.POSTAL_CODE:
                profile.setPostalCode(ensureNotNull(value));
                return;
            case AddressField.STREET_ADDRESS:
                profile.setStreetAddress(ensureNotNull(value));
                return;
            case AddressField.ORGANIZATION:
                profile.setCompanyName(ensureNotNull(value));
                return;
            case AddressField.RECIPIENT:
                profile.setFullName(ensureNotNull(value));
                return;
        }

        assert false;
    }

    private static String ensureNotNull(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Adds text fields to the editor model based on the country and language code of the profile
     * that's being edited.
     */
    private void addAddressTextFieldsToEditor(
            EditorModel container, String countryCode, String languageCode) {
        mAddressUiComponents = mAutofillProfileBridge.getAddressUiComponents(countryCode,
                languageCode);

        for (int i = 0; i < mAddressUiComponents.size(); i++) {
            AddressUiComponent component = mAddressUiComponents.get(i);

            // The country field is a dropdown, so there's no need to add a text field for it.
            if (component.id == AddressField.COUNTRY) continue;

            EditorFieldModel field = mAddressFields.get(component.id);
            // Labels depend on country, e.g., state is called province in some countries. These are
            // already localized.
            field.setLabel(component.label);
            field.setIsFullLine(component.isFullLine);

            // Libaddressinput formats do not always require the full name (RECIPIENT), but
            // PaymentRequest does.
            if (component.isRequired || component.id == AddressField.RECIPIENT) {
                field.setRequiredErrorMessage(mContext.getString(
                        R.string.payments_field_required_validation_message));
            } else {
                field.setRequiredErrorMessage(null);
            }

            container.addField(field);
        }
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
}
