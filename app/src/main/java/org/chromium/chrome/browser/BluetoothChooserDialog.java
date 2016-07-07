// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.omnibox.OmniboxUrlEmphasizer;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * A dialog for picking available Bluetooth devices. This dialog is shown when a website requests to
 * pair with a certain class of Bluetooth devices (e.g. through a bluetooth.requestDevice Javascript
 * call).
 */
public class BluetoothChooserDialog implements ItemChooserDialog.ItemSelectedCallback {
    Context mContext;

    // The dialog to show to let the user pick a device.
    ItemChooserDialog mItemChooserDialog;

    // The origin for the site wanting to pair with the bluetooth devices.
    String mOrigin;

    // The security level of the connection to the site wanting to pair with the
    // bluetooth devices. For valid values see SecurityStateModel::SecurityLevel.
    int mSecurityLevel;

    // A pointer back to the native part of the implementation for this dialog.
    long mNativeBluetoothChooserDialogPtr;

    // The type of link that is shown within the dialog.
    private enum LinkType {
        EXPLAIN_BLUETOOTH,
        EXPLAIN_PARING,
        ADAPTER_OFF,
        ADAPTER_OFF_HELP,
        RESTART_SEARCH,
    }

    /**
     * Creates the BluetoothChooserDialog and displays it (and starts waiting for data).
     *
     * @param context Context which is used for launching a dialog.
     */
    private BluetoothChooserDialog(Context context, String origin, int securityLevel,
            long nativeBluetoothChooserDialogPtr) {
        mContext = context;
        mOrigin = origin;
        mSecurityLevel = securityLevel;
        mNativeBluetoothChooserDialogPtr = nativeBluetoothChooserDialogPtr;
    }

    /**
     * Show the BluetoothChooserDialog.
     */
    private void show() {
        // Emphasize the origin.
        Profile profile = Profile.getLastUsedProfile();
        SpannableString origin = new SpannableString(mOrigin);
        OmniboxUrlEmphasizer.emphasizeUrl(
                origin, mContext.getResources(), profile, mSecurityLevel, false, true, true);
        // Construct a full string and replace the origin text with emphasized version.
        String message = mContext.getString(R.string.bluetooth_dialog_title, mOrigin);
        SpannableString title = SpanApplier.applySpans(
                message, new SpanInfo("<link>", "</link>",
                        new NoUnderlineClickableSpan(LinkType.EXPLAIN_PARING, mContext)));
        int start = title.toString().indexOf(mOrigin);
        TextUtils.copySpansFrom(origin, 0, origin.length(), Object.class, title, start);

        message = mContext.getString(R.string.bluetooth_not_found);
        SpannableString noneFound = SpanApplier.applySpans(
                message, new SpanInfo("<link>", "</link>",
                        new NoUnderlineClickableSpan(LinkType.RESTART_SEARCH, mContext)));

        String searching = mContext.getString(R.string.bluetooth_searching);
        String positiveButton = mContext.getString(R.string.bluetooth_confirm_button);

        SpannableString status = SpanApplier.applySpans(
                mContext.getString(R.string.bluetooth_not_seeing_it),
                new SpanInfo("<link1>", "</link1>",
                        new NoUnderlineClickableSpan(LinkType.RESTART_SEARCH, mContext)),
                new SpanInfo("<link2>", "</link2>",
                        new NoUnderlineClickableSpan(LinkType.EXPLAIN_BLUETOOTH, mContext)));

        SpannableString errorMessage = SpanApplier.applySpans(
                mContext.getString(R.string.bluetooth_adapter_off),
                new SpanInfo("<link>", "</link>",
                        new NoUnderlineClickableSpan(LinkType.ADAPTER_OFF, mContext)));
        SpannableString errorStatus = SpanApplier.applySpans(
                mContext.getString(R.string.bluetooth_adapter_off_help),
                new SpanInfo("<link>", "</link>",
                        new NoUnderlineClickableSpan(LinkType.ADAPTER_OFF_HELP, mContext)));

        ItemChooserDialog.ItemChooserLabels labels = new ItemChooserDialog.ItemChooserLabels(
                title, searching, noneFound, status, errorMessage, errorStatus, positiveButton);
        mItemChooserDialog = new ItemChooserDialog(mContext, this, labels);
    }

    @Override
    public void onItemSelected(String id) {
        if (mNativeBluetoothChooserDialogPtr != 0) {
            nativeOnDeviceSelected(mNativeBluetoothChooserDialogPtr, id);
        }
    }

    /**
     * A helper class to show a clickable link with underlines turned off.
     */
    private class NoUnderlineClickableSpan extends ClickableSpan {
        // The type of link this span represents.
        private LinkType mLinkType;

        private Context mContext;

        NoUnderlineClickableSpan(LinkType linkType, Context context) {
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
                    nativeShowBluetoothOverviewLink(mNativeBluetoothChooserDialogPtr);
                    closeDialog();
                    break;
                }
                case EXPLAIN_PARING: {
                    nativeShowBluetoothPairingLink(mNativeBluetoothChooserDialogPtr);
                    closeDialog();
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

        @Override
        public void updateDrawState(TextPaint textPaint) {
            super.updateDrawState(textPaint);
            textPaint.bgColor = Color.TRANSPARENT;
            textPaint.setUnderlineText(false);
        }
    }

    @CalledByNative
    private static BluetoothChooserDialog create(WindowAndroid windowAndroid, String origin,
            int securityLevel, long nativeBluetoothChooserDialogPtr) {
        Activity activity = windowAndroid.getActivity().get();
        assert activity != null;
        BluetoothChooserDialog dialog = new BluetoothChooserDialog(
                activity, origin, securityLevel, nativeBluetoothChooserDialogPtr);
        dialog.show();
        return dialog;
    }

    @CalledByNative
    private void addDevice(String deviceId, String deviceName) {
        List<ItemChooserDialog.ItemChooserRow> devices =
                new ArrayList<ItemChooserDialog.ItemChooserRow>();
        devices.add(new ItemChooserDialog.ItemChooserRow(deviceId, deviceName));
        mItemChooserDialog.showList(devices);
    }

    @CalledByNative
    private void closeDialog() {
        mNativeBluetoothChooserDialogPtr = 0;
        mItemChooserDialog.dismiss();
    }

    @CalledByNative
    private void removeDevice(String deviceId) {
        mItemChooserDialog.setEnabled(deviceId, false);
    }

    @CalledByNative
    private void notifyAdapterTurnedOff() {
        mItemChooserDialog.setErrorState();
    }

    private native void nativeOnDeviceSelected(long nativeBluetoothChooserAndroid, String deviceId);
    private native void nativeRestartSearch(long nativeBluetoothChooserAndroid);
    // Help links.
    private native void nativeShowBluetoothOverviewLink(long nativeBluetoothChooserAndroid);
    private native void nativeShowBluetoothPairingLink(long nativeBluetoothChooserAndroid);
    private native void nativeShowBluetoothAdapterOffLink(long nativeBluetoothChooserAndroid);
}
