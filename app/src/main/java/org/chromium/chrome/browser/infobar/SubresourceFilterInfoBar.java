// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ResourceId;

/**
 * After user proceed through Safe Browsing warning interstitials that are displayed when the site
 * ahead contains deceptive embedded content, the infobar appears, explains the feature and give
 * the user an ability to reload the page with the content we've blocked previously.
 */
public class SubresourceFilterInfoBar extends ConfirmInfoBar {
    private final String mExplanationMessage;

    @CalledByNative
    private static InfoBar show(int enumeratedIconId, String message, String primaryButtonText,
            String secondaryButtonText, String explanationMessage) {
        return new SubresourceFilterInfoBar(ResourceId.mapToDrawableId(enumeratedIconId), message,
                primaryButtonText, secondaryButtonText, explanationMessage);
    }

    private SubresourceFilterInfoBar(int iconDrawbleId, String message, String primaryButtonText,
            String secondaryButtonText, String explanationMessage) {
        super(iconDrawbleId, null, message, null, primaryButtonText, secondaryButtonText);
        mExplanationMessage = explanationMessage;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);
        InfoBarControlLayout controlLayout = layout.addControlLayout();
        controlLayout.addDescription(mExplanationMessage);
    }
}
