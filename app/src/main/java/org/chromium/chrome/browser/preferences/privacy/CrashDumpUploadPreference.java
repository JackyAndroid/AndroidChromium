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

/**
 * Crash upload bandwidth preference.
 */
public class CrashDumpUploadPreference extends ChromeBaseListPreference
        implements OnPreferenceChangeListener {

    private Context mContext;

    public CrashDumpUploadPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setOnPreferenceChangeListener(this);
        String currentCrashPreference =
                PrivacyPreferencesManager.getInstance(context).getPrefCrashDumpUploadPreference();
        setSummary(getSummaryText(currentCrashPreference));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        setSummary(getSummaryText((String) newValue));
        return true;
    }

    /**
     * Text to display in the summary of the preference.
     * @param currentCrashPreference current value OR selected value if exists.
     * @return resource of the text.
     */
    public int getSummaryText(String currentCrashPreference) {
        if (currentCrashPreference.equals(mContext.getString(
                R.string.crash_dump_always_upload_value))) {
            return R.string.crash_dump_always_upload;
        } else if (currentCrashPreference.equals(mContext.getString(
                R.string.crash_dump_only_with_wifi_value))) {
            return R.string.crash_dump_only_with_wifi;
        } else {
            return R.string.crash_dump_never_upload;
        }
    }
}
