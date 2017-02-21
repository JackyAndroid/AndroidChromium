// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.view.ViewGroup;

import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * Controls the Search Bar in the Contextual Search Panel.
 */
public class ContextualSearchBarControl
        implements ChromeAnimation.Animatable<ContextualSearchBarControl.AnimationType> {

    /**
     * Animation properties.
     */
    protected enum AnimationType {
        TEXT_OPACITY
    }

    /**
     * The panel used to get information about the panel layout.
     */
    protected OverlayPanel mOverlayPanel;

    /**
     * The {@link ContextualSearchContextControl} used to control the Search Context View.
     */
    private final ContextualSearchContextControl mContextControl;

    /**
     * The {@link ContextualSearchTermControl} used to control the Search Term View.
     */
    private final ContextualSearchTermControl mSearchTermControl;

    /**
    * The {@link ContextualSearchCaptionControl} used to control the Caption View.
    */
    private final ContextualSearchCaptionControl mCaptionControl;

    /**
     * The opacity of the Bar's Search Context.
     * This text control may not be initialized until the opacity is set beyond 0.
     */
    private float mSearchBarContextOpacity = 0.f;

    /**
     * The opacity of the Bar's Search Term.
     * This text control may not be initialized until the opacity is set beyond 0.
     */
    private float mSearchBarTermOpacity = 0.f;

    /**
     * Constructs a new bottom bar control container by inflating views from XML.
     *
     * @param panel     The panel.
     * @param context   The context used to build this view.
     * @param container The parent view for the bottom bar views.
     * @param loader    The resource loader that will handle the snapshot capturing.
     */
    public ContextualSearchBarControl(OverlayPanel panel,
                                      Context context,
                                      ViewGroup container,
                                      DynamicResourceLoader loader) {
        mOverlayPanel = panel;
        mContextControl = new ContextualSearchContextControl(panel, context, container, loader);
        mSearchTermControl = new ContextualSearchTermControl(panel, context, container, loader);
        mCaptionControl = new ContextualSearchCaptionControl(panel, context, container, loader);
    }

    /**
     * Removes the bottom bar views from the parent container.
     */
    public void destroy() {
        mContextControl.destroy();
        mSearchTermControl.destroy();
        mCaptionControl.destroy();
    }

    /**
     * Sets the search context to display in the control.
     * @param selection The portion of the context that represents the user's selection.
     * @param end The portion of the context after the selection.
     */
    public void setSearchContext(String selection, String end) {
        cancelSearchTermResolutionAnimation();
        hideCaption();
        mContextControl.setSearchContext(selection, end);
        resetSearchBarContextOpacity();
    }

    /**
     * Sets the search term to display in the control.
     * @param searchTerm The string that represents the search term.
     */
    public void setSearchTerm(String searchTerm) {
        cancelSearchTermResolutionAnimation();
        hideCaption();
        mSearchTermControl.setSearchTerm(searchTerm);
        resetSearchBarTermOpacity();
    }

    /**
     * Sets the caption to display in the control and sets the caption visible.
     * @param caption The caption to display.
     */
    public void setCaption(String caption) {
        mCaptionControl.setCaption(caption);
    }

    /**
     * Gets the current animation percentage for the Caption control, which guides the vertical
     * position and opacity of the caption.
     * @return The animation percentage ranging from 0.0 to 1.0.
     *
     */
    public float getCaptionAnimationPercentage() {
        return mCaptionControl.getAnimationPercentage();
    }

    /**
     * @return Whether the caption control is visible.
     */
    public boolean getCaptionVisible() {
        return mCaptionControl.getIsVisible();
    }

    /**
     * @return The Id of the Search Context View.
     */
    public int getSearchContextViewId() {
        return mContextControl.getViewId();
    }

    /**
     * @return The Id of the Search Term View.
     */
    public int getSearchTermViewId() {
        return mSearchTermControl.getViewId();
    }

    /**
     * @return The Id of the Search Caption View.
     */
    public int getCaptionViewId() {
        return mCaptionControl.getViewId();
    }

    /**
     * @return The opacity of the SearchBar's search context.
     */
    public float getSearchBarContextOpacity() {
        return mSearchBarContextOpacity;
    }

    /**
     * @return The opacity of the SearchBar's search term.
     */
    public float getSearchBarTermOpacity() {
        return mSearchBarTermOpacity;
    }

    /**
     * Resets the SearchBar text opacity when a new search context is set. The search
     * context is made visible and the search term invisible.
     */
    private void resetSearchBarContextOpacity() {
        mSearchBarContextOpacity = 1.f;
        mSearchBarTermOpacity = 0.f;
    }

    /**
     * Resets the SearchBar text opacity when a new search term is set. The search
     * term is made visible and the search context invisible.
     */
    private void resetSearchBarTermOpacity() {
        mSearchBarContextOpacity = 0.f;
        mSearchBarTermOpacity = 1.f;
    }

    /**
     * Hides the caption so it will not be displayed in the control.
     */
    private void hideCaption() {
        mCaptionControl.hide();
    }

    // ============================================================================================
    // Search Bar Animation
    // ============================================================================================

    /**
     * Animates the search term resolution.
     */
    public void animateSearchTermResolution() {
        mOverlayPanel.addToAnimation(this, AnimationType.TEXT_OPACITY, 0.f, 1.f,
                OverlayPanelAnimation.MAXIMUM_ANIMATION_DURATION_MS, 0);
    }

    /**
     * Cancels the search term resolution animation if it is in progress.
     */
    public void cancelSearchTermResolutionAnimation() {
        if (mOverlayPanel.animationIsRunning()) {
            mOverlayPanel.cancelAnimation(this, AnimationType.TEXT_OPACITY);
        }
    }

    @Override
    public void setProperty(AnimationType type, float value) {
        if (type == AnimationType.TEXT_OPACITY) {
            updateSearchBarTextOpacity(value);
        }
    }

    @Override
    public void onPropertyAnimationFinished(AnimationType prop) {}

    /**
     * Updates the UI state for the SearchBar text. The search context view will fade out
     * while the search term fades in.
     *
     * @param percentage The visibility percentage of the search term view.
     */
    private void updateSearchBarTextOpacity(float percentage) {
        // The search context will start fading out before the search term starts fading in.
        // They will both be partially visible for overlapPercentage of the animation duration.
        float overlapPercentage = .75f;
        float fadingOutPercentage = Math.max(1 - (percentage / overlapPercentage), 0.f);
        float fadingInPercentage =
                Math.max(percentage - (1 - overlapPercentage), 0.f) / overlapPercentage;

        mSearchBarContextOpacity = fadingOutPercentage;
        mSearchBarTermOpacity = fadingInPercentage;
    }
}
