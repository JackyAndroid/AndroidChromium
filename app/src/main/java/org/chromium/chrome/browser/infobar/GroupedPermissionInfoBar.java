// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.support.v7.widget.SwitchCompat;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.base.WindowAndroid;

import java.util.ArrayList;

/**
 * An infobar for showing several permission requests which can be allowed or blocked.
 */
public class GroupedPermissionInfoBar extends PermissionInfoBar {
    private final int[] mPermissionIcons;
    private final String[] mPermissionText;
    private final int[] mContentSettings;
    private long mNativeGroupedPermissionInfoBar;

    private WindowAndroid mWindowAndroid;

    GroupedPermissionInfoBar(String message, String buttonOk, String buttonCancel,
            int[] permissionIcons, String[] permissionText, WindowAndroid windowAndroid,
            int[] contentSettings, boolean showPersistenceToggle) {
        super(0, null, message, null, buttonOk, buttonCancel, showPersistenceToggle);
        mPermissionIcons = permissionIcons;
        mPermissionText = permissionText;
        mWindowAndroid = windowAndroid;
        mContentSettings = contentSettings;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        InfoBarControlLayout control = layout.addControlLayout();

        if (mPermissionIcons.length == 1) {
            control.addIcon(ResourceId.mapToDrawableId(mPermissionIcons[0]),
                    R.color.light_normal_color, mPermissionText[0], null);
        } else {
            for (int i = 0; i < mPermissionIcons.length; i++) {
                control.addSwitch(ResourceId.mapToDrawableId(mPermissionIcons[i]),
                        R.color.light_normal_color, mPermissionText[i], i, true);
            }
        }

        // Call this last to ensure that if a persistence toggle is added, it's added last.
        super.createContent(layout);
    }

    @Override
    public void onTabReparented(Tab tab) {
        mWindowAndroid = tab.getWindowAndroid();
    }

    @Override
    public void onButtonClicked(final boolean isPrimaryButton) {
        if (isPrimaryButton) {
            boolean[] toggleStatus = new boolean[mPermissionIcons.length];

            if (mPermissionIcons.length == 1) {
                toggleStatus[0] = true;
            } else {
                for (int i = 0; i < mPermissionIcons.length; i++) {
                    toggleStatus[i] = ((SwitchCompat) getView().findViewById(i)).isChecked();
                }
            }

            // Only call setContentSettings with the permissions which were actually allowed by the
            // user.
            ArrayList<Integer> selectedContentSettings = new ArrayList<Integer>();
            for (int i = 0; i < toggleStatus.length; i++) {
                if (toggleStatus[i]) {
                    selectedContentSettings.add(Integer.valueOf(mContentSettings[i]));
                }
            }
            int[] selectedArray = new int[selectedContentSettings.size()];
            for (int i = 0; i < selectedContentSettings.size(); i++) {
                selectedArray[i] = selectedContentSettings.get(i).intValue();
            }

            if (mNativeGroupedPermissionInfoBar != 0) {
                nativeSetPermissionState(mNativeGroupedPermissionInfoBar, toggleStatus);
                setContentSettings(mWindowAndroid, selectedArray);
            }
        }
        super.onButtonClicked(isPrimaryButton);
    }

    @CalledByNative
    protected boolean isPersistSwitchOn() {
        return super.isPersistSwitchOn();
    }

    /**
     * Create an infobar for a given set of permission requests.
     *
     * @param message Message to display at the top of the infobar.
     * @param buttonOk String to display on the 'Allow' button.
     * @param buttonCancel String to display on the 'Block' button.
     * @param permissionIcons Enumerated ID (from ResourceMapper) of an icon to display next to each
     *                        permission.
     * @param permissionText String to display for each permission request.
     * @param windowAndroid The window which owns the infobar.
     * @param contentSettings The list of ContentSettingsTypes requested by the infobar.
     * @param showPersistenceToggle Whether or not a toggle to opt-out of persisting a decision
     *                              should be displayed.
     */
    @CalledByNative
    private static InfoBar create(String message, String buttonOk, String buttonCancel,
            int[] permissionIcons, String[] permissionText, WindowAndroid windowAndroid,
            int[] contentSettings, boolean showPersistenceToggle) {
        GroupedPermissionInfoBar infobar =
                new GroupedPermissionInfoBar(message, buttonOk, buttonCancel, permissionIcons,
                        permissionText, windowAndroid, contentSettings, showPersistenceToggle);
        return infobar;
    }

    @CalledByNative
    private void setNativePtr(long nativePtr) {
        mNativeGroupedPermissionInfoBar = nativePtr;
    }

    @Override
    protected void onNativeDestroyed() {
        mNativeGroupedPermissionInfoBar = 0;
        super.onNativeDestroyed();
    }

    private native void nativeSetPermissionState(
            long nativeGroupedPermissionInfoBar, boolean[] permissions);
}
