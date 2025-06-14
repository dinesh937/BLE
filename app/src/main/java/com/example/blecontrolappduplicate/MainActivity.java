package com.example.blecontrolappduplicate;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Button btnToggleBluetooth, btnScan;
    private ListView listDevices;
    private DeviceAdapter deviceAdapter;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean hasPromptedPermissions = false;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                updateToggleButtonText();
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_OFF) {
                    deviceList.clear();
                    deviceAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggleBluetooth = findViewById(R.id.btnToggleBluetooth);
        btnScan = findViewById(R.id.btnScan);
        listDevices = findViewById(R.id.listDevices);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnToggleBluetooth.setOnClickListener(v -> checkPermissionsAndToggleBluetooth());
        btnScan.setOnClickListener(v -> checkPermissionsAndStartScan());

        deviceAdapter = new DeviceAdapter(this, deviceList);
        listDevices.setAdapter(deviceAdapter);

        listDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = deviceList.get(position);
            Intent intent = new Intent(MainActivity.this, DeviceActivity.class);
            intent.putExtra("device_address", device.getAddress());
            startActivity(intent);
        });

        btnToggleBluetooth.setText("Turn Bluetooth ON/OFF");

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
    }

    private void checkPermissionsAndToggleBluetooth() {
        if (hasPromptedPermissions) {
            if (hasBluetoothConnectPermission()) {
                toggleBluetooth();
            } else {
                Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothConnectPermission()) {
                showPermissionRationaleDialog("Bluetooth permission is required to enable or disable Bluetooth.",
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT});
                return;
            }
        }
        toggleBluetooth();
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void toggleBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
            Toast.makeText(this, "Turning Bluetooth OFF", Toast.LENGTH_SHORT).show();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void updateToggleButtonText() {
        if (!hasBluetoothConnectPermission()) {
            btnToggleBluetooth.setText("Turn Bluetooth ON/OFF");
            return;
        }
        btnToggleBluetooth.setText(bluetoothAdapter.isEnabled() ? "Turn Bluetooth OFF" : "Turn Bluetooth ON");
    }

    private void checkPermissionsAndStartScan() {
        if (hasPromptedPermissions) {
            if (hasAllBluetoothPermissions()) {
                startBleScan();
            } else {
                Toast.makeText(this, "Bluetooth permissions required for scanning", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasScanPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean hasConnectPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (!hasScanPermission || !hasConnectPermission) {
                showPermissionRationaleDialog("Bluetooth permissions are required to scan for devices.",
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT});
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showPermissionRationaleDialog("Location permission is required to scan for Bluetooth devices.",
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
                return;
            }
        }
        startBleScan();
    }

    private boolean hasAllBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void showPermissionRationaleDialog(String message, String[] permissions) {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(message)
                .setPositiveButton("Grant", (dialog, which) -> {
                    hasPromptedPermissions = true;
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
                })
                .setNegativeButton("Deny", (dialog, which) -> Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void startBleScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn ON Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        deviceList.clear();
        deviceAdapter.notifyDataSetChanged();

        handler.postDelayed(() -> {
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.stopScan(scanCallback);
                Toast.makeText(MainActivity.this, "Scan stopped", Toast.LENGTH_SHORT).show();
            }
        }, SCAN_PERIOD);

        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.startScan(scanCallback);
            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot start scan", Toast.LENGTH_SHORT).show();
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getAddress() != null) {
                String deviceName = device.getName();
                if (deviceName != null && deviceName.toLowerCase().startsWith("dashpod")) {
                    boolean isDuplicate = false;
                    for (BluetoothDevice d : deviceList) {
                        if (d.getAddress().equals(device.getAddress())) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (!isDuplicate) {
                        deviceList.add(device);
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothStateReceiver);
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show();
                updateToggleButtonText();
            } else {
                Toast.makeText(this, "Bluetooth enabling canceled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (btnToggleBluetooth.isPressed()) {
                    toggleBluetooth();
                } else {
                    startBleScan();
                }
            } else {
                hasPromptedPermissions = true;
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}