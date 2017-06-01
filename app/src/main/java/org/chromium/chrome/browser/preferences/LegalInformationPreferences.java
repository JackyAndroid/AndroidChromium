// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;

/**
 * Fragment to display legal information about Chrome.
 */
public class LegalInformationPreferences extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.legal_information_preferences);
        getActivity().setTitle(R.string.legal_information_title);
    }
}
