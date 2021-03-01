package com.voxeet.specifics;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.voxeet.VoxeetSDK;
import com.voxeet.notification.RNIncomingBundleChecker;
import com.voxeet.notification.RNIncomingCallActivity;
import com.voxeet.sdk.events.sdk.ConferenceStatusUpdatedEvent;
import com.voxeet.sdk.json.ConferenceDestroyedPush;
import com.voxeet.sdk.services.SessionService;
import com.voxeet.uxkit.controllers.VoxeetToolkit;
import com.voxeet.uxkit.providers.rootview.DefaultRootViewProvider;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class RNRootViewProvider extends DefaultRootViewProvider {
    private final Application mApplication;
    private RNIncomingBundleChecker mRNIncomingBundleChecker;

    /**
     * @param application a valid application which be called to obtain events
     * @param toolkit
     */
    public RNRootViewProvider(@NonNull Application application, @NonNull VoxeetToolkit toolkit) {
        super(application, toolkit);

        mApplication = application;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        super.onActivityCreated(activity, bundle);

        if (!RNIncomingCallActivity.class.equals(activity.getClass())) {
            mRNIncomingBundleChecker = new RNIncomingBundleChecker(mApplication, activity.getIntent(), null);
            mRNIncomingBundleChecker.createActivityAccepted(activity);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        super.onActivityStarted(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        super.onActivityResumed(activity);

        if (!RNIncomingCallActivity.class.equals(activity.getClass())) {
            VoxeetSDK.instance().register(this);

            if (!EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().register(this); //registering this activity
            }

            RNIncomingBundleChecker checker = RNIncomingCallActivity.REACT_NATIVE_ROOT_BUNDLE;
            if (null != checker && checker.isBundleValid()) {
                SessionService sessionService = VoxeetSDK.session();
                if (sessionService.isSocketOpen()) {
                    checker.onAccept();
                    RNIncomingCallActivity.REACT_NATIVE_ROOT_BUNDLE = null;
                }
            }
            //TODO next steps, fix this call here
            /*mRNIncomingBundleChecker = new CordovaIncomingBundleChecker(mApplication, activity.getIntent(), null);

            if (mRNIncomingBundleChecker.isBundleValid()) {
                mRNIncomingBundleChecker.onAccept();
            }*/
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        super.onActivityPaused(activity);
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        super.onActivityStopped(activity);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        super.onActivitySaveInstanceState(activity, bundle);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        super.onActivityDestroyed(activity);
    }


    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Specific event used to manage the current "incoming" call feature
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceStatusUpdatedEvent event) {
        switch (event.state) {
            case JOINING:
            case JOINED:
            case ERROR:
                if (mRNIncomingBundleChecker != null)
                    mRNIncomingBundleChecker.flushIntent();
            default:
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceDestroyedPush event) {
        if (mRNIncomingBundleChecker != null)
            mRNIncomingBundleChecker.flushIntent();
    }
}
