// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.WindowManager;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LoaderErrors;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.metrics.MemoryUma;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tabmodel.DocumentModeAssassin;
import org.chromium.chrome.browser.upgrade.UpgradeActivity;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.ui.base.DeviceFormFactor;

import java.lang.reflect.Field;

/**
 * An activity that talks with application and activity level delegates for async initialization.
 */
public abstract class AsyncInitializationActivity extends AppCompatActivity implements
        ChromeActivityNativeDelegate, BrowserParts {

    protected final Handler mHandler;

    // Time at which onCreate is called. This is realtime, counted in ms since device boot.
    private long mOnCreateTimestampMs;

    // Time at which onCreate is called. This is uptime, to be sent to native code.
    private long mOnCreateTimestampUptimeMs;
    private Bundle mSavedInstanceState;
    private int mCurrentOrientation = Surface.ROTATION_0;
    private boolean mDestroyed;
    private NativeInitializationController mNativeInitializationController;
    private MemoryUma mMemoryUma;
    private long mLastUserInteractionTime;
    private boolean mIsTablet;

    public AsyncInitializationActivity() {
        mHandler = new Handler();
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        super.onDestroy();
    }

    @Override
    // TODO(estevenson): Replace with Build.VERSION_CODES.N when available.
    @TargetApi(24)
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);

        // On N+, Chrome should always retain the tab strip layout on tablets. Normally in
        // multi-window, if Chrome is launched into a smaller screen Android will load the tab
        // switcher resources. Overriding the smallestScreenWidthDp in the Configuration ensures
        // Android will load the tab strip resources. See crbug.com/588838.
        if (Build.VERSION.CODENAME.equals("N") || Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            int smallestDeviceWidthDp = DeviceFormFactor.getSmallestDeviceWidthDp(this);

            if (smallestDeviceWidthDp >= DeviceFormFactor.MINIMUM_TABLET_WIDTH_DP) {
                Configuration overrideConfiguration = new Configuration();
                overrideConfiguration.smallestScreenWidthDp = smallestDeviceWidthDp;
                applyOverrideConfiguration(overrideConfiguration);
            }
        }
    }

    @Override
    public void preInflationStartup() {
        // On some devices, OEM modifications have been made to the resource loader that cause the
        // DeviceFormFactor calculation of whether a device is using tablet resources to be
        // incorrect. Check which resources were actually loaded and set the DeviceFormFactor
        // values. See crbug.com/662338.
        boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
        boolean isLargeTablet = getResources().getBoolean(R.bool.is_large_tablet);
        DeviceFormFactor.setIsTablet(isTablet, isLargeTablet);
        mIsTablet = isTablet;
    }

    @Override
    public final void setContentViewAndLoadLibrary() {
        // setContentView inflating the decorView and the basic UI hierarhcy as stubs.
        // This is done here before kicking long running I/O because inflation includes accessing
        // resource files(xmls etc) even if we are inflating views defined by the framework. If this
        // operation gets blocked because other long running I/O are running, we delay onCreate(),
        // onStart() and first draw consequently.

        setContentView();
        if (mLaunchBehindWorkaround != null) mLaunchBehindWorkaround.onSetContentView();

        // Kick off long running IO tasks that can be done in parallel.
        mNativeInitializationController = new NativeInitializationController(this);
        initializeChildProcessCreationParams();
        mNativeInitializationController.startBackgroundTasks(shouldAllocateChildConnection());
    }

    /** Controls the parameter of {@link NativeInitializationController#startBackgroundTasks()}.*/
    @VisibleForTesting
    public boolean shouldAllocateChildConnection() {
        return true;
    }

    /**
     * Allow derived classes to initialize their own {@link ChildProcessCreationParams}.
     */
    protected void initializeChildProcessCreationParams() {}

    @Override
    public void postInflationStartup() {
        final View firstDrawView = getViewToBeDrawnBeforeInitializingNative();
        assert firstDrawView != null;
        ViewTreeObserver.OnPreDrawListener firstDrawListener =
                new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                firstDrawView.getViewTreeObserver().removeOnPreDrawListener(this);
                onFirstDrawComplete();
                return true;
            }
        };
        firstDrawView.getViewTreeObserver().addOnPreDrawListener(firstDrawListener);
    }

    /**
     * @return The primary view that must have completed at least one draw before initializing
     *         native.  This must be non-null.
     */
    protected View getViewToBeDrawnBeforeInitializingNative() {
        return findViewById(android.R.id.content);
    }

    @Override
    public void maybePreconnect() {
        TraceEvent.begin("maybePreconnect");
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            final String url = intent.getDataString();
            WarmupManager.getInstance()
                .maybePreconnectUrlAndSubResources(Profile.getLastUsedProfile(), url);
        }
        TraceEvent.end("maybePreconnect");
    }

    @Override
    public void initializeCompositor() { }

    @Override
    public void initializeState() { }

    @Override
    public void finishNativeInitialization() {
        // Set up the initial orientation of the device.
        checkOrientation();
        findViewById(android.R.id.content).addOnLayoutChangeListener(
                new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        checkOrientation();
                    }
                });
        mMemoryUma = new MemoryUma();
        mNativeInitializationController.onNativeInitializationComplete();
    }

    /**
     * Actions that may be run at some point after startup. Place tasks that are not critical to the
     * startup path here.  This method will be called automatically and should not be called
     * directly by subclasses.
     */
    protected void onDeferredStartup() { }

    @Override
    public void onStartupFailure() {
        ProcessInitException e =
                new ProcessInitException(LoaderErrors.LOADER_ERROR_NATIVE_STARTUP_FAILED);
        ChromeApplication.reportStartupErrorAndExit(e);
    }

    /**
     * Extending classes should override {@link AsyncInitializationActivity#preInflationStartup()},
     * {@link AsyncInitializationActivity#setContentView()} and
     * {@link AsyncInitializationActivity#postInflationStartup()} instead of this call which will
     * be called on that order.
     */
    @Override
    @SuppressLint("MissingSuperCall")  // Called in onCreateInternal.
    protected final void onCreate(Bundle savedInstanceState) {
        TraceEvent.begin("AsyncInitializationActivity.onCreate()");
        onCreateInternal(savedInstanceState);
        TraceEvent.end("AsyncInitializationActivity.onCreate()");
    }

    private final void onCreateInternal(Bundle savedInstanceState) {
        if (DocumentModeAssassin.getInstance().isMigrationNecessary()) {
            super.onCreate(null);

            // Kick the user to the MigrationActivity.
            UpgradeActivity.launchInstance(this, getIntent());

            // Don't remove this task -- it may be a DocumentActivity that exists only in Recents.
            finish();
            return;
        }

        if (!isStartedUpCorrectly(getIntent())) {
            super.onCreate(null);
            ApiCompatibilityUtils.finishAndRemoveTask(this);
            return;
        }

        super.onCreate(savedInstanceState);
        mOnCreateTimestampMs = SystemClock.elapsedRealtime();
        mOnCreateTimestampUptimeMs = SystemClock.uptimeMillis();
        mSavedInstanceState = savedInstanceState;

        ChromeBrowserInitializer.getInstance(this).handlePreNativeStartup(this);
    }

    /**
     * Whether or not the Activity was started up via a valid Intent.
     */
    protected boolean isStartedUpCorrectly(Intent intent) {
        return true;
    }

    /**
     * @return The elapsed real time for the activity creation in ms.
     */
    protected long getOnCreateTimestampUptimeMs() {
        return mOnCreateTimestampUptimeMs;
    }

    /**
     * @return The uptime for the activity creation in ms.
     */
    protected long getOnCreateTimestampMs() {
        return mOnCreateTimestampMs;
    }

    /**
     * @return The saved bundle for the last recorded state.
     */
    protected Bundle getSavedInstanceState() {
        return mSavedInstanceState;
    }

    /**
     * Resets the saved state and makes it unavailable for the rest of the activity lifecycle.
     */
    protected void resetSavedInstanceState() {
        mSavedInstanceState = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        mNativeInitializationController.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mNativeInitializationController.onResume();
        if (mLaunchBehindWorkaround != null) mLaunchBehindWorkaround.onResume();
    }

    @Override
    public void onPause() {
        mNativeInitializationController.onPause();
        super.onPause();
        if (mLaunchBehindWorkaround != null) mLaunchBehindWorkaround.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMemoryUma != null) mMemoryUma.onStop();
        mNativeInitializationController.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null) return;
        mNativeInitializationController.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mNativeInitializationController.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public final void onCreateWithNative() {
        try {
            ChromeBrowserInitializer.getInstance(this).handlePostNativeStartup(true, this);
        } catch (ProcessInitException e) {
            ChromeApplication.reportStartupErrorAndExit(e);
        }
    }

    @Override
    public void onStartWithNative() { }

    @Override
    public void onResumeWithNative() { }

    @Override
    public void onPauseWithNative() { }

    @Override
    public void onStopWithNative() { }

    @Override
    public boolean isActivityDestroyed() {
        return mDestroyed;
    }

    @Override
    public boolean isActivityFinishing() {
        return isFinishing();
    }

    @Override
    public boolean shouldStartGpuProcess() {
        return true;
    }

    @Override
    public final void onFirstDrawComplete() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mNativeInitializationController.firstDrawComplete();
            }
        });
    }

    @Override
    public void onNewIntentWithNative(Intent intent) { }

    @Override
    public Intent getInitialIntent() {
        return getIntent();
    }

    @Override
    public boolean onActivityResultWithNative(int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mMemoryUma != null) mMemoryUma.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mMemoryUma != null) mMemoryUma.onTrimMemory(level);
    }

    /**
     * @return Whether the activity is running in tablet mode.
     */
    public boolean isTablet() {
        return mIsTablet;
    }

    @Override
    public void onUserInteraction() {
        mLastUserInteractionTime = SystemClock.elapsedRealtime();
    }

    /**
     * @return timestamp when the last user interaction was made.
     */
    public long getLastUserInteractionTime() {
        return mLastUserInteractionTime;
    }

    /**
     * Called when the orientation of the device changes.  The orientation is checked/detected on
     * root view layouts.
     * @param orientation One of {@link Surface#ROTATION_0} (no rotation),
     *                    {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180}, or
     *                    {@link Surface#ROTATION_270}.
     */
    protected void onOrientationChange(int orientation) {
    }

    private void checkOrientation() {
        WindowManager wm = getWindowManager();
        if (wm == null) return;

        Display display = wm.getDefaultDisplay();
        if (display == null) return;

        int oldOrientation = mCurrentOrientation;
        mCurrentOrientation = display.getRotation();

        if (oldOrientation != mCurrentOrientation) onOrientationChange(mCurrentOrientation);
    }

    /**
     * Removes the window background.
     */
    protected void removeWindowBackground() {
        boolean removeWindowBackground = true;
        try {
            Field field = Settings.Secure.class.getField(
                    "ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED");
            field.setAccessible(true);

            if (field.getType() == String.class) {
                String accessibilityMagnificationSetting = (String) field.get(null);
                // When Accessibility magnification is turned on, setting a null window
                // background causes the overlaid android views to stretch when panning.
                // (crbug/332994)
                if (Settings.Secure.getInt(
                        getContentResolver(), accessibilityMagnificationSetting) == 1) {
                    removeWindowBackground = false;
                }
            }
        } catch (SettingNotFoundException e) {
            // Window background is removed if an exception occurs.
        } catch (NoSuchFieldException e) {
            // Window background is removed if an exception occurs.
        } catch (IllegalAccessException e) {
            // Window background is removed if an exception occurs.
        } catch (IllegalArgumentException e) {
            // Window background is removed if an exception occurs.
        }
        if (removeWindowBackground) getWindow().setBackgroundDrawable(null);
    }

    /**
     * Extending classes should implement this and call {@link Activity#setContentView(int)} in it.
     */
    protected abstract void setContentView();

    /**
     * Lollipop (pre-MR1) makeTaskLaunchBehind() workaround.
     *
     * Our activity's surface is destroyed at the end of the new activity animation
     * when ActivityOptions.makeTaskLaunchBehind() is used, which causes a crash.
     * Making everything invisible when paused prevents the crash, since view changes
     * will not trigger draws to the missing surface. However, we need to wait until
     * after the first draw to make everything invisible, as the activity launch
     * animation needs a full frame (or it will delay the animation excessively).
     */
    private final LaunchBehindWorkaround mLaunchBehindWorkaround =
            (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP)
                    ? new LaunchBehindWorkaround()
                    : null;

    private class LaunchBehindWorkaround {
        private boolean mPaused = false;

        private View getDecorView() {
            return getWindow().getDecorView();
        }

        private ViewTreeObserver getViewTreeObserver() {
            return getDecorView().getViewTreeObserver();
        }

        private void onPause() {
            mPaused = true;
        }

        public void onResume() {
            mPaused = false;
            getDecorView().setVisibility(View.VISIBLE);
        }

        public void onSetContentView() {
            getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
        }

        // Note, we probably want onDrawListener here, but it isn't being called
        // when I add this to the decorView. However, it should be the same for
        // this purpose as long as no other pre-draw listener returns false.
        private final OnPreDrawListener mPreDrawListener = new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mPaused) {
                            getDecorView().setVisibility(View.GONE);
                        }
                        getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
                    }
                });
                return true;
            }
        };
    }
}
