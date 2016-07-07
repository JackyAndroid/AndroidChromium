// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.base.WindowAndroid;

/**
 * Java-side AutofillDialog and AutofillDialogFactory interfaces, and
 * JNI glue for C++ AutofillDialogControllerAndroid.
 */
@JNINamespace("autofill")
public class AutofillDialogControllerAndroid {
    private static AutofillDialogFactory sDialogFactory;

    private long mNativeDelegate;  // could be 0 after onDestroy().
    private AutofillDialog mDialog;

    /**
     * An interface to the two possible continuations for the dialog.
     * The dialog is expected to be dismissed when either of the calls is made.
     */
    public interface AutofillDialogDelegate {
        /**
         * Cancels the requestAutocomplete.
         */
        @VisibleForTesting
        void dialogCancel();

        /**
         * Submits the data to the web-page and persists the last account/card/address choices.
         * @param fullWallet Resulting billing/shipping information obtained from the user
         * @param lastUsedChoiceIsAutofill Whether the last selected data source is Autofill
         * @param lastUsedAccountName The last selected account name, or null
         * @param guidLastUsedBilling GUID of the last selected Autofill billing address, or null
         * @param guidLastUsedShipping GUID of the last selected Autofill shipping address, or null
         * @param guidLastUsedCard GUID of the last selected Autofill credit card, or null
         */
        @VisibleForTesting
        void dialogContinue(
                AutofillDialogResult.ResultWallet fullWallet,
                boolean lastUsedChoiceIsAutofill, String lastUsedAccountName,
                String guidLastUsedBilling, String guidLastUsedShipping, String guidLastUsedCard);
    }

    /**
     * An interface that exposes the necessary functionality for an Autofill dialog.
     * Note that all information necessary to construct the dialog is passed to the factory.
     */
    public interface AutofillDialog {
        /**
         * Notifies the dialog that the C++ side is gone.
         * The dialog needs to clear its reference to the no longer valid AutofillDialogDelegate.
         */
        void onDestroy();
    }

    /**
     * An interface to the factory that creates Autofill dialogs.
     */
    public interface AutofillDialogFactory {
        /**
         * Creates the dialog.
         * Reasonable attempts should be made to respect "initial choices",
         * Initial choices don't have to be self-consistent or valid.
         *
         * @param delegate Continuations for the dialog
         * @param windowAndroid Context in which the dialog should be shown
         * @param requestFullBillingAddress Whether the full billing address is required
         * @param requestShippingAddress Whether the shipping address is required
         * @param requestPhoneNumbers Whether the phone numbers are required in addresses
         * @param incognitoMode True if the dialog started from an incognito tab
         * @param initialChoiceIsAutofill Whether the selected data source should be Autofill
         * @param initialAccountName Account to be selected, or null
         * @param initialBillingGuid GUID of the initial billing address selection in Autofill
         * @param initialShippingGuid GUID of the initial shipping address selection in Autofill
         * @param initialCardGuid GUID of the initial credit card selection in Autofill
         * @param merchantDomain Scheme+origin for the originating web page, or null
         * @param shippingCountries A list of allowed shipping countries, or null
         * @param creditCardTypes A list of allowed credit card types (e.g. "VISA"), or null
         * @return The Autofill dialog that would later call into the delegate, or null
         */
        AutofillDialog createDialog(
                final AutofillDialogDelegate delegate,
                final WindowAndroid windowAndroid,
                final boolean requestFullBillingAddress, final boolean requestShippingAddress,
                final boolean requestPhoneNumbers,
                final boolean incognitoMode,
                final boolean initialChoiceIsAutofill, final String initialAccountName,
                final String initialBillingGuid, final String initialShippingGuid,
                final String initialCardGuid,
                final String merchantDomain,
                final String[] shippingCountries,
                final String[] creditCardTypes);
    }

    /**
     * Sets the factory to be used.
     * @param factory An instance of the AutofillDialogFactory that will handle requests.
     */
    @VisibleForTesting
    public static void setDialogFactory(AutofillDialogFactory factory) {
        sDialogFactory = factory;
    }

    @VisibleForTesting
    private AutofillDialogControllerAndroid(
            final long nativeAutofillDialogControllerAndroid,
            final WindowAndroid windowAndroid,
            final boolean requestFullBillingAddress, final boolean requestShippingAddress,
            final boolean requestPhoneNumbers,
            final boolean incognitoMode,
            final boolean initialChoiceIsAutofill, final String initialWalletAccountName,
            final String initialBillingGuid, final String initialShippingGuid,
            final String initialCardGuid,
            final String merchantDomain,
            final String[] shippingCountries,
            final String[] creditCardTypes) {
        mNativeDelegate = nativeAutofillDialogControllerAndroid;

        if (sDialogFactory == null) {
            nativeDialogCancel(mNativeDelegate);
            return;
        }

        AutofillDialogDelegate delegate = new AutofillDialogDelegate() {
            @Override
            public void dialogCancel() {
                nativeDialogCancel(mNativeDelegate);
            }

            @Override
            public void dialogContinue(
                    AutofillDialogResult.ResultWallet fullWallet,
                    boolean lastUsedChoiceIsAutofill, String lastUsedAccountName,
                    String guidLastUsedBilling, String guidLastUsedShipping,
                    String guidLastUsedCard) {
                nativeDialogContinue(mNativeDelegate, fullWallet,
                        lastUsedChoiceIsAutofill, lastUsedAccountName,
                        guidLastUsedBilling, guidLastUsedShipping, guidLastUsedCard);
            }
        };

        mDialog = sDialogFactory.createDialog(
                delegate,
                windowAndroid,
                requestFullBillingAddress, requestShippingAddress,
                requestPhoneNumbers,
                incognitoMode,
                initialChoiceIsAutofill, initialWalletAccountName,
                initialBillingGuid, initialShippingGuid, initialCardGuid,
                merchantDomain,
                shippingCountries,
                creditCardTypes);
        if (mDialog == null) {
            nativeDialogCancel(mNativeDelegate);
            return;
        }
    }

    @CalledByNative
    private static AutofillDialogControllerAndroid create(
            final long nativeAutofillDialogControllerAndroid,
            final WindowAndroid windowAndroid,
            final boolean requestFullBillingAddress, final boolean requestShippingAddress,
            final boolean requestPhoneNumbers,
            final boolean incognitoMode,
            final boolean initialChoiceIsAutofill, final String initialWalletAccountName,
            final String initialBillingGuid, final String initialShippingGuid,
            final String initialCreditCardGuid,
            final String merchantDomain,
            final String[] shippingCountries,
            final String[] creditCardTypes) {
        return new AutofillDialogControllerAndroid(
                nativeAutofillDialogControllerAndroid, windowAndroid,
                requestFullBillingAddress, requestShippingAddress, requestPhoneNumbers,
                incognitoMode,
                initialChoiceIsAutofill, initialWalletAccountName,
                initialBillingGuid, initialShippingGuid,
                initialCreditCardGuid,
                merchantDomain,
                shippingCountries,
                creditCardTypes);
    }

    @CalledByNative
    private static boolean isDialogAllowed(boolean isInvokedFromTheSameOrigin) {
        // TODO(aruslan): cross-origin invocations should be allowed with a
        // warning messge.
        return isInvokedFromTheSameOrigin;
    }

    @CalledByNative
    private void onDestroy() {
        if (mNativeDelegate == 0) return;

        if (mDialog != null) mDialog.onDestroy();

        mDialog = null;
        mNativeDelegate = 0;
    }

    // Calls from Java to C++ AutofillDialogControllerAndroid:

    private native void nativeDialogCancel(long nativeAutofillDialogControllerAndroid);
    private native void nativeDialogContinue(long nativeAutofillDialogControllerAndroid,
            Object fullWallet,
            boolean lastUsedChoiceIsAutofill, String lastUsedAccountName,
            String guidLastUsedBilling, String guidLastUsedShipping, String guidLastUsedCard);
}
