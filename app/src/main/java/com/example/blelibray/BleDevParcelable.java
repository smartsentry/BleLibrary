package com.example.blelibray;

import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

// simple class that just has one member property as an example
public class BleDevParcelable implements Parcelable {
    public BluetoothDevice mDevice;

    Creator<BluetoothDevice> creator;
    protected BleDevParcelable(Parcel in) {
        mDevice = in.readTypedObject(creator);
    }

    public static final Creator<BleDevParcelable> CREATOR = new Creator<BleDevParcelable>() {
        @Override
        public BleDevParcelable createFromParcel(Parcel in) {
            return new BleDevParcelable(in);
        }

        @Override
        public BleDevParcelable[] newArray(int size)
        {
            return new BleDevParcelable[size];
        }
    };

    public BleDevParcelable(BluetoothDevice  device) {
        mDevice = device;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dest.writeTypedObject(mDevice, 0);
        }
    }
}
