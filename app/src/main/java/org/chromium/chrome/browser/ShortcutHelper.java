// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Base64;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.blink_public.platform.WebDisplayMode;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.webapps.ChromeWebApkHost;
import org.chromium.chrome.browser.webapps.WebappActivity;
import org.chromium.chrome.browser.webapps.WebappAuthenticator;
import org.chromium.chrome.browser.webapps.WebappDataStorage;
import org.chromium.chrome.browser.webapps.WebappLauncherActivity;
import org.chromium.chrome.browser.webapps.WebappRegistry;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.content_public.common.ScreenOrientationConstants;
import org.chromium.ui.widget.Toast;
import org.chromium.webapk.lib.client.WebApkValidator;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * This class contains functions related to adding shortcuts to the Android Home
 * screen.  These shortcuts are used to either open a page in the main browser
 * or open a web app.
 */
public class ShortcutHelper {
    public static final String EXTRA_ICON = "org.chromium.chrome.browser.webapp_icon";
    public static final String EXTRA_ID = "org.chromium.chrome.browser.webapp_id";
    public static final String EXTRA_MAC = "org.chromium.chrome.browser.webapp_mac";
    // EXTRA_TITLE is present for backward compatibility reasons
    public static final String EXTRA_TITLE = "org.chromium.chrome.browser.webapp_title";
    public static final String EXTRA_NAME = "org.chromium.chrome.browser.webapp_name";
    public static final String EXTRA_SHORT_NAME = "org.chromium.chrome.browser.webapp_short_name";
    public static final String EXTRA_URL = "org.chromium.chrome.browser.webapp_url";
    public static final String EXTRA_SCOPE = "org.chromium.chrome.browser.webapp_scope";
    public static final String EXTRA_DISPLAY_MODE =
            "org.chromium.chrome.browser.webapp_display_mode";
    public static final String EXTRA_ORIENTATION = ScreenOrientationConstants.EXTRA_ORIENTATION;
    public static final String EXTRA_SOURCE = "org.chromium.chrome.browser.webapp_source";
    public static final String EXTRA_THEME_COLOR = "org.chromium.chrome.browser.theme_color";
    public static final String EXTRA_BACKGROUND_COLOR =
            "org.chromium.chrome.browser.background_color";
    public static final String EXTRA_IS_ICON_GENERATED =
            "org.chromium.chrome.browser.is_icon_generated";
    public static final String EXTRA_VERSION =
            "org.chromium.chrome.browser.webapp_shortcut_version";
    public static final String REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB =
            "REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB";
    public static final String EXTRA_WEBAPK_PACKAGE_NAME =
            "org.chromium.chrome.browser.webapk_package_name";

    // When a new field is added to the intent, this version should be incremented so that it will
    // be correctly populated into the WebappRegistry/WebappDataStorage.
    public static final int WEBAPP_SHORTCUT_VERSION = 2;

    // This value is equal to kInvalidOrMissingColor in the C++ content::Manifest struct.
    public static final long MANIFEST_COLOR_INVALID_OR_MISSING = ((long) Integer.MAX_VALUE) + 1;

    private static final String TAG = "ShortcutHelper";

    // There is no public string defining this intent so if Home changes the value, we
    // have to update this string.
    private static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

    // The activity class used for launching a WebApk.
    private static final String WEBAPK_MAIN_ACTIVITY = "org.chromium.webapk.shell_apk.MainActivity";

    // These sizes are from the Material spec for icons:
    // https://www.google.com/design/spec/style/icons.html#icons-product-icons
    private static final float MAX_INNER_SIZE_RATIO = 1.25f;
    private static final float ICON_PADDING_RATIO = 2.0f / 44.0f;
    private static final float ICON_CORNER_RADIUS_RATIO = 1.0f / 16.0f;
    private static final float GENERATED_ICON_PADDING_RATIO = 1.0f / 12.0f;
    private static final float GENERATED_ICON_FONT_SIZE_RATIO = 1.0f / 3.0f;

    /** Broadcasts Intents out Android for adding the shortcut. */
    public static class Delegate {
        /**
         * Broadcasts an intent to all interested BroadcastReceivers.
         * @param context The Context to use.
         * @param intent The intent to broadcast.
         */
        public void sendBroadcast(Context context, Intent intent) {
            context.sendBroadcast(intent);
        }

        /**
         * Returns the name of the fullscreen Activity to use when launching shortcuts.
         */
        public String getFullscreenAction() {
            return WebappLauncherActivity.ACTION_START_WEBAPP;
        }
    }

    private static Delegate sDelegate = new Delegate();

    /**
     * Sets the delegate to use.
     */
    @VisibleForTesting
    public static void setDelegateForTests(Delegate delegate) {
        sDelegate = delegate;
    }

    /**
     * Adds home screen shortcut which opens in a {@link WebappActivity}. Creates web app
     * home screen shortcut and registers web app asynchronously. Calls
     * ShortcutHelper::OnWebappDataStored() when done.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static void addWebapp(final String id, final String url, final String scopeUrl,
            final String userTitle, final String name, final String shortName, final String iconUrl,
            final Bitmap icon, final int displayMode, final int orientation, final int source,
            final long themeColor, final long backgroundColor, final long callbackPointer) {
        new AsyncTask<Void, Void, Intent>() {
            @Override
            protected Intent doInBackground(Void... args0) {
                // Encoding {@link icon} as a string and computing the mac are expensive.

                Context context = ContextUtils.getApplicationContext();
                String nonEmptyScopeUrl =
                        TextUtils.isEmpty(scopeUrl) ? getScopeFromUrl(url) : scopeUrl;
                Intent shortcutIntent = createWebappShortcutIntent(id,
                        sDelegate.getFullscreenAction(), url, nonEmptyScopeUrl, name, shortName,
                        icon, WEBAPP_SHORTCUT_VERSION, displayMode, orientation, themeColor,
                        backgroundColor, iconUrl.isEmpty());
                shortcutIntent.putExtra(EXTRA_MAC, getEncodedMac(context, url));
                shortcutIntent.putExtra(EXTRA_SOURCE, source);
                shortcutIntent.setPackage(context.getPackageName());
                return shortcutIntent;
            }
            @Override
            protected void onPostExecute(final Intent resultIntent) {
                Context context = ContextUtils.getApplicationContext();
                sDelegate.sendBroadcast(
                        context, createAddToHomeIntent(userTitle, icon, resultIntent));

                // Store the webapp data so that it is accessible without the intent. Once this
                // process is complete, call back to native code to start the splash image
                // download.
                WebappRegistry.getInstance().register(
                        id, new WebappRegistry.FetchWebappDataStorageCallback() {
                            @Override
                            public void onWebappDataStorageRetrieved(WebappDataStorage storage) {
                                storage.updateFromShortcutIntent(resultIntent);
                                nativeOnWebappDataStored(callbackPointer);
                            }
                        });

                showAddedToHomescreenToast(userTitle);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void addWebApkShortcut(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(
                    packageName, PackageManager.GET_META_DATA);
            String shortcutTitle = pm.getApplicationLabel(appInfo).toString();
            Bitmap shortcutIcon = ((BitmapDrawable) pm.getApplicationIcon(packageName)).getBitmap();

            Bitmap bitmap = createHomeScreenIconFromWebIcon(shortcutIcon);
            Intent i = new Intent();
            i.setClassName(packageName, WEBAPK_MAIN_ACTIVITY);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            context.sendBroadcast(createAddToHomeIntent(shortcutTitle, bitmap, i));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds home screen shortcut which opens in the browser Activity.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static void addShortcut(String url, String userTitle, Bitmap icon, int source) {
        Context context = ContextUtils.getApplicationContext();
        final Intent shortcutIntent = createShortcutIntent(url);
        shortcutIntent.putExtra(EXTRA_SOURCE, source);
        shortcutIntent.setPackage(context.getPackageName());
        sDelegate.sendBroadcast(
                context, createAddToHomeIntent(userTitle, icon, shortcutIntent));
        showAddedToHomescreenToast(userTitle);
    }

    /**
     * Show toast to alert user that the shortcut was added to the home screen.
     */
    private static void showAddedToHomescreenToast(final String title) {
        assert ThreadUtils.runningOnUiThread();

        Context applicationContext = ContextUtils.getApplicationContext();
        String toastText = applicationContext.getString(R.string.added_to_homescreen, title);
        Toast toast = Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * Stores the specified bitmap as the splash screen for a web app.
     * @param id          ID of the web app which is storing data.
     * @param splashImage Image which should be displayed on the splash screen of
     *                    the web app. This can be null of there is no image to show.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static void storeWebappSplashImage(final String id, final Bitmap splashImage) {
        final WebappDataStorage storage = WebappRegistry.getInstance().getWebappDataStorage(id);
        if (storage != null) {
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... args0) {
                    return encodeBitmapAsString(splashImage);
                }

                @Override
                protected void onPostExecute(String encodedImage) {
                    storage.updateSplashScreenImage(encodedImage);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Creates an intent that will add a shortcut to the home screen.
     * @param title Title of the shortcut.
     * @param icon Image that represents the shortcut.
     * @param shortcutIntent Intent to fire when the shortcut is activated.
     * @return Intent for the shortcut.
     */
    public static Intent createAddToHomeIntent(String title, Bitmap icon,
            Intent shortcutIntent) {
        Intent i = new Intent(INSTALL_SHORTCUT);
        i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        i.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        i.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        return i;
    }

    /**
     * Creates a shortcut to launch a web app on the home screen.
     * @param id              Id of the web app.
     * @param action          Intent action to open a full screen activity.
     * @param url             Url of the web app.
     * @param scope           Url scope of the web app.
     * @param name            Name of the web app.
     * @param shortName       Short name of the web app.
     * @param icon            Icon of the web app.
     * @param version         Version number of the shortcut.
     * @param displayMode     Display mode of the web app.
     * @param orientation     Orientation of the web app.
     * @param themeColor      Theme color of the web app.
     * @param backgroundColor Background color of the web app.
     * @param isIconGenerated True if the icon is generated by Chromium.
     * @return Intent for onclick action of the shortcut.
     * This method must not be called on the UI thread.
     */
    public static Intent createWebappShortcutIntent(String id, String action, String url,
            String scope, String name, String shortName, Bitmap icon, int version, int displayMode,
            int orientation, long themeColor, long backgroundColor, boolean isIconGenerated) {
        assert !ThreadUtils.runningOnUiThread();

        // Encode the icon as a base64 string (Launcher drops Bitmaps in the Intent).
        String encodedIcon = encodeBitmapAsString(icon);

        // Create an intent as a launcher icon for a full-screen Activity.
        Intent shortcutIntent = new Intent();
        shortcutIntent.setAction(action)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SCOPE, scope)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_SHORT_NAME, shortName)
                .putExtra(EXTRA_ICON, encodedIcon)
                .putExtra(EXTRA_VERSION, version)
                .putExtra(EXTRA_DISPLAY_MODE, displayMode)
                .putExtra(EXTRA_ORIENTATION, orientation)
                .putExtra(EXTRA_THEME_COLOR, themeColor)
                .putExtra(EXTRA_BACKGROUND_COLOR, backgroundColor)
                .putExtra(EXTRA_IS_ICON_GENERATED, isIconGenerated);
        return shortcutIntent;
    }

    /**
     * Creates an intent with mostly empty parameters for launching a web app on the homescreen.
     * @param id              Id of the web app.
     * @param url             Url of the web app.
     * @return the Intent
     * This method must not be called on the UI thread.
     */
    public static Intent createWebappShortcutIntentForTesting(String id, String url) {
        assert !ThreadUtils.runningOnUiThread();
        return createWebappShortcutIntent(id, null, url, getScopeFromUrl(url), null, null, null,
                WEBAPP_SHORTCUT_VERSION, WebDisplayMode.Standalone, 0, 0, 0, false);
    }

    /**
     * Shortcut intent for icon on home screen.
     * @param url Url of the shortcut.
     * @return Intent for onclick action of the shortcut.
     */
    public static Intent createShortcutIntent(String url) {
        Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        shortcutIntent.putExtra(REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB, true);
        return shortcutIntent;
    }

    /**
     * Utility method to check if a shortcut can be added to the home screen.
     * @param context Context used to get the package manager.
     * @return if a shortcut can be added to the home screen under the current profile.
     */
    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("WrongConstant")
    public static boolean isAddToHomeIntentSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent i = new Intent(INSTALL_SHORTCUT);
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(
                i, PackageManager.GET_INTENT_FILTERS);
        return !receivers.isEmpty();
    }

    /**
     * Returns whether the given icon matches the size requirements to be used on the home screen.
     * @param width Icon width, in pixels.
     * @param height Icon height, in pixels.
     * @return whether the given icon matches the size requirements to be used on the home screen.
     */
    @CalledByNative
    public static boolean isIconLargeEnoughForLauncher(int width, int height) {
        Context context = ContextUtils.getApplicationContext();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final int minimalSize = am.getLauncherLargeIconSize() / 2;
        return width >= minimalSize && height >= minimalSize;
    }

    /**
     * Adapts a website's icon (e.g. favicon or touch icon) to make it suitable for the home screen.
     * This involves adding padding if the icon is a full sized square.
     *
     * @param context Context used to create the intent.
     * @param webIcon The website's favicon or touch icon.
     * @return Bitmap Either the touch-icon or the newly created favicon.
     */
    @CalledByNative
    public static Bitmap createHomeScreenIconFromWebIcon(Bitmap webIcon) {
        // getLauncherLargeIconSize() is just a guess at the launcher icon size, and is often
        // wrong -- the launcher can show icons at any size it pleases. Instead of resizing the
        // icon to the supposed launcher size and then having the launcher resize the icon again,
        // just leave the icon at its original size and let the launcher do a single rescaling.
        // Unless the icon is much too big; then scale it down here too.
        Context context = ContextUtils.getApplicationContext();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int maxInnerSize = Math.round(am.getLauncherLargeIconSize() * MAX_INNER_SIZE_RATIO);
        int innerSize = Math.min(maxInnerSize, Math.max(webIcon.getWidth(), webIcon.getHeight()));
        int padding = Math.round(ICON_PADDING_RATIO * innerSize);
        int outerSize = innerSize + 2 * padding;

        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(outerSize, outerSize, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "OutOfMemoryError while creating bitmap for home screen icon.");
            return webIcon;
        }

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        Rect innerBounds;

        // Draw the icon with padding around it if all four corners are not transparent. Otherwise,
        // don't add padding.
        if (shouldPadIcon(webIcon)) {
            innerBounds = new Rect(padding, padding, outerSize - padding, outerSize - padding);
        } else {
            innerBounds = new Rect(0, 0, outerSize, outerSize);
        }
        canvas.drawBitmap(webIcon, null, innerBounds, paint);

        return bitmap;
    }

    /**
     * Generates a generic icon to be used in the launcher. This is just a rounded rectangle with
     * a letter in the middle taken from the website's domain name.
     *
     * @param url URL of the shortcut.
     * @param red Red component of the dominant icon color.
     * @param green Green component of the dominant icon color.
     * @param blue Blue component of the dominant icon color.
     * @return Bitmap Either the touch-icon or the newly created favicon.
     */
    @CalledByNative
    public static Bitmap generateHomeScreenIcon(String url, int red, int green, int blue) {
        Context context = ContextUtils.getApplicationContext();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final int outerSize = am.getLauncherLargeIconSize();
        final int iconDensity = am.getLauncherLargeIconDensity();

        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(outerSize, outerSize, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "OutOfMemoryError while trying to draw bitmap on canvas.");
            return null;
        }

        Canvas canvas = new Canvas(bitmap);

        // Draw the drop shadow.
        int padding = (int) (GENERATED_ICON_PADDING_RATIO * outerSize);
        Rect outerBounds = new Rect(0, 0, outerSize, outerSize);
        Bitmap iconShadow =
                getBitmapFromResourceId(context, R.mipmap.shortcut_icon_shadow, iconDensity);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(iconShadow, null, outerBounds, paint);

        // Draw the rounded rectangle and letter.
        int innerSize = outerSize - 2 * padding;
        int cornerRadius = Math.round(ICON_CORNER_RADIUS_RATIO * outerSize);
        int fontSize = Math.round(GENERATED_ICON_FONT_SIZE_RATIO * outerSize);
        int color = Color.rgb(red, green, blue);
        RoundedIconGenerator generator = new RoundedIconGenerator(
                innerSize, innerSize, cornerRadius, color, fontSize);
        Bitmap icon = generator.generateIconForUrl(url);
        if (icon == null) return null; // Bookmark URL does not have a domain.
        canvas.drawBitmap(icon, padding, padding, null);

        return bitmap;
    }

    /**
     * Returns the package name of the WebAPK if WebAPKs are enabled and there is an installed
     * WebAPK which can handle {@link url}. Returns null otherwise.
     */
    @CalledByNative
    private static String queryWebApkPackage(String url) {
        if (!ChromeWebApkHost.isEnabled()) return null;
        return WebApkValidator.queryWebApkPackage(ContextUtils.getApplicationContext(), url);
    }

    /**
     * Compresses a bitmap into a PNG and converts into a Base64 encoded string.
     * The encoded string can be decoded using {@link decodeBitmapFromString(String)}.
     * @param bitmap The Bitmap to compress and encode.
     * @return the String encoding the Bitmap.
     */
    public static String encodeBitmapAsString(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        return Base64.encodeToString(output.toByteArray(), Base64.DEFAULT);
    }

    /**
     * Decodes a Base64 string into a Bitmap. Used to decode Bitmaps encoded by
     * {@link encodeBitmapAsString(Bitmap)}.
     * @param encodedString the Base64 String to decode.
     * @return the Bitmap which was encoded by the String.
     */
    public static Bitmap decodeBitmapFromString(String encodedString) {
        if (TextUtils.isEmpty(encodedString)) return null;
        byte[] decoded = Base64.decode(encodedString, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
    }

    /**
     * Returns the ideal size for an icon representing a web app.  This size is used on app banners,
     * the Android Home screen, and in Android's recent tasks list, among other places.
     * @param resources Resources to retrieve the dimension from.
     * @return the dimensions in dp which the icon should have.
     */
    public static int getIdealHomescreenIconSizeInDp(Context context) {
        return getIdealSizeFromResourceInDp(context, R.dimen.webapp_home_screen_icon_size);
    }

    /**
     * Returns the minimum size for an icon representing a web app.  This size is used on app
     * banners, the Android Home screen, and in Android's recent tasks list, among other places.
     * @param resources Resources to retrieve the dimension from.
     * @return the lower bound of the size which the icon should have in dp.
     */
    public static int getMinimumHomescreenIconSizeInDp(Context context) {
        float sizeInPx = context.getResources().getDimension(R.dimen.webapp_home_screen_icon_size);
        float density = context.getResources().getDisplayMetrics().density;
        float idealIconSizeInDp = sizeInPx / density;

        float minimumIconSizeInPx = idealIconSizeInDp * (density - 1);
        return Math.round(minimumIconSizeInPx / density);
    }

    /**
     * Returns the ideal size for an image displayed on a web app's splash screen.
     * @param resources Resources to retrieve the dimension from.
     * @return the dimensions in dp which the image should have.
     */
    public static int getIdealSplashImageSizeInDp(Context context) {
        return getIdealSizeFromResourceInDp(context, R.dimen.webapp_splash_image_size_ideal);
    }

    /**
     * Returns the minimum size for an image displayed on a web app's splash screen.
     * @param resources Resources to retrieve the dimension from.
     * @return the lower bound of the size which the image should have in dp.
     */
    public static int getMinimumSplashImageSizeInDp(Context context) {
        return getIdealSizeFromResourceInDp(context, R.dimen.webapp_splash_image_size_minimum);
    }

    /**
     * @return String that can be used to verify that a WebappActivity is being started by Chrome.
     */
    public static String getEncodedMac(Context context, String url) {
        // The only reason we convert to a String here is because Android inexplicably eats a
        // byte[] when adding the shortcut -- the Bundle received by the launched Activity even
        // lacks the key for the extra.
        byte[] mac = WebappAuthenticator.getMacForUrl(context, url);
        return Base64.encodeToString(mac, Base64.DEFAULT);
    }

    /**
     * Generates a scope URL based on the passed in URL. It should be used if the Web Manifest
     * does not specify a scope URL.
     * @param url The url to convert to a scope.
     * @return The scope.
     */
    @CalledByNative
    public static String getScopeFromUrl(String url) {
        // Scope URL is generated by:
        // - Removing last component of the URL.
        // - Clearing the URL's query and fragment.

        Uri uri = Uri.parse(url);
        List<String> path = uri.getPathSegments();
        int endIndex = path.size();

        // If there is at least one path element, remove the last one.
        if (endIndex > 0) {
            endIndex -= 1;
        }

        // Make sure the path starts and ends with a slash (or is only a slash if there is no path).
        Uri.Builder builder = uri.buildUpon();
        String scope_path = "/" + TextUtils.join("/", path.subList(0, endIndex));
        if (scope_path.length() > 1) {
            scope_path += "/";
        }
        builder.path(scope_path);

        builder.fragment("");
        builder.query("");
        return builder.build().toString();
    }

    /**
     * Returns an array of sizes which describe the ideal size and minimum size of the Home screen
     * icon and the ideal and minimum sizes of the splash screen image in that order.
     */
    @CalledByNative
    private static int[] getHomeScreenIconAndSplashImageSizes() {
        Context context = ContextUtils.getApplicationContext();
        // This ordering must be kept up to date with the C++ ShortcutHelper.
        return new int[] {
            getIdealHomescreenIconSizeInDp(context),
            getMinimumHomescreenIconSizeInDp(context),
            getIdealSplashImageSizeInDp(context),
            getMinimumSplashImageSizeInDp(context)
        };
    }

    /**
     * Returns true if we should add padding to this icon. We use a heuristic that if the pixels in
     * all four corners of the icon are not transparent, we assume the icon is square and maximally
     * sized, i.e. in need of padding. Otherwise, no padding is added.
     */
    private static boolean shouldPadIcon(Bitmap icon) {
        int maxX = icon.getWidth() - 1;
        int maxY = icon.getHeight() - 1;

        if ((Color.alpha(icon.getPixel(0, 0)) != 0) && (Color.alpha(icon.getPixel(maxX, maxY)) != 0)
                && (Color.alpha(icon.getPixel(0, maxY)) != 0)
                && (Color.alpha(icon.getPixel(maxX, 0)) != 0)) {
            return true;
        }
        return false;
    }

    private static int getIdealSizeFromResourceInDp(Context context, int resource) {
        float sizeInPx = context.getResources().getDimension(resource);
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(sizeInPx / density);
    }

    private static Bitmap getBitmapFromResourceId(Context context, int id, int density) {
        Drawable drawable = ApiCompatibilityUtils.getDrawableForDensity(
                context.getResources(), id, density);

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) drawable;
            return bd.getBitmap();
        }
        assert false : "The drawable was not a bitmap drawable as expected";
        return null;
    }

    private static native void nativeOnWebappDataStored(long callbackPointer);
}
