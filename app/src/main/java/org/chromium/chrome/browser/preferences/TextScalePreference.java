// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.accessibility.FontSizePrefs;

/**
 * Preference that allows the user to change the scaling factor that's applied to web page text.
 * This also shows a preview of how large a typical web page's text will appear.
 */
public class TextScalePreference extends SeekBarPreference {
    private TextView mPreview;
    private View mView;
    private FontSizeObserver mFontSizeObserver;
    private FontSizePrefs mFontSizePrefs;

    private class FontSizeObserver implements FontSizePrefs.Observer {
        @Override
        public void onChangeFontSize(float font) {
            updatePreview();
        }

        @Override
        public void onChangeForceEnableZoom(boolean enabled) {}

        @Override
        public void onChangeUserSetForceEnableZoom(boolean enabled) {}
    }

    /**
     * Constructor for inflating from XML.
     */
    public TextScalePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mFontSizePrefs = FontSizePrefs.getInstance(getContext());
        mFontSizeObserver = new FontSizeObserver();

        setLayoutResource(R.layout.custom_preference);
        setWidgetLayoutResource(R.layout.preference_text_scale);
    }

    @Override
    protected View onCreateView(android.view.ViewGroup parent) {
        if (mView == null) mView = super.onCreateView(parent);
        return mView;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        if (mPreview == null) {
            mPreview = (TextView) view.findViewById(R.id.preview);
            updatePreview();
        }
    }

    /**
     * startObservingFont must be called when FontSizePreview's parent fragment is initialized.
     */
    public void startObservingFontPrefs() {
        mFontSizePrefs.addObserver(mFontSizeObserver);
        updatePreview();
    }

    /**
     * stopObservingFont must be called when FontSizePreview's parent fragment is destroyed.
     */
    public void stopObservingFontPrefs() {
        mFontSizePrefs.removeObserver(mFontSizeObserver);
    }

    private void updatePreview() {
        if (mPreview != null) {
            // Online body text tends to be around 13-16px. We ask the user to adjust the text scale
            // until 13px text is legible, that way all body text will be legible (and since font
            // boosting approximately preserves relative font size differences, other text will be
            // bigger/smaller as appropriate).
            final float smallestStandardWebPageFontSize = 13.0f;  // CSS px
            mPreview.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
                    smallestStandardWebPageFontSize * mFontSizePrefs.getFontScaleFactor());
        }
    }

}
