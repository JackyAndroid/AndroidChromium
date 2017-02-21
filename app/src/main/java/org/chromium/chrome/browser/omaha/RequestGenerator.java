// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.content.Context;
import android.os.Build;
import android.util.Xml;

import org.chromium.base.BuildInfo;
import org.chromium.base.VisibleForTesting;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;

/**
 * Generates XML requests to send to the Omaha server.
 */
public abstract class RequestGenerator {
    // The Omaha specs say that new installs should use "-1".
    public static final int INSTALL_AGE_IMMEDIATELY_AFTER_INSTALLING = -1;

    private static final long MS_PER_DAY = 1000 * 60 * 60 * 24;

    private final Context mApplicationContext;

    @VisibleForTesting
    public RequestGenerator(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    /**
     * Determine how long it's been since Chrome was first installed.  Note that this may not
     * accurate for various reasons, but it shouldn't affect stats too much.
     */
    public static long installAge(
            long currentTimestamp, long installTimestamp, boolean sendInstallEvent) {
        if (sendInstallEvent) {
            return INSTALL_AGE_IMMEDIATELY_AFTER_INSTALLING;
        } else {
            return Math.max(0L, (currentTimestamp - installTimestamp) / MS_PER_DAY);
        }
    }

    /**
     * Generates the XML for the current request.
     * Follows the format laid out at https://github.com/google/omaha/blob/wiki/ServerProtocolV3.md
     * with some additional dummy values supplied.
     */
    public String generateXML(String sessionID, String versionName, long installAge,
            RequestData data) throws RequestFailureException {
        XmlSerializer serializer = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        try {
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);

            // Set up <request protocol=3.0 ...>
            serializer.startTag(null, "request");
            serializer.attribute(null, "protocol", "3.0");
            serializer.attribute(null, "version", "Android-1.0.0.0");
            serializer.attribute(null, "ismachine", "1");
            serializer.attribute(null, "requestid", "{" + data.getRequestID() + "}");
            serializer.attribute(null, "sessionid", "{" + sessionID + "}");
            serializer.attribute(null, "installsource", data.getInstallSource());
            appendExtraAttributes("request", serializer);

            // Set up <os platform="android"... />
            serializer.startTag(null, "os");
            serializer.attribute(null, "platform", "android");
            serializer.attribute(null, "version", Build.VERSION.RELEASE);
            serializer.attribute(null, "arch", "arm");
            serializer.endTag(null, "os");

            // Set up <app version="" ...>
            serializer.startTag(null, "app");
            serializer.attribute(null, "brand", getBrand());
            serializer.attribute(null, "client", getClient());
            serializer.attribute(null, "appid", getAppId());
            serializer.attribute(null, "version", versionName);
            serializer.attribute(null, "nextversion", "");
            serializer.attribute(null, "lang", getLanguage());
            serializer.attribute(null, "installage", String.valueOf(installAge));
            serializer.attribute(null, "ap", getAdditionalParameters());
            appendExtraAttributes("app", serializer);

            if (data.isSendInstallEvent()) {
                // Set up <event eventtype="2" eventresult="1" />
                serializer.startTag(null, "event");
                serializer.attribute(null, "eventtype", "2");
                serializer.attribute(null, "eventresult", "1");
                serializer.endTag(null, "event");
            } else {
                // Set up <updatecheck />
                serializer.startTag(null, "updatecheck");
                serializer.endTag(null, "updatecheck");

                // Set up <ping active="1" />
                serializer.startTag(null, "ping");
                serializer.attribute(null, "active", "1");
                serializer.endTag(null, "ping");
            }

            serializer.endTag(null, "app");
            serializer.endTag(null, "request");

            serializer.endDocument();
        } catch (IOException e) {
            throw new RequestFailureException("Caught an IOException creating the XML: ", e);
        } catch (IllegalArgumentException e) {
            throw new RequestFailureException(
                    "Caught an IllegalArgumentException creating the XML: ", e);
        } catch (IllegalStateException e) {
            throw new RequestFailureException(
                    "Caught an IllegalStateException creating the XML: ", e);
        }

        return writer.toString();
    }

    /**
     * Returns the application context.
     */
    protected Context getContext() {
        return mApplicationContext;
    }

    /**
     * Returns the current Android language and region code (e.g. en-GB or de-DE).
     *
     * Note: the region code depends only on the language the user selected in Android settings.
     * It doesn't depend on the user's physical location.
     */
    public String getLanguage() {
        Locale locale = Locale.getDefault();
        if (locale.getCountry().isEmpty()) {
            return locale.getLanguage();
        } else {
            return locale.getLanguage() + "-" + locale.getCountry();
        }
    }

    /**
     * Sends additional info that might be useful for statistics generation,
     * including information about channel and device type.
     * This string is partially sanitized for dashboard viewing and because people randomly set
     * these strings when building their own custom Android ROMs.
     */
    public String getAdditionalParameters() {
        String applicationLabel =
                StringSanitizer.sanitize(BuildInfo.getPackageLabel(mApplicationContext));
        String brand = StringSanitizer.sanitize(Build.BRAND);
        String model = StringSanitizer.sanitize(Build.MODEL);
        return applicationLabel + ";" + brand + ";" + model;
    }

    /**
     * Appends extra attributes to the XML for the given tag.
     * @param tag Tag to add extra attributes to.
     * @param serializer Serializer to append the attributes to.  Expects the last open tag to be
     *                   the one being appended to.
     */
    protected void appendExtraAttributes(String tag, XmlSerializer serializer) throws IOException {
    }

    /** Returns the UUID of the Chrome version we're running. */
    protected abstract String getAppId();

    /** Returns the brand code. If one can't be retrieved, return "". */
    protected abstract String getBrand();

    /** Returns the current client ID. */
    protected abstract String getClient();

    /** URL for the Omaha server. */
    public abstract String getServerUrl();
}
