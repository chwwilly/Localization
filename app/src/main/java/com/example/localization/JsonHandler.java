package com.example.localization;

import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingResult;

import org.json.JSONException;
import org.json.JSONObject;

import static android.net.wifi.rtt.RangingResult.STATUS_SUCCESS;

public class JsonHandler {
    public static JSONObject addScanResult (ScanResult scanResult) {
        JSONObject mScanResult = new JSONObject();
        try {
            mScanResult.put("BSSID", scanResult.BSSID);
            //mScanResult.put("SSID", scanResult.SSID);
            //mScanResult.put("capabilities", scanResult.capabilities);
            //mScanResult.put("centerFreq0", scanResult.centerFreq0);
            //mScanResult.put("centerFreq1", scanResult.centerFreq1);
            //mScanResult.put("channelWidth", scanResult.channelWidth);
            //mScanResult.put("frequency", scanResult.frequency);
            mScanResult.put("level", scanResult.level);
            //mScanResult.put("operatorFriendlyName", scanResult.operatorFriendlyName.toString());
            //mScanResult.put("timestamp", scanResult.timestamp);
            //mScanResult.put("venueName", scanResult.venueName.toString());
            //mScanResult.put("is80211mcResponder", scanResult.is80211mcResponder());
            //mScanResult.put("isPasspointNetwork", scanResult.isPasspointNetwork());
        } catch (JSONException e) {

        }
        return mScanResult;
    }

    public static JSONObject addRangingResult (RangingResult rangingResult) {
        JSONObject mRangingResult = new JSONObject();
        try {
            mRangingResult.put("bssid", rangingResult.getMacAddress());
            mRangingResult.put("S", rangingResult.getStatus());
            if (rangingResult.getStatus() == STATUS_SUCCESS) {
                mRangingResult.put("r", rangingResult.getDistanceMm());
                mRangingResult.put("rsd", rangingResult.getDistanceStdDevMm());
                //mRangingResult.put("NumAttemptedMeasurements", rangingResult.getNumAttemptedMeasurements());
                mRangingResult.put("SuccMeas", rangingResult.getNumSuccessfulMeasurements());
                mRangingResult.put("rssi", rangingResult.getRssi());
                //mRangingResult.put("RangingTimestampMillis", rangingResult.getRangingTimestampMillis());
            }
        } catch (JSONException e) {

        }
        return mRangingResult;
    }
}
