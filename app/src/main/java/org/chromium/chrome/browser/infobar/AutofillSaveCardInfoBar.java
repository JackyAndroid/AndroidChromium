// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ResourceId;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * An infobar for saving credit card information.
 */
public class AutofillSaveCardInfoBar extends ConfirmInfoBar {
    /**
     * Legal message line with links to show in the infobar.
     */
    public static class LegalMessageLine {
        /**
         * A link in the legal message line.
         */
        public static class Link {
            /**
             * The starting inclusive index of the link position in the text.
             */
            public int start;

            /**
             * The ending exclusive index of the link position in the text.
             */
            public int end;

            /**
             * The URL of the link.
             */
            public String url;

            /**
             * Creates a new instance of the link.
             *
             * @param start The starting inclusive index of the link position in the text.
             * @param end The ending exclusive index of the link position in the text.
             * @param url The URL of the link.
             */
            public Link(int start, int end, String url) {
                this.start = start;
                this.end = end;
                this.url = url;
            }
        }

        /**
         * The plain text legal message line.
         */
        public String text;

        /**
         * A collection of links in the legal message line.
         */
        public final List<Link> links = new LinkedList<Link>();

        /**
         * Creates a new instance of the legal message line.
         *
         * @param text The plain text legal message.
         */
        public LegalMessageLine(String text) {
            this.text = text;
        }
    }

    private final long mNativeAutofillSaveCardInfoBar;
    private final List<CardDetail> mCardDetails = new ArrayList<>();
    private final LinkedList<LegalMessageLine> mLegalMessageLines =
            new LinkedList<LegalMessageLine>();

    /**
     * Creates a new instance of the infobar.
     *
     * @param nativeAutofillSaveCardInfoBar The pointer to the native object for callbacks.
     * @param enumeratedIconId ID corresponding to the icon that will be shown for the InfoBar.
     *                         The ID must have been mapped using the ResourceMapper class before
     *                         passing it to this function.
     * @param iconBitmap Bitmap to use if there is no equivalent Java resource for enumeratedIconId.
     * @param message Message to display to the user indicating what the InfoBar is for.
     * @param linkText Link text to display in addition to the message.
     * @param buttonOk String to display on the OK button.
     * @param buttonCancel String to display on the Cancel button.
     */
    private AutofillSaveCardInfoBar(long nativeAutofillSaveCardInfoBar, int enumeratedIconId,
            Bitmap iconBitmap, String message, String linkText, String buttonOk,
            String buttonCancel) {
        super(ResourceId.mapToDrawableId(enumeratedIconId), iconBitmap, message, linkText,
                buttonOk, buttonCancel);
        mNativeAutofillSaveCardInfoBar = nativeAutofillSaveCardInfoBar;
    }

    /**
     * Creates an infobar for saving a credit card.
     *
     * @param nativeAutofillSaveCardInfoBar The pointer to the native object for callbacks.
     * @param enumeratedIconId ID corresponding to the icon that will be shown for the InfoBar.
     *                         The ID must have been mapped using the ResourceMapper class before
     *                         passing it to this function.
     * @param iconBitmap Bitmap to use if there is no equivalent Java resource for enumeratedIconId.
     * @param message Message to display to the user indicating what the InfoBar is for.
     * @param linkText Link text to display in addition to the message.
     * @param buttonOk String to display on the OK button.
     * @param buttonCancel String to display on the Cancel button.
     * @return A new instance of the infobar.
     */
    @CalledByNative
    private static AutofillSaveCardInfoBar create(long nativeAutofillSaveCardInfoBar,
            int enumeratedIconId, Bitmap iconBitmap, String message, String linkText,
            String buttonOk, String buttonCancel) {
        return new AutofillSaveCardInfoBar(nativeAutofillSaveCardInfoBar, enumeratedIconId,
                iconBitmap, message, linkText, buttonOk, buttonCancel);
    }

    /**
     * Adds information to the infobar about the credit card that will be saved.
     *
     * @param enumeratedIconId ID corresponding to the icon that will be shown for this credit card.
     *                         The ID must have been mapped using the ResourceMapper class before
     *                         passing it to this function.
     * @param label The credit card label, for example "***1234".
     * @param subLabel The credit card sub-label, for example "Exp: 06/17".
     */
    @CalledByNative
    private void addDetail(int enumeratedIconId, String label, String subLabel) {
        mCardDetails.add(new CardDetail(enumeratedIconId, label, subLabel));
    }

    /**
     * Adds a line of legal message plain text to the infobar.
     *
     * @param text The legal message plain text.
     */
    @CalledByNative
    private void addLegalMessageLine(String text) {
        mLegalMessageLines.add(new LegalMessageLine(text));
    }

    /**
     * Marks up the last added line of legal message text with a link.
     *
     * @param start The inclusive offset of the start of the link in the text.
     * @param end The exclusive offset of the end of the link in the text.
     * @param url The URL to open when the link is clicked.
     */
    @CalledByNative
    private void addLinkToLastLegalMessageLine(int start, int end, String url) {
        mLegalMessageLines.getLast().links.add(new LegalMessageLine.Link(start, end, url));
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);
        InfoBarControlLayout control = layout.addControlLayout();
        for (int i = 0; i < mCardDetails.size(); i++) {
            CardDetail detail = mCardDetails.get(i);
            control.addIcon(detail.issuerIconDrawableId, 0, detail.label, detail.subLabel);
        }

        for (LegalMessageLine line : mLegalMessageLines) {
            SpannableString text = new SpannableString(line.text);
            for (final LegalMessageLine.Link link : line.links) {
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        nativeOnLegalMessageLinkClicked(mNativeAutofillSaveCardInfoBar, link.url);
                    }
                }, link.start, link.end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            }
            control.addDescription(text);
        }
    }

    private native void nativeOnLegalMessageLinkClicked(
            long nativeAutofillSaveCardInfoBar, String url);
}
