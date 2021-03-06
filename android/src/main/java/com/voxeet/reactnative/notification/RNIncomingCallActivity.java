package com.voxeet.reactnative.notification;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.squareup.picasso.Picasso;
import com.voxeet.VoxeetSDK;
import com.voxeet.sdk.events.sdk.ConferenceStatusUpdatedEvent;
import com.voxeet.sdk.json.ConferenceDestroyedPush;
import com.voxeet.sdk.json.ConferenceEnded;
import com.voxeet.sdk.models.Conference;
import com.voxeet.sdk.services.ConferenceService;
import com.voxeet.sdk.services.NotificationService;
import com.voxeet.sdk.utils.AndroidManifest;
import com.voxeet.sdk.utils.Opt;
import com.voxeet.sdk.utils.Validate;
import com.voxeet.uxkit.activities.notification.IncomingBundleChecker;
import com.voxeet.uxkit.views.internal.rounded.RoundedImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class RNIncomingCallActivity extends AppCompatActivity implements IncomingBundleChecker.IExtraBundleFillerListener {

    private final static String TAG = RNIncomingCallActivity.class.getSimpleName();
    private static final String DEFAULT_VOXEET_INCOMING_CALL_DURATION_KEY = "voxeet_incoming_call_duration";
    private static final int DEFAULT_VOXEET_INCOMING_CALL_DURATION_VALUE = 40 * 1000;

    protected TextView mUsername;
    protected TextView mStateTextView;
    protected TextView mDeclineTextView;
    protected TextView mAcceptTextView;
    protected RoundedImageView mAvatar;
    protected EventBus mEventBus;

    private RNIncomingBundleChecker mIncomingBundleChecker;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIncomingBundleChecker = new RNIncomingBundleChecker(this, getIntent(), this);

        //add few Flags to start the activity before its setContentView
        //note that if your device is using a keyguard (code or password)
        //when the call will be accepted, you still need to unlock it
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(com.voxeet.uxkit.R.layout.voxeet_activity_incoming_call);

        mUsername = (TextView) findViewById(com.voxeet.uxkit.R.id.voxeet_incoming_username);
        mAvatar = (RoundedImageView) findViewById(com.voxeet.uxkit.R.id.voxeet_incoming_avatar_image);
        mStateTextView = (TextView) findViewById(com.voxeet.uxkit.R.id.voxeet_incoming_text);
        mAcceptTextView = (TextView) findViewById(com.voxeet.uxkit.R.id.voxeet_incoming_accept);
        mDeclineTextView = (TextView) findViewById(com.voxeet.uxkit.R.id.voxeet_incoming_decline);

        mDeclineTextView.setOnClickListener(view -> onDecline());

        mAcceptTextView.setOnClickListener(view -> onAccept());

        mHandler = new Handler();
        mHandler.postDelayed(() -> {
            try {
                if (null != mHandler)
                    finish();
            } catch (Exception e) {

            }
        }, AndroidManifest.readMetadataInt(this, DEFAULT_VOXEET_INCOMING_CALL_DURATION_KEY,
                DEFAULT_VOXEET_INCOMING_CALL_DURATION_VALUE));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mIncomingBundleChecker.isBundleValid()) {
            VoxeetSDK instance = VoxeetSDK.instance();
            mEventBus = instance.getEventBus();
            if (null != mEventBus) mEventBus.register(this);

            mUsername.setText(mIncomingBundleChecker.getUserName());
            try {
                if (!TextUtils.isEmpty(mIncomingBundleChecker.getAvatarUrl())) {
                    Picasso.get()
                            .load(mIncomingBundleChecker.getAvatarUrl())
                            .into(mAvatar);
                }
            } catch (Exception e) {

            }
        } else {
            Toast.makeText(this, getString(com.voxeet.uxkit.R.string.invalid_bundle), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onPause() {

        if (mEventBus != null) {
            mEventBus.unregister(this);
        }

        super.onPause();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceDestroyedPush event) {
        if (mIncomingBundleChecker.isSameConference(event.conferenceId)) {
            finish();
        }
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Specific event used to manage the current "incoming" call feature
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceEnded event) {
        if (mIncomingBundleChecker.isSameConference(event.conferenceId)) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceStatusUpdatedEvent event) {
        switch (event.state) {
            case JOINING:
                if (mIncomingBundleChecker.isSameConference(event.conference)) {
                    finish();
                }
            default:
        }
    }

    @Nullable
    protected String getConferenceId() {
        return mIncomingBundleChecker != null && mIncomingBundleChecker.isBundleValid() ? mIncomingBundleChecker.getConferenceId() : null;
    }

    protected void onDecline() {
        ConferenceService conferenceService = VoxeetSDK.conference();
        NotificationService notificationService = VoxeetSDK.notification();

        String conferenceId = getConferenceId();
        Conference conference = Opt.of(conferenceId)
                .then(conferenceService::getConference).orNull();

        RNVoxeetFirebaseIncomingNotificationService.stop(this, conferenceId, null);

        if (null != conference) {
            notificationService.decline(conference)
                    .then(result -> {
                        finish();
                    })
                    .error(error -> finish());
        } else {
            finish();
        }
    }

    protected void onAccept() {
        String conferenceId = getConferenceId();
        RNVoxeetFirebaseIncomingNotificationService.stop(this, conferenceId, null);

        if (!Validate.hasMicrophonePermissions(this)) {
            Validate.requestMandatoryPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, 42);
            return;
        }

        if (mIncomingBundleChecker.isBundleValid()) {
            PendingInvitationResolution.onIncomingInvitationAccepted(this);
            //REACT_NATIVE_ROOT_BUNDLE = mIncomingBundleChecker;

            //Intent intent = mIncomingBundleChecker.createRNActivityAccepted(this);
            ////start the accepted call activity
            //startActivity(intent);

            //and finishing this one - before the prejoined event
            finish();
            overridePendingTransition(0, 0);
        }
    }

    /**
     * Give the possibility to add custom extra infos before starting a conference
     *
     * @return a nullable extra bundle (will not be the bundle sent but a value with a key)
     */
    @Nullable
    @Override
    public Bundle createExtraBundle() {
        //override to return a custom intent to add in the possible notification
        //note that everything which could have been backed up from the previous activity
        //will be injected after the creation - usefull if the app is mainly based on
        //passed intents
        return null;
    }

    /**
     * Get the instance of the bundle checker corresponding to this activity
     *
     * @return an instance or null corresponding to the current bundle checker
     */
    @Nullable
    protected RNIncomingBundleChecker getBundleChecker() {
        return mIncomingBundleChecker;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }
}
