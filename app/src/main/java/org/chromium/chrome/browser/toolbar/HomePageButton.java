// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.widget.TintedImageButton;

/**
 * View that displays the home page button.
 */
public class HomePageButton extends TintedImageButton
        implements OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener {

    private static final int ID_REMOVE = 0;

    /** Constructor inflating from XML. */
    public HomePageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnCreateContextMenuListener(this);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, ID_REMOVE, Menu.NONE, R.string.remove)
                .setOnMenuItemClickListener(this);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        assert item.getItemId() == ID_REMOVE;
        HomepageManager.getInstance(getContext()).setPrefHomepageEnabled(false);
        return true;
    }
}
