// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.content.Context;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.AttributeSet;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeBaseListPreference;
import org.chromium.chrome.browser.preferences.NetworkPredictionOptions;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

/**
 * Fragment to set and retrieve the prerender preference.
 */
public class NetworkPredictionPreference extends ChromeBaseListPreference
        implements OnPreferenceChangeListener {

    private final Context mContext;

    public NetworkPredictionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        String[] networkPredictionPrefs = context.getResources().getStringArray(
                R.array.bandwidth_entries);
        assert networkPredictionPrefs.length == NetworkPredictionOptions.choiceCount();
        setOnPreferenceChangeListener(this);
        setSummary(context.getString(
                PrefServiceBridge.getInstance().getNetworkPredictionOptions().getDisplayTitle()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        setSummary(mContext.getString(
                NetworkPredictionOptions.stringToEnum((String) newValue).getDisplayTitle()));
        return true;
    }
}
