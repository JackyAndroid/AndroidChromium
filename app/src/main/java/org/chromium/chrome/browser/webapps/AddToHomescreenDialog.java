// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Helper class showing the UI regarding Add to Homescreen. This class delegates
 * most of the logic to AddToHomescreenDialogHelper.
 */
public class AddToHomescreenDialog {
    private static AlertDialog sCurrentDialog;

    @VisibleForTesting
    public static AlertDialog getCurrentDialogForTest() {
        return sCurrentDialog;
    }

    /**
     * Shows the dialog for adding a shortcut to the home screen.
     * @param activity The current activity in which to create the dialog.
     * @param currentTab The current tab for which the shortcut is being created.
     */
    public static void show(final Activity activity, final Tab currentTab) {
        View view = activity.getLayoutInflater().inflate(
                R.layout.add_to_homescreen_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                .setTitle(R.string.menu_add_to_homescreen)
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        final AlertDialog dialog = builder.create();
        dialog.getDelegate().setHandleNativeActionModesEnabled(false);
        // On click of the menu item for "add to homescreen", an alert dialog pops asking the user
        // if the title needs to be edited. On click of "Add", shortcut is created. Default
        // title is the title of the page. OK button is disabled if the title text is empty.
        final View progressBarView = view.findViewById(R.id.spinny);
        final ImageView iconView = (ImageView) view.findViewById(R.id.icon);
        final EditText input = (EditText) view.findViewById(R.id.text);
        input.setEnabled(false);

        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (progressBarView.getMeasuredHeight() == input.getMeasuredHeight()
                        && input.getBackground() != null) {
                    // Force the text field to align better with the icon by accounting for the
                    // padding introduced by the background drawable.
                    input.getLayoutParams().height =
                            progressBarView.getMeasuredHeight() + input.getPaddingBottom();
                    v.requestLayout();
                    v.removeOnLayoutChangeListener(this);
                }
            }
        });

        final AddToHomescreenDialogHelper dialogHelper =
                new AddToHomescreenDialogHelper(activity.getApplicationContext(), currentTab);

        // Initializing the AddToHomescreenDialogHelper is asynchronous. Until
        // it is initialized, the UI will show a disabled text field and OK
        // buttons. They will be enabled and pre-filled as soon as the
        // onInitialized callback will be run. The user will still be able to
        // cancel the operation.
        dialogHelper.initialize(new AddToHomescreenDialogHelper.Observer() {
            @Override
            public void onUserTitleAvailable(String title) {
                input.setEnabled(true);
                input.setText(title);
            }

            @Override
            public void onIconAvailable(Bitmap icon) {
                progressBarView.setVisibility(View.GONE);
                iconView.setVisibility(View.VISIBLE);
                iconView.setImageBitmap(icon);
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
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
                activity.getResources().getString(R.string.add),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialogHelper.addShortcut(input.getText().toString());
                    }
                });

        // The "OK" button should only be shown when |dialogHelper| is
        // initialized, it should be kept disabled until then.
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(
                        dialogHelper.isInitialized());
            }
        });

        // We need to keep track of the current dialog for testing purposes.
        sCurrentDialog = dialog;
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                sCurrentDialog = null;
                dialogHelper.destroy();
            }
        });

        dialog.show();
    }
}
