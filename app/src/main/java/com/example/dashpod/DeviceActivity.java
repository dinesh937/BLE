package com.example.dashpod;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceActivity extends AppCompatActivity {

    private static final String TAG = "DeviceActivity";

    // Nordic UART Service UUIDs
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;

    // UI Components
    private TextView tvTerminal;
    private ScrollView svTerminal;
    private EditText etMacroName, etMacroValue;
    private RadioGroup rgEditMode, rgAction;
    private Button btnExecute, btnBPM, btnResetAvatar, btnToggleAvatar;
    private LineChart eulerChart, quaternionChart;
    private FrameLayout avatarContainer;

    // Custom Avatar View
    private AvatarView avatarView;
    private boolean isAvatarVisible = true;

    // Chart data sets
    private LineDataSet yawDataSet, pitchDataSet, rollDataSet;
    private LineDataSet qwDataSet, qxDataSet, qyDataSet, qzDataSet;
    private LineData eulerLineData, quaternionLineData;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    // Data storage
    private final List<Float> yawValues = new ArrayList<>();
    private final List<Float> pitchValues = new ArrayList<>();
    private final List<Float> rollValues = new ArrayList<>();
    private final List<Float> qwValues = new ArrayList<>();
    private final List<Float> qxValues = new ArrayList<>();
    private final List<Float> qyValues = new ArrayList<>();
    private final List<Float> qzValues = new ArrayList<>();
    private final List<String> timestamps = new ArrayList<>();
    private int dataPointCounter = 0;

    // Current sensor values
    private float currentYaw = 0f, currentPitch = 0f, currentRoll = 0f;
    private float currentQw = 1f, currentQx = 0f, currentQy = 0f, currentQz = 0f;

    // Regex patterns for parsing BNO055 data
    private final Pattern eulerPattern = Pattern.compile("\\{\"EX\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"EY\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"EZ\"\\s*:\\s*([-]?\\d*\\.?\\d+)\\}");
    private final Pattern quaternionPattern = Pattern.compile("\\{\"QW\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"QX\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"QY\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"QZ\"\\s*:\\s*([-]?\\d*\\.?\\d+)\\}");

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        initializeUI();
        initializeCharts();
        initializeAvatar();

        String deviceAddress = getIntent().getStringExtra("device_address");
        if (deviceAddress != null) {
            connectToDevice(deviceAddress);
        } else {
            appendToTerminal("No device address provided.");
        }
    }

    private void initializeUI() {
        tvTerminal = findViewById(R.id.tvTerminal);
        svTerminal = findViewById(R.id.svTerminal);
        etMacroName = findViewById(R.id.etMacroName);
        etMacroValue = findViewById(R.id.etMacroValue);
        rgEditMode = findViewById(R.id.rgEditMode);
        rgAction = findViewById(R.id.rgAction);
        btnExecute = findViewById(R.id.btnExecute);
        btnBPM = findViewById(R.id.btnBPM);
        btnResetAvatar = findViewById(R.id.btnResetAvatar);
        btnToggleAvatar = findViewById(R.id.btnToggleAvatar);
        eulerChart = findViewById(R.id.eulerChart);
        quaternionChart = findViewById(R.id.quaternionChart);
        avatarContainer = findViewById(R.id.avatarContainer);

        Spinner spMacros = findViewById(R.id.spMacros);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.device_macros_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMacros.setAdapter(adapter);

        spMacros.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                switch (selected) {
                    case "BNO Eu&Qua":
                        etMacroName.setText("BNO Eu&Qua");
                        etMacroValue.setText("01 04 01");
                        break;
                    case "BNO Set up Cal":
                        etMacroName.setText("BNO Set up Cal");
                        etMacroValue.setText("01 04 00");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }
        btnExecute.setOnClickListener(v -> executeMacro());
        btnBPM.setOnClickListener(this::goToBPMActivity);
        btnResetAvatar.setOnClickListener(v -> resetAvatar());
        btnToggleAvatar.setOnClickListener(v -> toggleAvatar());
    }

    private void initializeAvatar() {
        try {
            avatarView = new AvatarView(this);
            avatarContainer.addView(avatarView);
            appendToTerminal("Custom Avatar initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Avatar", e);
            appendToTerminal("Avatar initialization failed: " + e.getMessage());
        }
    }

    private void initializeCharts() {
        // Euler Angles Chart
        yawDataSet = new LineDataSet(new ArrayList<>(), "Yaw");
        yawDataSet.setColor(0xFF2196F3); // Blue
        yawDataSet.setLineWidth(2f);
        yawDataSet.setDrawCircles(false);

        pitchDataSet = new LineDataSet(new ArrayList<>(), "Pitch");
        pitchDataSet.setColor(0xFF4CAF50); // Green
        pitchDataSet.setLineWidth(2f);
        pitchDataSet.setDrawCircles(false);

        rollDataSet = new LineDataSet(new ArrayList<>(), "Roll");
        rollDataSet.setColor(0xFFCD5C5C); // Indian Red
        rollDataSet.setLineWidth(2f);
        rollDataSet.setDrawCircles(false);

        eulerLineData = new LineData(yawDataSet, pitchDataSet, rollDataSet);
        eulerChart.setData(eulerLineData);
        setupChart(eulerChart, -360f, 360f, "BNO055 Euler Angles");

        // Quaternion Chart
        qwDataSet = new LineDataSet(new ArrayList<>(), "W");
        qwDataSet.setColor(0xFF2196F3); // Blue
        qwDataSet.setLineWidth(2f);
        qwDataSet.setDrawCircles(false);

        qxDataSet = new LineDataSet(new ArrayList<>(), "X");
        qxDataSet.setColor(0xFF4CAF50); // Green
        qxDataSet.setLineWidth(2f);
        qxDataSet.setDrawCircles(false);

        qyDataSet = new LineDataSet(new ArrayList<>(), "Y");
        qyDataSet.setColor(0xFFCD5C5C); // Indian Red
        qyDataSet.setLineWidth(2f);
        qyDataSet.setDrawCircles(false);

        qzDataSet = new LineDataSet(new ArrayList<>(), "Z");
        qzDataSet.setColor(0xFFFF9800); // Orange
        qzDataSet.setLineWidth(2f);
        qzDataSet.setDrawCircles(false);

        quaternionLineData = new LineData(qwDataSet, qxDataSet, qyDataSet, qzDataSet);
        quaternionChart.setData(quaternionLineData);
        setupChart(quaternionChart, -1f, 1f, "BNO055 Quaternions");
    }

    private void setupChart(LineChart chart, float minY, float maxY, String description) {
        chart.getDescription().setText(description);
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                return (index >= 0 && index < timestamps.size()) ? timestamps.get(index) : "";
            }
        });
        xAxis.setLabelRotationAngle(45f);
        xAxis.setLabelCount(5);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(minY);
        leftAxis.setAxisMaximum(maxY);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(true);
        chart.invalidate();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(String deviceAddress) {
        if (deviceAddress == null) {
            appendToTerminal("No device address provided.");
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        appendToTerminal("Connecting to " + device.getName() + "...");

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected.set(true);
                    appendToTerminal("Connected");
                    gatt.discoverServices();
                    gatt.requestMtu(247);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected.set(false);
                    appendToTerminal("Disconnected");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService uartService = gatt.getService(UART_SERVICE_UUID);
                    if (uartService != null) {
                        setupCharacteristics(uartService);
                    } else {
                        appendToTerminal("UART service not found");
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    String received = new String(data).trim();
                    Log.d(TAG, "Raw data received: " + received);
                    if (!received.isEmpty()) {
                        appendToTerminal(received);
                    }
                }
            }
        });
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void setupCharacteristics(BluetoothGattService uartService) {
        rxCharacteristic = uartService.getCharacteristic(RX_CHARACTERISTIC_UUID);
        BluetoothGattCharacteristic txCharacteristic = uartService.getCharacteristic(TX_CHARACTERISTIC_UUID);

        if (txCharacteristic != null) {
            bluetoothGatt.setCharacteristicNotification(txCharacteristic, true);
            BluetoothGattDescriptor descriptor = txCharacteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(descriptor);
            }
        }
        appendToTerminal("Ready for communication");
    }

    private void appendToTerminal(String message) {
        uiHandler.post(() -> {
            String timestampedMessage = timeFormat.format(new Date()) + " " + message + "\n";
            tvTerminal.append(timestampedMessage);
            svTerminal.fullScroll(View.FOCUS_DOWN);

            if (!message.startsWith("Sent:") && !message.contains("Connecting") && !message.contains("Connected") &&
                    !message.contains("Disconnected") && !message.contains("UART") && !message.contains("Ready") &&
                    !message.contains("Avatar")) {
                try {
                    boolean dataFound = false;

                    Matcher eulerMatcher = eulerPattern.matcher(message);
                    if (eulerMatcher.find()) {
                        dataFound = true;
                        float yaw = Float.parseFloat(eulerMatcher.group(1));
                        float pitch = Float.parseFloat(eulerMatcher.group(2));
                        float roll = Float.parseFloat(eulerMatcher.group(3));

                        Log.d(TAG, "Parsed Euler: yaw=" + yaw + ", pitch=" + pitch + ", roll=" + roll);

                        currentYaw = yaw;
                        currentPitch = pitch;
                        currentRoll = roll;

                        yawValues.add(yaw);
                        pitchValues.add(pitch);
                        rollValues.add(roll);
                        timestamps.add(timeFormat.format(new Date()));

                        yawDataSet.addEntry(new Entry(dataPointCounter, yaw));
                        pitchDataSet.addEntry(new Entry(dataPointCounter, pitch));
                        rollDataSet.addEntry(new Entry(dataPointCounter, roll));

                        if (yawDataSet.getEntryCount() > 50) {
                            yawDataSet.removeFirst();
                            pitchDataSet.removeFirst();
                            rollDataSet.removeFirst();
                            yawValues.remove(0);
                            pitchValues.remove(0);
                            rollValues.remove(0);
                            timestamps.remove(0);

                            for (int i = 0; i < yawDataSet.getEntryCount(); i++) {
                                yawDataSet.getEntryForIndex(i).setX(i);
                                pitchDataSet.getEntryForIndex(i).setX(i);
                                rollDataSet.getEntryForIndex(i).setX(i);
                            }
                        }

                        eulerLineData.notifyDataChanged();
                        eulerChart.notifyDataSetChanged();
                        eulerChart.invalidate();
                    }

                    Matcher quatMatcher = quaternionPattern.matcher(message);
                    if (quatMatcher.find()) {
                        dataFound = true;
                        float qw = Float.parseFloat(quatMatcher.group(1));
                        float qx = Float.parseFloat(quatMatcher.group(2));
                        float qy = Float.parseFloat(quatMatcher.group(3));
                        float qz = Float.parseFloat(quatMatcher.group(4));

                        Log.d(TAG, "Parsed Quaternion: qw=" + qw + ", qx=" + qx + ", qy=" + qy + ", qz=" + qz);

                        currentQw = qw;
                        currentQx = qx;
                        currentQy = qy;
                        currentQz = qz;

                        qwValues.add(qw);
                        qxValues.add(qx);
                        qyValues.add(qy);
                        qzValues.add(qz);
                        if (!timestamps.contains(timeFormat.format(new Date()))) {
                            timestamps.add(timeFormat.format(new Date()));
                        }

                        qwDataSet.addEntry(new Entry(dataPointCounter, qw));
                        qxDataSet.addEntry(new Entry(dataPointCounter, qx));
                        qyDataSet.addEntry(new Entry(dataPointCounter, qy));
                        qzDataSet.addEntry(new Entry(dataPointCounter, qz));

                        if (qwDataSet.getEntryCount() > 50) {
                            qwDataSet.removeFirst();
                            qxDataSet.removeFirst();
                            qyDataSet.removeFirst();
                            qzDataSet.removeFirst();
                            qwValues.remove(0);
                            qxValues.remove(0);
                            qyValues.remove(0);
                            qzValues.remove(0);
                            if (timestamps.size() > qwDataSet.getEntryCount()) {
                                timestamps.remove(0);
                            }

                            for (int i = 0; i < qwDataSet.getEntryCount(); i++) {
                                qwDataSet.getEntryForIndex(i).setX(i);
                                qxDataSet.getEntryForIndex(i).setX(i);
                                qyDataSet.getEntryForIndex(i).setX(i);
                                qzDataSet.getEntryForIndex(i).setX(i);
                            }
                        }

                        quaternionLineData.notifyDataChanged();
                        quaternionChart.notifyDataSetChanged();
                        quaternionChart.invalidate();
                    }

                    if (dataFound) {
                        dataPointCounter++;
                        updateAvatar();
                    } else {
                        Log.d(TAG, "No Euler or Quaternion data found in message: " + message);
                    }

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid data format: " + message, e);
                }
            }
        });
    }

    private void updateAvatar() {
        if (avatarView != null && isAvatarVisible) {
            avatarView.updateSensorData(currentYaw, currentPitch, currentRoll,
                    currentQw, currentQx, currentQy, currentQz);
        }
    }

    private void resetAvatar() {
        if (avatarView != null) {
            avatarView.resetAvatar();
            currentYaw = 0f;
            currentPitch = 0f;
            currentRoll = 0f;
            currentQw = 1f;
            currentQx = 0f;
            currentQy = 0f;
            currentQz = 0f;
            appendToTerminal("Avatar reset to neutral position");
        }
    }

    private void toggleAvatar() {
        isAvatarVisible = !isAvatarVisible;
        avatarContainer.setVisibility(isAvatarVisible ? View.VISIBLE : View.GONE);
        btnToggleAvatar.setText(isAvatarVisible ? "Hide Avatar" : "Show Avatar");
        appendToTerminal("Avatar " + (isAvatarVisible ? "shown" : "hidden"));
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void executeMacro() {
        if (!isConnected.get()) {
            appendToTerminal("Not connected to device");
            return;
        }

        String name = etMacroName.getText().toString().trim();
        String value = etMacroValue.getText().toString().trim();

        if (name.isEmpty() || value.isEmpty()) {
            appendToTerminal("Macro name and value required");
            return;
        }

        boolean isHexMode = rgEditMode.getCheckedRadioButtonId() == R.id.rbHex;
        try {
            byte[] data = isHexMode ? hexStringToByteArray(value.replaceAll(" ", "")) : value.getBytes();
            rxCharacteristic.setValue(data);
            rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            bluetoothGatt.writeCharacteristic(rxCharacteristic);
            appendToTerminal("Sent: " + name + " (" + value + ")");
        } catch (Exception e) {
            appendToTerminal("Error sending macro: " + e.getMessage());
            Log.e(TAG, "Macro send error", e);
        }
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[(len + 1) / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) +
                    (i + 1 < len ? Character.digit(s.charAt(i + 1), 16) : 0));
        }
        return data;
    }

    public void goToBPMActivity(View view) {
        if (bluetoothGatt != null && bluetoothGatt.getDevice() != null) {
            Intent intent = new Intent(this, BPMActivity.class);
            intent.putExtra("device_address", bluetoothGatt.getDevice().getAddress());
            startActivity(intent);
            appendToTerminal("Navigating to BPM Activity");
        } else {
            appendToTerminal("No active Bluetooth connection");
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
        uiHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            btnExecute.setOnClickListener(v -> executeMacro());
            btnBPM.setOnClickListener(this::goToBPMActivity);
            btnResetAvatar.setOnClickListener(v -> resetAvatar());
            btnToggleAvatar.setOnClickListener(v -> toggleAvatar());
        } else {
            appendToTerminal("Bluetooth permission denied");
        }
    }
}