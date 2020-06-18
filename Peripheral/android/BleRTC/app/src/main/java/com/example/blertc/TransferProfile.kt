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

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import java.util.*


/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
object TransferProfile {

    val TRANSFER_SERVICE: UUID = UUID.fromString("E20A39F4-73F5-4BC4-A12F-17D1AD07A961")

    val TRANSFER_TX_CHARACTERISTIC: UUID = UUID.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D4")
    val TRANSFER_RX_CHARACTERISTIC: UUID = UUID.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D5")

    val TRANSFER_TX_DESCRIPTOR: UUID = UUID.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D0")

    fun createTransferService(): BluetoothGattService {
        val service = BluetoothGattService(TRANSFER_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Current Time characteristic
        val tx = BluetoothGattCharacteristic(TRANSFER_TX_CHARACTERISTIC,
                //Read-only characteristic, supports notifications
                 BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)

        tx.addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PERMISSION_WRITE))
//        val configDescriptor = BluetoothGattDescriptor(TRANSFER_TX_DESCRIPTOR,
//                //Read/write descriptor
//                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
//        configDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//        currentTime.addDescriptor(configDescriptor)

        // Local Time Information characteristic
        val rx = BluetoothGattCharacteristic(TRANSFER_RX_CHARACTERISTIC,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE)

        service.addCharacteristic(tx)
        service.addCharacteristic(rx)

        return service
    }
}
