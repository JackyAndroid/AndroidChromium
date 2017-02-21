// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.omnibox.LocationBarLayout;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.ui.base.DeviceFormFactor;

import java.net.URI;

/**
 * Maintains a URL bar that is displayed above the webapp's content.
 * For security reasons, this bar will appear when a user navigates to a website that is not
 * considered the same as the one that was used to open a WebappActivity originally.
 * The URL bar will disappear again once the user navigates back to the original website.
 *
 * Example scenario:
 * 0) User opens a webapp for http://domain1.com.           URL bar is hidden
 * 1) User navigates to http://domain1.com/some.html        URL bar is hidden
 * 2) User navigates to http://domain2.com/                 URL bar is shown
 * 3) User navigates back to http://domain1.com/some.html   URL bar is hidden
 */
public class WebappUrlBar extends FrameLayout implements View.OnLayoutChangeListener {
    private static final String TAG = "WebappUrlBar";

    private final TextView mUrlBar;
    private final View mSeparator;
    private final SparseIntArray mIconResourceWidths;

    private String mCurrentlyDisplayedUrl;
    private int mCurrentIconResource;

    /**
     * Creates a WebappUrlBar.
     * @param context Context to grab resources from.
     */
    public WebappUrlBar(Context context, AttributeSet attrSet) {
        super(context, attrSet);
        mIconResourceWidths = new SparseIntArray();

        mUrlBar = new TextView(context);
        mUrlBar.setSingleLine(true);
        mUrlBar.setGravity(Gravity.CENTER_VERTICAL);
        mUrlBar.setMovementMethod(ScrollingMovementMethod.getInstance());
        mUrlBar.setHorizontalFadingEdgeEnabled(true);
        mSeparator = new View(context);

        addView(mUrlBar,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER));
        addView(mSeparator,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM));

        // Set the colors.
        mSeparator.setBackgroundColor(ApiCompatibilityUtils.getColor(context.getResources(),
                R.color.webapp_url_bar_separator));
        setBackgroundColor(ApiCompatibilityUtils.getColor(context.getResources(),
                R.color.webapp_url_bar_bg));

        // Listen for changes in the URL bar's size.
        mUrlBar.addOnLayoutChangeListener(this);
    }

    /**
     * Updates the URL bar for the current URL.
     * @param url URL to display.
     * @param securityLevel Security level of the Tab.
     */
    public void update(String url, int securityLevel) {
        URI uri = createURI(url);
        updateSecurityIcon(securityLevel);
        updateDisplayedUrl(url, uri);
    }

    /**
     * @return the security icon being displayed for the current URL.
     */
    @VisibleForTesting
    protected int getCurrentIconResourceForTests() {
        return mCurrentIconResource;
    }

    /**
     * @return the URL being displayed.
     */
    @VisibleForTesting
    protected CharSequence getDisplayedUrlForTests() {
        return mUrlBar.getText();
    }

    /**
     * Show the end of the URL rather than the beginning.
     */
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        Layout layout = mUrlBar.getLayout();
        if (layout == null) return;

        // Android doesn't account for the compound Drawable in its width calculations, leading to
        // improper scrolling and even Android improperly placing the horizontal fade in its
        // TextView calculation.  Get around it by calculating that width manually: crbug.com/303908
        int urlBarWidth = mUrlBar.getWidth();
        int iconWidth =
                mCurrentIconResource == 0 ? 0 : mIconResourceWidths.get(mCurrentIconResource);
        int availableTextWidth = urlBarWidth - iconWidth;
        int desiredWidth = (int) Layout.getDesiredWidth(layout.getText(), layout.getPaint());

        if (desiredWidth > availableTextWidth) {
            mUrlBar.scrollTo(desiredWidth - availableTextWidth, 0);
        } else {
            mUrlBar.scrollTo(0, 0);
        }
    }

    private static URI createURI(String url) {
        // Get rid of spaces temporarily: crbug.com/298465
        // Get rid of the need for this hack eventually: crbug.com/296870
        url = url.replace(" ", "%20");

        try {
            return URI.create(url);
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Failed to convert URI: ", exception);
            return null;
        }
    }

    private void updateSecurityIcon(int securityLevel) {
        boolean isSmallDevice = !DeviceFormFactor.isTablet(getContext());
        mCurrentIconResource =
                LocationBarLayout.getSecurityIconResource(securityLevel, isSmallDevice, false);

        if (mCurrentIconResource != 0 && mIconResourceWidths.get(mCurrentIconResource, -1) == -1) {
            Drawable icon = ApiCompatibilityUtils.getDrawable(getResources(), mCurrentIconResource);
            mIconResourceWidths.put(mCurrentIconResource, icon.getIntrinsicWidth());
        }

        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(mUrlBar,
                mCurrentIconResource, 0, 0, 0);
    }

    private void updateDisplayedUrl(String originalUrl, URI uri) {
        boolean showScheme = mCurrentIconResource == 0;
        String displayUrl = originalUrl;
        if (uri != null) {
            String shortenedUrl = UrlFormatter.formatUrlForSecurityDisplay(uri, showScheme);
            if (!TextUtils.isEmpty(shortenedUrl)) displayUrl = shortenedUrl;
        }

        mUrlBar.setText(displayUrl);
        if (!TextUtils.equals(mCurrentlyDisplayedUrl, displayUrl)) {
            mCurrentlyDisplayedUrl = displayUrl;
        }
    }
}
