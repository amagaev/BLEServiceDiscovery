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
import io.ktor.util.KtorExperimentalAPI
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import java.io.UnsupportedEncodingException

@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private lateinit var signallingClient: SignallingClient
    private lateinit var rtcClient: RTCClient
    private lateinit var bluetoothManager: BluetoothManager
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

    fun sendData(message: String, device: BluetoothDevice) {
        try {
            object : Thread() {
                override fun run() {

                    performSend(message, device)
                }
            }.start()

        } catch (e: Exception) {
            // Part of my library - commented for this example
            // this.activity.extShowException (e)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        private var data: ByteArray = ByteArray(0)

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("TAG", "BluetoothDevice CONNECTED: $device")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
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
                                onDataReceived(message, device)
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

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            signallingClient.send(p0)
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
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
//                    signallingClient.send(p0)
                    rtcClient.addIceCandidate(p0)
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    p0?.videoTracks?.get(0)?.addSink(remote_view)
                }
            }
        )
        rtcClient.initSurfaceView(remote_view)
        rtcClient.initSurfaceView(local_view)
        rtcClient.startLocalVideoCapture(local_view)
        signallingClient = SignallingClient(createSignallingClientListener())
        //call_button.setOnClickListener { rtcClient.call(sdpObserver) }
    }

    private fun createSignallingClientListener() = object : SignallingClientListener {
        override fun onConnectionEstablished() {
            call_button.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            rtcClient.answer(sdpObserver)
            remote_view_loading.isGone = true
        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            remote_view_loading.isGone = true
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
        }
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

    fun onDataReceived(data: String, device: BluetoothDevice) {
        Log.i("TAG", "onDataReceived")
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(SessionDescription::class.java, SessionDescriptionDeserializer())
            .registerTypeAdapter(SessionDescription::class.java, SessionDescriptionSerializer())
            .create()
        val sessionDescription = gson.fromJson(data, SessionDescription::class.java)

        // Handle
        if (sessionDescription.type == SessionDescription.Type.OFFER) {
            Log.i("TAG", "OFFER")
            rtcClient.onRemoteSessionReceived(sessionDescription)
            Log.i("TAG", "answer")
            rtcClient.answer(object : AppSdpObserver() {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    super.onCreateSuccess(p0)
                    sendData(gson.toJson(p0!!), device)
                }})
        }
    }
}
