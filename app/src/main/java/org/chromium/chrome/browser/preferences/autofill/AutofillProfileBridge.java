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
     * This list must be kept in-sync with the corresponding enum in auotfill_profile_bridge.cc.
     */
    static class AddressField {
        static final int COUNTRY = 0;
        static final int ADMIN_AREA = 1;
        static final int LOCALITY = 2;
        static final int DEPENDENT_LOCALITY = 3;
        static final int SORTING_CODE = 4;
        static final int POSTAL_CODE = 5;
        static final int STREET_ADDRESS = 6;
        static final int ORGANIZATION = 7;
        static final int RECIPIENT = 8;

        static final int NUM_FIELDS = 9;
    }

    /**
     * A convenience class for storing a CLDR country code and its corresponding
     * localized display name.
     */
    static class Country {
        String mName;
        String mCode;

        Country(String name, String code) {
            mName = name;
            mCode = code;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    private String mCurrentBestLanguageCode;

    /**
     * @return The CLDR region code for the default locale.
     */
    public static String getDefaultCountryCode() {
        return nativeGetDefaultCountryCode();
    }

    /**
     * @return The list of supported countries sorted by their localized display
     *         names.
     */
    static List<Country> getSupportedCountries() {
        List<String> countryCodes = new ArrayList<String>();
        List<String> countryNames = new ArrayList<String>();
        List<Country> countries = new ArrayList<Country>();

        nativeGetSupportedCountries(countryCodes, countryNames);

        for (int i = 0; i < countryCodes.size(); i++) {
            countries.add(new Country(countryNames.get(i), countryCodes.get(i)));
        }

        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(countries, new Comparator<Country>() {
            @Override
            public int compare(Country lhs, Country rhs) {
                int result = collator.compare(lhs.mName, rhs.mName);
                if (result == 0) result = lhs.mCode.compareTo(rhs.mCode);
                return result;
            }
        });

        return countries;
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
     * @return A list containing pairs where the first element in the pair is an Integer
     *         representing the component id (one of the constants in AddressField), and the second
     *         element in the pair is the localized component name (intended for use as labels in
     *         the UI). The ordering in the list of pairs specifies the order these components
     *         should appear in the UI.
     */
    List<Pair<Integer, String>> getAddressUiComponents(String countryCode,
            String languageCode) {
        List<Integer> componentIds = new ArrayList<Integer>();
        List<String> componentNames = new ArrayList<String>();
        List<Pair<Integer, String>> uiComponents = new ArrayList<Pair<Integer, String>>();

        mCurrentBestLanguageCode =
                nativeGetAddressUiComponents(countryCode, languageCode, componentIds,
                        componentNames);

        for (int i = 0; i < componentIds.size(); i++) {
            uiComponents.add(new Pair<Integer, String>(componentIds.get(i), componentNames.get(i)));
        }

        return uiComponents;
    }

    /**
     * @return The language code associated with the most recently retrieved address ui components.
     *         Will return null if getAddressUiComponents() has not been called yet.
     */
    String getCurrentBestLanguageCode() {
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
    private static native String nativeGetAddressUiComponents(String countryCode,
            String languageCode, List<Integer> componentIds, List<String> componentNames);
}
