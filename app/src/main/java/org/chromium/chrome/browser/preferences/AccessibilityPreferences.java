// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.accessibility.FontSizePrefs;

import java.text.NumberFormat;

/**
 * Fragment to keep track of all the accessibility related preferences.
 */
public class AccessibilityPreferences extends PreferenceFragment
        implements OnPreferenceChangeListener {

    /**
     * This value gives the threshold beyond which force enable zoom is automatically turned on. It
     * is chosen such that force enable zoom will be activated when the accessibility large text
     * setting is on (i.e. this value should be the same as or lesser than the font size scale used
     * by accessiblity large text).
     */
    public static final float FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER = 1.3f;

    public static final String PREF_TEXT_SCALE = "text_scale";
    public static final String PREF_FORCE_ENABLE_ZOOM = "force_enable_zoom";

    private NumberFormat mFormat;
    private FontPrefsObserver mFontPrefsObserver;
    private FontSizePrefs mFontSizePrefs;

    private TextScalePreference mTextScalePref;
    private SeekBarLinkedCheckBoxPreference mForceEnableZoomPref;

    // Saves the previous font scale factor because AccessibilityPreferences no longer has access
    // to it after the font scale factor change notification.
    private float mPreviousFontScaleFactor;

    private class FontPrefsObserver implements FontSizePrefs.Observer {
        @Override
        public void onChangeFontSize(float font) {
            processFontWithForceEnableZoom(font);
            updateTextScaleSummary(font);
        }

        @Override
        public void onChangeForceEnableZoom(boolean enabled) {
            mForceEnableZoomPref.setChecked(enabled);
        }

        @Override
        public void onChangeUserSetForceEnableZoom(boolean enabled) {}
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_accessibility);
        addPreferencesFromResource(R.xml.accessibility_preferences);

        mFormat = NumberFormat.getPercentInstance();
        mFontSizePrefs = FontSizePrefs.getInstance(getActivity());
        mFontPrefsObserver = new FontPrefsObserver();

        mTextScalePref = (TextScalePreference) findPreference(PREF_TEXT_SCALE);
        mTextScalePref.setOnPreferenceChangeListener(this);

        mForceEnableZoomPref = (SeekBarLinkedCheckBoxPreference) findPreference(
                PREF_FORCE_ENABLE_ZOOM);
        mForceEnableZoomPref.setOnPreferenceChangeListener(this);
        mForceEnableZoomPref.setLinkedSeekBarPreference(mTextScalePref);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateValues();
        mTextScalePref.startObservingFontPrefs();
        mFontSizePrefs.addObserver(mFontPrefsObserver);
    }

    @Override
    public void onStop() {
        mTextScalePref.stopObservingFontPrefs();
        mFontSizePrefs.removeObserver(mFontPrefsObserver);
        super.onStop();
    }

    private void updateValues() {
        float fontScaleFactor = mFontSizePrefs.getFontScaleFactor();
        mTextScalePref.setValue(fontScaleFactor);
        updateTextScaleSummary(fontScaleFactor);

        mForceEnableZoomPref.setChecked(mFontSizePrefs.getForceEnableZoom());
    }

    private void updateTextScaleSummary(float fontScaleFactor) {
        mTextScalePref.setSummary(mFormat.format(fontScaleFactor));
        mPreviousFontScaleFactor = fontScaleFactor;
    }

    private void processFontWithForceEnableZoom(float fontScaleFactor) {
        float threshold = FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER;
        if (mPreviousFontScaleFactor < threshold && fontScaleFactor >= threshold
                && !mFontSizePrefs.getForceEnableZoom()) {
            // If the slider is moved from below the threshold to above the threshold, we check
            // force enable zoom even if the user has previously set it.
            mFontSizePrefs.setForceEnableZoom(true);
            mFontSizePrefs.setUserSetForceEnableZoom(false);
        } else if (mPreviousFontScaleFactor >= threshold && fontScaleFactor < threshold
                && !mFontSizePrefs.getUserSetForceEnableZoom()) {
            // If the slider is moved from above the threshold to below it, we only uncheck force
            // enable zoom if it was not set manually.
            mFontSizePrefs.setForceEnableZoom(false);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PREF_TEXT_SCALE.equals(preference.getKey())) {
            mFontSizePrefs.setFontScaleFactor((Float) newValue);
        } else if (PREF_FORCE_ENABLE_ZOOM.equals(preference.getKey())) {
            mFontSizePrefs.setUserSetForceEnableZoom(true);
            mFontSizePrefs.setForceEnableZoom((Boolean) newValue);
        }
        return true;
    }
}
