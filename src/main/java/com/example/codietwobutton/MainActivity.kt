package com.example.codietwobutton

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val SERVICE_UUID = UUID.fromString("52af0001-978a-628d-c845-0a104ca2b8dd")
    private val WRITE_UUID = UUID.fromString("52af0002-978a-628d-c845-0a104ca2b8dd")
    private val NOTIFY_UUID = UUID.fromString("52af0003-978a-628d-c845-0a104ca2b8dd")

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var forwardState = true // toggle between forward/back on left button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        val btnDrive = findViewById<Button>(R.id.btnDrive)
        val btnSteer = findViewById<Button>(R.id.btnSteer)

        // Left button toggles forward/back on click
        btnDrive.setOnClickListener {
            val cmd = if (forwardState) "FWD" else "BWD"
            sendCmd(cmd)
            forwardState = !forwardState
            Toast.makeText(this, "Drive: $cmd", Toast.LENGTH_SHORT).show()
        }

        // Right button: press and hold to turn; release to go straight
        btnSteer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sendCmd("LEFT")
                    v.isPressed = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sendCmd("STRAIGHT")
                    v.isPressed = false
                }
            }
            true
        }

        // Auto-connect to paired device named Codie (simple approach)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val paired = adapter?.bondedDevices
        val codie = paired?.firstOrNull { it.name?.contains("Codie", true) == true }
        codie?.let { connectToDevice(it) } ?: run {
            Toast.makeText(this, "Codie not paired. Pair in system Bluetooth first.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        ActivityCompat.requestPermissions(this, perms, 101)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val svc: BluetoothGattService? = gatt.getService(SERVICE_UUID)
                writeChar = svc?.getCharacteristic(WRITE_UUID)
                val notify = svc?.getCharacteristic(NOTIFY_UUID)
                if (notify != null) {
                    gatt.setCharacteristicNotification(notify, true)
                }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connected to Codie", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun sendCmd(cmd: String) {
        val b = cmd.toByteArray()
        writeChar?.setValue(b)
        writeChar?.writeType = WRITE_TYPE_NO_RESPONSE
        bluetoothGatt?.writeCharacteristic(writeChar)
    }
}
