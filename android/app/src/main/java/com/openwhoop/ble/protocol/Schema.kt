package com.openwhoop.ble.protocol

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class FieldSpec(
    val off: Int,
    val len: Int,
    val dtype: String?,
    val name: String,
    val cat: String,
    val enum: String?,
    val note: String?
)

data class AxisSpec(
    val name: String,
    val off: Int,
    val cat: String
)

data class VariantSpec(
    val kind: String,
    val note: String,
    val hrOff: Int?,
    val rrCountOff: Int?,
    val rrFirstOff: Int?,
    val samples: Int?,
    val axes: List<AxisSpec>,
    val tailFrom: Int?,
    val ppgOff: Int?,
    val ppgStride: Int?,
    val ppgSamples: Int?,
    val configFrom: Int?,
    val configTo: Int?
)

data class VersionSpec(
    val kind: String?,
    val fields: List<FieldSpec>,
    val rrFirstOff: Int?,
    val ref: String?
)

data class PacketSpec(
    val name: String,
    val type: Int,
    val aliases: List<Int>,
    val post: String?,
    val fields: List<FieldSpec>,
    val variants: Map<String, VariantSpec>,
    val versions: Map<String, VersionSpec>
)

class Schema(
    val enums: Map<String, Map<String, String>>,
    val envelope: List<FieldSpec>,
    val packets: Map<String, PacketSpec>
) {
    private val byType: Map<Int, PacketSpec>

    init {
        val idx = mutableMapOf<Int, PacketSpec>()
        for ((_, spec) in packets) {
            idx[spec.type] = spec
            for (alias in spec.aliases) {
                idx[alias] = spec
            }
        }
        this.byType = idx
    }

    fun typeName(type: Int): String {
        return enums["PacketType"]?.get(type.toString()) ?: "type$type"
    }

    fun enumName(enumName: String, value: Int): String {
        val name = enums[enumName]?.get(value.toString())
        return if (name != null) {
            "$name($value)"
        } else {
            String.format("0x%02X(%d)", value, value)
        }
    }

    fun packet(forType: Int): PacketSpec? {
        return byType[forType]
    }

    fun resolveVersion(versions: Map<String, VersionSpec>, version: Int): VersionSpec? {
        var entry = versions[version.toString()] ?: return null
        val seen = mutableSetOf<String>()
        while (entry.ref != null && !seen.contains(entry.ref)) {
            val ref = entry.ref!!
            seen.add(ref)
            val base = versions[ref] ?: break
            entry = VersionSpec(
                kind = entry.kind ?: base.kind,
                fields = if (entry.fields.isEmpty()) base.fields else entry.fields,
                rrFirstOff = entry.rrFirstOff ?: base.rrFirstOff,
                ref = null
            )
        }
        return entry
    }

    companion object {
        private var cachedSchema: Schema? = null

        fun get(context: Context?): Schema {
            synchronized(this) {
                if (cachedSchema != null) return cachedSchema!!
                if (context == null) {
                    throw IllegalStateException("Schema not initialized and context is null")
                }

                val assetManager = context.assets
                val inputStream = assetManager.open("whoop_protocol.json")
                return init(inputStream)
            }
        }

        fun init(inputStream: java.io.InputStream): Schema {
            synchronized(this) {
                if (cachedSchema != null) return cachedSchema!!

                val reader = InputStreamReader(inputStream)
                val gson = Gson()
                val rawSchema = gson.fromJson<RawSchema>(reader, object : TypeToken<RawSchema>() {}.type)
                
                val parsedPackets = mutableMapOf<String, PacketSpec>()
                for ((name, rp) in rawSchema.packets) {
                    val variants = mutableMapOf<String, VariantSpec>()
                    rp.variants?.forEach { (key, rv) ->
                        val axes = rv.axes?.map { axis ->
                            AxisSpec(
                                name = axis[0] as String,
                                off = (axis[1] as Double).toInt(),
                                cat = axis[2] as String
                            )
                        } ?: emptyList()

                        variants[key] = VariantSpec(
                            kind = rv.kind,
                            note = rv.note,
                            hrOff = rv.hr_off,
                            rrCountOff = rv.rr_count_off,
                            rrFirstOff = rv.rr_first_off,
                            samples = rv.samples,
                            axes = axes,
                            tailFrom = rv.tail_from,
                            ppgOff = rv.ppg_off,
                            ppgStride = rv.ppg_stride,
                            ppgSamples = rv.ppg_samples,
                            configFrom = rv.config_from,
                            configTo = rv.config_to
                        )
                    }

                    val versions = mutableMapOf<String, VersionSpec>()
                    rp.versions?.forEach { (key, rv) ->
                        versions[key] = VersionSpec(
                            kind = rv.kind,
                            fields = rv.fields ?: emptyList(),
                            rrFirstOff = rv.rr_first_off,
                            ref = rv.ref
                        )
                    }

                    parsedPackets[name] = PacketSpec(
                        name = name,
                        type = rp.type,
                        aliases = rp.aliases ?: emptyList(),
                        post = rp.post,
                        fields = rp.fields ?: emptyList(),
                        variants = variants,
                        versions = versions
                    )
                }

                val schema = Schema(
                    enums = rawSchema.enums,
                    envelope = rawSchema.envelope,
                    packets = parsedPackets
                )
                
                cachedSchema = schema
                return schema
            }
        }
    }
}

private class RawVariant(
    val kind: String,
    val note: String,
    val hr_off: Int?,
    val rr_count_off: Int?,
    val rr_first_off: Int?,
    val samples: Int?,
    val axes: List<List<Any>>?,
    val tail_from: Int?,
    val ppg_off: Int?,
    val ppg_stride: Int?,
    val ppg_samples: Int?,
    val config_from: Int?,
    val config_to: Int?
)

private class RawVersion(
    val kind: String?,
    val fields: List<FieldSpec>?,
    val rr_first_off: Int?,
    val ref: String?
)

private class RawPacket(
    val type: Int,
    val aliases: List<Int>?,
    val post: String?,
    val fields: List<FieldSpec>?,
    val variants: Map<String, RawVariant>?,
    val versions: Map<String, RawVersion>?
)

private class RawSchema(
    val enums: Map<String, Map<String, String>>,
    val envelope: List<FieldSpec>,
    val packets: Map<String, RawPacket>
)
