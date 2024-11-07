package com.tertiarybrewery.kegscales

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE
import java.nio.ByteBuffer
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.timerTask


@Suppress("DEPRECATION")
class KegScaleConnector(val context: Context) {
    private var configListener: ConfigListener? = null
    var bleListener: BleListener? = null
    private val kegScaleUUID = UUID.fromString("28f273ff-9f53-45d4-852a-bfb214442d44")
    private val remainingUUID = UUID.fromString("28f273fe-9f53-45d4-852a-bfb214442d44")
    private val pouredUUID = UUID.fromString("28f273fd-9f53-45d4-852a-bfb214442d44")
    private val configStatusUUID = UUID.fromString("28f273fc-9f53-45d4-852a-bfb214442d44")
    private val configStoreUUID = UUID.fromString("28f273fb-9f53-45d4-852a-bfb214442d44")
    private val configVolumeUUID = UUID.fromString("28f273fa-9f53-45d4-852a-bfb214442d44")
    private val nameUUID = UUID.fromString("28f273f9-9f53-45d4-852a-bfb214442d44")
    private val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val kegScales = HashMap<String, ConnectedKegScale>()
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }


    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        .build()


    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            with(result.device) {
                if (kegScales[address]==null || kegScales[address]?.gatt ==null) {
                    kegScales[address] = ConnectedKegScale(address)
                    kegScales[address]?.device = this
                    Log.i(
                        "ScanCallback",
                        "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address, data: $uuids, gatt: " + kegScales[address]?.gatt.toString()
                    )
                }
            }
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            Log.w("BluetoothGattCallback", "Connection State Change to $deviceAddress")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    kegScales[deviceAddress]?.connected=TRUE
                    gatt?.discoverServices()
                    if (connectDisconnected() == false) {
                        Handler(Looper.getMainLooper()).post  {
                            bleListener?.onScanComplete(kegScales)
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    bleListener?.onDisconnect(deviceAddress)
                    kegScales[deviceAddress]?.gatt?.close()
                    kegScales[deviceAddress]?.gatt=null

                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                bleListener?.onDisconnect(deviceAddress)
                kegScales[deviceAddress]?.gatt?.close()
                kegScales[deviceAddress]?.gatt=null
                connectDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val deviceAddress = gatt.device.address
            Log.w("BluetoothGattCallback", "Successfully discovered to ${gatt.services}")
            kegScales[deviceAddress]?.services = gatt.services
            gatt.services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString(
                    separator = "\n|--",
                    prefix = "|--"
                ) { it.uuid.toString() }
                Log.v(
                    "printGattTable",
                    "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
                )
            }
            readCharacteristic(gatt.device.address, kegScaleUUID, nameUUID)
            readCharacteristic(gatt.device.address, kegScaleUUID, configVolumeUUID)
            enableNotifications(gatt.device.address, kegScaleUUID, pouredUUID)

            Timer().schedule(timerTask {
                readCharacteristic(gatt.device.address, kegScaleUUID, remainingUUID)
            },2000,30000)

        }

        @SuppressLint("MissingPermission")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            val kegId = gatt.device.address.toString()
            if (characteristic.uuid == remainingUUID) {
                val value = ByteBuffer.wrap(characteristic.value).getInt()

                val remaining = (value*100)/ (kegScales[kegId]!!.volume )
                bleListener?.onKegRead(kegId, remaining)
            }
            if (characteristic.uuid == configStatusUUID) {
                val value = ByteBuffer.wrap(characteristic.value).getInt()

                configListener?.onConfigStatus(kegId, value)
            }
            if (characteristic.uuid == configVolumeUUID) {
                val value = ByteBuffer.wrap(characteristic.value).getInt()

                kegScales[kegId]?.volume = value
                Log.v("Keg Volume", "Keg $kegId : Volume " + value.toString())
            }
            if (characteristic.uuid == nameUUID) {
                val name = characteristic.value.toString(Charsets.UTF_8)

                kegScales[kegId]?.name = name
                bleListener?.onKegNameRead(kegId, name)
                Log.v("Keg Name", "Keg $kegId : Name $name")
            }
            kegScales[kegId]?.completedCommand()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = ByteBuffer.wrap(characteristic.value).getInt()
            val kegId = gatt.device.address.toString()
            bleListener?.onPour(kegId, value)
            Log.i("bluetooth", "Notify-is - $value")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(device: String, serviceUUID: UUID, characteristicUUID: UUID) {
        val service = kegScales[device]?.gatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)
        Log.i("bluetooth", "Enabling notifications")
        if (characteristic != null) {

            val descriptor = characteristic.getDescriptor(cccdUUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            kegScales[device]?.gatt?.setCharacteristicNotification(characteristic, true)
            kegScales[device]?.gatt?.writeDescriptor(descriptor)
            Log.i("bluetooth", "Notifications enabled")
        }
        else {
            Log.e("bluetooth", "Notify connect issue")
        }
    }
    fun getKegScales(kegId: String): ConnectedKegScale? {
        return kegScales[kegId]
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(device: String, serviceUUID: UUID, characteristicUUID: UUID) {
        val service = kegScales[device]?.gatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)

        if (characteristic != null) {
            val result = kegScales[device]?.addCommand(Runnable {

                    Log.v("bluetooth", "Readable " + characteristic.permissions)
                    val success = kegScales[device]?.gatt?.readCharacteristic(characteristic)
                    Log.v("bluetooth", "$characteristicUUID Read status: $success")

            })
        }
    }

    fun requestConfigStatus(kegId: String) {
        Log.v("bluetooth", "Reading Config")
        readCharacteristic(kegId, kegScaleUUID, configStatusUUID)
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        val filter = ScanFilter.Builder().setDeviceName("kegscale").build()

        Handler(Looper.getMainLooper()).postDelayed({
            bleScanner.stopScan(scanCallback)
            connectDisconnected()
        }, 5000)
        bleScanner.startScan(listOf(filter), scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun connectDisconnected(): Boolean? {
        for (kegid in kegScales.keys.shuffled()) {
            val kegScale = kegScales[kegid]
            if (kegScale != null) {
                if (!kegScale.connected) {
                    Log.v("connectDisconnected", "Connecting $kegid")
                    kegScale?.gatt =
                        kegScale.device?.connectGatt(context, false, gattCallback, TRANSPORT_LE)
                    return TRUE
                }
            }
        }
        return FALSE
    }

    interface BleListener {
        fun onScanComplete(kegScales: HashMap<String, ConnectedKegScale>);
        fun onKegRead(kegAddress: String, remaining: Int);
        fun onPour(kegAddress: String, poured:Int)
        fun onDisconnect(kegAddress:String)
        fun onKegNameRead(kegAddress: String, name: String)
    }
    fun setListener(activity: Activity) {
        bleListener = activity as BleListener
    }

    interface ConfigListener {
        fun onConfigStatus(kegAddress: String, configStatus: Int)
    }
    fun setConfigListener(dialog: ConfigureDialog) {
        configListener = dialog
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun triggerConfig(kegId: String) {
        val service = kegScales[kegId]?.gatt?.getService(kegScaleUUID)
        val characteristic = service?.getCharacteristic(configStoreUUID)
        val data = "update".toByteArray()
        Log.v("Config", "Updated")
        if (characteristic != null) {
            kegScales[kegId]?.gatt?.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }

    fun getVolume(kegId: String): Int {
        return kegScales[kegId]?.volume ?: 20000
    }
    fun getName(kegId: String): String {
        return kegScales[kegId]?.name ?: "KegScale"
    }
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun setVolume(kegId: String, volume: Int) {
        val service = kegScales[kegId]?.gatt?.getService(kegScaleUUID)
        val characteristic = service?.getCharacteristic(configStoreUUID)
        val data = volume.toString().toByteArray()
        if (characteristic != null) {
            kegScales[kegId]?.gatt?.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    fun setName(kegId: String, nameString: String) {
        kegScales[kegId]?.name = nameString
        val service = kegScales[kegId]?.gatt?.getService(kegScaleUUID)
        val characteristic = service?.getCharacteristic(nameUUID)
        val data = nameString.toByteArray()
        if (characteristic != null) {
            kegScales[kegId]?.gatt?.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }
    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        for (kegId in kegScales.keys) {
            kegScales[kegId]?.gatt?.disconnect()
        }
    }

}

