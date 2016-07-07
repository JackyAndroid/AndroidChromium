// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

/**
 * IDs for metrics tracking Document-mode actions.
 */
public class DocumentMetricIds {
    // DocumentActivity.StartedBy (sparse)
    public static final int STARTED_BY_UNKNOWN = 0;
    public static final int STARTED_BY_LAUNCHER = 1;
    public static final int STARTED_BY_ACTIVITY_RESTARTED = 2;
    public static final int STARTED_BY_ACTIVITY_BROUGHT_TO_FOREGROUND = 3;
    public static final int STARTED_BY_CHROME_HOME_MOST_VISITED = 100;
    public static final int STARTED_BY_CHROME_HOME_BOOKMARK = 101;
    public static final int STARTED_BY_CHROME_HOME_RECENT_TABS = 102;
    public static final int STARTED_BY_WINDOW_OPEN = 200;
    public static final int STARTED_BY_CONTEXT_MENU = 201;
    public static final int STARTED_BY_OPTIONS_MENU = 202;
    public static final int STARTED_BY_SEARCH_RESULT_PAGE = 300;
    public static final int STARTED_BY_SEARCH_SUGGESTION_EXTERNAL = 301;
    public static final int STARTED_BY_SEARCH_SUGGESTION_CHROME = 302;
    // Please reserve 4xx for STARTED_BY_EXTERNAL_APP_
    public static final int STARTED_BY_EXTERNAL_APP_GMAIL = 400;
    public static final int STARTED_BY_EXTERNAL_APP_FACEBOOK = 401;
    public static final int STARTED_BY_EXTERNAL_APP_PLUS = 402;
    public static final int STARTED_BY_EXTERNAL_APP_TWITTER = 403;
    public static final int STARTED_BY_EXTERNAL_APP_CHROME = 404;
    public static final int STARTED_BY_EXTERNAL_APP_OTHER = 405;
    public static final int STARTED_BY_EXTERNAL_APP_HANGOUTS = 406;
    public static final int STARTED_BY_EXTERNAL_APP_MESSENGER = 407;
    public static final int STARTED_BY_EXTERNAL_APP_NEWS = 408;
    public static final int STARTED_BY_EXTERNAL_APP_LINE = 409;
    public static final int STARTED_BY_EXTERNAL_APP_WHATSAPP = 410;
    public static final int STARTED_BY_EXTERNAL_APP_GSA = 411;
    public static final int STARTED_BY_CONTEXTUAL_SEARCH = 500;

    // DocumentActivity.OptOutDecision (enumerated)
    public static final int OPT_OUT_CLICK_GOT_IT = 0;
    public static final int OPT_OUT_CLICK_SETTINGS = 1;
    public static final int OPT_OUT_CLICK_COUNT = 2;
}
