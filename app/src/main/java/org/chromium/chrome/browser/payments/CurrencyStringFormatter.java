// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for currency strings that can be too large to parse into numbers.
 * https://w3c.github.io/browser-payment-api/specs/paymentrequest.html#currencyamount
 */
public class CurrencyStringFormatter {
    // Amount value pattern and capture group numbers.
    private static final String AMOUNT_VALUE_PATTERN = "^(-?)([0-9]+)(\\.([0-9]+))?$";
    private static final int OPTIONAL_NEGATIVE_GROUP = 1;
    private static final int DIGITS_BETWEEN_NEGATIVE_AND_PERIOD_GROUP = 2;
    private static final int DIGITS_AFTER_PERIOD_GROUP = 4;

    // Max currency code length. Maximum length of currency code can be at most 2048.
    private static final int MAX_CURRENCY_CODE_LEN = 2048;

    // Currency code exceeding 6 chars will be ellipsized during formatting for display.
    private static final int MAX_CURRENCY_CHARS = 6;

    // Unicode character for ellipsis.
    private static final String ELLIPSIS = "\u2026";

    // Formatting constants.
    private static final int DIGIT_GROUPING_SIZE = 3;

    private final Pattern mAmountValuePattern;

    /**
     * The currency formatted for display. Currency can be any string of at most
     * 2048 characters.Currency code more than 6 character is formatted to first
     * 5 characters and ellipsis.
     */
    public final String mFormattedCurrencyCode;

    /**
     * The symbol for the currency specified on the bill. For example, the symbol for "USD" is "$".
     */
    private final String mCurrencySymbol;

    /**
     * The number of digits after the decimal separator for the currency specified on the bill. For
     * example, 2 for "USD" and 0 for "JPY".
     */
    private final int mDefaultFractionDigits;

    /**
     * The number grouping separator for the current locale. For example, "," in US. 3-digit groups
     * are assumed.
     */
    private final char mGroupingSeparator;

    /**
     * The monetary decimal separator for the current locale. For example, "." in US and "," in
     * France.
     */
    private final char mMonetaryDecimalSeparator;

    /**
     * Builds the formatter for the given currency code and the current user locale.
     *
     * @param currencyCode The currency code. Most commonly, this follows ISO 4217 format: 3 upper
     *                     case ASCII letters. For example, "USD". Format is not restricted. Should
     *                     not be null.
     * @param userLocale User's current locale. Should not be null.
     */
    public CurrencyStringFormatter(String currencyCode, Locale userLocale) {
        assert currencyCode != null : "currencyCode should not be null";
        assert userLocale != null : "userLocale should not be null";

        mAmountValuePattern = Pattern.compile(AMOUNT_VALUE_PATTERN);

        mFormattedCurrencyCode = currencyCode.length() <= MAX_CURRENCY_CHARS
                ? currencyCode
                : currencyCode.substring(0, MAX_CURRENCY_CHARS - 1) + ELLIPSIS;

        String currencySymbol;
        int defaultFractionDigits;
        try {
            Currency currency = Currency.getInstance(currencyCode);
            currencySymbol = currency.getSymbol();
            defaultFractionDigits = currency.getDefaultFractionDigits();
        } catch (IllegalArgumentException e) {
            // The spec does not limit the currencies to official ISO 4217 currency code list, which
            // is used by java.util.Currency. For example, "BTX" (bitcoin) is not an official ISO
            // 4217 currency code, but is allowed by the spec.
            currencySymbol = "";
            defaultFractionDigits = 0;
        }

        // If the prefix of the currency symbol matches the prefix of the currency code, remove the
        // matching prefix from the symbol. The UI already shows the currency code, so there's no
        // need to show duplicate information.
        String symbol = "";
        for (int i = 0; i < currencySymbol.length(); i++) {
            if (i >= currencyCode.length() || currencySymbol.charAt(i) != currencyCode.charAt(i)) {
                symbol = currencySymbol.substring(i);
                break;
            }
        }
        mCurrencySymbol = symbol;

        mDefaultFractionDigits = defaultFractionDigits;

        // Use the symbols from user's current locale. For example, use "," for decimal separator in
        // France, even if paying in "USD".
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(userLocale);
        mGroupingSeparator = symbols.getGroupingSeparator();
        mMonetaryDecimalSeparator = symbols.getMonetaryDecimalSeparator();
    }

    /**
     * Returns true if the amount value string is in valid format.
     *
     * @param amountValue The number to check for validity.
     * @return Whether the number is in valid format.
     */
    public boolean isValidAmountValue(String amountValue) {
        return amountValue != null && mAmountValuePattern.matcher(amountValue).matches();
    }

    /**
     * Returns true if the currency code string is in valid format.
     *
     * @param amountCurrencyCode The currency code to check for validity.
     * @return Whether the currency code is in valid format.
     */
    public boolean isValidAmountCurrencyCode(String amountCurrencyCode) {
        return amountCurrencyCode != null && amountCurrencyCode.length() <= MAX_CURRENCY_CODE_LEN;
    }

    /** @return The currency code formatted for display. */
    public String getFormattedCurrencyCode() {
        return mFormattedCurrencyCode;
    }

    /**
     * Formats the currency string for display. Does not parse the string into a number, because it
     * might be too large. The number is formatted for the current locale and follows the symbol of
     * the currency code.
     *
     * @param amountValue The number to format. Should be in "^-?[0-9]+(\.[0-9]+)?$" format. Should
     *                    not be null.
     * @return The currency symbol followed by a space and the formatted number.
     */
    public String format(String amountValue) {
        assert amountValue != null : "amountValue should not be null";

        Matcher m = mAmountValuePattern.matcher(amountValue);

        // Required to capture the groups.
        boolean matches = m.matches();
        assert matches;

        StringBuilder result = new StringBuilder(m.group(OPTIONAL_NEGATIVE_GROUP));
        result.append(mCurrencySymbol);
        int digitStart = result.length();

        result.append(m.group(DIGITS_BETWEEN_NEGATIVE_AND_PERIOD_GROUP));
        for (int i = result.length() - DIGIT_GROUPING_SIZE; i > digitStart;
                i -= DIGIT_GROUPING_SIZE) {
            result.insert(i, mGroupingSeparator);
        }

        String decimals = m.group(DIGITS_AFTER_PERIOD_GROUP);
        int numberOfDecimals = decimals == null ? 0 : decimals.length();

        if (numberOfDecimals > 0 || mDefaultFractionDigits > 0) {
            result.append(mMonetaryDecimalSeparator);
            if (null != decimals) result.append(decimals);

            for (int i = numberOfDecimals; i < mDefaultFractionDigits; i++) {
                result.append("0");
            }
        }

        return result.toString();
    }
}
