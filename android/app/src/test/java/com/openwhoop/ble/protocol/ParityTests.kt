package com.openwhoop.ble.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.InputStreamReader

class ParityTests {

    companion object {
        // MUST equal the constants in StreamsParityTests.swift
        private const val DEVICE_CLOCK_REF = 31538447L
        private const val WALL_CLOCK_REF = 1736365593L

        private lateinit var gson: Gson

        @BeforeClass
        @JvmStatic
        fun setUp() {
            gson = GsonBuilder()
                .registerTypeAdapter(ParsedValue::class.java, ParsedValueAdapter())
                .create()

            // Initialize the Schema from the whoop_protocol.json test resource
            val resourceStream = ParityTests::class.java.classLoader.getResourceAsStream("whoop_protocol.json")
                ?: throw IllegalStateException("whoop_protocol.json not found in test resources")
            Schema.init(resourceStream)
        }

        private fun hexToBytes(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }

    private fun loadResource(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: throw IllegalArgumentException("Resource $name not found")
        return InputStreamReader(stream).use { it.readText() }
    }

    data class FrameEntry(val hex: String)
    
    data class GoldenEntry(
        val type_name: String,
        val seq: Int?,
        val crc_ok: Boolean?,
        val cmd_name: String?,
        val parsed: Map<String, ParsedValue>
    )

    data class HRGold(val ts: Long, val bpm: Int)
    data class RRGold(val ts: Long, val rr_ms: Int?)
    data class BatGold(val ts: Long, val soc: Double?, val mv: Int?, val charging: Boolean?)
    data class EvGold(val ts: Long, val kind: String, val payload: Map<String, ParsedValue>)
    
    data class StreamsGold(
        val hr: List<HRGold>,
        val rr: List<RRGold>,
        val events: List<EvGold>,
        val battery: List<BatGold>
    )

    data class SpO2Gold(val ts: Long, val red: Int, val ir: Int, val unit: String)
    data class TempGold(val ts: Long, val raw: Int, val unit: String)
    data class RespGold(val ts: Long, val raw: Int, val unit: String)
    data class GravGold(val ts: Long, val x: Double, val y: Double, val z: Double, val unit: String)
    data class BioGold(
        val hr: List<HRGold>,
        val rr: List<RRGold>,
        val spo2: List<SpO2Gold>,
        val skin_temp: List<TempGold>,
        val resp: List<RespGold>,
        val gravity: List<GravGold>
    )

    data class HistGold(
        val hr: List<HRGold>,
        val rr: List<RRGold>
    )

    @Test
    fun testKotlinMatchesPythonGolden() {
        val framesJson = loadResource("frames.json")
        val goldenJson = loadResource("golden.json")

        val frames: List<FrameEntry> = gson.fromJson(framesJson, object : TypeToken<List<FrameEntry>>() {}.type)
        val golden: List<GoldenEntry> = gson.fromJson(goldenJson, object : TypeToken<List<GoldenEntry>>() {}.type)

        assertEquals("frames.json and golden.json length mismatch", frames.size, golden.size)
        assertTrue("no parity frames loaded", frames.isNotEmpty())

        for (i in frames.indices) {
            val f = frames[i]
            val g = golden[i]
            val out = Interpreter.parseFrame(null, hexToBytes(f.hex))
            
            assertEquals("type_name mismatch at #$i", g.type_name, out.typeName)
            assertEquals("seq mismatch at #$i (${g.type_name})", g.seq, out.seq)
            assertEquals("crc_ok mismatch at #$i (${g.type_name})", g.crc_ok, out.crcOK)
            assertEquals("cmd_name mismatch at #$i (${g.type_name})", g.cmd_name, out.cmdName)
            
            assertEquals(
                "parsed mismatch at #$i (${g.type_name})\n  kotlin: ${out.parsed}\n  python: ${g.parsed}",
                g.parsed,
                out.parsed
            )
        }
    }

    @Test
    fun testEveryCorePacketTypeCovered() {
        val goldenJson = loadResource("golden.json")
        val golden: List<GoldenEntry> = gson.fromJson(goldenJson, object : TypeToken<List<GoldenEntry>>() {}.type)
        val types = golden.map { it.type_name }.toSet()
        for (expected in listOf("REALTIME_DATA", "COMMAND_RESPONSE", "EVENT", "METADATA",
            "CONSOLE_LOGS", "REALTIME_RAW_DATA")) {
            assertTrue("parity fixture missing $expected", types.contains(expected))
        }
    }

    @Test
    fun testKotlinStreamsMatchPythonGolden() {
        val framesJson = loadResource("frames.json")
        val streamsGoldenJson = loadResource("streams_golden.json")

        val frames: List<FrameEntry> = gson.fromJson(framesJson, object : TypeToken<List<FrameEntry>>() {}.type)
        val gold: StreamsGold = gson.fromJson(streamsGoldenJson, StreamsGold::class.java)

        val parsed = frames.map { Interpreter.parseFrame(null, hexToBytes(it.hex)) }
        val s = Streams.extractStreams(parsed, DEVICE_CLOCK_REF, WALL_CLOCK_REF)

        assertEquals(gold.hr.map { HRSample(it.ts, it.bpm) }, s.hr)
        assertEquals(gold.rr.map { RRInterval(it.ts, it.rr_ms ?: 0) }, s.rr)
        assertEquals(
            gold.battery.map { BatterySample(it.ts, it.soc, it.mv, it.charging) },
            s.battery
        )
        assertEquals("event count mismatch", gold.events.size, s.events.size)
        for (i in gold.events.indices) {
            val g = gold.events[i]
            assertEquals("event ts mismatch #$i", g.ts, s.events[i].ts)
            assertEquals("event kind mismatch #$i", g.kind, s.events[i].kind)
            assertEquals("event payload mismatch #$i (${g.kind})", g.payload, s.events[i].payload)
        }

        assertTrue(s.hr.size > 0)
        assertTrue(s.events.size > 0)
        assertTrue(s.battery.size > 0)
    }

    @Test
    fun testKotlinBiometricStreamsMatchPythonGolden() {
        val framesJson = loadResource("frames.json")
        val bioGoldenJson = loadResource("biometric_streams_golden.json")

        val frames: List<FrameEntry> = gson.fromJson(framesJson, object : TypeToken<List<FrameEntry>>() {}.type)
        val gold: BioGold = gson.fromJson(bioGoldenJson, BioGold::class.java)

        val v24 = frames.map { Interpreter.parseFrame(null, hexToBytes(it.hex)) }
            .filter { it.ok && it.typeName == "HISTORICAL_DATA" }
        assertEquals("expected the 60 V24 records from gen_golden.py", 60, v24.size)

        val s = HistoricalStreams.extract(v24, DEVICE_CLOCK_REF, WALL_CLOCK_REF)

        assertEquals(gold.hr.map { HRSample(it.ts, it.bpm) }, s.hr)
        assertEquals(gold.rr.map { RRInterval(it.ts, it.rr_ms ?: 0) }, s.rr)
        assertEquals(gold.spo2.map { SpO2Sample(it.ts, it.red, it.ir, it.unit) }, s.spo2)
        assertEquals(gold.skin_temp.map { SkinTempSample(it.ts, it.raw, it.unit) }, s.skinTemp)
        assertEquals(gold.resp.map { RespSample(it.ts, it.raw, it.unit) }, s.resp)
        assertEquals(gold.gravity.map { GravitySample(it.ts, it.x, it.y, it.z, it.unit) }, s.gravity)

        assertTrue(s.hr.size > 0)
        assertTrue(s.spo2.size > 0)
        assertTrue(s.gravity.size > 0)
    }

    @Test
    fun testKotlinHistoricalMatchesPythonGolden() {
        val framesJson = loadResource("historical_frames.json")
        val histGoldenJson = loadResource("historical_golden.json")

        val frames: List<FrameEntry> = gson.fromJson(framesJson, object : TypeToken<List<FrameEntry>>() {}.type)
        val gold: HistGold = gson.fromJson(histGoldenJson, HistGold::class.java)

        val parsed = frames.map { Interpreter.parseFrame(null, hexToBytes(it.hex)) }
        val s = HistoricalStreams.extract(parsed, DEVICE_CLOCK_REF, WALL_CLOCK_REF)

        assertEquals(gold.hr.map { HRSample(it.ts, it.bpm) }, s.hr)
        assertEquals(gold.rr.map { RRInterval(it.ts, it.rr_ms ?: 0) }, s.rr)
        assertTrue(s.hr.size > 0)
    }
}
