package com.fullstack.attendy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Debug_BOB";
    private static final int REQUEST_CODE = 1;
    private static final String PUSH_TAG = "TAG";

    Button connectBtn;
    Button recheckBtn;
    TextView instructionsText;
    TextView countdownText;

    MessageListener mMessageListener;
    ArrayList<Message> messageList;

    Strategy.Builder builder;
    PublishOptions options;

    RequestQueue queue;

    String name;
    String email;

    String url ="http://40.114.119.189";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent i = new Intent(this, LoginActivity.class);
        startActivityForResult(i, REQUEST_CODE);

        queue = Volley.newRequestQueue(this);

        builder = new Strategy.Builder();
        builder.setDiscoveryMode(Strategy.DISTANCE_TYPE_EARSHOT);
        options = new PublishOptions.Builder()
                .setStrategy(builder.build())
                .build();

        instructionsText = findViewById(R.id.instructionText);
        countdownText = findViewById(R.id.countdownText);

        // Set the alarm to start at 8:30 a.m.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 4);
        calendar.set(Calendar.MINUTE, 0);

        connectBtn = findViewById(R.id.connectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNearbyMessage(email);
                sendIdentityToServer();

                connectBtn.setEnabled(false);
                instructionsText.setText(R.string.waitInstruction);
                countdownText.setVisibility(View.VISIBLE);

                new DelayTask().execute();

            }
        });

        recheckBtn = findViewById(R.id.recheckBtn);
        recheckBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                startActivity(new Intent(MainActivity.this, MainActivity.class));
            }
        });

        messageList = new ArrayList<>();

        mMessageListener = new MessageListener() {
            @Override
            public void onFound(Message message) {
                //Toast.makeText(MainActivity.this, "Received:" + new String(message.getContent()), Toast.LENGTH_SHORT).show();
                confirmOtherToServer(new String(message.getContent()));
            }
        };
    }

    private void sendNearbyMessage(String m) {
        Message message = new Message(m.getBytes());
        Nearby.getMessagesClient(this).publish(message, options);
        messageList.add(message);
    }

    private void stopPublishing()
    {
        for(Message message : messageList)
        {
            Nearby.getMessagesClient(this).unpublish(message);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    private void sendIdentityToServer()
    {
        JSONObject jsonBody = new JSONObject();
        try {

            jsonBody.put("fullName", name);
            jsonBody.put("email", email);
            final String mRequestBody = jsonBody.toString();
            StringRequest selfIdentifyRequest = new StringRequest(Request.Method.POST, url + "/login",
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.d(TAG, "onResponse: " + response);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d(TAG, "onErrorResponse: " + error);
                        }
                    }
            ){
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() {
                    try {
                        return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };
            selfIdentifyRequest.setTag(PUSH_TAG);

            queue.add(selfIdentifyRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void confirmOtherToServer(String detectedEmail) {
        JSONObject jsonBody = new JSONObject();
        try {

            jsonBody.put("email", email);
            jsonBody.put("detectedEmail", detectedEmail);
            final String mRequestBody = jsonBody.toString();
            StringRequest confirmOtherRequest = new StringRequest(Request.Method.POST, url + "/reportStatus",
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.d(TAG, "onResponse: " + response);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d(TAG, "onErrorResponse: " + error);
                        }
                    }
            ) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() {
                    try {
                        return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };
            confirmOtherRequest.setTag(PUSH_TAG);

            queue.add(confirmOtherRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        Nearby.getMessagesClient(this).unsubscribe(mMessageListener);
        stopPublishing();
        super.onStop();
        if (queue != null) {
            queue.cancelAll(PUSH_TAG);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE)
        {
            name = data.getStringExtra("name");
            email = data.getStringExtra("email");
            Nearby.getMessagesClient(this).subscribe(mMessageListener);
        }
    }

    class DelayTask extends AsyncTask<Void, Integer, String> {
        int count = 0;
        int maxCount = 60 * 10;

        @Override
        protected void onPreExecute() {
            countdownText.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... params) {
            while (count < maxCount) {
                SystemClock.sleep(1000);
                count++;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int durationSeconds = maxCount - count;
                        countdownText.setText((String.format("%02d:%02d",
                                (durationSeconds % 3600) / 60, (durationSeconds % 60))));
                    }
                });
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    doneAsync();
                }
            });
            return "Complete";
        }

        @Override
        protected void onProgressUpdate(Integer... values) {

        }
    }

    private void doneAsync()
    {
        countdownText.setVisibility(View.INVISIBLE);
        connectBtn.setVisibility(View.INVISIBLE);
        instructionsText.setText(R.string.doneInstruction);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recheckBtn.setVisibility(View.VISIBLE);
            }
        },4000);
    }

}
