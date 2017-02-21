// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.chrome.browser.preferences.autofill.AutofillPreferences;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Android wrapper of the PersonalDataManager which provides access from the Java
 * layer.
 *
 * Only usable from the UI thread as it's primary purpose is for supporting the Android
 * preferences UI.
 *
 * See chrome/browser/autofill/personal_data_manager.h for more details.
 */
@JNINamespace("autofill")
public class PersonalDataManager {

    /**
     * Observer of PersonalDataManager events.
     */
    public interface PersonalDataManagerObserver {
        /**
         * Called when the data is changed.
         */
        void onPersonalDataChanged();
    }

    /**
     * Callback for full card request.
     */
    public interface FullCardRequestDelegate {
        /**
         * Called when user provided the full card details, including the CVC and the full PAN.
         *
         * @param card The full card.
         * @param cvc The CVC for the card.
         */
        @CalledByNative("FullCardRequestDelegate")
        void onFullCardDetails(CreditCard card, String cvc);

        /**
         * Called when user did not provide full card details.
         */
        @CalledByNative("FullCardRequestDelegate")
        void onFullCardError();
    }

    /**
     * Callback for normalized addresses.
     */
    public interface NormalizedAddressRequestDelegate {
        /**
         * Called when the address has been sucessfully normalized.
         *
         * @param profile The profile with the normalized address.
         */
        @CalledByNative("NormalizedAddressRequestDelegate")
        void onAddressNormalized(AutofillProfile profile);
    }

    /**
     * Autofill address information.
     */
    public static class AutofillProfile {
        private String mGUID;
        private String mOrigin;
        private boolean mIsLocal;
        private String mFullName;
        private String mCompanyName;
        private String mStreetAddress;
        private String mRegion;
        private String mLocality;
        private String mDependentLocality;
        private String mPostalCode;
        private String mSortingCode;
        private String mCountryCode;
        private String mPhoneNumber;
        private String mEmailAddress;
        private String mLabel;
        private String mLanguageCode;

        @CalledByNative("AutofillProfile")
        public static AutofillProfile create(String guid, String origin, boolean isLocal,
                String fullName, String companyName, String streetAddress, String region,
                String locality, String dependentLocality, String postalCode, String sortingCode,
                String country, String phoneNumber, String emailAddress, String languageCode) {
            return new AutofillProfile(guid, origin, isLocal, fullName, companyName, streetAddress,
                    region, locality, dependentLocality, postalCode, sortingCode, country,
                    phoneNumber, emailAddress, languageCode);
        }

        public AutofillProfile(String guid, String origin, boolean isLocal, String fullName,
                String companyName, String streetAddress, String region, String locality,
                String dependentLocality, String postalCode, String sortingCode, String countryCode,
                String phoneNumber, String emailAddress, String languageCode) {
            mGUID = guid;
            mOrigin = origin;
            mIsLocal = isLocal;
            mFullName = fullName;
            mCompanyName = companyName;
            mStreetAddress = streetAddress;
            mRegion = region;
            mLocality = locality;
            mDependentLocality = dependentLocality;
            mPostalCode = postalCode;
            mSortingCode = sortingCode;
            mCountryCode = countryCode;
            mPhoneNumber = phoneNumber;
            mEmailAddress = emailAddress;
            mLanguageCode = languageCode;
        }

        /**
         * Builds an empty local profile with "settings" origin and country code from the default
         * locale. All other fields are empty strings, because JNI does not handle null strings.
         */
        public AutofillProfile() {
            this("" /* guid */, AutofillPreferences.SETTINGS_ORIGIN /* origin */,
                    true /* isLocal */, "" /* fullName */, "" /* companyName */,
                    "" /* streetAddress */, "" /* region */, "" /* locality */,
                    "" /* dependentLocality */, "" /* postalCode */, "" /* sortingCode */,
                    Locale.getDefault().getCountry() /* country */, "" /* phoneNumber */,
                    "" /* emailAddress */, "" /* languageCode */);
        }

        /** TODO(estade): remove this constructor. */
        @VisibleForTesting
        public AutofillProfile(String guid, String origin, String fullName, String companyName,
                String streetAddress, String region, String locality, String dependentLocality,
                String postalCode, String sortingCode, String countryCode, String phoneNumber,
                String emailAddress, String languageCode) {
            this(guid, origin, true /* isLocal */, fullName, companyName, streetAddress, region,
                    locality, dependentLocality, postalCode, sortingCode, countryCode, phoneNumber,
                    emailAddress, languageCode);
        }

        @CalledByNative("AutofillProfile")
        public String getGUID() {
            return mGUID;
        }

        @CalledByNative("AutofillProfile")
        public String getOrigin() {
            return mOrigin;
        }

        @CalledByNative("AutofillProfile")
        public String getFullName() {
            return mFullName;
        }

        @CalledByNative("AutofillProfile")
        public String getCompanyName() {
            return mCompanyName;
        }

        @CalledByNative("AutofillProfile")
        public String getStreetAddress() {
            return mStreetAddress;
        }

        @CalledByNative("AutofillProfile")
        public String getRegion() {
            return mRegion;
        }

        @CalledByNative("AutofillProfile")
        public String getLocality() {
            return mLocality;
        }

        @CalledByNative("AutofillProfile")
        public String getDependentLocality() {
            return mDependentLocality;
        }

        public String getLabel() {
            return mLabel;
        }

        @CalledByNative("AutofillProfile")
        public String getPostalCode() {
            return mPostalCode;
        }

        @CalledByNative("AutofillProfile")
        public String getSortingCode() {
            return mSortingCode;
        }

        @CalledByNative("AutofillProfile")
        public String getCountryCode() {
            return mCountryCode;
        }

        @CalledByNative("AutofillProfile")
        public String getPhoneNumber() {
            return mPhoneNumber;
        }

        @CalledByNative("AutofillProfile")
        public String getEmailAddress() {
            return mEmailAddress;
        }

        @CalledByNative("AutofillProfile")
        public String getLanguageCode() {
            return mLanguageCode;
        }

        public boolean getIsLocal() {
            return mIsLocal;
        }

        @VisibleForTesting
        public void setGUID(String guid) {
            mGUID = guid;
        }

        public void setLabel(String label) {
            mLabel = label;
        }

        public void setOrigin(String origin) {
            mOrigin = origin;
        }

        public void setFullName(String fullName) {
            mFullName = fullName;
        }

        public void setCompanyName(String companyName) {
            mCompanyName = companyName;
        }

        @VisibleForTesting
        public void setStreetAddress(String streetAddress) {
            mStreetAddress = streetAddress;
        }

        public void setRegion(String region) {
            mRegion = region;
        }

        public void setLocality(String locality) {
            mLocality = locality;
        }

        public void setDependentLocality(String dependentLocality) {
            mDependentLocality = dependentLocality;
        }

        public void setPostalCode(String postalCode) {
            mPostalCode = postalCode;
        }

        public void setSortingCode(String sortingCode) {
            mSortingCode = sortingCode;
        }

        @VisibleForTesting
        public void setCountryCode(String countryCode) {
            mCountryCode = countryCode;
        }

        public void setPhoneNumber(String phoneNumber) {
            mPhoneNumber = phoneNumber;
        }

        public void setEmailAddress(String emailAddress) {
            mEmailAddress = emailAddress;
        }

        @VisibleForTesting
        public void setLanguageCode(String languageCode) {
            mLanguageCode = languageCode;
        }

        /** Used by ArrayAdapter in credit card settings. */
        @Override
        public String toString() {
            return mLabel;
        }
    }

    /**
     * Autofill credit card information.
     */
    public static class CreditCard {
        // Note that while some of these fields are numbers, they're predominantly read,
        // marshaled and compared as strings. To save conversions, we use strings everywhere.
        private String mGUID;
        private String mOrigin;
        private boolean mIsLocal;
        private boolean mIsCached;
        private String mName;
        private String mNumber;
        private String mObfuscatedNumber;
        private String mMonth;
        private String mYear;
        private String mBasicCardPaymentType;
        private int mIssuerIconDrawableId;
        private String mBillingAddressId;
        private String mServerId;

        @CalledByNative("CreditCard")
        public static CreditCard create(String guid, String origin, boolean isLocal,
                boolean isCached, String name, String number, String obfuscatedNumber, String month,
                String year, String basicCardPaymentType, int enumeratedIconId,
                String billingAddressId, String serverId) {
            return new CreditCard(guid, origin, isLocal, isCached, name, number, obfuscatedNumber,
                    month, year, basicCardPaymentType, ResourceId.mapToDrawableId(enumeratedIconId),
                    billingAddressId, serverId);
        }

        public CreditCard(String guid, String origin, boolean isLocal, boolean isCached,
                String name, String number, String obfuscatedNumber, String month, String year,
                String basicCardPaymentType, int issuerIconDrawableId, String billingAddressId,
                String serverId) {
            mGUID = guid;
            mOrigin = origin;
            mIsLocal = isLocal;
            mIsCached = isCached;
            mName = name;
            mNumber = number;
            mObfuscatedNumber = obfuscatedNumber;
            mMonth = month;
            mYear = year;
            mBasicCardPaymentType = basicCardPaymentType;
            mIssuerIconDrawableId = issuerIconDrawableId;
            mBillingAddressId = billingAddressId;
            mServerId = serverId;
        }

        public CreditCard() {
            this("" /* guid */, AutofillPreferences.SETTINGS_ORIGIN /*origin */, true /* isLocal */,
                    false /* isCached */, "" /* name */, "" /* number */, "" /* obfuscatedNumber */,
                    "" /* month */, "" /* year */, "" /* basicCardPaymentType */,
                    0 /* issuerIconDrawableId */, "" /* billingAddressId */, "" /* serverId */);
        }

        /** TODO(estade): remove this constructor. */
        @VisibleForTesting
        public CreditCard(String guid, String origin, String name, String number,
                String obfuscatedNumber, String month, String year) {
            this(guid, origin, true /* isLocal */, false /* isCached */, name, number,
                    obfuscatedNumber, month, year, "" /* basicCardPaymentType */,
                    0 /* issuerIconDrawableId */, "" /* billingAddressId */, "" /* serverId */);
        }

        @CalledByNative("CreditCard")
        public String getGUID() {
            return mGUID;
        }

        @CalledByNative("CreditCard")
        public String getOrigin() {
            return mOrigin;
        }

        @CalledByNative("CreditCard")
        public String getName() {
            return mName;
        }

        @CalledByNative("CreditCard")
        public String getNumber() {
            return mNumber;
        }

        public String getObfuscatedNumber() {
            return mObfuscatedNumber;
        }

        @CalledByNative("CreditCard")
        public String getMonth() {
            return mMonth;
        }

        @CalledByNative("CreditCard")
        public String getYear() {
            return mYear;
        }

        public String getFormattedExpirationDate(Context context) {
            return getMonth()
                    + context.getResources().getString(
                              R.string.autofill_card_unmask_expiration_date_separator) + getYear();
        }

        @CalledByNative("CreditCard")
        public boolean getIsLocal() {
            return mIsLocal;
        }

        @CalledByNative("CreditCard")
        public boolean getIsCached() {
            return mIsCached;
        }

        @CalledByNative("CreditCard")
        public String getBasicCardPaymentType() {
            return mBasicCardPaymentType;
        }

        public int getIssuerIconDrawableId() {
            return mIssuerIconDrawableId;
        }

        @CalledByNative("CreditCard")
        public String getBillingAddressId() {
            return mBillingAddressId;
        }

        @CalledByNative("CreditCard")
        public String getServerId() {
            return mServerId;
        }

        @VisibleForTesting
        public void setGUID(String guid) {
            mGUID = guid;
        }

        public void setOrigin(String origin) {
            mOrigin = origin;
        }

        public void setName(String name) {
            mName = name;
        }

        @VisibleForTesting
        public void setNumber(String number) {
            mNumber = number;
        }

        public void setObfuscatedNumber(String obfuscatedNumber) {
            mObfuscatedNumber = obfuscatedNumber;
        }

        @VisibleForTesting
        public void setMonth(String month) {
            mMonth = month;
        }

        public void setYear(String year) {
            mYear = year;
        }

        public void setBasicCardPaymentType(String type) {
            mBasicCardPaymentType = type;
        }

        public void setIssuerIconDrawableId(int id) {
            mIssuerIconDrawableId = id;
        }

        public void setBillingAddressId(String id) {
            mBillingAddressId = id;
        }
    }

    private static PersonalDataManager sManager;

    public static PersonalDataManager getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sManager == null) {
            sManager = new PersonalDataManager();
        }
        return sManager;
    }

    private static int sNormalizationTimeoutMs = 5000;

    private final long mPersonalDataManagerAndroid;
    private final List<PersonalDataManagerObserver> mDataObservers =
            new ArrayList<PersonalDataManagerObserver>();

    private PersonalDataManager() {
        // Note that this technically leaks the native object, however, PersonalDataManager
        // is a singleton that lives forever and there's no clean shutdown of Chrome on Android
        mPersonalDataManagerAndroid = nativeInit();
    }

    /**
     * Called from native when template URL service is done loading.
     */
    @CalledByNative
    private void personalDataChanged() {
        ThreadUtils.assertOnUiThread();
        for (PersonalDataManagerObserver observer : mDataObservers) {
            observer.onPersonalDataChanged();
        }
    }

    /**
     * Registers a PersonalDataManagerObserver on the native side.
     */
    public boolean registerDataObserver(PersonalDataManagerObserver observer) {
        ThreadUtils.assertOnUiThread();
        assert !mDataObservers.contains(observer);
        mDataObservers.add(observer);
        return nativeIsDataLoaded(mPersonalDataManagerAndroid);
    }

    /**
     * Unregisters the provided observer.
     */
    public void unregisterDataObserver(PersonalDataManagerObserver observer) {
        ThreadUtils.assertOnUiThread();
        assert (mDataObservers.size() > 0);
        assert (mDataObservers.contains(observer));
        mDataObservers.remove(observer);
    }

    // TODO(crbug.com/616102): Reduce the number of Java to Native calls when getting profiles.
    /**
     * Gets the profiles to show in the settings page. Returns all the profiles without any
     * processing.
     *
     * @return The list of profiles to show in the settings.
     */
    public List<AutofillProfile> getProfilesForSettings() {
        ThreadUtils.assertOnUiThread();
        return getProfilesWithLabels(nativeGetProfileLabelsForSettings(mPersonalDataManagerAndroid),
                nativeGetProfileGUIDsForSettings(mPersonalDataManagerAndroid));
    }

    // TODO(crbug.com/616102): Reduce the number of Java to Native calls when getting profiles.
    /**
     * Gets the profiles to suggest when filling a form or completing a transaction. The profiles
     * will have been processed to be more relevant to the user.
     *
     * @param includeName Whether to include the name in the profile's label.
     * @return The list of profiles to suggest to the user.
     */
    public List<AutofillProfile> getProfilesToSuggest(boolean includeName) {
        ThreadUtils.assertOnUiThread();
        return getProfilesWithLabels(
                nativeGetProfileLabelsToSuggest(
                        mPersonalDataManagerAndroid, includeName),
                nativeGetProfileGUIDsToSuggest(mPersonalDataManagerAndroid));
    }

    private List<AutofillProfile> getProfilesWithLabels(
            String[] profileLabels, String[] profileGUIDs) {
        List<AutofillProfile> profiles = new ArrayList<AutofillProfile>(profileGUIDs.length);
        for (int i = 0; i < profileGUIDs.length; i++) {
            AutofillProfile profile =
                    nativeGetProfileByGUID(mPersonalDataManagerAndroid, profileGUIDs[i]);
            profile.setLabel(profileLabels[i]);
            profiles.add(profile);
        }

        return profiles;
    }

    public AutofillProfile getProfile(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetProfileByGUID(mPersonalDataManagerAndroid, guid);
    }

    public void deleteProfile(String guid) {
        ThreadUtils.assertOnUiThread();
        nativeRemoveByGUID(mPersonalDataManagerAndroid, guid);
    }

    public String setProfile(AutofillProfile profile) {
        ThreadUtils.assertOnUiThread();
        return nativeSetProfile(mPersonalDataManagerAndroid, profile);
    }

    /**
     * Gets the credit cards to show in the settings page. Returns all the cards without any
     * processing.
     */
    public List<CreditCard> getCreditCardsForSettings() {
        ThreadUtils.assertOnUiThread();
        return getCreditCards(nativeGetCreditCardGUIDsForSettings(mPersonalDataManagerAndroid));
    }

    /**
     * Gets the credit cards to suggest when filling a form or completing a transaction. The cards
     * will have been processed to be more relevant to the user.
     */
    public List<CreditCard> getCreditCardsToSuggest() {
        ThreadUtils.assertOnUiThread();
        return getCreditCards(nativeGetCreditCardGUIDsToSuggest(mPersonalDataManagerAndroid));
    }

    private List<CreditCard> getCreditCards(String[] creditCardGUIDs) {
        List<CreditCard> cards = new ArrayList<CreditCard>(creditCardGUIDs.length);
        for (int i = 0; i < creditCardGUIDs.length; i++) {
            cards.add(nativeGetCreditCardByGUID(mPersonalDataManagerAndroid, creditCardGUIDs[i]));
        }
        return cards;
    }

    public CreditCard getCreditCard(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetCreditCardByGUID(mPersonalDataManagerAndroid, guid);
    }

    public CreditCard getCreditCardForNumber(String cardNumber) {
        ThreadUtils.assertOnUiThread();
        return nativeGetCreditCardForNumber(mPersonalDataManagerAndroid, cardNumber);
    }

    public String setCreditCard(CreditCard card) {
        ThreadUtils.assertOnUiThread();
        assert card.getIsLocal();
        return nativeSetCreditCard(mPersonalDataManagerAndroid, card);
    }

    public void updateServerCardBillingAddress(String cardServerId, String billingAddressId) {
        ThreadUtils.assertOnUiThread();
        nativeUpdateServerCardBillingAddress(
                mPersonalDataManagerAndroid, cardServerId, billingAddressId);
    }

    public String getBasicCardPaymentTypeIfValid(String cardNumber) {
        ThreadUtils.assertOnUiThread();
        return nativeGetBasicCardPaymentTypeIfValid(mPersonalDataManagerAndroid, cardNumber);
    }

    @VisibleForTesting
    public void addServerCreditCardForTest(CreditCard card) {
        ThreadUtils.assertOnUiThread();
        assert !card.getIsLocal();
        nativeAddServerCreditCardForTest(mPersonalDataManagerAndroid, card);
    }

    public void deleteCreditCard(String guid) {
        ThreadUtils.assertOnUiThread();
        nativeRemoveByGUID(mPersonalDataManagerAndroid, guid);
    }

    public void clearUnmaskedCache(String guid) {
        nativeClearUnmaskedCache(mPersonalDataManagerAndroid, guid);
    }

    public String getAddressLabelForPaymentRequest(AutofillProfile profile) {
        return nativeGetAddressLabelForPaymentRequest(mPersonalDataManagerAndroid, profile);
    }

    public void getFullCard(WebContents webContents, CreditCard card,
            FullCardRequestDelegate delegate) {
        nativeGetFullCardForPaymentRequest(mPersonalDataManagerAndroid, webContents, card,
                delegate);
    }

    /**
     * Records the use of the profile associated with the specified {@code guid}. Effectively
     * increments the use count of the profile and sets its use date to the current time. Also logs
     * usage metrics.
     *
     * @param guid The GUID of the profile.
     */
    public void recordAndLogProfileUse(String guid) {
        ThreadUtils.assertOnUiThread();
        nativeRecordAndLogProfileUse(mPersonalDataManagerAndroid, guid);
    }

    @VisibleForTesting
    protected void setProfileUseStatsForTesting(String guid, int count, long date) {
        ThreadUtils.assertOnUiThread();
        nativeSetProfileUseStatsForTesting(mPersonalDataManagerAndroid, guid, count, date);
    }

    @VisibleForTesting
    int getProfileUseCountForTesting(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetProfileUseCountForTesting(mPersonalDataManagerAndroid, guid);
    }

    @VisibleForTesting
    long getProfileUseDateForTesting(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetProfileUseDateForTesting(mPersonalDataManagerAndroid, guid);
    }

    /**
     * Records the use of the credit card associated with the specified {@code guid}. Effectively
     * increments the use count of the credit card and set its use date to the current time. Also
     * logs usage metrics.
     *
     * @param guid The GUID of the credit card.
     */
    public void recordAndLogCreditCardUse(String guid) {
        ThreadUtils.assertOnUiThread();
        nativeRecordAndLogCreditCardUse(mPersonalDataManagerAndroid, guid);
    }

    @VisibleForTesting
    protected void setCreditCardUseStatsForTesting(String guid, int count, long date) {
        ThreadUtils.assertOnUiThread();
        nativeSetCreditCardUseStatsForTesting(mPersonalDataManagerAndroid, guid, count, date);
    }

    @VisibleForTesting
    int getCreditCardUseCountForTesting(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetCreditCardUseCountForTesting(mPersonalDataManagerAndroid, guid);
    }

    @VisibleForTesting
    long getCreditCardUseDateForTesting(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetCreditCardUseDateForTesting(mPersonalDataManagerAndroid, guid);
    }

    @VisibleForTesting
    long getCurrentDateForTesting() {
        ThreadUtils.assertOnUiThread();
        return nativeGetCurrentDateForTesting(mPersonalDataManagerAndroid);
    }

    /**
     * Starts loading the address validation rules for the specified {@code regionCode}.
     *
     * @param regionCode The code of the region for which to load the rules.
     */
    public void loadRulesForRegion(String regionCode) {
        ThreadUtils.assertOnUiThread();
        nativeLoadRulesForRegion(mPersonalDataManagerAndroid, regionCode);
    }

    /**
     * Normalizes the address of the profile associated with the {@code guid} if the rules
     * associated with the {@code regionCode} are done loading. Otherwise sets up the callback to
     * start normalizing the address when the rules are loaded. The normalized profile will be sent
     * to the {@code delegate}.
     *
     * @param guid The GUID of the profile to normalize.
     * @param regionCode The region code indicating which rules to use for normalization.
     * @param delegate The object requesting the normalization.
     *
     * @return Whether the normalization will happen asynchronously.
     */
    public boolean normalizeAddress(
            String guid, String regionCode, NormalizedAddressRequestDelegate delegate) {
        ThreadUtils.assertOnUiThread();
        return nativeStartAddressNormalization(
                mPersonalDataManagerAndroid, guid, regionCode, delegate);
    }

    /** Cancels the pending address normalization. */
    public void cancelPendingAddressNormalization() {
        ThreadUtils.assertOnUiThread();
        nativeCancelPendingAddressNormalization(mPersonalDataManagerAndroid);
    }

    /**
     * @return Whether the Autofill feature is enabled.
     */
    public static boolean isAutofillEnabled() {
        return nativeIsAutofillEnabled();
    }

    /**
     * Enables or disables the Autofill feature.
     * @param enable True to disable Autofill, false otherwise.
     */
    public static void setAutofillEnabled(boolean enable) {
        nativeSetAutofillEnabled(enable);
    }

    /**
     * @return Whether the Autofill feature is managed.
     */
    public static boolean isAutofillManaged() {
        return nativeIsAutofillManaged();
    }

    /**
     * @return Whether the Payments integration feature is enabled.
     */
    public static boolean isPaymentsIntegrationEnabled() {
        return nativeIsPaymentsIntegrationEnabled();
    }

    /**
     * Enables or disables the Payments integration.
     * @param enable True to enable Payments data import.
     */
    public static void setPaymentsIntegrationEnabled(boolean enable) {
        nativeSetPaymentsIntegrationEnabled(enable);
    }

    /**
     * @return The timeout value for normalization.
     */
    public static int getNormalizationTimeoutMS() {
        return sNormalizationTimeoutMs;
    }

    @VisibleForTesting
    public static void setNormalizationTimeoutForTesting(int timeout) {
        sNormalizationTimeoutMs = timeout;
    }

    private native long nativeInit();
    private native boolean nativeIsDataLoaded(long nativePersonalDataManagerAndroid);
    private native String[] nativeGetProfileGUIDsForSettings(long nativePersonalDataManagerAndroid);
    private native String[] nativeGetProfileGUIDsToSuggest(long nativePersonalDataManagerAndroid);
    private native String[] nativeGetProfileLabelsForSettings(
            long nativePersonalDataManagerAndroid);
    private native String[] nativeGetProfileLabelsToSuggest(long nativePersonalDataManagerAndroid,
            boolean includeName);
    private native AutofillProfile nativeGetProfileByGUID(long nativePersonalDataManagerAndroid,
            String guid);
    private native String nativeSetProfile(long nativePersonalDataManagerAndroid,
            AutofillProfile profile);
    private native String nativeGetAddressLabelForPaymentRequest(
            long nativePersonalDataManagerAndroid, AutofillProfile profile);
    private native String[] nativeGetCreditCardGUIDsForSettings(
            long nativePersonalDataManagerAndroid);
    private native String[] nativeGetCreditCardGUIDsToSuggest(
            long nativePersonalDataManagerAndroid);
    private native CreditCard nativeGetCreditCardByGUID(long nativePersonalDataManagerAndroid,
            String guid);
    private native CreditCard nativeGetCreditCardForNumber(long nativePersonalDataManagerAndroid,
            String cardNumber);
    private native String nativeSetCreditCard(long nativePersonalDataManagerAndroid,
            CreditCard card);
    private native void nativeUpdateServerCardBillingAddress(long nativePersonalDataManagerAndroid,
            String guid, String billingAddressId);
    private native String nativeGetBasicCardPaymentTypeIfValid(
            long nativePersonalDataManagerAndroid, String cardNumber);
    private native void nativeAddServerCreditCardForTest(long nativePersonalDataManagerAndroid,
            CreditCard card);
    private native void nativeRemoveByGUID(long nativePersonalDataManagerAndroid, String guid);
    private native void nativeRecordAndLogProfileUse(long nativePersonalDataManagerAndroid,
            String guid);
    private native void nativeSetProfileUseStatsForTesting(
            long nativePersonalDataManagerAndroid, String guid, int count, long date);
    private native int nativeGetProfileUseCountForTesting(long nativePersonalDataManagerAndroid,
            String guid);
    private native long nativeGetProfileUseDateForTesting(long nativePersonalDataManagerAndroid,
            String guid);
    private native void nativeRecordAndLogCreditCardUse(long nativePersonalDataManagerAndroid,
            String guid);
    private native void nativeSetCreditCardUseStatsForTesting(
            long nativePersonalDataManagerAndroid, String guid, int count, long date);
    private native int nativeGetCreditCardUseCountForTesting(long nativePersonalDataManagerAndroid,
            String guid);
    private native long nativeGetCreditCardUseDateForTesting(long nativePersonalDataManagerAndroid,
            String guid);
    private native long nativeGetCurrentDateForTesting(long nativePersonalDataManagerAndroid);
    private native void nativeClearUnmaskedCache(
            long nativePersonalDataManagerAndroid, String guid);
    private native void nativeGetFullCardForPaymentRequest(long nativePersonalDataManagerAndroid,
            WebContents webContents, CreditCard card, FullCardRequestDelegate delegate);
    private native void nativeLoadRulesForRegion(
            long nativePersonalDataManagerAndroid, String regionCode);
    private native boolean nativeStartAddressNormalization(long nativePersonalDataManagerAndroid,
            String guid, String regionCode, NormalizedAddressRequestDelegate delegate);
    private native void nativeCancelPendingAddressNormalization(
            long nativePersonalDataManagerAndroid);
    private static native boolean nativeIsAutofillEnabled();
    private static native void nativeSetAutofillEnabled(boolean enable);
    private static native boolean nativeIsAutofillManaged();
    private static native boolean nativeIsPaymentsIntegrationEnabled();
    private static native void nativeSetPaymentsIntegrationEnabled(boolean enable);
    private static native String nativeToCountryCode(String countryName);
}
