// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Shows a list of USB devices that the user has granted websites permission to access.
 *
 * When the user selects an item UsbDevicePreferences is launched to show which sites have access
 * to the device.
 */
public class UsbChooserPreferences extends PreferenceFragment {
    // The site settings category we are showing.
    private SiteSettingsCategory mCategory;
    // Multiple sites may have access to the same device. A canonical UsbInfo for each device is
    // therefore arbitrarily chosen to represent it.
    private Map<String, Pair<ArrayList<UsbInfo>, ArrayList<Website>>> mPermissionsByObject =
            new HashMap<>();
    // The view to show when the list is empty.
    private TextView mEmptyView;
    // The view for searching the list of items.
    private SearchView mSearchView;
    // If not blank, represents a substring to use to search for site names.
    private String mSearch = "";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        addPreferencesFromResource(R.xml.usb_chooser_preferences);
        ListView listView = (ListView) getView().findViewById(android.R.id.list);
        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        listView.setEmptyView(mEmptyView);
        listView.setDivider(null);

        String category = getArguments().getString(SingleCategoryPreferences.EXTRA_CATEGORY);
        mCategory = SiteSettingsCategory.fromString(category);
        String title = getArguments().getString(SingleCategoryPreferences.EXTRA_TITLE);
        getActivity().setTitle(title);

        setHasOptionsMenu(true);

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.website_preferences_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                // Make search case-insensitive.
                query = query.toLowerCase();

                if (query.equals(mSearch)) return true;

                mSearch = query;
                getInfo();
                return true;
            }
        };
        mSearchView.setOnQueryTextListener(queryTextListener);

        MenuItem help =
                menu.add(Menu.NONE, R.id.menu_id_targeted_help, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_id_targeted_help) {
            HelpAndFeedback.getInstance(getActivity())
                    .show(getActivity(), getString(R.string.help_context_settings),
                            Profile.getLastUsedProfile(), null);
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        getInfo();
    }

    private class ResultsPopulator implements WebsitePermissionsFetcher.WebsitePermissionsCallback {
        @Override
        public void onWebsitePermissionsAvailable(Collection<Website> sites) {
            // This method may be called after the activity has been destroyed.
            // In that case, bail out.
            if (getActivity() == null) return;

            mPermissionsByObject.clear();
            for (Website site : sites) {
                for (UsbInfo info : site.getUsbInfo()) {
                    if (mSearch.isEmpty() || info.getName().toLowerCase().contains(mSearch)) {
                        Pair<ArrayList<UsbInfo>, ArrayList<Website>> entry =
                                mPermissionsByObject.get(info.getObject());
                        if (entry == null) {
                            entry = Pair.create(new ArrayList<UsbInfo>(), new ArrayList<Website>());
                            mPermissionsByObject.put(info.getObject(), entry);
                        }
                        entry.first.add(info);
                        entry.second.add(site);
                    }
                }
            }

            resetList();
        }
    }

    /**
     * Refreshes |mPermissionsByObject| with new data from native.
     *
     * resetList() is called to refresh the view when the data is ready.
     */
    private void getInfo() {
        WebsitePermissionsFetcher fetcher = new WebsitePermissionsFetcher(new ResultsPopulator());
        fetcher.fetchPreferencesForCategory(mCategory);
    }

    private void resetList() {
        getPreferenceScreen().removeAll();
        addPreferencesFromResource(R.xml.usb_chooser_preferences);

        if (mPermissionsByObject.isEmpty() && mSearch.isEmpty() && mEmptyView != null) {
            mEmptyView.setText(R.string.website_settings_usb_no_devices);
        }

        for (Pair<ArrayList<UsbInfo>, ArrayList<Website>> entry : mPermissionsByObject.values()) {
            Preference preference = new Preference(getActivity());
            Bundle extras = preference.getExtras();
            extras.putInt(UsbDevicePreferences.EXTRA_CATEGORY, mCategory.toContentSettingsType());
            extras.putString(
                    SingleCategoryPreferences.EXTRA_TITLE, getActivity().getTitle().toString());
            extras.putSerializable(UsbDevicePreferences.EXTRA_USB_INFOS, entry.first);
            extras.putSerializable(UsbDevicePreferences.EXTRA_SITES, entry.second);
            preference.setIcon(R.drawable.settings_usb);
            preference.setTitle(entry.first.get(0).getName());
            preference.setFragment(UsbDevicePreferences.class.getCanonicalName());
            getPreferenceScreen().addPreference(preference);
        }
    }
}
