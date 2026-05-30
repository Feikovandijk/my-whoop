package com.openwhoop.ble.protocol

enum class WhoopCommand(val value: Byte, val label: String) {
    TOGGLE_REALTIME_HR(3, "Toggle Realtime HR"),
    REPORT_VERSION_INFO(7, "Report Version Info"),
    SET_CLOCK(10, "Set Clock"),
    GET_CLOCK(11, "Get Clock"),
    SEND_HISTORICAL_DATA(22, "Send Historical Data"),
    HISTORICAL_DATA_RESULT(23, "Historical Data Result"),
    GET_BATTERY_LEVEL(26, "Get Battery Level"),
    GET_DATA_RANGE(34, "Get Data Range"),
    GET_HELLO_HARVARD(35, "Get Hello (Harvard)"),
    GET_ADVERTISING_NAME_HARVARD(76, "Get Advertising Name (Harvard)"),
    START_RAW_DATA(81, "Start Raw Data"),
    STOP_RAW_DATA(82, "Stop Raw Data"),
    ENTER_HIGH_FREQ_SYNC(96, "Enter High-Freq Sync"),
    EXIT_HIGH_FREQ_SYNC(97, "Exit High-Freq Sync"),
    GET_EXTENDED_BATTERY_INFO(98, "Get Extended Battery Info"),
    TOGGLE_IMU_MODE(106, "Toggle IMU Mode"),
    ENABLE_OPTICAL_DATA(107, "Enable Optical Data"),
    RUN_HAPTICS_PATTERN(79, "Run Haptics Pattern"),
    STOP_HAPTICS(122, "Stop Haptics"),
    SEND_R10_R11_REALTIME(63, "R10/R11 Realtime (raw stream)"),
    SET_ALARM_TIME(66, "Set Alarm Time"),
    GET_ALARM_TIME(67, "Get Alarm Time"),
    RUN_ALARM(68, "Run Alarm"),
    DISABLE_ALARM(69, "Disable Alarm");

    fun frame(seq: Byte, payload: ByteArray = byteArrayOf(0x00)): ByteArray {
        val inner = ByteArray(3 + payload.size)
        inner[0] = COMMAND_TYPE
        inner[1] = seq
        inner[2] = value
        System.arraycopy(payload, 0, inner, 3, payload.size)

        val length = inner.size + 4
        val lenBytes = byteArrayOf((length and 0xFF).toByte(), ((length shr 8) and 0xFF).toByte())
        val headerCRC = Framing.crc8(lenBytes)
        val trailer = Framing.crc32(inner)
        val trailerBytes = byteArrayOf(
            (trailer and 0xFF).toByte(),
            ((trailer shr 8) and 0xFF).toByte(),
            ((trailer shr 16) and 0xFF).toByte(),
            ((trailer shr 24) and 0xFF).toByte()
        )

        val frame = ByteArray(1 + 2 + 1 + inner.size + 4)
        frame[0] = 0xAA.toByte()
        frame[1] = lenBytes[0]
        frame[2] = lenBytes[1]
        frame[3] = headerCRC.toByte()
        System.arraycopy(inner, 0, frame, 4, inner.size)
        System.arraycopy(trailerBytes, 0, frame, 4 + inner.size, 4)

        return frame
    }

    companion object {
        const val COMMAND_TYPE: Byte = 35

        fun setAlarmPayload(epochSec: Long): ByteArray {
            return byteArrayOf(
                0x01,
                (epochSec and 0xFFL).toByte(),
                ((epochSec shr 8) and 0xFFL).toByte(),
                ((epochSec shr 16) and 0xFFL).toByte(),
                ((epochSec shr 24) and 0xFFL).toByte(),
                0x00,
                0x00
            )
        }
    }
}
