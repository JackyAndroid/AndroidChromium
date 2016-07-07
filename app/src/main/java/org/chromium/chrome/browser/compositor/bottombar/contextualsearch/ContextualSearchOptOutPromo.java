// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.ui.resources.dynamics.ViewResourceAdapter;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 */
public class ContextualSearchOptOutPromo extends RelativeLayout {
    /**
     * The {@link ViewResourceAdapter} instance.
     */
    private ViewResourceAdapter mResourceAdapter;

    /**
     * The interface used to talk to the Panel.
     */
    private ContextualSearchPromoHost mHost;

    /**
     * The delegate that is used to communicate with the Panel.
     */
    public interface ContextualSearchPromoHost {
        /**
         * Notifies that the preference link has been clicked.
         */
        void onPromoPreferenceClick();

        /**
         * Notifies that the a button has been clicked.
         * @param accepted Whether the feature was accepted.
         */
        void onPromoButtonClick(boolean accepted);
    }

    /**
     * Constructs a new control container.
     * <p>
     * This constructor is used when inflating from XML.
     *
     * @param context The context used to build this view.
     * @param attrs The attributes used to determine how to construct this view.
     */
    public ContextualSearchOptOutPromo(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @return The {@link ViewResourceAdapter} that exposes this {@link View} as a CC resource.
     */
    public ViewResourceAdapter getResourceAdapter() {
        return mResourceAdapter;
    }

    /**
     * Sets the Promo Host.
     *
     * @param host A {@ContextualSearchPromoHost} instance.
     */
    public void setPromoHost(ContextualSearchPromoHost host) {
        mHost = host;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mResourceAdapter =
                new ViewResourceAdapter(findViewById(R.id.contextual_search_opt_out_promo));

        // Fill in text with link to Settings.
        TextView optOutText = (TextView) findViewById(R.id.contextual_search_opt_out_text);

        ClickableSpan settingsLink = new ClickableSpan() {
            @Override
            public void onClick(View view) {
                mHost.onPromoPreferenceClick();
            }

            // Disable underline on the link text.
            @Override
            public void updateDrawState(android.text.TextPaint textPaint) {
                super.updateDrawState(textPaint);
                textPaint.setUnderlineText(false);
            }
        };

        optOutText.setText(SpanApplier.applySpans(
                getResources().getString(R.string.contextual_search_short_description),
                new SpanInfo("<link>", "</link>", settingsLink)));
        optOutText.setMovementMethod(LinkMovementMethod.getInstance());

        // "No thanks" button.
        Button noThanksButton = (Button) findViewById(R.id.contextual_search_no_thanks_button);
        noThanksButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHost.onPromoButtonClick(false);
            }
        });

        // "Got it" button.
        Button gotItButton = (Button) findViewById(R.id.contextual_search_got_it_button);
        gotItButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHost.onPromoButtonClick(true);
            }
        });

        setVisibility(View.INVISIBLE);
    }

    /**
     * Gets the Promo height for the given width.
     *
     * @param width The given width.
     * @return The Promo height for the given width.
     */
    public int getHeightForGivenWidth(int width) {
        // The Promo will be as wide as possible (same width as the Panel).
        int widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        // But the height will depend on how big is the Promo text.
        int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        // Calculates the height.
        measure(widthMeasureSpec, heightMeasureSpec);

        return getMeasuredHeight();
    }
}
