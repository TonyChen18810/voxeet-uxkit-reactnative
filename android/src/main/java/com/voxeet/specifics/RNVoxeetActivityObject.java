package com.voxeet.specifics;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.facebook.react.ReactActivity;
import com.voxeet.toolkit.activities.notification.IncomingBundleChecker;
import com.voxeet.toolkit.activities.notification.IncomingCallFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import eu.codlab.simplepromise.solve.ErrorPromise;
import eu.codlab.simplepromise.solve.PromiseExec;
import eu.codlab.simplepromise.solve.Solver;
import voxeet.com.sdk.core.VoxeetSdk;
import voxeet.com.sdk.core.services.ScreenShareService;
import voxeet.com.sdk.events.error.ConferenceJoinedError;
import voxeet.com.sdk.events.error.PermissionRefusedEvent;
import voxeet.com.sdk.events.success.ConferenceJoinedSuccessEvent;
import voxeet.com.sdk.events.success.ConferencePreJoinedEvent;
import voxeet.com.sdk.utils.Validate;

/**
 * Class managing the communication between the Activity and the underlying Bundle manager
 *
 * To integrate this, if your MainActivity extends ReactActivity, simply replace ReactActivity with
 * RNVoxeetActivity
 *
 * if your app extends an other non-ReactNative application, please bind the methods used in the RNVoxeetActivity
 */

public class RNVoxeetActivityObject {

    private IncomingBundleChecker mIncomingBundleChecker;
    private Activity mActivity;

    public void onCreate(@NonNull Activity activity) {

        mActivity = activity;

        BounceVoxeetActivity.registerBouncedActivity(activity.getClass());

        mIncomingBundleChecker = new IncomingBundleChecker(activity.getIntent(), null);
    }

    public void onResume(@NonNull Activity activity) {
        if (null != VoxeetSdk.getInstance()) {
            VoxeetSdk.getInstance().register(activity, this);
        }

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this); //registering this activity
        }

        if (canBeRegisteredToReceiveCalls()) {
            IncomingCallFactory.setTempAcceptedIncomingActivity(BounceVoxeetActivity.class);
            IncomingCallFactory.setTempExtras(activity.getIntent().getExtras());
        }

        if (mIncomingBundleChecker.isBundleValid()) {
            mIncomingBundleChecker.onAccept();
        }

        if (null != VoxeetSdk.getInstance()) {
            VoxeetSdk.getInstance().getScreenShareService().consumeRightsToScreenShare();
        }
    }

    public void onPause(@NonNull Activity activity) {
        if (null != VoxeetSdk.getInstance()) {
            //stop fetching stats if any pending
            if (!VoxeetSdk.getInstance().getConferenceService().isLive()) {
                VoxeetSdk.getInstance().getLocalStatsService().stopAutoFetch();
            }
        }
        if(mActivity == activity) mActivity = null;
        EventBus.getDefault().unregister(this);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PermissionRefusedEvent.RESULT_CAMERA: {
                if (null != VoxeetSdk.getInstance() && VoxeetSdk.getInstance().getConferenceService().isLive()) {
                    VoxeetSdk.getInstance().getConferenceService().startVideo()
                            .then(new PromiseExec<Boolean, Object>() {
                                @Override
                                public void onCall(@Nullable Boolean result, @NonNull Solver<Object> solver) {

                                }
                            })
                            .error(new ErrorPromise() {
                                @Override
                                public void onError(@NonNull Throwable error) {
                                    error.printStackTrace();
                                }
                            });
                }
                return;
            }
            default:
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionRefusedEvent event) {
        if (null != event.getPermission()) {
            switch (event.getPermission()) {
                case CAMERA:
                    Validate.requestMandatoryPermissions(mActivity,
                            new String[]{Manifest.permission.CAMERA},
                            PermissionRefusedEvent.RESULT_CAMERA);
                    break;
            }
        }
    }

    public void onNewIntent(Intent intent) {
        mIncomingBundleChecker = new IncomingBundleChecker(intent, null);
        if (mIncomingBundleChecker.isBundleValid()) {
            mIncomingBundleChecker.onAccept();
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean managed = false;
        if (null != VoxeetSdk.getInstance()) {
            managed = VoxeetSdk.getInstance().getScreenShareService().onActivityResult(requestCode, resultCode, data);
        }

        return managed;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ScreenShareService.RequestScreenSharePermissionEvent event) {
        VoxeetSdk.getInstance().getScreenShareService()
                .sendUserPermissionRequest(mActivity);
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Specific event used to manage the current "incoming" call feature
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferencePreJoinedEvent event) {
        mIncomingBundleChecker.flushIntent();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceJoinedSuccessEvent event) {
        mIncomingBundleChecker.flushIntent();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceJoinedError event) {
        mIncomingBundleChecker.flushIntent();
    }

    /**
     * Get the current voxeet bundle checker
     * <p>
     * usefull to retrieve info about the notification (if such)
     * - user name
     * - avatar url
     * - conference id
     * - user id
     * - external user id
     * - extra bundle (custom)
     *
     * @return a nullable object
     */
    @Nullable
    protected IncomingBundleChecker getExtraVoxeetBundleChecker() {
        return mIncomingBundleChecker;
    }

    /**
     * Method called during the onResume of this
     *
     * @return true by default, override to change behaviour
     */
    protected boolean canBeRegisteredToReceiveCalls() {
        return true;
    }
}