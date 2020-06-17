/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.gattserver

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import com.google.gson.Gson
import java.io.UnsupportedEncodingException

private const val TAG = "GattServerActivity"

data class DummyOffer(
        val type: String
)

class GattServerActivity : Activity() {
    private lateinit var textView: TextView
    /* Bluetooth API */
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null
    /* Collection of notification subscribers */
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)

            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    startAdvertising()
                    startServer()
                }
                BluetoothAdapter.STATE_OFF -> {
                    stopServer()
                    stopAdvertising()
                }
            }
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {


        private var data: ByteArray = ByteArray(0)

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: $device")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                //Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) = synchronized(lock) {
            Log.i(TAG, "onNotificationSent")
            lock.notifyAll()
        }

        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            Log.i(TAG, "onExecuteWrite")
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            mtuSize = mtu
        }

        private var mtuSize: Int = 23

        private val lock = Object()

        private fun performSend(message: String, device: BluetoothDevice) = synchronized(lock) {
            // Process message
            val txCharacteristic = bluetoothGattServer?.getService(TransferProfile.TRANSFER_SERVICE)?.getCharacteristic(TransferProfile.TRANSFER_TX_CHARACTERISTIC)

            Log.i(TAG, "sendData mtu: $mtuSize")

            val packetSize = mtuSize - 3

            var posBegin = 0
            val size = message.length
            var posEnd = if (size> packetSize) packetSize else size

            // Process, sending parts if they are greater than the maximum

            do {
                val part = message.substring (posBegin, posEnd)
                if (part.length> 0) {

                    //if (debugExtra) {
                    //    logD ("send part ($ {part.length}) -> $ {part.extExpandStr ()}")
                    //}
                    var data = ByteArray (0)
                    try {
                        data = part.toByteArray (charset ("UTF-8"))
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace ()
                    }

                    Log.i(TAG, "send packet")

                    txCharacteristic?.setValue(data)
                    bluetoothGattServer?.notifyCharacteristicChanged(device, txCharacteristic, false)

                    lock.wait()

                    if (posEnd == size) {
                        break
                    }

                    posBegin = posEnd
                    posEnd = posBegin + packetSize
                    if (posEnd > size) {
                        posEnd = size
                    }
                } else {
                    break
                }

            } while (posEnd <= size)

            Log.i(TAG, "send EOM")
            txCharacteristic?.setValue("EOM")
            bluetoothGattServer?.notifyCharacteristicChanged(device, txCharacteristic, false)
        }

        private fun sendData (message: String, device: BluetoothDevice) {

            // In separate Thread
            try {

                object: Thread () {
                    override fun run () {

                        performSend(message, device)
                    }
                } .start ()

            } catch (e: Exception) {
                // Part of my library - commented for this example
                // this.activity.extShowException (e)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            val now = System.currentTimeMillis()
            Log.i(TAG, "onCharacteristicWriteRequest")
            when {
                TransferProfile.TRANSFER_TX_CHARACTERISTIC == characteristic?.uuid -> {
                    Log.i(TAG, "Write TRANSFER_TX_CHARACTERISTIC")
//                    bluetoothGattServer?.sendResponse(device,
//                            requestId,
//                            BluetoothGatt.GATT_SUCCESS,
//                            0,
//                            TimeProfile.getExactTime(now, TimeProfile.ADJUST_NONE))
                }
                TransferProfile.TRANSFER_RX_CHARACTERISTIC == characteristic?.uuid -> {
                    Log.i(TAG, "Write TRANSFER_RX_CHARACTERISTIC")

                    val newData = value?.let {
                        val string = it.toString(Charsets.UTF_8)
                        Log.i(TAG, string)
                        if (string == "EOM") {


                            val response: String
                            val message = data.toString(Charsets.UTF_8)
                            data = ByteArray(0)
                            Log.i(TAG, message)

                            var gson = Gson()
                            val request = gson?.fromJson(message, DummyOffer::class.java)
                            if (request?.type == "offer") {
                                response = message
                            } else {
                                response = "Unknown Command"
                            }

                            data = ByteArray(0)

                            if (device != null) {
                                sendData(response, device)
                            }
                        } else {
                            data += it
                        }
                    }

//                    bluetoothGattServer?.sendResponse(device,
//                            requestId,
//                            BluetoothGatt.GATT_SUCCESS,
//                            0,
//                            TimeProfile.getLocalTimeInfo(now))
                }
                else -> {
                    // Invalid characteristic
                    Log.w(TAG, "Invalid Characteristic Write: " + characteristic?.uuid)
//                    bluetoothGattServer?.sendResponse(device,
//                            requestId,
//                            BluetoothGatt.GATT_FAILURE,
//                            0,
//                            null)
                }
            }
        }

//        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
//                                                 characteristic: BluetoothGattCharacteristic) {
//            val now = System.currentTimeMillis()
//            Log.i(TAG, "onCharacteristicReadRequest")
//            bluetoothGattServer?.sendResponse(device,
//                    requestId,
//                    BluetoothGatt.GATT_SUCCESS,
//                    0,
//                    null)
//        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
                                             descriptor: BluetoothGattDescriptor) {
            Log.i(TAG, "onDescriptorReadRequest")
//            if (TimeProfile.CLIENT_CONFIG == descriptor.uuid) {
//                Log.d(TAG, "Config descriptor read")
//                val returnValue = if (registeredDevices.contains(device)) {
//                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//                } else {
//                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
//                }
//                bluetoothGattServer?.sendResponse(device,
//                        requestId,
//                        BluetoothGatt.GATT_SUCCESS,
//                        0,
//                        returnValue)
//            } else {
//                Log.w(TAG, "Unknown descriptor read request")
//                bluetoothGattServer?.sendResponse(device,
//                        requestId,
//                        BluetoothGatt.GATT_FAILURE,
//                        0, null)
//            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int,
                                              descriptor: BluetoothGattDescriptor,
                                              preparedWrite: Boolean, responseNeeded: Boolean,
                                              offset: Int, value: ByteArray) {
                            if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0, null)
                }
            Log.i(TAG, "onDescriptorWriteRequest")
//            if (TimeProfile.CLIENT_CONFIG == descriptor.uuid) {
//                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
//                    Log.d(TAG, "Subscribe device to notifications: $device")
//                    registeredDevices.add(device)
//                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
//                    Log.d(TAG, "Unsubscribe device from notifications: $device")
//                    registeredDevices.remove(device)
//                }
//
//                if (responseNeeded) {
//                    bluetoothGattServer?.sendResponse(device,
//                            requestId,
//                            BluetoothGatt.GATT_SUCCESS,
//                            0, null)
//                }
//            } else {
//                Log.w(TAG, "Unknown descriptor write request")
//                if (responseNeeded) {
//                    bluetoothGattServer?.sendResponse(device,
//                            requestId,
//                            BluetoothGatt.GATT_FAILURE,
//                            0, null)
//                }
//            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        textView = findViewById(R.id.text_time)

        textView.text = "Test Message"

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            finish()
        }

        // Register for system Bluetooth events
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)
        if (!bluetoothAdapter.isEnabled) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling")
            bluetoothAdapter.enable()
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services")
            startAdvertising()
            startServer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter.isEnabled) {
            stopServer()
            stopAdvertising()
        }

        unregisterReceiver(bluetoothReceiver)
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private fun startAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
                bluetoothManager.adapter.bluetoothLeAdvertiser

        bluetoothLeAdvertiser?.let {
            val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build()

            val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(TransferProfile.TRANSFER_SERVICE))
                    .build()

            it.startAdvertising(settings, data, advertiseCallback)
        } ?: Log.w(TAG, "Failed to create advertiser")
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
                bluetoothManager.adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.let {
            it.stopAdvertising(advertiseCallback)
        } ?: Log.w(TAG, "Failed to create advertiser")
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        bluetoothGattServer?.addService(TransferProfile.createTransferService())
                ?: Log.w(TAG, "Unable to create GATT server")
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer() {
        bluetoothGattServer?.close()
    }

    /**
     * Send a time service notification to any devices that are subscribed
     * to the characteristic.
     */
    private fun notifyRegisteredDevices(timestamp: Long, adjustReason: Byte) {
//        if (registeredDevices.isEmpty()) {
//            Log.i(TAG, "No subscribers registered")
//            return
//        }
//        val exactTime = TimeProfile.getExactTime(timestamp, adjustReason)
//
//        Log.i(TAG, "Sending update to ${registeredDevices.size} subscribers")
//        for (device in registeredDevices) {
//            val timeCharacteristic = bluetoothGattServer
//                    ?.getService(TimeProfile.TIME_SERVICE)
//                    ?.getCharacteristic(TimeProfile.CURRENT_TIME)
//            timeCharacteristic?.value = exactTime
//            bluetoothGattServer?.notifyCharacteristicChanged(device, timeCharacteristic, false)
//        }
    }
}
