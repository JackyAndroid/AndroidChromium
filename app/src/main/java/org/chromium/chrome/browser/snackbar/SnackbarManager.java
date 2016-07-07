// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.graphics.Rect;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.HashSet;
import java.util.Stack;

/**
 * Manager for the snackbar showing at the bottom of activity.
 * <p/>
 * There should be only one SnackbarManager and one snackbar in the activity. The manager maintains
 * a stack to store all entries that should be displayed. When showing a new snackbar, old entry
 * will be pushed to stack and text/button will be updated to the newest entry.
 * <p/>
 * When action button is clicked, this manager will call
 * {@link SnackbarController#onAction(Object)} in corresponding listener, and show the next
 * entry in stack. Otherwise if no action is taken by user during
 * {@link #DEFAULT_SNACKBAR_DURATION_MS} milliseconds, it will clear the stack and call
 * {@link SnackbarController#onDismissNoAction(Object)} to all listeners.
 */
public class SnackbarManager implements OnClickListener, OnGlobalLayoutListener {

    /**
     * Interface that shows the ability to provide a unified snackbar manager.
     */
    public interface SnackbarManageable {
        /**
         * @return The snackbar manager that has a proper anchor view.
         */
        SnackbarManager getSnackbarManager();
    }

    /**
     * Controller that post entries to snackbar manager and interact with snackbar manager during
     * dismissal and action click event.
     */
    public static interface SnackbarController {
        /**
         * Callback triggered when user clicks on button at end of snackbar. This method is only
         * called for controller having posted the entry the user clicked on; other controllers are
         * not notified. Also once this {@link #onAction(Object)} is called,
         * {@link #onDismissNoAction(Object)} and {@link #onDismissForEachType(boolean)} will not be
         * called.
         * @param actionData Data object passed when showing this specific snackbar.
         */
        void onAction(Object actionData);

        /**
         * Callback triggered when the snackbar is dismissed by either timeout or UI environment
         * change. This callback will be called for each entry a controller has posted, _except_ for
         * entries which the user has done action with, by clicking the action button.
         * @param actionData Data object associated with the dismissed snackbar entry.
         */
        void onDismissNoAction(Object actionData);

        /**
         * Notify each SnackbarControllers instance only once immediately before the snackbar is
         * dismissed. This function is likely to be used for controllers to do user metrics for
         * dismissal.
         * @param isTimeout Whether this dismissal is triggered by timeout.
         */
        void onDismissForEachType(boolean isTimeout);
    }

    private static final int DEFAULT_SNACKBAR_DURATION_MS = 3000;
    private static final int ACCESSIBILITY_MODE_SNACKBAR_DURATION_MS = 6000;

    // Used instead of the constant so tests can override the value.
    private static int sSnackbarDurationMs = DEFAULT_SNACKBAR_DURATION_MS;
    private static int sAccessibilitySnackbarDurationMs = ACCESSIBILITY_MODE_SNACKBAR_DURATION_MS;

    private final boolean mIsTablet;

    private View mDecor;
    private final Handler mUIThreadHandler;
    private Stack<Snackbar> mStack = new Stack<Snackbar>();
    private SnackbarPopupWindow mPopup;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            dismissAllSnackbars(true);
        }
    };

    // Variables used and reused in local calculations.
    private int[] mTempDecorPosition = new int[2];
    private Rect mTempVisibleDisplayFrame = new Rect();

    /**
     * Constructs a SnackbarManager to show snackbars in the given window.
     */
    public SnackbarManager(Window window) {
        mDecor = window.getDecorView();
        mUIThreadHandler = new Handler();
        mIsTablet = DeviceFormFactor.isTablet(mDecor.getContext());
    }

    /**
     * Shows a snackbar at the bottom of the screen, or above the keyboard if the keyboard is
     * visible.
     */
    public void showSnackbar(Snackbar snackbar) {
        int durationMs = snackbar.getDuration();
        if (durationMs == 0) {
            durationMs = DeviceClassManager.isAccessibilityModeEnabled(mDecor.getContext())
                    ? sAccessibilitySnackbarDurationMs : sSnackbarDurationMs;
        }

        mUIThreadHandler.removeCallbacks(mHideRunnable);
        mUIThreadHandler.postDelayed(mHideRunnable, durationMs);

        mStack.push(snackbar);
        if (mPopup == null) {
            mPopup = new SnackbarPopupWindow(mDecor, this, snackbar);
            showPopupAtBottom();
            mDecor.getViewTreeObserver().addOnGlobalLayoutListener(this);
        } else {
            mPopup.update(snackbar, true);
        }

        mPopup.announceforAccessibility();
    }

    /**
     * Warning: Calling this method might cause cascading destroy loop, because you might trigger
     * callbacks for other {@link SnackbarController}. This method is only meant to be used during
     * {@link ChromeActivity}'s destruction routine. For other purposes, use
     * {@link #dismissSnackbars(SnackbarController)} instead.
     * <p>
     * Dismisses all snackbars in stack. This will call
     * {@link SnackbarController#onDismissNoAction(Object)} for every closing snackbar.
     *
     * @param isTimeout Whether dismissal was triggered by timeout.
     */
    public void dismissAllSnackbars(boolean isTimeout) {
        mUIThreadHandler.removeCallbacks(mHideRunnable);

        if (mPopup != null) {
            mPopup.dismiss();
            mPopup = null;
        }

        HashSet<SnackbarController> controllers = new HashSet<SnackbarController>();

        while (!mStack.isEmpty()) {
            Snackbar snackbar = mStack.pop();
            if (!controllers.contains(snackbar.getController())) {
                snackbar.getController().onDismissForEachType(isTimeout);
                controllers.add(snackbar.getController());
            }
            snackbar.getController().onDismissNoAction(snackbar.getActionData());
        }
        mDecor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    /**
     * Dismisses snackbars that are associated with the given {@link SnackbarController}.
     *
     * @param controller Only snackbars with this controller will be removed.
     */
    public void dismissSnackbars(SnackbarController controller) {
        boolean isFound = false;
        Snackbar[] snackbars = new Snackbar[mStack.size()];
        mStack.toArray(snackbars);
        for (Snackbar snackbar : snackbars) {
            if (snackbar.getController() == controller) {
                mStack.remove(snackbar);
                isFound = true;
            }
        }
        if (!isFound) return;

        finishSnackbarRemoval(controller);
    }

    /**
     * Dismisses snackbars that have a certain controller and action data.
     *
     * @param controller Only snackbars with this controller will be removed.
     * @param actionData Only snackbars whose action data is equal to actionData will be removed.
     */
    public void dismissSnackbars(SnackbarController controller, Object actionData) {
        boolean isFound = false;
        for (Snackbar snackbar : mStack) {
            if (snackbar.getActionData() != null && snackbar.getActionData().equals(actionData)
                    && snackbar.getController() == controller) {
                mStack.remove(snackbar);
                isFound = true;
                break;
            }
        }
        if (!isFound) return;

        finishSnackbarRemoval(controller);
    }

    private void finishSnackbarRemoval(SnackbarController controller) {
        controller.onDismissForEachType(false);

        if (mStack.isEmpty()) {
            dismissAllSnackbars(false);
        } else {
            // Refresh the snackbar to let it show top of stack and have full timeout.
            showSnackbar(mStack.pop());
        }
    }

    /**
     * Handles click event for action button at end of snackbar.
     */
    @Override
    public void onClick(View v) {
        assert !mStack.isEmpty();

        Snackbar snackbar = mStack.pop();
        snackbar.getController().onAction(snackbar.getActionData());

        if (!mStack.isEmpty()) {
            showSnackbar(mStack.pop());
        } else {
            dismissAllSnackbars(false);
        }
    }

    private void showPopupAtBottom() {
        // When the keyboard is showing, translating the snackbar upwards looks bad because it
        // overlaps the keyboard. In this case, use an alternative animation without translation.
        boolean isKeyboardShowing = UiUtils.isKeyboardShowing(mDecor.getContext(), mDecor);
        mPopup.setAnimationStyle(isKeyboardShowing ? R.style.SnackbarAnimationWithKeyboard
                : R.style.SnackbarAnimation);

        mDecor.getLocationInWindow(mTempDecorPosition);
        mDecor.getWindowVisibleDisplayFrame(mTempVisibleDisplayFrame);
        int decorBottom = mTempDecorPosition[1] + mDecor.getHeight();
        int visibleBottom = Math.min(mTempVisibleDisplayFrame.bottom, decorBottom);
        int margin = mIsTablet ? mDecor.getResources().getDimensionPixelSize(
                R.dimen.snackbar_tablet_margin) : 0;

        mPopup.showAtLocation(mDecor, Gravity.START | Gravity.BOTTOM, margin,
                decorBottom - visibleBottom + margin);
    }

    /**
     * Resize and re-position popup window when the device orientation changes or the software
     * keyboard appears. Be careful not to let the snackbar overlap the Android navigation bar:
     * http://b/17789629.
     */
    @Override
    public void onGlobalLayout() {
        if (mPopup == null) return;

        mDecor.getLocationInWindow(mTempDecorPosition);
        mDecor.getWindowVisibleDisplayFrame(mTempVisibleDisplayFrame);
        int decorBottom = mTempDecorPosition[1] + mDecor.getHeight();
        int visibleBottom = Math.min(mTempVisibleDisplayFrame.bottom, decorBottom);

        if (mIsTablet) {
            int margin = mDecor.getResources().getDimensionPixelOffset(
                    R.dimen.snackbar_tablet_margin);
            int width = mDecor.getResources().getDimensionPixelSize(R.dimen.snackbar_tablet_width);
            boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(mDecor);
            int startPosition = isRtl ? mDecor.getRight() - width - margin
                    : mDecor.getLeft() + margin;
            mPopup.update(startPosition, decorBottom - visibleBottom + margin, width, -1);
        } else {
            mPopup.update(mDecor.getLeft(), decorBottom - visibleBottom, mDecor.getWidth(), -1);
        }
    }

    /**
     * @return Whether there is a snackbar on screen.
     */
    public boolean isShowing() {
        if (mPopup == null) return false;
        return mPopup.isShowing();
    }

    /**
     * Overrides the default snackbar duration with a custom value for testing.
     * @param durationMs The duration to use in ms.
     */
    @VisibleForTesting
    public static void setDurationForTesting(int durationMs) {
        sSnackbarDurationMs = durationMs;
        sAccessibilitySnackbarDurationMs = durationMs;
    }
}