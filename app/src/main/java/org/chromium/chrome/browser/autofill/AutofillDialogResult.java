// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/**
 * Java-side result of a non-cancelled AutofillDialog invocation, and
 * JNI glue for C++ AutofillDialogResult used by AutofillDialogControllerAndroid.
 */
@JNINamespace("autofill")
public class AutofillDialogResult {
    /**
     * Information about the credit card in the dialog result.
     */
    public static class ResultCard {
        private final int mExpirationMonth;
        private final int mExpirationYear;
        private final String mPan;
        private final String mCvn;

        /**
         * Creates a ResultCard.
         * @param expirationMonth Expiration month
         * @param expirationYear Expiration year
         * @param pan Credit card number
         * @param cvn Credit card verification number
         */
        @VisibleForTesting
        public ResultCard(int expirationMonth, int expirationYear, String pan, String cvn) {
            mExpirationMonth = expirationMonth;
            mExpirationYear = expirationYear;
            mPan = pan;
            mCvn = cvn;
        }

        /**
         * @return Expiration month
         */
        @CalledByNative("ResultCard")
        public int getExpirationMonth() {
            return mExpirationMonth;
        }

        /**
         * @return Expiration year
         */
        @CalledByNative("ResultCard")
        public int getExpirationYear() {
            return mExpirationYear;
        }

        /**
         * @return Credit card number
         */
        @CalledByNative("ResultCard")
        public String getPan() {
            return mPan;
        }

        /**
         * @return Credit card verification number
         */
        @CalledByNative("ResultCard")
        public String getCvn() {
            return mCvn;
        }
    }

    /**
     * Information about an address in the dialog result.
     */
    public static class ResultAddress {
        private final String mName;
        private final String mPhoneNumber;
        private final String mStreetAddress;
        private final String mLocality;
        private final String mDependentLocality;
        private final String mAdministrativeArea;
        private final String mPostalCode;
        private final String mSortingCode;
        private final String mCountryCode;
        private final String mLanguageCode;

        /**
         * Creates a ResultAddress.
         * Any parameter can be empty or null.
         * @param name Full name
         * @param phoneNumber Phone number
         * @param streetAddress Street address
         * @param locality Locality / City
         * @param dependentLocality Inner-city district / Suburb / Dependent locality
         * @param administrativeArea Region / State
         * @param postalCode Postal code
         * @param sortingCode Sorting code
         * @param countryCode Country code
         * @param languageCode Language code
         */
        @VisibleForTesting
        public ResultAddress(
                String name, String phoneNumber,
                String streetAddress,
                String locality, String dependentLocality,
                String administrativeArea, String postalCode, String sortingCode,
                String countryCode, String languageCode) {
            mName = name;
            mPhoneNumber = phoneNumber;
            mStreetAddress = streetAddress;
            mLocality = locality;
            mDependentLocality = dependentLocality;
            mAdministrativeArea = administrativeArea;
            mPostalCode = postalCode;
            mSortingCode = sortingCode;
            mCountryCode = countryCode;
            mLanguageCode = languageCode;
        }

        /**
         * @return Full name
         */
        @CalledByNative("ResultAddress")
        public String getName() {
            return mName;
        }

        /**
         * @return Phone number
         */
        @CalledByNative("ResultAddress")
        public String getPhoneNumber() {
            return mPhoneNumber;
        }

        /**
         * @return Street address
         */
        @CalledByNative("ResultAddress")
        public String getStreetAddress() {
            return mStreetAddress;
        }

        /**
         * @return Locality (city)
         */
        @CalledByNative("ResultAddress")
        public String getLocality() {
            return mLocality;
        }

        /**
         * @return Dependent locality (inner-city district / suburb)
         */
        @CalledByNative("ResultAddress")
        public String getDependentLocality() {
            return mDependentLocality;
        }

        /**
         * @return Administrative area (region / state)
         */
        @CalledByNative("ResultAddress")
        public String getAdministrativeArea() {
            return mAdministrativeArea;
        }

        /**
         * @return Postal code
         */
        @CalledByNative("ResultAddress")
        public String getPostalCode() {
            return mPostalCode;
        }

        /**
         * @return Sorting code
         */
        @CalledByNative("ResultAddress")
        public String getSortingCode() {
            return mSortingCode;
        }

        /**
         * @return Country code
         */
        @CalledByNative("ResultAddress")
        public String getCountryCode() {
            return mCountryCode;
        }

        /**
         * @return Language code
         */
        @CalledByNative("ResultAddress")
        public String getLanguageCode() {
            return mLanguageCode;
        }
    }

    /**
     * A response from the dialog.
     */
    public static class ResultWallet {
        private final String mEmail;
        private final String mGoogleTransactionId;
        private final ResultCard mCard;
        private final ResultAddress mBillingAddress;
        private final ResultAddress mShippingAddress;

        /**
         * Creates a ResultWallet.
         * Any fields could be empty or null.
         * @param email Email address
         * @param googleTransactionId Google transaction ID if any
         * @param card Information about the credit card
         * @param billingAddress Information about the billing address
         * @param shippingAddress Information about the shipping address
         */
        @VisibleForTesting
        public ResultWallet(
                String email, String googleTransactionId,
                ResultCard card, ResultAddress billingAddress, ResultAddress shippingAddress) {
            mEmail = email;
            mGoogleTransactionId = googleTransactionId;
            mCard = card;
            mBillingAddress = billingAddress;
            mShippingAddress = shippingAddress;
        }

        /**
         * @return Email address
         */
        @CalledByNative("ResultWallet")
        public String getEmail() {
            return mEmail;
        }

        /**
         * @return Google transaction ID if any
         */
        @CalledByNative("ResultWallet")
        public String getGoogleTransactionId() {
            return mGoogleTransactionId;
        }

        /**
         * @return Credit card information, or null
         */
        @CalledByNative("ResultWallet")
        public ResultCard getCard() {
            return mCard;
        }

        /**
         * @return Billing address information, or null
         */
        @CalledByNative("ResultWallet")
        public ResultAddress getBillingAddress() {
            return mBillingAddress;
        }

        /**
         * @return Shipping address information, or null
         */
        @CalledByNative("ResultWallet")
        public ResultAddress getShippingAddress() {
            return mShippingAddress;
        }
    }
}
