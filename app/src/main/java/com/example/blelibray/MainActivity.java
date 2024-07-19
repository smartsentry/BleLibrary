package com.example.blelibray;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements
        GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private ListView mListview;
    private Set<BluetoothDevice> mBondedDevices;
    private BleService mBleService = new BleService();
    public static final String EXTRA_DEVICE_NAME = "uk.co.smartsentry.PCLQube.NAME";
    public static final String EXTRA_DEVICE_ADDRESS = "uk.co.smartsentry.PCLQube.ADDRESS";
    private static final String TAG = "DevListActivity";
    private GestureDetectorCompat mDetector;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothScanner;
    private boolean mConnected = false;
    private BleConnectionHandler mBleConnectionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDetector = new GestureDetectorCompat(this, this);
        mDetector.setOnDoubleTapListener((GestureDetector.OnDoubleTapListener) this);
        /*  Inform the BLE Service of the App object reference  */
        mBleService.setApplicationReference(this);
        mBleConnectionHandler = new BleConnectionHandler(this);
        mBleConnectionHandler.setBleServiceReference(mBleService);
        setContentView(R.layout.activity_dev_list);
        mHandler = new Handler();
        /* Check if BLE is supported on the device. */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        /* Initialises list view adapter.  */
        mLeDeviceListAdapter = new MainActivity.LeDeviceListAdapter();
        mListview = findViewById(R.id.devlistView);
        mListview.setAdapter(mLeDeviceListAdapter);
        mLeDeviceListAdapter.clear();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("device-event"));
        /*  Start the initial scan */
        mBleService.scanLeDevices(true);

        mListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;
                sendControlMessage(device);
                //        intent.putExtra(ControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                if (mScanning) {
                    mBleService.stopScan();
                    mScanning = false;
                }
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "OnPause...");
        //  TODO Inestigate why /if the line below is needed
        //        scanLeDevice(false);
        //  mLeDeviceListAdapter.clear();
    }
    @Override
    public boolean onScroll(@NonNull MotionEvent event1, @NonNull MotionEvent event2, float distanceX, float distanceY) {
        Log.d(TAG, "onScroll: " + event1.toString() + event2.toString());
        return true;
    }


    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }


    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices = new ArrayList<>();
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        /*  Adds the BLE device object to the ArrayList  */
        @SuppressLint("MissingPermission")
        public void addDevice(BluetoothDevice device) {
            String name;
            if (!mLeDevices.contains(device)) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    name = device.getAlias();
                    if (name != null) {
                        if (name.contains("PCL")) {
                            mLeDevices.add(device);
                        }
                    } else {
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
            MainActivity.ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.activity_dev_list, null);
                viewHolder = new MainActivity.ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.list_device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.list_device_name);
                view.setTag(viewHolder);
            } else {
                //  the view is already instantiated so must contain at least one entry.
                viewHolder = (MainActivity.ViewHolder) view.getTag();
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
            final String deviceAlias;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                deviceAlias = device.getAlias();

                if (deviceAlias != null && deviceAlias.length() > 0) {
                    viewHolder.deviceName.setText(deviceAlias);
                } else {
                    final String deviceName = device.getName();
                    if (deviceName != null && deviceName.length() > 0) {
                        viewHolder.deviceName.setText(deviceName);
                    } else {
                        viewHolder.deviceName.setText(R.string.unknown_device);
                    }
                }
                viewHolder.deviceAddress.setText(device.getAddress());
            }
            return view;
        }
    }

    /*  This receiver traps all messages from the device apart from the Gatt related ones  */
    /*  All Gatt related messages are trapped and handled by the BleConnectionHandler      */
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        BluetoothDevice device = null;
        BleDevParcelable devParcelable;

        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            //String message = intent.getStringExtra("device_address");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                devParcelable = intent.getParcelableExtra("device");
                device = devParcelable.mDevice;
            }
            if (device != null) {
                Log.d(TAG, "mMessageReceiver  OnReceive()" + device.getAddress().toString());
                mLeDeviceListAdapter.addDevice(device);
                mLeDeviceListAdapter.notifyDataSetChanged();
                invalidateOptionsMenu();
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent event) {
        Log.d(TAG, "onDown: " + event.toString());
        return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        Log.d(TAG, "onFling: " + event1.toString() + event2.toString());
        if (velocityY > 8000) {
            //scanLeDevice(true);
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        Log.d(TAG, "onLongPress: " + event.toString());
    }

    @Override
    public void onShowPress(MotionEvent event) {
        Log.d(TAG, "onShowPress: " + event.toString());
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        Log.d(TAG, "onSingleTapUp: " + event.toString());
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
        Log.d(TAG, "onDoubleTap: " + event.toString());
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        Log.d(TAG, "onDoubleTapEvent: " + event.toString());
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        Log.d(TAG, "onSingleTapConfirmed: " + event.toString());
        return true;
    }

    /*
        Pass the chosen device to the Vehicle Type activity
     */
    @SuppressLint("MissingPermission")
    public void sendControlMessage(BluetoothDevice device) {
        String deviceName = device.getName();
        String deviceAlias = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            deviceAlias = device.getAlias();
        }
        String deviceAddress = device.getAddress();
        BleUtils.getInstance().setDevAddress(deviceAddress);
        mBleConnectionHandler.setBleDeviceName(deviceName);
        mBleConnectionHandler.setBleDeviceAddress(deviceAddress);
        mBleConnectionHandler.connect(deviceAddress);
    }

//
//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
//        // fire an intent to display a dialog asking the user to grant permission to enable it.
//        if (!mBluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                // TODO: Consider calling
//                //    ActivityCompat#requestPermissions
//                // here to request the missing permissions, and then overriding
//                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                //                                          int[] grantResults)
//                // to handle the case where the user grants the permission. See the documentation
//                // for ActivityCompat#requestPermissions for more details.
//                return;
//            }
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
//        scanLeDevice(true);
//    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        // User chose not to enable Bluetooth.
//        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
//            finish();
//            return;
//        }
//        super.onActivityResult(requestCode, resultCode, data);
//    }
//


}