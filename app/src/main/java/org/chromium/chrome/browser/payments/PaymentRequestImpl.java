// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.NormalizedAddressRequestDelegate;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.payments.ui.Completable;
import org.chromium.chrome.browser.payments.ui.LineItem;
import org.chromium.chrome.browser.payments.ui.PaymentInformation;
import org.chromium.chrome.browser.payments.ui.PaymentOption;
import org.chromium.chrome.browser.payments.ui.PaymentRequestUI;
import org.chromium.chrome.browser.payments.ui.SectionInformation;
import org.chromium.chrome.browser.payments.ui.ShoppingCart;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.components.safejson.JsonSanitizer;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.WebContents;
import org.chromium.mojo.system.MojoException;
import org.chromium.payments.mojom.PaymentComplete;
import org.chromium.payments.mojom.PaymentDetails;
import org.chromium.payments.mojom.PaymentErrorReason;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;
import org.chromium.payments.mojom.PaymentOptions;
import org.chromium.payments.mojom.PaymentRequest;
import org.chromium.payments.mojom.PaymentRequestClient;
import org.chromium.payments.mojom.PaymentResponse;
import org.chromium.payments.mojom.PaymentShippingOption;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Android implementation of the PaymentRequest service defined in
 * third_party/WebKit/public/platform/modules/payments/payment_request.mojom.
 */
public class PaymentRequestImpl implements PaymentRequest, PaymentRequestUI.Client,
        PaymentApp.InstrumentsCallback, PaymentInstrument.DetailsCallback,
        NormalizedAddressRequestDelegate {
    /**
     * Observer to be notified when PaymentRequest UI has been dismissed.
     */
    public interface PaymentRequestDismissObserver {
        /**
         * Called when PaymentRequest UI has been dismissed.
         */
        void onPaymentRequestDismissed();
    }

    /**
     * A test-only observer for the PaymentRequest service implementation.
     */
    public interface PaymentRequestServiceObserverForTest {
        /**
         * Called when an abort request was denied.
         */
        void onPaymentRequestServiceUnableToAbort();

        /**
         * Called when the controller is notified of billing address change, but does not alter the
         * editor UI.
         */
        void onPaymentRequestServiceBillingAddressChangeProcessed();

        /**
         * Called when the controller is notified of an expiration month change.
         */
        void onPaymentRequestServiceExpirationMonthChange();

        /**
         * Called when a show request failed. This can happen when:
         * <ul>
         *   <li>The merchant requests only unsupported payment methods.</li>
         *   <li>The merchant requests only payment methods that don't have instruments and are not
         *       able to add instruments from PaymentRequest UI.</li>
         * </ul>
         */
        void onPaymentRequestServiceShowFailed();
    }

    private static final String TAG = "cr_PaymentRequest";
    private static final String ANDROID_PAY_METHOD_NAME = "https://android.com/pay";
    private static final int SUGGESTIONS_LIMIT = 4;
    private static final Comparator<Completable> COMPLETENESS_COMPARATOR =
            new Comparator<Completable>() {
                @Override
                public int compare(Completable a, Completable b) {
                    return (b.isComplete() ? 1 : 0) - (a.isComplete() ? 1 : 0);
                }
            };

    private static PaymentRequestServiceObserverForTest sObserverForTest;

    /** Monitors changes in the TabModelSelector. */
    private final TabModelSelectorObserver mSelectorObserver = new EmptyTabModelSelectorObserver() {
        @Override
        public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
            onDismiss();
        }
    };

    /** Monitors changes in the current TabModel. */
    private final TabModelObserver mTabModelObserver = new EmptyTabModelObserver() {
        @Override
        public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
            if (tab == null || tab.getId() != lastId) onDismiss();
        }
    };

    private final Handler mHandler = new Handler();
    private final ChromeActivity mContext;
    private final PaymentRequestDismissObserver mDismissObserver;
    private final String mMerchantName;
    private final String mOrigin;
    private final List<PaymentApp> mApps;
    private final AddressEditor mAddressEditor;
    private final CardEditor mCardEditor;
    private final PaymentRequestJourneyLogger mJourneyLogger = new PaymentRequestJourneyLogger();

    private Bitmap mFavicon;
    private PaymentRequestClient mClient;

    /**
     * The raw total amount being charged, as it was received from the website. This data is passed
     * to the payment app.
     */
    private PaymentItem mRawTotal;

    /**
     * The raw items in the shopping cart, as they were received from the website. This data is
     * passed to the payment app.
     */
    private List<PaymentItem> mRawLineItems;

    /**
     * The UI model of the shopping cart, including the total. Each item includes a label and a
     * price string. This data is passed to the UI.
     */
    private ShoppingCart mUiShoppingCart;

    /**
     * The UI model for the shipping options. Includes the label and sublabel for each shipping
     * option. Also keeps track of the selected shipping option. This data is passed to the UI.
     */
    private SectionInformation mUiShippingOptions;

    private Map<String, JSONObject> mMethodData;
    private SectionInformation mShippingAddressesSection;
    private SectionInformation mContactSection;
    private List<PaymentApp> mPendingApps;
    private List<PaymentInstrument> mPendingInstruments;
    private List<PaymentInstrument> mPendingAutofillInstruments;
    private SectionInformation mPaymentMethodsSection;
    private PaymentRequestUI mUI;
    private Callback<PaymentInformation> mPaymentInformationCallback;
    private boolean mPaymentAppRunning;
    private boolean mMerchantSupportsAutofillPaymentInstruments;
    private ContactEditor mContactEditor;
    private boolean mHasRecordedAbortReason;

    /** True if any of the requested payment methods are supported. */
    private boolean mArePaymentMethodsSupported;

    /** True if show() was called. */
    private boolean mIsShowing;

    private boolean mIsWaitingForNormalization;
    private PaymentResponse mPendingPaymentResponse;

    /**
     * Builds the PaymentRequest service implementation.
     *
     * @param context         The context where PaymentRequest has been invoked.
     * @param webContents     The web contents that have invoked the PaymentRequest API.
     * @param dismissObserver The observer to notify when PaymentRequest UI has been dismissed.
     */
    public PaymentRequestImpl(Activity context, WebContents webContents,
            PaymentRequestDismissObserver dismissObserver) {
        assert context != null;
        assert webContents != null;
        assert dismissObserver != null;

        assert context instanceof ChromeActivity;
        mContext = (ChromeActivity) context;

        mDismissObserver = dismissObserver;
        mMerchantName = webContents.getTitle();
        // The feature is available only in secure context, so it's OK to not show HTTPS.
        mOrigin = UrlFormatter.formatUrlForSecurityDisplay(webContents.getVisibleUrl(), false);

        final FaviconHelper faviconHelper = new FaviconHelper();
        faviconHelper.getLocalFaviconImageForURL(Profile.getLastUsedProfile(),
                webContents.getVisibleUrl(),
                mContext.getResources().getDimensionPixelSize(R.dimen.payments_favicon_size),
                new FaviconHelper.FaviconImageCallback() {
                    @Override
                    public void onFaviconAvailable(Bitmap bitmap, String iconUrl) {
                        faviconHelper.destroy();
                        if (bitmap == null) return;
                        if (mUI == null) {
                            mFavicon = bitmap;
                            return;
                        }
                        mUI.setTitleBitmap(bitmap);
                    }
                });

        mApps = PaymentAppFactory.create(webContents);

        mAddressEditor = new AddressEditor();
        mCardEditor = new CardEditor(webContents, mAddressEditor, sObserverForTest);

        recordSuccessFunnelHistograms("Initiated");
    }

    /**
     * Called by the merchant website to initialize the payment request data.
     */
    @Override
    public void init(PaymentRequestClient client, PaymentMethodData[] methodData,
            PaymentDetails details, PaymentOptions options) {
        if (mClient != null || client == null) return;
        mClient = client;

        if (mMethodData != null) {
            disconnectFromClientWithDebugMessage("PaymentRequest.show() called more than once.");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return;
        }

        mMethodData = getValidatedMethodData(methodData, mCardEditor);
        if (mMethodData == null) {
            disconnectFromClientWithDebugMessage("Invalid payment methods or data");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return;
        }

        if (!parseAndValidateDetailsOrDisconnectFromClient(details)) return;

        getMatchingPaymentInstruments();

        boolean requestShipping = options != null && options.requestShipping;
        boolean requestPayerPhone = options != null && options.requestPayerPhone;
        boolean requestPayerEmail = options != null && options.requestPayerEmail;

        List<AutofillProfile> profiles = null;
        if (requestShipping || requestPayerPhone || requestPayerEmail) {
            profiles = PersonalDataManager.getInstance().getProfilesToSuggest(
                    false /* includeName */);
        }

        if (requestShipping) {
            List<AutofillAddress> addresses = new ArrayList<>();

            for (int i = 0; i < profiles.size(); i++) {
                AutofillProfile profile = profiles.get(i);
                mAddressEditor.addPhoneNumberIfValid(profile.getPhoneNumber());

                // Only suggest addresses that have a street address.
                if (!TextUtils.isEmpty(profile.getStreetAddress())) {
                    boolean isComplete = mAddressEditor.isProfileComplete(profile);
                    addresses.add(new AutofillAddress(profile, isComplete));
                }
            }

            // Suggest complete addresses first.
            Collections.sort(addresses, COMPLETENESS_COMPARATOR);

            // Limit the number of suggestions.
            addresses = addresses.subList(0, Math.min(addresses.size(), SUGGESTIONS_LIMIT));

            // Load the validation rules for each unique region code.
            Set<String> uniqueCountryCodes = new HashSet<>();
            for (int i = 0; i < addresses.size(); ++i) {
                String countryCode = AutofillAddress.getCountryCode(addresses.get(i).getProfile());
                if (!uniqueCountryCodes.contains(countryCode)) {
                    uniqueCountryCodes.add(countryCode);
                    PersonalDataManager.getInstance().loadRulesForRegion(countryCode);
                }
            }

            // Log the number of suggested shipping addresses.
            mJourneyLogger.setNumberOfSuggestionsShown(
                    PaymentRequestJourneyLogger.SECTION_SHIPPING_ADDRESS, addresses.size());

            // Automatically select the first address if one is complete and if the merchant does
            // not require a shipping address to calculate shipping costs.
            int firstCompleteAddressIndex = SectionInformation.NO_SELECTION;
            if (mUiShippingOptions.getSelectedItem() != null && !addresses.isEmpty()
                    && addresses.get(0).isComplete()) {
                firstCompleteAddressIndex = 0;
            }

            mShippingAddressesSection =
                    new SectionInformation(PaymentRequestUI.TYPE_SHIPPING_ADDRESSES,
                            firstCompleteAddressIndex, addresses);
        }

        if (requestPayerPhone || requestPayerEmail) {
            Set<String> uniqueContactInfos = new HashSet<>();
            mContactEditor = new ContactEditor(requestPayerPhone, requestPayerEmail);
            List<AutofillContact> contacts = new ArrayList<>();

            for (int i = 0; i < profiles.size(); i++) {
                AutofillProfile profile = profiles.get(i);
                String phone = requestPayerPhone && !TextUtils.isEmpty(profile.getPhoneNumber())
                        ? profile.getPhoneNumber() : null;
                String email = requestPayerEmail && !TextUtils.isEmpty(profile.getEmailAddress())
                        ? profile.getEmailAddress() : null;
                mContactEditor.addPhoneNumberIfValid(phone);
                mContactEditor.addEmailAddressIfValid(email);

                if (phone != null || email != null) {
                    // Different profiles can have identical contact info. Do not add the same
                    // contact info to the list twice.
                    String uniqueContactInfo = phone + email;
                    if (!uniqueContactInfos.contains(uniqueContactInfo)) {
                        uniqueContactInfos.add(uniqueContactInfo);

                        boolean isComplete =
                                mContactEditor.isContactInformationComplete(phone, email);
                        contacts.add(new AutofillContact(profile, phone, email, isComplete));
                    }
                }
            }

            // Suggest complete contact infos first.
            Collections.sort(contacts, COMPLETENESS_COMPARATOR);

            // Limit the number of suggestions.
            contacts = contacts.subList(0, Math.min(contacts.size(), SUGGESTIONS_LIMIT));

            // Log the number of suggested contact infos.
            mJourneyLogger.setNumberOfSuggestionsShown(
                    PaymentRequestJourneyLogger.SECTION_CONTACT_INFO, contacts.size());

            // Automatically select the first address if it is complete.
            int firstCompleteContactIndex = SectionInformation.NO_SELECTION;
            if (!contacts.isEmpty() && contacts.get(0).isComplete()) {
                firstCompleteContactIndex = 0;
            }

            mContactSection = new SectionInformation(
                    PaymentRequestUI.TYPE_CONTACT_DETAILS, firstCompleteContactIndex, contacts);
        }

        mUI = new PaymentRequestUI(mContext, this, requestShipping,
                requestPayerPhone || requestPayerEmail, mMerchantSupportsAutofillPaymentInstruments,
                mMerchantName, mOrigin);

        if (mFavicon != null) mUI.setTitleBitmap(mFavicon);
        mFavicon = null;

        mAddressEditor.setEditorView(mUI.getEditorView());
        mCardEditor.setEditorView(mUI.getCardEditorView());
        if (mContactEditor != null) mContactEditor.setEditorView(mUI.getEditorView());

        PaymentRequestMetrics.recordRequestedInformationHistogram(requestPayerEmail,
                requestPayerPhone, requestShipping);
    }

    /**
     * Called by the merchant website to show the payment request to the user.
     */
    @Override
    public void show() {
        if (mClient == null || mIsShowing) return;

        mIsShowing = true;
        if (disconnectIfNoPaymentMethodsSupported()) return;

        // Catch any time the user switches tabs.  Because the dialog is modal, a user shouldn't be
        // allowed to switch tabs, which can happen if the user receives an external Intent.
        mContext.getTabModelSelector().addObserver(mSelectorObserver);
        mContext.getCurrentTabModel().addObserver(mTabModelObserver);

        mUI.show();
        recordSuccessFunnelHistograms("Shown");
    }

    private static Map<String, JSONObject> getValidatedMethodData(
            PaymentMethodData[] methodData, CardEditor paymentMethodsCollector) {
        // Payment methodData are required.
        if (methodData == null || methodData.length == 0) return null;
        Map<String, JSONObject> result = new HashMap<>();
        for (int i = 0; i < methodData.length; i++) {
            JSONObject data = null;
            if (!TextUtils.isEmpty(methodData[i].stringifiedData)) {
                try {
                    data = new JSONObject(JsonSanitizer.sanitize(methodData[i].stringifiedData));
                } catch (JSONException | IOException | IllegalStateException e) {
                    // Payment method specific data should be a JSON object.
                    // According to the payment request spec[1], for each method data,
                    // if the data field is supplied but is not a JSON-serializable object,
                    // then should throw a TypeError. So, we should return null here even if
                    // only one is bad.
                    // [1] https://w3c.github.io/browser-payment-api/
                    return null;
                }
            }

            String[] methods = methodData[i].supportedMethods;

            // Payment methods are required.
            if (methods == null || methods.length == 0) return null;

            for (int j = 0; j < methods.length; j++) {
                // Payment methods should be non-empty.
                if (TextUtils.isEmpty(methods[j])) return null;
                result.put(methods[j], data);
            }

            paymentMethodsCollector.addAcceptedPaymentMethodsIfRecognized(methods);
        }
        return result;
    }

    /** Queries the installed payment apps for their instruments that merchant supports. */
    private void getMatchingPaymentInstruments() {
        mPendingApps = new ArrayList<>(mApps);
        mPendingInstruments = new ArrayList<>();
        mPendingAutofillInstruments = new ArrayList<>();

        Map<PaymentApp, JSONObject> queryApps = new HashMap<>();
        for (int i = 0; i < mApps.size(); i++) {
            PaymentApp app = mApps.get(i);
            Set<String> appMethods = app.getSupportedMethodNames();
            appMethods.retainAll(mMethodData.keySet());
            if (appMethods.isEmpty()) {
                mPendingApps.remove(app);
            } else {
                mArePaymentMethodsSupported = true;
                mMerchantSupportsAutofillPaymentInstruments |= app instanceof AutofillPaymentApp;
                queryApps.put(app, mMethodData.get(appMethods.iterator().next()));
            }
        }

        // Query instruments after mMerchantSupportsAutofillPaymentInstruments has been initialized,
        // so a fast response from a non-autofill payment app at the front of the app list does not
        // cause NOT_SUPPORTED payment rejection.
        for (Map.Entry<PaymentApp, JSONObject> q : queryApps.entrySet()) {
            q.getKey().getInstruments(q.getValue(), this);
        }
    }

    /**
     * Called by merchant to update the shipping options and line items after the user has selected
     * their shipping address or shipping option.
     */
    @Override
    public void updateWith(PaymentDetails details) {
        if (mClient == null) return;

        if (mUI == null) {
            disconnectFromClientWithDebugMessage(
                    "PaymentRequestUpdateEvent.updateWith() called without PaymentRequest.show()");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return;
        }

        if (!parseAndValidateDetailsOrDisconnectFromClient(details)) return;

        if (mUiShippingOptions.isEmpty() && mShippingAddressesSection.getSelectedItem() != null) {
            mShippingAddressesSection.getSelectedItem().setInvalid();
            mShippingAddressesSection.setSelectedItemIndex(SectionInformation.INVALID_SELECTION);
        }

        if (mPaymentInformationCallback != null) {
            providePaymentInformation();
        } else {
            mUI.updateOrderSummarySection(mUiShoppingCart);
            mUI.updateSection(PaymentRequestUI.TYPE_SHIPPING_OPTIONS, mUiShippingOptions);
        }
    }

    /**
     * Sets the total, display line items, and shipping options based on input and returns the
     * status boolean. That status is true for valid data, false for invalid data. If the input is
     * invalid, disconnects from the client. Both raw and UI versions of data are updated.
     *
     * @param details The total, line items, and shipping options to parse, validate, and save in
     *                member variables.
     * @return True if the data is valid. False if the data is invalid.
     */
    private boolean parseAndValidateDetailsOrDisconnectFromClient(PaymentDetails details) {
        if (details == null) {
            disconnectFromClientWithDebugMessage("Payment details required");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return false;
        }

        if (!hasAllPaymentItemFields(details.total)) {
            disconnectFromClientWithDebugMessage("Invalid total");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return false;
        }

        String totalCurrency = details.total.amount.currency;
        CurrencyStringFormatter formatter =
                new CurrencyStringFormatter(totalCurrency, Locale.getDefault());

        if (!formatter.isValidAmountCurrencyCode(details.total.amount.currency)) {
            disconnectFromClientWithDebugMessage("Invalid total amount currency");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return false;
        }

        if (!formatter.isValidAmountValue(details.total.amount.value)
                || details.total.amount.value.startsWith("-")) {
            disconnectFromClientWithDebugMessage("Invalid total amount value");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return false;
        }

        LineItem uiTotal = new LineItem(
                details.total.label, formatter.getFormattedCurrencyCode(),
                formatter.format(details.total.amount.value));

        List<LineItem> uiLineItems = getValidatedLineItems(details.displayItems, totalCurrency,
                formatter);
        if (uiLineItems == null) {
            disconnectFromClientWithDebugMessage("Invalid line items");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return false;
        }

        mUiShoppingCart = new ShoppingCart(uiTotal, uiLineItems);
        mRawTotal = details.total;
        mRawLineItems = Arrays.asList(details.displayItems);

        mUiShippingOptions = getValidatedShippingOptions(details.shippingOptions, totalCurrency,
                formatter);
        if (mUiShippingOptions == null) {
            disconnectFromClientWithDebugMessage("Invalid shipping options");
            recordAbortReasonHistogram(
                    PaymentRequestMetrics.ABORT_REASON_INVALID_DATA_FROM_RENDERER);
            return false;
        }

        return true;
    }

    /**
     * Returns true if all fields in the payment item are non-null and non-empty.
     *
     * @param item The payment item to examine.
     * @return True if all fields are present and non-empty.
     */
    private static boolean hasAllPaymentItemFields(PaymentItem item) {
        // "label", "currency", and "value" should be non-empty.
        return item != null && !TextUtils.isEmpty(item.label) && item.amount != null
                && !TextUtils.isEmpty(item.amount.currency)
                && !TextUtils.isEmpty(item.amount.value);
    }

    /**
     * Validates a list of payment items and returns their parsed representation or null if invalid.
     *
     * @param items The payment items to parse and validate.
     * @param totalCurrency The currency code for the total amount of payment.
     * @param formatter A formatter and validator for the currency amount value.
     * @return A list of valid line items or null if invalid.
     */
    private static List<LineItem> getValidatedLineItems(
            PaymentItem[] items, String totalCurrency, CurrencyStringFormatter formatter) {
        // Line items are optional.
        if (items == null) return new ArrayList<>();

        List<LineItem> result = new ArrayList<>(items.length);
        for (int i = 0; i < items.length; i++) {
            PaymentItem item = items[i];

            if (!hasAllPaymentItemFields(item)) return null;

            // All currencies must match.
            if (!item.amount.currency.equals(totalCurrency)) return null;

            // Value should be in correct format.
            if (!formatter.isValidAmountValue(item.amount.value)) return null;

            result.add(new LineItem(item.label, "", formatter.format(item.amount.value)));
        }

        return result;
    }

    /**
     * Validates a list of shipping options and returns their parsed representation or null if
     * invalid.
     *
     * @param options The raw shipping options to parse and validate.
     * @param totalCurrency The currency code for the total amount of payment.
     * @param formatter A formatter and validator for the currency amount value.
     * @return The UI representation of the shipping options or null if invalid.
     */
    private static SectionInformation getValidatedShippingOptions(PaymentShippingOption[] options,
            String totalCurrency, CurrencyStringFormatter formatter) {
        // Shipping options are optional.
        if (options == null || options.length == 0) {
            return new SectionInformation(PaymentRequestUI.TYPE_SHIPPING_OPTIONS);
        }

        for (int i = 0; i < options.length; i++) {
            PaymentShippingOption option = options[i];

            // Each "id", "label", "currency", and "value" should be non-empty.
            // Each "value" should be a valid amount value.
            // Each "currency" should match the total currency.
            if (option == null || TextUtils.isEmpty(option.id) || TextUtils.isEmpty(option.label)
                    || option.amount == null || TextUtils.isEmpty(option.amount.currency)
                    || TextUtils.isEmpty(option.amount.value)
                    || !totalCurrency.equals(option.amount.currency)
                    || !formatter.isValidAmountValue(option.amount.value)) {
                return null;
            }
        }

        List<PaymentOption> result = new ArrayList<>();
        int selectedItemIndex = SectionInformation.NO_SELECTION;
        for (int i = 0; i < options.length; i++) {
            PaymentShippingOption option = options[i];
            result.add(new PaymentOption(option.id, option.label,
                    formatter.format(option.amount.value), PaymentOption.NO_ICON));
            if (option.selected) selectedItemIndex = i;
        }

        return new SectionInformation(PaymentRequestUI.TYPE_SHIPPING_OPTIONS, selectedItemIndex,
                result);
    }

    /**
     * Called to retrieve the data to show in the initial PaymentRequest UI.
     */
    @Override
    public void getDefaultPaymentInformation(Callback<PaymentInformation> callback) {
        mPaymentInformationCallback = callback;

        if (mPaymentMethodsSection == null) return;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                providePaymentInformation();
            }
        });
    }

    private void providePaymentInformation() {
        mPaymentInformationCallback.onResult(
                new PaymentInformation(mUiShoppingCart, mShippingAddressesSection,
                        mUiShippingOptions, mContactSection, mPaymentMethodsSection));
        mPaymentInformationCallback = null;
    }

    @Override
    public void getShoppingCart(final Callback<ShoppingCart> callback) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(mUiShoppingCart);
            }
        });
    }

    @Override
    public void getSectionInformation(@PaymentRequestUI.DataType final int optionType,
            final Callback<SectionInformation> callback) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (optionType == PaymentRequestUI.TYPE_SHIPPING_ADDRESSES) {
                    callback.onResult(mShippingAddressesSection);
                } else if (optionType == PaymentRequestUI.TYPE_SHIPPING_OPTIONS) {
                    callback.onResult(mUiShippingOptions);
                } else if (optionType == PaymentRequestUI.TYPE_CONTACT_DETAILS) {
                    callback.onResult(mContactSection);
                } else if (optionType == PaymentRequestUI.TYPE_PAYMENT_METHODS) {
                    assert mPaymentMethodsSection != null;
                    callback.onResult(mPaymentMethodsSection);
                }
            }
        });
    }

    @Override
    @PaymentRequestUI.SelectionResult public int onSectionOptionSelected(
            @PaymentRequestUI.DataType int optionType, PaymentOption option,
            Callback<PaymentInformation> callback) {
        if (optionType == PaymentRequestUI.TYPE_SHIPPING_ADDRESSES) {
            assert option instanceof AutofillAddress;
            // Log the change of shipping address.
            mJourneyLogger.incrementSelectionChanges(
                    PaymentRequestJourneyLogger.SECTION_SHIPPING_ADDRESS);
            AutofillAddress address = (AutofillAddress) option;
            if (address.isComplete()) {
                mShippingAddressesSection.setSelectedItem(option);
                // This updates the line items and the shipping options asynchronously.
                mClient.onShippingAddressChange(address.toPaymentAddress());
            } else {
                editAddress(address);
            }
            mPaymentInformationCallback = callback;
            return PaymentRequestUI.SELECTION_RESULT_ASYNCHRONOUS_VALIDATION;
        } else if (optionType == PaymentRequestUI.TYPE_SHIPPING_OPTIONS) {
            // This may update the line items.
            mUiShippingOptions.setSelectedItem(option);
            mClient.onShippingOptionChange(option.getIdentifier());
            mPaymentInformationCallback = callback;
            return PaymentRequestUI.SELECTION_RESULT_ASYNCHRONOUS_VALIDATION;
        } else if (optionType == PaymentRequestUI.TYPE_CONTACT_DETAILS) {
            assert option instanceof AutofillContact;
            // Log the change of contact info.
            mJourneyLogger.incrementSelectionChanges(
                    PaymentRequestJourneyLogger.SECTION_CONTACT_INFO);
            AutofillContact contact = (AutofillContact) option;

            if (contact.isComplete()) {
                mContactSection.setSelectedItem(option);
            } else {
                editContact(contact);
                return PaymentRequestUI.SELECTION_RESULT_EDITOR_LAUNCH;
            }
        } else if (optionType == PaymentRequestUI.TYPE_PAYMENT_METHODS) {
            assert option instanceof PaymentInstrument;
            if (option instanceof AutofillPaymentInstrument) {
                // Log the change of credit card.
                mJourneyLogger.incrementSelectionChanges(
                        PaymentRequestJourneyLogger.SECTION_CREDIT_CARDS);
                AutofillPaymentInstrument card = (AutofillPaymentInstrument) option;

                if (!card.isComplete()) {
                    editCard(card);
                    return PaymentRequestUI.SELECTION_RESULT_EDITOR_LAUNCH;
                }
            }

            mPaymentMethodsSection.setSelectedItem(option);
        }

        return PaymentRequestUI.SELECTION_RESULT_NONE;
    }

    @Override
    @PaymentRequestUI.SelectionResult public int onSectionAddOption(
            @PaymentRequestUI.DataType int optionType, Callback<PaymentInformation> callback) {
        if (optionType == PaymentRequestUI.TYPE_SHIPPING_ADDRESSES) {
            editAddress(null);
            mPaymentInformationCallback = callback;
            // Log the add of shipping address.
            mJourneyLogger.incrementSelectionAdds(
                    PaymentRequestJourneyLogger.SECTION_SHIPPING_ADDRESS);
            return PaymentRequestUI.SELECTION_RESULT_ASYNCHRONOUS_VALIDATION;
        } else if (optionType == PaymentRequestUI.TYPE_CONTACT_DETAILS) {
            editContact(null);
            // Log the add of contact info.
            mJourneyLogger.incrementSelectionAdds(PaymentRequestJourneyLogger.SECTION_CONTACT_INFO);
            return PaymentRequestUI.SELECTION_RESULT_EDITOR_LAUNCH;
        } else if (optionType == PaymentRequestUI.TYPE_PAYMENT_METHODS) {
            editCard(null);
            // Log the add of credit card.
            mJourneyLogger.incrementSelectionAdds(PaymentRequestJourneyLogger.SECTION_CREDIT_CARDS);
            return PaymentRequestUI.SELECTION_RESULT_EDITOR_LAUNCH;
        }

        return PaymentRequestUI.SELECTION_RESULT_NONE;
    }

    private void editAddress(final AutofillAddress toEdit) {
        if (toEdit != null) {
            // Log the edit of a shipping address.
            mJourneyLogger.incrementSelectionEdits(
                    PaymentRequestJourneyLogger.SECTION_SHIPPING_ADDRESS);
        }
        mAddressEditor.edit(toEdit, new Callback<AutofillAddress>() {
            @Override
            public void onResult(AutofillAddress completeAddress) {
                if (mUI == null) return;

                if (completeAddress == null) {
                    mShippingAddressesSection.setSelectedItemIndex(SectionInformation.NO_SELECTION);
                    providePaymentInformation();
                } else {
                    if (toEdit == null) mShippingAddressesSection.addAndSelectItem(completeAddress);
                    mCardEditor.updateBillingAddress(completeAddress);
                    mClient.onShippingAddressChange(completeAddress.toPaymentAddress());
                }
            }
        });
    }

    private void editContact(final AutofillContact toEdit) {
        if (toEdit != null) {
            // Log the edit of a contact info.
            mJourneyLogger.incrementSelectionEdits(
                    PaymentRequestJourneyLogger.SECTION_CONTACT_INFO);
        }
        mContactEditor.edit(toEdit, new Callback<AutofillContact>() {
            @Override
            public void onResult(AutofillContact completeContact) {
                if (mUI == null) return;

                if (completeContact == null) {
                    mContactSection.setSelectedItemIndex(SectionInformation.NO_SELECTION);
                } else if (toEdit == null) {
                    mContactSection.addAndSelectItem(completeContact);
                }

                mUI.updateSection(PaymentRequestUI.TYPE_CONTACT_DETAILS, mContactSection);
            }
        });
    }

    private void editCard(final AutofillPaymentInstrument toEdit) {
        if (toEdit != null) {
            // Log the edit of a credit card.
            mJourneyLogger.incrementSelectionEdits(
                    PaymentRequestJourneyLogger.SECTION_CREDIT_CARDS);
        }
        mCardEditor.edit(toEdit, new Callback<AutofillPaymentInstrument>() {
            @Override
            public void onResult(AutofillPaymentInstrument completeCard) {
                if (mUI == null) return;

                if (completeCard == null) {
                    mPaymentMethodsSection.setSelectedItemIndex(SectionInformation.NO_SELECTION);
                } else if (toEdit == null) {
                    mPaymentMethodsSection.addAndSelectItem(completeCard);
                }

                mUI.updateSection(PaymentRequestUI.TYPE_PAYMENT_METHODS, mPaymentMethodsSection);
            }
        });
    }

    @Override
    public boolean onPayClicked(PaymentOption selectedShippingAddress,
            PaymentOption selectedShippingOption, PaymentOption selectedPaymentMethod) {
        assert selectedPaymentMethod instanceof PaymentInstrument;
        PaymentInstrument instrument = (PaymentInstrument) selectedPaymentMethod;
        mPaymentAppRunning = true;
        instrument.getDetails(mMerchantName, mOrigin, mRawTotal, mRawLineItems,
                mMethodData.get(instrument.getMethodName()), this);
        recordSuccessFunnelHistograms("PayClicked");
        return !(instrument instanceof AutofillPaymentInstrument);
    }

    @Override
    public void onDismiss() {
        disconnectFromClientWithDebugMessage("Dialog dismissed");
        closeUI(true);
        recordAbortReasonHistogram(PaymentRequestMetrics.ABORT_REASON_ABORTED_BY_USER);
    }

    private void disconnectFromClientWithDebugMessage(String debugMessage) {
        disconnectFromClientWithDebugMessage(debugMessage, PaymentErrorReason.USER_CANCEL);
    }

    private void disconnectFromClientWithDebugMessage(String debugMessage, int reason) {
        Log.d(TAG, debugMessage);
        if (mClient != null) mClient.onError(reason);
        closeClient();
        closeUI(true);
    }

    /**
     * Called by the merchant website to abort the payment.
     */
    @Override
    public void abort() {
        if (mClient == null) return;
        mClient.onAbort(!mPaymentAppRunning);
        if (mPaymentAppRunning) {
            if (sObserverForTest != null) sObserverForTest.onPaymentRequestServiceUnableToAbort();
        } else {
            closeClient();
            closeUI(true);
            recordAbortReasonHistogram(PaymentRequestMetrics.ABORT_REASON_ABORTED_BY_MERCHANT);
        }
    }

    /**
     * Called when the merchant website has processed the payment.
     */
    @Override
    public void complete(int result) {
        if (mClient == null) return;
        recordSuccessFunnelHistograms("Completed");
        closeUI(PaymentComplete.FAIL != result);
    }

    /**
     * Called when the renderer closes the Mojo connection.
     */
    @Override
    public void close() {
        if (mClient == null) return;
        closeClient();
        closeUI(true);
        recordAbortReasonHistogram(PaymentRequestMetrics.ABORT_REASON_MOJO_RENDERER_CLOSING);
    }

    /**
     * Called when the Mojo connection encounters an error.
     */
    @Override
    public void onConnectionError(MojoException e) {
        if (mClient == null) return;
        closeClient();
        closeUI(true);
        recordAbortReasonHistogram(PaymentRequestMetrics.ABORT_REASON_MOJO_CONNECTION_ERROR);
    }

    /**
     * Called after retrieving the list of payment instruments in an app.
     */
    @Override
    public void onInstrumentsReady(PaymentApp app, List<PaymentInstrument> instruments) {
        if (mClient == null) return;
        mPendingApps.remove(app);

        // Place the instruments into either "autofill" or "non-autofill" list to be displayed when
        // all apps have responded.
        if (instruments != null) {
            for (int i = 0; i < instruments.size(); i++) {
                PaymentInstrument instrument = instruments.get(i);
                if (mMethodData.containsKey(instrument.getMethodName())) {
                    addPendingInstrument(instrument);
                } else {
                    instrument.dismiss();
                }
            }
        }

        // Some payment apps still have not responded. Continue waiting for them.
        if (!mPendingApps.isEmpty()) return;

        if (disconnectIfNoPaymentMethodsSupported()) return;

        // List order:
        // > Non-autofill instruments.
        // > Complete autofill instruments.
        // > Incomplete autofill instruments.
        Collections.sort(mPendingAutofillInstruments, COMPLETENESS_COMPARATOR);
        mPendingInstruments.addAll(mPendingAutofillInstruments);

        // Log the number of suggested credit cards.
        mJourneyLogger.setNumberOfSuggestionsShown(PaymentRequestJourneyLogger.SECTION_CREDIT_CARDS,
                mPendingAutofillInstruments.size());

        mPendingAutofillInstruments.clear();
        mPendingAutofillInstruments = null;

        // Pre-select the first instrument on the list, if it is complete.
        int selection = SectionInformation.NO_SELECTION;
        if (!mPendingInstruments.isEmpty()) {
            PaymentInstrument first = mPendingInstruments.get(0);
            if (!(first instanceof AutofillPaymentInstrument)
                    || ((AutofillPaymentInstrument) first).isComplete()) {
                selection = 0;
            }
        }

        // The list of payment instruments is ready to display.
        mPaymentMethodsSection = new SectionInformation(PaymentRequestUI.TYPE_PAYMENT_METHODS,
                selection, mPendingInstruments);

        mPendingInstruments.clear();

        // UI has requested the full list of payment instruments. Provide it now.
        if (mPaymentInformationCallback != null) providePaymentInformation();
    }

    /**
     * If no payment methods are supported, disconnect from the client and return true.
     *
     * @return True if no payment methods are supported
     */
    private boolean disconnectIfNoPaymentMethodsSupported() {
        boolean waitingForPaymentApps = !mPendingApps.isEmpty() || !mPendingInstruments.isEmpty();
        boolean foundPaymentMethods =
                mPaymentMethodsSection != null && !mPaymentMethodsSection.isEmpty();

        if (!mArePaymentMethodsSupported
                || (mIsShowing && !waitingForPaymentApps && !foundPaymentMethods
                           && !mMerchantSupportsAutofillPaymentInstruments)) {
            // All payment apps have responded, but none of them have instruments. It's possible to
            // add credit cards, but the merchant does not support them either. The payment request
            // must be rejected.
            disconnectFromClientWithDebugMessage("Requested payment methods have no instruments",
                    PaymentErrorReason.NOT_SUPPORTED);
            recordAbortReasonHistogram(mArePaymentMethodsSupported
                            ? PaymentRequestMetrics.ABORT_REASON_NO_MATCHING_PAYMENT_METHOD
                            : PaymentRequestMetrics.ABORT_REASON_NO_SUPPORTED_PAYMENT_METHOD);
            if (sObserverForTest != null) sObserverForTest.onPaymentRequestServiceShowFailed();
            return true;
        }

        return false;
    }

    /**
     * Saves the given instrument in either "autofill" or "non-autofill" list. The separation
     * enables placing autofill instruments on the bottom of the list.
     *
     * Autofill instruments are also checked for completeness. A complete autofill instrument can be
     * sent to the merchant as-is, without editing first. Such instruments should be displayed
     * higher in the list.
     *
     * @param instrument The instrument to add to either "autofill" or "non-autofill" list.
     */
    private void addPendingInstrument(PaymentInstrument instrument) {
        if (instrument instanceof AutofillPaymentInstrument) {
            AutofillPaymentInstrument autofillInstrument = (AutofillPaymentInstrument) instrument;
            if (mCardEditor.isCardComplete(autofillInstrument.getCard())) {
                autofillInstrument.setIsComplete();
            }
            mPendingAutofillInstruments.add(instrument);
        } else {
            mPendingInstruments.add(instrument);
        }
    }

    /**
     * Called after retrieving instrument details.
     */
    @Override
    public void onInstrumentDetailsReady(String methodName, String stringifiedDetails) {
        if (mClient == null) return;

        PaymentResponse response = new PaymentResponse();
        response.methodName = methodName;
        response.stringifiedDetails = stringifiedDetails;

        if (mContactSection != null) {
            PaymentOption selectedContact = mContactSection.getSelectedItem();
            if (selectedContact != null) {
                // Contacts are created in show(). These should all be instances of AutofillContact.
                assert selectedContact instanceof AutofillContact;
                response.payerPhone = ((AutofillContact) selectedContact).getPayerPhone();
                response.payerEmail = ((AutofillContact) selectedContact).getPayerEmail();
            }
        }

        if (mUiShippingOptions != null) {
            PaymentOption selectedShippingOption = mUiShippingOptions.getSelectedItem();
            if (selectedShippingOption != null && selectedShippingOption.getIdentifier() != null) {
                response.shippingOption = selectedShippingOption.getIdentifier();
            }
        }

        // Record the payment method used to complete the transaction. If the payment method was an
        // Autofill credit card with an identifier, record its use.
        PaymentOption selectedPaymentMethod = mPaymentMethodsSection.getSelectedItem();
        if (selectedPaymentMethod instanceof AutofillPaymentInstrument) {
            if (!selectedPaymentMethod.getIdentifier().isEmpty()) {
                PersonalDataManager.getInstance().recordAndLogCreditCardUse(
                        selectedPaymentMethod.getIdentifier());
            }
            PaymentRequestMetrics.recordSelectedPaymentMethodHistogram(
                    PaymentRequestMetrics.SELECTED_METHOD_CREDIT_CARD);
        } else if (methodName.equals(ANDROID_PAY_METHOD_NAME)) {
            PaymentRequestMetrics.recordSelectedPaymentMethodHistogram(
                    PaymentRequestMetrics.SELECTED_METHOD_ANDROID_PAY);
        } else {
            PaymentRequestMetrics.recordSelectedPaymentMethodHistogram(
                    PaymentRequestMetrics.SELECTED_METHOD_OTHER_PAYMENT_APP);
        }

        mUI.showProcessingMessage();

        if (mShippingAddressesSection != null) {
            PaymentOption selectedShippingAddress = mShippingAddressesSection.getSelectedItem();
            if (selectedShippingAddress != null) {
                // Shipping addresses are created in show(). These should all be instances of
                // AutofillAddress.
                assert selectedShippingAddress instanceof AutofillAddress;
                AutofillAddress selectedAutofillAddress = (AutofillAddress) selectedShippingAddress;

                // Addresses to be sent to the merchant should always be complete.
                assert selectedAutofillAddress.isComplete();

                // Record the use of the profile.
                PersonalDataManager.getInstance().recordAndLogProfileUse(
                        selectedAutofillAddress.getProfile().getGUID());

                response.shippingAddress = selectedAutofillAddress.toPaymentAddress();

                // Create the normalization task.
                mPendingPaymentResponse = response;
                mIsWaitingForNormalization = true;
                boolean willNormalizeAsync = PersonalDataManager.getInstance().normalizeAddress(
                        selectedAutofillAddress.getProfile().getGUID(),
                        AutofillAddress.getCountryCode(selectedAutofillAddress.getProfile()), this);

                if (willNormalizeAsync) {
                    // If the normalization was not done synchronously, start a timer to cancel the
                    // asynchronous normalization if it takes too long.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onAddressNormalized(null);
                        }
                    }, PersonalDataManager.getInstance().getNormalizationTimeoutMS());
                }

                // The payment response will be sent to the merchant in onAddressNormalized instead.
                return;
            }
        }

        mClient.onPaymentResponse(response);
        recordSuccessFunnelHistograms("ReceivedInstrumentDetails");
    }

    /**
     * Callback method called either when the address has finished normalizing or when the timeout
     * triggers. Replaces the address in the response with the normalized version if present and
     * sends the response to the merchant.
     *
     * @param profile The profile with the address normalized or a null profile if the timeout
     *                triggered first.
     */
    @Override
    public void onAddressNormalized(AutofillProfile profile) {
        // Check if the other task finished first.
        if (!mIsWaitingForNormalization) return;
        mIsWaitingForNormalization = false;

        // Check if the response was already sent to the merchant.
        if (mClient == null || mPendingPaymentResponse == null) return;

        if (profile != null && !TextUtils.isEmpty(profile.getGUID())) {
            // The normalization finished first: use the normalized address.
            mPendingPaymentResponse.shippingAddress =
                    new AutofillAddress(profile, true /* isComplete */).toPaymentAddress();
        } else {
            // The timeout triggered first: cancel the normalization task.
            PersonalDataManager.getInstance().cancelPendingAddressNormalization();
        }

        // Send the payment response to the merchant.
        mClient.onPaymentResponse(mPendingPaymentResponse);

        mPendingPaymentResponse = null;

        recordSuccessFunnelHistograms("ReceivedInstrumentDetails");
    }

    /**
     * Called if unable to retrieve instrument details.
     */
    @Override
    public void onInstrumentDetailsError() {
        if (mClient == null) return;
        mUI.onPayButtonProcessingCancelled();
        mPaymentAppRunning = false;
    }

    /**
     * Closes the UI. If the client is still connected, then it's notified of UI hiding.
     *
     * @param immediateClose If true, then UI immediately closes. If false, the UI shows the error
     *                       message "There was an error processing your order." This message
     *                       implies that the merchant attempted to process the order, failed, and
     *                       called complete("fail") to notify the user. Therefore, this parameter
     *                       may be "false" only when called from
     *                       {@link PaymentRequestImpl#complete(int)}. All other callers should
     *                       always pass "true."
     */
    private void closeUI(boolean immediateClose) {
        if (mUI != null) {
            mUI.close(immediateClose, new Runnable() {
                @Override
                public void run() {
                    if (mClient != null) mClient.onComplete();
                    closeClient();
                }
            });
            mUI = null;
        }

        if (mPaymentMethodsSection != null) {
            for (int i = 0; i < mPaymentMethodsSection.getSize(); i++) {
                PaymentOption option = mPaymentMethodsSection.getItem(i);
                assert option instanceof PaymentInstrument;
                ((PaymentInstrument) option).dismiss();
            }
            mPaymentMethodsSection = null;
        }

        mContext.getTabModelSelector().removeObserver(mSelectorObserver);
        mContext.getCurrentTabModel().removeObserver(mTabModelObserver);
    }

    private void closeClient() {
        if (mClient != null) mClient.close();
        mClient = null;
        mDismissObserver.onPaymentRequestDismissed();
    }

    @VisibleForTesting
    public static void setObserverForTest(PaymentRequestServiceObserverForTest observerForTest) {
        sObserverForTest = observerForTest;
    }

    /**
     * Records specific histograms related to the different steps of a successful checkout.
     */
    private void recordSuccessFunnelHistograms(String funnelPart) {
        RecordHistogram.recordBooleanHistogram("PaymentRequest.CheckoutFunnel." + funnelPart, true);

        if (funnelPart.equals("Completed")) {
            mJourneyLogger.recordJourneyStatsHistograms("Completed");
        }
    }

    /**
     * Adds an entry to the aborted Payment Request histogram in the bucket corresponding to the
     * reason for aborting. Only records the initial reason for aborting, as some closing code calls
     * other closing code that can log too.
     */
    private void recordAbortReasonHistogram(int abortReason) {
        assert abortReason < PaymentRequestMetrics.ABORT_REASON_MAX;
        if (mHasRecordedAbortReason) return;

        mHasRecordedAbortReason = true;
        RecordHistogram.recordEnumeratedHistogram(
                "PaymentRequest.CheckoutFunnel.Aborted", abortReason,
                PaymentRequestMetrics.ABORT_REASON_MAX);

        if (abortReason == PaymentRequestMetrics.ABORT_REASON_ABORTED_BY_USER) {
            mJourneyLogger.recordJourneyStatsHistograms("UserAborted");
        } else {
            mJourneyLogger.recordJourneyStatsHistograms("OtherAborted");
        }
    }
}
