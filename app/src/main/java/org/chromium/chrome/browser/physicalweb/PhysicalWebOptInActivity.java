// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * This activity invites the user to opt-in to the Physical Web feature.
 */
public class PhysicalWebOptInActivity extends AppCompatActivity {
    private static final String EXTRA_CUSTOM_TABS_SESSION =
            "android.support.customtabs.extra.SESSION";
    private static final String PHYSICAL_WEB_LEARN_MORE_URL =
            "https://support.google.com/chrome/answer/6239299/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.physical_web_optin);
        PhysicalWebUma.onOptInNotificationPressed(this);

        TextView description = (TextView) findViewById(R.id.physical_web_optin_description);
        description.setMovementMethod(LinkMovementMethod.getInstance());
        description.setText(getDescriptionText());

        Button declineButton = (Button) findViewById(R.id.physical_web_decline);
        declineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PhysicalWebUma.onOptInDeclineButtonPressed(PhysicalWebOptInActivity.this);
                PrivacyPreferencesManager.getInstance().setPhysicalWebEnabled(false);
                finish();
            }
        });

        Button enableButton = (Button) findViewById(R.id.physical_web_enable);
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PhysicalWebUma.onOptInEnableButtonPressed(PhysicalWebOptInActivity.this);
                PrivacyPreferencesManager.getInstance().setPhysicalWebEnabled(true);
                startActivity(createListUrlsIntent(PhysicalWebOptInActivity.this));
                finish();
            }
        });
    }

    private static Intent createListUrlsIntent(Context context) {
        Intent intent = new Intent(context, ListUrlsActivity.class);
        intent.putExtra(ListUrlsActivity.REFERER_KEY,
                ListUrlsActivity.OPTIN_REFERER);
        return intent;
    }

    private SpannableString getDescriptionText() {
        return SpanApplier.applySpans(
                getString(R.string.physical_web_optin_description),
                new SpanInfo("<learnmore>", "</learnmore>", new ClickableSpan() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(PHYSICAL_WEB_LEARN_MORE_URL));
                        // Add the SESSION extra to indicate we want a Chrome custom tab. This
                        // allows the help page to open in the same task as the opt-in activity so
                        // they can share a back stack.
                        String session = null;
                        intent.putExtra(EXTRA_CUSTOM_TABS_SESSION, session);
                        PhysicalWebOptInActivity.this.startActivity(intent);
                    }

                    @Override
                    public void updateDrawState(TextPaint ds) {
                        // Color links but do not underline them.
                        ds.setColor(ds.linkColor);
                    }
                }));
    }
}
