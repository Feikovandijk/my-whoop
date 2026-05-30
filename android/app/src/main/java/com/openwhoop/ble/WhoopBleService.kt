package com.openwhoop.ble

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.content.pm.ServiceInfo
import java.text.SimpleDateFormat
import androidx.core.app.NotificationCompat
import com.openwhoop.ble.protocol.*
import com.openwhoop.database.*
import com.openwhoop.sync.*
import kotlinx.coroutines.*
import java.util.*

class WhoopBleService : Service() {

    companion object {
        const val NOTIFICATION_ID = 4040
        val CUSTOM_SERVICE_UUID = UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6")
        val CMD_WRITE_CHAR_UUID = UUID.fromString("61080002-8d6d-82b8-614a-1c8cb0f8dcc6")
        val CMD_NOTIFY_CHAR_UUID = UUID.fromString("61080003-8d6d-82b8-614a-1c8cb0f8dcc6")
        val EVENT_NOTIFY_CHAR_UUID = UUID.fromString("61080004-8d6d-82b8-614a-1c8cb0f8dcc6")
        val DATA_NOTIFY_CHAR_UUID = UUID.fromString("61080005-8d6d-82b8-614a-1c8cb0f8dcc6")
        val HEART_RATE_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATTERY_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        // Singleton instance of LiveState shared with UI
        val liveState = LiveState()
        
        // Static helper for service control
        var activeInstance: WhoopBleService? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var cmdWriteChar: BluetoothGattCharacteristic? = null

    private var store: WhoopStore? = null
    private var collector: Collector? = null
    private var backfiller: Backfiller? = null

    private val reassembler = Reassembler()
    private var seq: Byte = 0
    private var didBond = false
    private var clockRequested = false
    private var connectHandshakeDone = false
    private var backfillStarted = false
    private var backfilling = false
    private var intentionalDisconnect = false

    private val stuckDetector = StuckStrapDetector(stuckAfterSeconds = 600.0, behindGapSeconds = 300)
    private var strapNewestTs: Long? = null
    private var backfillTimeoutJob: Job? = null

    private var uploadJob: Job? = null
    private var backfillJob: Job? = null

    private val backfillChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var backfillConsumerJob: Job? = null


    private var hasRediscoveredServices = false

    // GATT command queue
    private val gattQueue = LinkedList<Runnable>()
    private var isGattBusy = false

    // Frame Router
    private val frameRouter = FrameRouter(liveState).apply {
        onSyncTrigger = { requestSync(BackfillTrigger.STRAP) }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            if (device.address == bluetoothGatt?.device?.address) {
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    log("Bonding completed. Re-discovering services to expose custom characteristics...")
                    val gatt = bluetoothGatt ?: return
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        log("Permission error rediscovering services after bonding")
                    }
                }
            }
        }
    }

    private fun startBackfillConsumer() {
        backfillConsumerJob?.cancel()
        backfillConsumerJob = serviceScope.launch {
            for (frame in backfillChannel) {
                if (backfilling) {
                    if (Backfiller.endData(frame) != null || (frame.size > 4 && (frame[4].toInt() == 47 || frame[4].toInt() == 48 || frame[4].toInt() == 49 || frame[4].toInt() == 50))) {
                        armBackfillTimeout()
                    }
                    backfiller?.ingest(frame)
                    afterBackfillIngest()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))

        startBackfillConsumer()

        serviceScope.launch {
            bootstrapStore()
        }
    }


    private suspend fun bootstrapStore() {
        withContext(Dispatchers.IO) {
            val db = WhoopDatabase.getDatabase(this@WhoopBleService)
            val tempStore = WhoopStore(db)
            tempStore.upsertDevice("my-whoop", null, "WHOOP 4.0")
            
            // Hardcoded research toggle OFF (same as iOS default)
            val enableRawCapture = false 

            store = tempStore
            collector = Collector(this@WhoopBleService, tempStore, "my-whoop", serviceScope, enableRawCapture = enableRawCapture)
            backfiller = Backfiller(
                this@WhoopBleService,
                tempStore,
                "my-whoop",
                ackTrim = { trim, endData -> ackHistoricalChunk(trim, endData) },
                enableRawCapture = enableRawCapture
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        intentionalDisconnect = false
        startScanning()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        unregisterReceiver(bondStateReceiver)
        serviceScope.cancel()
        disconnectGatt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "whoop_ble_service"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OpenWhoop Bluetooth Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, Class.forName("com.openwhoop.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OpenWhoop Connected")
            .setContentText("Streaming telemetry in the background...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startScanning() {
        if (bluetoothGatt != null) {
            log("GATT client already exists, skipping scan.")
            return
        }

        val adapter = bluetoothAdapter
        val manager = bluetoothManager
        if (adapter == null || manager == null) {
            log("Bluetooth adapter or manager not available")
            return
        }

        // 1. Check system-connected GATT devices first (extremely common on Android if official app or system is connected)
        try {
            val connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT)
            for (device in connectedDevices) {
                val name = device.name ?: ""
                if (name.contains("WHOOP", ignoreCase = true)) {
                    log("Found system-connected Whoop device: $name (${device.address}). Connecting directly...")
                    connectToDevice(device)
                    return
                }
            }
        } catch (e: SecurityException) {
            log("Permission error checking connected GATT devices")
        }

        // 2. Check bonded devices next
        try {
            val bondedDevices = adapter.bondedDevices
            if (bondedDevices != null) {
                for (device in bondedDevices) {
                    val name = device.name ?: ""
                    if (name.contains("WHOOP", ignoreCase = true)) {
                        log("Found bonded Whoop device: $name (${device.address}). Connecting directly...")
                        connectToDevice(device)
                        return
                    }
                }
            }
        } catch (e: SecurityException) {
            log("Permission error checking bonded devices")
        }

        // 3. Fall back to scanning
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            log("Scanner not available (Bluetooth disabled?)")
            return
        }

        log("Scanning for Whoop strap service...")
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(CUSTOM_SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"))).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            log("Missing BLE permissions to scan")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = try { device.name } catch (e: SecurityException) { null } ?: result.scanRecord?.deviceName ?: "Unknown"
            if (!name.contains("WHOOP", ignoreCase = true)) {
                // Ignore other heart rate monitors
                return
            }
            log("Discovered Whoop device: $name (${device.address})")
            
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
            } catch (e: SecurityException) {}

            connectToDevice(device)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        log("Connecting to device: ${device.address}")
        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(this, false, gattCallback)
            }
        } catch (e: SecurityException) {
            log("SecurityException connecting to GATT")
        }
    }

    private fun disconnectGatt() {
        intentionalDisconnect = true
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {}
        bluetoothGatt = null
        liveState.connected.value = false
        liveState.bonded.value = false
    }

    // GATT Queue runners
    @Synchronized
    private fun enqueueGatt(runnable: Runnable) {
        gattQueue.add(runnable)
        if (!isGattBusy) {
            runNextGatt()
        }
    }

    @Synchronized
    private fun runNextGatt() {
        val next = gattQueue.poll()
        if (next != null) {
            isGattBusy = true
            next.run()
        } else {
            isGattBusy = false
        }
    }

    private fun gattCompleted(delayMs: Long = 0) {
        if (delayMs > 0) {
            serviceScope.launch {
                delay(delayMs)
                runNextGatt()
            }
        } else {
            runNextGatt()
        }
    }

    private fun subscribeToCharacteristic(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        enqueueGatt {
            try {
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    serviceScope.launch {
                        var success = false
                        var attempts = 0
                        while (!success && attempts < 3) {
                            try {
                                success = gatt.writeDescriptor(descriptor)
                            } catch (e: SecurityException) {
                                break
                            }
                            if (!success) {
                                attempts++
                                delay(50)
                            }
                        }
                        if (!success) {
                            log("GATT write descriptor initiation failed after 3 attempts for: ${char.uuid}")
                            gattCompleted(50)
                        } else {
                            log("GATT write descriptor initiated for: ${char.uuid}")
                        }
                    }
                } else {
                    log("GATT writeDescriptor: descriptor 2902 is null for char: ${char.uuid}")
                    gattCompleted(0)
                }
            } catch (e: SecurityException) {
                log("GATT subscribe failed due to permissions")
                gattCompleted(50)
            }
        }
    }

    private fun sendCommand(gatt: BluetoothGatt, command: WhoopCommand, payload: ByteArray = byteArrayOf(0x00), writeWithResponse: Boolean = true) {
        enqueueGatt {
            val char = cmdWriteChar
            if (char == null) {
                gattCompleted(0)
                return@enqueueGatt
            }
            seq = (seq + 1).toByte()
            val frame = command.frame(seq, payload)
            char.value = frame
            char.writeType = if (writeWithResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            
            serviceScope.launch {
                var success = false
                var attempts = 0
                while (!success && attempts < 3) {
                    try {
                        success = gatt.writeCharacteristic(char)
                    } catch (e: SecurityException) {
                        log("GATT write characteristic failed due to permissions")
                        break
                    }
                    if (!success) {
                        attempts++
                        delay(50)
                    }
                }

                if (!success) {
                    log("GATT write characteristic initiation failed for command ${command.name} after 3 attempts")
                    gattCompleted(50)
                } else if (char.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    // WRITE_TYPE_NO_RESPONSE doesn't trigger onCharacteristicWrite callback,
                    // so we must complete the queue item after a small delay to prevent write overlap.
                    gattCompleted(150)
                }
            }
        }
    }

    private fun startSubscribing(gatt: BluetoothGatt) {
        val customService = gatt.getService(CUSTOM_SERVICE_UUID)
        val hrService = gatt.getService(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"))
        val batService = gatt.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))

        if (customService != null) {
            val cNotify = customService.getCharacteristic(CMD_NOTIFY_CHAR_UUID)
            val eNotify = customService.getCharacteristic(EVENT_NOTIFY_CHAR_UUID)
            val dNotify = customService.getCharacteristic(DATA_NOTIFY_CHAR_UUID)
            
            cNotify?.let { subscribeToCharacteristic(gatt, it) }
            eNotify?.let { subscribeToCharacteristic(gatt, it) }
            dNotify?.let { subscribeToCharacteristic(gatt, it) }
        }

        if (hrService != null) {
            val hrChar = hrService.getCharacteristic(HEART_RATE_CHAR_UUID)
            hrChar?.let { subscribeToCharacteristic(gatt, it) }
        }

        if (batService != null) {
            val batChar = batService.getCharacteristic(BATTERY_CHAR_UUID)
            batChar?.let { subscribeToCharacteristic(gatt, it) }
        }

        // Verify bonding works and kick handshake
        enqueueGatt {
            val char = cmdWriteChar
            if (char != null) {
                seq = (seq + 1).toByte()
                val frame = WhoopCommand.GET_BATTERY_LEVEL.frame(seq, byteArrayOf(0x00))
                char.value = frame
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                serviceScope.launch {
                    var success = false
                    var attempts = 0
                    while (!success && attempts < 3) {
                        try {
                            success = gatt.writeCharacteristic(char)
                        } catch (e: SecurityException) {
                            break
                        }
                        if (!success) {
                            attempts++
                            delay(50)
                        }
                    }
                    if (!success) {
                        log("GATT verification write failed to initiate after 3 attempts")
                        gattCompleted(50)
                    }
                }
            } else {
                gattCompleted(0)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                liveState.connected.value = true
                log("GATT connected, discovering services...")
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                serviceScope.launch {
                    collector?.flush()
                }
                liveState.connected.value = false
                liveState.bonded.value = false
                didBond = false
                clockRequested = false
                connectHandshakeDone = false
                backfillStarted = false
                backfilling = false
                hasRediscoveredServices = false
                
                uploadJob?.cancel()
                backfillJob?.cancel()
                backfillTimeoutJob?.cancel()

                log("GATT disconnected")
                try {
                    gatt.close()
                } catch (e: SecurityException) {}
                if (bluetoothGatt == gatt) {
                    bluetoothGatt = null
                }

                if (!intentionalDisconnect) {
                    log("Retrying connection in 3s...")
                    serviceScope.launch {
                        delay(3000)
                        startScanning()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(CUSTOM_SERVICE_UUID)
                cmdWriteChar = service?.getCharacteristic(CMD_WRITE_CHAR_UUID)

                try {
                    val bondState = gatt.device.bondState
                    if (bondState == BluetoothDevice.BOND_NONE) {
                        log("Initiating OS bonding...")
                        gatt.device.createBond()
                    } else if (bondState == BluetoothDevice.BOND_BONDED) {
                        if (service != null && cmdWriteChar != null) {
                            log("Device bonded and custom service verified. Subscribing...")
                            startSubscribing(gatt)
                        } else if (!hasRediscoveredServices) {
                            log("Device bonded but custom service not found. Re-discovering services once...")
                            hasRediscoveredServices = true
                            gatt.discoverServices()
                        } else {
                            log("Device bonded but custom service still missing after re-discovery. Subscribing anyway...")
                            startSubscribing(gatt)
                        }
                    } else {
                        log("Device bonding in progress... waiting for bond completion.")
                    }
                } catch (e: SecurityException) {
                    log("Permission error checking bond state")
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            log("onDescriptorWrite: char=${descriptor.characteristic.uuid} status=$status")
            gattCompleted()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            log("onCharacteristicWrite: char=${characteristic.uuid} status=$status")
            if (characteristic.uuid == CMD_WRITE_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                if (!didBond) {
                    didBond = true
                    liveState.bonded.value = true
                    log("BOND CONFIRMED. Custom telemetry channels verified.")
                }
                
                if (!connectHandshakeDone) {
                    runHandshake(gatt)
                }
            }
            gattCompleted()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            android.util.Log.i("WhoopBleService", "CharChanged UUID=${characteristic.uuid} len=${bytes.size}")
            when (characteristic.uuid) {
                HEART_RATE_CHAR_UUID -> {
                    parseStandardHR(bytes)
                }
                BATTERY_CHAR_UUID -> {
                    if (bytes.isNotEmpty()) {
                        val pct = bytes[0].toInt() and 0xFF
                        liveState.setBattery(pct.toDouble())
                    }
                }
                CMD_NOTIFY_CHAR_UUID, EVENT_NOTIFY_CHAR_UUID, DATA_NOTIFY_CHAR_UUID -> {
                    val frames = reassembler.feed(bytes)
                    for (frame in frames) {
                        frameRouter.handle(frame)

                        // Parse clockRef if GET_CLOCK response is received
                        val parsed = Interpreter.parseFrame(this@WhoopBleService, frame)
                        android.util.Log.i("WhoopBleService", "Frame parsed: ok=${parsed.ok} type=${parsed.typeName} cmd=${parsed.cmdName} keys=${parsed.parsed.keys}")
                        if (parsed.ok && parsed.crcOK == true && parsed.typeName == "COMMAND_RESPONSE") {
                            val deviceClock = parsed.parsed["clock"]?.let {
                                when (it) {
                                    is ParsedValue.IntVal -> it.value
                                    is ParsedValue.DoubleVal -> it.value.toLong()
                                    is ParsedValue.StringVal -> it.value.toLongOrNull()
                                    else -> null
                                }
                            }
                            if (deviceClock != null) {
                                val wall = System.currentTimeMillis() / 1000
                                val ref = ClockRef(deviceClock, wall)
                                collector?.clockRef = ref
                                backfiller?.clockRef = ref
                                log("Clock correlated: device=$deviceClock wall=$wall")
                            }
                        }
                        
                        // Debug: log frame type and HR for type-40
                        if (frame.size > 4) {
                            val frameType = frame[4].toInt() and 0xFF
                            if (frameType == 40 && frame.size > 13) {
                                val hr = frame[12].toInt() and 0xFF
                                android.util.Log.i("WhoopBleService", "REALTIME_DATA frame: HR=$hr")
                            }
                        }

                        if (frame.size > 6 && (frame[6].toInt() and 0xFF) == WhoopCommand.GET_DATA_RANGE.value.toInt()) {
                            strapNewestTs = dataRangeNewestUnix(frame)
                        }

                        if (backfilling) {
                            if (isOffloadFrame(frame)) {
                                backfillChannel.trySend(frame)
                            }
                        } else {
                            collector?.ingest(frame)
                        }
                    }
                }
                else -> {
                    android.util.Log.i("WhoopBleService", "Notification from unknown UUID: ${characteristic.uuid}")
                }
            }
        }
    }

    private fun runHandshake(gatt: BluetoothGatt) {
        connectHandshakeDone = true
        backfillStarted = true

        sendCommand(gatt, WhoopCommand.GET_HELLO_HARVARD)
        sendCommand(gatt, WhoopCommand.GET_ADVERTISING_NAME_HARVARD)
        
        val nowSec = System.currentTimeMillis() / 1000
        val setClockPayload = byteArrayOf(
            (nowSec and 0xFF).toByte(),
            ((nowSec shr 8) and 0xFF).toByte(),
            ((nowSec shr 16) and 0xFF).toByte(),
            ((nowSec shr 24) and 0xFF).toByte(),
            0, 0, 0, 0
        )
        sendCommand(gatt, WhoopCommand.SET_CLOCK, setClockPayload)
        sendCommand(gatt, WhoopCommand.GET_CLOCK, byteArrayOf())
        sendCommand(gatt, WhoopCommand.SEND_R10_R11_REALTIME, byteArrayOf(0x00))
        sendCommand(gatt, WhoopCommand.TOGGLE_REALTIME_HR, byteArrayOf(0x01))
        sendCommand(gatt, WhoopCommand.GET_DATA_RANGE, byteArrayOf())
        
        serviceScope.launch {
            syncAlarmToStrap()
            delay(1500)
            requestSync(BackfillTrigger.CONNECT)
        }
        
        startUploadTimer()
        startBackfillTimer()
    }

    private fun parseStandardHR(data: ByteArray) {
        val result = StandardHeartRate.parse(data) ?: return
        android.util.Log.i("WhoopBleService", "parseStandardHR: dataLen=${data.size} hr=${result.hr} rrCount=${result.rr.size}")
        if (result.rr.isNotEmpty()) {
            liveState.updateRR(result.rr)
        }
        if (result.hr > 0) {
            liveState.heartRate.value = result.hr
        }
    }

    private fun dataRangeNewestUnix(frame: ByteArray): Long? {
        if (frame.size <= 7) return null
        var newest: Long? = null
        var i = 7
        while (i + 4 <= frame.size) {
            val w = (frame[i].toLong() and 0xFFL) or
                    ((frame[i+1].toLong() and 0xFFL) shl 8) or
                    ((frame[i+2].toLong() and 0xFFL) shl 16) or
                    ((frame[i+3].toLong() and 0xFFL) shl 24)
            if (w in 1700000000L..1900000000L) {
                newest = Math.max(newest ?: 0L, w)
            }
            i += 4
        }
        return newest
    }

    private fun isOffloadFrame(frame: ByteArray): Boolean {
        if (frame.size <= 4) return false
        val type = frame[4].toInt() and 0xFF
        return type == 47 || type == 48 || type == 49 || type == 50
    }

    private fun ackHistoricalChunk(trim: Long, endData: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val payload = ByteArray(1 + endData.size)
        payload[0] = 0x01
        System.arraycopy(endData, 0, payload, 1, endData.size)
        log("Acking historical chunk. Trim: $trim")
        sendCommand(gatt, WhoopCommand.HISTORICAL_DATA_RESULT, payload, writeWithResponse = true)
    }

    private fun beginBackfill() {
        if (!connectHandshakeDone) return
        val back = backfiller ?: return
        while (backfillChannel.tryReceive().isSuccess) {
            // Drain any leftover/stale frames
        }
        back.begin()
        backfilling = true
        val gatt = bluetoothGatt ?: return
        sendCommand(gatt, WhoopCommand.SEND_HISTORICAL_DATA, byteArrayOf(0x00), writeWithResponse = true)
        armBackfillTimeout()
        log("Backfill session started.")
    }

    private suspend fun afterBackfillIngest() {
        if (backfilling && backfiller?.isBackfilling == false) {
            exitBackfilling("HISTORY_COMPLETE")
        }
    }

    private fun armBackfillTimeout() {
        backfillTimeoutJob?.cancel()
        backfillTimeoutJob = serviceScope.launch {
            delay(60000) // 60s idle timeout
            backfiller?.timeoutFired()
            exitBackfilling("timeout")
        }
    }

    private fun exitBackfilling(reason: String) {
        if (!backfilling) return
        backfilling = false
        backfillTimeoutJob?.cancel()
        backfillTimeoutJob = null
        log("Backfill session ended. Reason: $reason")
        
        uploadOpportunistically()
        
        if (reason == "HISTORY_COMPLETE") {
            val now = System.currentTimeMillis() / 1000.0
            liveState.lastSyncedAt.value = now
        }
        
        checkStrapLiveness()
    }

    private fun checkStrapLiveness() {
        val strapNewest = strapNewestTs ?: return
        val gatt = bluetoothGatt ?: return
        serviceScope.launch {
            val frontier = collector?.latestHRSampleTs()
            val stuck = stuckDetector.observe(strapNewest, frontier, System.currentTimeMillis() / 1000.0)
            liveState.strapNeedsReboot.value = stuck
            if (stuck) {
                log("Watchdog: strap frozen, sending recovery signals...")
                sendCommand(gatt, WhoopCommand.EXIT_HIGH_FREQ_SYNC, byteArrayOf(0x00))
                val nowSec = System.currentTimeMillis() / 1000
                val setClockPayload = byteArrayOf(
                    (nowSec and 0xFF).toByte(),
                    ((nowSec shr 8) and 0xFF).toByte(),
                    ((nowSec shr 16) and 0xFF).toByte(),
                    ((nowSec shr 24) and 0xFF).toByte(),
                    0, 0, 0, 0
                )
                sendCommand(gatt, WhoopCommand.SET_CLOCK, setClockPayload)
            }
        }
    }

    private fun requestSync(trigger: BackfillTrigger) {
        val canBackfill = liveState.connected.value && liveState.bonded.value && !backfilling
        if (!canBackfill) return
        val now = System.currentTimeMillis() / 1000.0
        
        // Android equivalent of UserDefaults is SharedPreferences.
        val prefs = getSharedPreferences("whoop_prefs", MODE_PRIVATE)
        val lastStr = prefs.getString("last_backfill_at", null)
        val last = lastStr?.toDoubleOrNull()

        if (BackfillPolicy.shouldRun(trigger, now, last)) {
            prefs.edit().putString("last_backfill_at", now.toString()).apply()
            beginBackfill()
        } else {
            log("Backfill: $trigger skipped (rate-limited).")
        }
    }

    private fun startUploadTimer() {
        uploadJob?.cancel()
        uploadJob = serviceScope.launch {
            while (true) {
                delay(30000)
                uploadOpportunistically()
                if (!backfilling) {
                    pullFromServer()
                }
            }
        }
    }

    private fun startBackfillTimer() {
        backfillJob?.cancel()
        backfillJob = serviceScope.launch {
            while (true) {
                delay(900000)
                requestSync(BackfillTrigger.PERIODIC)
            }
        }
    }

    private fun uploadOpportunistically() {
        val u = store?.let { Uploader(this, it, "my-whoop") } ?: return
        serviceScope.launch(Dispatchers.IO) {
            u.drain()
        }
    }

    private fun pullFromServer() {
        val s = store?.let { ServerSync(this, it, "my-whoop") } ?: return
        serviceScope.launch(Dispatchers.IO) {
            s.pull()
        }
    }

    fun testAlarmBuzz() {
        val gatt = bluetoothGatt ?: return
        sendCommand(gatt, WhoopCommand.RUN_HAPTICS_PATTERN, byteArrayOf(2, 3, 0, 0, 0))
        sendCommand(gatt, WhoopCommand.RUN_ALARM, byteArrayOf(0x01))
        log("Alarm: test buzz fired (patternId=2, runAlarm)")
    }

    fun disableStrapAlarm() {
        val gatt = bluetoothGatt ?: return
        sendCommand(gatt, WhoopCommand.DISABLE_ALARM, byteArrayOf(0x01))
        log("Alarm: disarmed")
    }

    fun setStrapAlarm(epochSec: Long) {
        val gatt = bluetoothGatt ?: return
        val payload = WhoopCommand.setAlarmPayload(epochSec)
        sendCommand(gatt, WhoopCommand.SET_ALARM_TIME, payload)
        log("Alarm: set time to epoch $epochSec (${Date(epochSec * 1000)})")
    }

    fun syncAlarmToStrap() {
        try {
            val prefs = getSharedPreferences("whoop_prefs", MODE_PRIVATE)
            val alarmOn = prefs.getBoolean("alarm_on", false)
            if (alarmOn) {
                val alarmHour = prefs.getInt("alarm_hour", 7)
                val alarmMinute = prefs.getInt("alarm_minute", 0)
                val alarmDaysString = prefs.getString("alarm_days", "") ?: ""
                val alarmDays = if (alarmDaysString.isEmpty()) emptySet() else alarmDaysString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                val epoch = calculateNextAlarmEpoch(alarmHour, alarmMinute, alarmDays)
                setStrapAlarm(epoch)
            } else {
                disableStrapAlarm()
            }
        } catch (e: Exception) {
            log("Error syncing alarm: ${e.message}")
        }
    }

    private fun calculateNextAlarmEpoch(hour: Int, minute: Int, daysOfWeek: Set<Int>): Long {
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance()
        alarmTime.set(Calendar.HOUR_OF_DAY, hour)
        alarmTime.set(Calendar.MINUTE, minute)
        alarmTime.set(Calendar.SECOND, 0)
        alarmTime.set(Calendar.MILLISECOND, 0)

        if (daysOfWeek.isEmpty()) {
            if (alarmTime.before(now)) {
                alarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            return alarmTime.timeInMillis / 1000
        }

        for (i in 0..7) {
            val testTime = alarmTime.clone() as Calendar
            testTime.add(Calendar.DAY_OF_YEAR, i)
            val testDayOfWeek = testTime.get(Calendar.DAY_OF_WEEK)
            val jsDayOfWeek = testDayOfWeek - 1 // 0 for Sunday... 6 for Saturday
            if (daysOfWeek.contains(jsDayOfWeek)) {
                if (testTime.after(now)) {
                    return testTime.timeInMillis / 1000
                }
            }
        }
        
        if (alarmTime.before(now)) {
            alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        }
        return alarmTime.timeInMillis / 1000
    }

    fun captureRawAccel(seconds: Double) {
        val secs = RawCaptureWindow.clamp(seconds)
        collector?.beginRawCapture(secs)
        val gatt = bluetoothGatt ?: return
        sendCommand(gatt, WhoopCommand.START_RAW_DATA, byteArrayOf(0x01))
        sendCommand(gatt, WhoopCommand.TOGGLE_IMU_MODE, byteArrayOf(0x01))
        log("Raw-accel capture: started for ${secs}s")
        serviceScope.launch {
            delay((secs * 1000).toLong())
            val prefs = getSharedPreferences("whoop_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("enableRawCapture", false)) {
                val gatt2 = bluetoothGatt ?: return@launch
                sendCommand(gatt2, WhoopCommand.STOP_RAW_DATA, byteArrayOf(0x01))
            }
        }
    }

    fun forceSyncStrap() {
        requestSync(BackfillTrigger.MANUAL)
    }

    fun manualConnect() {
        intentionalDisconnect = false
        log("Manual connect requested. Scanning for strap...")
        startScanning()
    }

    fun manualDisconnect() {
        log("Manual disconnect requested.")
        disconnectGatt()
    }

    private fun log(s: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        liveState.appendLog("[$stamp] $s")
        android.util.Log.i("WhoopBleService", s)
    }
}

// Simple Frame Router bridge
class FrameRouter(private val state: LiveState) {
    var onSyncTrigger: (() -> Unit)? = null

    fun handle(frame: ByteArray) {
        // Simple bridge to update UI immediately from raw data
        val sof = frame[0].toInt() and 0xFF
        if (sof != 0xAA) return
        val type = frame[4].toInt() and 0xFF
        state.lastFrameType.value = when (type) {
            40 -> "REALTIME_DATA"
            43 -> "REALTIME_RAW_DATA"
            47 -> "HISTORICAL_DATA"
            48 -> "EVENT"
            49 -> "METADATA"
            50 -> "CONSOLE_LOGS"
            else -> "type$type"
        }

        if (type == 40 && frame.size > 13) {
            val hr = frame[12].toInt() and 0xFF
            state.heartRate.value = hr
        } else if (type == 48 && frame.size > 6) {
            val eventCode = frame[6].toInt() and 0xFF
            state.lastEvent.value = "event $eventCode"
            if (eventCode == 23) {
                state.bonded.value = true
            }
            onSyncTrigger?.invoke()
        }
    }
}
