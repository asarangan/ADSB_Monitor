package org.sarangan.adsb

import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.*


val lightArray = arrayOf(
    "hb_light",
    "gps_light",
    "traffic_light",
    "ahrs_light",
    "uplink_light"
)
var txtFieldArray = arrayOf(0, 0, 0, 0, 0)
var packetCount = arrayOf(0, 0, 0, 0, 0)
val timerArray = arrayOf(Timer(), Timer(), Timer(), Timer(), Timer())
var ffStatus: Boolean = false
var ffFlag: Boolean = true


open class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mySwitch = findViewById<SwitchCompat>(R.id.switch1)
        mySwitch.isChecked = true

        txtFieldArray = arrayOf<Int>(
            resources.getIdentifier("textViewhb", "id", this.packageName),
            resources.getIdentifier("textViewgps", "id", this.packageName),
            resources.getIdentifier("textViewtraffic", "id", this.packageName),
            resources.getIdentifier("textViewahrs", "id", this.packageName),
            resources.getIdentifier("textViewuplink", "id", this.packageName)
        )
        for (i in 0..4) {
            findViewById<TextView>(txtFieldArray[i]).text =
                packetCount[i].toString()
        }

        fun ipToString(i: Int): String {
            return (i and 0xFF).toString() + "." +
                    (i shr 8 and 0xFF) + "." +
                    (i shr 16 and 0xFF) + "." +
                    (i shr 24 and 0xFF)
        }


        val wifiManager: WifiManager =
            getApplicationContext().getSystemService(WIFI_SERVICE) as WifiManager
        val lock: MulticastLock = wifiManager.createMulticastLock("Log_Tag")
        lock.acquire(); //Multicast lock is needed because some devices block UDP broadcast
        val ipAddress = ipToString(wifiManager.connectionInfo.ipAddress)
        val ipAddressInArray = ipAddress.split(".")
        val ipAddress255 =
            ipAddressInArray[0] + "." + ipAddressInArray[1] + "." + ipAddressInArray[2] + ".255"
        val buffer = ByteArray(64)
        val socketIn = MulticastSocket(4000) //Most ADSB transmit GDL-90 on port 4000. Otherwise this may need to be changed.
        socketIn.soTimeout = 2000   //When Stratus is in FF mode, it transmits in a different port, so a timeout is necessary for socketIn
        val packetIn = DatagramPacket(buffer, buffer.size)
        val socketOut = DatagramSocket()
        socketOut.broadcast = true  //Not sure if this is needed
        val sendDataOpen: ByteArray = byteArrayOf(  //Byte sequence to switch Stratus to Open Mode
            0xC2.toByte(),
            0x53.toByte(),
            0xFF.toByte(),
            0x56.toByte(),
            0x01.toByte(),
            0x01.toByte(),
            0x6E.toByte(),
            0x37.toByte()
        )
        val sendDataClose: ByteArray = byteArrayOf(  //Byte sequence to switch Stratus to Foreflight-only Mode
            0xC2.toByte(),
            0x53.toByte(),
            0xFF.toByte(),
            0x56.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0x6D.toByte(),
            0x36.toByte()
        )

        fun udpSend() {
            //C2 53 FF 56 01 01 6E 37 (UDP Port 41500) to switch to Open Mode
            //C2 53 FF 56 01 00 6D 36  (UDP Port 41500) to switch to Closed Mode
            val sendData: ByteArray
            if (mySwitch.isChecked) {
                sendData = sendDataOpen
            } else {
                sendData = sendDataClose
            }
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName(ipAddress255),
                41500       //The switch command is sent on port 41500
            )
            socketOut.send(sendPacket)
        }


        Thread(Runnable {
            while (true) {
                try {
                    if (!lock.isHeld) { //On some Android (Motorola MotoG), lock needs to be continuously checked.
                        lock.acquire();
                    }
                    if (ffFlag) {   //On startup, send command to Stratus switch to open mode
                        udpSend()
                        ffFlag = false
                    }

                    socketIn.receive(packetIn)
                    if (packetIn.data[0].toInt() == 0x7e) { // 0x7e is the flag byte of a data frame
                        if (packetIn.data[1].toInt() == 0) {// Heart Beat
                            packetCount[0]++
                            this@MainActivity.runOnUiThread(java.lang.Runnable {
                                changeToGreen(0)
                            })
                        }

                        if (packetIn.data[1].toInt() == 10) {// Ownship (GPS)
                            packetCount[1]++
                            this@MainActivity.runOnUiThread(java.lang.Runnable {
                                changeToGreen(1)
                            })
                        }

                        if (packetIn.data[1].toInt() == 20) {//Traffic
                            packetCount[2]++
                            this@MainActivity.runOnUiThread(java.lang.Runnable {
                                changeToGreen(2)
                            })
                        }

                        if (packetIn.data[1].toInt() == 0x4C) {// Stratux AHRS
                            packetCount[3]++
                            this@MainActivity.runOnUiThread(java.lang.Runnable {
                                changeToGreen(3)
                            })
                        }

                        if ((packetIn.data[1].toInt() == 0x4C) && (packetIn.data[2].toInt() == 1)) {// Foreflight AHRS
                            packetCount[3]++
                            this@MainActivity.runOnUiThread(java.lang.Runnable {
                                changeToGreen(3)
                            })
                        }

                        if (packetIn.data[1].toInt() == 7) {// Uplink data from tower
                            packetCount[4]++
                            this@MainActivity.runOnUiThread(java.lang.Runnable {
                                changeToGreen(4)
                            })
                        }
                    }
                } catch (e: Exception) {
                    //Timeout exception will occur when Stratus is in FF mode
                }
            }

        }).start()
    }


    fun changeToRed(buttonCounter: Int) {
        findViewById<Button>(
            this.resources.getIdentifier(
                lightArray[buttonCounter],
                "id",
                this.packageName
            )
        ).setBackgroundResource(R.drawable.circle_red)
    }

    fun changeToGreen(buttonCounter: Int) {
        findViewById<Button>(
            resources.getIdentifier(
                lightArray[buttonCounter],
                "id",
                this.packageName
            )
        ).setBackgroundResource(R.drawable.circle_green)
        findViewById<TextView>(txtFieldArray[buttonCounter]).text =
            packetCount[buttonCounter].toString()
        timerArray[buttonCounter].cancel()
        timerArray[buttonCounter] = Timer()
        val timerTask = object : TimerTask() {
            override fun run() {
                changeToRed(buttonCounter)
            }
        }
        timerArray[buttonCounter].schedule(timerTask, 2500)
    }

    fun ffStatus(view: View) {
        val mySwitch = findViewById<SwitchCompat>(R.id.switch1);
        ffStatus = !mySwitch.isChecked
        ffFlag = true
    }
}



