// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * A factory class for creating the "Sad Tab" view, which is shown in place of a crashed renderer.
 */
public class SadTabViewFactory {
    /**
     * @param context Context of the resulting Sad Tab view.
     * @param suggestionAction Action to be executed when user clicks "try these suggestions".
     * @param reloadButtonAction Action to be executed when Reload button is pressed.
     *                           (e.g., refreshing the page)
     * @return A "Sad Tab" view instance which is used in place of a crashed renderer.
     */
    public static View createSadTabView(
            Context context, final OnClickListener suggestionAction,
            OnClickListener reloadButtonAction) {
        // Inflate Sad tab and initialize.
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View sadTabView = inflater.inflate(R.layout.sad_tab, null);

        TextView messageText = (TextView) sadTabView.findViewById(R.id.sad_tab_message);
        messageText.setText(getHelpMessage(context, suggestionAction));
        messageText.setMovementMethod(LinkMovementMethod.getInstance());

        Button reloadButton = (Button) sadTabView.findViewById(R.id.sad_tab_reload_button);
        reloadButton.setOnClickListener(reloadButtonAction);

        return sadTabView;
    }

    /**
     * Construct and return help message to be displayed on R.id.sad_tab_message.
     * @param context Context of the resulting Sad Tab view. This is needed to load the strings.
     * @param suggestionAction Action to be executed when user clicks "try these suggestions".
     * @return Help message to be displayed on R.id.sad_tab_message.
     */
    private static CharSequence getHelpMessage(
            Context context, final OnClickListener suggestionAction) {
        String helpMessage = context.getString(R.string.sad_tab_message)
                + "\n\n" + context.getString(R.string.sad_tab_suggestions);
        ClickableSpan span = new ClickableSpan() {
            @Override
            public void onClick(View view) {
                suggestionAction.onClick(view);
            }

            // Disable underline on the link text.
            @Override
            public void updateDrawState(android.text.TextPaint textPaint) {
                super.updateDrawState(textPaint);
                textPaint.setUnderlineText(false);
            }
        };
        return SpanApplier.applySpans(helpMessage, new SpanInfo("<link>", "</link>", span));
    }
}
