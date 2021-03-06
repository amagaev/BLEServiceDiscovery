package com.example.blertc

import android.Manifest
import android.bluetooth.*
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.example.androidthings.gattserver.TransferProfile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.ktor.util.KtorExperimentalAPI
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.io.UnsupportedEncodingException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedTransferQueue

@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private lateinit var rtcClient: RTCClient
    private lateinit var bluetoothManager: BluetoothManager
    private val BleRTCTag: String = "BleRTC"
    private var bluetoothGattServer: BluetoothGattServer? = null
    private val registeredDevices = mutableSetOf<BluetoothDevice>()
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
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i("MainActivity", "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w("MainActivity", "LE Advertise Failed: $errorCode")
        }
    }


    private var mtuSize: Int = 23
    private val lock = Object()

    private fun performSend(message: String, device: BluetoothDevice) = synchronized(lock) {
        val txCharacteristic = bluetoothGattServer?.getService(TransferProfile.TRANSFER_SERVICE)
            ?.getCharacteristic(TransferProfile.TRANSFER_TX_CHARACTERISTIC)
        Log.i("", "sendData mtu: $mtuSize")
        val packetSize = mtuSize - 3

        var posBegin = 0
        val size = message.length
        var posEnd = if (size > packetSize) packetSize else size

        do {
            val part = message.substring(posBegin, posEnd)
            if (part.length > 0) {
                var data = ByteArray(0)
                try {
                    data = part.toByteArray(charset("UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }

                Log.i("TAG", "send packet")
                txCharacteristic?.setValue(data)
                bluetoothGattServer?.notifyCharacteristicChanged(
                    device,
                    txCharacteristic,
                    false
                )
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
        Log.i("TAG", "send EOM")
        txCharacteristic?.setValue("EOM")
        bluetoothGattServer?.notifyCharacteristicChanged(device, txCharacteristic, false)
    }

    private val sendingQueue: BlockingQueue<String> = LinkedTransferQueue<String>()
    private var sendingThread: Thread? = null

    fun sendData(message: String) {
        sendingQueue.add(message)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        private var data: ByteArray = ByteArray(0)

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("TAG", "BluetoothDevice CONNECTED: $device")
                registeredDevices.add(device)

                sendingThread = Thread({
                    try {
                        while (true) {
                           var message = sendingQueue.take();
                            performSend(message, registeredDevices.first())
                        }
                    } catch (ex: InterruptedException) {

                    }
                })
                sendingThread?.start()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendingThread?.interrupt()
                sendingThread = null
                sendingQueue.clear()
                Log.i("TAG", "BluetoothDevice DISCONNECTED: $device")
                //Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) =
            synchronized(lock) {
                Log.i("TAG", "onNotificationSent")
                lock.notifyAll()
            }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            mtuSize = mtu
        }

        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            Log.i("TAG", "onExecuteWrite")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val now = System.currentTimeMillis()
            Log.i("TAG", "onCharacteristicWriteRequest")
            when {
                TransferProfile.TRANSFER_TX_CHARACTERISTIC == characteristic?.uuid -> {
                    Log.i("TAG", "Write TRANSFER_TX_CHARACTERISTIC")
                }
                TransferProfile.TRANSFER_RX_CHARACTERISTIC == characteristic?.uuid -> {
                    Log.i("TAG", "Write TRANSFER_RX_CHARACTERISTIC")

                    value?.let {
                        val string = it.toString(Charsets.UTF_8)
                        Log.i("TAG", string)
                        if (string == "EOM") {
                            val message = data.toString(Charsets.UTF_8)
                            data = ByteArray(0)
                            Log.i("TAG", message)
                            if (device != null) {
                                onDataReceived(message)
                            }
                        } else {
                            data += it
                        }
                    }
                }
                else -> {
                    // Invalid characteristic
                    Log.w("TAG", "Invalid Characteristic Write: " + characteristic?.uuid)
                }
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.i("TAG", "onDescriptorReadRequest")
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0, null
                )
            }
            Log.i("TAG", "onDescriptorWriteRequest")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkCameraPermission()

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
            Log.d("TAG", "Bluetooth is currently disabled...enabling")
            bluetoothAdapter.enable()
        } else {
            Log.d("TAG", "Bluetooth enabled...starting services")
            startAdvertising()
            startServer()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        } else {
            onCameraPermissionGranted()
        }
    }

    private fun onCameraPermissionGranted() {
        Log.i(BleRTCTag, "!!! onCameraPermissionGranted")
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
//                    super.onIceGatheringChange(p0)
                    Log.i(BleRTCTag, "!!! onIceGatheringChange")
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
//                    super.onIceCandidatesRemoved(p0)
                    Log.i(BleRTCTag, "!!! onIceCandidatesRemoved")
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
//                    super.onIceConnectionChange(p0)
                    Log.i(BleRTCTag, "!!! onIceConnectionChange")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
//                    super.onIceConnectionReceivingChange(p0)
                    Log.i(BleRTCTag, "!!! onIceConnectionReceivingChange")
                }

                override fun onIceCandidate(p0: IceCandidate?) {
                    Log.i(BleRTCTag, "!!! onIceCandidate")

                    if (p0 != null) {
                        super.onIceCandidate(p0)

                        val iceGson: Gson = GsonBuilder()
                            .registerTypeAdapter(
                                IceCandidate::class.java,
                                ICEDeserializer()
                            )
                            .registerTypeAdapter(IceCandidate::class.java, ICESerializer())
                            .create()

                        sendData(iceGson.toJson(p0!!))
                    }
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.i(BleRTCTag, "!!! onAddStream")
                    p0?.videoTracks?.get(0)?.addSink(remote_view)
                }
            }
        )
        rtcClient.initSurfaceView(remote_view)
        rtcClient.initSurfaceView(local_view)
        rtcClient.startLocalVideoCapture(local_view)
    }

    private fun requestCameraPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                CAMERA_PERMISSION
            ) && !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app need the camera to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
    }

    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        bluetoothGattServer?.addService(TransferProfile.createTransferService())
            ?: Log.w("TAG", "Unable to create GATT server")
    }

    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {
        if (bluetoothAdapter == null) {
            Log.w("MainActivity", "Bluetooth is not supported")
            return false
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w("MainActivity", "Bluetooth LE is not supported")
            return false
        }
        return true
    }

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
        } ?: Log.w("MainActivity", "Failed to create advertiser")
    }

    private fun stopServer() {
        bluetoothGattServer?.close()
    }

    private fun stopAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) ?: Log.w(
            "TAG",
            "Failed to create advertiser"
        )
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

    fun onDataReceived(data: String) {
        Log.i(BleRTCTag, "onDataReceived")

        val gson = Gson()
        val jsonObject = gson.fromJson(data, JsonObject::class.java)

        if (jsonObject.has("candidate")) {
            val iceGson: Gson = GsonBuilder()
                .registerTypeAdapter(
                    IceCandidate::class.java,
                    ICEDeserializer()
                )
                .registerTypeAdapter(IceCandidate::class.java, ICESerializer())
                .create()
            val candidate = iceGson.fromJson(data, IceCandidate::class.java)
//            rtcClient.addIceCandidate(candidate)
        } else {
            val offerGson: Gson = GsonBuilder()
                .registerTypeAdapter(
                    SessionDescription::class.java,
                    SessionDescriptionDeserializer()
                )
                .registerTypeAdapter(SessionDescription::class.java, SessionDescriptionSerializer())
                .create()
            val sessionDescription = offerGson.fromJson(data, SessionDescription::class.java)
            if (sessionDescription.type == SessionDescription.Type.OFFER) {
                Log.i(BleRTCTag, "Offer")
                rtcClient.iceCandidateState()
                rtcClient.onRemoteSessionReceived(sessionDescription, object : AppSdpObserver() {
                    override fun onSetSuccess() {
                        Log.i("!!!!", "!!! onSetSuccess")
                        rtcClient.iceCandidateState()
                        super.onSetSuccess()
                        rtcClient.answer(object : AppSdpObserver() {
                            override fun onCreateSuccess(p0: SessionDescription?) {
                                rtcClient.iceCandidateState()
                                super.onCreateSuccess(p0)
                                rtcClient.setLocalDescription(p0!!, object : AppSdpObserver() {
                                    override fun onSetSuccess() {
                                        super.onSetSuccess()

                                        rtcClient.iceCandidateState()
                                        Log.i(BleRTCTag, "On Set local DESC")
                                        sendData(offerGson.toJson(p0!!))
                                    }

                                    override fun onSetFailure(p0: String?) {
                                        super.onSetFailure(p0)
                                        Log.i(BleRTCTag, "onSetFailure ${p0}")
                                    }
                                })
                            }

                            override fun onCreateFailure(p0: String?) {
                                super.onCreateFailure(p0)
                                Log.i(BleRTCTag, "onCreateFailure ${p0}")
                            }
                        })
                    }
                })

            }
        }
    }
}