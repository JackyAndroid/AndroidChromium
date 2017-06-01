// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;

/**
 * Autofill settings fragment, which allows the user to edit autofill and credit card profiles.
 */
public class AutofillPreferences extends PreferenceFragment
        implements OnPreferenceChangeListener, PersonalDataManager.PersonalDataManagerObserver {

    public static final String AUTOFILL_GUID = "guid";
    // Needs to be in sync with kSettingsOrigin[] in
    // chrome/browser/ui/webui/options/autofill_options_handler.cc
    public static final String SETTINGS_ORIGIN = "Chrome settings";
    private static final String PREF_AUTOFILL_SWITCH = "autofill_switch";
    private static final String PREF_AUTOFILL_PROFILES = "autofill_profiles";
    private static final String PREF_AUTOFILL_CREDIT_CARDS = "autofill_credit_cards";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.autofill_preferences);
        getActivity().setTitle(R.string.prefs_autofill);

        ChromeSwitchPreference autofillSwitch =
                (ChromeSwitchPreference) findPreference(PREF_AUTOFILL_SWITCH);
        autofillSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PersonalDataManager.setAutofillEnabled((boolean) newValue);
                return true;
            }
        });

        setPreferenceCategoryIcons();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        refreshState();
        return true;
    }

    private void setPreferenceCategoryIcons() {
        Drawable plusIcon = ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.plus);
        plusIcon.mutate();
        plusIcon.setColorFilter(
                ApiCompatibilityUtils.getColor(getResources(), R.color.pref_accent_color),
                PorterDuff.Mode.SRC_IN);
        findPreference(PREF_AUTOFILL_PROFILES).setIcon(plusIcon);

        plusIcon = ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.plus);
        plusIcon.mutate();
        plusIcon.setColorFilter(
                ApiCompatibilityUtils.getColor(getResources(), R.color.pref_accent_color),
                PorterDuff.Mode.SRC_IN);
        findPreference(PREF_AUTOFILL_CREDIT_CARDS).setIcon(plusIcon);
    }

    /**
     * Refresh state (profile and credit card lists, preference summaries, etc.).
     */
    private void refreshState() {
        updateSummaries();
        rebuildProfileList();
        rebuildCreditCardList();
    }

    // Always clears the list before building/rebuilding.
    private void rebuildProfileList() {
        // Add an edit preference for each current Chrome profile.
        PreferenceGroup profileCategory = (PreferenceGroup) findPreference(PREF_AUTOFILL_PROFILES);
        profileCategory.removeAll();
        for (AutofillProfile profile : PersonalDataManager.getInstance().getProfilesForSettings()) {
            // Add an item on the current page...
            Preference pref = new Preference(getActivity());
            pref.setTitle(profile.getFullName());
            pref.setSummary(profile.getLabel());

            if (profile.getIsLocal()) {
                pref.setFragment(AutofillProfileEditor.class.getName());
            } else {
                pref.setWidgetLayoutResource(R.layout.autofill_server_data_label);
                pref.setFragment(AutofillServerProfilePreferences.class.getName());
            }

            Bundle args = pref.getExtras();
            args.putString(AUTOFILL_GUID, profile.getGUID());
            profileCategory.addPreference(pref);
        }
    }

    private void rebuildCreditCardList() {
        PreferenceGroup profileCategory =
                (PreferenceGroup) findPreference(PREF_AUTOFILL_CREDIT_CARDS);
        profileCategory.removeAll();
        for (CreditCard card : PersonalDataManager.getInstance().getCreditCardsForSettings()) {
            // Add an item on the current page...
            Preference pref = new Preference(getActivity());
            pref.setTitle(card.getObfuscatedNumber());
            pref.setSummary(card.getFormattedExpirationDate(getActivity()));

            if (card.getIsLocal()) {
                pref.setFragment(AutofillLocalCardEditor.class.getName());
            } else {
                pref.setFragment(AutofillServerCardEditor.class.getName());
                pref.setWidgetLayoutResource(R.layout.autofill_server_data_label);
            }

            Bundle args = pref.getExtras();
            args.putString(AUTOFILL_GUID, card.getGUID());
            profileCategory.addPreference(pref);
        }
    }

    private void updateSummaries() {
        ChromeSwitchPreference autofillSwitch =
                (ChromeSwitchPreference) findPreference(PREF_AUTOFILL_SWITCH);
        autofillSwitch.setChecked(PersonalDataManager.isAutofillEnabled());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always rebuild our list of profiles.  Although we could
        // detect if profiles are added or deleted (GUID list
        // changes), the profile summary (name+addr) might be
        // different.  To be safe, we update all.
        refreshState();
    }

    @Override
    public void onPersonalDataChanged() {
        refreshState();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        PersonalDataManager.getInstance().registerDataObserver(this);
    }

    @Override
    public void onDestroyView() {
        PersonalDataManager.getInstance().unregisterDataObserver(this);
        super.onDestroyView();
    }
}
