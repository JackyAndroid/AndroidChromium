// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * Defines the interface between a {@link ContextualSearchTranslateController} and the code that
 * handles callbacks.
 */
interface ContextualSearchTranslateInterface {
    /**
     * @return The accept-languages string.
     */
    public String getAcceptLanguages();

    /**
     * @return The Translate Service's target language string, non-empty.
     */
    public String getTranslateServiceTargetLanguage();
}
