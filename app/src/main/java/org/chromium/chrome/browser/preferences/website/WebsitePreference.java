// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.preference.Preference;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;

/**
 * A preference that displays a website's favicon and URL and, optionally, the amount of local
 * storage used by the site.
 */
@SuppressFBWarnings("EQ_COMPARETO_USE_OBJECT_EQUALS")
class WebsitePreference extends Preference implements FaviconImageCallback {
    private final Website mSite;
    private final SiteSettingsCategory mCategory;

    private static final int TEXT_SIZE_SP = 13;

    // Loads the favicons asynchronously.
    private FaviconHelper mFaviconHelper;

    // Whether the favicon has been fetched already.
    private boolean mFaviconFetched = false;

    // Metrics for favicon processing.
    private static final int FAVICON_CORNER_RADIUS_DP = 2;
    private static final int FAVICON_PADDING_DP = 4;
    private static final int FAVICON_TEXT_SIZE_DP = 10;
    private static final int FAVICON_BACKGROUND_COLOR = 0xff969696;

    private int mFaviconSizePx;

    WebsitePreference(Context context, Website site, SiteSettingsCategory category) {
        super(context);
        mSite = site;
        mCategory = category;
        setWidgetLayoutResource(R.layout.website_features);
        mFaviconSizePx = context.getResources().getDimensionPixelSize(R.dimen.default_favicon_size);

        // To make sure the layout stays stable throughout, we assign a
        // transparent drawable as the icon initially. This is so that
        // we can fetch the favicon in the background and not have to worry
        // about the title appearing to jump (http://crbug.com/453626) when the
        // favicon becomes available.
        setIcon(new ColorDrawable(Color.TRANSPARENT));

        refresh();
    }

    public void putSiteIntoExtras(String key) {
        getExtras().putSerializable(key, mSite);
    }

    /**
     * Return the Website this object is representing.
     */
    public Website site() {
        return mSite;
    }

    @Override
    public void onFaviconAvailable(Bitmap image, String iconUrl) {
        mFaviconHelper.destroy();
        mFaviconHelper = null;
        if (image == null) {
            // Invalid favicon, produce a generic one.
            float density = getContext().getResources().getDisplayMetrics().density;
            int faviconSizeDp = Math.round(mFaviconSizePx / density);
            RoundedIconGenerator faviconGenerator = new RoundedIconGenerator(
                    getContext(), faviconSizeDp, faviconSizeDp,
                    FAVICON_CORNER_RADIUS_DP, FAVICON_BACKGROUND_COLOR,
                    FAVICON_TEXT_SIZE_DP);
            image = faviconGenerator.generateIconForUrl(faviconUrl());
        }

        setIcon(new BitmapDrawable(getContext().getResources(), image));
    }

    /**
     * Returns the url of the site to fetch a favicon for.
     */
    private String faviconUrl() {
        String origin = mSite.getAddress().getOrigin();
        if (origin == null) {
            return "http://" + mSite.getAddress().getHost();
        }

        Uri uri = Uri.parse(origin);
        if (uri.getPort() != -1) {
            // Remove the port.
            uri = uri.buildUpon().authority(uri.getHost()).build();
        }
        return uri.toString();
    }

    private void refresh() {
        setTitle(mSite.getTitle());
        String subtitleText = mSite.getSummary();
        if (subtitleText != null) {
            setSummary(String.format(getContext().getString(R.string.website_settings_embedded_in),
                                     subtitleText));
        }
    }

    @Override
    public int compareTo(Preference preference) {
        if (!(preference instanceof WebsitePreference)) {
            return super.compareTo(preference);
        }
        WebsitePreference other = (WebsitePreference) preference;
        if (mCategory.showStorageSites()) {
            return mSite.compareByStorageTo(other.mSite);
        }

        return mSite.compareByAddressTo(other.mSite);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView usageText = (TextView) view.findViewById(R.id.usage_text);
        usageText.setVisibility(View.GONE);
        if (mCategory.showStorageSites()) {
            long totalUsage = mSite.getTotalUsage();
            if (totalUsage > 0) {
                usageText.setText(Formatter.formatShortFileSize(getContext(), totalUsage));
                usageText.setTextSize(TEXT_SIZE_SP);
                usageText.setVisibility(View.VISIBLE);
            }
        }

        if (!mFaviconFetched) {
            // Start the favicon fetching. Will respond in onFaviconAvailable.
            mFaviconHelper = new FaviconHelper();
            if (!mFaviconHelper.getLocalFaviconImageForURL(
                        Profile.getLastUsedProfile(), faviconUrl(), mFaviconSizePx, this)) {
                onFaviconAvailable(null, null);
            }
            mFaviconFetched = true;
        }

        float density = getContext().getResources().getDisplayMetrics().density;
        int iconPadding = Math.round(FAVICON_PADDING_DP * density);
        View iconView = view.findViewById(android.R.id.icon);
        iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
    }
}
