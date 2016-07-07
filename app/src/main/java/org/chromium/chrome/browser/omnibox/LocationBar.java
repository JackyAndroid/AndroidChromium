// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import org.chromium.chrome.browser.WindowDelegate;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.UrlBar.UrlBarDelegate;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.toolbar.ActionModeController;
import org.chromium.chrome.browser.toolbar.ActionModeController.ActionBarDelegate;
import org.chromium.chrome.browser.toolbar.Toolbar;
import org.chromium.chrome.browser.toolbar.ToolbarActionModeCallback;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;
import org.chromium.ui.base.WindowAndroid;

/**
 * Container that holds the {@link UrlBar} and SSL state related with the current {@link Tab}.
 */
public interface LocationBar extends UrlBarDelegate {

    /**
     * Handles native dependent initialization for this class.
     */
    void onNativeLibraryReady();

    /**
     * Triggered when the current tab has changed to a {@link NewTabPage}.
     */
    void onTabLoadingNTP(NewTabPage ntp);

    /**
     * Called to set the autocomplete profile to a new profile.
     */
    void setAutocompleteProfile(Profile profile);

    /**
     * Call to force the UI to update the state of various buttons based on whether or not the
     * current tab is incognito.
     */
    void updateVisualsForState();

    /**
     * Sets the displayed URL to be the URL of the page currently showing.
     *
     * <p>The URL is converted to the most user friendly format (removing HTTP:// for example).
     *
     * <p>If the current tab is null, the URL text will be cleared.
     */
    void setUrlToPageUrl();

    /**
     * Sets the displayed title to the page title.
     */
    void setTitleToPageTitle();

    /**
     * Sets whether the location bar should have a layout showing a title.
     * @param showTitle Whether the title should be shown.
     */
    void setShowTitle(boolean showTitle);

    /**
     * Update the visuals based on a loading state change.
     * @param updateUrl Whether to update the URL as a result of the this call.
     */
    void updateLoadingState(boolean updateUrl);

    /**
     * Sets the {@link ToolbarDataProvider} to be used for accessing {@link Toolbar} state.
     */
    void setToolbarDataProvider(ToolbarDataProvider model);

    /**
     * Sets the menu helper that should be used if there is a menu button in {@link LocationBar}.
     * @param helper The helper to be used.
     */
    void setMenuButtonHelper(AppMenuButtonHelper helper);

    /**
     * @return The anchor view that should be used for the app menu. Null if there is no menu in
     *         {@link LocationBar} for the current configuration.
     */
    View getMenuAnchor();

    /**
     * Initialize controls that will act as hooks to various functions.
     * @param windowDelegate {@link WindowDelegate} that will provide {@link Window} related info.
     * @param delegate {@link ActionBarDelegate} to be used while creating a
     *                 {@link ActionModeController}.
     * @param windowAndroid {@link WindowAndroid} that is used by the owning {@link Activity}.
     */
    void initializeControls(WindowDelegate windowDelegate,
            ActionBarDelegate delegate, WindowAndroid windowAndroid);

    /**
     * Sets the URL focus change listener that will be notified when the URL gains or loses focus.
     * @param listener The listener to be registered.
     */
    void setUrlFocusChangeListener(UrlFocusChangeListener listener);

    /**
     * Signal a {@link UrlBar} focus change request.
     * @param shouldBeFocused Whether the focus should be requested or cleared. True requests focus
     *        and False clears focus.
     */
    void setUrlBarFocus(boolean shouldBeFocused);

    /**
     * Reverts any pending edits of the location bar and reset to the page state.  This does not
     * change the focus state of the location bar.
     */
    void revertChanges();

    /**
     * @return The timestamp for the {@link UrlBar} gaining focus for the first time.
     */
    long getFirstUrlBarFocusTime();

    /**
     * Updates the security icon displayed in the LocationBar.
     */
    void updateSecurityIcon(int securityLevel);

    /**
     * @return The {@link ViewGroup} that this container holds.
     */
    View getContainerView();

    /**
     * Updates the state of the mic button if there is one.
     */
    void updateMicButtonState();

    /**
     * Signal to the {@link SuggestionView} populated by us.
     */
    void hideSuggestions();

    /**
     * Sets the callback to be used by default for text editing action bar.
     * @param callback The callback to use.
     */
    void setDefaultTextEditActionModeCallback(ToolbarActionModeCallback callback);

}
