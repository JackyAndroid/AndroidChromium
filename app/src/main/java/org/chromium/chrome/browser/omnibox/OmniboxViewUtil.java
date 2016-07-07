// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

/**
 * Utilities for the Omnibox view component.
 */
public class OmniboxViewUtil {

    /**
     * Sanitizing the given string to be safe to paste into the omnibox.
     *
     * @param clipboardString The string from the clipboard.
     * @return The sanitized version of the string.
     */
    public static String sanitizeTextForPaste(String clipboardString) {
        return nativeSanitizeTextForPaste(clipboardString);
    }

    private static native String nativeSanitizeTextForPaste(String clipboardString);
}
