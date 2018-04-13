package com.example.manav.automaticinfosender;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.IntentService;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.widget.Toast;
import android.Manifest;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

/**
 * Created by Manav on 4/9/2018.
 */


public class MainBackgroundService extends Service {

    public MainBackgroundService(Context applicationContext) {
        super();
        Log.i("HERE", "here I am!");
    }

    public MainBackgroundService() {
    }
    private Timer t;
    private int cell_id;
    private  GsmCellLocation gsmCellLocation = null;
    private String network_type;
    private String device_id;
    private String imsi;
    private String network_operator;
    private Double lat;
    private Double lon;
    private static final String TAG = "TESTGPS";
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 10000;
    private static final float LOCATION_DISTANCE = 0;
    private TelephonyManager telephonyManager = null;
    private WifiManager mWifiManager;
    private Location mLastLocation;
    private Socket mSocket;
    private String wifi_available_list;
    private String previous_connected_wifi;

    {
        try {
            mSocket = IO.socket("http://192.168.178.20:3000");
        } catch (URISyntaxException e) {}
    }

    /**
     * mSocket.connect();    To establish a connection
     * mSocket.emit("senddata", data)      senddata is keyword and data is JSO
     **/
    private class LocationListener implements android.location.LocationListener {


        public LocationListener(String provider) {
            Log.e(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.e(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);
            lat = mLastLocation.getLatitude();
            lon = mLastLocation.getLongitude();

        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        if (device_id!=null)
        Log.e("Device id",device_id);
        Log.e("imsi",imsi);
        Log.e("Network Operator",network_operator);
        Log.e("Network Type",network_type);
        Log.e("Cell Id", String.valueOf(cell_id));
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;

    }

    @Override
    public void onCreate() {
        mSocket.connect();
        Log.e(TAG, "onCreate");
        initializeLocationManager();
        initializeTelephonyManager();
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        device_id = telephonyManager.getDeviceId();
        imsi = telephonyManager.getSubscriberId();
        network_operator = telephonyManager.getNetworkOperatorName();
        network_type = getNetworkClass(this);

        if (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            gsmCellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
            if (gsmCellLocation != null) {
                cell_id = gsmCellLocation.getCid();
            }
        }

        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        registerReceiver(mWifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();

        if(mWifiManager.isWifiEnabled()){
        List<WifiConfiguration> configuredList = mWifiManager.getConfiguredNetworks();
        String temp_wifi="";
        for (WifiConfiguration config : configuredList) {
            temp_wifi = temp_wifi + config.SSID + ", ";
        }
        previous_connected_wifi = temp_wifi;
        Log.e("wifi list names", previous_connected_wifi);
    }

         t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                JSONObject mobile_info = new JSONObject();
                try {
                    mobile_info.put("IMSI", imsi);
                    mobile_info.put("IMEI",device_id);
                    mobile_info.put("cell_id",cell_id );
                    mobile_info.put("network_operator",network_operator);
                    mobile_info.put("network_type",network_type);
                    mobile_info.put("wifi_available",wifi_available_list);
                    mobile_info.put("wifi_saved",previous_connected_wifi);
                    if(mLastLocation!=null) {
                        Log.e("final latitude", String.valueOf(mLastLocation.getLatitude()));
                        mobile_info.put("lattitude", String.valueOf(mLastLocation.getLatitude()));
                        mobile_info.put("longitude", String.valueOf(mLastLocation.getLongitude()));
                        if(mSocket.connected())
                        mSocket.emit("senddata",mobile_info);
                        else mSocket.connect();
                    }


                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        },10000,5000);


    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("EXIT", "ondestroy!");
        unregisterReceiver(mWifiScanReceiver);
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
        t.cancel();
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }
    private void initializeTelephonyManager() {
        Log.e(TAG, "initializeTelephonyManager");
        if(telephonyManager==null){
            telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        }
    }

    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                int netCount = 0;
                List<ScanResult> mScanResults = mWifiManager.getScanResults();
                netCount = mScanResults.size();
                String wifiList = "";
                try { netCount=netCount -1;
                    while (netCount>=0){
                        Log.d("Available Wifi list", mScanResults.get(netCount).SSID);
                        wifiList = wifiList + mScanResults.get(netCount).SSID + ", ";
                        netCount=netCount -1;
                    }
                }
                catch (Exception e){ Log.d("Wifi", e.getMessage());
                }
                    wifi_available_list = wifiList;
                    Log.i("wifi list final",wifi_available_list);
            }
        }
    };

    public String getNetworkClass(Context context) {
        int networkType = telephonyManager.getNetworkType();
        Log.i("network type",String.valueOf(networkType));
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            default:
                return "Unknown";
        }
    }

}

class MyPhoneStateListener extends PhoneStateListener {
    MyPhoneStateListener mPhoneStatelistener;
    int mSignalStrength = 0;
    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        super.onSignalStrengthsChanged(signalStrength);
        mSignalStrength = signalStrength.getGsmSignalStrength();
        mSignalStrength = (2 * mSignalStrength) - 113; // -> dBm
    }
}
