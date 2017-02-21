// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.BuildInfo;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.components.sync.PassphraseType;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Dialog to ask the user select what type of password to use for encryption.
 */
public class PassphraseTypeDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener, OnItemClickListener {
    private static final String TAG = "PassphraseTypeDialogFragment";

    interface Listener {
        void onPassphraseTypeSelected(PassphraseType type);
    }

    private String[] getDisplayNames(List<PassphraseType> passphraseTypes) {
        String[] displayNames = new String[passphraseTypes.size()];
        for (int i = 0; i < displayNames.length; i++) {
            displayNames[i] = textForPassphraseType(passphraseTypes.get(i));
        }
        return displayNames;
    }

    private String textForPassphraseType(PassphraseType type) {
        switch (type) {
            case IMPLICIT_PASSPHRASE:  // Intentional fall through.
            case KEYSTORE_PASSPHRASE:
                return getString(R.string.sync_passphrase_type_keystore);
            case FROZEN_IMPLICIT_PASSPHRASE:
                String passphraseDate = getPassphraseDateStringFromArguments();
                String frozenPassphraseString = getString(R.string.sync_passphrase_type_frozen);
                return String.format(frozenPassphraseString, passphraseDate);
            case CUSTOM_PASSPHRASE:
                return getString(R.string.sync_passphrase_type_custom);
            default:
                return "";
        }
    }

    private Adapter createAdapter(PassphraseType currentType) {
        List<PassphraseType> passphraseTypes =
                new ArrayList<PassphraseType>(currentType.getVisibleTypes());
        return new Adapter(passphraseTypes, getDisplayNames(passphraseTypes));
    }

    /**
     * The adapter for our ListView; only visible for testing purposes.
     */
    @VisibleForTesting
    public class Adapter extends ArrayAdapter<String> {

        private final List<PassphraseType> mPassphraseTypes;

        /**
         * Do not call this constructor directly. Instead use
         * {@link PassphraseTypeDialogFragment#createAdapter}.
         */
        private Adapter(List<PassphraseType> passphraseTypes, String[] displayStrings) {
            super(getActivity(), R.layout.passphrase_type_item, displayStrings);
            mPassphraseTypes = passphraseTypes;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return getType(position).internalValue();
        }

        public PassphraseType getType(int position) {
            return mPassphraseTypes.get(position);
        }

        public int getPositionForType(PassphraseType type) {
            return mPassphraseTypes.indexOf(type);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CheckedTextView view = (CheckedTextView) super.getView(position, convertView, parent);
            PassphraseType positionType = getType(position);
            PassphraseType currentType = getCurrentTypeFromArguments();
            Set<PassphraseType> allowedTypes =
                    currentType.getAllowedTypes(getIsEncryptEverythingAllowedFromArguments());

            // Set the item to checked it if it is the currently selected encryption type.
            view.setChecked(positionType == currentType);
            // Allow user to click on enabled types for the current type.
            view.setEnabled(allowedTypes.contains(positionType));
            return view;
        }
    }

    /**
     * This argument should contain a single value of type {@link PassphraseType}.
     */
    private static final String ARG_CURRENT_TYPE = "arg_current_type";

    private static final String ARG_PASSPHRASE_TIME = "arg_passphrase_time";

    private static final String ARG_IS_ENCRYPT_EVERYTHING_ALLOWED =
            "arg_is_encrypt_everything_allowed";

    static PassphraseTypeDialogFragment create(
            PassphraseType currentType, long passphraseTime, boolean isEncryptEverythingAllowed) {
        PassphraseTypeDialogFragment dialog = new PassphraseTypeDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_CURRENT_TYPE, currentType);
        args.putLong(ARG_PASSPHRASE_TIME, passphraseTime);
        args.putBoolean(ARG_IS_ENCRYPT_EVERYTHING_ALLOWED, isEncryptEverythingAllowed);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.sync_passphrase_types, null);

        // Configure the passphrase type list
        ListView list = (ListView) v.findViewById(R.id.passphrase_types);
        Adapter adapter = createAdapter(getCurrentTypeFromArguments());
        list.setAdapter(adapter);
        list.setId(R.id.passphrase_type_list);
        list.setOnItemClickListener(this);
        list.setDividerHeight(0);
        PassphraseType currentType = getCurrentTypeFromArguments();
        list.setSelection(adapter.getPositionForType(currentType));

        // Configure the hint to reset the passphrase settings
        // Only show this hint if encryption has been set to use sync passphrase
        if (currentType == PassphraseType.CUSTOM_PASSPHRASE) {
            TextView instructionsView = (TextView) v.findViewById(R.id.reset_sync_text);
            instructionsView.setVisibility(View.VISIBLE);
            instructionsView.setMovementMethod(LinkMovementMethod.getInstance());
            instructionsView.setText(getResetText());
        }

        // Create and return the dialog
        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setNegativeButton(R.string.cancel, this)
                .setTitle(R.string.sync_passphrase_type_title)
                .setView(v)
                .create();
    }

    private SpannableString getResetText() {
        final Context context = getActivity();
        return SpanApplier.applySpans(
                context.getString(R.string.sync_passphrase_encryption_reset_instructions),
                new SpanInfo("<resetlink>", "</resetlink>", new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        Uri syncDashboardUrl = Uri.parse(
                                context.getText(R.string.sync_dashboard_url).toString());
                        Intent intent = new Intent(Intent.ACTION_VIEW, syncDashboardUrl);
                        intent.setPackage(BuildInfo.getPackageName(context));
                        IntentUtils.safePutBinderExtra(
                                intent, CustomTabsIntent.EXTRA_SESSION, null);
                        context.startActivity(intent);
                    }
                }));
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            dismiss();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long typeId) {
        PassphraseType currentType = getCurrentTypeFromArguments();
        // We know this conversion from long to int is safe, because it represents very small
        // enum values.
        PassphraseType type = PassphraseType.fromInternalValue((int) typeId);
        boolean isEncryptEverythingAllowed = getIsEncryptEverythingAllowedFromArguments();
        if (currentType.getAllowedTypes(isEncryptEverythingAllowed).contains(type)) {
            if (typeId != currentType.internalValue()) {
                Listener listener = (Listener) getTargetFragment();
                listener.onPassphraseTypeSelected(type);
            }
            dismiss();
        }
    }

    @VisibleForTesting
    public PassphraseType getCurrentTypeFromArguments() {
        PassphraseType currentType = getArguments().getParcelable(ARG_CURRENT_TYPE);
        if (currentType == null) {
            throw new IllegalStateException("Unable to find argument with current type.");
        }
        return currentType;
    }

    private String getPassphraseDateStringFromArguments() {
        long passphraseTime = getArguments().getLong(ARG_PASSPHRASE_TIME);
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        return df.format(new Date(passphraseTime));
    }

    private boolean getIsEncryptEverythingAllowedFromArguments() {
        return getArguments().getBoolean(ARG_IS_ENCRYPT_EVERYTHING_ALLOWED);
    }
}
