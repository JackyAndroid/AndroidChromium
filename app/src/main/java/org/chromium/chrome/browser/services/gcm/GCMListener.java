// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services.gcm;

//import android.content.Intent;
//import android.os.Bundle;
//import android.util.Log;
//
//import com.google.ipc.invalidation.external.client.contrib.MultiplexingGcmListener;
//
//import org.chromium.base.ThreadUtils;
//import org.chromium.components.gcm_driver.GCMDriver;
//
///**
// * Receives GCM registration events and messages rebroadcast by MultiplexingGcmListener.
// */
//public class GCMListener extends MultiplexingGcmListener.AbstractListener {
//    /**
//     * Receiver for broadcasts by the multiplexed GCM service. It forwards them to
//     * GCMListener.
//     *
//     * This class is public so that it can be instantiated by the Android runtime.
//     */
//    public static class Receiver extends MultiplexingGcmListener.AbstractListener.Receiver {
//        @Override
//        protected Class<?> getServiceClass() {
//            return GCMListener.class;
//        }
//    }
//
//    private static final String TAG = "GCMListener";
//
//    public GCMListener() {
//        super(TAG);
//    }
//
//    @Override
//    protected void onRegistered(String registrationId) {
//        // Ignore this, since we register using GoogleCloudMessagingV2.
//    }
//
//    @Override
//    protected void onUnregistered(String registrationId) {
//        // Ignore this, since we register using GoogleCloudMessagingV2.
//    }
//
//    @Override
//    protected void onMessage(final Intent intent) {
//        final String bundleSubtype = "subtype";
//        final String bundleDataForPushApi = "data";
//        Bundle extras = intent.getExtras();
//        if (!extras.containsKey(bundleSubtype)) {
//            Log.w(TAG, "Received push message with no subtype");
//            return;
//        }
//        final String appId = extras.getString(bundleSubtype);
//        ThreadUtils.runOnUiThread(new Runnable() {
//            @Override public void run() {
//                GCMDriver.onMessageReceived(getApplicationContext(), appId,
//                        intent.getExtras());
//            }
//        });
//    }
//
//    @Override
//    protected void onDeletedMessages(int total) {
//        // TODO(johnme): Refactor/replace MultiplexingGcmListener so it passes us the extras and
//        // hence the subtype (aka appId).
//        Log.w(TAG, "Push messages were deleted, but we can't tell the Service Worker, as we"
//                   + " don't have access to the intent extras so we can't get the appId");
//        return;
//        //ThreadUtils.runOnUiThread(new Runnable() {
//        //    @Override public void run() {
//        //        GCMDriver.onMessagesDeleted(getApplicationContext(), appId);
//        //    }
//        //});
//    }
//}
