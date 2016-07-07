// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.EmbedContentViewActivity;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;

/**
 * Fragment for settings page that allows user to view and edit a single server-provided credit
 * card.
 */
public class AutofillServerCardPreferences
        extends PreferenceFragment implements OnPreferenceClickListener {
    private String mGUID;

    private static final String PREF_SERVER_CARD_DESCRIPTION = "server_card_description";
    private static final String PREF_SERVER_CARD_EDIT_LINK = "server_card_edit_link";
    private static final String PREF_SERVER_CARD_LOCAL_COPY = "server_card_local_copy_button";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        addPreferencesFromResource(R.xml.autofill_server_card_preferences);
        getActivity().setTitle(R.string.autofill_edit_credit_card);

        // We know which card to display based on the GUID stuffed in
        // our extras by AutofillPreferences.
        Bundle extras = getArguments();
        if (extras != null) {
            mGUID = extras.getString(AutofillPreferences.AUTOFILL_GUID);
        }
        assert mGUID != null;
        CreditCard card = PersonalDataManager.getInstance().getCreditCard(mGUID);
        assert !card.getIsLocal();

        Preference cardDescription = findPreference(PREF_SERVER_CARD_DESCRIPTION);
        cardDescription.setTitle(card.getObfuscatedNumber());
        cardDescription.setSummary(card.getFormattedExpirationDate(getActivity()));

        findPreference(PREF_SERVER_CARD_EDIT_LINK).setOnPreferenceClickListener(this);

        Preference clearLocalCopy = findPreference(PREF_SERVER_CARD_LOCAL_COPY);
        if (!card.getIsCached()) {
            getPreferenceScreen().removePreference(clearLocalCopy);
        } else {
            clearLocalCopy.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(PREF_SERVER_CARD_EDIT_LINK)) {
            EmbedContentViewActivity.show(preference.getContext(),
                    R.string.autofill_edit_credit_card, R.string.autofill_manage_wallet_cards_url);
        } else {
            assert preference.getKey().equals(PREF_SERVER_CARD_LOCAL_COPY);
            PersonalDataManager.getInstance().clearUnmaskedCache(mGUID);
            // It's no longer cached locally. Hide the clear button.
            getPreferenceScreen().removePreference(preference);
        }
        return true;
    }
}
