// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import org.chromium.chrome.browser.ResourceId;

/**
 * Detailed card information to show in the various Autofill infobars.
 */
public class CardDetail {
    /**
     * The identifier of the drawable of the card issuer icon.
     */
    public int issuerIconDrawableId;

    /**
     * The label for the card.
     */
    public String label;

    /**
     * The sub-label for the card.
     */
    public String subLabel;

    /**
     * Creates a new instance of the detailed card information.
     *
     * @param enumeratedIconId ID corresponding to the icon that will be shown for this credit
     *                         card. The ID must have been mapped using the ResourceMapper class
     *                         before passing it to this function.
     * @param label The credit card label, for example "***1234".
     * @param subLabel The credit card sub-label, for example "Exp: 06/17".
     */
    public CardDetail(int enumeratedIconId, String label, String subLabel) {
        this.issuerIconDrawableId = ResourceId.mapToDrawableId(enumeratedIconId);
        this.label = label;
        this.subLabel = subLabel;
    }
}
