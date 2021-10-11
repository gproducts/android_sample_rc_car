/*
MIT License

Copyright (c) 2021 G.Products

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

// btSPP
// 2020/04/30 ver3.0

package com.example.android_bluetooth_rc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class btSPP {

    private final static String TAG = "BT";

    private boolean intRx;
    private List<Byte> wData;  //CopyOnWriteArrayList<>();
    private List<Byte> rData;  //CopyOnWriteArrayList<>();

    private static BluetoothSocket mBTSocket;

    private static boolean threadEN;
    private static boolean threadStatus;
    private static boolean connectEN;
    private static final int readBufferSize = 4096;

    public btSPP() {
        btInit();
    }

    private void startThread() {
        threadEN = true;
        threadStatus = true;
        new Thread(() -> {
            Log.d(TAG, "Started RxTx thread");
            do {
                rxLoop();   // synchronized
                txLoop();   // synchronized
                try {
                    Thread.sleep(5); // wait 5ms
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (threadEN);
            threadStatus = false;
            Log.d(TAG, "Finished RxTx thread");
        }).start();
    }

    private void btInit() {
        Log.i(TAG, "btInit: ");
        wData = new CopyOnWriteArrayList<>();
        rData = new CopyOnWriteArrayList<>();
        rData.clear();
        wData.clear();

        intRx = false;
        threadEN = false;
        connectEN = false;
        mBTSocket = null;
    }

    private synchronized void rxLoop() {
        String sBuf;
        if ((mBTSocket != null) && (connectEN)) {
            if (!intRx) {
                readFunc(this.rData);
                if (this.rData.size() > 0) {
                    intRx = true;
                    sBuf = String.valueOf(this.rData.size());
                    Log.d(TAG, "rxLoop: Rx Data: size=" + sBuf);
                }
            }
        }
    }

    private synchronized void txLoop() {
        String sBuf;
        if ((mBTSocket != null) && (connectEN)) {
            if (this.wData.size() > 0) {
                sBuf = String.valueOf(this.wData.size());
                Log.d(TAG, "txLoop: Tx Data: size=" + sBuf);
                writeFunc(this.wData);
                wData.clear();
            }
        }
    }

    private String findAddress(String deviceName) {
        Log.i(TAG, "findAddress: ");
        String mAdd = "";
        BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (BTAdapter != null) {
            Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (deviceName.equals(device.getName())) {
                        mAdd = device.getAddress();
                        Log.i(TAG, "findAddress: mAdd=" + mAdd);
                    }
                }
            }
        }
        if (mAdd.equals("")) {
            Log.e(TAG, "findAddress: Error");
        }
        return mAdd;
    }

    public void getDeviceName(List<String> deviceList) {
        Log.i(TAG, "getDeviceName: ");
        BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (BTAdapter != null) {
            Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    deviceList.add(device.getName());
                }
            }
        }
        Log.i(TAG, deviceList.toString());
    }

    private void writeFunc(List<Byte> data) {
        int i;
        byte[] bData = new byte[data.size()];

        for (i = 0; i < data.size(); i++) {
            bData[i] = data.get(i);
        }
        if (mBTSocket != null) {
            try {
                OutputStream out = mBTSocket.getOutputStream();
                out.write(bData);

            } catch (IOException e) {
                btInit();
                Log.e(TAG, "writeFunc: Error");
            }
        } else {
            btInit();
            Log.e(TAG, "writeFunc: mBTSocket == null");
        }
    }

    private void readFunc(List<Byte> data) {
        int i, size;
        byte[] bData = new byte[readBufferSize];
        int available;

        if (mBTSocket != null) {
            try {
                InputStream in = mBTSocket.getInputStream();
                available = in.available();
                if (available > 0) {
                    size = in.read(bData);
                    data.clear();
                    for (i = 0; i < size; i++) {
                        data.add(bData[i]);
                    }
                }
            } catch (IOException e) {
                btInit();
                Log.e(TAG, "readFunc: Error");
            }
        } else {
            btInit();
            Log.e(TAG, "readFunc: mBTSocket == null");
        }
    }

    public boolean connect(String deviceName) {
        Log.i(TAG, "connect: "+ deviceName);

        // Initialize
        this.close();

        String macAddress = "";
        macAddress = findAddress(deviceName);
        if (macAddress.isEmpty()) {
            Log.e(TAG, "connect: macAddress");
            return true;
        }

        // Get instance of BT device
        String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"; // select SPP
        BluetoothAdapter mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice mBTDevice = mBTAdapter.getRemoteDevice(macAddress);

        // Setting for socket
        try {
            mBTSocket = mBTDevice.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
            Log.d(TAG, "connect: mBTDevice.createRfcommSocketToServiceRecord()");
        } catch (IOException e) {
            btInit();
            Log.e(TAG, "connect: mBTSocket2");
            return true;
        }

        // Connect
        try {
            mBTSocket.connect();
            Log.d(TAG, "connect: mBTSocket.connect()");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!isOpened()) {
            Log.e(TAG, "connect: Couldn't open SPP");
            return true;
        }

        connectEN = true;
        startThread();

        Log.i(TAG, "connect: Opened SPP correctly");
        return false;
    }

    public boolean isOpened() {
        if (mBTSocket != null) {
            if (mBTSocket.isConnected())
                return true;
            else
                return false;
        } else {
            return false;
        }
    }

    public synchronized boolean write(List<Byte> data) {
        Log.d(TAG, "write: "+ logByte2Str(data));
        if (wData.size() == 0) {
            for (int i = 0; i < data.size(); i++) {
                wData.add(data.get(i));
            }
            return false;
        } else {
            Log.e(TAG, "write: Couldn't write");
            return true;
        }
    }

    public synchronized boolean write(String str) {
        byte[] bData = str.getBytes(StandardCharsets.UTF_8);
        List<Byte> data = new ArrayList<>();
        for (int i = 0; i < bData.length; i++) {
            data.add(bData[i]);
        }
        return this.write(data);
    }

    public synchronized boolean read(List<Byte> data) {
        if (intRx) {
            data.clear();
            for (int i = 0; i < rData.size(); i++) {
                data.add(rData.get(i));
            }
            Log.d(TAG, "read: " + logByte2Str(rData));
            rData.clear();
            intRx = false;
            return false;
        } else {
            Log.d(TAG, "read: no data");
            return true;
        }
    }

    public synchronized boolean read(StringBuilder str) {
        List<Byte> data = new ArrayList<>();
        if (this.read(data)) {
            return true;
        }
        byte[] bData = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            bData[i] = data.get(i);
        }
        String sBuf = new String(bData);
        str.append(sBuf);
        return false;
    }

    public boolean close() {
        int loop;
        Log.i(TAG, "close:");

        // waiting for finishing the thread.
        this.threadEN = false;
        loop = 0;
        do {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (loop++ > 50) {
                Log.e(TAG, "close: Error Timeout1");
                break;
            }
        } while (threadStatus);

        loop = 0;
        if (isOpened()) {

            try {
                mBTSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            do {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (loop++ > 50) {
                    Log.e(TAG, "close: Error Timeout2");
                    break;
                }
            } while (isOpened());
            Log.d(TAG, "close: closed BT port");
        }
        btInit();

        Log.i(TAG, "close: done");
        return false;
    }

    private String logByte2Str(List<Byte> data) {
        List<String> buf = new ArrayList<>();
        String sBuf;
        byte b;
        sBuf = String.valueOf(data.size());
        buf.add(("size=" + sBuf));
        for (int i = 0; i < data.size(); i++) {
            b = data.get(i);
            sBuf = String.valueOf((char) b);
            buf.add("0x" + String.format("%02X", data.get(i)) + ":" + sBuf);
        }
        return buf.toString();
    }
}
