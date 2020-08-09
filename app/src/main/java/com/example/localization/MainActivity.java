package com.example.localization;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.NetworkInterface;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.net.wifi.rtt.RangingResult.STATUS_SUCCESS;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String TAG2 = "JsonObj";
    private static final String TAG3 = "RttResults";

    private int DELAY_BEFORE_NEW_RANGING_REQUEST = 250;

    private int REQUEST_FINE_LOCATION = 1;

    private WifiManager mWifiManager;
    private WifiScanReceiver mWifiScanReceiver;
    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;
    private List<ScanResult> mAccessPointsSupporting80211mc;

    private int mNumberOfRangeRequests;

    final Handler mRangeRequestDelayHandler = new Handler();

//    private String url = "http://140.112.18.2:12093/inference/rtt-multiple-regression-md402";
    private String url = "http://140.112.18.2:12093/inference/rtt-multiple-regression-comb-md402";

    private List<String> ScanList;
    private List<Integer> level;
    private List<Integer> frequency;
    private List<Integer> channelWidth;

    private List<MacAddress> MacList;
    private ArrayList<Integer>[] Range;
    private ArrayList<Integer>[] RangeSD;
    private ArrayList<Integer>[] Rssi;
    private ArrayList<Integer>[] SuccMeas;

    private TextView latencyText;
    private TextView responseText;
    private TextView measText;

    private DecimalFormat mDecimalFormat = new DecimalFormat("#.##");

    private RequestQueue requestQueue;
    private Cache cache;
    private Network network;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        responseText = findViewById(R.id.responseTextView);
        latencyText = findViewById(R.id.latencyTextView);
        measText = findViewById(R.id.measTextView);

        mAccessPointsSupporting80211mc = new ArrayList<>();

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new WifiScanReceiver();

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mRttRangingResultCallback = new RttRangingResultCallback();

        mNumberOfRangeRequests = 0;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Fine location permission is needed.", Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }

        findDistancesToAccessPoints();

        // Instantiate the cache
        cache = new DiskBasedCache(this.getCacheDir(), 1024); // 1kB cap
        // Set up the network to use HttpURLConnection as the HTTP client.
        network = new BasicNetwork(new HurlStack());
        // Instantiate the RequestQueue with the cache and network.
        requestQueue = new RequestQueue(cache, network);

        // Start the queue
        requestQueue.start();
    }

    @Override
    public void onRequestPermissionsResult(int requstCode, String[] permissions, int[] grantResults) {
        if (requstCode == REQUEST_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requstCode, permissions, grantResults);
        }
    }

    private void postJson(JSONObject jsonObject) {
//        RequestQueue queue = Volley.newRequestQueue(this);
//        Log.d(TAG, jsonObject.toString());
        JsonObjectRequest getRequest = new JsonObjectRequest(Request.Method.POST, url, jsonObject,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response) {
                        // display response
//                        Log.d(TAG, "Response: " + response.toString());
                        try {
                            String output = "";
                            for (int i = 0; i < 3; i++) {
                                output += mDecimalFormat.format(response.getJSONArray("position").getDouble(i)) + "    ";
                            }
                            Log.d(TAG, "Response: " + response.get("position").toString());
                            responseText.setText(output);
                            latencyText.setText("Latency");
                        } catch (JSONException e) {
                            Log.d(TAG, e.toString());
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "Error.Response: " + error.toString());
                        responseText.setText(error.toString());
                    }
                }
        ){
            @Override
            public Map getHeaders() throws AuthFailureError {
                HashMap headers = new HashMap();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        requestQueue.add(getRequest);
    }

    private void startRttSurvey ( ) {
        if (mAccessPointsSupporting80211mc.size() > 0) {
            //Log.d(TAG, "start rtt scan");

            if (mNumberOfRangeRequests == 0) {
                int num = mAccessPointsSupporting80211mc.size();
                for (int i = 0; i < num; i++) {
                    resetRttResult(i);
                }
            } else {
                JSONObject mJSONObj = storeRangingData(mAccessPointsSupporting80211mc.size());
                postJson(mJSONObj);
            }

            mNumberOfRangeRequests ++;
            RangingRequest rangingRequest =
                    new RangingRequest.Builder().addAccessPoints(mAccessPointsSupporting80211mc).build();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
            mWifiRttManager.startRanging(
                    rangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback);
        } else {
            findDistancesToAccessPoints();
        }
    }

    private void resetRttResult (int index) {
        Range[index] = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0));
        RangeSD[index] = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0));
        Rssi[index] = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0));
        SuccMeas[index] = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0));
    }

    private JSONObject storeRangingData(int num) {
        JSONObject jsonObject = new JSONObject();
        JSONObject jsonObjectScan = new JSONObject();
        JSONObject jsonObjectFeature = new JSONObject();
        JSONArray jsonArrayRtt = new JSONArray();
        JSONArray jsonArrayState = new JSONArray();

        try {
            jsonObjectScan.put("bssid", new JSONArray(ScanList));
            jsonObjectScan.put("level", new JSONArray(level));
            jsonObjectScan.put("frequency", new JSONArray(frequency));
            jsonObjectScan.put("channelWidth", new JSONArray(channelWidth));
        } catch (Exception e) {}

        for (int i = 0; i < num; i++) {
            JSONObject jsonObjectRtt = new JSONObject();
            try {
                jsonObjectRtt.put("bssid", MacList.get(i));
                jsonObjectRtt.put("Range", new JSONArray(Range[i]));
                jsonObjectRtt.put("RangeSD", new JSONArray(RangeSD[i]));
                jsonObjectRtt.put("Rssi", new JSONArray(Rssi[i]));
                jsonObjectRtt.put("SuccMeas", new JSONArray(SuccMeas[i]));
            } catch (Exception e) {}
            jsonArrayRtt.put(jsonObjectRtt);
        }

        JSONObject jsonObjectFilter = new JSONObject();
        try {
            jsonObjectFilter.put("State", "aaaa");
        } catch (Exception e) {}
        jsonArrayState.put(jsonObjectFilter);

        try {
            jsonObjectFeature.put("scan", jsonObjectScan);
            jsonObjectFeature.put("rtt", jsonArrayRtt);
            jsonObjectFeature.put("state", jsonArrayState);
        } catch (Exception e) {}
        JSONObject jsonObjectDevice = new JSONObject();
        try {
            jsonObjectDevice.put("name", Build.MODEL.replaceAll(" ", "_"));
            jsonObjectDevice.put("addr", getMacAddr());
            jsonObjectDevice.put("type", "Android");
            jsonObject.put("device", jsonObjectDevice);
            jsonObject.put("feature", jsonObjectFeature);
        } catch (Exception e) {}

        Log.d(TAG2,  Build.MODEL.replaceAll(" ", "_") + " " + jsonObject.toString());
        return jsonObject;
    }

    private void findDistancesToAccessPoints () {
        ScanList = new ArrayList<>();
        level = new ArrayList<>();
        frequency = new ArrayList<>();
        channelWidth = new ArrayList<>();

        // Register wifi receiver
        registerReceiver(
                mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();
    }


    private class WifiScanReceiver extends BroadcastReceiver {

        private List<ScanResult> find80211mcSupportedAccessPoints(
                @NonNull List<ScanResult> originalList) {
            List<ScanResult> newList = new ArrayList<>();

            for (ScanResult scanResult : originalList) {
                if (scanResult.is80211mcResponder() && scanResult.channelWidth == 2) {
                    newList.add(scanResult);
                }
                if (newList.size() >= RangingRequest.getMaxPeers()) {
                    break;
                }
            }
            return newList;
        }

        // This is checked via mLocationPermissionApproved boolean
        @Override
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> scanResults = mWifiManager.getScanResults();

            if (scanResults != null && MacList == null) {
//                long currTime = System.currentTimeMillis();
                JSONArray jsonArray = new JSONArray();

                for (ScanResult scanResult : scanResults) {
//                    JSONObject json = JsonHandler.addScanResult(scanResult);
//                    jsonArray.put(json);
                    ScanList.add(scanResult.BSSID);
                    level.add(scanResult.level);
                    frequency.add(scanResult.frequency);
                    channelWidth.add(scanResult.channelWidth);
                }
                mAccessPointsSupporting80211mc = find80211mcSupportedAccessPoints(scanResults);
                int num = mAccessPointsSupporting80211mc.size();
                MacList = new ArrayList<>();
                Range = new ArrayList[num];
                RangeSD = new ArrayList[num];
                Rssi = new ArrayList[num];
                SuccMeas = new ArrayList[num];
                Log.d(TAG, mAccessPointsSupporting80211mc.toString());

                startRttSurvey();
            }
        }
    }

    private class RttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest () {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRttSurvey();
                        }
                    },
                    DELAY_BEFORE_NEW_RANGING_REQUEST);
        }

        @Override
        public void onRangingFailure (int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> rangingResultList) {
            int SuccNum = 0;
            for (int i = 0; i < rangingResultList.size(); i++) {
                RangingResult rangingResult = rangingResultList.get(i);

                if (Range[i] == null) {
                    resetRttResult(i);
                }

                Range[i].remove(0);
                RangeSD[i].remove(0);
                Rssi[i].remove(0);
                SuccMeas[i].remove(0);

                if (mNumberOfRangeRequests == 1) {
                    MacList.add(rangingResult.getMacAddress());
                }

                if (rangingResult.getStatus() == STATUS_SUCCESS) {
                    SuccNum = SuccNum + 1;
                    Range[i].add(rangingResult.getDistanceMm());
                    RangeSD[i].add(rangingResult.getDistanceStdDevMm());
                    Rssi[i].add(rangingResult.getRssi());
                    SuccMeas[i].add(rangingResult.getNumSuccessfulMeasurements());
                } else {
                    Range[i].add(0);
                    RangeSD[i].add(0);
                    Rssi[i].add(0);
                    SuccMeas[i].add(0);
                }
            }
            measText.setText("Num of 802.11mc responder: " + SuccNum + " / " + rangingResultList.size());
            long currTime = System.currentTimeMillis() % 100000 ;
            Log.d(TAG, "Rtt " + currTime + " " + rangingResultList.size());

            queueNextRangingRequest();
        }
    }

    // This function prevent getting Mac addr of 02:00:00:00:00:00
    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(Integer.toHexString(b & 0xFF) + ":");
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
        }
        return "02:00:00:00:00:00";
    }
}
