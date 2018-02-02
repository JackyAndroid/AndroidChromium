// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;

import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.Window;
import android.view.WindowManager;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * Handles updating the UI based on requests to the HTML Fullscreen API.
 */
public class FullscreenHtmlApiHandler {
    private static final int MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS = 1;
    private static final int MSG_ID_CLEAR_LAYOUT_FULLSCREEN_FLAG = 2;

    // The time we allow the Android notification bar to be shown when it is requested temporarily
    // by the Android system (this value is additive on top of the show duration imposed by
    // Android).
    private static final long ANDROID_CONTROLS_SHOW_DURATION_MS = 200;
    // Delay to allow a frame to render between getting the fullscreen layout update and clearing
    // the SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN flag.
    private static final long CLEAR_LAYOUT_FULLSCREEN_DELAY_MS = 20;

    private final Window mWindow;
    private final Handler mHandler;
    private final FullscreenHtmlApiDelegate mDelegate;

    // We still need this since we are setting fullscreen UI state on the contentviewcore's
    // container view, and a tab can have null content view core, i.e., if you navigate
    // to a native page.
    private ContentViewCore mContentViewCoreInFullscreen;
    private Tab mTabInFullscreen;
    private boolean mIsPersistentMode;

    // Toast at the top of the screen that is shown when user enters fullscreen for the
    // first time.
    private Toast mNotificationToast;

    private OnLayoutChangeListener mFullscreenOnLayoutChangeListener;

    /**
     * Delegate that allows embedders to react to fullscreen API requests.
     */
    public interface FullscreenHtmlApiDelegate {
        /**
         * Notifies the delegate that entering fullscreen has been requested and allows them
         * to hide their controls.
         * <p>
         * Once the delegate has hidden the their controls, it must call
         * {@link FullscreenHtmlApiHandler#enterFullscreen(Tab)}.
         */
        void onEnterFullscreen();

        /**
         * Cancels a pending enter fullscreen request if present.
         * @return Whether the request was cancelled.
         */
        boolean cancelPendingEnterFullscreen();

        /**
         * Notifies the delegate that the window UI has fully exited fullscreen and gives
         * the embedder a chance to update their controls.
         *
         * @param tab The tab whose fullscreen is being exited.
         */
        void onFullscreenExited(Tab tab);

        /**
         * @return Whether the notification toast should be shown. For fullscreen video in
         *         overlay mode, the notification toast should be disabled.
         */
        boolean shouldShowNotificationToast();
    }

    // This static inner class holds a WeakReference to the outer object, to avoid triggering the
    // lint HandlerLeak warning.
    private static class FullscreenHandler extends Handler {
        private final WeakReference<FullscreenHtmlApiHandler> mFullscreenHtmlApiHandler;

        public FullscreenHandler(FullscreenHtmlApiHandler fullscreenHtmlApiHandler) {
            mFullscreenHtmlApiHandler = new WeakReference<FullscreenHtmlApiHandler>(
                    fullscreenHtmlApiHandler);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg == null) return;
            FullscreenHtmlApiHandler fullscreenHtmlApiHandler = mFullscreenHtmlApiHandler.get();
            if (fullscreenHtmlApiHandler == null) return;
            switch (msg.what) {
                case MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS: {
                    assert fullscreenHtmlApiHandler.getPersistentFullscreenMode() :
                        "Calling after we exited fullscreen";
                    final ContentViewCore contentViewCore =
                            fullscreenHtmlApiHandler.mContentViewCoreInFullscreen;
                    if (contentViewCore == null) return;
                    final View contentView = contentViewCore.getContainerView();
                    int systemUiVisibility = contentView.getSystemUiVisibility();
                    if ((systemUiVisibility & SYSTEM_UI_FLAG_FULLSCREEN)
                            == SYSTEM_UI_FLAG_FULLSCREEN) {
                        return;
                    }
                    systemUiVisibility |= SYSTEM_UI_FLAG_FULLSCREEN;
                    systemUiVisibility |= SYSTEM_UI_FLAG_LOW_PROFILE;
                    systemUiVisibility |= getExtraFullscreenUIFlags();
                    contentView.setSystemUiVisibility(systemUiVisibility);

                    // Trigger a update to clear the SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN flag
                    // once the view has been laid out after this system UI update.  Without
                    // clearing this flag, the keyboard appearing will not trigger a relayout
                    // of the contents, which prevents updating the overdraw amount to the
                    // renderer.
                    contentView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(View v, int left, int top, int right,
                                int bottom, int oldLeft, int oldTop, int oldRight,
                                int oldBottom) {
                            sendEmptyMessageDelayed(MSG_ID_CLEAR_LAYOUT_FULLSCREEN_FLAG,
                                    CLEAR_LAYOUT_FULLSCREEN_DELAY_MS);
                            contentView.removeOnLayoutChangeListener(this);
                        }
                    });

                    contentView.requestLayout();
                    break;
                }
                case MSG_ID_CLEAR_LAYOUT_FULLSCREEN_FLAG: {
                    // Change this assert to simply ignoring the message to work around
                    // http://crbug/365638
                    // TODO(aberent): Fix bug
                    // assert mIsPersistentMode : "Calling after we exited fullscreen";
                    if (!fullscreenHtmlApiHandler.getPersistentFullscreenMode()) return;
                    final ContentViewCore contentViewCore =
                            fullscreenHtmlApiHandler.mContentViewCoreInFullscreen;
                    if (contentViewCore == null) return;
                    final View view = contentViewCore.getContainerView();
                    int systemUiVisibility = view.getSystemUiVisibility();
                    if ((systemUiVisibility & SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) == 0) {
                        return;
                    }
                    systemUiVisibility &= ~SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    view.setSystemUiVisibility(systemUiVisibility);
                    break;
                }
                default:
                    assert false : "Unexpected message for ID: " + msg.what;
                    break;
            }
        }
    }

    /**
     * Constructs the handler that will manage the UI transitions from the HTML fullscreen API.
     *
     * @param window The window containing the view going to fullscreen.
     * @param delegate The delegate that allows embedders to handle fullscreen transitions.
     */
    public FullscreenHtmlApiHandler(Window window, FullscreenHtmlApiDelegate delegate) {
        mWindow = window;
        mDelegate = delegate;
        mHandler = new FullscreenHandler(this);
    }

    /**
     * Enters or exits persistent fullscreen mode.  In this mode, the browser controls will be
     * permanently hidden until this mode is exited.
     *
     * @param enabled Whether to enable persistent fullscreen mode.
     */
    public void setPersistentFullscreenMode(boolean enabled) {
        if (mIsPersistentMode == enabled) return;

        mIsPersistentMode = enabled;

        if (mIsPersistentMode) {
            mDelegate.onEnterFullscreen();
        } else {
            if (mContentViewCoreInFullscreen != null && mTabInFullscreen != null) {
                exitFullscreen(mContentViewCoreInFullscreen, mTabInFullscreen);
            } else {
                if (!mDelegate.cancelPendingEnterFullscreen()) {
                    assert false : "No content view previously set to fullscreen.";
                }
            }
            mContentViewCoreInFullscreen = null;
            mTabInFullscreen = null;
        }
    }

    /**
     * @return Whether the application is in persistent fullscreen mode.
     * @see #setPersistentFullscreenMode(boolean)
     */
    public boolean getPersistentFullscreenMode() {
        return mIsPersistentMode;
    }

    private void exitFullscreen(final ContentViewCore contentViewCore, final Tab tab) {
        final View contentView = contentViewCore.getContainerView();
        hideNotificationToast();
        mHandler.removeMessages(MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS);
        mHandler.removeMessages(MSG_ID_CLEAR_LAYOUT_FULLSCREEN_FLAG);

        int systemUiVisibility = contentView.getSystemUiVisibility();
        systemUiVisibility &= ~SYSTEM_UI_FLAG_LOW_PROFILE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            systemUiVisibility &= ~SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            systemUiVisibility &= ~SYSTEM_UI_FLAG_FULLSCREEN;
            systemUiVisibility &= ~getExtraFullscreenUIFlags();
        } else {
            mWindow.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        contentView.setSystemUiVisibility(systemUiVisibility);
        if (mFullscreenOnLayoutChangeListener != null) {
            contentView.removeOnLayoutChangeListener(mFullscreenOnLayoutChangeListener);
        }
        mFullscreenOnLayoutChangeListener = new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((bottom - top) < (oldBottom - oldTop)) {
                    mDelegate.onFullscreenExited(tab);
                    contentView.removeOnLayoutChangeListener(this);
                }
            }
        };
        contentView.addOnLayoutChangeListener(mFullscreenOnLayoutChangeListener);

        // getWebContents() will return null if contentViewCore has been destroyed
        if (contentViewCore.getWebContents() != null) {
            contentViewCore.getWebContents().exitFullscreen();
        }
    }

    /**
     * Handles hiding the system UI components to allow the content to take up the full screen.
     * @param tab The tab that is entering fullscreen.
     */
    public void enterFullscreen(final Tab tab) {
        ContentViewCore contentViewCore = tab.getContentViewCore();
        if (contentViewCore == null) return;
        final View contentView = contentViewCore.getContainerView();
        int systemUiVisibility = contentView.getSystemUiVisibility();
        systemUiVisibility |= SYSTEM_UI_FLAG_LOW_PROFILE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if ((systemUiVisibility & SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                    == SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) {
                systemUiVisibility |= SYSTEM_UI_FLAG_FULLSCREEN;
                systemUiVisibility |= getExtraFullscreenUIFlags();
            } else {
                systemUiVisibility |= SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            }
        } else {
            mWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        }
        if (mFullscreenOnLayoutChangeListener != null) {
            contentView.removeOnLayoutChangeListener(mFullscreenOnLayoutChangeListener);
        }
        mFullscreenOnLayoutChangeListener = new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // On certain sites playing embedded video (http://crbug.com/293782), setting the
                // SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN does not always trigger a view-level layout
                // with an updated height.  To work around this, do not check for an increased
                // height and always just trigger the next step of the fullscreen initialization.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    // Posting the message to set the fullscreen flag because setting it
                    // directly in the onLayoutChange would have no effect.
                    mHandler.sendEmptyMessage(MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS);
                }

                if ((bottom - top) <= (oldBottom - oldTop)) return;
                if (mDelegate.shouldShowNotificationToast()) {
                    showNotificationToast();
                }
                contentView.removeOnLayoutChangeListener(this);
            }
        };
        contentView.addOnLayoutChangeListener(mFullscreenOnLayoutChangeListener);
        contentView.setSystemUiVisibility(systemUiVisibility);

        // Request a layout so the updated system visibility takes affect.
        contentView.requestLayout();

        mContentViewCoreInFullscreen = contentViewCore;
        mTabInFullscreen = tab;
    }

    /**
     * Create and show the fullscreen notification toast.
     */
    private void showNotificationToast() {
        if (mNotificationToast == null) {
            int resId = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    ? R.string.immersive_fullscreen_api_notification
                    : R.string.fullscreen_api_notification;
            mNotificationToast = Toast.makeText(
                    mWindow.getContext(), resId, Toast.LENGTH_LONG);
            mNotificationToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
        }
        mNotificationToast.show();
    }

    /**
     * Hides the notification toast.
     */
    public void hideNotificationToast() {
        if (mNotificationToast != null) {
            mNotificationToast.cancel();
        }
    }

    /**
     * Notified when the system UI visibility for the current ContentView has changed.
     * @param visibility The updated UI visibility.
     * @see View#getSystemUiVisibility()
     */
    public void onContentViewSystemUiVisibilityChange(int visibility) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return;

        if (mTabInFullscreen == null || !mIsPersistentMode) return;
        mHandler.sendEmptyMessageDelayed(
                MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS, ANDROID_CONTROLS_SHOW_DURATION_MS);
    }

    /**
     * Ensure the proper system UI flags are set after the window regains focus.
     * @see android.app.Activity#onWindowFocusChanged(boolean)
     */
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) hideNotificationToast();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return;

        mHandler.removeMessages(MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS);
        mHandler.removeMessages(MSG_ID_CLEAR_LAYOUT_FULLSCREEN_FLAG);
        if (mTabInFullscreen == null || !mIsPersistentMode || !hasWindowFocus) return;
        mHandler.sendEmptyMessageDelayed(
                MSG_ID_SET_FULLSCREEN_SYSTEM_UI_FLAGS, ANDROID_CONTROLS_SHOW_DURATION_MS);
    }

    /*
     * Helper method to return extra fullscreen UI flags for Kitkat devices.
     * @return fullscreen flags to be applied to system UI visibility.
     */
    private static int getExtraFullscreenUIFlags() {
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }
}
