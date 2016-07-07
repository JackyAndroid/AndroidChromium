// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.appmenu;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import org.chromium.base.metrics.RecordUserAction;

/**
 * A helper class for a menu button to decide when to show the app menu and forward touch
 * events.
 *
 * Simply construct this class and pass the class instance to a menu button as TouchListener.
 * Then this class will handle everything regarding showing app menu for you.
 */
public class AppMenuButtonHelper implements OnTouchListener {
    private final AppMenuHandler mMenuHandler;
    private Runnable mOnAppMenuShownListener;
    private boolean mIsTouchEventsBeingProcessed;

    /**
     * @param menuHandler MenuHandler implementation that can show and get the app menu.
     */
    public AppMenuButtonHelper(AppMenuHandler menuHandler) {
        mMenuHandler = menuHandler;
    }

    /**
     * @param onAppMenuShownListener This is called when the app menu is shown by this class.
     */
    public void setOnAppMenuShownListener(Runnable onAppMenuShownListener) {
        mOnAppMenuShownListener = onAppMenuShownListener;
    }

    /**
     * Shows the app menu if it is not already shown.
     * @param view View that initiated showing this menu. Normally it is a menu button.
     * @param startDragging Whether dragging is started.
     * @return Whether or not if the app menu is successfully shown.
     */
    private boolean showAppMenu(View view, boolean startDragging) {
        if (!mMenuHandler.isAppMenuShowing()
                && mMenuHandler.showAppMenu(view, startDragging)) {
            // Initial start dragging can be canceled in case if it was just single tap.
            // So we only record non-dragging here, and will deal with those dragging cases in
            // AppMenuDragHelper class.
            if (!startDragging) RecordUserAction.record("MobileUsingMenuBySwButtonTap");

            if (mOnAppMenuShownListener != null) {
                mOnAppMenuShownListener.run();
            }
            return true;
        }
        return false;
    }

    /**
     * @return Whether app menu is active. That is, AppMenu is showing or menu button is consuming
     *         touch events to prepare AppMenu showing.
     */
    public boolean isAppMenuActive() {
        return mIsTouchEventsBeingProcessed || mMenuHandler.isAppMenuShowing();
    }

    /**
     * Handle the key press event on a menu button.
     * @param view View that received the enter key press event.
     * @return Whether the app menu was shown as a result of this action.
     */
    public boolean onEnterKeyPress(View view) {
        return showAppMenu(view, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        boolean isTouchEventConsumed = false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsTouchEventsBeingProcessed = true;
                isTouchEventConsumed |= true;
                view.setPressed(true);
                showAppMenu(view, true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsTouchEventsBeingProcessed = false;
                isTouchEventConsumed |= true;
                view.setPressed(false);
                break;
            default:
        }

        // If user starts to drag on this menu button, ACTION_DOWN and all the subsequent touch
        // events are received here. We need to forward this event to the app menu to handle
        // dragging correctly.
        AppMenuDragHelper dragHelper = mMenuHandler.getAppMenuDragHelper();
        if (dragHelper != null) {
            isTouchEventConsumed |= dragHelper.handleDragging(event, view);
        }
        return isTouchEventConsumed;
    }
}
