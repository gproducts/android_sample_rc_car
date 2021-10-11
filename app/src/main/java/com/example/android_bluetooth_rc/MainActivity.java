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

package com.example.android_bluetooth_rc;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.Math.abs;

public class MainActivity extends AppCompatActivity {

    private TextView textView1;
    private btSPP sp;

    private SeekBar seekBar1;
    private int speedmax;

    private boolean threadEN;
    private boolean gmodeEN;

    private List<Byte> sendData;

    private SensorManager manager;
    private SensorEventListener listener;
    private List<Sensor> list;

    private double xval; // left(+100%) ~ 0 ~ right(-100%)
    private double yval; // front(+100%) ~ 0 ~ rear(-100%)
    private double xmax = 5;    // ~9.8
    private double ymax = 5;    // ~9.8

    private List<Double> xave;
    private List<Double> yave;
    private int aveNumber = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sendData = new CopyOnWriteArrayList<>();

        textView1 = findViewById(R.id.textView1);
        Button button_connect = findViewById(R.id.button_connect);
        Button button_close = findViewById(R.id.button_close);
        Button button_mode = findViewById(R.id.button_mode);
        Button button_up = findViewById(R.id.button_up);
        Button button_down = findViewById(R.id.button_down);
        Button button_left = findViewById(R.id.button_left);
        Button button_right = findViewById(R.id.button_right);
        Button button_stop = findViewById(R.id.button_stop);

        speedmax = 40;

        // SeekBar
        seekBar1 = findViewById(R.id.seekBar1);
        seekBar1.setProgress(speedmax);
        seekBar1.setMax(90);

        threadEN = false;
        gmodeEN = false;

        sp = new btSPP();

        xave = new CopyOnWriteArrayList<>();
        yave = new CopyOnWriteArrayList<>();

        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        list = manager.getSensorList(Sensor.TYPE_ACCELEROMETER);

        listener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                String str;
                int i;
                if (gmodeEN) {

                    double x = event.values[0];
                    if (x > 0) {
                        // left = 9.8
                        if (x > xmax) x = xmax;
                        x = 100 * x / xmax;
                    } else {
                        // right
                        if (x < (-1 * xmax)) x = -1 * xmax;
                        x = 100 * x / xmax;
                    }

                    // averaging
                    if (xave.size() > aveNumber) xave.remove(0);
                    xave.add(x);
                    x = 0;
                    for (i = 0; i < xave.size(); i++) {
                        x = x + xave.get(i);
                    }
                    xval = x / xave.size();


                    double y = event.values[1];
                    if (y > 0) {
                        // rear
                        if (y > ymax) y = ymax;
                        y = -100 * y / ymax;
                    } else {
                        // front
                        if (y < (-1 * ymax)) y = -1 * ymax;
                        y = -100 * y / ymax;
                    }
                    // averaging
                    if (yave.size() > aveNumber) yave.remove(0);
                    yave.add(y);
                    y = 0;
                    for (i = 0; i < yave.size(); i++) {
                        y = y + yave.get(i);
                    }
                    yval = y / yave.size();

                    str = "x: " + String.format("%.2f", xval);
                    str = str + ", y: " + String.format("%.2f", yval);

                    textView1.setText(str);
                }
            }

            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        seekBar1.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar, int progress, boolean fromUser) {
                        textView1.setText(String.valueOf(progress));
                        speedmax = progress;
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // When the bar is started tracking.
                    }

                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // When the bar is released.
                    }
                });

        button_connect.setOnClickListener(v -> {
            Log.d("BT", "Button_connect");
            if (sp.connect("bt_uart")) {
                textView1.setText("Error to connect.");
                threadEN = false;
            } else {
                textView1.setText("Success to connect !");
                threadEN = true;
                new Thread(() -> {
                    String dL, dR;
                    Log.d("BT", "Started thread");
                    do {

                        if (gmodeEN) {
                            calcMove();
                        }
                        // protocol
                        // header           0xff
                        // speed left       0~100
                        // speed right      0~100
                        // direction left   0,1:forward,2:back
                        // direction right  0,1:forward,2:back
                        if (sendData.size() == 5) {
                            dL = "*";
                            if (sendData.get(3) == 1)
                                dL = "F";
                            if (sendData.get(3) == 2)
                                dL = "B";
                            dR = "*";
                            if (sendData.get(4) == 1)
                                dR = "F";
                            if (sendData.get(4) == 2)
                                dR = "B";
                            Log.i("BT", "L:" + dL + toString().valueOf(sendData.get(2)) + " R:" + dR + toString().valueOf(sendData.get(1)));

                            sp.write(sendData);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } while (threadEN);

                    Log.d("BT", "Finished thread");
                }).start();
            }
        });

        button_close.setOnClickListener(v -> {
            Log.d("BT", "Button_close");
            if(sp.isOpened())
            {
                sp.close();
                threadEN = false;
                textView1.setText("Success to close.");
            }
        });

        button_stop.setOnClickListener(v -> {
            Log.i("BT", "Button_stop");
            List<Byte> bData = new CopyOnWriteArrayList<>();
            bData.add((byte) 0xff);
            bData.add((byte) 0);
            bData.add((byte) 0);
            bData.add((byte) 0);
            bData.add((byte) 0);
            sendData = bData;
        });

        button_up.setOnClickListener(v -> {
            Log.i("BT", "Button_up");
            if (gmodeEN) return;
            List<Byte> bData = new CopyOnWriteArrayList<>();
            bData.add((byte) 0xff);
            bData.add((byte) speedmax);
            bData.add((byte) speedmax);
            bData.add((byte) 1);
            bData.add((byte) 1);
            sendData = bData;
        });

        button_down.setOnClickListener(v -> {
            Log.i("BT", "Button_down");
            if (gmodeEN) return;
            List<Byte> bData = new CopyOnWriteArrayList<>();
            bData.add((byte) 0xff);
            bData.add((byte) speedmax);
            bData.add((byte) speedmax);
            bData.add((byte) 2);
            bData.add((byte) 2);
            sendData = bData;
        });

        button_left.setOnClickListener(v -> {
            Log.i("BT", "Button_left");
            if (gmodeEN) return;
            List<Byte> bData = new CopyOnWriteArrayList<>();
            bData.add((byte) 0xff);
            bData.add((byte) speedmax);
            bData.add((byte) speedmax);
            bData.add((byte) 2);
            bData.add((byte) 1);
            sendData = bData;
        });

        button_right.setOnClickListener(v -> {
            Log.i("BT", "Button_right");
            if (gmodeEN) return;
            List<Byte> bData = new CopyOnWriteArrayList<>();
            bData.add((byte) 0xff);
            bData.add((byte) speedmax);
            bData.add((byte) speedmax);
            bData.add((byte) 1);
            bData.add((byte) 2);
            sendData = bData;
        });

        button_mode.setOnClickListener(v -> {
            Log.d("BT", "Button_mode");
            if (gmodeEN) {
                gmodeEN = false;
                textView1.setText("manual mode");
            } else {
                gmodeEN = true;
                textView1.setText("g-sensor mode");
            }
        });
    }

    private void calcMove() {
        List<Byte> bData = new CopyOnWriteArrayList<>();

        byte bbuf, bbufL, bbufR;
        double deadzone = 20;       // %
        double steeringMode = 35;   // %
        double contributionRatio = 35;  // %

        bData.add((byte) 0xff);

        if (abs(xval) < deadzone) {

            if (abs(yval) < deadzone) {
                bData.add((byte) 0);
                bData.add((byte) 0);
                bData.add((byte) 0);
                bData.add((byte) 0);
            } else {
                bbuf = (byte) abs(speedmax * yval / 100);
                bData.add(bbuf);
                bData.add(bbuf);
                if (yval > 0) {
                    // forward
                    bData.add((byte) 2);
                    bData.add((byte) 2);
                } else {
                    // back
                    bData.add((byte) 1);
                    bData.add((byte) 1);
                }
            }

        } else {

            if (abs(yval) > steeringMode) {
                bbuf = (byte) abs(speedmax * yval / 100);
                if (yval > 0) {
                    if (xval > 0) {
                        // forward-left
                        bbufR = bbuf;
                        bbufL = (byte) (abs(speedmax * yval / 100) * ((contributionRatio * (000 - abs(speedmax * xval / 100)) / 100) + (100 - contributionRatio)) / 100);
                        bData.add(bbufR);
                        bData.add(bbufL);
                        bData.add((byte) 2);
                        bData.add((byte) 2);
                    } else {
                        // forward-right
                        bbufL = bbuf;
                        bbufR = (byte) (abs(speedmax * yval / 100) * ((contributionRatio * (000 - abs(speedmax * xval / 100)) / 100) + (100 - contributionRatio)) / 100);
                        bData.add(bbufR);
                        bData.add(bbufL);
                        bData.add((byte) 2);
                        bData.add((byte) 2);
                    }
                } else {
                    if (xval > 0) {
                        // back-left
                        bbufR = bbuf;
                        bbufL = (byte) (abs(speedmax * yval / 100) * ((contributionRatio * (000 - abs(speedmax * xval / 100)) / 100) + (100 - contributionRatio)) / 100);
                        bData.add(bbufR);
                        bData.add(bbufL);
                        bData.add((byte) 1);
                        bData.add((byte) 1);
                    } else {
                        // back-right
                        bbufL = bbuf;
                        bbufR = (byte) (abs(speedmax * yval / 100) * ((contributionRatio * (000 - abs(speedmax * xval / 100)) / 100) + (100 - contributionRatio)) / 100);
                        bData.add(bbufR);
                        bData.add(bbufL);
                        bData.add((byte) 1);
                        bData.add((byte) 1);
                    }
                }
            } else {
                bbuf = (byte) abs(speedmax * xval / 100);
                if (xval > 0) {
                    // strong-left
                    bData.add(bbuf);
                    bData.add(bbuf);
                    bData.add((byte) 2);
                    bData.add((byte) 1);
                } else {
                    // strong-right
                    bData.add(bbuf);
                    bData.add(bbuf);
                    bData.add((byte) 1);
                    bData.add((byte) 2);
                }
            }
        }
        sendData = bData;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list.size() > 0) {
            manager.registerListener(listener, list.get(0),
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        manager.unregisterListener(listener, list.get(0));
    }
}
