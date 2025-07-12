package com.example.dashpod;

import android.Manifest;
import android.bluetooth.*;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.ValueFormatter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceActivity extends AppCompatActivity {

    private static final String TAG = "DeviceActivity";
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private TextView tvTerminal;
    private ScrollView svTerminal;
    private EditText etMacroName, etMacroValue;
    private RadioGroup rgEditMode, rgAction;
    private Button btnExecute, btnBPM;
    private LineChart eulerChart, quaternionChart;
    private LineDataSet yawDataSet, pitchDataSet, rollDataSet;
    private LineDataSet qwDataSet, qxDataSet, qyDataSet, qzDataSet;
    private LineData eulerLineData, quaternionLineData;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private List<Float> yawValues = new ArrayList<>();
    private List<Float> pitchValues = new ArrayList<>();
    private List<Float> rollValues = new ArrayList<>();
    private List<Float> qwValues = new ArrayList<>();
    private List<Float> qxValues = new ArrayList<>();
    private List<Float> qyValues = new ArrayList<>();
    private List<Float> qzValues = new ArrayList<>();
    private List<String> timestamps = new ArrayList<>();
    private int dataPointCounter = 0;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        initializeUI();
        initializeCharts();
        connectToDevice(getIntent().getStringExtra("device_address"));
    }

    private void initializeUI() {
        tvTerminal = findViewById(R.id.tvTerminal);
        svTerminal = findViewById(R.id.svTerminal);
        etMacroName = findViewById(R.id.etMacroName);
        etMacroValue = findViewById(R.id.etMacroValue);
        rgEditMode = findViewById(R.id.rgEditMode);
        rgAction = findViewById(R.id.rgAction);
        btnExecute = findViewById(R.id.btnExecute);
        btnBPM = findViewById(R.id.btnBPM); // Initialize BPM button
        eulerChart = findViewById(R.id.eulerChart);
        quaternionChart = findViewById(R.id.quaternionChart);

        Spinner spMacros = findViewById(R.id.spMacros);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.device_macros_array, android.R.layout.simple_spinner_item);
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

        btnExecute.setOnClickListener(v -> executeMacro());
        btnBPM.setOnClickListener(v -> goToBPMActivity(v)); // Pass the View parameter
    }

    private void initializeCharts() {
        // Euler Angles Chart
        List<Entry> yawEntries = new ArrayList<>();
        List<Entry> pitchEntries = new ArrayList<>();
        List<Entry> rollEntries = new ArrayList<>();

        yawDataSet = new LineDataSet(yawEntries, "Yaw");
        yawDataSet.setColor(0xFF2196F3); // Blue
        yawDataSet.setLineWidth(2f);
        yawDataSet.setDrawCircles(false);

        pitchDataSet = new LineDataSet(pitchEntries, "Pitch");
        pitchDataSet.setColor(0xFF4CAF50); // Green
        pitchDataSet.setLineWidth(2f);
        pitchDataSet.setDrawCircles(false);

        rollDataSet = new LineDataSet(rollEntries, "Roll");
        rollDataSet.setColor(0xFFCD5C5C); // Indian Red
        rollDataSet.setLineWidth(2f);
        rollDataSet.setDrawCircles(false);

        eulerLineData = new LineData(yawDataSet, pitchDataSet, rollDataSet);
        eulerChart.setData(eulerLineData);

        eulerChart.getDescription().setText("BNO055 Euler Angles");
        XAxis eulerXAxis = eulerChart.getXAxis();
        eulerXAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                return (index >= 0 && index < timestamps.size()) ? timestamps.get(index) : "";
            }
        });
        eulerXAxis.setLabelRotationAngle(45f);
        eulerXAxis.setLabelCount(5);
        eulerXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis eulerLeftAxis = eulerChart.getAxisLeft();
        eulerLeftAxis.setAxisMinimum(-360f);
        eulerLeftAxis.setAxisMaximum(360f);
        eulerChart.getAxisRight().setEnabled(false);
        eulerChart.getLegend().setEnabled(true);
        eulerChart.setTouchEnabled(true);
        eulerChart.setPinchZoom(true);
        eulerChart.invalidate();

        // Quaternion Chart
        List<Entry> qwEntries = new ArrayList<>();
        List<Entry> qxEntries = new ArrayList<>();
        List<Entry> qyEntries = new ArrayList<>();
        List<Entry> qzEntries = new ArrayList<>();

        qwDataSet = new LineDataSet(qwEntries, "W");
        qwDataSet.setColor(0xFF2196F3); // Blue
        qwDataSet.setLineWidth(2f);
        qwDataSet.setDrawCircles(false);

        qxDataSet = new LineDataSet(qxEntries, "X");
        qxDataSet.setColor(0xFF4CAF50); // Green
        qxDataSet.setLineWidth(2f);
        qxDataSet.setDrawCircles(false);

        qyDataSet = new LineDataSet(qyEntries, "Y");
        qyDataSet.setColor(0xFFCD5C5C); // Indian Red
        qyDataSet.setLineWidth(2f);
        qyDataSet.setDrawCircles(false);

        qzDataSet = new LineDataSet(qzEntries, "Z");
        qzDataSet.setColor(0xFFFF9800); // Orange
        qzDataSet.setLineWidth(2f);
        qzDataSet.setDrawCircles(false);

        quaternionLineData = new LineData(qwDataSet, qxDataSet, qyDataSet, qzDataSet);
        quaternionChart.setData(quaternionLineData);

        quaternionChart.getDescription().setText("BNO055 Quaternions");
        XAxis quatXAxis = quaternionChart.getXAxis();
        quatXAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                return (index >= 0 && index < timestamps.size()) ? timestamps.get(index) : "";
            }
        });
        quatXAxis.setLabelRotationAngle(45f);
        quatXAxis.setLabelCount(5);
        quatXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis quatLeftAxis = quaternionChart.getAxisLeft();
        quatLeftAxis.setAxisMinimum(-1f);
        quatLeftAxis.setAxisMaximum(1f);
        quaternionChart.getAxisRight().setEnabled(false);
        quaternionChart.getLegend().setEnabled(true);
        quaternionChart.setTouchEnabled(true);
        quaternionChart.setPinchZoom(true);
        quaternionChart.invalidate();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(String deviceAddress) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        appendToTerminal(timeFormat.format(new Date()) + " Connecting to " + device.getName() + "...");

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected.set(true);
                    appendToTerminal(timeFormat.format(new Date()) + " Connected");
                    gatt.discoverServices();
                    gatt.requestMtu(247);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected.set(false);
                    appendToTerminal(timeFormat.format(new Date()) + " Disconnected");
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
            tvTerminal.append(timeFormat.format(new Date()) + " " + message + "\n");
            svTerminal.fullScroll(View.FOCUS_DOWN);

            if (!message.startsWith("Sent:") && !message.contains("Connecting") && !message.contains("Connected") &&
                    !message.contains("Disconnected") && !message.contains("UART") && !message.contains("Ready") &&
                    !message.startsWith("^")) {
                try {
                    boolean dataFound = false;

                    // More robust regex for Euler Angles
                    Pattern eulerPattern = Pattern.compile("\\{\"EX\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"EY\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"EZ\"\\s*:\\s*([-]?\\d*\\.?\\d+)\\}");
                    Matcher eulerMatcher = eulerPattern.matcher(message);

                    if (eulerMatcher.find()) {
                        dataFound = true;
                        float yaw = Float.parseFloat(eulerMatcher.group(1));
                        float pitch = Float.parseFloat(eulerMatcher.group(2));
                        float roll = Float.parseFloat(eulerMatcher.group(3));

                        Log.d(TAG, "Parsed Euler: yaw=" + yaw + ", pitch=" + pitch + ", roll=" + roll);

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

                    // More robust regex for Quaternions
                    Pattern quatPattern = Pattern.compile("\\{\"QW\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"QX\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"QY\"\\s*:\\s*([-]?\\d*\\.?\\d+),\\s*\"QZ\"\\s*:\\s*([-]?\\d*\\.?\\d+)\\}");
                    Matcher quatMatcher = quatPattern.matcher(message);

                    if (quatMatcher.find()) {
                        dataFound = true;
                        float qw = Float.parseFloat(quatMatcher.group(1));
                        float qx = Float.parseFloat(quatMatcher.group(2));
                        float qy = Float.parseFloat(quatMatcher.group(3));
                        float qz = Float.parseFloat(quatMatcher.group(4));

                        Log.d(TAG, "Parsed Quaternion: qw=" + qw + ", qx=" + qx + ", qy=" + qy + ", qz=" + qz);

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
                            timestamps.remove(0);

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
                    } else {
                        Log.d(TAG, "No Euler or Quaternion data found in message: " + message);
                    }

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid data format: " + message, e);
                }
            }
        });
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
            appendToTerminal(timeFormat.format(new Date()) + " Navigating to BPM Activity");
        } else {
            appendToTerminal(timeFormat.format(new Date()) + " No active Bluetooth connection");
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
}