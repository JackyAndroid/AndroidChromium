// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmark;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

/**
 * Fragment that allows running asynchronous tasks showing a progress dialog if it takes too long.
 * Since each task blocks the UI in an informative way, only one task is allowed at once.
 * The fragment is retained in order to correctly support screen rotation changes and to prevent
 * tasks to run multiple times. Tasks are automatically canceled if the fragment is destroyed.
 * This class assumes that all its public methods will be called from the UI thread.
 *
 * The main purpose of this class is to allow fragments to correcly use our provider considering
 * that it should never been called from the UI thread.
 */
public class AsyncTaskFragment extends Fragment {
    /**
     * Delay in milliseconds introduced before showing the progress dialog.
     * Introduced in order to avoid flickering for short operations.
     */
    private static final int DELAY_BEFORE_PROGRESS_DIALOG_MS = 300;

    /**
     * Minimum time in miliseconds the dialog stays if shown.
     * This will artificially delay the result of the asynchronous tasks.
     * Will be ignored if the task is cancelled.
     */
    private static final int MINIMUM_DIALOG_STAY_MS = 500;

    private final Handler mHandler = new Handler();
    private ProgressDialog mProgressDialog;
    private String mDialogMessage;
    private boolean mShouldShowDialog;
    private boolean mHasDialogStayedEnough;
    protected FragmentAsyncTask mCurrentTask;

    private final Runnable mShowProgressDialog = new Runnable() {
        @Override
        public void run() {
            mShouldShowDialog = true;
            showDialog();
        }
    };

    private final Runnable mDialogStaysEnough = new Runnable() {
        @Override
        public void run() {
            mHasDialogStayedEnough = true;
            if (mCurrentTask != null) mCurrentTask.onDialogStayedEnough();
        }
    };

    /**
     * @return true if an asynchronous fragment task is currently running in the fragment.
     */
    public boolean isFragmentAsyncTaskRunning() {
        return mCurrentTask != null;
    }

    /**
     * Starts a new asynchronous fragment task. Will fail if another task is already running.
     *
     * @param task New asynchronous fragment task to run.
     * @param dialogMessage Message shown in the progress dialog while the task is run.
     */
    public boolean runFragmentAsyncTask(FragmentAsyncTask task, String dialogMessage) {
        if (isFragmentAsyncTaskRunning() || task == null) return false;

        mCurrentTask = task;
        mDialogMessage = dialogMessage;
        mShouldShowDialog = false;
        mHasDialogStayedEnough = false;
        mHandler.postDelayed(mShowProgressDialog, DELAY_BEFORE_PROGRESS_DIALOG_MS);
        task.execute();
        return true;
    }

    /**
     * Cancels the execution of any ongoing fragment asynchronous task.
     * Note that this doesn't ensure the immediate interruption of the task.
     */
    public void cancelFragmentAsyncTask() {
        if (mCurrentTask != null) {
            mCurrentTask.cancel(false);
            taskFinished();
        }
    }

    /**
     * Base class for asynchronous tasks to be run within the fragment.
     */
    public abstract class FragmentAsyncTask extends AsyncTask<Void, Void, Void> {
        /**
         * Method called to run the asynchronous code.
         */
        protected abstract void runBackgroundTask();

        /**
         * Method called when the asynchronous task has finished.
         */
        protected abstract void onTaskFinished();

        /**
         * Method called to disable the UI elements that depend on the task when it starts,
         * and again to re-enable them when finished or cancelled.
         */
        protected abstract void setDependentUIEnabled(boolean enabled);

        /**
         * Updates the enabled status of the UI elements depending on the task according to
         * the current task status.
         */
        public void updateDependentUI() {
            setDependentUIEnabled(getStatus() != Status.RUNNING);
        }

        @Override
        protected void onPreExecute() {
            setDependentUIEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            runBackgroundTask();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // Don't dispatch the task result yet if the dialog is present and hasn't stayed enough.
            if (mProgressDialog == null || mHasDialogStayedEnough) finishTask();
        }

        @Override
        protected void onCancelled(Void result) {
            cleanUp();
        }

        private void finishTask() {
            cleanUp();
            onTaskFinished();
        }

        private void cleanUp() {
            setDependentUIEnabled(true);
            taskFinished();
        }

        void onDialogStayedEnough() {
            // Dispatch the results of any finished tasks that are waiting for the dialog.
            if (getStatus() == Status.FINISHED) finishTask();
        }
    }

    // TODO: remove this than we only support Build.VERSION_CODES.M and newer.
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        showDialog();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        showDialog();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        hideDialog();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.setRetainInstance(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelFragmentAsyncTask();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (isFragmentAsyncTaskRunning()) updateTaskDependentUI();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            hideDialog();
        } else {
            showDialog();
        }
    }

    @Override
    public void setRetainInstance(boolean retain) {
        // The fragment is always retained for task and dialog consistence when rotating the screen.
        assert retain;
    }

    private void updateTaskDependentUI() {
        if (mCurrentTask != null) mCurrentTask.updateDependentUI();
    }

    private void showDialog() {
        if (isDetached() || isHidden() || !mShouldShowDialog) return;
        mProgressDialog = ProgressDialog.show(getActivity(), null, mDialogMessage, true, false);
        mHandler.postDelayed(mDialogStaysEnough, MINIMUM_DIALOG_STAY_MS);
    }

    private void hideDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private void taskFinished() {
        mHandler.removeCallbacks(mShowProgressDialog);
        mHandler.removeCallbacks(mDialogStaysEnough);
        hideDialog();
        mShouldShowDialog = false;
        mHasDialogStayedEnough = false;
        mCurrentTask = null;
    }
}
