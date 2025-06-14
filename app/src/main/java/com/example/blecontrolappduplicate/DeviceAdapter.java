package com.example.blecontrolappduplicate;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.List;

public class DeviceAdapter extends BaseAdapter {

    private final Context context;
    private final List<BluetoothDevice> devices;

    public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
        this.context = context;
        this.devices = devices;
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("MissingPermission")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BluetoothDevice device = devices.get(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.device_item, parent, false);
        }

        TextView name = convertView.findViewById(R.id.deviceName);
        TextView address = convertView.findViewById(R.id.deviceAddress);

        name.setText(device.getName() != null ? device.getName() : "Unknown");
        address.setText(device.getAddress());

        return convertView;
    }
}