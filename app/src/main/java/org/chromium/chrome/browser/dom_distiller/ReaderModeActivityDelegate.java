// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.widget.ReaderModeControl;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;


/**
 * Manager for the Reader Mode feature.
 * This class keeps track of the status of Reader Mode and coordinates the control
 * with the layout.
 */
public class ReaderModeActivityDelegate {
    private static final String TAG = "ReaderModeActivityDelegate";

    private DynamicResourceLoader mResourceLoader;
    private ReaderModeControl mControl;
    private final ChromeActivity mActivity;
    private ViewGroup mParentView;

    /**
     * Constructs the manager for the given activity, and will attach views to the given parent.
     * @param activity             The {@code ChromeActivity} in use.
     */
    public ReaderModeActivityDelegate(ChromeActivity activity) {
        mActivity = activity;
    }

    /**
     * Initializes this manager.  Must be called before {@link #getReaderModeControl()}.
     * @param parentView The parent view to attach Reader Mode UX to.
     */
    public void initialize(ViewGroup parentView) {
        mParentView = parentView;
    }

    /**
     * Destroys the Reader Mode activity delegate.
     */
    public void destroy() {
        destroyReaderModeControl();
        mParentView = null;
    }

    /**
     * @param resourceLoader The {@link DynamicResourceLoader} to register and unregister the view.
     */
    public void setDynamicResourceLoader(DynamicResourceLoader resourceLoader) {
        mResourceLoader = resourceLoader;
        if (mControl != null) {
            mResourceLoader.registerResource(R.id.reader_mode_view,
                    mControl.getResourceAdapter());
        }
    }

    /**
     * Inflates the Reader Mode control, if needed.
     */
    public ReaderModeControl getReaderModeControl() {
        assert mParentView != null;
        if (mControl == null) {
            LayoutInflater.from(mActivity).inflate(R.layout.reader_mode_control, mParentView);
            mControl = (ReaderModeControl)
                    mParentView.findViewById(R.id.reader_mode_view);
            if (mResourceLoader != null) {
                mResourceLoader.registerResource(R.id.reader_mode_view,
                        mControl.getResourceAdapter());
            }
        }
        assert mControl != null;
        mControl.setVisibility(View.INVISIBLE);
        return mControl;
    }

    /**
     * Destroys the Reader Mode control.
     */
    public void destroyReaderModeControl() {
        if (mControl != null) {
            ((ViewGroup) mControl.getParent()).removeView(mControl);
            mControl = null;
            if (mResourceLoader != null) {
                mResourceLoader.unregisterResource(R.id.reader_mode_view);
            }
        }
    }
}
