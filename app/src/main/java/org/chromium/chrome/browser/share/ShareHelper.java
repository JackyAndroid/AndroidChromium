// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.share;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.ui.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A helper class that helps to start an intent to share titles and URLs.
 */
public class ShareHelper {

    /** Interface that receives intents for testing (to fake out actually sending them). */
    public interface FakeIntentReceiver {
        /** Sets the intent to send back in the broadcast. */
        public void setIntentToSendBack(Intent intent);

        /** Called when a custom chooser dialog is shown. */
        public void onCustomChooserShown(AlertDialog dialog);

        /**
         * Simulates firing the given intent, without actually doing so.
         *
         * @param context The context that will receive broadcasts from the simulated activity.
         * @param intent The intent to send to the system.
         */
        public void fireIntent(Context context, Intent intent);
    }

    private static final String TAG = "share";

    /** The task ID of the activity that triggered the share action. */
    public static final String EXTRA_TASK_ID = "org.chromium.chrome.extra.TASK_ID";

    private static final String JPEG_EXTENSION = ".jpg";
    private static final String PACKAGE_NAME_KEY = "last_shared_package_name";
    private static final String CLASS_NAME_KEY = "last_shared_class_name";
    private static final String EXTRA_SHARE_SCREENSHOT_AS_STREAM = "share_screenshot_as_stream";
    private static final long COMPONENT_INFO_READ_TIMEOUT_IN_MS = 1000;

    /**
     * Directory name for shared images.
     *
     * Named "screenshot" for historical reasons as we only initially shared screenshot images.
     */
    private static final String SHARE_IMAGES_DIRECTORY_NAME = "screenshot";

    /** Force the use of a Chrome-specific intent chooser, not the system chooser. */
    private static boolean sForceCustomChooserForTesting = false;

    /** If non-null, will be used instead of the real activity. */
    private static FakeIntentReceiver sFakeIntentReceiverForTesting;

    private ShareHelper() {}

    private static void fireIntent(Activity activity, Intent intent) {
        if (sFakeIntentReceiverForTesting != null) {
            Context context = activity.getApplicationContext();
            sFakeIntentReceiverForTesting.fireIntent(context, intent);
        } else {
            activity.startActivity(intent);
        }
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private static void deleteShareImageFiles(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            for (File f : file.listFiles()) deleteShareImageFiles(f);
        }
        if (!file.delete()) {
            Log.w(TAG, "Failed to delete share image file: %s", file.getAbsolutePath());
        }
    }

    /**
     * Force the use of a Chrome-specific intent chooser, not the system chooser.
     *
     * This emulates the behavior on pre Lollipop-MR1 systems, where the system chooser is not
     * available.
     */
    public static void setForceCustomChooserForTesting(boolean enabled) {
        sForceCustomChooserForTesting = enabled;
    }

    /**
     * Uses a FakeIntentReceiver instead of actually sending intents to the system.
     *
     * @param receiver The object to send intents to. If null, resets back to the default behavior
     *                 (really send intents).
     */
    public static void setFakeIntentReceiverForTesting(FakeIntentReceiver receiver) {
        sFakeIntentReceiverForTesting = receiver;
    }

    /**
     * Callback interface for when a target is chosen.
     */
    public static interface TargetChosenCallback {
        /**
         * Called when the user chooses a target in the share dialog.
         *
         * Note that if the user cancels the share dialog, this callback is never called.
         */
        public void onTargetChosen(ComponentName chosenComponent);

        /**
         * Called when the user cancels the share dialog.
         *
         * Guaranteed that either this, or onTargetChosen (but not both) will be called, eventually.
         */
        public void onCancel();
    }

    /**
     * Receiver to record the chosen component when sharing an Intent.
     */
    static class TargetChosenReceiver extends BroadcastReceiver {
        private static final String EXTRA_RECEIVER_TOKEN = "receiver_token";
        private static final Object LOCK = new Object();

        private static String sTargetChosenReceiveAction;
        private static TargetChosenReceiver sLastRegisteredReceiver;

        private final boolean mSaveLastUsed;
        @Nullable private final TargetChosenCallback mCallback;

        private TargetChosenReceiver(boolean saveLastUsed,
                                     @Nullable TargetChosenCallback callback) {
            mSaveLastUsed = saveLastUsed;
            mCallback = callback;
        }

        static boolean isSupported() {
            return !sForceCustomChooserForTesting
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
        static void sendChooserIntent(boolean saveLastUsed, Activity activity,
                                      Intent sharingIntent,
                                      @Nullable TargetChosenCallback callback) {
            synchronized (LOCK) {
                if (sTargetChosenReceiveAction == null) {
                    sTargetChosenReceiveAction = activity.getPackageName() + "/"
                            + TargetChosenReceiver.class.getName() + "_ACTION";
                }
                Context context = activity.getApplicationContext();
                if (sLastRegisteredReceiver != null) {
                    context.unregisterReceiver(sLastRegisteredReceiver);
                    // Must cancel the callback (to satisfy guarantee that exactly one method of
                    // TargetChosenCallback is called).
                    // TODO(mgiuca): This should be called immediately upon cancelling the chooser,
                    // not just when the next share takes place (https://crbug.com/636274).
                    sLastRegisteredReceiver.cancel();
                }
                sLastRegisteredReceiver = new TargetChosenReceiver(saveLastUsed, callback);
                context.registerReceiver(
                        sLastRegisteredReceiver, new IntentFilter(sTargetChosenReceiveAction));
            }

            Intent intent = new Intent(sTargetChosenReceiveAction);
            intent.setPackage(activity.getPackageName());
            intent.putExtra(EXTRA_RECEIVER_TOKEN, sLastRegisteredReceiver.hashCode());
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            Intent chooserIntent = Intent.createChooser(sharingIntent,
                    activity.getString(R.string.share_link_chooser_title),
                    pendingIntent.getIntentSender());
            if (sFakeIntentReceiverForTesting != null) {
                sFakeIntentReceiverForTesting.setIntentToSendBack(intent);
            }
            fireIntent(activity, chooserIntent);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (LOCK) {
                if (sLastRegisteredReceiver != this) return;
                context.getApplicationContext().unregisterReceiver(sLastRegisteredReceiver);
                sLastRegisteredReceiver = null;
            }
            if (!intent.hasExtra(EXTRA_RECEIVER_TOKEN)
                    || intent.getIntExtra(EXTRA_RECEIVER_TOKEN, 0) != this.hashCode()) {
                return;
            }

            ComponentName target = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);
            if (mCallback != null) {
                mCallback.onTargetChosen(target);
            }
            if (mSaveLastUsed && target != null) {
                setLastShareComponentName(target);
            }
        }

        private void cancel() {
            if (mCallback != null) {
                mCallback.onCancel();
            }
        }
    }

    /**
     * Clears all shared image files.
     */
    public static void clearSharedImages() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File imagePath = UiUtils.getDirectoryForImageCapture(
                            ContextUtils.getApplicationContext());
                    deleteShareImageFiles(new File(imagePath, SHARE_IMAGES_DIRECTORY_NAME));
                } catch (IOException ie) {
                    // Ignore exception.
                }
                return null;
            }
        }.execute();
    }

    /**
     * Creates and shows a share intent picker dialog or starts a share intent directly with the
     * activity that was most recently used to share based on shareDirectly value.
     *
     * This function will save |screenshot| under {app's root}/files/images/screenshot (or
     * /sdcard/DCIM/browser-images/screenshot if ADK is lower than JB MR2).
     * Cleaning up doesn't happen automatically, and so an app should call clearSharedScreenshots()
     * explicitly when needed.
     *
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param saveLastUsed Whether to save the chosen activity for future direct sharing.
     * @param activity Activity that is used to access package manager.
     * @param title Title of the page to be shared.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param url URL of the page to be shared.
     * @param offlineUri URI to the offline MHTML file to be shared.
     * @param screenshotUri Uri of the screenshot of the page to be shared.
     * @param callback Optional callback to be called when user makes a choice. Will not be called
     *                 if receiving a response when the user makes a choice is not supported (on
     *                 older Android versions).
     */
    public static void share(boolean shareDirectly, boolean saveLastUsed, Activity activity,
            String title, String text, String url, @Nullable Uri offlineUri, Uri screenshotUri,
            @Nullable TargetChosenCallback callback) {
        if (shareDirectly) {
            shareWithLastUsed(activity, title, text, url, offlineUri, screenshotUri);
        } else if (TargetChosenReceiver.isSupported()) {
            makeIntentAndShare(saveLastUsed, activity, title, text, url, offlineUri, screenshotUri,
                    null, callback);
        } else {
            showShareDialog(
                    saveLastUsed, activity, title, text, url, offlineUri, screenshotUri, callback);
        }
    }

    /**
     * Trigger the share action for the given image data.
     * @param activity The activity used to trigger the share action.
     * @param jpegImageData The image data to be shared in jpeg format.
     */
    public static void shareImage(final Activity activity, final byte[] jpegImageData) {
        if (jpegImageData.length == 0) {
            Log.w(TAG, "Share failed -- Received image contains no data.");
            return;
        }

        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... params) {
                FileOutputStream fOut = null;
                try {
                    File path = new File(UiUtils.getDirectoryForImageCapture(activity),
                            SHARE_IMAGES_DIRECTORY_NAME);
                    if (path.exists() || path.mkdir()) {
                        File saveFile = File.createTempFile(
                                String.valueOf(System.currentTimeMillis()), JPEG_EXTENSION, path);
                        fOut = new FileOutputStream(saveFile);
                        fOut.write(jpegImageData);
                        fOut.flush();
                        fOut.close();

                        return saveFile;
                    } else {
                        Log.w(TAG, "Share failed -- Unable to create share image directory.");
                    }
                } catch (IOException ie) {
                    if (fOut != null) {
                        try {
                            fOut.close();
                        } catch (IOException e) {
                            // Ignore exception.
                        }
                    }
                }

                return null;
            }

            @Override
            protected void onPostExecute(File saveFile) {
                if (saveFile == null) return;

                if (ApplicationStatus.getStateForApplication()
                        != ApplicationState.HAS_DESTROYED_ACTIVITIES) {
                    Uri imageUri = UiUtils.getUriForImageCaptureFile(activity, saveFile);

                    Intent chooserIntent = Intent.createChooser(getShareImageIntent(imageUri),
                            activity.getString(R.string.share_link_chooser_title));
                    fireIntent(activity, chooserIntent);
                }
            }
        }.execute();
    }

    /**
     * Persists the screenshot file and notifies the file provider that the file is ready to be
     * accessed by the client.
     *
     * The bitmap is compressed to JPEG before being written to the file.
     *
     * @param screenshot  The screenshot bitmap to be written to file.
     * @param callback    The callback that will be called once the bitmap is saved.
     */
    public static void saveScreenshotToDisk(final Bitmap screenshot, final Context context,
            final Callback<Uri> callback) {
        if (screenshot == null) {
            callback.onResult(null);
            return;
        }

        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... params) {
                FileOutputStream fOut = null;
                try {
                    File path = new File(UiUtils.getDirectoryForImageCapture(context) + "/"
                            + SHARE_IMAGES_DIRECTORY_NAME);
                    if (path.exists() || path.mkdir()) {
                        String fileName = String.valueOf(System.currentTimeMillis());
                        File saveFile = File.createTempFile(fileName, JPEG_EXTENSION, path);
                        fOut = new FileOutputStream(saveFile);
                        screenshot.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
                        return saveFile;
                    }
                } catch (IOException ie) {
                    Log.w(TAG, "Ignoring IOException when saving screenshot.", ie);
                } finally {
                    StreamUtil.closeQuietly(fOut);
                }

                return null;
            }

            @Override
            protected void onPostExecute(File savedFile) {
                Uri fileUri = null;
                if (ApplicationStatus.getStateForApplication()
                        != ApplicationState.HAS_DESTROYED_ACTIVITIES
                        && savedFile != null) {
                    fileUri = UiUtils.getUriForImageCaptureFile(context, savedFile);
                }
                callback.onResult(fileUri);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Creates and shows a share intent picker dialog.
     *
     * @param saveLastUsed Whether to save the chosen activity for future direct sharing.
     * @param activity Activity that is used to access package manager.
     * @param title Title of the page to be shared.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param url URL of the page to be shared.
     * @oaram offlineUri URI of the offline page to be shared.
     * @param screenshotUri Uri of the screenshot of the page to be shared.
     * @param callback Optional callback to be called when user makes a choice. Will not be called
     *                 if receiving a response when the user makes a choice is not supported (on
     *                 older Android versions).
     */
    private static void showShareDialog(final boolean saveLastUsed, final Activity activity,
            final String title, final String text, final String url, final Uri offlineUri,
            final Uri screenshotUri, @Nullable final TargetChosenCallback callback) {
        Intent intent = getShareIntent(activity, title, text, url, null, null);
        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> resolveInfoList = manager.queryIntentActivities(intent, 0);
        assert resolveInfoList.size() > 0;
        if (resolveInfoList.size() == 0) return;
        Collections.sort(resolveInfoList, new ResolveInfo.DisplayNameComparator(manager));

        final ShareDialogAdapter adapter =
                new ShareDialogAdapter(activity, manager, resolveInfoList);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        builder.setTitle(activity.getString(R.string.share_link_chooser_title));
        builder.setAdapter(adapter, null);

        // Need a mutable object to record whether the callback has been fired.
        final boolean[] callbackCalled = new boolean[1];

        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ResolveInfo info = adapter.getItem(position);
                ActivityInfo ai = info.activityInfo;
                ComponentName component =
                        new ComponentName(ai.applicationInfo.packageName, ai.name);
                if (callback != null && !callbackCalled[0]) {
                    callback.onTargetChosen(component);
                    callbackCalled[0] = true;
                }
                if (saveLastUsed) setLastShareComponentName(component);
                makeIntentAndShare(false, activity, title, text, url, offlineUri, screenshotUri,
                        component, null);
                dialog.dismiss();
            }
        });

        if (callback != null) {
            dialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (!callbackCalled[0]) {
                        callback.onCancel();
                        callbackCalled[0] = true;
                    }
                }
            });
        }

        if (sFakeIntentReceiverForTesting != null) {
            sFakeIntentReceiverForTesting.onCustomChooserShown(dialog);
        }
    }

    /**
     * Starts a share intent with the activity that was most recently used to share.
     * If there is no most recently used activity, it does nothing.
     * @param activity Activity that is used to start the share intent.
     * @param title Title of the page to be shared.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param url URL of the page to be shared.
     * @oaram offlineUri URI of the offline page to be shared.
     * @param screenshotUri Uri of the screenshot of the page to be shared.
     */
    private static void shareWithLastUsed(Activity activity, String title, String text, String url,
            Uri offlineUri, Uri screenshotUri) {
        ComponentName component = getLastShareComponentName();
        if (component == null) return;
        makeIntentAndShare(
                false, activity, title, text, url, offlineUri, screenshotUri, component, null);
    }

    private static void shareIntent(boolean saveLastUsed, Activity activity, Intent sharingIntent,
                                    @Nullable TargetChosenCallback callback) {
        if (sharingIntent.getComponent() != null) {
            // If a component was specified, there should not also be a callback.
            assert callback == null;
            fireIntent(activity, sharingIntent);
        } else {
            assert TargetChosenReceiver.isSupported();
            TargetChosenReceiver.sendChooserIntent(saveLastUsed, activity, sharingIntent, callback);
        }
    }

    private static void makeIntentAndShare(final boolean saveLastUsed, final Activity activity,
            final String title, final String text, final String url, final Uri offlineUri,
            final Uri screenshotUri, final ComponentName component,
            @Nullable final TargetChosenCallback callback) {
        Intent intent = getDirectShareIntentForComponent(
                activity, title, text, url, offlineUri, screenshotUri, component);
        shareIntent(saveLastUsed, activity, intent, callback);
    }

    /**
     * Set the icon and the title for the menu item used for direct share.
     *
     * @param activity Activity that is used to access the package manager.
     * @param item The menu item that is used for direct share
     */
    public static void configureDirectShareMenuItem(Activity activity, MenuItem item) {
        Drawable directShareIcon = null;
        CharSequence directShareTitle = null;

        final ComponentName component = getLastShareComponentName();
        boolean isComponentValid = false;
        if (component != null) {
            Intent intent = getShareIntent(activity, "", "", "", null, null);
            intent.setPackage(component.getPackageName());
            PackageManager manager = activity.getPackageManager();
            List<ResolveInfo> resolveInfoList = manager.queryIntentActivities(intent, 0);
            for (ResolveInfo info : resolveInfoList) {
                ActivityInfo ai = info.activityInfo;
                if (component.equals(new ComponentName(ai.applicationInfo.packageName, ai.name))) {
                    isComponentValid = true;
                    break;
                }
            }
        }
        if (isComponentValid) {
            boolean retrieved = false;
            try {
                final PackageManager pm = activity.getPackageManager();
                AsyncTask<Void, Void, Pair<Drawable, CharSequence>> task =
                        new AsyncTask<Void, Void, Pair<Drawable, CharSequence>>() {
                            @Override
                            protected Pair<Drawable, CharSequence> doInBackground(Void... params) {
                                Drawable directShareIcon = null;
                                CharSequence directShareTitle = null;
                                try {
                                    directShareIcon = pm.getActivityIcon(component);
                                    ApplicationInfo ai =
                                            pm.getApplicationInfo(component.getPackageName(), 0);
                                    directShareTitle = pm.getApplicationLabel(ai);
                                } catch (NameNotFoundException exception) {
                                    // Use the default null values.
                                }
                                return new Pair<Drawable, CharSequence>(
                                        directShareIcon, directShareTitle);
                            }
                        };
                task.execute();
                Pair<Drawable, CharSequence> result =
                        task.get(COMPONENT_INFO_READ_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
                directShareIcon = result.first;
                directShareTitle = result.second;
                retrieved = true;
            } catch (InterruptedException ie) {
                // Use the default null values.
            } catch (ExecutionException ee) {
                // Use the default null values.
            } catch (TimeoutException te) {
                // Use the default null values.
            }
            RecordHistogram.recordBooleanHistogram(
                    "Android.IsLastSharedAppInfoRetrieved", retrieved);
        }

        item.setIcon(directShareIcon);
        if (directShareTitle != null) {
            item.setTitle(activity.getString(R.string.accessibility_menu_share_via,
                    directShareTitle));
        }
    }

    /*
     * Stores the component selected for sharing last time share was called.
     *
     * This method is public since it is used in tests to avoid creating share dialog.
     */
    @VisibleForTesting
    public static void setLastShareComponentName(ComponentName component) {
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PACKAGE_NAME_KEY, component.getPackageName());
        editor.putString(CLASS_NAME_KEY, component.getClassName());
        editor.apply();
    }

    @VisibleForTesting
    public static Intent getShareIntent(Activity activity, String title, String text, String url,
            Uri offlineUri, Uri screenshotUri) {
        if (!TextUtils.isEmpty(url)) {
            url = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
            if (!TextUtils.isEmpty(text)) {
                // Concatenate text and URL with a space.
                text = text + " " + url;
            } else {
                text = url;
            }
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(ApiCompatibilityUtils.getActivityNewDocumentFlag());
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.putExtra(EXTRA_TASK_ID, activity.getTaskId());

        if (screenshotUri != null) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        if (screenshotUri != null) {
            // To give read access to an Intent target, we need to put |screenshotUri| in clipData
            // because adding Intent.FLAG_GRANT_READ_URI_PERMISSION doesn't work for
            // EXTRA_SHARE_SCREENSHOT_AS_STREAM.
            intent.setClipData(ClipData.newRawUri("", screenshotUri));
            intent.putExtra(EXTRA_SHARE_SCREENSHOT_AS_STREAM, screenshotUri);
        }
        if (offlineUri == null) {
            intent.setType("text/plain");
        } else {
            intent.setType("multipart/related");
            intent.putExtra(Intent.EXTRA_STREAM, offlineUri);
        }
        return intent;
    }

    private static Intent getShareImageIntent(Uri imageUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(ApiCompatibilityUtils.getActivityNewDocumentFlag());
        intent.setType("image/jpeg");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        return intent;
    }

    private static Intent getDirectShareIntentForComponent(Activity activity, String title,
            String text, String url, Uri offlineUri, Uri screenshotUri, ComponentName component) {
        Intent intent = getShareIntent(activity, title, text, url, offlineUri, screenshotUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        intent.setComponent(component);
        return intent;
    }

    private static ComponentName getLastShareComponentName() {
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        String packageName = preferences.getString(PACKAGE_NAME_KEY, null);
        String className = preferences.getString(CLASS_NAME_KEY, null);
        if (packageName == null || className == null) return null;
        return new ComponentName(packageName, className);
    }
}
