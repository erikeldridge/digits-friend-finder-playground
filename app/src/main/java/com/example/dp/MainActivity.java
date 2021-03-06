package com.example.dp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.crashlytics.android.answers.Answers;
import com.digits.sdk.android.AuthCallback;
import com.digits.sdk.android.ContactsCallback;
import com.digits.sdk.android.ContactsUploadFailureResult;
import com.digits.sdk.android.ContactsUploadService;
import com.digits.sdk.android.Digits;
import com.digits.sdk.android.DigitsAuthButton;
import com.digits.sdk.android.DigitsException;
import com.digits.sdk.android.DigitsSession;
import com.digits.sdk.android.SessionListener;
import com.digits.sdk.android.models.Contacts;
import com.digits.sdk.android.models.ContactsUploadResult;
import com.digits.sdk.android.models.DigitsUser;
import com.google.gson.Gson;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterApiException;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.fabric.sdk.android.DefaultLogger;
import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SessionListener, AuthCallback {
    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
    static final String TWITTER_KEY = BuildConfig.TWITTER_KEY;
    static final String TWITTER_SECRET = BuildConfig.TWITTER_SECRET;

    List<String> logBuffer = new ArrayList<>();
    ArrayAdapter<String> logAdapter;
    BroadcastReceiver broadcastReceiver;
    Gson jsonParser = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, logBuffer);
        broadcastReceiver = new ContactsUploadResultReceiver();
        TwitterAuthConfig twitterConfig = new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric fabric = new Fabric.Builder(this)
                .kits(new TwitterCore(twitterConfig), new Answers(), new Digits.Builder().build())
                .logger(new DefaultLogger(Log.DEBUG))
                .debuggable(true)
                .build();
        Fabric.with(fabric);
        DigitsSession digitsSession = Digits.getActiveSession();
        log("initializing, TWITTER_KEY=%s, TWITTER_SECRET=%s, session=%s", TWITTER_KEY, TWITTER_SECRET, jsonParser.toJson(digitsSession));
        setContentView(R.layout.activity_main);
        Button clearSessionButton = (Button) findViewById(R.id.clear_session);
        clearSessionButton.setOnClickListener(this);
        Button uploadButton = (Button) findViewById(R.id.upload);
        uploadButton.setOnClickListener(this);
        Button getButton = (Button) findViewById(R.id.get);
        getButton.setOnClickListener(this);
        DigitsAuthButton digitsButton = (DigitsAuthButton) findViewById(R.id.auth_button);
        digitsButton.setCallback(this);
        Digits.addSessionListener(this);
        ListView logView = (ListView) findViewById(R.id.log);
        logView.setAdapter(logAdapter);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(broadcastReceiver);
        super.onStop();
    }

    @Override
    protected void onStart() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.digits.sdk.android.UPLOAD_COMPLETE");
        filter.addAction("com.digits.sdk.android.UPLOAD_FAILED");
        registerReceiver(broadcastReceiver, filter);
        super.onStart();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.clear_session:
                Digits.clearActiveSession();
                log("clear session, session=%s", jsonParser.toJson(
                        Digits.getActiveSession()));
                break;
            case R.id.upload:
                Digits.uploadContacts();
                break;
            case R.id.get:
                Digits.findFriends(null, null, new ContactsCallback<Contacts>() {

                            @Override
                            public void success(Result<Contacts> result) {
                                final List<DigitsUser> matches = result != null
                                        && result.data != null
                                        && result.data.users != null
                                        ? result.data.users : Collections.<DigitsUser>emptyList();
                                final String cursor = result != null
                                        && result.data != null
                                        ? result.data.nextCursor : null;
                                log("contact match lookup success, matches=%s, cursor=%s",
                                        matches, cursor);
                                if (!TextUtils.isEmpty(cursor)) {
                                    Digits.findFriends(cursor, null, this);
                                }
                            }

                            @Override
                            public void failure(TwitterException exception) {
                                final TwitterApiException apiException =
                                        (TwitterApiException) exception;
                                log("errorCode=%d, errorMessage=%s",
                                        apiException.getErrorCode(),
                                        apiException.getErrorMessage());
                            }
                        });
            default:
                break;
        }
    }

    @Override
    public void changed(DigitsSession session) {
        log("digits session changed, session=%s", jsonParser.toJson(session));
    }

    @Override
    public void success(DigitsSession session, String phoneNumber) {
        log("digits auth success, session=%s, phonenumber=%s", jsonParser.toJson(session), phoneNumber);
    }

    @Override
    public void failure(DigitsException exception) {
        log(exception.toString());
    }

    class ContactsUploadResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ContactsUploadService.UPLOAD_COMPLETE.equals(intent.getAction())) {
                ContactsUploadResult result = intent
                        .getParcelableExtra(ContactsUploadService.UPLOAD_COMPLETE_EXTRA);
                log("contacts upload success, result=%s", result.toString());
            } else {
                ContactsUploadFailureResult result = intent
                        .getParcelableExtra(ContactsUploadService.UPLOAD_FAILED_EXTRA);
                log("contacts upload failure, result=%s", result.toString());
            }
        }
    }

    void log(String format, Object... args) {
        final String text = String.format(format, args);
        Log.d("Digits Playground", text);
        logBuffer.add(text);
        logAdapter.notifyDataSetChanged();
    }
}
