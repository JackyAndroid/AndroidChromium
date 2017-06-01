// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.readermode;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelInflater;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * Controls the Search Term View that is used as a dynamic resource.
 */
public class ReaderModeBarControl extends OverlayPanelInflater {
    /**
     * The search term View.
     */
    private TextView mReaderText;

    /**
     * Track the last string that was displayed in the bar to avoid unnecessary re-draw.
     */
    private int mLastDisplayedStringId;

    /**
     * @param panel             The panel.
     * @param context           The Android Context used to inflate the View.
     * @param container         The container View used to inflate the View.
     * @param resourceLoader    The resource loader that will handle the snapshot capturing.
     */
    public ReaderModeBarControl(OverlayPanel panel,
                                  Context context,
                                  ViewGroup container,
                                  DynamicResourceLoader resourceLoader) {
        super(panel, R.layout.reader_mode_text_view, R.id.reader_mode_text_view,
                context, container, resourceLoader);
        invalidate();
    }

    /**
     * Set the text in the reader mode panel.
     * @param stringId The resource ID of the string to set the text to.
     */
    public void setBarText(int stringId) {
        if (stringId == mLastDisplayedStringId) return;
        mLastDisplayedStringId = stringId;

        inflate();
        mReaderText.setText(stringId);
        invalidate();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View view = getView();
        mReaderText = (TextView) view.findViewById(R.id.reader_mode_text);
    }
}
