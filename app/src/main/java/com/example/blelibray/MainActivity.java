package com.example.blelibray;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final int infstatus_Initialisation = 0;
    private final int infstatus_TestPulse = 1;
    private final int infstatus_ZeroOffset = 2;
    private final int infstatus_Idle = 3;
    private final int infstatus_Error = 4;
    private final int infstatus_ShowPressure = 5;
    private final int infstatus_FlatTyreConfirm = 6;
    private final int infstatus_FlatTyreFill = 7;
    private final int infstatus_AutoInflate = 8;
    private final int infstatus_PurgeDeflate = 9;
    private final int infstatus_PurgeInflate = 10;
    private final int infstatus_CycleComplete = 11;
    private final int infstatus_CycleCompleteStable = 12;
    private final int infstatus_NitroInflating = 100;
    private final int infstatus_NitroComplete = 101;
    private final int infstatus_Waiting = 13;
    private int mInflatorStatus = infstatus_Waiting;
    private final int packet_idhigh_index = 0x00;
    private final int packet_idlow_index = 0x01;
    private final int packet_numhigh_index = 0x02;
    private final int packet_numlow_index = 0x03;
    private final int station_address_index = 0x04;
    private float mRequestedTargetPressure = -2.0f;
    private float mDisplayedTargetPressure = -2.0f;
    private float mCurrentPressure = -2.0f;
    private BluetoothGattCharacteristic mPclChas = null;
    private MainActivity  mActivity = null;

    BleService mBleService;
    boolean mConnected = false;
    final static String TAG = "MainActivity";
    private final int target_pressure = 0x00;
    private final int current_pressure = 0x01;
    private final int inflator_status = 0x02;
    private final int inflate_coil_status = 0x03;
    private final int custom_packet_id = 0x04;
    private TextView mActionStatus;
    private ListView mDeviceList;
    private ArrayList<String>  mMacAddressList = new ArrayList<>();
    private ArrayAdapter<String> mMacListAdapter;
    //private LeDeviceListAdapter mLeDeviceListAdapter;
    private final MBLite modbus = new MBLite();
    private boolean mNitroComplete = false;
    private Context mApplicationContext;
    public static final String BLUETOOTH_SERVER_CONNECTED_DEVICES = "uk.co.smartsentry.blelibrary.connecteddevices";
    public static final String BLUETOOTH_SERVER_CONNECTED_DEVICES_EXTRA = "uk.co.smartsentry.blelibrary.connecteddevices.extra";
    String connectedDevicesString = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mActivity = this;
        //mActionStatus = findViewById(R.id.status);
       // mLeDeviceListAdapter = new LeDeviceListAdapter();
        mDeviceList = findViewById(R.id.device_listview);
        mMacListAdapter = new ArrayAdapter<String>(this, R.layout.activity_main, R.id.list_device_address, mMacAddressList);
        mDeviceList.setAdapter(mMacListAdapter);
        mApplicationContext = getApplicationContext();

        // Register to receive messages.
        // with actions named "device-event".
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("device-event"));
        regrec();
        regrec();

    }


@Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("device_address");
            Log.d(TAG, "Got message: " + message);
           mMacAddressList.add(message);
           mMacListAdapter.notifyDataSetChanged();        }
    };
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }


    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = getLayoutInflater();
        }


        public void addDeviceMac(String macaddress) {
            String name;
            if (!mMacAddressList.contains(macaddress)) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                }
                mMacAddressList.add(macaddress);
                mMacListAdapter.notifyDataSetChanged();
            }
        }

        public void addDevice(BluetoothDevice device) {
            String name;
            if (!mLeDevices.contains(device)) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation for ActivityCompat#requestPermissions for more details.
                    //  requestBleConnectPermission();
                    //  return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    name = device.getAlias();
                    if (name != null) {
                        if (name.contains("PCL")) {
                            mLeDevices.add(device);
                        }
                    }
                    else {
                        name = device.getName();
                        if (name != null) {
                            if (device.getName().contains("PCL")) {
                                mLeDevices.add(device);
                                device.createBond();
                            }
                        }
                    }
                }
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.activity_main, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.list_device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.list_device_name);
                view.setTag(viewHolder);
            } else {
                //  the view is already instantiated so must contain at least one entry.
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                //return view;
            }
            String deviceAlias = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                deviceAlias = device.getAlias();
                if (deviceAlias != null && deviceAlias.length() > 0) {
                    viewHolder.deviceName.setText(deviceAlias);
                }
            } else {
                final String deviceName = device.getName();
                if (deviceName != null && deviceName.length() > 0) {
                    viewHolder.deviceName.setText(deviceName); }
                else{
                    viewHolder.deviceName.setText("Unknown Device");
                }
            }
            viewHolder.deviceAddress.setText(device.getAddress());
            return view;
        }
    }
        private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleService = ((BleService.LocalBinder) service).getService();
            if (!mBleService.initialize(mActivity)) {
                Log.d(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mConnected = false;
            mBleService = null;
        }
    };

    /*
    Define which events are of value
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void registerForNotification(List<BluetoothGattService> supportedGattServices) {

        // Build a list of gatt services and characteristics that the remote device supports
        //  And iterate through them to match up with the ones we need to interact with
        //  TODO for this case, we're using only the PCL_CHAS_UUID
        BluetoothGattService svc;
        BluetoothGattCharacteristic chas;
        UUID uid;
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
            }
        }

        mBleService.setCharacteristicNotification(mPclChas, true);
        try {
            Thread.sleep(200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void regrec() {
        boolean ok = false;
        String valstr;
        Intent recvIntt;
        IntentFilter gattFlt;

        if (!mConnected ) {
            Intent gattServiceIntent = new Intent(this, BleService.class);
            ok = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            if (ok == true) {
                gattFlt = makeGattUpdateIntentFilter();
                recvIntt = registerReceiver(mGattUpdateReceiver, gattFlt, Context.RECEIVER_EXPORTED);

                if (recvIntt != null) {
                    valstr = "Gatt Update Receiver Successful";
                    Toast.makeText(this, valstr, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, valstr);
                }
                else {
                    Log.i(TAG, "Gatt Update Receiver UnSuccessful");
                }
            }
        }

    }

//    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mApplicationContext);


    /* All broadcasts from the BLEService are filtered here.  Some
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
            } else if (BleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            } else if (BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                /* Show all the supported services and characteristics on the user interface.  */
                List<BluetoothGattService> supportedGattServices = mBleService.getSupportedGattServices();
                if (supportedGattServices != null) {
                    registerForNotification(supportedGattServices);
                }
            } else if (BleService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] rawdata;
                rawdata = intent.getByteArrayExtra(BleService.EXTRA_DATA);
                if (rawdata != null ) {
                    updateRaw(rawdata);
                    handleState();
                } else {
                    String macaddress = intent.getStringExtra(BleService.EXTRA_DATA);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            mActionStatus.setText(macaddress);
                        }
                    });
                }

            }

        }
    };

    /*
    Directed here by the BLE Broadcast listener callback
    Handles each incoming characteristic read or notification and assigns the values
    to the respective View
 */
    private void updateRaw(byte[] raw) {
        int val;
        float psi;
        byte [] tickFrame;

        //        if (modbus.getSlaveAddress() == 0x01) {
        if (raw.length > 8) {
            if (raw[station_address_index] == 0x01) {
                //modbus.parseRawData(raw);
//                modbus.getResponseFunction();
                int rec_pkt_num = modbus.getPacketNum();
                switch (rec_pkt_num) {
                    case target_pressure:
                        val = modbus.getPressure();
                        psi = (float) (((float) val) / 68.948);
                        mDisplayedTargetPressure = convertPressureUnits(psi);
                        break;

                    case current_pressure:
                        val = modbus.getPressure();
                        psi = (float) (((float) val) / 68.948);
                        mCurrentPressure = convertPressureUnits(psi);
                        break;

                    case inflate_coil_status:
                        val = modbus.getInflateStatus();
                        break;

                    case inflator_status:
                        mInflatorStatus = modbus.getInflatorState();
                        switch (mInflatorStatus) {
//                            case infstatus_Initialisation:
//                                mActionStatus.setText("Initialisation");
//                                break;
//
//                            case infstatus_TestPulse:
//                                mActionStatus.setText("Test Pulse");
//                                break;
//
//                            case infstatus_ZeroOffset:
//                                mActionStatus.setText("Zero Offset");
//                                break;
//
//                            case infstatus_Idle:
//                                mActionStatus.setText("Idle");
//                                break;
//
//                            case infstatus_Error:
//                                mActionStatus.setText("Error!");
//                                break;
//
//                            case infstatus_ShowPressure:
//                                mActionStatus.setText("Show Pressure");
//                                break;
//
//                            case infstatus_FlatTyreConfirm:
//                                mActionStatus.setText("Flat Tyre Confirm");
//                                break;
//
//                            case infstatus_FlatTyreFill:
//                                mActionStatus.setText("Flat Tyre Fill");
//                                break;
//
//                            case infstatus_AutoInflate:
//                                mActionStatus.setText("Auto Inflate");
//                                break;
//
//                            case infstatus_PurgeDeflate:
//                                mActionStatus.setText("Purge Deflate");
//                                break;
//
//                            case infstatus_PurgeInflate:
//                                mActionStatus.setText("Purge Inflate");
//                                break;
//
//                            case infstatus_CycleComplete:
//                                mActionStatus.setText("Cycle Complete");
//                                break;
//
//                            case infstatus_CycleCompleteStable:
//                                mActionStatus.setText("Pressure Complete and Stable");
//                                //  TODO    ??? mState = inflation_complete_state;
//                                break;
//
//                            case infstatus_NitroInflating:
//                                mActionStatus.setText("Nitro Inflating");
//                                break;
//
//                            case infstatus_NitroComplete:
//                                if ( mNitroComplete == true) {
//                                    mActionStatus.setText("Nitro Complete and Stable");
//                                    tickFrame = modbus.buildRawNitroCommandFrameWrite(0x01, 0x00);
//                                    mPclChas.setValue(tickFrame);
//                                    mBleService.writeCharacteristic(mPclChas);
//                                } else {
//                                    mActionStatus.setText("Nitro Complete");
//                                }
//                                break;
//
//                            default:
//                                String defStr = "***  DEFAULT: " + String.format(Locale.getDefault(), "%06d", mInflatorStatus) + "***";
//                                mActionStatus.setText(defStr);
//                                break;
                        }
                        break;

                    default:
                        break;
                }
            }
        }
    }

    private void handleState() {

    }

    private float convertPressureUnits (float fromPressure) {
        float toPressure;

        toPressure = fromPressure;
        return (toPressure);
    }

}