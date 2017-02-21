// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill;

import android.util.Pair;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Static methods to fetch information needed to create the address fields for the autofill profile
 * form.
 */
@JNINamespace("autofill")
public class AutofillProfileBridge {

    /**
     * Address field types.
     * This list must be kept in-sync with the corresponding enum in
     * third_party/libaddressinput/src/cpp/include/libaddressinput/address_field.h
     */
    public static class AddressField {
        public static final int COUNTRY = 0;
        public static final int ADMIN_AREA = 1;
        public static final int LOCALITY = 2;
        public static final int DEPENDENT_LOCALITY = 3;
        public static final int SORTING_CODE = 4;
        public static final int POSTAL_CODE = 5;
        public static final int STREET_ADDRESS = 6;
        public static final int ORGANIZATION = 7;
        public static final int RECIPIENT = 8;

        public static final int NUM_FIELDS = 9;
    }

    /**
     * A convenience class for displaying keyed values in a dropdown.
     */
    public static class DropdownKeyValue extends Pair<String, String> {
        public DropdownKeyValue(String key, String value) {
            super(key, value);
        }

        /** @return The key identifier. */
        public String getKey() {
            return super.first;
        }

        /** @return The human-readable localized display value. */
        public String getValue() {
            return super.second;
        }

        @Override
        public String toString() {
            return super.second;
        }
    }

    private String mCurrentBestLanguageCode;

    /**
     * @return The CLDR region code for the default locale.
     */
    public static String getDefaultCountryCode() {
        return nativeGetDefaultCountryCode();
    }

    /** @return The list of supported countries sorted by their localized display names. */
    public static List<DropdownKeyValue> getSupportedCountries() {
        List<String> countryCodes = new ArrayList<>();
        List<String> countryNames = new ArrayList<>();
        List<DropdownKeyValue> countries = new ArrayList<>();

        nativeGetSupportedCountries(countryCodes, countryNames);

        for (int i = 0; i < countryCodes.size(); i++) {
            countries.add(new DropdownKeyValue(countryCodes.get(i), countryNames.get(i)));
        }

        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(countries, new Comparator<DropdownKeyValue>() {
            @Override
            public int compare(DropdownKeyValue lhs, DropdownKeyValue rhs) {
                int result = collator.compare(lhs.getValue(), rhs.getValue());
                if (result == 0) result = lhs.getKey().compareTo(rhs.getKey());
                return result;
            }
        });

        return countries;
    }

    /** @return The list of required fields. COUNTRY is always included. RECIPIENT often omitted. */
    public static List<Integer> getRequiredAddressFields(String countryCode) {
        List<Integer> requiredFields = new ArrayList<>();
        nativeGetRequiredFields(countryCode, requiredFields);
        return requiredFields;
    }

    /**
     * Description of an address editor input field.
     */
    public static class AddressUiComponent {
        /** The type of the field, e.g., AddressField.LOCALITY. */
        public final int id;

        /** The localized display label for the field, e.g., "City." */
        public final String label;

        /** Whether the field is required. */
        public final boolean isRequired;

        /** Whether the field takes up the full line.*/
        public final boolean isFullLine;

        /**
         * Builds a description of an address editor input field.
         *
         * @param id         The type of the field, .e.g., AddressField.LOCALITY.
         * @param label      The localized display label for the field, .e.g., "City."
         * @param isRequired Whether the field is required.
         * @param isFullLine Whether the field takes up the full line.
         */
        public AddressUiComponent(int id, String label, boolean isRequired, boolean isFullLine) {
            this.id = id;
            this.label = label;
            this.isRequired = isRequired;
            this.isFullLine = isFullLine;
        }
    }

    /**
     * Returns the UI components for the CLDR countryCode and languageCode provided. If no language
     * code is provided, the application's default locale is used instead. Also stores the
     * currentBestLanguageCode, retrievable via getCurrentBestLanguageCode, to be used when saving
     * an autofill profile.
     *
     * @param countryCode The CLDR code used to retrieve address components.
     * @param languageCode The language code associated with the saved autofill profile that ui
     *                     components are being retrieved for; can be null if ui components are
     *                     being retrieved for a new profile.
     * @return A list of address UI components. The ordering in the list specifies the order these
     *         components should appear in the UI.
     */
    public List<AddressUiComponent> getAddressUiComponents(
            String countryCode, String languageCode) {
        List<Integer> componentIds = new ArrayList<>();
        List<String> componentNames = new ArrayList<>();
        List<Integer> componentRequired = new ArrayList<>();
        List<Integer> componentLengths = new ArrayList<>();
        List<AddressUiComponent> uiComponents = new ArrayList<>();

        mCurrentBestLanguageCode = nativeGetAddressUiComponents(countryCode, languageCode,
                componentIds, componentNames, componentRequired, componentLengths);

        for (int i = 0; i < componentIds.size(); i++) {
            uiComponents.add(new AddressUiComponent(componentIds.get(i), componentNames.get(i),
                    componentRequired.get(i) == 1, componentLengths.get(i) == 1));
        }

        return uiComponents;
    }

    /**
     * @return The language code associated with the most recently retrieved address ui components.
     *         Will return null if getAddressUiComponents() has not been called yet.
     */
    public String getCurrentBestLanguageCode() {
        return mCurrentBestLanguageCode;
    }

    @CalledByNative
    private static void stringArrayToList(String[] array, List<String> list) {
        for (String s : array) {
            list.add(s);
        }
    }

    @CalledByNative
    private static void intArrayToList(int[] array, List<Integer> list) {
        for (int s : array) {
            list.add(s);
        }
    }

    private static native String nativeGetDefaultCountryCode();
    private static native void nativeGetSupportedCountries(List<String> countryCodes,
            List<String> countryNames);
    private static native void nativeGetRequiredFields(
            String countryCode, List<Integer> requiredFields);
    private static native String nativeGetAddressUiComponents(String countryCode,
            String languageCode, List<Integer> componentIds, List<String> componentNames,
            List<Integer> componentRequired, List<Integer> componentLengths);
}
