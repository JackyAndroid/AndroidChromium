// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.text.TextUtils;

import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.payments.ui.PaymentOption;
import org.chromium.payments.mojom.PaymentAddress;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * The locally stored autofill address.
 */
public class AutofillAddress extends PaymentOption {
    /** The pattern for a valid region code. */
    private static final String REGION_CODE_PATTERN = "^[A-Z]{2}$";

    // Language/script code pattern and capture group numbers.
    private static final String LANGUAGE_SCRIPT_CODE_PATTERN =
            "^([a-z]{2})(-([A-Z][a-z]{3}))?(-[A-Za-z]+)*$";
    private static final int LANGUAGE_CODE_GROUP = 1;
    private static final int SCRIPT_CODE_GROUP = 3;

    @Nullable private static Pattern sRegionCodePattern;

    private AutofillProfile mProfile;
    @Nullable private Pattern mLanguageScriptCodePattern;

    /**
     * Builds the autofill address.
     *
     * @param profile The autofill profile containing the address information.
     */
    public AutofillAddress(AutofillProfile profile, boolean isComplete) {
        super(profile.getGUID(), profile.getFullName(), profile.getLabel(),
                profile.getPhoneNumber(), PaymentOption.NO_ICON);
        mProfile = profile;
        mIsComplete = isComplete;
    }

    /** @return The autofill profile where this address data lives. */
    public AutofillProfile getProfile() {
        return mProfile;
    }

    /**
     * Updates the address and marks it "complete." Called after the user has edited this address.
     * Updates the identifier and labels.
     *
     * @param profile The new profile to use.
     */
    public void completeAddress(AutofillProfile profile) {
        mProfile = profile;
        mIsComplete = true;
        updateIdentifierAndLabels(mProfile.getGUID(), mProfile.getFullName(), mProfile.getLabel(),
                mProfile.getPhoneNumber());
    }

    /** @return The country code to use, e.g., when constructing an editor for this address. */
    public static String getCountryCode(@Nullable AutofillProfile profile) {
        if (sRegionCodePattern == null) sRegionCodePattern = Pattern.compile(REGION_CODE_PATTERN);

        return profile == null || TextUtils.isEmpty(profile.getCountryCode())
                        || !sRegionCodePattern.matcher(profile.getCountryCode()).matches()
                ? Locale.getDefault().getCountry() : profile.getCountryCode();
    }

    /** @return The address for the merchant. */
    public PaymentAddress toPaymentAddress() {
        assert mIsComplete;
        PaymentAddress result = new PaymentAddress();

        result.country = getCountryCode(mProfile);
        result.addressLine = mProfile.getStreetAddress().split("\n");
        result.region = mProfile.getRegion();
        result.city = mProfile.getLocality();
        result.dependentLocality = mProfile.getDependentLocality();
        result.postalCode = mProfile.getPostalCode();
        result.sortingCode = mProfile.getSortingCode();
        result.organization = mProfile.getCompanyName();
        result.recipient = mProfile.getFullName();
        result.languageCode = "";
        result.scriptCode = "";
        result.phone = mProfile.getPhoneNumber();

        if (mProfile.getLanguageCode() == null) return result;

        if (mLanguageScriptCodePattern == null) {
            mLanguageScriptCodePattern = Pattern.compile(LANGUAGE_SCRIPT_CODE_PATTERN);
        }

        Matcher matcher = mLanguageScriptCodePattern.matcher(mProfile.getLanguageCode());
        if (matcher.matches()) {
            result.languageCode = ensureNotNull(matcher.group(LANGUAGE_CODE_GROUP));
            result.scriptCode = ensureNotNull(matcher.group(SCRIPT_CODE_GROUP));
        }

        return result;
    }

    private static String ensureNotNull(@Nullable String value) {
        return value == null ? "" : value;
    }
}
