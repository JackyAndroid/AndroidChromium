// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.prerender;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import org.chromium.chrome.R;

/**
 * An activity for testing the prerender API. This can test whether
 * ChromePrerenderService warms up Chrome and prerender a given url
 * successfully.
 */
public class PrerenderAPITestActivity extends Activity implements OnClickListener {
    private static final String DEFAULT_URL = "http://www.nytimes.com";
    static final int MSG_PRERENDER_URL = 1;
    static final String KEY_PREPRENDERED_URL = "url_to_preprender";
    private String mUrl;

    Messenger mService = null;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.prerender_test_main);
        findViewById(R.id.preload_button).setOnClickListener(this);
        findViewById(R.id.load_button).setOnClickListener(this);
        ((EditText) findViewById(R.id.url_to_load)).setText(DEFAULT_URL);

        mUrl = DEFAULT_URL;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent();
        intent.setClassName(getApplicationContext().getPackageName(),
                ChromePrerenderService.class.getName());
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mService != null) unbindService(mConnection);
    }

    /**
     * Prerender a url using ChromePrerenderService.
     * @param url The url to be prerendered.
     */
    public void prerenderUrl(String url) {
        if (mService == null) return;
        Message msg = Message.obtain(
                null, ChromePrerenderService.MSG_PRERENDER_URL, 0, 0);
        Bundle data = new Bundle();
        data.putString(ChromePrerenderService.KEY_PRERENDERED_URL, url);
        msg.setData(data);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        mUrl = ((EditText) findViewById(R.id.url_to_load)).getText().toString();
        if (view.getId() == R.id.preload_button) {
            prerenderUrl(mUrl);
        } else if (view.getId() == R.id.load_button) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUrl));
            intent.setPackage(getApplicationContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

}
