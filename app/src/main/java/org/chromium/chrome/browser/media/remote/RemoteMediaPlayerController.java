// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.MediaRouteChooserDialogFragment;
import android.support.v7.app.MediaRouteControllerDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;

import com.google.android.gms.cast.CastMediaControlIntent;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.media.remote.MediaRouteController.MediaStateListener;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.ui.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The singleton responsible managing the global resources for remote media playback (cast)
 */
public class RemoteMediaPlayerController implements MediaRouteController.UiListener {
    // Singleton instance of the class. May only be accessed from UI thread.
    private static RemoteMediaPlayerController sInstance;

    private static final String TAG = "MediaFling";

    private static final String DEFAULT_CASTING_MESSAGE = "Casting to Chromecast";

    private CastNotificationControl mNotificationControl;

    private Context mCastContextApplicationContext;
    // The Activity that was in the foreground when the video was cast.
    private WeakReference<Activity> mChromeVideoActivity;

    private List<MediaRouteController> mMediaRouteControllers;

    // points to mDefaultRouteSelector, mYouTubeRouteSelector or null
    private MediaRouteController mCurrentRouteController;

    // This is a key for meta-data in the package manifest.
    private static final String REMOTE_MEDIA_PLAYERS_KEY =
            "org.chromium.content.browser.REMOTE_MEDIA_PLAYERS";

    /**
     * The private constructor to make sure the object is only created by the instance() method.
     */
    private RemoteMediaPlayerController() {
        mChromeVideoActivity = new WeakReference<Activity>(null);
        mMediaRouteControllers = new ArrayList<MediaRouteController>();
    }

    /**
     * @return The poster image for the currently playing remote video, null if there's none.
     */
    public Bitmap getPoster() {
        if (mCurrentRouteController == null) return null;
        return mCurrentRouteController.getPoster();
    }

    /**
     * The singleton instance access method for native objects. Must be called on the UI thread
     * only.
     */
    public static RemoteMediaPlayerController instance() {
        ThreadUtils.assertOnUiThread();

        if (sInstance == null) sInstance = new RemoteMediaPlayerController();
        if (sInstance.mChromeVideoActivity.get() == null) sInstance.linkToBrowserActivity();

        return sInstance;
    }

    /**
     * Gets the MediaRouteController for a video, creating it if necessary.
     * @param frameUrl The Url of the frame containing the video
     * @return the MediaRouteController, or null if none.
     */
    public MediaRouteController getMediaRouteController(String sourceUrl, String frameUrl) {
        for (MediaRouteController controller: mMediaRouteControllers) {
            if (controller.canPlayMedia(sourceUrl, frameUrl)) {
                return controller;
            }
        }
        return null;
    }

    /**
     * Gets the default MediaRouteController, creating it if necessary.
     * @return the default MediaRouteController.
     */
    public List<MediaRouteController> getMediaRouteControllers() {
        return mMediaRouteControllers;
    }

    /**
     * Links this object to the Activity that owns the video, if it exists.
     *
     */
    private void linkToBrowserActivity() {

        Activity currentActivity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (currentActivity != null) {
            mChromeVideoActivity = new WeakReference<Activity>(currentActivity);

            mCastContextApplicationContext = currentActivity.getApplicationContext();
            createMediaRouteControllers(currentActivity);
        }
    }

    /**
     * Create the mediaRouteControllers
     * @param context - the current Android Context
     */
    public void createMediaRouteControllers(Context context) {
        // We only need to do this once
        if (!mMediaRouteControllers.isEmpty()) return;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            String classNameString = bundle.getString(REMOTE_MEDIA_PLAYERS_KEY);

            if (classNameString != null) {
                String[] classNames = classNameString.split(",");
                for (String className : classNames) {
                    Log.d(TAG, "Adding remote media route controller %s", className.trim());
                    Class<?> mediaRouteControllerClass = Class.forName(className.trim());
                    Object mediaRouteController = mediaRouteControllerClass.newInstance();
                    assert mediaRouteController instanceof MediaRouteController;
                    mMediaRouteControllers.add((MediaRouteController) mediaRouteController);
                }
            }
        } catch (NameNotFoundException | ClassNotFoundException | SecurityException
                | InstantiationException | IllegalAccessException | IllegalArgumentException e) {
            // Should never happen, implies corrupt AndroidManifest
            Log.e(TAG, "Couldn't instatiate MediaRouteControllers", e);
            assert false;
        }
    }

    private void onStateReset(MediaRouteController controller) {

        if (!controller.initialize()) return;

        mNotificationControl = CastNotificationControl.getOrCreate(
                mChromeVideoActivity.get(), controller);
        mNotificationControl.setPosterBitmap(getPoster());
        controller.prepareMediaRoute();

        controller.addUiListener(this);
    }

    /**
     * Called when a lower layer requests that a video be cast. This will typically be a request
     * from Blink when the cast button is pressed on the default video controls.
     * @param player the player for which cast is being requested
     * @param frameUrl the URL of the frame containing the video, needed for YouTube videos
     */
    public void requestRemotePlayback(
            MediaRouteController.MediaStateListener player, MediaRouteController controller) {
        Activity currentActivity = ApplicationStatus.getLastTrackedFocusedActivity();
        mChromeVideoActivity = new WeakReference<Activity>(currentActivity);

        if (mCurrentRouteController != null && controller != mCurrentRouteController) {
            mCurrentRouteController.release();
        }

        onStateReset(controller);
        showMediaRouteDialog(player, controller, currentActivity);

    }

    /**
     * Called when a lower layer requests control of a video that is being cast.
     * @param player The player for which remote playback control is being requested.
     */
    public void requestRemotePlaybackControl(MediaRouteController.MediaStateListener player) {
        // Player should match currently remotely played item, but there
        // can be a race between various
        // ways that the a video can stop playing remotely. Check that the
        // player is current, and ignore if not.

        if (mCurrentRouteController == null) return;
        if (mCurrentRouteController.getMediaStateListener() != player) return;

        showMediaRouteControlDialog(player, ApplicationStatus.getLastTrackedFocusedActivity());
    }

    /**
     * Called when a lower layer requests to stop casting the video.
     * @param player The player to stop remote playback for.
     */
    public void requestRemotePlaybackStop(MediaRouteController.MediaStateListener player) {
        if (mCurrentRouteController == null) return;
        if (mCurrentRouteController.getMediaStateListener() != player) return;

        mCurrentRouteController.release();
    }

    private void showMediaRouteDialog(MediaStateListener player, MediaRouteController controller,
            Activity activity) {

        FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
        if (fm == null) {
            throw new IllegalStateException("The activity must be a subclass of FragmentActivity");
        }

        MediaRouteDialogFactory factory = new MediaRouteChooserDialogFactory(player, controller,
                activity);

        if (fm.findFragmentByTag(
                "android.support.v7.mediarouter:MediaRouteChooserDialogFragment") != null) {
            Log.w(TAG, "showDialog(): Route chooser dialog already showing!");
            return;
        }
        MediaRouteChooserDialogFragment f = factory.onCreateChooserDialogFragment();

        f.setRouteSelector(controller.buildMediaRouteSelector());
        f.show(fm, "android.support.v7.mediarouter:MediaRouteChooserDialogFragment");
    }

    private void showMediaRouteControlDialog(MediaStateListener player, Activity activity) {
        FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
        if (fm == null) {
            throw new IllegalStateException("The activity must be a subclass of FragmentActivity");
        }
        MediaRouteDialogFactory factory = new MediaRouteControllerDialogFactory(player);

        if (fm.findFragmentByTag(
                "android.support.v7.mediarouter:MediaRouteControllerDialogFragment") != null) {
            Log.w(TAG, "showDialog(): Route controller dialog already showing!");
            return;
        }
        MediaRouteControllerDialogFragment f = factory.onCreateControllerDialogFragment();

        f.show(fm, "android.support.v7.mediarouter:MediaRouteControllerDialogFragment");
    }

    /**
     * @return the currently playing MediaRouteController
     */
    public MediaRouteController getCurrentlyPlayingMediaRouteController() {
        return mCurrentRouteController;
    }

    /**
     * Set the current MediaRouteController
     * @param controller the controller
     */
    public void setCurrentMediaRouteController(MediaRouteController controller) {
        mCurrentRouteController = controller;
    }

    private CastNotificationControl getNotificationControl() {
        return mNotificationControl;
    }

    @Override
    public void onPrepared(MediaRouteController mediaRouteController) {
    }

    @Override
    public void onPlaybackStateChanged(PlayerState newState) {
    }

    @Override
    public void onError(int error, String errorMessage) {
        if (error == CastMediaControlIntent.ERROR_CODE_SESSION_START_FAILED) {
            showMessageToast(errorMessage);
        }
    }

    @Override
    public void onDurationUpdated(long durationMillis) {}

    @Override
    public void onPositionChanged(long positionMillis) {}

    @Override
    public void onTitleChanged(String title) {}

    @Override
    public void onRouteSelected(String routeName, MediaRouteController mediaRouteController) {
        if (mCurrentRouteController != mediaRouteController) {
            mCurrentRouteController = mediaRouteController;
            resetPlayingVideo();
        }
    }

    /**
     * Gets some text to tell the user that the video is being cast.
     * @param routeName The name of the route on which the video is being cast.
     * @return A String to be shown to the user.
     */
    public String getCastingMessage(String routeName) {
        String castingMessage = DEFAULT_CASTING_MESSAGE;
        if (mCastContextApplicationContext != null) {
            castingMessage = mCastContextApplicationContext.getString(
                    R.string.cast_casting_video, routeName);
        }
        return castingMessage;
    }

    // Note that, after switching MediaRouteControllers onRouteUnselected may be called for
    // the old media route controller, so this should not do anything to
    // mCurrentRouteController
    @Override
    public void onRouteUnselected(MediaRouteController mediaRouteController) {
        if (mediaRouteController == mCurrentRouteController) {
            mCurrentRouteController = null;
        }
    }

    private void showMessageToast(String message) {
        Toast toast = Toast.makeText(mCastContextApplicationContext, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void resetPlayingVideo() {
        if (mNotificationControl != null) {
            mNotificationControl.setRouteController(mCurrentRouteController);
        }
    }

    @VisibleForTesting
    static RemoteMediaPlayerController getIfExists() {
        return sInstance;
    }

}
