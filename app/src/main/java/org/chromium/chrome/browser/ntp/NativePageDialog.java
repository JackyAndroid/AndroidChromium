// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.widget.AlwaysDismissedDialog;

/**
 * Displays a NativePage in a full screen dialog instead of like a regular Chrome page.
 */
public class NativePageDialog extends AlwaysDismissedDialog {
    private final NativePage mPage;

    public NativePageDialog(Activity ownerActivity, NativePage page) {
        super(ownerActivity, R.style.DialogWhenLarge);
        mPage = page;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout view = (FrameLayout) LayoutInflater.from(getContext()).inflate(
                R.layout.dialog_with_titlebar, null);
        view.addView(mPage.getView(), 0);
        setContentView(view);

        getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        TextView title = (TextView) view.findViewById(R.id.title);
        title.setText(mPage.getTitle());

        ImageButton closeButton = (ImageButton) view.findViewById(R.id.close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (mPage != null) mPage.destroy();
    }
}
