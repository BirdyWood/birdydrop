package io.github.birdywood.birdydrop.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.timerTask


data class DeviceBluetooth (
    var name:String,
    val uid:String,
    var ip:String,
    val lastSeen:Long = System.currentTimeMillis(),
    var isConnected: IsConnected = IsConnected.NO,
    var gatt: BluetoothGatt?=null,
    var device: BluetoothDevice?=null
) {
    enum class IsConnected {
        YES,
        NO,
        IN_PROGRESS;
        operator fun not():Boolean {
            return when (this) {
                YES -> false
                NO -> true
                IN_PROGRESS -> false
            }
        }

    }
}

@SuppressLint("MissingPermission")
private fun MutableList<DeviceBluetooth>.addDevice(result: ScanResult):Boolean {
    this.getDevice(result)?.let {
        return true
    }
    return this.add(
        DeviceBluetooth(
        name = result.device.name ?: "Unknown",
        uid = result.device.address,
        ip = result.device.address
    )
    )
}


@SuppressLint("MissingPermission")
private fun MutableList<DeviceBluetooth>.addDevice(device: BluetoothDevice):Boolean {
    this.getDevice(device)?.let {
        it.device=device
        return true
    }
    return this.add(
        DeviceBluetooth(
        name = device.name ?: "Unknown",
        uid = device.address,
        ip = device.address,
        device = device
    )
    )
}


private fun MutableList<DeviceBluetooth>.getDevice(result: ScanResult): DeviceBluetooth? {
    for (u in this) {
        if (u.uid == result.device.address) {
            return u
        }
    }
    return null
}
private fun MutableList<DeviceBluetooth>.getDevice(device: BluetoothDevice): DeviceBluetooth? {
    for (u in this) {
        if (u.uid == device.address) {
            return u
        }
    }
    return null
}
private fun MutableList<DeviceBluetooth>.containsUID(user: ScanResult): Boolean {
    for (u in this) {
        if (u.uid == user.device.address) {
            return true
        }
    }
    return false
}
private fun MutableList<DeviceBluetooth>.removeDevice(device: BluetoothDevice) {
    for (u in this) {
        if (u.uid  == device.address) {
            if (u.gatt!=null) {
                u.device = null
            } else {
                this.remove(u)
            }
            return
        }
    }
}
private fun MutableList<DeviceBluetooth>.removeDevice(user: ScanResult) {
    for (u in this) {
        if (u.uid  == user.device.address) {
            this.remove(u)
            return
        }
    }
}



@SuppressLint("MissingPermission")
class BluetoothService(ctx: Context,lister: Listener): GenericService(ctx,lister) {
    val listUsers = mutableListOf<DeviceBluetooth>()
    val serviceUUID: UUID = UUID.fromString("fcce0000-0483-45ec-9fd7-6e8a3d7c0000")
    val serviceUUIDMain: UUID = UUID.fromString("fcce0001-0483-45ec-9fd7-6e8a3d7c0000")
    val serviceUUIDMainDescriptor: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val serviceUUIDMainReadOnly: UUID = UUID.fromString("fcce0002-0483-45ec-9fd7-6e8a3d7c0000")
    val serviceUUIDName: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val characteristicUUIDName: UUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")

    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothManager = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
    private var bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private var bluetoothLeAdvertiser =bluetoothAdapter.bluetoothLeAdvertiser
    private var scanning = false
    private val scanPeriod: Long = 10000
    private val listener = lister
    override val limitSize = 512

    init {
        checkAllPermissions()
        nameDevice = bluetoothAdapter.name
        log("Bluetooth Name: ${bluetoothAdapter.name} Enabled: ${bluetoothAdapter.isEnabled}")

    }

    /* --SERVER-- */

    override fun sendData(usr: String, msg: ByteArray) {
        log("Sending data to $usr: ${msg.decodeToString()}")
        val device= listUsers.find { it.uid == usr }
        if (device?.gatt !=null) {
            log("SEND by Standard Write")
            val characteristic =
                device.gatt!!.getService(serviceUUID)?.getCharacteristic(serviceUUIDMain)
            if (characteristic != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device.gatt!!.writeCharacteristic(
                        characteristic,
                        msg,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    characteristic.value = msg
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    device.gatt!!.writeCharacteristic(characteristic)
                }
            }
        } else if (device?.device != null) {
            log("SEND by Notification")
            val characteristic = bluetoothGattServer
                ?.getService(serviceUUID)
                ?.getCharacteristic(serviceUUIDMain)
            if (characteristic != null) {
                log("Notify: $device")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGattServer?.notifyCharacteristicChanged(device.device!!, characteristic, false, msg)
                } else {
                    characteristic.value = msg
                    bluetoothGattServer?.notifyCharacteristicChanged(device.device!!, characteristic, false)
                }
            }
        } else {
            log("Device not found")
        }
    }

    fun getLocalBluetoothName(): String {
        var name: String = bluetoothAdapter.getName()
        if (name == "") {
            println("Name is null!")
            name = bluetoothAdapter.getAddress()
        }
        return name
    }

    fun preProcess() {
        if (!bluetoothAdapter.isEnabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {advertising()}
        } else advertising()
        listener.onNewDevice()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            log("LE Advertise Started.")
        }
        override fun onStartFailure(errorCode: Int) {
            log( "LE Advertise Failed: $errorCode")
        }
    }

    private fun advertising() {
        bluetoothLeAdvertiser?.let {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false) //WHY ???
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(serviceUUID))
                .build()
            it.startAdvertising(settings, data, advertiseCallback)
        }
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        bluetoothGattServer?.addService(createService())
    }

    private fun createService(): BluetoothGattService {
        val service = BluetoothGattService(serviceUUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val mainCharacteristic = BluetoothGattCharacteristic(serviceUUIDMain,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ  or BluetoothGattCharacteristic.PERMISSION_WRITE)
        val mainDescriptor = BluetoothGattDescriptor(serviceUUIDMainDescriptor,BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
        mainCharacteristic.addDescriptor(mainDescriptor)
        service.addCharacteristic(mainCharacteristic)
        service.addCharacteristic(BluetoothGattCharacteristic(serviceUUIDMainReadOnly,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ))
        return service
    }

    fun stopService(){
        if (bluetoothGattServer == null) return
        if (bluetoothLeAdvertiser == null) return
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        bluetoothGattServer?.close()
        bluetoothGattServer = null
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            log("BluetoothDevice StateChange: $device $status $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("BluetoothDevice CONNECTED: $device")
                listUsers.addDevice(device)
                listener.onNewDevice()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log( "BluetoothDevice DISCONNECTED: $device")
                listUsers.removeDevice(device)
                listener.onNewDevice()
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            log("Notification Sent: $device $status")
        }
        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            if (descriptor != null) {
                if (serviceUUIDMainDescriptor == descriptor.uuid) {
                    log("Read Descriptor: ${descriptor.uuid}")
                    if (device!=null) {
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        listUsers.getDevice(device)?.isConnected = DeviceBluetooth.IsConnected.YES
                        listener.onNewDevice()
                    }

                } else {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            if (responseNeeded) bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            when {
                serviceUUIDMain == characteristic.uuid -> {
                    log("Read SERVICE_UUID_MAIN")
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "SERVICE_UUID_CHAR1".toByteArray())
                }
                serviceUUIDMainReadOnly == characteristic.uuid -> {
                    log("Read SERVICE_UUID_READONLY")
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "SERVICE_UUID_CHAR2".toByteArray())
                }
                else -> {
                    log("Invalid Characteristic Read: " + characteristic.uuid)
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            log("New MTU: $mtu")

        }
        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value )
            log("Write SERVICE_UUID_MAIN: ${value.toString()}, UUID: ${characteristic?.uuid}")
            if (characteristic != null) {
                when {
                    serviceUUIDMain == characteristic.uuid -> {
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        if (value != null && device != null)
                            onReceived(device.address,value)
                    }
                    else -> {
                        log("Invalid Characteristic Write: " + characteristic.uuid + " " + value?.decodeToString())
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            }
        }
    }



    /* --CLIENT-- */
    override fun sendPing() {
        if (!scanning) {
            listener.onNewDevice()
            bluetoothLeScanner?.startScan(leScanCallback)
            scanning = true
            Timer().schedule(timerTask {
                bluetoothLeScanner?.stopScan(leScanCallback)
                scanning = false
            }, scanPeriod)
        }
    }
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val isMyService = result.scanRecord?.serviceUuids?.contains(ParcelUuid.fromString(serviceUUID.toString()))
                ?: return
            if (!listUsers.containsUID(result) && isMyService) {
                listUsers.addDevice(result)
                listener.onNewDevice()
            }
            if (isMyService) {
                val device = listUsers.getDevice(result)
                if (device != null && !device.isConnected) {
                    device.isConnected = DeviceBluetooth.IsConnected.IN_PROGRESS
                    listener.onNewDevice()
                    val gattCallback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                            super.onConnectionStateChange(gatt, status, newState)
                            log("Connection State Change on Client BLE: $status $newState")
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    device.isConnected = DeviceBluetooth.IsConnected.YES
                                    device.gatt = gatt
                                    gatt?.requestMtu(512)
                                    listener.onNewDevice()
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    device.gatt = gatt
                                    device.isConnected = DeviceBluetooth.IsConnected.NO
                                    listUsers.removeDevice(result)
                                    listener.onNewDevice()
                                }
                            }
                        }
                        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                            super.onServicesDiscovered(gatt, status)
                            gatt?.services?.forEach { service ->
                                service.characteristics.forEach { characteristic ->
                                    log("Discovered Service: ${service.uuid}- Characteristic: ${characteristic.uuid}")
                                    if (service.uuid == serviceUUIDName && characteristic.uuid == characteristicUUIDName  && characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                                        log("Characteristic Name Found: ${characteristic.uuid} && ${characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ}")
                                        gatt.readCharacteristic( gatt.getService(serviceUUIDName)?.getCharacteristic(characteristicUUIDName))
                                    }
                                    else if (service.uuid == serviceUUID && characteristic.uuid == serviceUUIDMain && characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                        log("Characteristic Notification Found: ${characteristic.uuid} && ${characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ}")
                                        gatt.setCharacteristicNotification(characteristic, true)
                                        val descriptor: BluetoothGattDescriptor? = characteristic?.getDescriptor(serviceUUIDMainDescriptor)
                                        if (descriptor != null) {
                                            Timer().schedule(timerTask {
                                                val i =
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        gatt.writeDescriptor(
                                                            descriptor,
                                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                                        )
                                                    } else {
                                                        descriptor.value =
                                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                                        gatt.writeDescriptor(descriptor)
                                                    }
                                                log("Write Descriptor: $i")
                                            },1000)
                                        }
                                    }
                                }
                            }
                        }
                        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray,
                            status: Int
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                log("Characteristic Read: ${characteristic.uuid}")
                                super.onCharacteristicRead(gatt, characteristic, value, status)
                                when (characteristic.uuid) {
                                    serviceUUIDMain -> {
                                        log("SERVICE_UUID_CHAR1: ${value.decodeToString()}")
                                    }

                                    serviceUUIDMainReadOnly -> {
                                        log("SERVICE_UUID_READONLY: ${value.decodeToString()}")
                                    }

                                    characteristicUUIDName -> {
                                        log("Name Characteristic Read: ${value.decodeToString()}")
                                        device.name = value.decodeToString()
                                        listener.onNewDevice()
                                    }

                                    else -> {
                                        log("Unknown Characteristic Read: " + characteristic.uuid)
                                    }
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt?,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int
                        ) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                log("Characteristic Read: ${characteristic.uuid}")
                                super.onCharacteristicRead(gatt, characteristic, status)
                                val value = characteristic.getStringValue(0)
                                when (characteristic.uuid) {
                                    serviceUUIDMain -> {
                                        log("SERVICE_UUID_CHAR1: $value")
                                    }

                                    serviceUUIDMainReadOnly -> {
                                        log("SERVICE_UUID_READONLY: $value")
                                    }

                                    characteristicUUIDName -> {
                                        log("Name Characteristic Read: $value")
                                        device.name = value
                                        listener.onNewDevice()
                                    }

                                    else -> {
                                        log("Unknown Characteristic Read: " + characteristic.uuid)
                                    }
                                }
                            }
                        }

                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                super.onCharacteristicChanged(gatt, characteristic, value)
                                log("Characteristic Changed: ${characteristic.uuid} ${value.decodeToString()}")
                                onReceived(gatt.device.address, value)
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic
                        ) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                super.onCharacteristicChanged(gatt, characteristic)
                                log("Characteristic Changed: ${characteristic.uuid} ${characteristic.value.decodeToString()}")
                                onReceived(gatt.device.address, characteristic.value)
                            }
                        }

                        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                            super.onMtuChanged(gatt, mtu, status)
                            log("New MTU: $mtu")
                            gatt?.discoverServices()
                        }


                    }
                    log("Connecting to device: ${result.device}")
                    result.device.connectGatt(context, false, gattCallback)
                    log(result.toString())
                    log(result.device.toString())
                }
            }
        }
    }

    private fun checkAllPermissions() {
        if (REQUIRED_PERMISSIONS.all {
                log("$it Granted? ${ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED}")
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }) {
            log("OK")
        } else {
            log("KO")
            ActivityCompat.requestPermissions(context as Activity, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE=985
        val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_ADMIN
            )

        }

    }
}




