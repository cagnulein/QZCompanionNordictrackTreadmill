package org.cagnulein.qzcompanionnordictracktreadmill;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;

/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:8002,broadcast,sp=8002
 */
public class UDPListenerService extends Service {
    private static final String LOG_TAG = "QZ:UDPListenerService";

    static String UDP_BROADCAST = "UDPBroadcast";

    //Boolean shouldListenForUDPBroadcast = false;
    static DatagramSocket socket;

    static int y1Speed;      //vertical position of slider at 2.0
    static int y1Inclination;    //vertical position of slider at 0.0
    static double lastReqResistance = 0;
    static int y1Resistance;

    static long lastSwipeMs = 0;
    static double reqCachedSpeed = -1;
    static double reqCachedResistance = -1;
    static double reqCachedInclination = -100;

    public enum _device {
        x11i,
        nordictrack_2950,
        other,
        proform_2000,
        s22i,
		tdf10,
		t85s,
        s40,
        exp7i,
        x32i,
        c1750,
        t65s,
        nordictrack_2950_maxspeed22,
        t75s,
        grand_tour_pro,
        proform_studio_bike_pro22,
        x32i_NTL39019,
        x22i,
        NTEX71021,
        c1750_2021,
        s22i_NTEX02121_5,
        x32i_NTL39221,
        c1750_2020,        
        elite1000,
        x14i,
        nordictrack_2450,
        c1750_2020_kph,
    }

    public static _device device;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    public static void setDevice(_device dev) {
        switch(dev) {
            case x11i:
                y1Speed = 600;      //vertical position of slider at 2.0
                y1Inclination = 557;    //vertical position of slider at 0.0
                break;
            case x22i:
                y1Speed = 785;      //vertical position of slider at 2.0
                y1Inclination = 785;    //vertical position of slider at 0.0
                break;                
            case nordictrack_2450:
                y1Speed = 807;      //vertical position of slider at 2.0
                y1Inclination = 717;    //vertical position of slider at 0.0
            case x14i:
                y1Speed = 785;      //vertical position of slider at 2.0
                y1Inclination = 645;    //vertical position of slider at 0.0
                break;                
            case nordictrack_2950:
            case nordictrack_2950_maxspeed22:
                y1Speed = 807;      //vertical position of slider at 2.0
                y1Inclination = 717;    //vertical position of slider at 0.0
                break;
            case proform_2000:
                y1Speed = 598;      //vertical position of slider at 2.0
                y1Inclination = 522;    //vertical position of slider at 0.0
                break;
            case s22i:
                lastReqResistance = 0;
                y1Resistance = 618;
                break;
            case s22i_NTEX02121_5:
                lastReqResistance = 0;
                y1Resistance = 535;
                break;
            case tdf10:
                lastReqResistance = 1;
                y1Resistance = 604;
                break;
            case proform_studio_bike_pro22:
                lastReqResistance = 1;
                y1Resistance = 805;
                break;                				
            case t85s:
                y1Speed = 609;      //vertical position of slider at 2.0
                y1Inclination = 609;    //vertical position of slider at 0.0
            case s40:
                y1Speed = 482;      //vertical position of slider at 2.0
                y1Inclination = 490;    //vertical position of slider at 0.0
                break;
            case exp7i:
                y1Speed = 430;      //vertical position of slider at 2.0
                y1Inclination = 442;    //vertical position of slider at 0.0
                break;
            case x32i:
                y1Speed = 927;      //vertical position of slider at 2.0
                y1Inclination = 881;    //vertical position of slider at 0.0
                break;
            case x32i_NTL39019:
                y1Speed = 779;      //vertical position of slider at 2.0
                y1Inclination = 740;    //vertical position of slider at 0.0
                break;          
            case x32i_NTL39221:
                y1Speed = 579;      //vertical position of slider at 2.0
                y1Inclination = 635;    //vertical position of slider at 0.0
                break;                                
            case t65s:
                y1Speed = 495;      //vertical position of slider at 2.0
                y1Inclination = 585;    //vertical position of slider at 0.0                
                break;
            case t75s:
                y1Speed = 495;      //vertical position of slider at 2.0
                y1Inclination = 585;    //vertical position of slider at 0.0                
                break;                
            case grand_tour_pro:
                y1Speed = 495;      //vertical position of slider at 2.0
                y1Inclination = 585;    //vertical position of slider at 0.0                
                break;                                
            case c1750:
                y1Speed = 793;      //vertical position of slider at 2.0
                y1Inclination = 694;    //vertical position of slider at 0.0                
                break;                      
            case c1750_2021:
                y1Speed = 592;      //vertical position of slider at 2.0
                y1Inclination = 547;    //vertical position of slider at 0.0                
            case c1750_2020:
                y1Speed = 575;      //vertical position of slider at 1.0
                y1Inclination = 525;    //vertical position of slider at 0.0                                
                break;              
            case c1750_2020_kph:
                y1Speed = 598;      //vertical position of slider at 1.0
                y1Inclination = 525;    //vertical position of slider at 0.0                                
                break;                              
            case elite1000:
                y1Speed = 600;      //vertical position of slider at 1.0
                y1Inclination = 600;    //vertical position of slider at 0.0                                
                break;                                                      
            case NTEX71021:
                y1Resistance = 480;      //vertical position of slider at 1.0
                break;                      
            default:
                break;
        }
        device = dev;
    }

    private void writeLog(String command) {
        MainActivity.writeLog(command);
        Log.i(LOG_TAG, command);
    }

    private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) throws Exception {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        char decimalSeparator = symbols.getDecimalSeparator();

        byte[] recvBuf = new byte[15000];
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }
        //socket.setSoTimeout(1000);
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        writeLog("Waiting for UDP broadcast");
        socket.receive(packet);

        String senderIP = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        writeLog("Got UDP broadcast from " + senderIP + ", message: " + message);

        writeLog(message);
        String[] amessage = message.split(";");
        if(device == _device.s22i || device == _device.s22i_NTEX02121_5 || device == _device.tdf10 || device == _device.proform_studio_bike_pro22) {
            if (amessage.length > 0) {
                String rResistance = amessage[0];
                if(decimalSeparator != '.') {
                    rResistance = rResistance.replace('.', decimalSeparator);
                }
                double reqResistance = Double.parseDouble(rResistance);
                reqResistance = Math.round((reqResistance) * 10) / 10.0;
                writeLog("requestResistance: " + reqResistance + " " + lastReqResistance);

                if (lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                    if (reqResistance != -1 && reqResistance != -100 && lastReqResistance != reqResistance || (reqCachedResistance != -1 && reqCachedResistance != -100)) {
                        if (reqCachedResistance != -1 && reqCachedResistance != -100) {
                            reqResistance = reqCachedResistance;
                        }
                        int x1 = 0;
                        int y2 = 0;
                        if (device == _device.s22i) {
                            x1 = 75;
                            y2 = (int) (616.18 - (17.223 * reqResistance));
                        } else if (device == _device.s22i_NTEX02121_5) {
							x1 = 75;
                            writeLog("lastInclinationFloat " + QZService.lastInclinationFloat);
                            y1Resistance = 800 - (int) ((QZService.lastInclinationFloat + 10) * 19);
                            //set speed slider to target position
                            y2 = y1Resistance - (int) ((reqResistance - QZService.lastInclinationFloat) * 19);
                        } else if (device == _device.tdf10) {
							x1 = 1205;
                            y2 = (int) (619.91 - (15.913 * reqResistance));
                        } else if (device == _device.proform_studio_bike_pro22) {
							x1 = 1828;
                            y2 = (int) (826.25 - (21.25 * reqResistance));
                        } else if (device == _device.NTEX71021) {
                            x1 = 950;
                            y2 = (int) (493 - (13.57 * reqResistance));                            
						} else {
							x1 = 1828;
                            y2 = (int) (826.25 - (21.25 * reqResistance));
						}

                        String command = "input swipe " + x1 + " " + y1Resistance + " " + x1 + " " + y2 + " 200";
                        MainActivity.sendCommand(command);
                        writeLog(command);

                        if (device == _device.s22i || device == _device.s22i_NTEX02121_5 || device == _device.tdf10 || device == _device.proform_studio_bike_pro22 || device == _device.NTEX71021)
                            y1Resistance = y2;  //set new vertical position of speed slider
                        lastReqResistance = reqResistance;
                        lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                        reqCachedResistance = -1;
                    }
                } else {
                    reqCachedResistance = reqResistance;
                }
            }
        } else {
            if (amessage.length > 0) {
                String rSpeed = amessage[0];
                if(decimalSeparator != '.') {
                    rSpeed = rSpeed.replace('.', decimalSeparator);
                }

                double reqSpeed = Double.parseDouble(rSpeed);
                reqSpeed = Math.round((reqSpeed) * 10) / 10.0;
                writeLog("requestSpeed: " + reqSpeed + " lastSpeed:" + QZService.lastSpeedFloat + " cachedSpeed:" + reqCachedSpeed);

                if (lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis() && QZService.lastSpeedFloat > 0) {
                    if (reqSpeed != -1 || reqCachedSpeed != -1) {
                        if (reqCachedSpeed != -1) {
                            reqSpeed = reqCachedSpeed;
                        }
                        int x1 = 0;
                        int y2 = 0;
                        if (device == _device.x11i) {
                            x1 = 1207;
                            y2 = (int) (621.997 - (21.785 * reqSpeed));
                        } else if (device == _device.x22i) {
                            x1 = 1845;
                            y2 = (int) (785 - (23.636363636363636 * reqSpeed));                            
                        } else if (device == _device.x14i) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 807 - (int) ((QZService.lastSpeedFloat - 1) * 31);
                            //set speed slider to target position
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 31);                                                
                        } else if (device == _device.x32i) {
                            x1 = 1845;
                            y2 = (int) (834.85 - (26.946 * reqSpeed));
                        } else if (device == _device.x32i_NTL39019) {
                            x1 = 1845;
                            y2 = (int) (817.5 - (42.5 * reqSpeed * 0.621371));                            
						} else if (device == _device.t85s) {
                            x1 = 1207;
                            y2 = (int) (629.81 - (20.81 * reqSpeed));
                        } else if (device == _device.s40) {
                            x1 = 949;
                            y2 = (int) (507 - (12.5 * reqSpeed));
                        } else if (device == _device.exp7i) {
                            x1 = 950;
                            y2 = (int) (453.014 - (22.702 * reqSpeed * 0.621371));
                        } else if (device == _device.t65s) {
                            x1 = 1205;
                            y2 = (int) (578.36 - (35.866 * reqSpeed * 0.621371));   
                        } else if (device == _device.t75s) {
                            x1 = 1205;
                            y2 = (int) (578.36 - (35.866 * reqSpeed * 0.621371));                                                        
                        } else if (device == _device.grand_tour_pro) {
                            x1 = 1205;
                            y2 = (int) (578.36 - (35.866 * reqSpeed * 0.621371));                                                                                    
                        } else if (device == _device.nordictrack_2950) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 807 - (int) ((QZService.lastSpeedFloat - 1) * 31);
                            //set speed slider to target position
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 31);
                        } else if (device == _device.x32i_NTL39221) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 807 - (int) (((QZService.lastSpeedFloat * 0.621371) - 1) * 46.63);
                            //set speed slider to target position
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371) - 1) * 46.63);
                        } else if (device == _device.nordictrack_2950_maxspeed22) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 682 - (int) ((QZService.lastSpeedFloat - 1) * 26.5);
                            //set speed slider to target position
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 26.5);                            
                        } else if (device == _device.proform_2000) {
                            x1 = 1205;     //middle of slider
                            y2 = (int) ((-19.921 * reqSpeed) + 631.03);
                        } else if (device == _device.c1750) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 785 - (int) ((QZService.lastSpeedFloat - 1) * 31.42);
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 31.42);
                        } else if (device == _device.c1750_2021) {
                            x1 = 1205;     //middle of slider
                            y1Speed = 620 - (int) ((QZService.lastSpeedFloat - 1) * 20.73);
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 20.73);                            
                        } else if (device == _device.c1750_2020) {
                            x1 = 1205;     //middle of slider
                            y1Speed = 575 - (int) (((QZService.lastSpeedFloat * 0.621371) - 1) * 28.91);
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 28.91);
                        } else if (device == _device.c1750_2020_kph) {
                            x1 = 1205;     //middle of slider
                            y1Speed = 593 - (int) (((QZService.lastSpeedFloat) - 2) * 17.7);
                            y2 = y1Speed - (int) (((reqSpeed) - (QZService.lastSpeedFloat)) * 17.7);
                        } else if (device == _device.nordictrack_2450) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 790 - (int) (((QZService.lastSpeedFloat * 0.621371) - 1) * 46.36);
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 46.36);                            
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 28.91);                                                        
                        } else if (device == _device.elite1000) {
                            x1 = 1209;     //middle of slider
                            y1Speed = 600 - (int) (((QZService.lastSpeedFloat * 0.621371)) * 31.33);
                            y2 = y1Speed - (int) (((reqSpeed * 0.621371) - (QZService.lastSpeedFloat * 0.621371)) * 31.33);                                                                                    
                        } else {
                            x1 = 1205;     //middle of slider
                            y2 = (int) ((-19.921 * reqSpeed) + 631.03);
                        }

                        String command = "input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200";
                        if(device == _device.x22i || device == _device.x14i) {
                            shellRuntime.exec(command);
                        }
                        else {
                            MainActivity.sendCommand(command);
                        }
                        writeLog(command);

                        if (device == _device.x11i || device == _device.nordictrack_2450 || device == _device.x14i || device == _device.x22i || device == _device.elite1000 || device == _device.c1750 || device == _device.c1750_2021 || device == _device.c1750_2020 || device == _device.c1750_2020_kph || device == _device.proform_2000 || device == _device.t85s || device == _device.t65s || device == _device.grand_tour_pro || device == _device.t75s || device == _device.s40 || device == _device.exp7i || device == _device.x32i || device == _device.x32i_NTL39019 || device == _device.x32i_NTL39221)
                            y1Speed = y2;  //set new vertical position of speed slider
                        lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                        reqCachedSpeed = -1;
                    }
                } else {
                    reqCachedSpeed = reqSpeed;
                }
            }

            if (amessage.length > 1 && lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                String rInclination = amessage[1];
                if(decimalSeparator != '.') {
                    rInclination = rInclination.replace('.', decimalSeparator);
                }
                double reqInclination = roundToHalf(Double.parseDouble(rInclination));
                writeLog("requestInclination: " + reqInclination + " " + reqCachedInclination);				
				Boolean need = reqInclination != -100;
				if (!need && reqCachedInclination != -100) {
					reqInclination = reqCachedInclination;
					reqCachedInclination = -100;
				}					
                if (reqInclination != -100) {
                    int x1 = 0;
                    int y2 = 0;
                    if (device == _device.x11i) {
                        x1 = 75;
                        y2 = (int) (565.491 - (8.44 * reqInclination));
                    } else if (device == _device.x22i) {
                        x1 = 75;
                        y2 = (int) (785 - (11.304347826086957 * (reqInclination + 6)));                        
                    } else if (device == _device.x14i) {
                        x1 = 75;
                        y1Inclination = 785;
                        switch(reqInclination) {
                            case -6f: y2 = 856; break;
                            case -5.5f: y2 = 850; break;
                            case -5f: y2 = 844; break;
                            case -4.5f: y2 = 838; break;
                            case -4f: y2 = 832; break;
                            case -3.5f: y2 = 826; break;
                            case -3f: y2 = 820; break;
                            case -2.5f: y2 = 814; break;
                            case -2f: y2 = 808; break;
                            case -1.5f: y2 = 802; break;
                            case -1f: y2 = 796; break;
                            case -0.5f: y2 = 785; break;
                            case 0f: y2 = 783; break;
                            case 0.5f: y2 = 778; break;
                            case 1f: y2 = 774; break;
                            case 1.5f: y2 = 768; break;
                            case 2f: y2 = 763; break;
                            case 2.5f: y2 = 757; break;
                            case 3f: y2 = 751; break;
                            case 3.5f: y2 = 745; break;
                            case 4f: y2 = 738; break;
                            case 4.5f: y2 = 731; break;
                            case 5f: y2 = 724; break;
                            case 5.5f: y2 = 717; break;
                            case 6f: y2 = 710; break;
                            case 6.5f: y2 = 703; break;
                            case 7f: y2 = 696; break;
                            case 7.5f: y2 = 691; break;
                            case 8f: y2 = 689; break;
                            case 8.5f: y2 = 684; break;
                            case 9f: y2 = 677; break;
                            case 9.5f: y2 = 671; break;
                            case 10f: y2 = 665; break;
                            case 10.5f: y2 = 658; break;
                            case 11f: y2 = 651; break;
                            case 11.5f: y2 = 645; break;
                            case 12f: y2 = 638; break;
                            case 12.5f: y2 = 631; break;
                            case 13f: y2 = 624; break;
                            case 13.5f: y2 = 617; break;
                            case 14f: y2 = 610; break;
                            case 14.5f: y2 = 605; break;
                            case 15f: y2 = 598; break;
                            case 15.5f: y2 = 593; break;
                            case 16f: y2 = 587; break;
                            case 16.5f: y2 = 581; break;
                            case 17f: y2 = 575; break;
                            case 17.5f: y2 = 569; break;
                            case 18f: y2 = 563; break;
                            case 18.5f: y2 = 557; break;
                            case 19f: y2 = 551; break;
                            case 19.5f: y2 = 545; break;
                            case 20f: y2 = 539; break;
                            case 20.5f: y2 = 533; break;
                            case 21f: y2 = 527; break;
                            case 21.5f: y2 = 521; break;
                            case 22f: y2 = 515; break;
                            case 22.5f: y2 = 509; break;
                            case 23f: y2 = 503; break;
                            case 23.5f: y2 = 497; break;
                            case 24f: y2 = 491; break;
                            case 24.5f: y2 = 485; break;
                            case 25f: y2 = 479; break;
                            case 25.5f: y2 = 473; break;
                            case 26f: y2 = 467; break;
                            case 26.5f: y2 = 461; break;
                            case 27f: y2 = 455; break;
                            case 27.5f: y2 = 449; break;
                            case 28f: y2 = 443; break;
                            case 28.5f: y2 = 437; break;
                            case 29f: y2 = 431; break;
                            case 29.5f: y2 = 425; break;
                            case 30f: y2 = 418; break;
                            case 30.5f: y2 = 412; break;
                            case 31f: y2 = 406; break;
                            case 31.5f: y2 = 400; break;
                            case 32f: y2 = 394; break;
                            case 32.5f: y2 = 388; break;
                            case 33f: y2 = 382; break;
                            case 33.5f: y2 = 375; break;
                            case 34f: y2 = 369; break;
                            case 34.5f: y2 = 363; break;
                            case 35f: y2 = 357; break;
                            case 35.5f: y2 = 351; break;
                            case 36f: y2 = 345; break;
                            case 36.5f: y2 = 338; break;
                            case 37f: y2 = 332; break;
                            case 37.5f: y2 = 326; break;
                            case 38f: y2 = 320; break;
                            case 38.5f: y2 = 314; break;
                            case 39f: y2 = 308; break;
                            case 39.5f: y2 = 302; break;
                            case 40f: y2 = 295; break;
                        }
                    } else if (device == _device.x32i) {
                        x1 = 76;
                        y2 = (int) (734.07 - (12.297 * reqInclination));
                    } else if (device == _device.x32i_NTL39019) {
                        x1 = 74;
                        y2 = (int) (749 - (11.8424 * reqInclination));                        
					} else if (device == _device.t85s) {
                        x1 = 75;
                        y2 = (int) (609 - (36.417 * reqInclination));
                    } else if (device == _device.s40) {
                        x1 = 75;
                        y2 = (int) (490 - (21.4 * reqInclination));
                    } else if (device == _device.exp7i) {
                        x1 = 74;
                        y2 = (int) (441.813 - (21.802 * reqInclination));
                    } else if (device == _device.t65s) {
                        x1 = 74;
                        y2 = (int) (576.91 - (34.182 * reqInclination));                        
                    } else if (device == _device.t75s) {
                        x1 = 74;
                        y2 = (int) (576.91 - (34.182 * reqInclination));                                                
                    } else if (device == _device.grand_tour_pro) {
                        x1 = 74;
                        y2 = (int) (576.91 - (34.182 * reqInclination));                                                                        
                    } else if (device == _device.nordictrack_2950 || device == _device.nordictrack_2950_maxspeed22) {
                        x1 = 75;     //middle of slider
                        y1Inclination = 807 - (int) ((QZService.lastInclinationFloat + 3) * 31.1);
                        //set speed slider to target position
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 31.1);
                    } else if (device == _device.x32i_NTL39221) {
                        x1 = 75;     //middle of slider
                        y1Inclination = 750 - (int) ((QZService.lastInclinationFloat) * 12.05);
                        //set speed slider to target position
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 12.05);
                    } else if (device == _device.proform_2000) {
                        x1 = 79;
                        y1Inclination = 520 - (int) ((QZService.lastInclinationFloat + 3) * 21.804);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 21.804);
                    } else if (device == _device.nordictrack_2450) {
                        x1 = 72;
                        y1Inclination = 715 - (int) ((QZService.lastInclinationFloat + 3) * 29.26);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 29.26);
                    } else if (device == _device.c1750) {
                        x1 = 79;
                        y2 = (int) ((-34.9 * reqInclination) + 700);                        
                    } else if (device == _device.c1750_2021) {
                        x1 = 79;
                        y2 = (int) ((-22 * reqInclination) + 553);                                                
                    } else if (device == _device.c1750_2020) {
                        x1 = 75;
                        y1Inclination = 520 - (int) (QZService.lastInclinationFloat * 20);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 20);
                    } else if (device == _device.c1750_2020_kph) {
                        x1 = 75;
                        y1Inclination = 594 - (int) ((QZService.lastInclinationFloat -3) * 19.83);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 19.83);
                    } else if (device == _device.elite1000) {
                        x1 = 76;
                        y1Inclination = 589 - (int) (QZService.lastInclinationFloat * 32.8);
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 32.8);
                    } else {
                        x1 = 79;
                        y2 = (int) ((-21.804 * reqInclination) + 520.11);
                    }

                    String command = " input swipe " + x1 + " " + y1Inclination + " " + x1 + " " + y2 + " 200";
                    if(device == _device.x22i || device == _device.x14i) {
                        shellRuntime.exec(command);
                    } else {
                        MainActivity.sendCommand(command);
                    }
                    writeLog(command);

                    if (device == _device.x11i || device == _device.nordictrack_2450 || device == _device.elite1000 || device == _device.x22i || device == _device.x14i || device == _device.c1750 || device == _device.c1750_2021 || device == _device.c1750_2020  || device == _device.c1750_2020_kph || device == _device.proform_2000 || device == _device.t85s || device == _device.t65s || device == _device.t75s || device == _device.grand_tour_pro || device == _device.s40 || device == _device.exp7i || device == _device.x32i || device == _device.x32i_NTL39221)
                        y1Inclination = y2;  //set new vertical position of inclination slider
                    lastSwipeMs = Calendar.getInstance().getTimeInMillis();
					reqCachedInclination = -100;
                }
            } else if(amessage.length > 1) {
                String rInclination = amessage[1];
                double reqInclination = roundToHalf(Double.parseDouble(rInclination));
                if(reqInclination != -100) {
                    writeLog("requestInclination not handled due to lastSwipeMs: " + reqInclination);
                    reqCachedInclination = reqInclination;
                }
			}
        }

        broadcastIntent(senderIP, message);
        //socket.close();
    }

    private double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    private void broadcastIntent(String senderIP, String message) {
        Intent intent = new Intent(UDPListenerService.UDP_BROADCAST);
        intent.putExtra("sender", senderIP);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    Thread UDPBroadcastThread;

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

    void startListenForUDPBroadcast() {
        UDPBroadcastThread = new Thread(new Runnable() {
            public void run() {
				{					
					try {
						InetAddress broadcastIP = getBroadcastAddress();
						Integer port = 8003;
						while (shouldRestartSocketListen) {
							listenAndWaitAndThrowIntent(broadcastIP, port);
						}
						//if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
					} catch (Exception e) {
                        writeLog("no longer listening for UDP broadcasts cause of error " + e.getMessage());
					}
				}
            }
        });
        UDPBroadcastThread.start();
    }

    private Boolean shouldRestartSocketListen=true;

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
        stopListen();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDPBroadcast();
        writeLog("Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
