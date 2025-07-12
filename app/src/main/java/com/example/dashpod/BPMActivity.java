package com.example.dashpod;

import android.Manifest;
import android.bluetooth.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BPMActivity extends AppCompatActivity {

    private static final String TAG = "BPMActivity";
    private static final int DATA_POINT_INTERVAL = 10000; // 10 seconds between points
    private static final int TOTAL_DURATION = 120000; // 2 minutes (120 seconds)
    private static final int MAX_DATA_POINTS = 12; // 120s / 10s = 12 points

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private EditText etMacroName, etMacroValue;
    private RadioGroup rgEditMode, rgAction;
    private Button btnExecute;
    private Spinner spMacros;
    private LineChart bpmChart;
    private TextView tvTerminal, tvAverageBpm, tvMinute1Avg, tvMinute2Avg, tvBpmRanges;
    private ScrollView svTerminal;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private LineDataSet bpmDataSet;
    private LineData lineData;
    private List<Entry> bpmEntries = new ArrayList<>();
    private long startTime = 0;
    private boolean isBPMStarted = false;
    private int dataPointCount = 0;
    private List<Float> allBpmValues = new ArrayList<>();
    private List<Float> firstMinuteBpmValues = new ArrayList<>();
    private List<Float> secondMinuteBpmValues = new ArrayList<>();

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendToTerminal("Connected to GATT server");
                gatt.discoverServices();
                isConnected.set(true);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendToTerminal("Disconnected from GATT server");
                isConnected.set(false);
                isBPMStarted = false;
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                appendToTerminal("Services discovered");
                setupCharacteristics();
            } else {
                appendToTerminal("Service discovery failed");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                String received = new String(data).trim();
                if (!received.isEmpty()) {
                    appendToTerminal(received);
                    if (isBPMStarted) {
                        try {
                            float bpm = parseBPMFromData(received);
                            if (bpm >= 0) {
                                long currentTime = System.currentTimeMillis();
                                float secondsSinceStart = (currentTime - startTime) / 1000f;

                                // Store all BPM values for average calculation
                                allBpmValues.add(bpm);

                                // Store values for minute averages
                                if (secondsSinceStart <= 60) {
                                    firstMinuteBpmValues.add(bpm);
                                } else {
                                    secondMinuteBpmValues.add(bpm);
                                }

                                updateAverageBpm();

                                // Only plot at exact 10-second intervals starting from 10s (10, 20, ..., 120)
                                if (secondsSinceStart >= (dataPointCount + 1) * 10 && dataPointCount < MAX_DATA_POINTS) {
                                    synchronized (bpmEntries) {
                                        bpmEntries.add(new Entry((dataPointCount + 1) * 10f, bpm));
                                        dataPointCount++;

                                        uiHandler.post(() -> updateChartDisplay());
                                    }
                                }

                                if (secondsSinceStart >= TOTAL_DURATION / 1000f) {
                                    isBPMStarted = false;
                                    appendToTerminal("BPM data collection completed (2 minutes elapsed)");
                                    calculateFinalAverages();
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid data format: " + received, e);
                        }
                    }
                }
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bpm);

        initializeUI();
        initializeChart();

        String deviceAddress = getIntent().getStringExtra("device_address");
        if (deviceAddress != null) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (device != null) {
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
                appendToTerminal("Connecting to device...");
            } else {
                appendToTerminal("Device not found");
            }
        }
    }

    private void initializeUI() {
        etMacroName = findViewById(R.id.etMacroName);
        etMacroValue = findViewById(R.id.etMacroValue);
        rgEditMode = findViewById(R.id.rgEditMode);
        rgAction = findViewById(R.id.rgAction);
        btnExecute = findViewById(R.id.btnExecute);
        spMacros = findViewById(R.id.spMacros);
        bpmChart = findViewById(R.id.eulerChart);
        tvTerminal = findViewById(R.id.tvTerminal);
        svTerminal = findViewById(R.id.svTerminal);
        tvAverageBpm = findViewById(R.id.tvAverageBpm);
        tvMinute1Avg = findViewById(R.id.tvMinute1Avg);
        tvMinute2Avg = findViewById(R.id.tvMinute2Avg);
        tvBpmRanges = findViewById(R.id.tvBpmRanges);

        // Set BPM ranges text
        String bpmRanges = "Normal Breathing Rates (BPM):\n" +
                "Newborns (0–1 month): 30–60\n" +
                "Infants (1–12 months): 30–50\n" +
                "Toddlers (1–3 years): 24–40\n" +
                "Preschoolers (3–5 years): 22–34\n" +
                "Children (6–12 years): 18–30\n" +
                "Teenagers (13–18 years): 12–20\n" +
                "Adults (>18 years): 12–20";
        tvBpmRanges.setText(bpmRanges);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.bpm_macros_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMacros.setAdapter(adapter);

        spMacros.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                switch (selected) {
                    case "BPM Start":
                        etMacroName.setText("BPM Start");
                        etMacroValue.setText("01 0A");
                        break;
                    case "BPM Stop":
                        etMacroName.setText("BPM Stop");
                        etMacroValue.setText("03");
                        break;
                    default:
                        etMacroName.setText("");
                        etMacroValue.setText("");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                etMacroName.setText("");
                etMacroValue.setText("");
            }
        });

        btnExecute.setOnClickListener(v -> executeMacro());
    }

    private void initializeChart() {
        bpmDataSet = new LineDataSet(new ArrayList<>(), "Breathing Rate (BPM)");
        bpmDataSet.setColor(0xFFCD5C5C);
        bpmDataSet.setCircleColor(0xFF4682B4);
        bpmDataSet.setLineWidth(2f);
        bpmDataSet.setCircleRadius(4f);
        bpmDataSet.setDrawCircles(true);
        bpmDataSet.setDrawValues(true);
        bpmDataSet.setMode(LineDataSet.Mode.LINEAR);
        bpmDataSet.setDrawFilled(false);

        lineData = new LineData(bpmDataSet);
        bpmChart.setData(lineData);

        Description description = new Description();
        description.setText("Breathing Rate (10s intervals)");
        bpmChart.setDescription(description);

        XAxis xAxis = bpmChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%ds", (int) value);
            }
        });
        xAxis.setLabelCount(13, true);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(120f);
        xAxis.setGranularity(10f);

        YAxis leftAxis = bpmChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(60f);
        leftAxis.setLabelCount(7, true);
        bpmChart.getAxisRight().setEnabled(false);

        bpmChart.setTouchEnabled(true);
        bpmChart.setDragEnabled(true);
        bpmChart.setScaleEnabled(true);
        bpmChart.setPinchZoom(true);
        bpmChart.setAutoScaleMinMaxEnabled(true);
        bpmChart.invalidate();
    }

    private void resetActivity() {
        // Clear all data collections
        synchronized (bpmEntries) {
            bpmEntries.clear();
        }
        allBpmValues.clear();
        firstMinuteBpmValues.clear();
        secondMinuteBpmValues.clear();
        dataPointCount = 0;
        startTime = 0;
        isBPMStarted = false;

        // Reset UI elements
        tvAverageBpm.setText("Current Avg BPM: 0.0");
        tvMinute1Avg.setText("1st Min Avg: 0.0 BPM");
        tvMinute2Avg.setText("2nd Min Avg: 0.0 BPM");

        // Reinitialize chart to clear and reset
        initializeChart();
    }

    private void updateChartDisplay() {
        synchronized (bpmEntries) {
            bpmDataSet.setValues(bpmEntries);
            lineData.notifyDataChanged();
            bpmChart.notifyDataSetChanged();
            bpmChart.invalidate();
        }
    }

    private void updateAverageBpm() {
        if (allBpmValues.isEmpty()) return;

        float sum = 0;
        for (float bpm : allBpmValues) {
            sum += bpm;
        }
        float average = sum / allBpmValues.size();

        uiHandler.post(() -> {
            tvAverageBpm.setText(String.format(Locale.getDefault(), "Current Avg BPM: %.1f", average));
        });
    }

    private void calculateFinalAverages() {
        float minute1Avg = calculateAverage(firstMinuteBpmValues);
        float minute2Avg = calculateAverage(secondMinuteBpmValues);
        float overallAvg = calculateAverage(allBpmValues);

        uiHandler.post(() -> {
            tvMinute1Avg.setText(String.format(Locale.getDefault(), "1st Min Avg: %.1f BPM", minute1Avg));
            tvMinute2Avg.setText(String.format(Locale.getDefault(), "2nd Min Avg: %.1f BPM", minute2Avg));
            tvAverageBpm.setText(String.format(Locale.getDefault(), "Overall Avg: %.1f BPM", overallAvg));
        });
    }

    private float calculateAverage(List<Float> values) {
        if (values == null || values.isEmpty()) return 0f;

        float sum = 0;
        for (float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void setupCharacteristics() {
        if (bluetoothGatt != null) {
            for (BluetoothGattService service : bluetoothGatt.getServices()) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        rxCharacteristic = characteristic;
                    }
                    if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        bluetoothGatt.setCharacteristicNotification(characteristic, true);
                        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            bluetoothGatt.writeDescriptor(descriptor);
                        }
                    }
                }
            }
        }
    }

    private void appendToTerminal(String message) {
        uiHandler.post(() -> {
            tvTerminal.append(timeFormat.format(new Date()) + " " + message + "\n");
            svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
        });
    }

    private float parseBPMFromData(String data) {
        if (data.toLowerCase().contains("bpm")) {
            String[] parts = data.split("bpm");
            if (parts.length > 0) {
                try {
                    String bpmStr = parts[0].replaceAll("[^0-9.]", "").trim();
                    return Float.parseFloat(bpmStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid BPM format: " + data, e);
                }
            }
        }
        return -1f;
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

        // Check if Send is selected (though it's the only option, kept for consistency)
        if (rgAction.getCheckedRadioButtonId() == R.id.rbSend) {
            try {
                byte[] data = hexStringToByteArray(value.replaceAll(" ", ""));
                if (rxCharacteristic != null) {
                    rxCharacteristic.setValue(data);
                    rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    bluetoothGatt.writeCharacteristic(rxCharacteristic);
                    appendToTerminal("Sent: " + name + " (" + value + ")");

                    if (name.equalsIgnoreCase("BPM Start")) {
                        // Reset activity if previously stopped or completed
                        if (!isBPMStarted) {
                            resetActivity();
                            isBPMStarted = true;
                            startTime = System.currentTimeMillis();
                            appendToTerminal("BPM data collection started - plotting every 10 seconds");
                        }
                    } else if (name.equalsIgnoreCase("BPM Stop")) {
                        isBPMStarted = false;
                        appendToTerminal("BPM data collection stopped");
                        // Calculate final averages to display current state
                        calculateFinalAverages();
                        // Do not clear the graph or reset data (Case 2)
                    }
                } else {
                    appendToTerminal("No write characteristic available");
                }
            } catch (Exception e) {
                appendToTerminal("Error sending macro: " + e.getMessage());
                Log.e(TAG, "Macro send error", e);
            }
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