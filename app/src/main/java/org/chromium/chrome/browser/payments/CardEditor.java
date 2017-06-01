// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.CreditCardScanner;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.chrome.browser.payments.PaymentRequestImpl.PaymentRequestServiceObserverForTest;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel.EditorFieldValidator;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel.EditorValueIconGenerator;
import org.chromium.chrome.browser.payments.ui.EditorModel;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.DropdownKeyValue;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

/**
 * A credit card editor. Can be used for editing both local and server credit cards. Everything in
 * local cards can be edited. For server cards, only the billing address is editable.
 */
public class CardEditor extends EditorBase<AutofillPaymentInstrument>
        implements CreditCardScanner.Delegate {
    /** Description of a card type. */
    private static class CardTypeInfo {
        /** The identifier for the drawable resource of the card type, e.g., R.drawable.pr_visa. */
        public final int icon;

        /**
         * The identifier for the localized description string for accessibility, e.g.,
         * R.string.autofill_cc_visa.
         */
        public final int description;

        /**
         * Builds a description of a card type.
         *
         * @param icon        The identifier for the drawable resource of the card type.
         * @param description The identifier for the localized description string for accessibility.
         */
        public CardTypeInfo(int icon, int description) {
            this.icon = icon;
            this.description = description;
        }
    }

    /** The dropdown key that triggers the address editor to add a new billing address. */
    private static final String BILLING_ADDRESS_ADD_NEW = "add";

    /** The shared preference for the 'save card to device' checkbox status*/
    private static final String CHECK_SAVE_CARD_TO_DEVICE = "check_save_card_to_device";

    /** The web contents where the web payments API is invoked. */
    private final WebContents mWebContents;

    /**
     * The list of profiles that can be used for billing address. This cache avoids re-reading
     * profiles from disk, which may have changed due to sync, for example. updateBillingAddress()
     * updates this cache.
     */
    private final List<AutofillProfile> mProfilesForBillingAddress;

    /** Used for verifying billing address completeness and also editing billing addresses. */
    private final AddressEditor mAddressEditor;

    /** An optional observer used by tests. */
    @Nullable private final PaymentRequestServiceObserverForTest mObserverForTest;

    /**
     * A mapping from all card types recognized in Chrome to information about these card types. The
     * card types (e.g., "visa") are defined in:
     * https://w3c.github.io/webpayments-methods-card/#method-id
     */
    private final Map<String, CardTypeInfo> mCardTypes;

    /**
     * The card types accepted by the merchant website. This is a subset of recognized cards. Used
     * in the validator.
     */
    private final Set<String> mAcceptedCardTypes;

    /**
     * The information about the accepted card types. Used in the editor as a hint to the user about
     * the valid card types. This is important to keep in a list, because the display order matters.
     */
    private final List<CardTypeInfo> mAcceptedCardTypeInfos;

    private final Handler mHandler;
    private final EditorFieldValidator mCardNumberValidator;
    private final EditorValueIconGenerator mCardIconGenerator;
    private final AsyncTask<Void, Void, Calendar> mCalendar;

    @Nullable private EditorFieldModel mIconHint;
    @Nullable private EditorFieldModel mNumberField;
    @Nullable private EditorFieldModel mNameField;
    @Nullable private EditorFieldModel mMonthField;
    @Nullable private EditorFieldModel mYearField;
    @Nullable private EditorFieldModel mBillingAddressField;
    @Nullable private EditorFieldModel mSaveCardCheckbox;
    @Nullable private CreditCardScanner mCardScanner;
    @Nullable private EditorFieldValidator mCardExpirationMonthValidator;
    private boolean mCanScan;
    private boolean mIsScanning;
    private int mCurrentMonth;
    private int mCurrentYear;

    /**
     * Builds a credit card editor.
     *
     * @param webContents     The web contents where the web payments API is invoked.
     * @param addressEditor   Used for verifying billing address completeness and also editing
     *                        billing addresses.
     * @param observerForTest Optional observer for test.
     */
    public CardEditor(WebContents webContents, AddressEditor addressEditor,
            @Nullable PaymentRequestServiceObserverForTest observerForTest) {
        assert webContents != null;
        assert addressEditor != null;

        mWebContents = webContents;
        mAddressEditor = addressEditor;
        mObserverForTest = observerForTest;

        List<AutofillProfile> profiles =
                PersonalDataManager.getInstance().getBillingAddressesToSuggest();
        mProfilesForBillingAddress = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            AutofillProfile profile = profiles.get(i);
            // 1) Include only local profiles, because GUIDs of server profiles change on every
            //    browser restart. Server profiles are not supported as billing addresses.
            // 2) Include only complete profiles, so that user launches the editor only when
            //    explicitly selecting [+ ADD ADDRESS] in the dropdown.
            if (profile.getIsLocal()
                    && AutofillAddress.checkAddressCompletionStatus(profile)
                            == AutofillAddress.COMPLETE) {
                mProfilesForBillingAddress.add(profile);
            }
        }

        mCardTypes = new HashMap<>();
        mCardTypes.put("amex",
                new CardTypeInfo(R.drawable.pr_amex, R.string.autofill_cc_amex));
        mCardTypes.put("diners",
                new CardTypeInfo(R.drawable.pr_dinersclub, R.string.autofill_cc_diners));
        mCardTypes.put("discover",
                new CardTypeInfo(R.drawable.pr_discover, R.string.autofill_cc_discover));
        mCardTypes.put("jcb",
                new CardTypeInfo(R.drawable.pr_jcb, R.string.autofill_cc_jcb));
        mCardTypes.put("mastercard",
                new CardTypeInfo(R.drawable.pr_mc, R.string.autofill_cc_mastercard));
        mCardTypes.put("unionpay",
                new CardTypeInfo(R.drawable.pr_unionpay, R.string.autofill_cc_union_pay));
        mCardTypes.put("visa",
                new CardTypeInfo(R.drawable.pr_visa, R.string.autofill_cc_visa));

        mAcceptedCardTypes = new HashSet<>();
        mAcceptedCardTypeInfos = new ArrayList<>();
        mHandler = new Handler();

        mCardNumberValidator = new EditorFieldValidator() {
            @Override
            public boolean isValid(@Nullable CharSequence value) {
                return value != null
                        && mAcceptedCardTypes.contains(
                                   PersonalDataManager.getInstance().getBasicCardPaymentType(
                                           value.toString(), true));
            }
        };

        mCardIconGenerator = new EditorValueIconGenerator() {
            @Override
            public int getIconResourceId(@Nullable CharSequence value) {
                if (value == null) return 0;
                CardTypeInfo cardTypeInfo =
                        mCardTypes.get(PersonalDataManager.getInstance().getBasicCardPaymentType(
                                value.toString(), false));
                if (cardTypeInfo == null) return 0;
                return cardTypeInfo.icon;
            }
        };

        mCalendar = new AsyncTask<Void, Void, Calendar>() {
            @Override
            protected Calendar doInBackground(Void... unused) {
                return Calendar.getInstance();
            }
        };
        mCalendar.execute();
    }

    /**
     * Adds accepted payment methods to the editor, if they are recognized credit card types.
     *
     * @param acceptedMethods The accepted method payments.
     */
    public void addAcceptedPaymentMethodsIfRecognized(String[] acceptedMethods) {
        assert acceptedMethods != null;
        for (int i = 0; i < acceptedMethods.length; i++) {
            String method = acceptedMethods[i];
            if (mCardTypes.containsKey(method)) {
                assert !mAcceptedCardTypes.contains(method);
                mAcceptedCardTypes.add(method);
                mAcceptedCardTypeInfos.add(mCardTypes.get(method));
            }
        }
    }

    /**
     * Builds and shows an editor model with the following fields for local cards.
     *
     * [ accepted card types hint images     ]
     * [ card number                         ]
     * [ name on card                        ]
     * [ expiration month ][ expiration year ]
     * [ billing address dropdown            ]
     * [ save this card checkbox             ] <-- Shown only for new cards.
     *
     * Server cards have the following fields instead.
     *
     * [ card's obfuscated number            ]
     * [ billing address dropdown            ]
     */
    @Override
    public void edit(@Nullable final AutofillPaymentInstrument toEdit,
            final Callback<AutofillPaymentInstrument> callback) {
        super.edit(toEdit, callback);

        // If |toEdit| is null, we're creating a new credit card.
        final boolean isNewCard = toEdit == null;

        // Ensure that |instrument| and |card| are never null.
        final AutofillPaymentInstrument instrument = isNewCard
                ? new AutofillPaymentInstrument(mContext, mWebContents, new CreditCard(), null)
                : toEdit;
        final CreditCard card = instrument.getCard();

        final EditorModel editor = new EditorModel(
                isNewCard ? mContext.getString(R.string.payments_add_card) : toEdit.getEditTitle());

        if (card.getIsLocal()) {
            Calendar calendar = null;
            try {
                calendar = mCalendar.get();
            } catch (InterruptedException | ExecutionException e) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(null);
                    }
                });
                return;
            }
            assert calendar != null;

            // Let user edit any part of the local card.
            addLocalCardInputs(editor, card, calendar);
        } else {
            // Display some information about the server card.
            editor.addField(EditorFieldModel.createLabel(card.getObfuscatedNumber(), card.getName(),
                    mContext.getString(R.string.payments_credit_card_expiration_date_abbr,
                            card.getMonth(), card.getYear()),
                    card.getIssuerIconDrawableId()));
        }

        // Always show the billing address dropdown.
        addBillingAddressDropdown(editor, card);

        // Allow saving new cards on disk.
        if (isNewCard) addSaveCardCheckbox(editor);

        // If the user clicks [Cancel], send a null card back to the caller.
        editor.setCancelCallback(new Runnable() {
            @Override
            public void run() {
                callback.onResult(null);
            }
        });

        // If the user clicks [Done], save changes on disk, mark the card "complete," and send it
        // back to the caller.
        editor.setDoneCallback(new Runnable() {
            @Override
            public void run() {
                commitChanges(card, isNewCard);
                for (int i = 0; i < mProfilesForBillingAddress.size(); ++i) {
                    if (TextUtils.equals(mProfilesForBillingAddress.get(i).getGUID(),
                            card.getBillingAddressId())) {
                        instrument.completeInstrument(card, mProfilesForBillingAddress.get(i));
                        break;
                    }
                }
                callback.onResult(instrument);
            }
        });

        mEditorView.show(editor);
    }

    /**
     * Adds the given billing address to the list of billing addresses, if it's complete. If the
     * address is already known, then updates the existing address. Should be called before opening
     * the card editor.
     *
     * @param billingAddress The billing address to add or update. Should not be null.
     */
    public void updateBillingAddressIfComplete(AutofillAddress billingAddress) {
        if (!billingAddress.isComplete()) return;

        for (int i = 0; i < mProfilesForBillingAddress.size(); ++i) {
            if (TextUtils.equals(mProfilesForBillingAddress.get(i).getGUID(),
                        billingAddress.getIdentifier())) {
                mProfilesForBillingAddress.set(i, billingAddress.getProfile());
                return;
            }
        }

        // No matching profile was found. Add the new profile at the top of the list.
        mProfilesForBillingAddress.add(0, billingAddress.getProfile());
    }

    /**
     * Adds the following fields to the editor.
     *
     * [ accepted card types hint images     ]
     * [ card number              [ocr icon] ]
     * [ name on card                        ]
     * [ expiration month ][ expiration year ]
     */
    private void addLocalCardInputs(EditorModel editor, CreditCard card, Calendar calendar) {
        // Local card editor shows a card icon hint.
        if (mIconHint == null) {
            List<Integer> icons = new ArrayList<>();
            List<Integer> descriptions = new ArrayList<>();
            for (int i = 0; i < mAcceptedCardTypeInfos.size(); i++) {
                icons.add(mAcceptedCardTypeInfos.get(i).icon);
                descriptions.add(mAcceptedCardTypeInfos.get(i).description);
            }
            mIconHint = EditorFieldModel.createIconList(
                    mContext.getString(R.string.payments_accepted_cards_label), icons,
                    descriptions);
        }
        editor.addField(mIconHint);

        // Card scanner is expensive to query.
        if (mCardScanner == null) {
            mCardScanner = CreditCardScanner.create(mContext,
                    ContentViewCore.fromWebContents(mWebContents).getWindowAndroid(),
                    this);
            mCanScan = mCardScanner.canScan();
        }

        // Card number is validated.
        if (mNumberField == null) {
            mNumberField = EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_CREDIT_CARD,
                    mContext.getString(R.string.autofill_credit_card_editor_number), null,
                    mCardNumberValidator, mCardIconGenerator,
                    mContext.getString(R.string.payments_field_required_validation_message),
                    mContext.getString(R.string.payments_card_number_invalid_validation_message),
                    null);
            if (mCanScan) {
                mNumberField.addActionIcon(R.drawable.ic_photo_camera,
                        R.string.autofill_scan_credit_card, new Runnable() {
                            @Override
                            public void run() {
                                if (mIsScanning) return;
                                mIsScanning = true;
                                mCardScanner.scan();
                            }
                        });
            }
        }
        mNumberField.setValue(card.getNumber());
        editor.addField(mNumberField);

        // Name on card is required.
        if (mNameField == null) {
            mNameField = EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_PERSON_NAME,
                    mContext.getString(R.string.autofill_credit_card_editor_name), null, null, null,
                    mContext.getString(R.string.payments_field_required_validation_message), null,
                    null);
        }
        mNameField.setValue(card.getName());
        editor.addField(mNameField);

        // Expiration month dropdown.
        if (mMonthField == null) {
            mCurrentYear = calendar.get(Calendar.YEAR);
            // The month in calendar is 0 based but the month value is 1 based.
            mCurrentMonth = calendar.get(Calendar.MONTH) + 1;

            if (mCardExpirationMonthValidator == null) {
                mCardExpirationMonthValidator = new EditorFieldValidator() {
                    @Override
                    public boolean isValid(@Nullable CharSequence monthValue) {
                        CharSequence yearValue = mYearField.getValue();
                        if (monthValue == null || yearValue == null) return false;

                        int month = Integer.parseInt(monthValue.toString());
                        int year = Integer.parseInt(yearValue.toString());

                        return year > mCurrentYear
                              || (year == mCurrentYear && month >= mCurrentMonth);
                    }
                };
            }

            mMonthField = EditorFieldModel.createDropdown(
                    mContext.getString(R.string.autofill_credit_card_editor_expiration_date),
                    buildMonthDropdownKeyValues(calendar),
                    mCardExpirationMonthValidator,
                    mContext.getString(
                          R.string.payments_card_expiration_invalid_validation_message));
            mMonthField.setIsFullLine(false);

            if (mObserverForTest != null) {
                mMonthField.setDropdownCallback(new Callback<Pair<String, Runnable>>() {
                    @Override
                    public void onResult(final Pair<String, Runnable> eventData) {
                        mObserverForTest.onPaymentRequestServiceExpirationMonthChange();
                    }
                });
            }
        }
        if (mMonthField.getDropdownKeys().contains(card.getMonth())) {
            mMonthField.setValue(card.getMonth());
        } else {
            mMonthField.setValue(mMonthField.getDropdownKeyValues().get(0).getKey());
        }
        editor.addField(mMonthField);

        // Expiration year dropdown is side-by-side with the expiration year dropdown. The dropdown
        // should include the card's expiration year, so it's not cached.
        mYearField = EditorFieldModel.createDropdown(
                null /* label */, buildYearDropdownKeyValues(calendar, card.getYear()),
                null /* hint */);
        mYearField.setIsFullLine(false);
        if (mYearField.getDropdownKeys().contains(card.getYear())) {
            mYearField.setValue(card.getYear());
        } else {
            mYearField.setValue(mYearField.getDropdownKeyValues().get(0).getKey());
        }
        editor.addField(mYearField);
    }

    /** Builds the key-value pairs for the month dropdown. */
    private static List<DropdownKeyValue> buildMonthDropdownKeyValues(Calendar calendar) {
        List<DropdownKeyValue> result = new ArrayList<>();

        Locale locale = Locale.getDefault();
        SimpleDateFormat keyFormatter = new SimpleDateFormat("M", locale);
        SimpleDateFormat valueFormatter = new SimpleDateFormat("MMMM (MM)", locale);

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        for (int month = 0; month < 12; month++) {
            calendar.set(Calendar.MONTH, month);
            Date date = calendar.getTime();
            result.add(
                    new DropdownKeyValue(keyFormatter.format(date), valueFormatter.format(date)));
        }

        return result;
    }

    /** Builds the key-value pairs for the year dropdown. */
    private static List<DropdownKeyValue> buildYearDropdownKeyValues(
            Calendar calendar, String alwaysIncludedYear) {
        List<DropdownKeyValue> result = new ArrayList<>();

        int initialYear = calendar.get(Calendar.YEAR);
        boolean foundAlwaysIncludedYear = false;
        for (int year = initialYear; year < initialYear + 10; year++) {
            String yearString = Integer.toString(year);
            if (yearString.equals(alwaysIncludedYear)) foundAlwaysIncludedYear = true;
            result.add(new DropdownKeyValue(yearString, yearString));
        }

        if (!foundAlwaysIncludedYear && !TextUtils.isEmpty(alwaysIncludedYear)) {
            result.add(0, new DropdownKeyValue(alwaysIncludedYear, alwaysIncludedYear));
        }

        return result;
    }

    /**
     * Adds the billing address dropdown to the editor with the following items.
     *
     * | "select"           |
     * | complete address 1 |
     * | complete address 2 |
     *      ...
     * | complete address n |
     * | "add address"      |
     */
    private void addBillingAddressDropdown(EditorModel editor, final CreditCard card) {
        final List<DropdownKeyValue> billingAddresses = new ArrayList<>();

        for (int i = 0; i < mProfilesForBillingAddress.size(); ++i) {
            billingAddresses.add(new DropdownKeyValue(mProfilesForBillingAddress.get(i).getGUID(),
                    mProfilesForBillingAddress.get(i).getLabel()));
        }

        billingAddresses.add(new DropdownKeyValue(BILLING_ADDRESS_ADD_NEW,
                mContext.getString(R.string.autofill_create_profile)));

        // Don't cache the billing address dropdown, because the user may have added or removed
        // profiles. Also pass the "Select" dropdown item as a hint to the dropdown constructor.
        mBillingAddressField = EditorFieldModel.createDropdown(
                mContext.getString(R.string.autofill_credit_card_editor_billing_address),
                billingAddresses, mContext.getString(R.string.select));

        // The billing address is required.
        mBillingAddressField.setRequiredErrorMessage(
                mContext.getString(R.string.payments_field_required_validation_message));

        mBillingAddressField.setDropdownCallback(new Callback<Pair<String, Runnable>>() {
            @Override
            public void onResult(final Pair<String, Runnable> eventData) {
                if (!BILLING_ADDRESS_ADD_NEW.equals(eventData.first)) {
                    if (mObserverForTest != null) {
                        mObserverForTest.onPaymentRequestServiceBillingAddressChangeProcessed();
                    }
                    return;
                }

                mAddressEditor.edit(null, new Callback<AutofillAddress>() {
                    @Override
                    public void onResult(AutofillAddress billingAddress) {
                        if (billingAddress == null) {
                            // User has cancelled the address editor.
                            mBillingAddressField.setValue(null);
                        } else {
                            // User has added a new complete address. Add it to the top of the
                            // dropdown.
                            mProfilesForBillingAddress.add(billingAddress.getProfile());
                            billingAddresses.add(
                                    0, new DropdownKeyValue(billingAddress.getIdentifier(),
                                               billingAddress.getSublabel()));
                            mBillingAddressField.setDropdownKeyValues(billingAddresses);
                            mBillingAddressField.setValue(billingAddress.getIdentifier());
                        }

                        // Let the card editor UI re-read the model and re-create UI elements.
                        mHandler.post(eventData.second);
                    }
                });
            }
        });

        if (mBillingAddressField.getDropdownKeys().contains(card.getBillingAddressId())) {
            mBillingAddressField.setValue(card.getBillingAddressId());
        }

        editor.addField(mBillingAddressField);
    }

    /** Adds the "save this card" checkbox to the editor. */
    private void addSaveCardCheckbox(EditorModel editor) {
        if (mSaveCardCheckbox == null) {
            mSaveCardCheckbox = EditorFieldModel.createCheckbox(
                    mContext.getString(R.string.payments_save_card_to_device_checkbox),
                    CHECK_SAVE_CARD_TO_DEVICE);
        }
        editor.addField(mSaveCardCheckbox);
    }

    /**
     * Saves the edited credit card.
     *
     * If this is a server card, then only its billing address identifier is updated.
     *
     * If this is a new local card, then it's saved on this device only if the user has checked the
     * "save this card" checkbox.
     */
    private void commitChanges(CreditCard card, boolean isNewCard) {
        card.setBillingAddressId(mBillingAddressField.getValue().toString());

        PersonalDataManager pdm = PersonalDataManager.getInstance();
        if (!card.getIsLocal()) {
            pdm.updateServerCardBillingAddress(card.getServerId(), card.getBillingAddressId());
            return;
        }

        card.setNumber(mNumberField.getValue().toString().replace(" ", "").replace("-", ""));
        card.setName(mNameField.getValue().toString());
        card.setMonth(mMonthField.getValue().toString());
        card.setYear(mYearField.getValue().toString());

        // Calculate the basic card payment type, obfuscated number, and the icon for this card.
        // All of these depend on the card number. The type is sent to the merchant website. The
        // obfuscated number and the icon are displayed in the user interface.
        CreditCard displayableCard = pdm.getCreditCardForNumber(card.getNumber());
        card.setBasicCardPaymentType(displayableCard.getBasicCardPaymentType());
        card.setObfuscatedNumber(displayableCard.getObfuscatedNumber());
        card.setIssuerIconDrawableId(displayableCard.getIssuerIconDrawableId());

        if (!isNewCard) {
            pdm.setCreditCard(card);
            return;
        }

        if (mSaveCardCheckbox != null && mSaveCardCheckbox.isChecked()) {
            card.setGUID(pdm.setCreditCard(card));
        }
    }

    @Override
    public void onScanCompleted(
            String cardHolderName, String cardNumber, int expirationMonth, int expirationYear) {
        if (!TextUtils.isEmpty(cardHolderName)) mNameField.setValue(cardHolderName);
        if (!TextUtils.isEmpty(cardNumber)) mNumberField.setValue(cardNumber);
        if (expirationYear >= 2000) mYearField.setValue(Integer.toString(expirationYear));

        if (expirationMonth >= 1 && expirationMonth <= 12) {
            mMonthField.setValue(Integer.toString(expirationMonth));
        }

        mEditorView.update();
        mIsScanning = false;
    }

    @Override
    public void onScanCancelled() {
        mIsScanning = false;
    }
}
