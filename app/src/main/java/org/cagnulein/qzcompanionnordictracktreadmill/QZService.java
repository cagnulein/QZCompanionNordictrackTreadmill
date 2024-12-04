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
    static float lastGearFloat = 0;
    static String lastSpeed = "";
    static String lastInclination = "";
    static String lastWattage = "";
    static String lastCadence = "";
    static String lastResistance = "";
    String lastGear = "";
    static String lastHeart = "";

    static boolean ifit_v2 = false;

    int counterTruncate = 0;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    @Override
    public void onCreate() {
  // The service is being created
        //Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();
        writeLog( "Service onCreate");

        try {
            runnable = new Runnable() {
                @Override
                public void run() {
                    writeLog( "Service run"); /*parse();*/ getOCR();
                    handler.postDelayed(runnable, 100);
                }
            };
        } finally {
            if(socket != null){
                socket.close();
                writeLog("socket.close()");
            }
        }

        if(runnable != null) {
            writeLog( "Service postDelayed");
            handler.postDelayed(runnable, 100);
        }
    }

    private boolean speed(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            String[] b = line.split(" ");
            line = line.replaceAll(",", ".");
            if(ifit_v2) {                
                lastSpeed = "Changed KPH " + b[b.length-2];
                lastSpeedFloat = Float.parseFloat(b[b.length-2]);
            } else {
                lastSpeed = line;
                lastSpeedFloat = Float.parseFloat(b[b.length-1]);
            }
            sendBroadcast(lastSpeed);
            return true;
        }
        return  false;
    }

    private boolean incline(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            String[] b = line.split(" ");
            if(ifit_v2) {  
                lastInclination = "Changed Grade " + b[b.length-2];
                lastInclinationFloat = Float.parseFloat(b[b.length-2]);
            } else {
                lastInclination = line;
                lastInclinationFloat = Float.parseFloat(b[b.length-1]);                
            }
            sendBroadcast(lastInclination);
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
            String[] b = line.split(" ");
            lastGearFloat = Float.parseFloat(b[b.length-1]);
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

    public String[] getOCR() {
        String text = ScreenCaptureService.getLastText();
        String t = text;

        String textExtended = ScreenCaptureService.getLastTextExtended();
        String tt = textExtended;

        int w = ScreenCaptureService.getImageWidth();
        int h = ScreenCaptureService.getImageHeight();

        String tExtended = textExtended;

        String packageName = MediaProjection.getPackageName();

        // Extract incline and speed values
        String[] result = new String[2];
        String[] lines = t.split("\\$\\$|\\n");

        Log.i(LOG_TAG, "getOCR");

        for (int i = 1; i < lines.length; i++) {
            Log.i("OCRlines", i + " " + lines[i]);
            if (lines[i].toLowerCase().contains("incline")) {
                try {                    
                    QZService.lastInclination = "Changed Grade " + lines[i-1].trim();
                    QZService.lastInclinationFloat = Float.parseFloat(lines[i-1].trim());
                } catch (Exception e) {
                    QZService.lastInclination = "";
                    QZService.lastInclinationFloat = 0.0f;
                }

            }
	   if (lines[i].toLowerCase().contains("cadence")) {
                try {                    
                    QZService.lastCadence = "Changed RPM " + lines[i-1].trim();
                } catch (Exception e) {
                    QZService.lastCadence = "";
                }

            }
	    if (lines[i].toLowerCase().contains("resistance")) {
                try {                    
                    QZService.lastResistance = "Changed Resistance " + lines[i-1].trim();
                    QZService.lastResistanceFloat = Float.parseFloat(lines[i-1].trim());
                } catch (Exception e) {
                    QZService.lastResistance = "";
                    QZService.lastResistanceFloat = 0.0f;
                }

            }
	    if (lines[i].toLowerCase().contains("watt")) {
                try {                    
                    String numberStr = lines[i-1].trim().replaceAll("[^0-9]", " ").trim();
                    String[] numbers = numberStr.split("\\s+");
                    if (numbers.length > 0) {
                        int watts = Integer.parseInt(numbers[numbers.length - 1]);
                        QZService.lastWattage = "Changed Watts " + watts;
                    }
                } catch (Exception e) {
                }

            }
            if (lines[i].toLowerCase().contains("speed")) {
                try {                    
                    QZService.lastSpeed = "Changed KPH " + lines[i-1].trim();
                    QZService.lastSpeedFloat = Float.parseFloat(lines[i-1].trim());
                } catch (Exception e) {
                    QZService.lastSpeed = "";
                    QZService.lastSpeedFloat = 0.0f;
                }
            }
        }
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);

            if(!QZService.lastSpeed.equals(""))
                sendBroadcast(QZService.lastSpeed);
            if(!QZService.lastInclination.equals(""))
                sendBroadcast(QZService.lastInclination);
            if(!QZService.lastWattage.equals(""))
                sendBroadcast(QZService.lastWattage);
            if(!QZService.lastCadence.equals(""))
                sendBroadcast(QZService.lastCadence);
            if(!QZService.lastResistance.equals(""))
                sendBroadcast(QZService.lastResistance);
        } catch (Exception ex) {
            ex.printStackTrace();
            return result;
        }
        socket.close();
        return result;
    }

    private void parse() {

        String file = pickLatestFileFromDownloads();
        writeLog("Parsing " + file);

        if(!file.equals("")) {
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);

                writeLog("Device: " + UDPListenerService.device);

				// this device doesn't have tail and grep capabilities
				if(UDPListenerService.device == UDPListenerService._device.c1750 || 
                UDPListenerService.device == UDPListenerService._device.c1750_2021 || 
                UDPListenerService.device == UDPListenerService._device.c1750_2020 || 
                UDPListenerService.device == UDPListenerService._device.c1750_2020_kph || 
                UDPListenerService.device == UDPListenerService._device.x22i ||
                UDPListenerService.device == UDPListenerService._device.x14i ||
                UDPListenerService.device == UDPListenerService.device.proform_carbon_t14) {
					try {
						InputStream speed2InputStream = shellRuntime.execAndGetOutput("cat " + file);
						BufferedReader is = new BufferedReader(new InputStreamReader(speed2InputStream));
						String line;
						while ((line = is.readLine()) != null) {
							if(line.contains("Changed KPH") || line.contains("Kph changed")) {
								lastSpeed = line;
                                String[] b = line.split(" ");
                                lastSpeedFloat = Float.parseFloat(b[b.length-1]);
							} else if(line.contains("Changed Grade") || line.contains("Grade changed")) {
								lastInclination = line;
                            } else if(line.contains("Changed Watts") || line.contains("Watts changed")) {
                                lastWattage = line;
                            } else if(line.contains("Changed RPM")) {
                                lastCadence = line;
                            } else if(line.contains("Changed CurrentGear")) {
                                lastGear = line;
                            } else if(line.contains("Changed Resistance")) {
                                lastResistance = line;
                            } else if(line.contains("HeartRateDataUpdate")) {
                                lastHeart = line;
                            }
						}
						if(!lastSpeed.equals(""))
							sendBroadcast(lastSpeed);
						if(!lastInclination.equals(""))
							sendBroadcast(lastInclination);
                        if(!lastWattage.equals(""))
                            sendBroadcast(lastWattage);
                        if(!lastCadence.equals(""))
                            sendBroadcast(lastCadence);
                        if(!lastGear.equals(""))
                            sendBroadcast(lastGear);
                        if(!lastResistance.equals(""))
                            sendBroadcast(lastResistance);
                        if(!lastHeart.equals(""))
                            sendBroadcast(lastHeart);
					} catch (IOException e) {
						  // Handle Exception						
						writeLog(e.getMessage());
                    }					
                } // this device doesn't log on the wolflog file
				else if(UDPListenerService.device == UDPListenerService._device.t75s) {
					try {
                        String command = "logcat -b all -d > /sdcard/logcat.log";
                        MainActivity.sendCommand(command);
                        writeLog(command);                        
						InputStream speed2InputStream = shellRuntime.execAndGetOutput("cat /sdcard/logcat.log");
						BufferedReader is = new BufferedReader(new InputStreamReader(speed2InputStream));
						String line;
						while ((line = is.readLine()) != null) {
							if(line.contains("Changed KPH") || line.contains("Changed Actual KPH")) {
								lastSpeed = line.replaceAll("Actual ", "");
                                String[] b = line.split(" ");
                                lastSpeedFloat = Float.parseFloat(b[b.length-1]);                                
							} else if(line.contains("Changed Grade") || line.contains("Changed Actual Grade")) {
								lastInclination = line.replaceAll("Actual ", "");;
                            } else if(line.contains("Changed Watts")) {
                                lastWattage = line;
                            } else if(line.contains("Changed RPM")) {
                                lastCadence = line;
                            } else if(line.contains("Changed CurrentGear")) {
                                lastGear = line;
                            } else if(line.contains("Changed Resistance")) {
                                lastResistance = line;
                            }
						}
						if(!lastSpeed.equals(""))
							sendBroadcast(lastSpeed);
						if(!lastInclination.equals(""))
							sendBroadcast(lastInclination);
                        if(!lastWattage.equals(""))
                            sendBroadcast(lastWattage);
                        if(!lastCadence.equals(""))
                            sendBroadcast(lastCadence);
                        if(!lastGear.equals(""))
                            sendBroadcast(lastGear);
                        if(!lastResistance.equals(""))
                            sendBroadcast(lastResistance);
					} catch (IOException e) {
						  // Handle Exception						
						writeLog(e.getMessage());
                    }
                } else if(UDPListenerService.device == UDPListenerService._device.grand_tour_pro ||
                          UDPListenerService.device == UDPListenerService._device.NTEX71021 ||
                          UDPListenerService.device == UDPListenerService._device.proform_carbon_c10) {
                        try {
                            //String command = "logcat -b all -d > /storage/sdcard0/logcat.log";
                            //MainActivity.sendCommand(command);
                            //writeLog(command);                        
                            //InputStream speed2InputStream = shellRuntime.execAndGetOutput("cat /storage/sdcard0/logcat.log");
                            try {
                                String command = "logcat -b all -d";
                                // Executes the command.
                                Process process = Runtime.getRuntime().exec(command);
                                writeLog(command);                        

                                // Reads stdout.
                                // NOTE: You can write to stdin of the command using
                                //       process.getOutputStream().
                                BufferedReader is = new BufferedReader(
                                    new InputStreamReader(process.getInputStream()));
                                String line;
                                while ((line = is.readLine()) != null) {
                                    if(!line.contains(LOG_TAG)) {
                                        if(line.contains("Changed KPH") || line.contains("Changed Actual KPH")) {
                                            lastSpeed = line.replaceAll("Actual ", "");;
                                        } else if(line.contains("Changed Grade") || line.contains("Changed Actual Grade") || line.contains("Grade changed")) {
                                            lastInclination = line.replaceAll("Actual ", "").replaceAll("Grade changed", "Changed Grade");
                                        } else if(line.contains("Changed Watts")) {
                                            lastWattage = line;
                                        } else if(line.contains("Changed RPM")) {
                                            lastCadence = line;
                                        } else if(line.contains("Changed CurrentGear")) {
                                            lastGear = line;
                                        } else if(line.contains("Changed Resistance")) {
                                            lastResistance = line;
                                        }
                                    }  
                                }
                                if(!lastSpeed.equals(""))
                                    sendBroadcast(lastSpeed);
                                if(!lastInclination.equals(""))
                                    sendBroadcast(lastInclination);
                                if(!lastWattage.equals(""))
                                    sendBroadcast(lastWattage);
                                if(!lastCadence.equals(""))
                                    sendBroadcast(lastCadence);
                                if(!lastGear.equals(""))
                                    sendBroadcast(lastGear);
                                if(!lastResistance.equals("")) {
                                    sendBroadcast(lastResistance);
                                    sendBroadcast(lastResistance);
                                }

                                is.close();
                
                                // Waits for the command to finish.
                                process.waitFor();                    
    
                            } catch (IOException e) {
                                    // Handle Exception						
                                writeLog(e.getMessage());
                            }		
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
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
                    String sIncline = "Grade";
                    if(ifit_v2)
                        sIncline = "INCLINE";
					InputStream inclineInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed " + sIncline + "\" | tail -n1");
					if(!incline(inclineInputStream)) {
						InputStream incline2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed " + sIncline + "\" " + file + "  | tail -n1");
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
						writeLog("Truncating file...");
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

        handler.postDelayed(runnable, 100);
    }

    public void sendBroadcast(String messageStr) {
        StrictMode.ThreadPolicy policy = new   StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {

            byte[] sendData = messageStr.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), this.clientPort);
            socket.send(sendPacket);
            writeLog("sendBroadcast " + messageStr);
        } catch (IOException e) {
            writeLog("IOException: " + e.getMessage());
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
            writeLog( "IOException: " + e.getMessage());
        }
        byte[] quads = new byte[4];
        return InetAddress.getByAddress(quads);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        writeLog("Service started");
      
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
        File file = new File("/sdcard/android/data/com.ifit.glassos_service/files/.valinorlogs/log.latest.txt");
        if (file.exists()) {
            ifit_v2 = true;
            return "/sdcard/android/data/com.ifit.glassos_service/files/.valinorlogs/log.latest.txt";
        } else {
            String ret = pickLatestFileFromDownloadsInternal("/sdcard/.wolflogs/");
            if(ret.equals("")) {
                ret = pickLatestFileFromDownloadsInternal("/.wolflogs/");
                if(ret.equals("")) {
                    ret = pickLatestFileFromDownloadsInternal("/sdcard/eru/");
                    if(ret.equals("")) {
                        ret = pickLatestFileFromDownloadsInternal("/storage/emulated/0/.wolflogs/");
                    }
                }
            }
            return ret;
        }
    }

    public static String pickLatestFileFromDownloadsInternal(String path) {
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            writeLog("There is no file in the folder");
            return "";
        }

        File lastModifiedFile = files[0];
        for (int i = 1; i < files.length; i++) {
            if (lastModifiedFile.lastModified() < files[i].lastModified() && (files[i].getName().contains("_logs.txt") || files[i].getName().contains("FitPro_"))) {
                lastModifiedFile = files[i];
            }
        }
        String k = lastModifiedFile.toString();

        writeLog(path);
        writeLog("lastModifiedFile " + lastModifiedFile);
        writeLog("string: " + k);
        return k;

    }

    private static void writeLog(String command) {
        MainActivity.writeLog(command);
        Log.i(LOG_TAG, command);
    }
}
