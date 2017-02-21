// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.StrictMode;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.tab.Tab;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Manages interactions with the VR Shell.
 */
@JNINamespace("vr_shell")
public class VrShellDelegate {
    private static final String TAG = "VrShellDelegate";

    private ChromeTabbedActivity mActivity;

    private boolean mVrShellEnabled;

    private Class<? extends VrShellInterface> mVrShellClass;
    private VrShellInterface mVrShell;
    private boolean mInVr;
    private int mRestoreSystemUiVisibilityFlag = -1;
    private String mVrExtra;
    private long mNativeVrShellDelegate;

    public VrShellDelegate(ChromeTabbedActivity activity) {
        mActivity = activity;

        mVrShellClass = maybeFindVrShell();
        if (mVrShellClass != null) {
            mVrShellEnabled = true;
            try {
                mVrExtra = (String) mVrShellClass.getField("VR_EXTRA").get(null);
            } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException e) {
                Log.e(TAG, "Unable to read VR_EXTRA field", e);
                mVrShellEnabled = false;
            }
        }
    }

    /**
     * Should be called once the native library is loaded so that the native portion of this
     * class can be initialized.
     */
    public void onNativeLibraryReady() {
        if (mVrShellEnabled) {
            mNativeVrShellDelegate = nativeInit();
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends VrShellInterface> maybeFindVrShell() {
        try {
            return (Class<? extends VrShellInterface>) Class
                    .forName("org.chromium.chrome.browser.vr_shell.VrShell");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Enters VR Shell, displaying browser UI and tab contents in VR.
     *
     * This function performs native initialization, and so must only be called after native
     * libraries are ready.
     * @param inWebVR If true should begin displaying WebVR content rather than the VrShell UI.
     * @return Whether or not we are in VR when this function returns.
     */
    @CalledByNative
    public boolean enterVRIfNecessary(boolean inWebVR) {
        if (!mVrShellEnabled || mNativeVrShellDelegate == 0) return false;
        Tab tab = mActivity.getActivityTab();
        // TODO(mthiesse): When we have VR UI for opening new tabs, etc., allow VR Shell to be
        // entered without any current tabs.
        if (tab == null || tab.getContentViewCore() == null) {
            return false;
        }
        if (mInVr) return true;
        // VrShell must be initialized in Landscape mode due to a bug in the GVR library.
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        if (!createVrShell()) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            return false;
        }
        addVrViews();
        setupVrModeWindowFlags();
        mVrShell.initializeNative(tab, this);
        if (inWebVR) mVrShell.setWebVrModeEnabled(true);
        mVrShell.setVrModeEnabled(true);
        mInVr = true;
        tab.updateFullscreenEnabledState();
        return true;
    }

    @CalledByNative
    private boolean exitWebVR() {
        if (!mInVr) return false;
        mVrShell.setWebVrModeEnabled(false);
        // TODO(bajones): Once VR Shell can be invoked outside of WebVR this
        // should no longer exit the shell outright. Need a way to determine
        // how VrShell was created.
        shutdownVR();
        return true;
    }

    /**
     * Resumes VR Shell.
     */
    public void resumeVR() {
        setupVrModeWindowFlags();
        mVrShell.resume();
    }

    /**
     * Pauses VR Shell.
     */
    public void pauseVR() {
        mVrShell.pause();
    }

    /**
     * Exits the current VR mode (WebVR or VRShell)
     * @return Whether or not we exited VR.
     */
    public boolean exitVRIfNecessary() {
        if (!mInVr) return false;
        // If WebVR is presenting instruct it to exit. VR mode should not
        // exit in this scenario, in case we want to return to the VrShell.
        if (!nativeExitWebVRIfNecessary(mNativeVrShellDelegate)) {
            // If WebVR was not presenting, shutdown VR mode entirely.
            shutdownVR();
        }

        return true;
    }

    /**
     * Exits VR Shell, performing all necessary cleanup.
     */
    private void shutdownVR() {
        if (!mInVr) return;
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        mVrShell.setVrModeEnabled(false);
        mVrShell.pause();
        removeVrViews();
        clearVrModeWindowFlags();
        destroyVrShell();
        mInVr = false;
        Tab tab = mActivity.getActivityTab();
        if (tab != null) tab.updateFullscreenEnabledState();
    }

    private boolean createVrShell() {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            Constructor<?> vrShellConstructor = mVrShellClass.getConstructor(Activity.class);
            mVrShell = (VrShellInterface) vrShellConstructor.newInstance(mActivity);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            Log.e(TAG, "Unable to instantiate VrShell", e);
            return false;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return true;
    }

    private void addVrViews() {
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(mVrShell.getContainer(), params);
        mActivity.setUIVisibilityForVR(View.GONE);
    }

    private void removeVrViews() {
        mActivity.setUIVisibilityForVR(View.VISIBLE);
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mVrShell.getContainer());
    }

    private void setupVrModeWindowFlags() {
        if (mRestoreSystemUiVisibilityFlag == -1) {
            mRestoreSystemUiVisibilityFlag = mActivity.getWindow().getDecorView()
                    .getSystemUiVisibility();
        }
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mActivity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void clearVrModeWindowFlags() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mRestoreSystemUiVisibilityFlag != -1) {
            mActivity.getWindow().getDecorView()
                    .setSystemUiVisibility(mRestoreSystemUiVisibilityFlag);
        }
        mRestoreSystemUiVisibilityFlag = -1;
    }

    /**
     * Clean up VrShell, and associated native objects.
     */
    public void destroyVrShell() {
        if (mVrShell != null) {
            mVrShell.teardown();
            mVrShell = null;
        }
    }

    /**
     * Whether or not the intent is a Daydream VR Intent.
     */
    public boolean isVrIntent(Intent intent) {
        if (intent == null) return false;
        return intent.getBooleanExtra(mVrExtra, false);
    }

    /**
     * Whether or not we are currently in VR.
     */
    public boolean isInVR() {
        return mInVr;
    }

    /**
     * @return Whether or not VR Shell is currently enabled.
     */
    public boolean isVrShellEnabled() {
        return mVrShellEnabled;
    }

    /**
     * @return Pointer to the native VrShellDelegate object.
     */
    @CalledByNative
    private long getNativePointer() {
        return mNativeVrShellDelegate;
    }

    private native long nativeInit();
    private native boolean nativeExitWebVRIfNecessary(long nativeVrShellDelegate);
}
