package org.cagnulein.qzcompanionnordictracktreadmill;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import static android.content.ContentValues.TAG;

import com.cgutman.androidremotedebugger.AdbUtils;
import com.cgutman.androidremotedebugger.console.ConsoleBuffer;
import com.cgutman.androidremotedebugger.devconn.DeviceConnection;
import com.cgutman.androidremotedebugger.devconn.DeviceConnectionListener;
import com.cgutman.androidremotedebugger.service.ShellService;
import com.cgutman.adblib.AdbCrypto;

public class MainActivity extends AppCompatActivity  implements DeviceConnectionListener {
    private ShellService.ShellServiceBinder binder;
    private static DeviceConnection connection;
    private Intent service;
    private static final String LOG_TAG = "QZ:AdbRemote";
    private static String lastCommand = "";
    private static boolean ADBConnected = false;
    private static String appLogs = "";

	private final ShellRuntime shellRuntime = new ShellRuntime();

    // on below line we are creating variables.
    RadioGroup radioGroup;
    SharedPreferences sharedPreferences;

    private boolean checkPermissions(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            return false;
        }
    }

    @Override
    public void notifyConnectionEstablished(DeviceConnection devConn) {
        ADBConnected = true;
        Log.i(LOG_TAG, "notifyConnectionEstablished" + lastCommand);
    }

    @Override
    public void notifyConnectionFailed(DeviceConnection devConn, Exception e) {
        ADBConnected = false;
        Log.e(LOG_TAG, e.getMessage());
    }

    @Override
    public void notifyStreamFailed(DeviceConnection devConn, Exception e) {
        ADBConnected = false;
        Log.e(LOG_TAG, e.getMessage());
    }

    @Override
    public void notifyStreamClosed(DeviceConnection devConn) {
        ADBConnected = false;
        Log.e(LOG_TAG, "notifyStreamClosed");
    }

    @Override
    public AdbCrypto loadAdbCrypto(DeviceConnection devConn) {
        return AdbUtils.readCryptoConfig(getFilesDir());
    }

    @Override
    public boolean canReceiveData() {
        return true;
    }

    @Override
    public void receivedData(DeviceConnection devConn, byte[] data, int offset, int length) {
        Log.i(LOG_TAG, data.toString());
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public void consoleUpdated(DeviceConnection devConn, ConsoleBuffer console) {

    }


    private DeviceConnection startConnection(String host, int port) {
        /* Create the connection object */
        DeviceConnection conn = binder.createConnection(host, port);

        /* Add this activity as a connection listener */
        binder.addListener(conn, this);

        /* Begin the async connection process */
        conn.startConnect();

        return conn;
    }

    private DeviceConnection connectOrLookupConnection(String host, int port) {
        DeviceConnection conn = binder.findConnection(host, port);
        if (conn == null) {
            /* No existing connection, so start the connection process */
            conn = startConnection(host, port);
        }
        else {
            /* Add ourselves as a new listener of this connection */
            binder.addListener(conn, this);
        }
        return conn;
    }

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            binder = (ShellService.ShellServiceBinder)arg1;
            if (connection != null) {
                binder.removeListener(connection, MainActivity.this);
            }
            connection = connectOrLookupConnection("127.0.0.1", 5555);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            binder = null;
        }
    };

    public static void writeLog(String command) {
        Date date = new Date();
        Timestamp timestamp2 = new Timestamp(date.getTime());
        appLogs = appLogs + "\n" + timestamp2 + " " + command;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();

        sharedPreferences = getSharedPreferences("QZ",MODE_PRIVATE);
        radioGroup = findViewById(R.id.radiogroupDevice);

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                RadioButton radioButton = findViewById(i);
                if(i == R.id.x11i) {
                    UDPListenerService.setDevice(UDPListenerService._device.x11i);
                } else if(i == R.id.x22i) {
                    UDPListenerService.setDevice(UDPListenerService._device.x22i);
				} else if(i == R.id.t85s) {
                    UDPListenerService.setDevice(UDPListenerService._device.t85s);
                } else if(i == R.id.x32i) {
                    UDPListenerService.setDevice(UDPListenerService._device.x32i);
                } else if(i == R.id.x32i_NTL39019) {
                    UDPListenerService.setDevice(UDPListenerService._device.x32i_NTL39019);                    
                } else if(i == R.id.s40) {
                    UDPListenerService.setDevice(UDPListenerService._device.s40);
                } else if(i == R.id.exp7i) {
                    UDPListenerService.setDevice(UDPListenerService._device.exp7i);
                } else if(i == R.id.nordictrack_2950) {
                    UDPListenerService.setDevice(UDPListenerService._device.nordictrack_2950);
                } else if(i == R.id.nordictrack_2950_maxspeed22) {
                    UDPListenerService.setDevice(UDPListenerService._device.nordictrack_2950_maxspeed22);
                } else if(i == R.id.proform_2000) {
                    UDPListenerService.setDevice(UDPListenerService._device.proform_2000);
                } else if(i == R.id.s22i) {
                    UDPListenerService.setDevice(UDPListenerService._device.s22i);
                } else if(i == R.id.tdf10) {
                    UDPListenerService.setDevice(UDPListenerService._device.tdf10);
                } else if(i == R.id.c1750) {
                    UDPListenerService.setDevice(UDPListenerService._device.c1750);
                } else if(i == R.id.t65s) {
                    UDPListenerService.setDevice(UDPListenerService._device.t65s);
                } else if(i == R.id.t75s) {
                    UDPListenerService.setDevice(UDPListenerService._device.t75s);
                } else if(i == R.id.grand_tour_pro) {
                    UDPListenerService.setDevice(UDPListenerService._device.grand_tour_pro);
                } else if(i == R.id.proform_studio_bike_pro22) {
                    UDPListenerService.setDevice(UDPListenerService._device.proform_studio_bike_pro22);                    
                } else {
                    UDPListenerService.setDevice(UDPListenerService._device.other);
                }
                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                myEdit.putInt("device", i);
                myEdit.commit();
            }
        });

        int device = sharedPreferences.getInt("device", R.id.other);
        RadioButton radioButton;
        radioButton = findViewById(device);
        if(radioButton != null)
            radioButton.setChecked(true);

        Button dumplog = findViewById(R.id.dumplog);
        dumplog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            TextView tv = (TextView)findViewById(R.id.dumplog_tv);
            tv.setText(tv.getText() + "\n" + appLogs);

                String command = "logcat -b all -d > /sdcard/logcat.log";
                MainActivity.sendCommand(command);
                Log.i(LOG_TAG, command);
				/*
				String file = QZService.pickLatestFileFromDownloads();
				if(!file.equals("")) {
					TextView tv = (TextView)findViewById(R.id.dumplog_tv);
					tv.setText("FILE " + file);
					try {
						InputStream speed2InputStream = shellRuntime.execAndGetOutput("cat " + file);
						BufferedReader is = new BufferedReader(new InputStreamReader(speed2InputStream));
						String line;
						while ((line = is.readLine()) != null) {
							tv.setText(tv.getText().toString() + "\r\n" + line);
							tv.setMovementMethod(new ScrollingMovementMethod());
						}					  					  
					} catch (IOException e) {
						  // Handle Exception
						tv.setText(e.getMessage());
						tv.setMovementMethod(new ScrollingMovementMethod());
						Log.e(LOG_TAG, e.getMessage());
					}
				} else {
					TextView tv = (TextView)findViewById(R.id.dumplog_tv);
					tv.setText("file not found");
					tv.setMovementMethod(new ScrollingMovementMethod());
				}*/
            }
        });

        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), TcpServerService.class));
        } else {
            startService(new Intent(getApplicationContext(), TcpServerService.class));
        }*/

        AlarmReceiver alarm = new AlarmReceiver();
        alarm.setAlarm(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /* If we have old RSA keys, just use them */
            AdbCrypto crypto = AdbUtils.readCryptoConfig(getFilesDir());
            if (crypto == null) {
                /* We need to make a new pair */
                Log.i(LOG_TAG,
                        "This will only be done once.");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        AdbCrypto crypto;

                        crypto = AdbUtils.writeNewCryptoConfig(getFilesDir());

                        if (crypto == null) {
                            Log.e(LOG_TAG,
                                    "Unable to generate and save RSA key pair");
                            return;
                        }

                    }
                }).start();
            }

            if (binder == null) {
                service = new Intent(this, ShellService.class);

                /* Bind the service if we're not bound already. After binding, the callback will
                 * perform the initial connection. */
                getApplicationContext().bindService(service, serviceConn, Service.BIND_AUTO_CREATE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
            }
        }
    }

    static public void sendCommand(String command) {
        if(ADBConnected) {
            StringBuilder commandBuffer = new StringBuilder();

            commandBuffer.append(command);

            /* Append a newline since it's not included in the command itself */
            commandBuffer.append('\n');

            /* Send it to the device */
            connection.queueCommand(commandBuffer.toString());
        } else {
            Log.e(LOG_TAG, "sendCommand ADB is not connected!");
        }
    }

}