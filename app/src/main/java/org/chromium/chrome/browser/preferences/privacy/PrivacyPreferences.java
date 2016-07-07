// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchFieldTrial;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.precache.PrecacheLauncher;
import org.chromium.chrome.browser.preferences.ButtonPreference;
import org.chromium.chrome.browser.preferences.ChromeBaseCheckBoxPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.NetworkPredictionOptions;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.safebrowsing.SafeBrowsingFieldTrial;

/**
 * Fragment to keep track of the all the privacy related preferences.
 */
public class PrivacyPreferences extends PreferenceFragment
        implements OnPreferenceChangeListener {

    /**
     * Set to true in the {@link Preferences#EXTRA_SHOW_FRAGMENT_ARGUMENTS} bundle to
     * trigger the clear browsing data dialog when showing the privacy preferences.
     */
    public static final String SHOW_CLEAR_BROWSING_DATA_EXTRA =
            "ShowClearBrowsingData";

    private static final String PREF_NAVIGATION_ERROR = "navigation_error";
    private static final String PREF_SEARCH_SUGGESTIONS = "search_suggestions";
    private static final String PREF_SAFE_BROWSING_EXTENDED_REPORTING =
            "safe_browsing_extended_reporting";
    private static final String PREF_SAFE_BROWSING = "safe_browsing";
    private static final String PREF_CONTEXTUAL_SEARCH = "contextual_search";
    private static final String PREF_NETWORK_PREDICTIONS = "network_predictions";
    private static final String PREF_NETWORK_PREDICTIONS_NO_CELLULAR =
            "network_predictions_no_cellular";
    private static final String PREF_CRASH_DUMP_UPLOAD = "crash_dump_upload";
    private static final String PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR =
            "crash_dump_upload_no_cellular";
    private static final String PREF_DO_NOT_TRACK = "do_not_track";
    private static final String PREF_CLEAR_BROWSING_DATA = "clear_browsing_data";
    private static final String PREF_USAGE_AND_CRASH_REPORTING = "usage_and_crash_reports";

    private ClearBrowsingDataDialogFragment mClearBrowsingDataDialogFragment;
    private ManagedPreferenceDelegate mManagedPreferenceDelegate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PrivacyPreferencesManager privacyPrefManager =
                PrivacyPreferencesManager.getInstance(getActivity());
        addPreferencesFromResource(R.xml.privacy_preferences);
        getActivity().setTitle(R.string.prefs_privacy);
        setHasOptionsMenu(true);

        mManagedPreferenceDelegate = createManagedPreferenceDelegate();

        NetworkPredictionPreference networkPredictionPref =
                (NetworkPredictionPreference) findPreference(PREF_NETWORK_PREDICTIONS);
        ChromeBaseCheckBoxPreference networkPredictionNoCellularPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_NETWORK_PREDICTIONS_NO_CELLULAR);
        NetworkPredictionOptions networkPredictionOptions = PrefServiceBridge.getInstance()
                .getNetworkPredictionOptions();

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        boolean isMobileNetworkCapable = privacyPrefManager.isMobileNetworkCapable();
        if (isMobileNetworkCapable) {
            preferenceScreen.removePreference(networkPredictionNoCellularPref);
            networkPredictionPref.setValue(networkPredictionOptions.enumToString());
            networkPredictionPref.setOnPreferenceChangeListener(this);
            networkPredictionPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
        } else {
            preferenceScreen.removePreference(networkPredictionPref);
            networkPredictionNoCellularPref.setChecked(
                    networkPredictionOptions != NetworkPredictionOptions.NETWORK_PREDICTION_NEVER);
            networkPredictionNoCellularPref.setOnPreferenceChangeListener(this);
            networkPredictionNoCellularPref.setManagedPreferenceDelegate(
                    mManagedPreferenceDelegate);
        }

        // Display the correct settings fragment according to the user experiment group and to type
        // of the device, by removing not applicable preference fragments.
        CrashDumpUploadPreference uploadCrashDumpPref =
                (CrashDumpUploadPreference) findPreference(PREF_CRASH_DUMP_UPLOAD);
        ChromeBaseCheckBoxPreference uploadCrashDumpNoCellularPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR);

        if (privacyPrefManager.isCellularExperimentEnabled()) {
            preferenceScreen.removePreference(uploadCrashDumpNoCellularPref);
            preferenceScreen.removePreference(uploadCrashDumpPref);
        } else {
            preferenceScreen.removePreference(findPreference(PREF_USAGE_AND_CRASH_REPORTING));
            if (isMobileNetworkCapable) {
                preferenceScreen.removePreference(uploadCrashDumpNoCellularPref);
                uploadCrashDumpPref.setOnPreferenceChangeListener(this);
                uploadCrashDumpPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
            } else {
                preferenceScreen.removePreference(uploadCrashDumpPref);
                uploadCrashDumpNoCellularPref.setOnPreferenceChangeListener(this);
                uploadCrashDumpNoCellularPref.setManagedPreferenceDelegate(
                        mManagedPreferenceDelegate);
            }
        }

        ChromeBaseCheckBoxPreference navigationErrorPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_NAVIGATION_ERROR);
        navigationErrorPref.setOnPreferenceChangeListener(this);
        navigationErrorPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        ChromeBaseCheckBoxPreference searchSuggestionsPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_SEARCH_SUGGESTIONS);
        searchSuggestionsPref.setOnPreferenceChangeListener(this);
        searchSuggestionsPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        if (!ContextualSearchFieldTrial.isEnabled(getActivity())) {
            preferenceScreen.removePreference(findPreference(PREF_CONTEXTUAL_SEARCH));
        }

        ChromeBaseCheckBoxPreference safeBrowsingExtendedReportingPref =
                (ChromeBaseCheckBoxPreference) findPreference(
                        PREF_SAFE_BROWSING_EXTENDED_REPORTING);
        safeBrowsingExtendedReportingPref.setOnPreferenceChangeListener(this);
        safeBrowsingExtendedReportingPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        ChromeBaseCheckBoxPreference safeBrowsingPref =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_SAFE_BROWSING);
        safeBrowsingPref.setOnPreferenceChangeListener(this);
        safeBrowsingPref.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        if (!SafeBrowsingFieldTrial.isEnabled()) {
            preferenceScreen.removePreference(
                    findPreference(PREF_SAFE_BROWSING_EXTENDED_REPORTING));
            preferenceScreen.removePreference(findPreference(PREF_SAFE_BROWSING));
        }

        ButtonPreference clearBrowsingData =
                (ButtonPreference) findPreference(PREF_CLEAR_BROWSING_DATA);
        clearBrowsingData.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showClearBrowsingDialog();
                return true;
            }
        });

        if (getArguments() != null) {
            boolean showClearBrowsingData =
                    getArguments().getBoolean(SHOW_CLEAR_BROWSING_DATA_EXTRA, false);
            if (showClearBrowsingData) showClearBrowsingDialog();
        }
        updateSummaries();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // CrashDumpUploadPreference listens to its own PreferenceChanged to update its text.
        // We have replaced the listener. If we do run into a CrashDumpUploadPreference change,
        // we will call onPreferenceChange to change the displayed text.
        if (preference instanceof CrashDumpUploadPreference) {
            ((CrashDumpUploadPreference) preference).onPreferenceChange(preference, newValue);
        }

        // NetworkPredictionPreference listens to its own PreferenceChanged to update its text.
        // We have replaced the listener. If we do run into a NetworkPredictionPreference change,
        // we will call onPreferenceChange to change the displayed text.
        if (preference instanceof NetworkPredictionPreference) {
            ((NetworkPredictionPreference) preference).onPreferenceChange(preference, newValue);
        }

        String key = preference.getKey();
        if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
            PrefServiceBridge.getInstance().setSearchSuggestEnabled((boolean) newValue);
        } else if (PREF_SAFE_BROWSING.equals(key)) {
            PrefServiceBridge.getInstance().setSafeBrowsingEnabled((boolean) newValue);
        } else if (PREF_SAFE_BROWSING_EXTENDED_REPORTING.equals(key)) {
            PrefServiceBridge.getInstance().setSafeBrowsingExtendedReportingEnabled(
                    (boolean) newValue);
        } else if (PREF_NETWORK_PREDICTIONS.equals(key)) {
            PrefServiceBridge.getInstance().setNetworkPredictionOptions(
                    NetworkPredictionOptions.stringToEnum((String) newValue));
            PrecacheLauncher.updatePrecachingEnabled(getActivity());
        } else if (PREF_NETWORK_PREDICTIONS_NO_CELLULAR.equals(key)) {
            PrefServiceBridge.getInstance().setNetworkPredictionOptions((boolean) newValue
                    ? NetworkPredictionOptions.NETWORK_PREDICTION_ALWAYS
                    : NetworkPredictionOptions.NETWORK_PREDICTION_NEVER);
            PrecacheLauncher.updatePrecachingEnabled(getActivity());
        } else if (PREF_NAVIGATION_ERROR.equals(key)) {
            PrefServiceBridge.getInstance().setResolveNavigationErrorEnabled((boolean) newValue);
        } else if (PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR.equals(key)) {
            PrefServiceBridge.getInstance().setCrashReporting((boolean) newValue);
        } else if (PREF_CRASH_DUMP_UPLOAD.equals(key)) {
            PrivacyPreferencesManager.getInstance(getActivity()).setUploadCrashDump(
                    (String) newValue);
        }

        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
    }

    /**
     * Updates the summaries for several preferences.
     */
    public void updateSummaries() {
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();

        CheckBoxPreference navigationErrorPref = (CheckBoxPreference) findPreference(
                PREF_NAVIGATION_ERROR);
        navigationErrorPref.setChecked(prefServiceBridge.isResolveNavigationErrorEnabled());

        CheckBoxPreference searchSuggestionsPref = (CheckBoxPreference) findPreference(
                PREF_SEARCH_SUGGESTIONS);
        searchSuggestionsPref.setChecked(prefServiceBridge.isSearchSuggestEnabled());

        CheckBoxPreference extendedReportingPref =
                (CheckBoxPreference) findPreference(PREF_SAFE_BROWSING_EXTENDED_REPORTING);
        if (extendedReportingPref != null) {
            extendedReportingPref.setChecked(
                    prefServiceBridge.isSafeBrowsingExtendedReportingEnabled());
        }

        CheckBoxPreference safeBrowsingPref =
                (CheckBoxPreference) findPreference(PREF_SAFE_BROWSING);
        if (safeBrowsingPref != null) {
            safeBrowsingPref.setChecked(prefServiceBridge.isSafeBrowsingEnabled());
        }

        Preference doNotTrackPref = findPreference(PREF_DO_NOT_TRACK);
        if (prefServiceBridge.isDoNotTrackEnabled()) {
            doNotTrackPref.setSummary(getActivity().getResources().getText(R.string.text_on));
        } else {
            doNotTrackPref.setSummary(getActivity().getResources().getText(R.string.text_off));
        }
        Preference contextualPref = findPreference(PREF_CONTEXTUAL_SEARCH);
        if (contextualPref != null) {
            if (prefServiceBridge.isContextualSearchDisabled()) {
                contextualPref.setSummary(getActivity().getResources().getText(R.string.text_off));
            } else {
                contextualPref.setSummary(getActivity().getResources().getText(R.string.text_on));
            }
        }
        PrivacyPreferencesManager privacyPrefManager =
                PrivacyPreferencesManager.getInstance(getActivity());
        if (privacyPrefManager.isCellularExperimentEnabled()) {
            Preference usageAndCrashPref = findPreference(PREF_USAGE_AND_CRASH_REPORTING);
            if (privacyPrefManager.isUsageAndCrashReportingEnabled()) {
                usageAndCrashPref.setSummary(
                        getActivity().getResources().getText(R.string.text_on));
            } else {
                usageAndCrashPref.setSummary(
                        getActivity().getResources().getText(R.string.text_off));
            }
        }
    }

    private ManagedPreferenceDelegate createManagedPreferenceDelegate() {
        return new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                String key = preference.getKey();
                PrefServiceBridge prefs = PrefServiceBridge.getInstance();
                if (PREF_NAVIGATION_ERROR.equals(key)) {
                    return prefs.isResolveNavigationErrorManaged();
                }
                if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
                    return prefs.isSearchSuggestManaged();
                }
                if (PREF_SAFE_BROWSING_EXTENDED_REPORTING.equals(key)) {
                    return prefs.isSafeBrowsingExtendedReportingManaged();
                }
                if (PREF_SAFE_BROWSING.equals(key)) {
                    return prefs.isSafeBrowsingManaged();
                }
                if (PREF_NETWORK_PREDICTIONS_NO_CELLULAR.equals(key)
                        || PREF_NETWORK_PREDICTIONS.equals(key)) {
                    return prefs.isNetworkPredictionManaged();
                }
                if (PREF_CRASH_DUMP_UPLOAD.equals(key)
                        || PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR.equals(key)) {
                    return prefs.isCrashReportManaged();
                }
                return false;
            }
        };
    }

    private void showClearBrowsingDialog() {
        mClearBrowsingDataDialogFragment = new ClearBrowsingDataDialogFragment();
        mClearBrowsingDataDialogFragment.show(
                getFragmentManager(), ClearBrowsingDataDialogFragment.FRAGMENT_TAG);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mClearBrowsingDataDialogFragment != null) {
            // In case the progress dialog is still showing and waiting for a callback, dismiss it.
            // See bug http://b/13396757.
            mClearBrowsingDataDialogFragment.dismissProgressDialog();
        }
        mClearBrowsingDataDialogFragment = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        MenuItem help = menu.add(
                Menu.NONE, R.id.menu_id_help_privacy, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_id_help_privacy) {
            HelpAndFeedback.getInstance(getActivity())
                    .show(getActivity(), getString(R.string.help_context_privacy),
                            Profile.getLastUsedProfile(), null);
            return true;
        }
        return false;
    }
}
