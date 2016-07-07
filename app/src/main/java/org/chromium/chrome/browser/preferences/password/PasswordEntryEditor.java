// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * Password entry editor that allows to view and delete passwords stored in Chrome.
 */
public class PasswordEntryEditor extends Fragment {

    // ID of this name/password or exception.
    private int mID;

    // If true this is an exception site (never save here).
    // If false this represents a saved name/password.
    private boolean mException;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v = inflater.inflate(R.layout.password_entry_editor, container, false);
        getActivity().setTitle(R.string.password_entry_editor_title);

        // Extras are set on this intent in class SavePasswordsPreferences.
        Bundle extras = getArguments();
        assert extras != null;
        mID = extras.getInt(SavePasswordsPreferences.PASSWORD_LIST_ID);
        String name = null;
        if (extras.containsKey(SavePasswordsPreferences.PASSWORD_LIST_NAME)) {
            name = extras.getString(SavePasswordsPreferences.PASSWORD_LIST_NAME);
        }
        TextView nameView = (TextView) v.findViewById(R.id.password_entry_editor_name);
        if (name != null) {
            nameView.setText(name);
        } else {
            nameView.setText(R.string.section_saved_passwords_exceptions);
            mException = true;
        }
        String url = extras.getString(SavePasswordsPreferences.PASSWORD_LIST_URL);
        TextView urlView = (TextView) v.findViewById(R.id.password_entry_editor_url);
        urlView.setText(url);

        hookupCancelDeleteButtons(v);
        return v;
    }

    // Delete was clicked.
    private void removeItem() {
        Intent data = new Intent();
        data.putExtra(SavePasswordsPreferences.PASSWORD_LIST_DELETED_ID, mID);
        data.putExtra(SavePasswordsPreferences.DELETED_ITEM_IS_EXCEPTION, mException);
        getActivity().setResult(SavePasswordsPreferences.RESULT_DELETE_PASSWORD, data);
    }

    private void hookupCancelDeleteButtons(View v) {
        Button button = (Button) v.findViewById(R.id.password_entry_editor_delete);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeItem();
                    getActivity().finish();
                }
            });

        button = (Button) v.findViewById(R.id.password_entry_editor_cancel);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getActivity().finish();
                }
            });
    }
}
