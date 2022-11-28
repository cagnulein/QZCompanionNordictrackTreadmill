package org.cagnulein.qzcompanionnordictracktreadmill

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class TcpClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream) : Thread() {
    var lastReqSpeed = 2f
    var y1Speed = 782 //vertical position of slider at 2.0

    var lastReqInclination = -1f
    var y1Inclination = 722 //vertical position of slider at 0.0

    override fun run() {
        val shellRuntime = ShellRuntime()
        while (true) {
            try {
                if(dataInputStream.available() > 0){
                    var smessage : String = dataInputStream.readLine()
                    Log.i(TAG, "Received: " + smessage)
                    val amessage: Array<String> = smessage.split(";").toTypedArray()
                    if (amessage.size > 0) {
                        val rSpeed = amessage[0]
                        val reqSpeed = rSpeed.toFloat()
                        Log.i(TAG, "requestSpeed: $reqSpeed")
                        if (reqSpeed != -1f && lastReqSpeed != reqSpeed) {
                            val x1 = 1845 //middle of slider
                            //set speed slider to target position
                            val y2 =
                                (y1Speed - ((lastReqSpeed - reqSpeed) * 29.78).toInt()) //calculate vertical pixel position for new speed
                            val command = "input swipe $x1 $y1Speed $x1 $y2 200"
                            shellRuntime.exec(command)
                            Log.d(TAG, command)
                            y1Speed = y2 //set new vertical position of speed slider
                            lastReqSpeed = reqSpeed
                        }
                    }

                    if (amessage.size > 1) {
                        val rInclination = amessage[1]
                        val reqInclination = rInclination.toFloat()
                        Log.d(TAG, "requestInclination: $reqInclination")
                        if (reqInclination != -100f && lastReqInclination != reqInclination) {
                            val x1 = 75 //middle of slider
                            y1Inclination = 722 //vertical position of slider at 0.0
                            val y2 =
                                y1Inclination - ((lastReqInclination - reqInclination) * 29.9).toInt() //calculate vertical pixel position for new incline
                            val command = " input swipe $x1 $y1Speed $x1 $y2 200"
                            shellRuntime.exec(command)
                            Log.d(TAG, command)
                            y1Inclination = y2 //set new vertical position of speed slider
                            lastReqInclination = reqInclination
                        }
                    }

                    dataOutputStream.writeUTF(smessage)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
    }

    companion object {
        private val TAG = TcpClientHandler::class.java.simpleName
    }

}