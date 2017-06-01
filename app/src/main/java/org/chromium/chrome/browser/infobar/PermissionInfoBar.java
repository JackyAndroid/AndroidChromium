// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.graphics.Bitmap;
import android.support.v7.widget.SwitchCompat;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.chrome.browser.permissions.AndroidPermissionRequester;
import org.chromium.chrome.browser.tab.Tab;

/**
 * An infobar used for prompting the user to grant a web API permission.
 */
public class PermissionInfoBar
        extends ConfirmInfoBar implements AndroidPermissionRequester.RequestDelegate {

    /** The tab which this infobar will be displayed upon. */
    protected final Tab mTab;

    /** The content settings types corresponding to the permission requested in this infobar. */
    protected int[] mContentSettingsTypes;

    /** Whether or not to show a toggle for opting out of persisting the decision. */
    private boolean mShowPersistenceToggle;

    protected PermissionInfoBar(Tab tab, int[] contentSettingsTypes, int iconDrawableId,
            Bitmap iconBitmap, String message, String linkText, String primaryButtonText,
            String secondaryButtonText, boolean showPersistenceToggle) {
        super(iconDrawableId, iconBitmap, message, linkText, primaryButtonText,
                secondaryButtonText);
        mTab = tab;
        mContentSettingsTypes = contentSettingsTypes;
        mShowPersistenceToggle = showPersistenceToggle;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);

        if (mShowPersistenceToggle) {
            InfoBarControlLayout controlLayout = layout.addControlLayout();
            String description =
                    layout.getContext().getString(R.string.permission_prompt_persist_text);
            controlLayout.addSwitch(
                    0, 0, description, R.id.permission_infobar_persist_toggle, true);
        }
    }

    @Override
    public void onAndroidPermissionAccepted() {
        onButtonClickedInternal(true);
    }

    @Override
    public void onAndroidPermissionCanceled() {
        onCloseButtonClicked();
    }

    @Override
    public void onButtonClicked(final boolean isPrimaryButton) {
        // requestAndroidPermissions will call back into this class to finalize the action if it
        // returns true.
        if (!isPrimaryButton || getContext() == null
                || !AndroidPermissionRequester.requestAndroidPermissions(
                           mTab, mContentSettingsTypes.clone(), this)) {
            onButtonClickedInternal(isPrimaryButton);
            return;
        }
    }

    private void onButtonClickedInternal(boolean isPrimaryButton) {
        super.onButtonClicked(isPrimaryButton);
    }

    /**
     * Returns true if the persist switch exists and is toggled on.
     */
    @CalledByNative
    protected boolean isPersistSwitchOn() {
        SwitchCompat persistSwitch = (SwitchCompat) getView().findViewById(
                R.id.permission_infobar_persist_toggle);
        if (mShowPersistenceToggle && persistSwitch != null) {
            return persistSwitch.isChecked();
        }
        return false;
    }

    /**
     * Creates and begins the process for showing a PermissionInfoBar.
     * @param tab                   The owning tab for the infobar.
     * @param enumeratedIconId      ID corresponding to the icon that will be shown for the infobar.
     *                              The ID must have been mapped using the ResourceMapper class
     *                              before passing it to this function.
     * @param iconBitmap            Bitmap to use if there is no equivalent Java resource for
     *                              enumeratedIconId.
     * @param message               Message to tell the user the purpose of the infobar.
     * @param linkText              Link text to display in addition to the message.
     * @param buttonOk              String to display on the OK button.
     * @param buttonCancel          String to display on the Cancel button.
     * @param contentSettingsTypes  The list of ContentSettingTypes being requested by this infobar.
     * @param showPersistenceToggle Whether or not a toggle to opt-out of persisting a decision
     *                              should be displayed.
     */
    @CalledByNative
    private static PermissionInfoBar create(Tab tab, int enumeratedIconId, Bitmap iconBitmap,
            String message, String linkText, String buttonOk, String buttonCancel,
            int[] contentSettingsTypes, boolean showPersistenceToggle) {
        int drawableId = ResourceId.mapToDrawableId(enumeratedIconId);

        PermissionInfoBar infoBar = new PermissionInfoBar(tab, contentSettingsTypes, drawableId,
                iconBitmap, message, linkText, buttonOk, buttonCancel, showPersistenceToggle);

        return infoBar;
    }
}
