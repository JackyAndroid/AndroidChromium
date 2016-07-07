// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.view.KeyEvent;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.content.browser.ContentViewCore;

/**
 * Implements app-level keyboard shortcuts for ChromeTabbedActivity and DocumentActivity.
 */
public class KeyboardShortcuts {

    private static final int CTRL = 1 << 31;
    private static final int ALT = 1 << 30;
    private static final int SHIFT = 1 << 29;

    private KeyboardShortcuts() {}

    private static int getMetaState(KeyEvent event) {
        return (event.isCtrlPressed() ? CTRL : 0)
                | (event.isAltPressed() ? ALT : 0)
                | (event.isShiftPressed() ? SHIFT : 0);
    }

    private static boolean isGamepadAPIActive(ChromeActivity activity) {
        ContentViewCore cvc = activity.getCurrentContentViewCore();
        return (cvc != null) ? cvc.isGamepadAPIActive() : false;
    }

    /**
     * This should be called from the Activity's dispatchKeyEvent() to handle keyboard shortcuts.
     *
     * Note: dispatchKeyEvent() is called before the active view or web page gets a chance to handle
     * the key event. So the keys handled here cannot be overridden by any view or web page.
     *
     * @param event The KeyEvent to handle.
     * @param activity The ChromeActivity in which the key was pressed.
     * @param uiInitialized Whether the UI has been initialized. If this is false, most keys will
     *                      not be handled.
     * @return True if the event was handled. False if the event was ignored. Null if the event
     *         should be handled by the activity's parent class.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public static Boolean dispatchKeyEvent(KeyEvent event, ChromeActivity activity,
            boolean uiInitialized) {
        int keyCode = event.getKeyCode();
        if (!uiInitialized) {
            if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_MENU) return true;
            return null;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    activity.onMenuOrKeyboardAction(R.id.focus_url_bar, false);
                }
                // Always consume the SEARCH key events to prevent android from showing
                // the default app search UI, which locks up Chrome.
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    activity.onMenuOrKeyboardAction(R.id.show_menu, false);
                }
                return true;
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_DVR:
            case KeyEvent.KEYCODE_AVR_INPUT:
            case KeyEvent.KEYCODE_AVR_POWER:
            case KeyEvent.KEYCODE_STB_INPUT:
            case KeyEvent.KEYCODE_STB_POWER:
            case KeyEvent.KEYCODE_TV_INPUT:
            case KeyEvent.KEYCODE_TV_POWER:
            case KeyEvent.KEYCODE_WINDOW:
                // Do not consume the AV device-related keys so that the system will take
                // an appropriate action, such as switching to TV mode.
                return false;
        }

        return null;
    }

    /**
     * This should be called from the Activity's onKeyDown() to handle keyboard shortcuts.
     *
     * Note: onKeyDown() is called after the active view or web page has had a chance to handle
     * the key event. So the keys handled here *can* be overridden by any view or web page.
     *
     * @param event The KeyEvent to handle.
     * @param activity The ChromeActivity in which the key was pressed.
     * @param isCurrentTabVisible Whether page-related actions are valid, e.g. reload, zoom in.
     *                            This should be false when in the tab switcher.
     * @param tabSwitchingEnabled Whether shortcuts that switch between tabs are enabled (e.g.
     *                            Ctrl+Tab, Ctrl+3).
     * @return Whether the key event was handled.
     */
    public static boolean onKeyDown(KeyEvent event, ChromeActivity activity,
            boolean isCurrentTabVisible, boolean tabSwitchingEnabled) {
        int keyCode = event.getKeyCode();
        if (event.getRepeatCount() != 0 || KeyEvent.isModifierKey(keyCode)) return false;
        if (KeyEvent.isGamepadButton(keyCode)) {
            if (isGamepadAPIActive(activity)) return false;
        } else if (!event.isCtrlPressed() && !event.isAltPressed()
                && keyCode != KeyEvent.KEYCODE_F3
                && keyCode != KeyEvent.KEYCODE_F5
                && keyCode != KeyEvent.KEYCODE_FORWARD) {
            return false;
        }

        TabModel curModel = activity.getCurrentTabModel();
        int count = curModel.getCount();

        int metaState = getMetaState(event);
        int keyCodeAndMeta = keyCode | metaState;

        switch (keyCodeAndMeta) {
            case CTRL | KeyEvent.KEYCODE_T:
                activity.onMenuOrKeyboardAction(curModel.isIncognito()
                        ? R.id.new_incognito_tab_menu_id
                        : R.id.new_tab_menu_id, false);
                return true;
            case CTRL | KeyEvent.KEYCODE_N:
                activity.onMenuOrKeyboardAction(R.id.new_tab_menu_id, false);
                return true;
            case CTRL | SHIFT | KeyEvent.KEYCODE_N:
                activity.onMenuOrKeyboardAction(R.id.new_incognito_tab_menu_id, false);
                return true;
            case ALT | KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_BUTTON_Y:
                activity.onMenuOrKeyboardAction(R.id.show_menu, false);
                return true;
        }

        if (isCurrentTabVisible) {
            if (tabSwitchingEnabled && (metaState == CTRL || metaState == ALT)) {
                int numCode = keyCode - KeyEvent.KEYCODE_0;
                if (numCode > 0 && numCode <= Math.min(count, 8)) {
                    // Ctrl+1 to Ctrl+8: select tab by index
                    TabModelUtils.setIndex(curModel, numCode - 1);
                    return true;
                } else if (numCode == 9 && count != 0) {
                    // Ctrl+9: select last tab
                    TabModelUtils.setIndex(curModel, count - 1);
                    return true;
                }
            }

            switch (keyCodeAndMeta) {
                case CTRL | KeyEvent.KEYCODE_TAB:
                case CTRL | KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (tabSwitchingEnabled && count > 1) {
                        TabModelUtils.setIndex(curModel, (curModel.index() + 1) % count);
                    }
                    return true;
                case CTRL | SHIFT | KeyEvent.KEYCODE_TAB:
                case CTRL | KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (tabSwitchingEnabled && count > 1) {
                        TabModelUtils.setIndex(curModel, (curModel.index() + count - 1) % count);
                    }
                    return true;
                case CTRL | KeyEvent.KEYCODE_W:
                case CTRL | KeyEvent.KEYCODE_F4:
                case KeyEvent.KEYCODE_BUTTON_B:
                    TabModelUtils.closeCurrentTab(curModel);
                    return true;
                case CTRL | KeyEvent.KEYCODE_F:
                case CTRL | KeyEvent.KEYCODE_G:
                case CTRL | SHIFT | KeyEvent.KEYCODE_G:
                case KeyEvent.KEYCODE_F3:
                case SHIFT | KeyEvent.KEYCODE_F3:
                    activity.onMenuOrKeyboardAction(R.id.find_in_page_id, false);
                    return true;
                case CTRL | KeyEvent.KEYCODE_L:
                case ALT | KeyEvent.KEYCODE_D:
                case KeyEvent.KEYCODE_BUTTON_X:
                    activity.onMenuOrKeyboardAction(R.id.focus_url_bar, false);
                    return true;
                case CTRL | SHIFT | KeyEvent.KEYCODE_B:
                    activity.onMenuOrKeyboardAction(R.id.all_bookmarks_menu_id, false);
                    return true;
                case KeyEvent.KEYCODE_BOOKMARK:
                case CTRL | KeyEvent.KEYCODE_D:
                    activity.onMenuOrKeyboardAction(R.id.bookmark_this_page_id, false);
                    return true;
                case CTRL | KeyEvent.KEYCODE_H:
                    activity.onMenuOrKeyboardAction(R.id.open_history_menu_id, false);
                    return true;
                case CTRL | KeyEvent.KEYCODE_P:
                    activity.onMenuOrKeyboardAction(R.id.print_id, false);
                    return true;
                case CTRL | KeyEvent.KEYCODE_PLUS:
                case CTRL | KeyEvent.KEYCODE_EQUALS:
                case CTRL | SHIFT | KeyEvent.KEYCODE_PLUS:
                case CTRL | SHIFT | KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_ZOOM_IN:
                    ContentViewCore cvc = activity.getCurrentContentViewCore();
                    if (cvc != null) cvc.zoomIn();
                    return true;
                case CTRL | KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_ZOOM_OUT:
                    cvc = activity.getCurrentContentViewCore();
                    if (cvc != null) cvc.zoomOut();
                    return true;
                case CTRL | KeyEvent.KEYCODE_0:
                    cvc = activity.getCurrentContentViewCore();
                    if (cvc != null) cvc.zoomReset();
                    return true;
                case SHIFT | CTRL | KeyEvent.KEYCODE_R:
                case CTRL | KeyEvent.KEYCODE_R:
                case SHIFT | KeyEvent.KEYCODE_F5:
                case KeyEvent.KEYCODE_F5:
                    Tab tab = activity.getActivityTab();
                    if (tab != null) {
                        if ((keyCodeAndMeta & SHIFT) == SHIFT) {
                            tab.reloadIgnoringCache();
                        } else {
                            tab.reload();
                        }

                        if (activity.getToolbarManager() != null
                                && tab.getWebContents() != null
                                && tab.getWebContents().focusLocationBarByDefault()) {
                            activity.getToolbarManager().revertLocationBarChanges();
                        } else {
                            tab.requestFocus();
                        }
                    }
                    return true;
                case ALT | KeyEvent.KEYCODE_DPAD_LEFT:
                    tab = activity.getActivityTab();
                    if (tab != null && tab.canGoBack()) tab.goBack();
                    return true;
                case ALT | KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_FORWARD:
                case KeyEvent.KEYCODE_BUTTON_START:
                    tab = activity.getActivityTab();
                    if (tab != null && tab.canGoForward()) tab.goForward();
                    return true;
                case CTRL | SHIFT | KeyEvent.KEYCODE_SLASH:  // i.e. Ctrl+?
                    activity.onMenuOrKeyboardAction(R.id.help_id, false);
                    return true;
            }
        }

        return false;
    }
}
