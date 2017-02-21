// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.content.Context;
import android.support.v7.app.MediaRouteButton;
import android.util.AttributeSet;
import android.view.View;

/**
 * Cast button that wraps around a MediaRouteButton. We show the button only if there are available
 * cast devices.
 */
public class FullscreenMediaRouteButton extends MediaRouteButton {

    // Are we in the time window when the button should become visible if there're routes?
    private boolean mVisibilityRequested;

    /**
     * The constructor invoked when inflating the button.
     */
    public FullscreenMediaRouteButton(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mVisibilityRequested = false;
    }

    /**
     * Set the necessary state for the button to work.
     */
    /**
     * Set the necessary state for the button to work
     * @param controller the MediaRouteController controlling the route
     */
    public void initialize(MediaRouteController controller) {
        setRouteSelector(controller.buildMediaRouteSelector());
        setDialogFactory(new MediaRouteControllerDialogFactory());
    }

    @Override
    public void setEnabled(boolean enabled) {
        // TODO(aberent) not sure if this is still used, and in particular if mVisibilityRequest
        // is still used.

        // We need to check if the button was in the same state before to avoid doing anything,
        // but we also need to update the current state for {@link #setButtonVisibility} to work.
        boolean wasEnabled = isEnabled();
        super.setEnabled(enabled);

        if (wasEnabled == enabled) return;

        if (enabled && mVisibilityRequested) {
            setButtonVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    private void setButtonVisibility(int visibility) {
        // If the button is being set to visible, first make sure that it can even cast
        // to anything before making it actually visible.
        if (visibility == View.VISIBLE) {
            if (isEnabled()) {
                setVisibility(View.VISIBLE);
            } else {
                setVisibility(View.GONE);
            }
        } else {
            setVisibility(visibility);
        }
    }

}

