// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.ui.widget.Toast;

/**
 * The promo screen encouraging users to enable Data Saver.
 */
public class DataReductionPromoScreen extends Dialog implements View.OnClickListener,
        DialogInterface.OnDismissListener {
    private int mState;

    private static View getContentView(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.data_reduction_promo_screen, null);
    }

    /**
     * Launch the data reduction promo, if it needs to be displayed.
     */
    public static void launchDataReductionPromo(Activity parentActivity) {
        // The promo is displayed if Chrome is launched directly (i.e., not with the intent to
        // navigate to and view a URL on startup), the instance is part of the field trial,
        // and the promo has not been displayed before.
        if (!DataReductionPromoUtils.canShowPromos()) return;
        if (DataReductionPromoUtils.getDisplayedFreOrSecondRunPromo()) return;
        // Showing the promo dialog in multiwindow mode is broken on Galaxy Note devices:
        // http://crbug.com/354696. If we're in multiwindow mode, save the dialog for later.
        if (MultiWindowUtils.getInstance().isLegacyMultiWindow(parentActivity)) return;

        DataReductionPromoScreen promoScreen = new DataReductionPromoScreen(parentActivity);
        promoScreen.setOnDismissListener(promoScreen);
        promoScreen.show();
    }

    /**
     * DataReductionPromoScreen constructor.
     *
     * @param context An Android context.
     */
    public DataReductionPromoScreen(Context context) {
        super(context, R.style.DataReductionPromoScreenDialog);
        setContentView(getContentView(context), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // Remove the shadow from the enable button.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Button enableButton = (Button) findViewById(R.id.enable_button);
            enableButton.setStateListAnimator(null);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the window full screen otherwise the flip animation will frame-skip.
        getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        addListenerOnButton();

        mState = DataReductionProxyUma.ACTION_DISMISSED;
    }

    private void addListenerOnButton() {
        int [] interactiveViewIds = new int[] {
            R.id.no_thanks_button,
            R.id.enable_button,
            R.id.close_button
        };

        for (int interactiveViewId : interactiveViewIds) {
            findViewById(interactiveViewId).setOnClickListener(this);
        }
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.no_thanks_button) {
            handleCloseButtonPressed();
        } else if (id == R.id.enable_button) {
            mState = DataReductionProxyUma.ACTION_ENABLED;
            handleEnableButtonPressed();
        } else if (id == R.id.close_button) {
            handleCloseButtonPressed();
        } else {
            assert false : "Unhandled onClick event";
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        DataReductionPromoUtils.saveFreOrSecondRunPromoDisplayed();
    }

    private void handleEnableButtonPressed() {
        DataReductionProxySettings.getInstance().setDataReductionProxyEnabled(
                getContext(), true);
        dismiss();
        Toast.makeText(getContext(), getContext().getString(R.string.data_reduction_enabled_toast),
                Toast.LENGTH_LONG).show();
    }

    private void handleCloseButtonPressed() {
        dismiss();
    }

    @Override
    public void dismiss() {
        if (mState < DataReductionProxyUma.ACTION_INDEX_BOUNDARY) {
            DataReductionProxyUma.dataReductionProxyUIAction(mState);
            mState = DataReductionProxyUma.ACTION_INDEX_BOUNDARY;
        }
        super.dismiss();
    }
}
