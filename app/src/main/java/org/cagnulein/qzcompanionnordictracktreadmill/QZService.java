package org.cagnulein.qzcompanionnordictracktreadmill;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

public class QZService extends Service {
    private static final String LOG_TAG = "QZ:Service";
    int startMode;       // indicates how to behave if the service is killed
    IBinder binder;      // interface for clients that bind
    boolean allowRebind; // indicates whether onRebind should be used    
    int clientPort = 8002;
    Handler handler = new Handler();
    Runnable runnable = null;
    DatagramSocket socket = null;

    byte[] lmessage = new byte[1024];
    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

    AtomicLong filePointer = new AtomicLong();
    String fileName = "";
    RandomAccessFile bufferedReader = null;
    boolean firstTime = false;
    static float lastSpeedFloat = 0;
    static float lastInclinationFloat = 0;
    static float lastResistanceFloat = 0;
    static String lastSpeed = "";
    static String lastInclination = "";
    String lastWattage = "";
    String lastCadence = "";
    static String lastResistance = "";
    String lastGear = "";

    int counterTruncate = 0;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    @Override
    public void onCreate() {
  // The service is being created
        //Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();
        Log.i(LOG_TAG, "Service onCreate");

        try {
            runnable = new Runnable() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, "Service run"); parse();
                }
            };
        } finally {
            if(socket != null){
                socket.close();
                Log.e(LOG_TAG, "socket.close()");
            }
        }

        if(runnable != null) {
            Log.i(LOG_TAG, "Service postDelayed");
            handler.postDelayed(runnable, 500);
        }
    }

    private boolean speed(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            System.out.println(line);
            lastSpeed = line;
            String[] b = line.split(" ");
            lastSpeedFloat = Float.parseFloat(b[b.length-1]);
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean incline(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastInclination = line;
            String[] b = line.split(" ");
            lastInclinationFloat = Float.parseFloat(b[b.length-1]);
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean watt(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastWattage = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean cadence(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastCadence = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean gear(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastGear = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean resistance(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastResistance = line;
            String[] b = line.split(" ");
            lastResistanceFloat = Float.parseFloat(b[b.length-1]);
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private void parse() {

        String file = pickLatestFileFromDownloads();
        Log.d(LOG_TAG,"Parsing " + file);

        if(!file.equals("")) {
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);

				// this device doesn't have tail and grep capabilities
				if(UDPListenerService.device == UDPListenerService._device.c1750) {
					try {
						InputStream speed2InputStream = shellRuntime.execAndGetOutput("cat " + file);
						BufferedReader is = new BufferedReader(new InputStreamReader(speed2InputStream));
						String line;
						while ((line = is.readLine()) != null) {
							if(line.contains("Changed KPH")) {
								lastSpeed = line;
							} else if(line.contains("Changed Grade")) {
								lastInclination = line;
							}
						}
						if(!lastSpeed.equals(""))
							sendBroadcast(lastSpeed);
						if(!lastInclination.equals(""))
							sendBroadcast(lastInclination);						
					} catch (IOException e) {
						  // Handle Exception						
						Log.e(LOG_TAG, e.getMessage());
					}					
				} else {					
					InputStream speedInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed KPH\" | tail -n1");
					if(!speed(speedInputStream)) {
						InputStream speed2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed KPH\" " + file + "  | tail -n1");
						if(!speed(speed2InputStream)) {
							sendBroadcast(lastSpeed);
						}
						speed2InputStream.close();
					}
					speedInputStream.close();
					InputStream inclineInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed Grade\" | tail -n1");
					if(!incline(inclineInputStream)) {
						InputStream incline2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed Grade\" " + file + "  | tail -n1");
						if(!incline(incline2InputStream)) {
							sendBroadcast(lastInclination);
						}
						incline2InputStream.close();
					}
					inclineInputStream.close();
					InputStream procWattInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed Watts\" | tail -n1");
					if(!watt(procWattInputStream)) {
						InputStream watt2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed Watts\" " + file + "  | tail -n1");
						if(!watt(watt2InputStream)) {
							sendBroadcast(lastWattage);
						}
						watt2InputStream.close();
					}
					procWattInputStream.close();
					InputStream cadenceInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed RPM\" | tail -n1");
					if(!cadence(cadenceInputStream)) {
						InputStream cadence2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed RPM\" " + file + "  | tail -n1");
						if(!cadence(cadence2InputStream)) {
							sendBroadcast(lastCadence);
						}
						cadence2InputStream.close();
					}
					cadenceInputStream.close();
					InputStream gearInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed CurrentGear\" | tail -n1");
					if(!gear(gearInputStream)) {
						InputStream gear2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed CurrentGear\" " + file + "  | tail -n1");
						if(!gear(gear2InputStream)) {
							sendBroadcast(lastGear);
						}
						gear2InputStream.close();
					}
					gearInputStream.close();
					InputStream resistanceInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed Resistance\" | tail -n1");
					if(!resistance(resistanceInputStream)) {
						InputStream resistance2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed Resistance\" " + file + "  | tail -n1");
						if(!resistance(resistance2InputStream)) {
							sendBroadcast(lastResistance);
						}
						resistance2InputStream.close();
					}
					resistanceInputStream.close();

					if(counterTruncate++ > 1200) {
						Log.d(LOG_TAG, "Truncating file...");
						counterTruncate = 0;
						shellRuntime.exec("truncate -s0 " + file);
					}
				}
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
            socket.close();
        }

        handler.postDelayed(runnable, 500);
    }

    public void sendBroadcast(String messageStr) {
        StrictMode.ThreadPolicy policy = new   StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {

            byte[] sendData = messageStr.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), this.clientPort);
            socket.send(sendPacket);
            System.out.println(messageStr);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
    }
    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager)    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = null;
        try {
            dhcp = wifi.getDhcpInfo();
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++)
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
        byte[] quads = new byte[4];
        return InetAddress.getByAddress(quads);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "Service started");
      
        return START_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        return allowRebind;
    }
    @Override
    public void onRebind(Intent intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }
    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
    }

    public static String pickLatestFileFromDownloads() {

        String ret = pickLatestFileFromDownloadsInternal("/sdcard/.wolflogs/");
        if(ret.equals("")) {
            ret = pickLatestFileFromDownloadsInternal("/.wolflogs/");
        }
        return ret;
    }

    public static String pickLatestFileFromDownloadsInternal(String path) {
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Log.i(LOG_TAG,"There is no file in the folder");
            return "";
        }

        File lastModifiedFile = files[0];
        for (int i = 1; i < files.length; i++) {
            if (lastModifiedFile.lastModified() < files[i].lastModified() && (files[i].getName().contains("_logs.txt") || files[i].getName().contains("FitPro_"))) {
                lastModifiedFile = files[i];
            }
        }
        String k = lastModifiedFile.toString();

        Log.i(LOG_TAG, path);
        Log.i(LOG_TAG, "lastModifiedFile " + lastModifiedFile);
        Log.i(LOG_TAG, "string: " + k);
        return k;

    }
}
