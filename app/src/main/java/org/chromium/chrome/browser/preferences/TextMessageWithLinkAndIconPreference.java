// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint.FontMetrics;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;

/**
 * A text message whose summary can contain a link. The link should be denoted by "<link></link>"
 * tags and the action upon its clicking is defined by |setLinkClickDelegate()|.
 */
public class TextMessageWithLinkAndIconPreference extends TextMessagePreference {
    private Runnable mLinkClickDelegate;
    private boolean mNoBottomSpacing;

    /**
     * Constructor for inflating from XML.
     */
    public TextMessageWithLinkAndIconPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.text_message_with_link_and_icon_preference);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.TextMessageWithLinkAndIconPreference);
        mNoBottomSpacing = a.getBoolean(
                R.styleable.TextMessageWithLinkAndIconPreference_noBottomSpacing, false);
        a.recycle();
    }

    /**
     * @param delegate A delegate to handle link click.
     */
    public void setLinkClickDelegate(Runnable delegate) {
        mLinkClickDelegate = delegate;
    }

    @Override
    public void setSummary(CharSequence summary) {
        // If there is no link in the summary, invoke the default behavior.
        String summaryString = summary.toString();
        if (!summaryString.contains("<link>") || !summaryString.contains("</link>")) {
            super.setSummary(summary);
            return;
        }

        // Linkify <link></link> span.
        final SpannableString summaryWithLink = SpanApplier.applySpans(summaryString,
                new SpanApplier.SpanInfo("<link>", "</link>", new NoUnderlineClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        if (mLinkClickDelegate != null) mLinkClickDelegate.run();
                    }
                }));

        super.setSummary(summaryWithLink);
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        if (mNoBottomSpacing) {
            ApiCompatibilityUtils.setPaddingRelative(
                    view,
                    ApiCompatibilityUtils.getPaddingStart(view),
                    view.getPaddingTop(),
                    ApiCompatibilityUtils.getPaddingEnd(view),
                    0);
        }

        ((TextView) view.findViewById(android.R.id.summary)).setMovementMethod(
                LinkMovementMethod.getInstance());

        // The icon is aligned to the top of the text view, which can be higher than the
        // ascender line of the text, and makes it look aligned improperly.
        TextView textView = (TextView) view.findViewById(
                getTitle() != null ? android.R.id.title : android.R.id.summary);
        FontMetrics metrics = textView.getPaint().getFontMetrics();
        ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
        ApiCompatibilityUtils.setPaddingRelative(
                icon, 0, (int) java.lang.Math.ceil(metrics.ascent - metrics.top), 0, 0);

        return view;
    }
}
