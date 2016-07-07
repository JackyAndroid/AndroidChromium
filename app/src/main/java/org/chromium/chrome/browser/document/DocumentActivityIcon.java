// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;

import org.chromium.chrome.browser.widget.RoundedIconGenerator;

/**
 * Generates the icon for the DocumentActivity in the recent tasks list.
 */
public class DocumentActivityIcon {
    private static final int APP_ICON_MIN_SIZE_DP = 32;
    private static final int APP_ICON_SIZE_DP = 64;
    private static final int APP_ICON_CORNER_RADIUS_DP = 3;
    private static final int APP_ICON_TEXT_SIZE_DP = 30;
    private static final int APP_ICON_DEFAULT_BACKGROUND_COLOR = Color.rgb(50, 50, 50);

    private Context mContext;

    /**
     * The page URL for which {@link #mGeneratedIcon} was generated.
     */
    private String mGeneratedPageUrl;

    /**
     * The most recently generated icon.
     */
    private Bitmap mGeneratedIcon;

    /**
     * Generates the icon if there is no adequate favicon.
     */
    private RoundedIconGenerator mGenerator;

    public DocumentActivityIcon(Context context) {
        mContext = context;
    }

    /**
     * Returns the icon to use for the DocumentActivity in the recent tasks list. Returns the
     * favicon if it is adequate. If the passed in favicon is not adequate, an icon is generated
     * from the page URL.
     * @param pageUrl The URL of the DocumentActivity's tab.
     * @param largestFavicon The largest favicon available at the page URL.
     * @return The icon to use in the recent tasks list.
     */
    public Bitmap getBitmap(String pageUrl, Bitmap largestFavicon) {
        int minSize =
                (int) mContext.getResources().getDisplayMetrics().density * APP_ICON_MIN_SIZE_DP;
        if (largestFavicon != null && largestFavicon.getWidth() >= minSize
                && largestFavicon.getHeight() >= minSize) {
            return largestFavicon;
        }

        if (TextUtils.equals(pageUrl, mGeneratedPageUrl)) {
            return mGeneratedIcon;
        }

        if (mGenerator == null) {
            mGenerator = new RoundedIconGenerator(mContext, APP_ICON_SIZE_DP, APP_ICON_SIZE_DP,
                    APP_ICON_CORNER_RADIUS_DP, APP_ICON_DEFAULT_BACKGROUND_COLOR,
                    APP_ICON_TEXT_SIZE_DP);
        }

        mGeneratedPageUrl = pageUrl;
        mGeneratedIcon = mGenerator.generateIconForUrl(pageUrl);
        return mGeneratedIcon;
    }
}
