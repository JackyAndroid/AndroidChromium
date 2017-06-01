// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.permissions;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v7.app.AlertDialog;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

/**
 * Methods to handle requesting native permissions from Android when the user grants a website a
 * permission.
 */
public class AndroidPermissionRequester {
    /**
    * An interface for classes which need to be informed of the outcome of asking a user to grant an
    * Android permission.
    */
    public interface RequestDelegate {
        void onAndroidPermissionAccepted();
        void onAndroidPermissionCanceled();
    }

    private static SparseArray<String> generatePermissionsMapping(
            WindowAndroid windowAndroid, int[] contentSettingsTypes) {
        SparseArray<String> permissionsToRequest = new SparseArray<String>();
        for (int i = 0; i < contentSettingsTypes.length; i++) {
            String permission = PrefServiceBridge.getAndroidPermissionForContentSetting(
                    contentSettingsTypes[i]);
            if (permission != null && !windowAndroid.hasPermission(permission)) {
                permissionsToRequest.append(contentSettingsTypes[i], permission);
            }
        }
        return permissionsToRequest;
    }

    private static int getDeniedPermissionResourceId(
            SparseArray<String> contentSettingsTypesToPermissionsMap, String permission) {
        int contentSettingsType = 0;
        // SparseArray#indexOfValue uses == instead of .equals, so we need to manually iterate
        // over the list.
        for (int i = 0; i < contentSettingsTypesToPermissionsMap.size(); i++) {
            if (permission.equals(contentSettingsTypesToPermissionsMap.valueAt(i))) {
                contentSettingsType = contentSettingsTypesToPermissionsMap.keyAt(i);
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

    /**
     * Returns true if any of the permissions in contentSettingsTypes must be requested from the
     * system. Otherwise returns false.
     *
     * If true is returned, this method will asynchronously request the necessary permissions using
     * a dialog, running methods on the RequestDelegate when the user has made a decision.
     */
    public static boolean requestAndroidPermissions(
            final Tab tab, final int[] contentSettingsTypes, final RequestDelegate delegate) {
        final WindowAndroid windowAndroid = tab.getWindowAndroid();
        if (windowAndroid == null) return false;

        final SparseArray<String> contentSettingsTypesToPermissionsMap =
                generatePermissionsMapping(windowAndroid, contentSettingsTypes);

        if (contentSettingsTypesToPermissionsMap.size() == 0) return false;

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
                            deniedStringId = getDeniedPermissionResourceId(
                                    contentSettingsTypesToPermissionsMap, permissions[i]);
                        }

                        if (windowAndroid.canRequestPermission(permissions[i])) {
                            requestableCount++;
                        }
                    }
                }

                Activity activity = windowAndroid.getActivity().get();
                if (deniedCount > 0 && requestableCount > 0 && activity != null) {
                    View view = activity.getLayoutInflater().inflate(
                            R.layout.update_permissions_dialog, null);
                    TextView dialogText = (TextView) view.findViewById(R.id.text);
                    dialogText.setText(deniedStringId);

                    AlertDialog.Builder builder =
                            new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
                    builder.setView(view);
                    builder.setPositiveButton(R.string.infobar_update_permissions_button_text,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    requestAndroidPermissions(tab, contentSettingsTypes, delegate);
                                }
                            });
                    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            delegate.onAndroidPermissionCanceled();
                        }
                    });
                    builder.create().show();
                } else if (deniedCount > 0) {
                    delegate.onAndroidPermissionCanceled();
                } else {
                    delegate.onAndroidPermissionAccepted();
                }
            }
        };

        String[] permissionsToRequest = new String[contentSettingsTypesToPermissionsMap.size()];
        for (int i = 0; i < contentSettingsTypesToPermissionsMap.size(); i++) {
            permissionsToRequest[i] = contentSettingsTypesToPermissionsMap.valueAt(i);
        }
        windowAndroid.requestPermissions(permissionsToRequest, callback);
        return true;
    }
}
