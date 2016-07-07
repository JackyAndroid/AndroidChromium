// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressField;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.Country;
import org.chromium.chrome.browser.widget.FloatLabelLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the Java-ui for editing a Profile autofill entry.
 */
public class AutofillProfileEditor extends Fragment implements TextWatcher,
        OnItemSelectedListener {
    // GUID of the profile we are editing.
    // May be the empty string if creating a new profile.
    private String mGUID;

    private boolean mNoCountryItemIsSelected;
    private LayoutInflater mInflater;
    private EditText mPhoneText;
    private FloatLabelLayout mPhoneLabel;
    private EditText mEmailText;
    private FloatLabelLayout mEmailLabel;
    private String mLanguageCodeString;
    private List<String> mCountryCodes;
    private int mCurrentCountryPos;
    private Spinner mCountriesSpinner;
    private ViewGroup mWidgetRoot;
    private FloatLabelLayout[] mAddressFields;
    private AutofillProfileBridge mAutofillProfileBridge;
    private boolean mUseSavedProfileLanguage;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We know which profile to edit based on the GUID stuffed in
        // our extras by AutofillPreferences.
        Bundle extras = getArguments();
        if (extras != null) {
            mGUID = extras.getString(AutofillPreferences.AUTOFILL_GUID);
        }
        if (mGUID == null) {
            mGUID = "";
            getActivity().setTitle(R.string.autofill_create_profile);
        } else {
            getActivity().setTitle(R.string.autofill_edit_profile);
        }

        mInflater = inflater;
        mAddressFields = new FloatLabelLayout[AddressField.NUM_FIELDS];
        View v = mInflater.inflate(R.layout.autofill_profile_editor, container, false);

        mPhoneText = (EditText) v.findViewById(R.id.phone_number_edit);
        mPhoneLabel = (FloatLabelLayout) v.findViewById(R.id.phone_number_label);
        mEmailText = (EditText) v.findViewById(R.id.email_address_edit);
        mEmailLabel = (FloatLabelLayout) v.findViewById(R.id.email_address_label);
        mWidgetRoot = (ViewGroup) v.findViewById(R.id.autofill_profile_widget_root);
        mCountriesSpinner = (Spinner) v.findViewById(R.id.countries);

        mAutofillProfileBridge = new AutofillProfileBridge();

        populateCountriesSpinner();
        createAndPopulateEditFields();
        initializeSaveCancelDeleteButtons(v);

        return v;
    }

    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

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
        for (FloatLabelLayout field : mAddressFields) {
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

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    private void populateCountriesSpinner() {
        List<Country> countries = AutofillProfileBridge.getSupportedCountries();
        mCountryCodes = new ArrayList<String>();

        for (Country country : countries) {
            mCountryCodes.add(country.mCode);
        }

        ArrayAdapter<Country> countriesAdapter = new ArrayAdapter<Country>(getActivity(),
                android.R.layout.simple_spinner_item, countries);
        countriesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCountriesSpinner.setAdapter(countriesAdapter);
    }

    private void createAndPopulateEditFields() {
        AutofillProfile profile = PersonalDataManager.getInstance().getProfile(mGUID);

        if (profile != null) {
            if (!TextUtils.isEmpty(profile.getPhoneNumber())) {
                mPhoneLabel.setText(profile.getPhoneNumber());
            }

            if (!TextUtils.isEmpty(profile.getEmailAddress())) {
                mEmailLabel.setText(profile.getEmailAddress());
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

        mCountriesSpinner.setSelection(mCurrentCountryPos);
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
        List<Pair<Integer, String>> fields = mAutofillProfileBridge.getAddressUiComponents(
                mCountryCodes.get(countryCodeIndex),
                mLanguageCodeString);
        if (!mUseSavedProfileLanguage) {
            mLanguageCodeString = mAutofillProfileBridge.getCurrentBestLanguageCode();
        }

        // Create form fields and focus the first field if autoFocusFirstField is true.
        boolean firstField = true;
        for (Pair<Integer, String> field : fields) {
            int fieldId = field.first;
            String fieldLabel = field.second;
            FloatLabelLayout fieldFloatLabel = (FloatLabelLayout) mInflater.inflate(
                    R.layout.preference_address_float_label_layout, mWidgetRoot, false);
            fieldFloatLabel.setHint(fieldLabel);

            EditText fieldEditText =
                    (EditText) fieldFloatLabel.findViewById(R.id.address_edit_text);
            fieldEditText.setHint(fieldLabel);
            fieldEditText.setContentDescription(fieldLabel);
            fieldEditText.addTextChangedListener(this);
            if (fieldId == AddressField.STREET_ADDRESS) {
                fieldEditText.setSingleLine(false);
            }

            mAddressFields[fieldId] = fieldFloatLabel;
            mWidgetRoot.addView(fieldFloatLabel);

            if (firstField && autoFocusFirstField) {
                fieldFloatLabel.focusWithoutAnimation();
                firstField = false;
            }
        }

        // Add back saved field text.
        for (int i = 0; i < mAddressFields.length; i++) {
            if (mAddressFields[i] != null && fieldText[i] != null
                    && !TextUtils.isEmpty(fieldText[i])) {
                mAddressFields[i].setText(fieldText[i]);
            }
        }
    }

    // Read edited data; save in the associated Chrome profile.
    // Ignore empty fields.
    private void saveProfile() {
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
            mAddressFields[fieldId].setText(text);
        }
    }

    private void deleteProfile() {
        if (AutofillProfileEditor.this.mGUID != null) {
            PersonalDataManager.getInstance().deleteProfile(mGUID);
        }
    }

    private void initializeSaveCancelDeleteButtons(View v) {
        Button button = (Button) v.findViewById(R.id.autofill_profile_delete);
        if ((mGUID == null) || (mGUID.compareTo("") == 0)) {
            // If this is a create, disable the delete button.
            button.setEnabled(false);
        } else {
            button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AutofillProfileEditor.this.deleteProfile();
                        getActivity().finish();
                    }
                });
        }
        button = (Button) v.findViewById(R.id.autofill_profile_cancel);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getActivity().finish();
                }
            });
        button = (Button) v.findViewById(R.id.autofill_profile_save);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AutofillProfileEditor.this.saveProfile();
                    getActivity().finish();
                }
            });
        button.setEnabled(false);

        // Listen for changes to inputs. Enable the save button after something has changed.
        mPhoneText.addTextChangedListener(this);
        mEmailText.addTextChangedListener(this);
        mCountriesSpinner.setOnItemSelectedListener(this);
        mNoCountryItemIsSelected = true;
    }

    private void setSaveButtonEnabled(boolean enabled) {
        if (getView() != null) {
            Button button = (Button) getView().findViewById(R.id.autofill_profile_save);
            button.setEnabled(enabled);
        }
    }
}
