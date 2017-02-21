// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Looper;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeVersionInfo;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps tabs on the current state of Chrome, tracking if and when a request should be sent to the
 * Omaha Server.
 *
 * A hook in ChromeActivity's doDeferredResume() initializes the service.  Further attempts to
 * reschedule events will be scheduled by the class itself.
 *
 * Each request to the server will perform an update check and ping the server.
 * We use a repeating alarm to schedule the XML requests to be generated 5 hours apart.
 * If Chrome isn't running when the alarm is fired, the request generation will be stalled until
 * the next time Chrome runs.
 *
 * mevissen suggested being conservative with our timers for sending requests.
 * POST attempts that fail to be acknowledged by the server are re-attempted, with at least
 * one hour between each attempt.
 *
 * Status is saved directly to the the disk after every operation.  Unit tests testing the code
 * paths without using Intents may need to call restoreState() manually as it is not automatically
 * handled in onCreate().
 *
 * Implementation notes:
 * http://docs.google.com/a/google.com/document/d/1scTCovqASf5ktkOeVj8wFRkWTCeDYw2LrOBNn05CDB0/edit
 */
public class OmahaClient extends IntentService {
    private static final String TAG = "omaha";

    // Intent actions.
    private static final String ACTION_INITIALIZE =
            "org.chromium.chrome.browser.omaha.ACTION_INITIALIZE";
    private static final String ACTION_REGISTER_REQUEST =
            "org.chromium.chrome.browser.omaha.ACTION_REGISTER_REQUEST";
    private static final String ACTION_POST_REQUEST =
            "org.chromium.chrome.browser.omaha.ACTION_POST_REQUEST";

    // Delays between events.
    private static final long MS_PER_HOUR = 3600000;
    private static final long MS_POST_BASE_DELAY = MS_PER_HOUR;
    private static final long MS_POST_MAX_DELAY = 5 * MS_PER_HOUR;
    private static final long MS_BETWEEN_REQUESTS = 5 * MS_PER_HOUR;
    private static final int MS_CONNECTION_TIMEOUT = 60000;

    // Flags for retrieving the OmahaClient's state after it's written to disk.
    // The PREF_PACKAGE doesn't match the current OmahaClient package for historical reasons.
    @VisibleForTesting
    static final String PREF_PACKAGE = "com.google.android.apps.chrome.omaha";
    @VisibleForTesting
    static final String PREF_PERSISTED_REQUEST_ID = "persistedRequestID";
    @VisibleForTesting
    static final String PREF_TIMESTAMP_OF_REQUEST = "timestampOfRequest";
    @VisibleForTesting
    static final String PREF_INSTALL_SOURCE = "installSource";
    private static final String PREF_SEND_INSTALL_EVENT = "sendInstallEvent";
    private static final String PREF_TIMESTAMP_OF_INSTALL = "timestampOfInstall";
    private static final String PREF_TIMESTAMP_FOR_NEXT_POST_ATTEMPT =
            "timestampForNextPostAttempt";
    private static final String PREF_TIMESTAMP_FOR_NEW_REQUEST = "timestampForNewRequest";

    // Strings indicating how the Chrome APK arrived on the user's device. These values MUST NOT
    // be changed without updating the corresponding Omaha server strings.
    static final String INSTALL_SOURCE_SYSTEM = "system_image";
    static final String INSTALL_SOURCE_ORGANIC = "organic";

    // Lock object used to synchronize all calls that modify or read sIsFreshInstallOrDataCleared.
    private static final Object sIsFreshInstallLock = new Object();

    @VisibleForTesting
    static final String PREF_LATEST_VERSION = "latestVersion";
    @VisibleForTesting
    static final String PREF_MARKET_URL = "marketURL";

    private static final long INVALID_TIMESTAMP = -1;
    @VisibleForTesting
    static final String INVALID_REQUEST_ID = "invalid";

    // Static fields
    private static boolean sEnableCommunication = true;
    private static boolean sEnableUpdateDetection = true;
    private static VersionNumberGetter sVersionNumberGetter = null;
    private static MarketURLGetter sMarketURLGetter = null;
    private static Boolean sIsFreshInstallOrDataCleared = null;

    // Member fields not persisted to disk.
    private boolean mStateHasBeenRestored;
    private ExponentialBackoffScheduler mBackoffScheduler;
    private RequestGenerator mGenerator;

    // State saved written to and read from disk.
    private RequestData mCurrentRequest;
    private long mTimestampOfInstall;
    private long mTimestampForNextPostAttempt;
    private long mTimestampForNewRequest;
    private String mLatestVersion;
    private String mMarketURL;
    private String mInstallSource;
    protected boolean mSendInstallEvent;

    public OmahaClient() {
        super(TAG);
        setIntentRedelivery(true);
    }

    /**
     * Sets whether Chrome should be communicating with the Omaha server.
     * The alternative to using a static field within OmahaClient is using a member variable in
     * the ChromeTabbedActivity.  The problem is that it is difficult to set the variable before
     * ChromeTabbedActivity is started.
     */
    @VisibleForTesting
    public static void setEnableCommunication(boolean state) {
        sEnableCommunication = state;
    }

    /**
     * If false, OmahaClient will never report that a newer version is available.
     */
    @VisibleForTesting
    public static void setEnableUpdateDetection(boolean state) {
        sEnableUpdateDetection = state;
    }

    @VisibleForTesting
    long getTimestampForNextPostAttempt() {
        return mTimestampForNextPostAttempt;
    }

    @VisibleForTesting
    long getTimestampForNewRequest() {
        return mTimestampForNewRequest;
    }

    @VisibleForTesting
    int getCumulativeFailedAttempts() {
        return getBackoffScheduler().getNumFailedAttempts();
    }

    /**
     * Creates the scheduler used to space out POST attempts.
     */
    @VisibleForTesting
    ExponentialBackoffScheduler createBackoffScheduler(String prefPackage, Context context,
            long base, long max) {
        return new ExponentialBackoffScheduler(prefPackage, context, base, max);
    }

    /**
     * Creates the request generator used to create Omaha XML.
     */
    @VisibleForTesting
    RequestGenerator createRequestGenerator(Context context) {
        return ((ChromeApplication) context.getApplicationContext()).createOmahaRequestGenerator();
    }

    /**
     * Handles an action on a thread separate from the UI thread.
     * @param intent Intent fired by some part of Chrome.
     */
    @Override
    public void onHandleIntent(Intent intent) {
        assert Looper.myLooper() != Looper.getMainLooper();

        if (!sEnableCommunication) {
            Log.v(TAG, "Disabled.  Ignoring intent.");
            return;
        }

        if (getRequestGenerator() == null) {
            return;
        }

        if (!mStateHasBeenRestored) {
            restoreState();
        }

        if (ACTION_INITIALIZE.equals(intent.getAction())) {
            handleInitialize();
        } else if (ACTION_REGISTER_REQUEST.equals(intent.getAction())) {
            handleRegisterRequest(intent);
        } else if (ACTION_POST_REQUEST.equals(intent.getAction())) {
            handlePostRequestIntent(intent);
        } else {
            Log.e(TAG, "Got unknown action from intent: " + intent.getAction());
        }
    }

    /**
     * Begin communicating with the Omaha Update Server.
     */
    public static void onForegroundSessionStart(Context context) {
        if (!ChromeVersionInfo.isOfficialBuild()) return;
        Intent omahaIntent = createInitializeIntent(context);
        context.startService(omahaIntent);
    }

    static Intent createInitializeIntent(Context context) {
        Intent intent = new Intent(context, OmahaClient.class);
        intent.setAction(ACTION_INITIALIZE);
        return intent;
    }

    /**
     * Start a recurring alarm to fire request generation intents.
     */
    private void handleInitialize() {
        scheduleRepeatingAlarm();

        // If a request exists, fire a POST intent to restart its timer.
        if (hasRequest()) startService(createPostRequestIntent(this));
    }

    /**
     * Returns an Intent for registering a new request to send to the server.
     */
    static Intent createRegisterRequestIntent(Context context) {
        Intent intent = new Intent(context, OmahaClient.class);
        intent.setAction(ACTION_REGISTER_REQUEST);
        return intent;
    }

    /**
     * Determines if a new request should be generated.  New requests are only generated if enough
     * time has passed between now and the last time a request was generated.
     */
    private void handleRegisterRequest(Intent intent) {
        if (!isChromeBeingUsed()) {
            cancelRepeatingAlarm();
            return;
        }

        // If the current request is too old, generate a new one.
        long currentTimestamp = getBackoffScheduler().getCurrentTime();
        boolean isTooOld = hasRequest()
                && mCurrentRequest.getAgeInMilliseconds(currentTimestamp) >= MS_BETWEEN_REQUESTS;
        boolean isOverdue = !hasRequest() && currentTimestamp >= mTimestampForNewRequest;
        if (isTooOld || isOverdue) {
            registerNewRequest(currentTimestamp);
        }

        // Create an intent to send the request.
        if (hasRequest()) {
            startService(createPostRequestIntent(this));
        }
    }

    /**
     * Returns an Intent for POSTing the current request to the Omaha server.
     */
    static Intent createPostRequestIntent(Context context) {
        Intent intent = new Intent(context, OmahaClient.class);
        intent.setAction(ACTION_POST_REQUEST);
        return intent;
    }

    /**
     * Sends the request it is holding.
     */
    @VisibleForTesting
    private void handlePostRequestIntent(Intent intent) {
        if (!hasRequest()) {
            return;
        }

        // If enough time has passed since the last attempt, try sending a request.
        long currentTimestamp = getBackoffScheduler().getCurrentTime();
        if (currentTimestamp >= mTimestampForNextPostAttempt) {
            // All requests made during the same session should have the same ID.
            String sessionID = generateRandomUUID();
            boolean sendingInstallRequest = mSendInstallEvent;
            boolean succeeded = generateAndPostRequest(currentTimestamp, sessionID);

            if (succeeded && sendingInstallRequest) {
                // Only the first request ever generated should contain an install event.
                mSendInstallEvent = false;

                // Create and immediately send another request for a ping and update check.
                registerNewRequest(currentTimestamp);
                generateAndPostRequest(currentTimestamp, sessionID);
            }
        } else {
            // Set an alarm to POST at the proper time.  Previous alarms are destroyed.
            Intent postIntent = createPostRequestIntent(this);
            getBackoffScheduler().createAlarm(postIntent, mTimestampForNextPostAttempt);
        }

        // Write everything back out again to save our state.
        saveState();
    }

    private boolean generateAndPostRequest(long currentTimestamp, String sessionID) {
        ExponentialBackoffScheduler scheduler = getBackoffScheduler();
        try {
            // Generate the XML for the current request.
            long installAgeInDays = RequestGenerator.installAge(currentTimestamp,
                    mTimestampOfInstall, mCurrentRequest.isSendInstallEvent());
            String version = getVersionNumberGetter().getCurrentlyUsedVersion(this);
            String xml = getRequestGenerator().generateXML(
                    sessionID, version, installAgeInDays, mCurrentRequest);

            // Send the request to the server & wait for a response.
            String response = postRequest(currentTimestamp, xml);
            parseServerResponse(response);

            // If we've gotten this far, we've successfully sent a request.
            mCurrentRequest = null;
            mTimestampForNextPostAttempt = currentTimestamp + MS_POST_BASE_DELAY;
            scheduler.resetFailedAttempts();
            Log.i(TAG, "Request to Server Successful. Timestamp for next request:"
                    + String.valueOf(mTimestampForNextPostAttempt));

            return true;
        } catch (RequestFailureException e) {
            // Set the alarm to try again later.
            Log.e(TAG, "Failed to contact server: ", e);
            Intent postIntent = createPostRequestIntent(this);
            mTimestampForNextPostAttempt = scheduler.createAlarm(postIntent);
            scheduler.increaseFailedAttempts();
            return false;
        }
    }

    /**
     * Sets a repeating alarm that fires request registration Intents.
     * Setting the alarm overwrites whatever alarm is already there, and rebooting
     * clears whatever alarms are currently set.
     */
    private void scheduleRepeatingAlarm() {
        Intent registerIntent = createRegisterRequestIntent(this);
        PendingIntent pIntent = PendingIntent.getService(this, 0, registerIntent, 0);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        setAlarm(am, pIntent, AlarmManager.RTC, mTimestampForNewRequest);
    }

    /**
     * Sets up a timer to fire after each interval.
     * Override to prevent a real alarm from being set.
     */
    @VisibleForTesting
    protected void setAlarm(AlarmManager am, PendingIntent operation, int alarmType,
            long triggerAtTime) {
        try {
            am.setRepeating(AlarmManager.RTC, triggerAtTime, MS_BETWEEN_REQUESTS, operation);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to set repeating alarm.");
        }
    }

    /**
     * Cancels the alarm that launches this service.  It will be replaced when Chrome next resumes.
     */
    private void cancelRepeatingAlarm() {
        Intent requestIntent = createRegisterRequestIntent(this);
        PendingIntent pendingIntent =
                PendingIntent.getService(this, 0, requestIntent, PendingIntent.FLAG_NO_CREATE);
        // Setting FLAG_NO_CREATE forces Android to return an already existing PendingIntent.
        // Here it would be the one that was used to create the existing alarm (if it exists).
        // If the pendingIntent is null, it is likely that no alarm was created.
        if (pendingIntent != null) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    /**
     * Determine whether or not Chrome is currently being used actively.
     */
    @VisibleForTesting
    protected boolean isChromeBeingUsed() {
        boolean isChromeVisible = ApplicationStatus.hasVisibleActivities();
        boolean isScreenOn = ApiCompatibilityUtils.isInteractive(this);
        return isChromeVisible && isScreenOn;
    }

    /**
     * Registers a new request with the current timestamp.  Internal timestamps are reset to start
     * fresh.
     * @param currentTimestamp Current time.
     */
    @VisibleForTesting
    void registerNewRequest(long currentTimestamp) {
        mCurrentRequest = createRequestData(currentTimestamp, null);
        getBackoffScheduler().resetFailedAttempts();
        mTimestampForNextPostAttempt = currentTimestamp;

        // Tentatively set the timestamp for a new request.  This will be updated when the server
        // is successfully contacted.
        mTimestampForNewRequest = currentTimestamp + MS_BETWEEN_REQUESTS;
        scheduleRepeatingAlarm();

        saveState();
    }

    private RequestData createRequestData(long currentTimestamp, String persistedID) {
        // If we're sending a persisted event, keep trying to send the same request ID.
        String requestID;
        if (persistedID == null || INVALID_REQUEST_ID.equals(persistedID)) {
            requestID = generateRandomUUID();
        } else {
            requestID = persistedID;
        }
        return new RequestData(mSendInstallEvent, currentTimestamp, requestID, mInstallSource);
    }

    @VisibleForTesting
    boolean hasRequest() {
        return mCurrentRequest != null;
    }

    /**
     * Posts the request to the Omaha server.
     * @return the XML response as a String.
     * @throws RequestFailureException if the request fails.
     */
    @VisibleForTesting
    String postRequest(long timestamp, String xml) throws RequestFailureException {
        String response = null;

        HttpURLConnection urlConnection = null;
        try {
            urlConnection = createConnection();
            setUpPostRequest(timestamp, urlConnection, xml);
            sendRequestToServer(urlConnection, xml);
            response = readResponseFromServer(urlConnection);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return response;
    }

    /**
     * Parse the server's response and confirm that we received an OK response.
     */
    private void parseServerResponse(String response) throws RequestFailureException {
        String appId = getRequestGenerator().getAppId();
        boolean sentPingAndUpdate = !mSendInstallEvent;
        ResponseParser parser =
                new ResponseParser(appId, mSendInstallEvent, sentPingAndUpdate, sentPingAndUpdate);
        parser.parseResponse(response);
        mTimestampForNewRequest = getBackoffScheduler().getCurrentTime() + MS_BETWEEN_REQUESTS;
        mLatestVersion = parser.getNewVersion();
        mMarketURL = parser.getURL();
        scheduleRepeatingAlarm();
    }

    /**
     * Returns a HttpURLConnection to the server.
     */
    @VisibleForTesting
    protected HttpURLConnection createConnection() throws RequestFailureException {
        try {
            URL url = new URL(getRequestGenerator().getServerUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(MS_CONNECTION_TIMEOUT);
            connection.setReadTimeout(MS_CONNECTION_TIMEOUT);
            return connection;
        } catch (MalformedURLException e) {
            throw new RequestFailureException("Caught a malformed URL exception.", e);
        } catch (IOException e) {
            throw new RequestFailureException("Failed to open connection to URL", e);
        }
    }

    /**
     * Prepares the HTTP header.
     */
    private void setUpPostRequest(long timestamp, HttpURLConnection urlConnection, String xml)
            throws RequestFailureException {
        try {
            urlConnection.setDoOutput(true);
            urlConnection.setFixedLengthStreamingMode(xml.getBytes().length);
            if (mSendInstallEvent && getCumulativeFailedAttempts() > 0) {
                String age = Long.toString(mCurrentRequest.getAgeInSeconds(timestamp));
                urlConnection.addRequestProperty("X-RequestAge", age);
            }
        } catch (IllegalAccessError e) {
            throw new RequestFailureException("Caught an IllegalAccessError:", e);
        } catch (IllegalArgumentException e) {
            throw new RequestFailureException("Caught an IllegalArgumentException:", e);
        } catch (IllegalStateException e) {
            throw new RequestFailureException("Caught an IllegalStateException:", e);
        }
    }

    /**
     * Sends the request to the server.
     */
    private void sendRequestToServer(HttpURLConnection urlConnection, String xml)
            throws RequestFailureException {
        try {
            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            OutputStreamWriter writer = new OutputStreamWriter(out);
            writer.write(xml, 0, xml.length());
            writer.close();
            checkServerResponseCode(urlConnection);
        } catch (IOException e) {
            throw new RequestFailureException("Failed to write request to server: ", e);
        }
    }

    /**
     * Reads the response from the Omaha Server.
     */
    private String readResponseFromServer(HttpURLConnection urlConnection)
            throws RequestFailureException {
        try {
            InputStreamReader reader = new InputStreamReader(urlConnection.getInputStream());
            BufferedReader in = new BufferedReader(reader);
            try {
                StringBuilder response = new StringBuilder();
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    response.append(line);
                }
                checkServerResponseCode(urlConnection);
                return response.toString();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new RequestFailureException("Failed when reading response from server: ", e);
        }
    }

    /**
     * Confirms that the Omaha server sent back an "OK" code.
     */
    private void checkServerResponseCode(HttpURLConnection urlConnection)
            throws RequestFailureException {
        try {
            if (urlConnection.getResponseCode() != 200) {
                throw new RequestFailureException(
                        "Received " + urlConnection.getResponseCode()
                        + " code instead of 200 (OK) from the server.  Aborting.");
            }
        } catch (IOException e) {
            throw new RequestFailureException("Failed to read response code from server: ", e);
        }
    }

    /**
     * Checks if we know about a newer version available than the one we're using.  This does not
     * actually fire any requests over to the server; it just checks the version we stored the last
     * time we talked to the Omaha server.
     *
     * NOTE: This function incurs I/O, so don't use it on the main thread.
     */
    static boolean isNewerVersionAvailable(Context context) {
        assert Looper.myLooper() != Looper.getMainLooper();

        // This may be explicitly enabled for some channels and for unit tests.
        if (!sEnableUpdateDetection) {
            return false;
        }

        // If the market link is bad, don't show an update to avoid frustrating users trying to
        // hit the "Update" button.
        if ("".equals(getMarketURL(context))) {
            return false;
        }

        // Compare version numbers.
        VersionNumberGetter getter = getVersionNumberGetter();
        String currentStr = getter.getCurrentlyUsedVersion(context);
        String latestStr = getter.getLatestKnownVersion(context, PREF_PACKAGE, PREF_LATEST_VERSION);

        VersionNumber currentVersionNumber = VersionNumber.fromString(currentStr);
        VersionNumber latestVersionNumber = VersionNumber.fromString(latestStr);

        if (currentVersionNumber == null || latestVersionNumber == null) {
            return false;
        }

        return currentVersionNumber.isSmallerThan(latestVersionNumber);
    }

    /**
     * Retrieves the latest version we know about from disk.
     * This function incurs I/O, so make sure you don't use it from the main thread.
     *
     * @return A string representing the latest version.
     */
    static String getLatestVersionNumberString(Context context) {
        assert Looper.myLooper() != Looper.getMainLooper();
        VersionNumberGetter getter = getVersionNumberGetter();
        return getter.getLatestKnownVersion(context, PREF_PACKAGE, PREF_LATEST_VERSION);
    }

    /**
     * Determine how the Chrome APK arrived on the device.
     * @param context Context to pull resources from.
     * @return A String indicating the install source.
     */
    String determineInstallSource() {
        boolean isInSystemImage = (getApplicationFlags() & ApplicationInfo.FLAG_SYSTEM) != 0;
        return isInSystemImage ? INSTALL_SOURCE_SYSTEM : INSTALL_SOURCE_ORGANIC;
    }

    /**
     * Returns the Application's flags, used to determine if Chrome was installed as part of the
     * system image.
     * @return The Application's flags.
     */
    @VisibleForTesting
    int getApplicationFlags() {
        return getApplicationInfo().flags;
    }

    /**
     * Reads the data back from the file it was saved to.  Uses SharedPreferences to handle I/O.
     * Sanity checks are performed on the timestamps to guard against clock changing.
     */
    @VisibleForTesting
    void restoreState() {
        boolean mustRewriteState = false;
        SharedPreferences preferences = getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
        Map<String, ?> items = preferences.getAll();

        // Read out the recorded data.
        long currentTime = getBackoffScheduler().getCurrentTime();
        mTimestampForNewRequest =
                getLongFromMap(items, PREF_TIMESTAMP_FOR_NEW_REQUEST, currentTime);
        mTimestampForNextPostAttempt =
                getLongFromMap(items, PREF_TIMESTAMP_FOR_NEXT_POST_ATTEMPT, currentTime);

        long requestTimestamp = getLongFromMap(items, PREF_TIMESTAMP_OF_REQUEST, INVALID_TIMESTAMP);

        // If the preference doesn't exist, it's likely that we haven't sent an install event.
        mSendInstallEvent = getBooleanFromMap(items, PREF_SEND_INSTALL_EVENT, true);

        // Restore the install source.
        String defaultInstallSource = determineInstallSource();
        mInstallSource = getStringFromMap(items, PREF_INSTALL_SOURCE, defaultInstallSource);

        // If we're not sending an install event, don't bother restoring the request ID:
        // the server does not expect to have persisted request IDs for pings or update checks.
        String persistedRequestId = mSendInstallEvent
                ? getStringFromMap(items, PREF_PERSISTED_REQUEST_ID, INVALID_REQUEST_ID)
                : INVALID_REQUEST_ID;

        mCurrentRequest = requestTimestamp == INVALID_TIMESTAMP
                ? null : createRequestData(requestTimestamp, persistedRequestId);

        mLatestVersion = getStringFromMap(items, PREF_LATEST_VERSION, "");
        mMarketURL = getStringFromMap(items, PREF_MARKET_URL, "");

        // If we don't have a timestamp for when we installed Chrome, then set it to now.
        mTimestampOfInstall = getLongFromMap(items, PREF_TIMESTAMP_OF_INSTALL, currentTime);

        // Confirm that the timestamp for the next request is less than the base delay.
        long delayToNewRequest = mTimestampForNewRequest - currentTime;
        if (delayToNewRequest > MS_BETWEEN_REQUESTS) {
            Log.w(TAG, "Delay to next request (" + delayToNewRequest
                    + ") is longer than expected.  Resetting to now.");
            mTimestampForNewRequest = currentTime;
            mustRewriteState = true;
        }

        // Confirm that the timestamp for the next POST is less than the current delay.
        long delayToNextPost = mTimestampForNextPostAttempt - currentTime;
        if (delayToNextPost > getBackoffScheduler().getGeneratedDelay()) {
            Log.w(TAG, "Delay to next post attempt (" + delayToNextPost
                    + ") is greater than expected (" + getBackoffScheduler().getGeneratedDelay()
                    + ").  Resetting to now.");
            mTimestampForNextPostAttempt = currentTime;
            mustRewriteState = true;
        }

        if (mustRewriteState) {
            saveState();
        }

        mStateHasBeenRestored = true;
    }

    /**
     * Writes out the current state to a file.
     */
    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_SEND_INSTALL_EVENT, mSendInstallEvent);
        setIsFreshInstallOrDataHasBeenCleared(this);
        editor.putLong(PREF_TIMESTAMP_OF_INSTALL, mTimestampOfInstall);
        editor.putLong(PREF_TIMESTAMP_FOR_NEXT_POST_ATTEMPT, mTimestampForNextPostAttempt);
        editor.putLong(PREF_TIMESTAMP_FOR_NEW_REQUEST, mTimestampForNewRequest);
        editor.putLong(PREF_TIMESTAMP_OF_REQUEST,
                hasRequest() ? mCurrentRequest.getCreationTimestamp() : INVALID_TIMESTAMP);
        editor.putString(PREF_PERSISTED_REQUEST_ID,
                hasRequest() ? mCurrentRequest.getRequestID() : INVALID_REQUEST_ID);
        editor.putString(PREF_LATEST_VERSION, mLatestVersion == null ? "" : mLatestVersion);
        editor.putString(PREF_MARKET_URL, mMarketURL == null ? "" : mMarketURL);

        if (mInstallSource != null) editor.putString(PREF_INSTALL_SOURCE, mInstallSource);

        editor.apply();
    }

    /**
     * Generates a random UUID.
     */
    @VisibleForTesting
    protected String generateRandomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sets the VersionNumberGetter used to get version numbers.  Set a new one to override what
     * version numbers are returned.
     */
    @VisibleForTesting
    static void setVersionNumberGetterForTests(VersionNumberGetter getter) {
        sVersionNumberGetter = getter;
    }

    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    @VisibleForTesting
    static VersionNumberGetter getVersionNumberGetter() {
        if (sVersionNumberGetter == null) {
            sVersionNumberGetter = new VersionNumberGetter();
        }
        return sVersionNumberGetter;
    }

    /**
     * Sets the MarketURLGetter used to get version numbers.  Set a new one to override what
     * URL is returned.
     */
    @VisibleForTesting
    static void setMarketURLGetterForTests(MarketURLGetter getter) {
        sMarketURLGetter = getter;
    }

    /**
     * Returns the stub used to grab the market URL for Chrome.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static String getMarketURL(Context context) {
        if (sMarketURLGetter == null) {
            sMarketURLGetter = new MarketURLGetter();
        }
        return sMarketURLGetter.getMarketURL(context, PREF_PACKAGE, PREF_MARKET_URL);
    }

    /**
     * Pulls a long from the shared preferences map.
     */
    private static long getLongFromMap(final Map<String, ?> items, String key, long defaultValue) {
        Long value = (Long) items.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Pulls a string from the shared preferences map.
     */
    private static String getStringFromMap(final Map<String, ?> items, String key,
            String defaultValue) {
        String value = (String) items.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Pulls a boolean from the shared preferences map.
     */
    private static boolean getBooleanFromMap(final Map<String, ?> items, String key,
            boolean defaultValue) {
        Boolean value = (Boolean) items.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * @return Whether it is either a fresh install or data has been cleared.
     * PREF_TIMESTAMP_OF_INSTALL is set within the first few seconds after a fresh install.
     * sIsFreshInstallOrDataCleared will be set to true if PREF_TIMESTAMP_OF_INSTALL has not
     * been previously set. Else, it will be set to false. sIsFreshInstallOrDataCleared is
     * guarded by sLock.
     * @param context The current Context.
     */
    public static boolean isFreshInstallOrDataHasBeenCleared(Context context) {
        return setIsFreshInstallOrDataHasBeenCleared(context);
    }

    private static boolean setIsFreshInstallOrDataHasBeenCleared(Context context) {
        synchronized (sIsFreshInstallLock) {
            if (sIsFreshInstallOrDataCleared == null) {
                SharedPreferences prefs = context.getSharedPreferences(
                        PREF_PACKAGE, Context.MODE_PRIVATE);
                sIsFreshInstallOrDataCleared = (prefs.getLong(PREF_TIMESTAMP_OF_INSTALL, -1) == -1);
            }
            return sIsFreshInstallOrDataCleared;
        }
    }

    protected final RequestGenerator getRequestGenerator() {
        if (mGenerator == null) mGenerator = createRequestGenerator(this);
        return mGenerator;
    }

    protected final ExponentialBackoffScheduler getBackoffScheduler() {
        if (mBackoffScheduler == null) {
            mBackoffScheduler = createBackoffScheduler(
                    PREF_PACKAGE, this, MS_POST_BASE_DELAY, MS_POST_MAX_DELAY);
        }
        return mBackoffScheduler;
    }
}
