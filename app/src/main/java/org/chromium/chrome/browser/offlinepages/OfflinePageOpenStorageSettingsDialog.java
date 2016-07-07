// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * A dialog that prompts the user to open the Storage page in Android settings.
 *
 * Shown when an offline page can't be saved because the device storage is almost full.
 */
public class OfflinePageOpenStorageSettingsDialog {
    public static void showDialog(final Context context) {
        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                if (id == AlertDialog.BUTTON_NEGATIVE) {
                    dialog.cancel();
                    return;
                }
                context.startActivity(new Intent(Settings.ACTION_MEMORY_CARD_SETTINGS));
            }
        };

        AlertDialog.Builder builder =
                new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                        .setTitle(R.string.offline_pages_free_up_space_title)
                        .setPositiveButton(R.string.offline_pages_view_button, listener)
                        .setNegativeButton(R.string.cancel, listener)
                        .setMessage(R.string.offline_pages_open_storage_settings_dialog_text);
        builder.create().show();
    }
}
