package com.example.blelibray;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.RECEIVER_EXPORTED;

import static androidx.core.content.ContextCompat.registerReceiver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.UUID;

public class BleConnectionHandler implements ServiceConnection {
    private static final String TAG = "BleConnectionHandler";
    private BluetoothGattCharacteristic mPclChas = null;
    Intent gattServiceIntent;
    private String valstr;
    private Intent recvIntt;
    private IntentFilter gattFlt;
    private AppCompatActivity mActivityRef;
    private BleService mBleService;
    private final MbLite modbus = new MbLite();
    private Context mContext;
    private boolean mConnected = false;
    private String mDeviceName;
    private String mDeviceAddress;
    private final int target_pressure = 0x00;
    private final int current_pressure = 0x01;
    private final int inflator_status = 0x02;
    private final int inflate_coil_status = 0x03;
    private final int custom_packet_id = 0x04;
    private int mSentPacketId = 0;

    public void setBleDeviceName (String deviceName)
    {
        mDeviceName = deviceName;
    }

    public void setBleDeviceAddress (String deviceAddress)
    {
        mDeviceAddress = deviceAddress;
    }

    public void setBleServiceReference (BleService bleService)
    {
        mBleService = bleService;
    }

    public String getBleDeviceName() {
        return (mDeviceName);
    }

    public String getBleDeviceAddress() {
        return (mDeviceAddress);
    }

    public BleConnectionHandler(AppCompatActivity activity) {
        mActivityRef = activity;
        mContext = mActivityRef.getApplicationContext();
        gattServiceIntent = new Intent(mActivityRef.getApplicationContext(), BleService.class);
        mActivityRef.bindService(gattServiceIntent, this, BIND_AUTO_CREATE);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleService = ((BleService.LocalBinder) service).getService();
            if (!mBleService.initialize()) {
                Log.d(TAG, "Unable to initialize Bluetooth");
            }
            mBleService.connect(mDeviceAddress);
            mConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mConnected = false;
            mBleService = null;
        }
    };

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mBleService = ((BleService.LocalBinder) service).getService();
        if (!mBleService.initialize()) {
            Log.d(TAG, "Unable to initialize Bluetooth");
        }
    }

    /*
        Define which events are of value
     */
    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    public void connect(String address) {
        boolean ok = false;
        String valstr;
        Intent recvIntt;
        IntentFilter gattFlt;

        mDeviceAddress = address;
        if (!mConnected ) {
            Intent gattServiceIntent;
            gattServiceIntent = new Intent(mContext, (BleService.class));
            gattFlt = makeGattUpdateIntentFilter();
            ok = mActivityRef.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            if (ok) {
                recvIntt = mActivityRef.registerReceiver(mGattUpdateReceiver, gattFlt, RECEIVER_EXPORTED);
                if (recvIntt != null) {
                    valstr = "Gatt Update Receiver Successful";
                    Toast.makeText(mContext, valstr, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, valstr);
                } else {
                    Log.i(TAG, "Gatt Update Receiver UnSuccessful");
                }
            }
        }
    }


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            String strhexdata = new String();
            if (BleService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Log.i(TAG, "mGattUpdateReceiver: Device Connected");
            } else if (BleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Log.i(TAG, "mGattUpdateReceiver: Device Disconnected");
            } else if (BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                /* Show all the supported services and characteristics on the user interface.  */
                List<BluetoothGattService> supportedGattServices = mBleService.getSupportedGattServices();
                Log.i(TAG, "mGattUpdateReceiver: Services Discovered");
                if (supportedGattServices != null) {
                    registerForNotification(supportedGattServices);
                }
            } else if (BleService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] rawdata;
                rawdata = intent.getByteArrayExtra(BleService.EXTRA_DATA);
                if (rawdata != null ) {
                    for (int i=0; i < rawdata.length; i++) {
                        String hexbyte = String.format("%02x",rawdata[i]);
                        strhexdata += (hexbyte);
                    }
                    Log.i(TAG, "mGattUpdateReceiver: Data Received: " + strhexdata);


                }
            }
        }
    };

    private void registerForNotification(List<BluetoothGattService> supportedGattServices) {
        // Build a list of gatt services and characteristics that the remote device supports
        //  And iterate through them to match up with the ones we need to interact with
        //  TODO for this case, we're using only the PCL_CHAS_UUID
        BluetoothGattService svc;
        BluetoothGattCharacteristic chas;
        UUID uid;
        boolean success = false;
        for (int i = 0; i < supportedGattServices.size(); i++) {
            svc = supportedGattServices.get(i);
            //  Which service is this? only interested in 1 (PCL_CHAS_UUID)
            uid = svc.getUuid();
            if (uid.equals(mBleService.PCL_SERVICE_UUID)) {
                List<BluetoothGattCharacteristic> gattc = svc.getCharacteristics();
                for (int j = 0; j < gattc.size(); j++) {
                    chas = gattc.get(j);
                    if (chas.getUuid().equals(mBleService.PCL_CHAS_UUID)) {
                        mPclChas = svc.getCharacteristic(mBleService.PCL_CHAS_UUID);
                    }
                }
                if (mPclChas == null) {
                    Log.d(TAG, "registerForNotification mPclChas failed!");
                }
                else {
                   success =  mBleService.setCharacteristicNotification(mPclChas, true);
                   if (success) {
                       startPingHandler();
                   }
                }
            }
        }

        try {
            Thread.sleep(200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mBleService = null;
    }

    private void startPingHandler() {
        pingHandler.postDelayed(pingRunnable, 500);

    }

    /* Timer task used for heart-beat and display update connections to the remote device */
    Handler pingHandler = new Handler();
    Runnable pingRunnable = new Runnable() {

        @Override
        public void run() {
            String strhexdata = new String();
            if (mBleService != null) {
                if (mBleService.mConnectionState == BleService.STATE_CONNECTED) {
                    if (mPclChas != null) {

                        /*  Cyclic request for remote data
                         *  The sent_pkt_num encodes the type of request, this is necessary
                         *  because the modbus response does not indicate which register
                         *  is referenced in the response
                         */
                        switch (mSentPacketId) {

                            case target_pressure:    // TargetPressure (reg 4)
                                strhexdata = "";
                                byte[] tickFrame = modbus.buildRawTargetPressureFrameRead(mSentPacketId);
                                mPclChas.setValue(tickFrame);
                                mBleService.writeCharacteristic(mPclChas);
                                if (tickFrame != null ) {
                                    for (int i = 0; i < tickFrame.length; i++) {
                                        String hexbyte = String.format("%02x", tickFrame[i]);
                                        strhexdata += (hexbyte);
                                    }
                                }
                                Log.i(TAG, "Ping out  target_pressure: " + strhexdata);
                                break;

                            case current_pressure: //  CurrentPressure (reg 2)
                                tickFrame = modbus.buildRawCurrentPressueFrameRead(mSentPacketId);
                                strhexdata = "";
                                mPclChas.setValue(tickFrame);
                                mBleService.writeCharacteristic(mPclChas);
                                if (tickFrame != null ) {
                                    for (int i = 0; i < tickFrame.length; i++) {
                                        String hexbyte = String.format("%02x", tickFrame[i]);
                                        strhexdata += (hexbyte);
                                    }
                                }
                                Log.i(TAG, "Ping out  current_pressure: " + strhexdata);
                                break;

                            case inflator_status:    // Inflation Coil State (reg 0)
                                strhexdata = "";
                                tickFrame = modbus.buildRawInflatorStateFrameRead(mSentPacketId);
                                mPclChas.setValue(tickFrame);
                                mBleService.writeCharacteristic(mPclChas);
                                if (tickFrame != null ) {
                                    for (int i = 0; i < tickFrame.length; i++) {
                                        String hexbyte = String.format("%02x", tickFrame[i]);
                                        strhexdata += (hexbyte);
                                    }
                                }
                                Log.i(TAG, "Ping out  inflator_status: " + strhexdata);
                                break;

                            default:
                                break;
                        }
                        if (mSentPacketId < 2)
                            mSentPacketId++;
                        else {
                            mSentPacketId = 0;
                        }
                    }
                }
            }
            pingHandler.postDelayed(this, 800);
        }
    };


};
