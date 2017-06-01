// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.ui.base.WindowAndroid;

/**
 * Helper for detecting whether the device supports scanning credit cards and for scanning credit
 * cards. The default implementation cannot scan cards. An implementing subclass must provide a
 * factory that builds its instances.
 */
public class CreditCardScanner {
    /**
     * Can be used to build subclasses of the scanner without the user of the class knowing about
     * the subclass name.
     */
    private static Factory sFactory;

    /** The delegate to notify of scanning result. */
    protected final Delegate mDelegate;

    /** Application context. Used in subclass. */
    @SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    protected final Context mContext;

    /** The window that's requesting a scan. Used in subclass. */
    @SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    protected final WindowAndroid mWindow;

    /** Builds instances of credit card scanners. */
    public interface Factory {
        /**
         * Builds an instance of credit card scanner.
         *
         * @param context  Application context.
         * @param window   The window that's requesting a scan.
         * @param delegate The delegate to notify of scanning result.
         * @return An object that can scan a credit card.
         */
        CreditCardScanner create(Context context, WindowAndroid window, Delegate delegate);
    }

    /** The delegate for credit card scanning. */
    public interface Delegate {
        /**
         * Notifies the delegate that scanning was cancelled.
         */
        void onScanCancelled();

        /**
         * Notifies the delegate that scanning was successful.
         *
         * @param cardHolderName  The card holder name.
         * @param cardNumber      Credit card number.
         * @param expirationMonth Expiration month in the range [1, 12].
         * @param expirationYear  Expiration year, e.g. 2000.
         */
        void onScanCompleted(
                String cardHolderName, String cardNumber, int expirationMonth, int expirationYear);
    }

    /**
     * Sets the factory that can build instances of credit card scanners.
     *
     * @param factory Can build instances of credit card scanners.
     */
    public static void setFactory(Factory factory) {
        sFactory = factory;
    }

    /**
     * Creates an instance of a credit card scanner.
     *
     * @param context  Application context.
     * @param window   The window that's requesting a scan.
     * @param delegate The delegate to notify of scanning result.
     * @return An object that can scan a credit card.
     */
    public static CreditCardScanner create(
            Context context, WindowAndroid window, Delegate delegate) {
        return sFactory != null ? sFactory.create(context, window, delegate)
                                : new CreditCardScanner(context, window, delegate);
    }

    /**
     * Constructor for the credit card scanner.
     *
     * @param context  Application context.
     * @param window   The window that's requesting a scan.
     * @param delegate The delegate to notify of scanning result.
     */
    protected CreditCardScanner(Context context, WindowAndroid window, Delegate delegate) {
        mContext = context;
        mWindow = window;
        mDelegate = delegate;
    }

    /**
     * Returns true if this instance has the ability to scan credit cards.
     *
     * @return True if has ability to scan credit cards.
     */
    public boolean canScan() {
        return false;
    }

    /**
     * Scans a credit card. Will invoke a delegate callback with the result.
     */
    public void scan() {
        mDelegate.onScanCancelled();
    }
}
