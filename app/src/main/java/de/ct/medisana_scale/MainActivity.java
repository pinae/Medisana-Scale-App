package de.ct.medisana_scale;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 0;
    private static final int MY_PERMISSIONS_REQUEST_BTLOC = 1;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean btScanning = false;
    private Handler mHandler;
    private TextView mTextViewDeviceAdr;
    private TextView mTextViewDeviceName;
    private TextView mTextViewData;
    private TextView mTextViewWeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextViewDeviceAdr = findViewById(R.id.textViewDeviceAdress);
        mTextViewDeviceName = findViewById(R.id.textViewDeviceName);
        mTextViewData = findViewById(R.id.textViewBytes);
        mTextViewWeight = findViewById(R.id.textViewWeight);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int PERM_GRANT = PackageManager.PERMISSION_GRANTED;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_ADMIN) != PERM_GRANT ||
                ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PERM_GRANT) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_BTLOC);
        } else {
            activateBluetooth();
        }
    }

    private void activateBluetooth() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanLeDevice(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_BTLOC: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    activateBluetooth();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }

    private void scanLeDevice(final boolean enable) {
        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (enable) {
            btScanning = true;
            ScanSettings settings = new ScanSettings.Builder().setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY).setCallbackType(
                            ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setMatchMode(
                                    ScanSettings.MATCH_MODE_AGGRESSIVE).setNumOfMatches(
                                            ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT).build();
            bluetoothLeScanner.startScan(null, settings, mLeScanCallback);
        } else {
            btScanning = false;
            bluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (result.getDevice().getType() == 2) {
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null && scanRecord.getAdvertiseFlags() > 0) {
                    byte[] manSpData = scanRecord.getManufacturerSpecificData(0x00b4);
                    if (manSpData != null && manSpData.length == 10) {
                        String deviceName = result.getDevice().getName();
                        if (deviceName == null || deviceName.isEmpty())
                            deviceName = "empty device name";
                        mTextViewDeviceAdr.setText(result.getDevice().getAddress());
                        mTextViewDeviceName.setText(deviceName);
                        mTextViewData.setText(Hextools.bytesToHex(manSpData));
                        int weight = 0;
                        int[] mfacs = {1, 16, 256, 4096};
                        boolean transmissionError = false;
                        for (int i = 0; i < 4 && !transmissionError; i++) {
                            byte t = manSpData[manSpData.length-1-i];
                            if ((t & 0xF0) >> 4 != 15 - (t & 0x0F)) transmissionError = true;
                            weight += ((t & 0xF0) >> 4) * mfacs[i];
                        }
                        if (!transmissionError) {
                            if ((manSpData[manSpData.length-5] & 0x08) == 0x08) weight = -weight;
                            boolean stabilized = (manSpData[manSpData.length-5] & 0x01) == 0x01;
                            mTextViewWeight.setText(Integer.toString(weight));
                        }
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };
}
