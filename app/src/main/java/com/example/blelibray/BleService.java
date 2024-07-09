package com.example.blelibray;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleService extends Service {

    private final static String TAG = BleService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 3 seconds.
    private static final long SCAN_PERIOD = 4000;
    private LeDeviceList mLeDeviceList = new LeDeviceList();
    private BluetoothLeScanner mBluetoothScanner;
    private boolean mScanning;
    private Set<BluetoothDevice> mBondedDevices;
    private Handler mHandler;
    private Activity mActivity;






    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public int mConnectionState = STATE_DISCONNECTED;

    public final static String ACTION_GATT_CONNECTED = "uk.co.smartsentry.blelibrary.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "uk.co.smartsentry.blelibrary.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "uk.co.smartsentry.blelibrary.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "uk.co.smartsentry.blelibrary.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "uk.co.smartsentry.blelibrary.EXTRA_DATA";
    public final static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public final static UUID PCL_SERVICE_UUID = UUID.fromString("0000abf0-0000-1000-8000-00805f9b34fb");
    public final static UUID PCL_CHAS_UUID = UUID.fromString("0000abf1-0000-1000-8000-00805f9b34fb");

    /*
            00001800-0000-1000-8000-00805f9b34fb
            00001801-0000-1000-8000-00805f9b34fb
            59462f12-9543-9999-12c8-58b459a2712d
            00001811-0000-1000-8000-00805f9b34fb    The PCL Alert Notification Service

            0000abf0-0000-1000-8000-00805f9b34fb    The insecure PCL service
            0000abf1-0000-1000-8000-00805f9b34fb    The insecure PCL characteristic

            59462f12-9543-9999-12c8-58b459a2712d    The secure PCL service
            5c3a659e-897e-45e1-b016-007107c96df6    The secure PCL static value characteristic
            5c3a659e-897e-45e1-b016-007107c96df7    The secure PCL random value characteristic
    */

    /*
        Implements callback methods for GATT events that the app cares about.  For example,
        connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    // return;
                }
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                mBluetoothGatt.close();
                mBluetoothGatt = null;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
//        final Intent intent = new Intent(action);
//        sendBroadcast(intent);



        Intent intent = new Intent("device-event");
        // You can also include some extra data.
        intent.putExtra("message", "broadcastUpdate()");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        UUID destUuid = characteristic.getUuid();
        final byte[] data = characteristic.getValue();

        //  For this application, the properties are encoded by the peripheral, so the data is simply
        //  broadcast directly to the control activity where it's parsed and assigned to the
        //  appropriate view
        if (data != null && data.length > 0) {
            if (PCL_CHAS_UUID.equals(destUuid)) {
                intent.putExtra(EXTRA_DATA, data);
                sendBroadcast(intent);
            }
        }
    }

    private void broadcastUpdate(final String action, final String message) {
        final Intent intent = new Intent(action);

        //  For this application, the properties are encoded by the peripheral, so the data is simply
        //  broadcast directly to the control activity where it's parsed and assigned to the
        //  appropriate view
        if (message != null && message.length() > 0) {
            intent.putExtra(EXTRA_DATA, message);
            sendBroadcast(intent);
        }
    }

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /*
     * Initializes a reference to the local Bluetooth adapter.
     * @return Return true if the initialization is successful.
     */
    public boolean initialize(Activity activity) {
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothScanner == null) {
            Log.e(TAG, "Unable to obtain a BluetoothScanner.");
            return false;
        }

        mHandler = new Handler();
        mActivity = activity;

        scanLeDevices(true);

        return true;
    }

    /*
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if ((mBluetoothDeviceAddress != null) && (address.equals(mBluetoothDeviceAddress)) && (mBluetoothGatt != null)) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                //  return false;
            }
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // Connect to the device, so set the autoConnect parameter to false.
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return true;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "disconnect() - BLE Adapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        boolean res = false;
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "writechas() BLE Adapter not available");
            return false;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return false;
        }
        res = mBluetoothGatt.writeCharacteristic(characteristic);
        try {
            Thread.sleep(200);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        return res;
    }

    /*
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        boolean res;
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "readchas() BLE Adapter not available");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return;
        }
        res = mBluetoothGatt.readCharacteristic(characteristic);
        try {
            Thread.sleep(200);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

//    public void enableNotifications(BluetoothGattCharacteristic chas) {
//        setCharacteristicNotification(chas, true);
//    }

    private boolean hasPermissions() {
        String[] permissions = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        int permissionsCode = 42;
        Log.d(TAG, "Requesting Permissions");
            mActivity.requestPermissions(permissions, permissionsCode);

            return true;
        }

    /*
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    @SuppressLint("MissingPermission")
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        boolean success;
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "SetCharNotn - BLE Adapter not initialized");
            return;
        }
        if (hasPermissions()) {
            success = mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
            if (success) {
                Log.w(TAG, "Characteristic enabled");
            }
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


    private class LeDeviceList {
        private ArrayList<BluetoothDevice> mLeDevices = new ArrayList<>();

       public void addDevice(BluetoothDevice device) {
            String name;
            if (!mLeDevices.contains(device)) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
        public int getSize() {
           return mLeDevices.size();
        }

        public BluetoothDevice getDeviceAt(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

    }


    /*
     * Start a scan.  This function is invoked  by either a menu item (Scan or Stop) being clicked
     * or OnPause() & OnResume()
     */
    @SuppressLint("MissingPermission")
    private void scanLeDevices(final boolean enable) {

        if (hasPermissions()) {
            Log.d(TAG, "ScanLeDevice enabled...: ");
            // Stops scanning after a pre-defined scan period.
            mBondedDevices = mBluetoothAdapter.getBondedDevices();
            if (mBondedDevices != null) {
                Iterator value = mBondedDevices.iterator();
                for (int i = 0; i < mBondedDevices.size(); i++) {
                    BluetoothDevice device = (BluetoothDevice) value.next();
                    mLeDeviceList.addDevice(device);
                }
            }

            mHandler.postDelayed(new Runnable() {
                /*  NOTE:  This anonymous section of code is the part that is invoked after SCAN_PERIOD has elapsed  */
                public void run() {
                    mScanning = false;
                    Log.d(TAG, "ScanLeDevice scan time is up...: ");
//                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
//                        mActivity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 2);
//                    }
                    mBluetoothScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            /* BEWARE Logcat message  E  [GSIM LOG]: gsimLogHandler, msg: MESSAGE_SCAN_START:  see https://stackoverflow.com/questions/69322378/android-ble-scanning   */
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                mActivity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 2);
            }
            Log.d(TAG, "Starting scan...: ");
            mLeDeviceList.clear();
            mBluetoothScanner.startScan(mLeScanCallback);
            mScanning = true;
        } else {
            Log.d(TAG, "ScanLeDevice disabled...: ");
            mBluetoothScanner.stopScan(mLeScanCallback);
            mScanning = false;
        }
    }

    /*
        Device scan callback.  Adds device name and address to list as they're found
     */
    public ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device;
            device = result.getDevice();
            mLeDeviceList.addDevice(device);
            //broadcastUpdate(ACTION_DATA_AVAILABLE, device.getAddress());
            Intent intent = new Intent("device-event");
            intent.putExtra("device_address", device.getAddress());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

            //broadcastUpdate("device-event", device.getAddress());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };


}
