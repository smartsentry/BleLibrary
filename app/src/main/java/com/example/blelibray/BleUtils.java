package com.example.blelibray;

public class BleUtils {
    public String mDevAddressStr = "EmptyAddress";
    public String mDevName = "EmptyName";
    public String mDevTargetPressure = "EmptyTargetPressure";
    public String mDevCurrentPressure = "EmptyCurrentPressure";
    public String mDevInflationStatus = "EmptyInflationStatus";

    public String getDevAddress() {
        return mDevAddressStr;
    }
    public String getDevTargetPressure() {
        return mDevTargetPressure;
    }
    public String getDevCurrentPressure() {
        return mDevCurrentPressure;
    }

    public void setDevAddress(String devaddr) {
        mDevAddressStr = devaddr;
    }
    public void setDevTargetPressure(String value) {
        mDevTargetPressure = value;
    }
    public void setDevCurrentPressure(String value) {
        mDevCurrentPressure = value;
    }

    private static final BleUtils utils = new BleUtils();
    public static BleUtils getInstance() {return utils;}

}
