// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressField;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressUiComponent;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.DropdownKeyValue;
import org.chromium.chrome.browser.widget.CompatibilityTextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the Java-ui for editing a Profile autofill entry.
 */
public class AutofillProfileEditor extends AutofillEditorBase {
    private boolean mNoCountryItemIsSelected;
    private LayoutInflater mInflater;
    private EditText mPhoneText;
    private CompatibilityTextInputLayout mPhoneLabel;
    private EditText mEmailText;
    private CompatibilityTextInputLayout mEmailLabel;
    private String mLanguageCodeString;
    private List<String> mCountryCodes;
    private int mCurrentCountryPos;
    private Spinner mCountriesDropdown;
    private ViewGroup mWidgetRoot;
    private CompatibilityTextInputLayout[] mAddressFields;
    private AutofillProfileBridge mAutofillProfileBridge;
    private boolean mUseSavedProfileLanguage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        mInflater = inflater;
        mAddressFields = new CompatibilityTextInputLayout[AddressField.NUM_FIELDS];

        mPhoneText = (EditText) v.findViewById(R.id.phone_number_edit);
        mPhoneLabel = (CompatibilityTextInputLayout) v.findViewById(R.id.phone_number_label);
        mEmailText = (EditText) v.findViewById(R.id.email_address_edit);
        mEmailLabel = (CompatibilityTextInputLayout) v.findViewById(R.id.email_address_label);
        mWidgetRoot = (ViewGroup) v.findViewById(R.id.autofill_profile_widget_root);
        mCountriesDropdown = (Spinner) v.findViewById(R.id.spinner);

        TextView countriesLabel = (TextView) v.findViewById(R.id.spinner_label);
        countriesLabel.setText(v.getContext().getString(R.string.autofill_profile_editor_country));

        mAutofillProfileBridge = new AutofillProfileBridge();

        populateCountriesDropdown();
        createAndPopulateEditFields();
        initializeButtons(v);

        return v;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.autofill_profile_editor;
    }

    @Override
    protected int getTitleResourceId(boolean isNewEntry) {
        return isNewEntry ? R.string.autofill_create_profile : R.string.autofill_edit_profile;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        boolean empty = mNoCountryItemIsSelected && TextUtils.isEmpty(s) && allFieldsEmpty();
        setSaveButtonEnabled(!empty);
    }

    private boolean allFieldsEmpty() {
        if (!TextUtils.isEmpty(mPhoneText.getText())
                || !TextUtils.isEmpty(mEmailText.getText())) {
            return false;
        }
        for (CompatibilityTextInputLayout field : mAddressFields) {
            if (field != null && !TextUtils.isEmpty(field.getEditText().getText())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position != mCurrentCountryPos) {
            mCurrentCountryPos = position;
            mUseSavedProfileLanguage = false;
            // If all fields are empty (e.g. the user just entered the form and the first thing
            // they did was select a country), focus on the first form element. Otherwise, don't.
            resetFormFields(position, allFieldsEmpty());
            mNoCountryItemIsSelected = false;
            setSaveButtonEnabled(true);
        }
    }

    private void populateCountriesDropdown() {
        List<DropdownKeyValue> countries = AutofillProfileBridge.getSupportedCountries();
        mCountryCodes = new ArrayList<String>();

        for (DropdownKeyValue country : countries) {
            mCountryCodes.add(country.getKey());
        }

        ArrayAdapter<DropdownKeyValue> countriesAdapter = new ArrayAdapter<DropdownKeyValue>(
                getActivity(), android.R.layout.simple_spinner_item, countries);
        countriesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCountriesDropdown.setAdapter(countriesAdapter);
    }

    private void createAndPopulateEditFields() {
        AutofillProfile profile = PersonalDataManager.getInstance().getProfile(mGUID);

        if (profile != null) {
            if (!TextUtils.isEmpty(profile.getPhoneNumber())) {
                mPhoneLabel.getEditText().setText(profile.getPhoneNumber());
            }

            if (!TextUtils.isEmpty(profile.getEmailAddress())) {
                mEmailLabel.getEditText().setText(profile.getEmailAddress());
            }

            mLanguageCodeString = profile.getLanguageCode();
            mUseSavedProfileLanguage = true;

            mCurrentCountryPos = mCountryCodes.indexOf(profile.getCountryCode());
            if (mCurrentCountryPos == -1) {
                // Use the default country code if profile code is invalid.
                mCurrentCountryPos = mCountryCodes.indexOf(
                        AutofillProfileBridge.getDefaultCountryCode());
                if (mCurrentCountryPos == -1) {
                    // Use the first item in country spinner if the default country code is
                    // invalid.
                    mCurrentCountryPos = 0;
                }
            }

            resetFormFields(mCurrentCountryPos, false);

            setFieldText(AddressField.ADMIN_AREA, profile.getRegion());
            setFieldText(AddressField.LOCALITY, profile.getLocality());
            setFieldText(AddressField.DEPENDENT_LOCALITY, profile.getDependentLocality());
            setFieldText(AddressField.SORTING_CODE, profile.getSortingCode());
            setFieldText(AddressField.POSTAL_CODE, profile.getPostalCode());
            setFieldText(AddressField.STREET_ADDRESS, profile.getStreetAddress());
            setFieldText(AddressField.ORGANIZATION, profile.getCompanyName());
            setFieldText(AddressField.RECIPIENT, profile.getFullName());
        } else {
            mCurrentCountryPos = mCountryCodes.indexOf(
                    AutofillProfileBridge.getDefaultCountryCode());
            if (mCurrentCountryPos == -1) {
                // Use the first item in country spinner if the default country code is
                // invalid.
                mCurrentCountryPos = 0;
            }
            resetFormFields(mCurrentCountryPos, true);
        }

        mCountriesDropdown.setSelection(mCurrentCountryPos);
    }

    private void resetFormFields(int countryCodeIndex, boolean autoFocusFirstField) {
        // Save field text so we can restore it after updating the fields for the current country,
        // and reset mAddressFields.
        String[] fieldText = new String[mAddressFields.length];
        for (int i = 0; i < mAddressFields.length; i++) {
            if (mAddressFields[i] != null) {
                fieldText[i] = mAddressFields[i].getEditText().getText().toString();
                mAddressFields[i] = null;
            }
        }

        // Remove all address form fields.
        mWidgetRoot.removeAllViews();

        // Get address fields for the selected country.
        List<AddressUiComponent> fields = mAutofillProfileBridge.getAddressUiComponents(
                mCountryCodes.get(countryCodeIndex),
                mLanguageCodeString);
        if (!mUseSavedProfileLanguage) {
            mLanguageCodeString = mAutofillProfileBridge.getCurrentBestLanguageCode();
        }

        // Create form fields and focus the first field if autoFocusFirstField is true.
        boolean firstField = true;
        for (AddressUiComponent field : fields) {
            CompatibilityTextInputLayout fieldFloatLabel =
                    (CompatibilityTextInputLayout) mInflater.inflate(
                            R.layout.preference_address_float_label_layout, mWidgetRoot, false);
            fieldFloatLabel.setHint(field.label);

            EditText fieldEditText = fieldFloatLabel.getEditText();
            fieldEditText.addTextChangedListener(this);
            if (field.id == AddressField.STREET_ADDRESS) {
                fieldEditText.setSingleLine(false);
            }

            mAddressFields[field.id] = fieldFloatLabel;
            mWidgetRoot.addView(fieldFloatLabel);

            if (firstField && autoFocusFirstField) {
                fieldEditText.requestFocus();
                firstField = false;
            }
        }

        // Add back saved field text.
        for (int i = 0; i < mAddressFields.length; i++) {
            if (mAddressFields[i] != null && fieldText[i] != null
                    && !TextUtils.isEmpty(fieldText[i])) {
                mAddressFields[i].getEditText().setText(fieldText[i]);
            }
        }
    }

    // Read edited data; save in the associated Chrome profile.
    // Ignore empty fields.
    @Override
    protected void saveEntry() {
        AutofillProfile profile = new PersonalDataManager.AutofillProfile(mGUID,
                AutofillPreferences.SETTINGS_ORIGIN, true /* isLocal */,
                getFieldText(AddressField.RECIPIENT), getFieldText(AddressField.ORGANIZATION),
                getFieldText(AddressField.STREET_ADDRESS), getFieldText(AddressField.ADMIN_AREA),
                getFieldText(AddressField.LOCALITY), getFieldText(AddressField.DEPENDENT_LOCALITY),
                getFieldText(AddressField.POSTAL_CODE), getFieldText(AddressField.SORTING_CODE),
                mCountryCodes.get(mCurrentCountryPos), mPhoneText.getText().toString(),
                mEmailText.getText().toString(), mLanguageCodeString);
        PersonalDataManager.getInstance().setProfile(profile);
    }

    private String getFieldText(int fieldId) {
        if (mAddressFields[fieldId] != null) {
            return mAddressFields[fieldId].getEditText().getText().toString();
        }
        return null;
    }

    private void setFieldText(int fieldId, String text) {
        if (mAddressFields[fieldId] != null && !TextUtils.isEmpty(text)) {
            mAddressFields[fieldId].getEditText().setText(text);
        }
    }

    @Override
    protected void deleteEntry() {
        if (mGUID != null) {
            PersonalDataManager.getInstance().deleteProfile(mGUID);
        }
    }

    @Override
    protected void initializeButtons(View v) {
        super.initializeButtons(v);

        // Listen for changes to inputs. Enable the save button after something has changed.
        mPhoneText.addTextChangedListener(this);
        mEmailText.addTextChangedListener(this);
        mCountriesDropdown.setOnItemSelectedListener(this);
        mNoCountryItemIsSelected = true;
    }

    private void setSaveButtonEnabled(boolean enabled) {
        if (getView() != null) {
            Button button = (Button) getView().findViewById(R.id.button_primary);
            button.setEnabled(enabled);
        }
    }
}
