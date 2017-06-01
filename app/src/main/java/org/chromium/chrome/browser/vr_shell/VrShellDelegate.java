// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.content_public.common.BrowserControlsState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Manages interactions with the VR Shell.
 */
@JNINamespace("vr_shell")
public class VrShellDelegate {
    private static final String TAG = "VrShellDelegate";
    // Pseudo-random number to avoid request id collisions.
    public static final int EXIT_VR_RESULT = 721251;

    public static final int ENTER_VR_NOT_NECESSARY = 0;
    public static final int ENTER_VR_CANCELLED = 1;
    public static final int ENTER_VR_REQUESTED = 2;
    public static final int ENTER_VR_SUCCEEDED = 3;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ENTER_VR_NOT_NECESSARY, ENTER_VR_CANCELLED, ENTER_VR_REQUESTED, ENTER_VR_SUCCEEDED })
    public @interface EnterVRResult {}

    // TODO(bshe): These should be replaced by string provided by NDK. Currently, it only available
    // in SDK and we don't want add dependency to SDK just to get these strings.
    private static final String DAYDREAM_VR_EXTRA = "android.intent.extra.VR_LAUNCH";
    private static final String DAYDREAM_CATEGORY = "com.google.intent.category.DAYDREAM";
    private static final String CARDBOARD_CATEGORY = "com.google.intent.category.CARDBOARD";

    private static final String VR_ACTIVITY_ALIAS =
            "org.chromium.chrome.browser.VRChromeTabbedActivity";

    private static final long REENTER_VR_TIMEOUT_MS = 1000;

    private final ChromeTabbedActivity mActivity;
    private final TabObserver mTabObserver;
    private final Intent mEnterVRIntent;

    private boolean mVrAvailable;
    private boolean mDaydreamReadyDevice;
    private Boolean mVrShellEnabled;

    private Class<? extends VrShell> mVrShellClass;
    private Class<? extends NonPresentingGvrContext> mNonPresentingGvrContextClass;
    private Class<? extends VrDaydreamApi> mVrDaydreamApiClass;
    private Class<? extends VrCoreVersionChecker> mVrCoreVersionCheckerClass;
    private VrShell mVrShell;
    private NonPresentingGvrContext mNonPresentingGvrContext;
    private VrDaydreamApi mVrDaydreamApi;
    private VrCoreVersionChecker mVrCoreVersionChecker;
    private boolean mInVr;
    private int mRestoreSystemUiVisibilityFlag = -1;
    private int mRestoreOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private long mNativeVrShellDelegate;
    private Tab mTab;
    private boolean mRequestedWebVR;
    private long mLastVRExit;
    private boolean mListeningForWebVrActivate;
    private boolean mListeningForWebVrActivateBeforePause;

    public VrShellDelegate(ChromeTabbedActivity activity) {
        mActivity = activity;
        mVrAvailable = maybeFindVrClasses() && isVrCoreCompatible() && createVrDaydreamApi()
                && mVrDaydreamApi.isDaydreamReadyDevice();
        if (mVrAvailable) {
            mEnterVRIntent = mVrDaydreamApi.createVrIntent(
                    new ComponentName(mActivity, VR_ACTIVITY_ALIAS));
        } else {
            mEnterVRIntent = null;
        }
        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onContentChanged(Tab tab) {
                if (tab.getNativePage() != null || tab.isShowingSadTab()) {
                    // For now we don't handle native pages. crbug.com/661609
                    shutdownVR(true, true);
                }
            }

            @Override
            public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
                // TODO(mthiesse): Update the native WebContents pointer and compositor. This
                // doesn't seem to get triggered in VR Shell currently, but that's likely to change
                // when we have omnibar navigation.
            }
        };
    }

    /**
     * Should be called once the native library is loaded so that the native portion of this
     * class can be initialized.
     */
    public void onNativeLibraryReady() {
        if (!mVrAvailable) return;
        mNativeVrShellDelegate = nativeInit();
    }

    @SuppressWarnings("unchecked")
    private boolean maybeFindVrClasses() {
        try {
            mVrShellClass = (Class<? extends VrShell>) Class.forName(
                    "org.chromium.chrome.browser.vr_shell.VrShellImpl");
            mNonPresentingGvrContextClass =
                    (Class<? extends NonPresentingGvrContext>) Class.forName(
                            "org.chromium.chrome.browser.vr_shell.NonPresentingGvrContextImpl");
            mVrDaydreamApiClass = (Class<? extends VrDaydreamApi>) Class.forName(
                    "org.chromium.chrome.browser.vr_shell.VrDaydreamApiImpl");
            mVrCoreVersionCheckerClass = (Class<? extends VrCoreVersionChecker>) Class.forName(
                    "org.chromium.chrome.browser.vr_shell.VrCoreVersionCheckerImpl");
            return true;
        } catch (ClassNotFoundException e) {
            mVrShellClass = null;
            mNonPresentingGvrContextClass = null;
            mVrDaydreamApiClass = null;
            mVrCoreVersionCheckerClass = null;
            return false;
        }
    }

    /**
     * Handle a VR intent, entering VR in the process unless we're unable to.
     */
    public void enterVRFromIntent(Intent intent) {
        if (!mVrAvailable) return;
        assert isVrIntent(intent);
        if (mListeningForWebVrActivateBeforePause && !mRequestedWebVR) {
            nativeDisplayActivate(mNativeVrShellDelegate);
            return;
        }
        // Normally, if the active page doesn't have a vrdisplayactivate listener, and WebVR was not
        // presenting and VrShell was not enabled, we shouldn't enter VR and Daydream Homescreen
        // should show after DON flow. But due to a failure in unregisterDaydreamIntent, we still
        // try to enterVR. Here we detect this case and force switch to Daydream Homescreen.
        if (!mListeningForWebVrActivateBeforePause && !mRequestedWebVR && !isVrShellEnabled()) {
            mVrDaydreamApi.launchVrHomescreen();
            return;
        }
        if (enterVR()) {
            if (mRequestedWebVR) {
                nativeSetPresentResult(mNativeVrShellDelegate, true);
                mVrShell.setWebVrModeEnabled(true);
            }
        } else {
            if (mRequestedWebVR) nativeSetPresentResult(mNativeVrShellDelegate, false);
            if (!mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                mVrDaydreamApi.setVrModeEnabled(false);
            }
        }

        mRequestedWebVR = false;
    }

    private boolean enterVR() {
        if (mInVr) return true;
        mVrDaydreamApi.setVrModeEnabled(true);

        Tab tab = mActivity.getActivityTab();
        if (!canEnterVR(tab)) return false;

        mRestoreOrientation = mActivity.getRequestedOrientation();
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        if (!createVrShell()) {
            mActivity.setRequestedOrientation(mRestoreOrientation);
            return false;
        }
        mInVr = true;
        mTab = tab;
        mTab.addObserver(mTabObserver);
        addVrViews();
        setupVrModeWindowFlags();
        mVrShell.initializeNative(mTab, this);
        mVrShell.setCloseButtonListener(new Runnable() {
            @Override
            public void run() {
                exitVRIfNecessary(true);
            }
        });
        // onResume needs to be called on GvrLayout after initialization to make sure DON flow work
        // properly.
        mVrShell.resume();
        mTab.updateFullscreenEnabledState();
        return true;
    }

    private boolean canEnterVR(Tab tab) {
        if (!LibraryLoader.isInitialized()) {
            return false;
        }
        // If vr isn't in the build, or we haven't initialized yet, or vr shell is not enabled and
        // this is not a web vr request, then return immediately.
        if (!mVrAvailable || mNativeVrShellDelegate == 0
                || (!isVrShellEnabled() && !(mRequestedWebVR || mListeningForWebVrActivate))) {
            return false;
        }
        // TODO(mthiesse): When we have VR UI for opening new tabs, etc., allow VR Shell to be
        // entered without any current tabs.
        if (tab == null || tab.getContentViewCore() == null) {
            return false;
        }
        // For now we don't handle native pages. crbug.com/661609
        if (tab.getNativePage() != null || tab.isShowingSadTab()) {
            return false;
        }
        // crbug.com/667781
        if (MultiWindowUtils.getInstance().isInMultiWindowMode(mActivity)) {
            return false;
        }
        return true;
    }

    @CalledByNative
    private void presentRequested() {
        // TODO(mthiesse): There's a GVR bug where they're not calling us back with the intent we
        // ask them to when we call DaydreamApi#launchInVr. As a temporary hack, remember locally
        // that we want to enter webVR.
        mRequestedWebVR = true;
        switch (enterVRIfNecessary()) {
            case ENTER_VR_NOT_NECESSARY:
                mVrShell.setWebVrModeEnabled(true);
                nativeSetPresentResult(mNativeVrShellDelegate, true);
                mRequestedWebVR = false;
                break;
            case ENTER_VR_CANCELLED:
                nativeSetPresentResult(mNativeVrShellDelegate, false);
                mRequestedWebVR = false;
                break;
            case ENTER_VR_REQUESTED:
                break;
            case ENTER_VR_SUCCEEDED:
                mVrShell.setWebVrModeEnabled(true);
                nativeSetPresentResult(mNativeVrShellDelegate, true);
                mRequestedWebVR = false;
                break;
            default:
                Log.e(TAG, "Unexpected enum.");
        }
    }

    /**
     * Enters VR Shell if necessary, displaying browser UI and tab contents in VR.
     */
    @EnterVRResult
    public int enterVRIfNecessary() {
        if (!mVrAvailable) return ENTER_VR_CANCELLED;
        if (mInVr) return ENTER_VR_NOT_NECESSARY;
        if (!canEnterVR(mActivity.getActivityTab())) return ENTER_VR_CANCELLED;

        if (!mVrDaydreamApi.isDaydreamCurrentViewer()) {
            // Avoid using launchInVr which would trigger DON flow regardless current viewer type
            // due to the lack of support for unexported activities.
            return enterVR() ? ENTER_VR_SUCCEEDED : ENTER_VR_CANCELLED;
        } else {
            if (!mVrDaydreamApi.launchInVr(getPendingEnterVRIntent())) return ENTER_VR_CANCELLED;
        }
        return ENTER_VR_REQUESTED;
    }

    @CalledByNative
    private boolean exitWebVR() {
        if (!mInVr) return false;
        mVrShell.setWebVrModeEnabled(false);
        // TODO(bajones): Once VR Shell can be invoked outside of WebVR this
        // should no longer exit the shell outright. Need a way to determine
        // how VrShell was created.
        shutdownVR(true, !isVrShellEnabled());
        return true;
    }

    /**
     * Resumes VR Shell.
     */
    public void maybeResumeVR() {
        if (!mVrAvailable) return;
        if (isVrShellEnabled() || mListeningForWebVrActivateBeforePause) {
            registerDaydreamIntent();
        }
        // If this is still set, it means the user backed out of the DON flow, and we won't be
        // receiving an intent from daydream.
        if (mRequestedWebVR) {
            nativeSetPresentResult(mNativeVrShellDelegate, false);
            mRequestedWebVR = false;
        }

        // TODO(bshe): Ideally, we do not need two gvr context exist at the same time. We can
        // probably shutdown non presenting gvr when presenting and create a new one after exit
        // presenting. See crbug.com/655242
        if (mNonPresentingGvrContext != null) {
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
            try {
                mNonPresentingGvrContext.resume();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unable to resume mNonPresentingGvrContext", e);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }

        if (mInVr) {
            setupVrModeWindowFlags();
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
            try {
                mVrShell.resume();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unable to resume VrShell", e);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        } else if (mVrDaydreamApi.isDaydreamCurrentViewer()
                && mLastVRExit + REENTER_VR_TIMEOUT_MS > SystemClock.uptimeMillis()) {
            enterVRIfNecessary();
        }
    }

    /**
     * Pauses VR Shell.
     */
    public void maybePauseVR() {
        if (!mVrAvailable) return;
        unregisterDaydreamIntent();

        // When the active web page has a vrdisplayactivate event handler,
        // mListeningForWebVrActivate should be set to true, which means a vrdisplayactive event
        // should be fired once DON flow finished. However, DON flow will pause our activity, which
        // makes the active page becomes invisible. And the event fires before the active page
        // becomes visible again after DON finished. So here we remember the value of
        // mListeningForWebVrActivity before pause and use this value to decide if vrdisplayactivate
        // event should be dispatched in enterVRFromIntent.
        mListeningForWebVrActivateBeforePause = mListeningForWebVrActivate;
        if (mNonPresentingGvrContext != null) {
            mNonPresentingGvrContext.pause();
        }

        // TODO(mthiesse): When VR Shell lives in its own activity, and integrates with Daydream
        // home, pause instead of exiting VR here. For now, because VR Apps shouldn't show up in the
        // non-VR recents, and we don't want ChromeTabbedActivity disappearing, exit VR.
        exitVRIfNecessary(false);
    }

    /**
     * Exits the current VR mode (WebVR or VRShell)
     * @return Whether or not we exited VR.
     */
    public boolean exitVRIfNecessary(boolean returnTo2D) {
        if (!mVrAvailable) return false;
        if (!mInVr) return false;
        shutdownVR(returnTo2D, false);
        return true;
    }

    public void onExitVRResult(int resultCode) {
        assert mVrAvailable;
        if (resultCode == Activity.RESULT_OK) {
            mVrDaydreamApi.setVrModeEnabled(false);
        } else {
            // For now, we don't handle re-entering VR when exit fails, so keep trying to exit.
            if (!mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                mVrDaydreamApi.setVrModeEnabled(false);
            }
        }
    }

    private PendingIntent getPendingEnterVRIntent() {
        return PendingIntent.getActivity(mActivity, 0, mEnterVRIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    /**
     * Registers the Intent to fire after phone inserted into a headset.
     */
    private void registerDaydreamIntent() {
        mVrDaydreamApi.registerDaydreamIntent(getPendingEnterVRIntent());
    }

    /**
     * Unregisters the Intent which registered by this context if any.
     */
    private void unregisterDaydreamIntent() {
        mVrDaydreamApi.unregisterDaydreamIntent();
    }

    @CalledByNative
    private long createNonPresentingNativeContext() {
        if (!mVrAvailable) return 0;

        try {
            Constructor<?> nonPresentingGvrContextConstructor =
                    mNonPresentingGvrContextClass.getConstructor(Activity.class);
            mNonPresentingGvrContext =
                    (NonPresentingGvrContext)
                            nonPresentingGvrContextConstructor.newInstance(mActivity);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            Log.e(TAG, "Unable to instantiate NonPresentingGvrContext", e);
            return 0;
        }
        return mNonPresentingGvrContext.getNativeGvrContext();
    }

    @CalledByNative
    private void shutdownNonPresentingNativeContext() {
        mNonPresentingGvrContext.shutdown();
        mNonPresentingGvrContext = null;
    }

    @CalledByNative
    private void setListeningForWebVrActivate(boolean listening) {
        if (!mVrAvailable) return;
        mListeningForWebVrActivate = listening;
        if (listening) {
            registerDaydreamIntent();
        } else {
            unregisterDaydreamIntent();
        }
    }

    /**
     * Exits VR Shell, performing all necessary cleanup.
     */
    private void shutdownVR(boolean returnTo2D, boolean showTransition) {
        if (!mInVr) return;
        mRequestedWebVR = false;
        if (returnTo2D) {
            if (!showTransition || !mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                mVrDaydreamApi.setVrModeEnabled(false);
            }
        } else {
            mVrDaydreamApi.setVrModeEnabled(false);
            mLastVRExit = SystemClock.uptimeMillis();
        }
        mActivity.setRequestedOrientation(mRestoreOrientation);
        mVrShell.pause();
        removeVrViews();
        clearVrModeWindowFlags();
        destroyVrShell();
        mInVr = false;
        mTab.removeObserver(mTabObserver);
        mTab.updateFullscreenEnabledState();
        mTab.updateBrowserControlsState(BrowserControlsState.SHOWN, true);
    }

    private boolean isVrCoreCompatible() {
        if (mVrCoreVersionChecker != null) {
            return mVrCoreVersionChecker.isVrCoreCompatible();
        }

        if (mVrCoreVersionCheckerClass == null) {
            return false;
        }

        try {
            Constructor<?> mVrCoreVersionCheckerConstructor =
                    mVrCoreVersionCheckerClass.getConstructor();
            mVrCoreVersionChecker =
                    (VrCoreVersionChecker) mVrCoreVersionCheckerConstructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            Log.d(TAG, "Unable to instantiate VrCoreVersionChecker", e);
            return false;
        }
        return mVrCoreVersionChecker.isVrCoreCompatible();
    }

    private boolean createVrDaydreamApi() {
        try {
            Constructor<?> vrPrivateApiConstructor =
                    mVrDaydreamApiClass.getConstructor(Activity.class);
            mVrDaydreamApi = (VrDaydreamApi) vrPrivateApiConstructor.newInstance(mActivity);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            Log.d(TAG, "Unable to instantiate VrDaydreamApi", e);
            return false;
        }
        return true;
    }

    private boolean createVrShell() {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            Constructor<?> vrShellConstructor = mVrShellClass.getConstructor(Activity.class);
            mVrShell = (VrShell) vrShellConstructor.newInstance(mActivity);
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
        if (intent.getBooleanExtra(DAYDREAM_VR_EXTRA, false)) return true;
        if (intent.getCategories() != null) {
            if (intent.getCategories().contains(DAYDREAM_CATEGORY)) return true;
            if (intent.getCategories().contains(CARDBOARD_CATEGORY)) return true;
        }
        return false;
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
        if (mVrShellEnabled == null) {
            if (!LibraryLoader.isInitialized()) {
                return false;
            }
            mVrShellEnabled = ChromeFeatureList.isEnabled(ChromeFeatureList.VR_SHELL);
        }
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
    private native void nativeSetPresentResult(long nativeVrShellDelegate, boolean result);
    private native void nativeDisplayActivate(long nativeVrShellDelegate);
}
