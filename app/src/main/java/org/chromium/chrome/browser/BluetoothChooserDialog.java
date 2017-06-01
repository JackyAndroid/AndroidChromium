// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.omnibox.OmniboxUrlEmphasizer;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.location.LocationUtils;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * A dialog for picking available Bluetooth devices. This dialog is shown when a website requests to
 * pair with a certain class of Bluetooth devices (e.g. through a bluetooth.requestDevice Javascript
 * call).
 *
 * The dialog is shown by create() or show(), and always runs finishDialog() as it's closing.
 */
public class BluetoothChooserDialog
        implements ItemChooserDialog.ItemSelectedCallback, WindowAndroid.PermissionCallback {
    // These constants match BluetoothChooserAndroid::ShowDiscoveryState, and are used in
    // notifyDiscoveryState().
    static final int DISCOVERY_FAILED_TO_START = 0;
    static final int DISCOVERING = 1;
    static final int DISCOVERY_IDLE = 2;

    // Values passed to nativeOnDialogFinished:eventType, and only used in the native function.
    static final int DIALOG_FINISHED_DENIED_PERMISSION = 0;
    static final int DIALOG_FINISHED_CANCELLED = 1;
    static final int DIALOG_FINISHED_SELECTED = 2;

    // The window that owns this dialog.
    final WindowAndroid mWindowAndroid;

    // Always equal to mWindowAndroid.getActivity().get(), but stored separately to make sure it's
    // not GC'ed.
    final Activity mActivity;

    // The dialog to show to let the user pick a device.
    ItemChooserDialog mItemChooserDialog;

    // The origin for the site wanting to pair with the bluetooth devices.
    String mOrigin;

    // The security level of the connection to the site wanting to pair with the
    // bluetooth devices. For valid values see SecurityStateModel::SecurityLevel.
    int mSecurityLevel;

    // A pointer back to the native part of the implementation for this dialog.
    long mNativeBluetoothChooserDialogPtr;

    // Used to keep track of when the Mode Changed Receiver is registered.
    boolean mIsLocationModeChangedReceiverRegistered = false;

    @VisibleForTesting
    final BroadcastReceiver mLocationModeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!LocationManager.MODE_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }
            if (checkLocationServicesAndPermission()) {
                mItemChooserDialog.clear();
                nativeRestartSearch(mNativeBluetoothChooserDialogPtr);
            }
        }
    };

    // The type of link that is shown within the dialog.
    private enum LinkType {
        EXPLAIN_BLUETOOTH,
        ADAPTER_OFF,
        ADAPTER_OFF_HELP,
        REQUEST_LOCATION_PERMISSION,
        REQUEST_LOCATION_SERVICES,
        NEED_LOCATION_PERMISSION_HELP,
        RESTART_SEARCH,
    }

    /**
     * Creates the BluetoothChooserDialog.
     */
    @VisibleForTesting
    BluetoothChooserDialog(WindowAndroid windowAndroid, String origin, int securityLevel,
            long nativeBluetoothChooserDialogPtr) {
        mWindowAndroid = windowAndroid;
        mActivity = windowAndroid.getActivity().get();
        assert mActivity != null;
        mOrigin = origin;
        mSecurityLevel = securityLevel;
        mNativeBluetoothChooserDialogPtr = nativeBluetoothChooserDialogPtr;
    }

    /**
     * Show the BluetoothChooserDialog.
     */
    @VisibleForTesting
    void show() {
        // Emphasize the origin.
        Profile profile = Profile.getLastUsedProfile();
        SpannableString origin = new SpannableString(mOrigin);
        OmniboxUrlEmphasizer.emphasizeUrl(
                origin, mActivity.getResources(), profile, mSecurityLevel, false, true, true);
        // Construct a full string and replace the origin text with emphasized version.
        SpannableString title =
                new SpannableString(mActivity.getString(R.string.bluetooth_dialog_title, mOrigin));
        int start = title.toString().indexOf(mOrigin);
        TextUtils.copySpansFrom(origin, 0, origin.length(), Object.class, title, start);

        String noneFound = mActivity.getString(R.string.bluetooth_not_found);

        SpannableString searching = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_searching),
                new SpanInfo("<link>", "</link>",
                        new BluetoothClickableSpan(LinkType.EXPLAIN_BLUETOOTH, mActivity)));

        String positiveButton = mActivity.getString(R.string.bluetooth_confirm_button);

        SpannableString statusIdleNoneFound = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_not_seeing_it_idle_none_found),
                new SpanInfo("<link1>", "</link1>",
                        new BluetoothClickableSpan(LinkType.EXPLAIN_BLUETOOTH, mActivity)),
                new SpanInfo("<link2>", "</link2>",
                        new BluetoothClickableSpan(LinkType.RESTART_SEARCH, mActivity)));

        SpannableString statusActive = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_not_seeing_it),
                new SpanInfo("<link>", "</link>",
                        new BluetoothClickableSpan(LinkType.EXPLAIN_BLUETOOTH, mActivity)));

        SpannableString statusIdleSomeFound = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_not_seeing_it_idle_some_found),
                new SpanInfo("<link1>", "</link1>",
                        new BluetoothClickableSpan(LinkType.EXPLAIN_BLUETOOTH, mActivity)),
                new SpanInfo("<link2>", "</link2>",
                        new BluetoothClickableSpan(LinkType.RESTART_SEARCH, mActivity)));

        ItemChooserDialog.ItemChooserLabels labels =
                new ItemChooserDialog.ItemChooserLabels(title, searching, noneFound, statusActive,
                        statusIdleNoneFound, statusIdleSomeFound, positiveButton);
        mItemChooserDialog = new ItemChooserDialog(mActivity, this, labels);

        mActivity.registerReceiver(mLocationModeBroadcastReceiver,
                new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
        mIsLocationModeChangedReceiverRegistered = true;
    }

    // Called to report the dialog's results back to native code.
    private void finishDialog(int resultCode, String id) {
        if (mIsLocationModeChangedReceiverRegistered) {
            mActivity.unregisterReceiver(mLocationModeBroadcastReceiver);
            mIsLocationModeChangedReceiverRegistered = false;
        }

        if (mNativeBluetoothChooserDialogPtr != 0) {
            nativeOnDialogFinished(mNativeBluetoothChooserDialogPtr, resultCode, id);
        }
    }

    @Override
    public void onItemSelected(String id) {
        if (id.isEmpty()) {
            finishDialog(DIALOG_FINISHED_CANCELLED, "");
        } else {
            finishDialog(DIALOG_FINISHED_SELECTED, id);
        }
    }

    @Override
    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        // The chooser might have been closed during the request.
        if (mNativeBluetoothChooserDialogPtr == 0) {
            return;
        }

        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                if (checkLocationServicesAndPermission()) {
                    mItemChooserDialog.clear();
                    nativeRestartSearch(mNativeBluetoothChooserDialogPtr);
                }
                return;
            }
        }
        // If the location permission is not present, leave the currently-shown message in place.
    }

    // Returns true if Location Services is on and Chrome has permission to see the user's location.
    private boolean checkLocationServicesAndPermission() {
        final boolean havePermission = LocationUtils.getInstance().hasAndroidLocationPermission();
        final boolean locationServicesOn =
                LocationUtils.getInstance().isSystemLocationSettingEnabled();

        if (!havePermission
                && !mWindowAndroid.canRequestPermission(
                           Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // Immediately close the dialog because the user has asked Chrome not to request the
            // location permission.
            finishDialog(DIALOG_FINISHED_DENIED_PERMISSION, "");
            return false;
        }

        // Compute the message to show the user.
        final SpanInfo permissionSpan = new SpanInfo("<permission_link>", "</permission_link>",
                new BluetoothClickableSpan(LinkType.REQUEST_LOCATION_PERMISSION, mActivity));
        final SpanInfo servicesSpan = new SpanInfo("<services_link>", "</services_link>",
                new BluetoothClickableSpan(LinkType.REQUEST_LOCATION_SERVICES, mActivity));
        final SpannableString needLocationMessage;
        if (havePermission) {
            if (locationServicesOn) {
                // We don't need to request anything.
                return true;
            } else {
                needLocationMessage = SpanApplier.applySpans(
                        mActivity.getString(R.string.bluetooth_need_location_services_on),
                        servicesSpan);
            }
        } else {
            if (locationServicesOn) {
                needLocationMessage = SpanApplier.applySpans(
                        mActivity.getString(R.string.bluetooth_need_location_permission),
                        permissionSpan);
            } else {
                needLocationMessage = SpanApplier.applySpans(
                        mActivity.getString(
                                R.string.bluetooth_need_location_permission_and_services_on),
                        permissionSpan, servicesSpan);
            }
        }

        SpannableString needLocationStatus = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_need_location_permission_help),
                new SpanInfo("<link>", "</link>",
                        new BluetoothClickableSpan(
                                     LinkType.NEED_LOCATION_PERMISSION_HELP, mActivity)));

        mItemChooserDialog.setErrorState(needLocationMessage, needLocationStatus);
        return false;
    }

    private class BluetoothClickableSpan extends NoUnderlineClickableSpan {
        // The type of link this span represents.
        private LinkType mLinkType;

        private Context mContext;

        BluetoothClickableSpan(LinkType linkType, Context context) {
            mLinkType = linkType;
            mContext = context;
        }

        @Override
        public void onClick(View view) {
            if (mNativeBluetoothChooserDialogPtr == 0) {
                return;
            }

            switch (mLinkType) {
                case EXPLAIN_BLUETOOTH: {
                    // No need to close the dialog here because
                    // ShowBluetoothOverviewLink will close it.
                    // TODO(ortuno): The BluetoothChooserDialog should dismiss
                    // itself when a new tab is opened or the current tab navigates.
                    // https://crbug.com/588127
                    nativeShowBluetoothOverviewLink(mNativeBluetoothChooserDialogPtr);
                    break;
                }
                case ADAPTER_OFF: {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    mContext.startActivity(intent);
                    break;
                }
                case ADAPTER_OFF_HELP: {
                    nativeShowBluetoothAdapterOffLink(mNativeBluetoothChooserDialogPtr);
                    closeDialog();
                    break;
                }
                case REQUEST_LOCATION_PERMISSION: {
                    mWindowAndroid.requestPermissions(
                            new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                            BluetoothChooserDialog.this);
                    break;
                }
                case REQUEST_LOCATION_SERVICES: {
                    mContext.startActivity(
                            LocationUtils.getInstance().getSystemLocationSettingsIntent());
                    break;
                }
                case NEED_LOCATION_PERMISSION_HELP: {
                    nativeShowNeedLocationPermissionLink(mNativeBluetoothChooserDialogPtr);
                    closeDialog();
                    break;
                }
                case RESTART_SEARCH: {
                    mItemChooserDialog.clear();
                    nativeRestartSearch(mNativeBluetoothChooserDialogPtr);
                    break;
                }
                default:
                    assert false;
            }

            // Get rid of the highlight background on selection.
            view.invalidate();
        }
    }

    @CalledByNative
    private static BluetoothChooserDialog create(WindowAndroid windowAndroid, String origin,
            int securityLevel, long nativeBluetoothChooserDialogPtr) {
        if (!LocationUtils.getInstance().hasAndroidLocationPermission()
                && !windowAndroid.canRequestPermission(
                           Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // If we can't even ask for enough permission to scan for Bluetooth devices, don't open
            // the dialog.
            return null;
        }
        BluetoothChooserDialog dialog = new BluetoothChooserDialog(
                windowAndroid, origin, securityLevel, nativeBluetoothChooserDialogPtr);
        dialog.show();
        return dialog;
    }

    @VisibleForTesting
    @CalledByNative
    void addOrUpdateDevice(String deviceId, String deviceName) {
        mItemChooserDialog.addOrUpdateItem(deviceId, deviceName);
    }

    @VisibleForTesting
    @CalledByNative
    void closeDialog() {
        mNativeBluetoothChooserDialogPtr = 0;
        mItemChooserDialog.dismiss();
    }

    @VisibleForTesting
    @CalledByNative
    void removeDevice(String deviceId) {
        mItemChooserDialog.setEnabled(deviceId, false);
    }

    @VisibleForTesting
    @CalledByNative
    void notifyAdapterTurnedOff() {
        SpannableString adapterOffMessage = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_adapter_off),
                new SpanInfo("<link>", "</link>",
                        new BluetoothClickableSpan(LinkType.ADAPTER_OFF, mActivity)));
        SpannableString adapterOffStatus = SpanApplier.applySpans(
                mActivity.getString(R.string.bluetooth_adapter_off_help),
                new SpanInfo("<link>", "</link>",
                        new BluetoothClickableSpan(LinkType.ADAPTER_OFF_HELP, mActivity)));

        mItemChooserDialog.setErrorState(adapterOffMessage, adapterOffStatus);
    }

    @CalledByNative
    private void notifyAdapterTurnedOn() {
        mItemChooserDialog.clear();
    }

    @VisibleForTesting
    @CalledByNative
    void notifyDiscoveryState(int discoveryState) {
        switch (discoveryState) {
            case DISCOVERY_FAILED_TO_START: {
                // FAILED_TO_START might be caused by a missing Location
                // permission or by the Location service being turned off.
                // Check, and show a request if so.
                checkLocationServicesAndPermission();
                break;
            }
            case DISCOVERY_IDLE: {
                mItemChooserDialog.setIdleState();
                break;
            }
            default: {
                // TODO(jyasskin): Report the new state to the user.
                break;
            }
        }
    }

    @VisibleForTesting
    native void nativeOnDialogFinished(
            long nativeBluetoothChooserAndroid, int eventType, String deviceId);
    @VisibleForTesting
    native void nativeRestartSearch(long nativeBluetoothChooserAndroid);
    // Help links.
    @VisibleForTesting
    native void nativeShowBluetoothOverviewLink(long nativeBluetoothChooserAndroid);
    @VisibleForTesting
    native void nativeShowBluetoothAdapterOffLink(long nativeBluetoothChooserAndroid);
    @VisibleForTesting
    native void nativeShowNeedLocationPermissionLink(long nativeBluetoothChooserAndroid);
}
