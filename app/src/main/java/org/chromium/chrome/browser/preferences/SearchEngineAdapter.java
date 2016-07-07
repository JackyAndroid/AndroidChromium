// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.omnibox.geo.GeolocationHeader;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;
import org.chromium.chrome.browser.preferences.website.SingleWebsitePreferences;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.LoadListener;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrl;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

import java.util.List;

/**
* A custom adapter for listing search engines.
*/
public class SearchEngineAdapter extends BaseAdapter implements LoadListener, OnClickListener {
    /**
     * A callback for reporting progress to the owner.
     */
    public interface SelectSearchEngineCallback {
        /**
         * Called when the search engine data has loaded and we've determined the currently active
         * one.
         * @param name Provides the name of it (with a simplified URL in parenthesis).
         */
        void currentSearchEngineDetermined(String name);

        /**
         * Called when the dialog should be dismissed.
         */
        void onDismissDialog();
    }

    // The current context.
    private Context mContext;

    // The layout inflater to use for the custom views.
    private LayoutInflater mLayoutInflater;

    // The callback to use for notifying caller of progress.
    private SelectSearchEngineCallback mCallback;

    // The list of available search engines.
    private List<TemplateUrl> mSearchEngines;
    // The position (index into mSearchEngines) of the currently selected search engine. Can be -1
    // if current search engine is managed and set to something other than the pre-populated values.
    private int mSelectedSearchEnginePosition = -1;

    /**
     * Construct a SearchEngineAdapter.
     * @param context The current context.
     * @param callback The callback to use to communicate back.
     */
    public SearchEngineAdapter(Context context, SelectSearchEngineCallback callback) {
        mContext = context;
        mLayoutInflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mCallback = callback;

        initEntries();
    }

    // Used for testing.

    String getValueForTesting() {
        return Integer.toString(mSelectedSearchEnginePosition);
    }

    void setValueForTesting(String value) {
        searchEngineSelected(Integer.parseInt(value));
    }

    /**
     * Initialize the search engine list.
     */
    private void initEntries() {
        TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
        if (!templateUrlService.isLoaded()) {
            templateUrlService.registerLoadListener(this);
            templateUrlService.load();
            return;  // Flow continues in onTemplateUrlServiceLoaded below.
        }

        // Fetch all the search engine info and the currently active one.
        mSearchEngines = templateUrlService.getLocalizedSearchEngines();
        int searchEngineIndex = templateUrlService.getDefaultSearchEngineIndex();
        // Convert the TemplateUrl index into an index into mSearchEngines.
        mSelectedSearchEnginePosition = -1;
        for (int i = 0; i < mSearchEngines.size(); ++i) {
            if (mSearchEngines.get(i).getIndex() == searchEngineIndex) {
                mSelectedSearchEnginePosition = i;
            }
        }

        // Report back what is selected.
        String name = "";
        if (mSelectedSearchEnginePosition > -1) {
            TemplateUrl templateUrl = mSearchEngines.get(mSelectedSearchEnginePosition);
            name = getSearchEngineNameAndDomain(mContext.getResources(), templateUrl);
        }
        mCallback.currentSearchEngineDetermined(name);
    }

    private int toIndex(int position) {
        return mSearchEngines.get(position).getIndex();
    }

    /**
     * @return The name of the search engine followed by the domain, e.g. "Google (google.co.uk)".
     */
    private static String getSearchEngineNameAndDomain(Resources res, TemplateUrl searchEngine) {
        String title = searchEngine.getShortName();
        if (!searchEngine.getKeyword().isEmpty()) {
            title = res.getString(R.string.search_engine_name_and_domain, title,
                    searchEngine.getKeyword());
        }
        return title;
    }

    // BaseAdapter:

    @Override
    public int getCount() {
        return mSearchEngines.size();
    }

    @Override
    public Object getItem(int pos) {
        TemplateUrl templateUrl = mSearchEngines.get(pos);
        return getSearchEngineNameAndDomain(mContext.getResources(), templateUrl);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (convertView == null) {
            view = mLayoutInflater.inflate(R.layout.search_engine, null);
        }

        view.setOnClickListener(this);
        view.setTag(position);

        // TODO(finnur): There's a tinting bug in the AppCompat lib (see http://crbug.com/474695),
        // which causes the first radiobox to always appear selected, even if it is not. It is being
        // addressed, but in the meantime we should use the native RadioButton instead.
        RadioButton radioButton = (RadioButton) view.findViewById(R.id.radiobutton);
        // On Lollipop this removes the redundant animation ring on selection but on older versions
        // it would cause the radio button to disappear.
        // TODO(finnur): Remove the encompassing if statement once we go back to using the AppCompat
        // control.
        final boolean selected = position == mSelectedSearchEnginePosition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            radioButton.setBackgroundResource(0);
        }
        radioButton.setChecked(selected);

        TextView description = (TextView) view.findViewById(R.id.description);
        TemplateUrl templateUrl = mSearchEngines.get(position);
        Resources resources = mContext.getResources();
        description.setText(getSearchEngineNameAndDomain(resources, templateUrl));

        // To improve the explore-by-touch experience, the radio button is hidden from accessibility
        // and instead, "checked" or "not checked" is read along with the search engine's name, e.g.
        // "google.com checked" or "google.com not checked".
        radioButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        description.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(host, event);
                event.setChecked(selected);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(selected);
            }
        });

        TextView link = (TextView) view.findViewById(R.id.link);
        link.setVisibility(selected ? View.VISIBLE : View.GONE);
        if (selected) {
            ForegroundColorSpan linkSpan = new ForegroundColorSpan(
                    ApiCompatibilityUtils.getColor(resources, R.color.pref_accent_color));
            if (LocationSettings.getInstance().isSystemLocationSettingEnabled()) {
                String message = mContext.getString(
                        locationEnabled(position, true)
                        ? R.string.search_engine_location_allowed
                        : R.string.search_engine_location_blocked);
                SpannableString messageWithLink = new SpannableString(message);
                messageWithLink.setSpan(linkSpan, 0, messageWithLink.length(), 0);
                link.setText(messageWithLink);
            } else {
                link.setText(SpanApplier.applySpans(
                        mContext.getString(R.string.android_location_off),
                        new SpanInfo("<link>", "</link>", linkSpan)));
            }

            link.setOnClickListener(this);
        }

        return view;
    }

    // TemplateUrlService.LoadListener

    @Override
    public void onTemplateUrlServiceLoaded() {
        TemplateUrlService.getInstance().unregisterLoadListener(this);
        initEntries();
    }

    // OnClickListener:

    @Override
    public void onClick(View view) {
        if (view.getTag() == null) {
            onLocationLinkClicked();
        } else {
            searchEngineSelected((int) view.getTag());
        }
    }

    private void searchEngineSelected(int position) {
        // First clean up any automatically added permissions (if any) for the previously selected
        // search engine.
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(mContext);
        if (sharedPreferences.getBoolean(PrefServiceBridge.LOCATION_AUTO_ALLOWED, false)) {
            if (locationEnabled(mSelectedSearchEnginePosition, false)) {
                String url = TemplateUrlService.getInstance().getSearchEngineUrlFromTemplateUrl(
                        toIndex(mSelectedSearchEnginePosition));
                WebsitePreferenceBridge.nativeSetGeolocationSettingForOrigin(
                        url, url, ContentSetting.DEFAULT.toInt(), false);
            }
            sharedPreferences.edit().remove(PrefServiceBridge.LOCATION_AUTO_ALLOWED).apply();
        }

        // Record the change in search engine.
        mSelectedSearchEnginePosition = position;
        TemplateUrlService.getInstance().setSearchEngine(toIndex(mSelectedSearchEnginePosition));

        // Report the change back.
        TemplateUrl templateUrl = mSearchEngines.get(mSelectedSearchEnginePosition);
        mCallback.currentSearchEngineDetermined(getSearchEngineNameAndDomain(
                mContext.getResources(), templateUrl));

        notifyDataSetChanged();
    }

    private void onLocationLinkClicked() {
        if (!LocationSettings.getInstance().isSystemLocationSettingEnabled()) {
            mContext.startActivity(
                    LocationSettings.getInstance().getSystemLocationSettingsIntent());
        } else {
            Intent settingsIntent = PreferencesLauncher.createIntentForSettingsPage(
                    mContext, SingleWebsitePreferences.class.getName());
            String url = TemplateUrlService.getInstance().getSearchEngineUrlFromTemplateUrl(
                    toIndex(mSelectedSearchEnginePosition));
            Bundle fragmentArgs = SingleWebsitePreferences.createFragmentArgsForSite(url);
            fragmentArgs.putBoolean(SingleWebsitePreferences.EXTRA_LOCATION,
                    locationEnabled(mSelectedSearchEnginePosition, true));
            settingsIntent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
            mContext.startActivity(settingsIntent);
        }
        mCallback.onDismissDialog();
    }

    private boolean locationEnabled(int position, boolean checkGeoHeader) {
        if (position == -1) return false;

        String url = TemplateUrlService.getInstance().getSearchEngineUrlFromTemplateUrl(
                toIndex(position));
        GeolocationInfo locationSettings = new GeolocationInfo(url, null, false);
        ContentSetting locationPermission = locationSettings.getContentSetting();
        // Handle the case where the geoHeader being sent when no permission has been specified.
        if (locationPermission == ContentSetting.ASK && checkGeoHeader) {
            return GeolocationHeader.isGeoHeaderEnabledForUrl(mContext, url, false);
        }
        return locationPermission == ContentSetting.ALLOW;
    }
}
