// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.PasswordUIView;
import org.chromium.chrome.browser.PasswordUIView.PasswordListObserver;

/**
 * Password entry editor that allows to view and delete passwords stored in Chrome.
 */
public class PasswordEntryEditor extends Fragment {

    // ID of this name/password or exception.
    private int mID;

    // If true this is an exception site (never save here).
    // If false this represents a saved name/password.
    private boolean mException;

    public static final String VIEW_PASSWORDS = "view-passwords";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ChromeFeatureList.isEnabled(VIEW_PASSWORDS)) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v;
        if (ChromeFeatureList.isEnabled(VIEW_PASSWORDS)) {
            v = inflater.inflate(R.layout.password_entry_editor_interactive, container, false);
        } else {
            v = inflater.inflate(R.layout.password_entry_editor, container, false);
        }
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
        if (!ChromeFeatureList.isEnabled(VIEW_PASSWORDS)) {
            hookupCancelDeleteButtons(v);
        }
        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.password_entry_editor_action_bar_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_delete_saved_password) {
            removeItem();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Delete was clicked.
    private void removeItem() {
        final PasswordUIView passwordUIView = new PasswordUIView();
        final PasswordListObserver passwordDeleter = new PasswordListObserver() {
            @Override
            public void passwordListAvailable(int count) {
                if (!mException) {
                    passwordUIView.removeSavedPasswordEntry(mID);
                    passwordUIView.destroy();
                    getActivity().finish();
                }
            }

            @Override
            public void passwordExceptionListAvailable(int count) {
                if (mException) {
                    passwordUIView.removeSavedPasswordException(mID);
                    passwordUIView.destroy();
                    getActivity().finish();
                }
            }
        };

        passwordUIView.addObserver(passwordDeleter);
        passwordUIView.updatePasswordLists();
    }

    private void hookupCancelDeleteButtons(View v) {
        final Button deleteButton = (Button) v.findViewById(R.id.password_entry_editor_delete);
        final Button cancelButton = (Button) v.findViewById(R.id.password_entry_editor_cancel);

        deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeItem();
                    deleteButton.setEnabled(false);
                    cancelButton.setEnabled(false);
                }
            });

        cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getActivity().finish();
                }
            });
    }
}
