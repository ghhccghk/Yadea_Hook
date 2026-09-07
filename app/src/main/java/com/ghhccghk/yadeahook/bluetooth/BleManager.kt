package com.ghhccghk.yadeahook.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT = 10000L
    }
    
    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothScanner: BluetoothLeScanner? = null
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    var connectionStateCallback: ConnectionStateCallback? = null
    var dataCallback: DataCallback? = null
    var scanCallback: DeviceScanCallback? = null
    
    var currentVersion: CoyoteProtocol.Version = CoyoteProtocol.Version.V3
    
    var isConnected: Boolean = false
        private set
    
    var batteryLevel: Int = -1
        private set
    
    interface ConnectionStateCallback {
        fun onConnecting(device: BluetoothDevice)
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected(device: BluetoothDevice, status: Int)
        fun onConnectionFailed(device: BluetoothDevice, status: Int)
    }
    
    interface DataCallback {
        fun onStrengthUpdate(sequenceNumber: Int, strength: Int)
        fun onBatteryLevelUpdate(level: Int)
        fun onDataReceived(data: ByteArray)
    }
    
    interface DeviceScanCallback {
        fun onDeviceFound(device: BluetoothDevice, rssi: Int, name: String?)
        fun onScanComplete()
        fun onScanFailed(errorCode: Int)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected: " + device.address)
                    isConnected = true
                    handler.post { connectionStateCallback?.onConnected(device) }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected: " + device.address)
                    isConnected = false
                    writeCharacteristic = null
                    notifyCharacteristic = null
                    handler.post { connectionStateCallback?.onDisconnected(device, status) }
                    closeGatt()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                setupCharacteristics(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: " + status)
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            val hexString = data.joinToString(" ") { String.format("%02X", it) }
            Log.d(TAG, "Data received: " + hexString)
            
            handler.post {
                if (data.size >= 3 && data[0] == 0xB1.toByte()) {
                    CoyoteProtocol.parseB1Response(data)?.let { (seq, strength) ->
                        dataCallback?.onStrengthUpdate(seq, strength)
                    }
                }
                
                if (characteristic.uuid.toString().equals(CoyoteProtocol.UUID.BATTERY_V3, ignoreCase = true)) {
                    CoyoteProtocol.parseBatteryLevel(data)?.let { level ->
                        batteryLevel = level
                        dataCallback?.onBatteryLevelUpdate(level)
                    }
                }
                
                dataCallback?.onDataReceived(data)
            }
        }
        
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value
                val hexString = data?.joinToString(" ") { String.format("%02X", it) }
                Log.d(TAG, "Read data: " + hexString)
                
                handler.post {
                    if (characteristic.uuid.toString().equals(CoyoteProtocol.UUID.BATTERY_V3, ignoreCase = true)) {
                        data?.let {
                            CoyoteProtocol.parseBatteryLevel(it)?.let { level ->
                                batteryLevel = level
                                dataCallback?.onBatteryLevelUpdate(level)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName
            val rssi = result.rssi
            
            if (name != null && (name.startsWith("47L121000") || name.startsWith("47L120100"))) {
                Log.d(TAG, "Device found: " + name + " (" + device.address + ")")
                handler.post { scanCallback?.onDeviceFound(device, rssi, name) }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: " + errorCode)
            isScanning = false
            handler.post { scanCallback?.onScanFailed(errorCode) }
        }
    }
    
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }
    
    fun startScan() {
        if (isScanning) return
        
        bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothScanner == null) {
            Log.e(TAG, "Cannot get scanner")
            scanCallback?.onScanFailed(-1)
            return
        }
        
        isScanning = true
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bluetoothScanner?.startScan(null, settings, leScanCallback)
        Log.d(TAG, "Start scanning")
        
        handler.postDelayed({
            if (isScanning) {
                stopScan()
            }
        }, SCAN_TIMEOUT)
    }
    
    fun stopScan() {
        if (!isScanning) return
        
        bluetoothScanner?.stopScan(leScanCallback)
        isScanning = false
        Log.d(TAG, "Stop scanning")
        
        handler.post { scanCallback?.onScanComplete() }
    }
    
    fun connect(device: BluetoothDevice, version: CoyoteProtocol.Version = CoyoteProtocol.Version.V3) {
        currentVersion = version
        handler.post { connectionStateCallback?.onConnecting(device) }
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Connecting: " + device.address)
    }
    
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }
    
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    
    private fun setupCharacteristics(gatt: BluetoothGatt) {
        val (serviceUuid, writeUuid, notifyUuid) = when (currentVersion) {
            CoyoteProtocol.Version.V3 -> CoyoteProtocol.getV3Uuids()
            CoyoteProtocol.Version.V2 -> CoyoteProtocol.getV2Uuids()
        }
        
        val service: BluetoothGattService? = gatt.getService(UUID.fromString(serviceUuid))
        
        if (service == null) {
            Log.e(TAG, "Service not found: " + serviceUuid)
            gatt.services.forEach { s ->
                Log.d(TAG, "Available service: " + s.uuid)
            }
            return
        }
        
        writeCharacteristic = service.getCharacteristic(UUID.fromString(writeUuid))
        notifyCharacteristic = service.getCharacteristic(UUID.fromString(notifyUuid))
        
        if (writeCharacteristic == null) {
            Log.e(TAG, "Write characteristic not found: " + writeUuid)
        }
        
        if (notifyCharacteristic == null) {
            Log.e(TAG, "Notify characteristic not found: " + notifyUuid)
        }
        
        if (notifyCharacteristic != null) {
            gatt.setCharacteristicNotification(notifyCharacteristic, true)
            
            val descriptor = notifyCharacteristic?.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            
            Log.d(TAG, "Notifications enabled")
        }
        
        if (currentVersion == CoyoteProtocol.Version.V3) {
            val batteryCharacteristic = service.getCharacteristic(
                UUID.fromString(CoyoteProtocol.UUID.BATTERY_V3)
            )
            if (batteryCharacteristic != null) {
                gatt.readCharacteristic(batteryCharacteristic)
            }
        }
        
        Log.d(TAG, "Characteristics setup complete")
    }
    
    fun sendB0Command(command: CoyoteProtocol.B0Command): Boolean {
        val characteristic = writeCharacteristic ?: run {
            Log.e(TAG, "Write characteristic not initialized")
            return false
        }
        
        if (!isConnected) {
            Log.e(TAG, "Device not connected")
            return false
        }
        
        val data = when (currentVersion) {
            CoyoteProtocol.Version.V3 -> CoyoteProtocol.encodeB0V3(command)
            CoyoteProtocol.Version.V2 -> CoyoteProtocol.encodeB0V3(command)
        }
        
        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        
        val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Send B0: " + hexString)
        
        return success
    }
    
    fun setStrength(strengthA: Int, strengthB: Int): Boolean {
        val command = CoyoteProtocol.createSetStrengthCommand(strengthA, strengthB)
        return sendB0Command(command)
    }
    
    fun setStrength(channel: CoyoteProtocol.Channel, strength: Int): Boolean {
        return when (channel) {
            CoyoteProtocol.Channel.A -> setStrength(strength, 0)
            CoyoteProtocol.Channel.B -> setStrength(0, strength)
            CoyoteProtocol.Channel.BOTH -> setStrength(strength, strength)
        }
    }
    
    fun zeroStrength(): Boolean {
        val command = CoyoteProtocol.createZeroStrengthCommand()
        return sendB0Command(command)
    }
    
    fun readBatteryLevel() {
        if (currentVersion != CoyoteProtocol.Version.V3) return
        
        val service = bluetoothGatt?.getService(UUID.fromString(CoyoteProtocol.UUID.SERVICE_V3))
        val characteristic = service?.getCharacteristic(UUID.fromString(CoyoteProtocol.UUID.BATTERY_V3))
        
        if (characteristic != null) {
            bluetoothGatt?.readCharacteristic(characteristic)
        }
    }
    
    fun release() {
        stopScan()
        disconnect()
        closeGatt()
        handler.removeCallbacksAndMessages(null)
    }
}