// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.UiUtils;

/**
 * A helper for editing bookmarks.
*/
public class EditBookmarkHelper {
    /**
     * Opens the standard "edit bookmark" dialog.
     * @param bookmarkId ID of the bookmark to be edited
     * @param isFolder True if it is a folder
     */
    public static void editBookmark(Context context, long bookmarkId, boolean isFolder) {
        Intent intent = new Intent(context, ManageBookmarkActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_IS_FOLDER, isFolder);
        intent.putExtra(ManageBookmarkActivity.BOOKMARK_INTENT_ID, bookmarkId);
        context.startActivity(intent);
    }

    /**
     * Opens an "edit bookmark" dialog for a partner bookmark.
     * @param bookmarkId ID of the bookmark to be edited
     * @param oldTitle Old title of the bookmark
     * @param isFolder True if it is a folder
     */
    public static void editPartnerBookmark(Context context, final Profile profile,
            final long bookmarkId, String oldTitle, boolean isFolder) {
        // TODO(aruslan): http://crbug.com/313853
        View view = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.single_line_edit_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(isFolder ? R.string.edit_folder : R.string.edit_bookmark)
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        });
        final AlertDialog dialog = builder.create();

        // On click of "Save", changes are committed.
        // Default title is the title of the bookmark.
        // OK button is disabled if the title text is empty.
        TextView titleLabel = (TextView) view.findViewById(R.id.title);
        final EditText input = (EditText) view.findViewById(R.id.text);
        titleLabel.setText(R.string.bookmark_name);
        input.setText(oldTitle);
        input.setSelection(0, oldTitle.length());
        input.requestFocus();
        input.post(new Runnable() {
            @Override
            public void run() {
                UiUtils.showKeyboard(input);
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable editableText) {
                if (TextUtils.isEmpty(editableText)) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                } else {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                }
            }
        });
        dialog.setView(view);
        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                context.getResources().getString(R.string.save),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String newTitle = input.getText().toString();
                        nativeSetPartnerBookmarkTitle(profile, bookmarkId, newTitle);
                    }
                });
        dialog.show();
    }

    // JNI
    private static native void nativeSetPartnerBookmarkTitle(
            Profile profile, long partnerBookmarkId, String newTitle);
}
