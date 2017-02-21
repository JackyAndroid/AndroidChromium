// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

/**
 * An infobar used for prompting the user to grant a web API permission.
 *
 */
public class PermissionInfoBar extends ConfirmInfoBar {

    /** Whether or not to show a toggle for opting out of persisting the decision. */
    private boolean mShowPersistenceToggle;

    private WindowAndroid mWindowAndroid;

    /**
     * Mapping between the required {@link ContentSettingsType}s and their associated Android
     * runtime permissions.  Only {@link ContentSettingsType}s that are associated with runtime
     * permissions will be included in this list while all others will be excluded.
     */
    private SparseArray<String> mContentSettingsToPermissionsMap;

    protected PermissionInfoBar(int iconDrawableId, Bitmap iconBitmap, String message,
            String linkText, String primaryButtonText, String secondaryButtonText,
            boolean showPersistenceToggle) {
        super(iconDrawableId, iconBitmap, message, linkText, primaryButtonText,
                secondaryButtonText);
        mShowPersistenceToggle = showPersistenceToggle;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);

        if (mShowPersistenceToggle) {
            InfoBarControlLayout controlLayout = layout.addControlLayout();
            String description =
                    layout.getContext().getString(R.string.permission_infobar_persist_text);
            controlLayout.addSwitch(
                    0, 0, description, R.id.permission_infobar_persist_toggle, true);
        }
    }

    @Override
    public void onTabReparented(Tab tab) {
        mWindowAndroid = tab.getWindowAndroid();
    }

    /**
     * Specifies the {@link ContentSettingsType}s that are controlled by this InfoBar.
     *
     * @param windowAndroid The WindowAndroid that will be used to check for the necessary
     *                      permissions.
     * @param contentSettings The list of {@link ContentSettingsType}s whose access is guarded
     *                        by this InfoBar.
     */
    protected void setContentSettings(
            WindowAndroid windowAndroid, int[] contentSettings) {
        mWindowAndroid = windowAndroid;
        assert windowAndroid != null
                : "A WindowAndroid must be specified to request access to content settings";

        mContentSettingsToPermissionsMap = generatePermissionsMapping(contentSettings);
    }

    private SparseArray<String> generatePermissionsMapping(int[] contentSettings) {
        SparseArray<String> permissionsToRequest = new SparseArray<String>();
        for (int i = 0; i < contentSettings.length; i++) {
            String permission = PrefServiceBridge.getAndroidPermissionForContentSetting(
                    contentSettings[i]);
            if (permission != null && !mWindowAndroid.hasPermission(permission)) {
                permissionsToRequest.append(contentSettings[i], permission);
            }
        }
        return permissionsToRequest;
    }

    private int getDeniedPermissionResourceId(String permission) {
        int contentSettingsType = 0;
        // SparseArray#indexOfValue uses == instead of .equals, so we need to manually iterate
        // over the list.
        for (int i = 0; i < mContentSettingsToPermissionsMap.size(); i++) {
            if (permission.equals(mContentSettingsToPermissionsMap.valueAt(i))) {
                contentSettingsType = mContentSettingsToPermissionsMap.keyAt(i);
            }
        }

        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION) {
            return R.string.infobar_missing_location_permission_text;
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC) {
            return R.string.infobar_missing_microphone_permission_text;
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA) {
            return R.string.infobar_missing_camera_permission_text;
        }
        assert false : "Unexpected content setting type received: " + contentSettingsType;
        return R.string.infobar_missing_multiple_permissions_text;
    }

    @Override
    public void onButtonClicked(final boolean isPrimaryButton) {
        if (mWindowAndroid == null || !isPrimaryButton || getContext() == null
                || mContentSettingsToPermissionsMap == null
                || mContentSettingsToPermissionsMap.size() == 0) {
            onButtonClickedInternal(isPrimaryButton);
            return;
        }

        requestAndroidPermissions();
    }

    private void requestAndroidPermissions() {
        PermissionCallback callback = new PermissionCallback() {
            @Override
            public void onRequestPermissionsResult(
                    String[] permissions, int[] grantResults) {
                int deniedCount = 0;
                int requestableCount = 0;
                int deniedStringId = R.string.infobar_missing_multiple_permissions_text;
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        deniedCount++;
                        if (deniedCount > 1) {
                            deniedStringId = R.string.infobar_missing_multiple_permissions_text;
                        } else {
                            deniedStringId = getDeniedPermissionResourceId(permissions[i]);
                        }

                        if (mWindowAndroid.canRequestPermission(permissions[i])) {
                            requestableCount++;
                        }
                    }
                }

                Activity activity = mWindowAndroid.getActivity().get();
                if (deniedCount > 0 && requestableCount > 0 && activity != null) {
                    View view = activity.getLayoutInflater().inflate(
                            R.layout.update_permissions_dialog, null);
                    TextView dialogText = (TextView) view.findViewById(R.id.text);
                    dialogText.setText(deniedStringId);

                    AlertDialog.Builder builder =
                            new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                            .setView(view)
                            .setPositiveButton(R.string.infobar_update_permissions_button_text,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            requestAndroidPermissions();
                                        }
                                    })
                             .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                     @Override
                                     public void onCancel(DialogInterface dialog) {
                                         onCloseButtonClicked();
                                     }
                             });
                    builder.create().show();
                } else if (deniedCount > 0) {
                    onCloseButtonClicked();
                } else {
                    onButtonClickedInternal(true);
                }
            }
        };

        String[] permissionsToRequest = new String[mContentSettingsToPermissionsMap.size()];
        for (int i = 0; i < mContentSettingsToPermissionsMap.size(); i++) {
            permissionsToRequest[i] = mContentSettingsToPermissionsMap.valueAt(i);
        }
        mWindowAndroid.requestPermissions(permissionsToRequest, callback);
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
     * @param windowAndroid The owning window for the infobar.
     * @param enumeratedIconId ID corresponding to the icon that will be shown for the infobar.
     *                         The ID must have been mapped using the ResourceMapper class before
     *                         passing it to this function.
     * @param iconBitmap Bitmap to use if there is no equivalent Java resource for
     *                   enumeratedIconId.
     * @param message Message to display to the user indicating what the infobar is for.
     * @param linkText Link text to display in addition to the message.
     * @param buttonOk String to display on the OK button.
     * @param buttonCancel String to display on the Cancel button.
     * @param contentSettings The list of ContentSettingTypes being requested by this infobar.
     * @param showPersistenceToggle Whether or not a toggle to opt-out of persisting a decision
     *                              should be displayed.
     */
    @CalledByNative
    private static PermissionInfoBar create(WindowAndroid windowAndroid, int enumeratedIconId,
            Bitmap iconBitmap, String message, String linkText, String buttonOk,
            String buttonCancel, int[] contentSettings, boolean showPersistenceToggle) {
        int drawableId = ResourceId.mapToDrawableId(enumeratedIconId);

        PermissionInfoBar infoBar = new PermissionInfoBar(drawableId, iconBitmap, message, linkText,
                buttonOk, buttonCancel, showPersistenceToggle);
        infoBar.setContentSettings(windowAndroid, contentSettings);

        return infoBar;
    }
}
