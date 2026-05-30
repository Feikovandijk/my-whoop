package com.openwhoop.ble.protocol

import com.google.gson.*
import java.lang.reflect.Type

sealed class ParsedValue {
    data class IntVal(val value: Long) : ParsedValue() {
        constructor(value: Int) : this(value.toLong())
    }
    data class DoubleVal(val value: Double) : ParsedValue()
    data class StringVal(val value: String) : ParsedValue()
    data class IntArrayVal(val value: List<Int>) : ParsedValue()
    data class BoolVal(val value: Boolean) : ParsedValue()
    object NullVal : ParsedValue()

    val intValue: Int?
        get() = (this as? IntVal)?.value?.toInt()
    val doubleValue: Double?
        get() = when (this) {
            is DoubleVal -> value
            is IntVal -> value.toDouble()
            else -> null
        }
    val stringValue: String?
        get() = (this as? StringVal)?.value
    val intArrayValue: List<Int>?
        get() = (this as? IntArrayVal)?.value
    val boolValue: Boolean?
        get() = (this as? BoolVal)?.value

    override fun toString(): String {
        return when (this) {
            is IntVal -> value.toString()
            is DoubleVal -> value.toString()
            is StringVal -> value
            is IntArrayVal -> value.toString()
            is BoolVal -> value.toString()
            NullVal -> "null"
        }
    }
}

data class DecodedField(
    val off: Int,
    val len: Int,
    val name: String,
    val cat: String,
    val value: ParsedValue?,
    val raw: String,
    val note: String?
)

data class ParsedFrame(
    val ok: Boolean,
    val typeName: String,
    val seq: Int?,
    val cmdName: String?,
    val crcOK: Boolean?,
    val lenBytes: Int,
    val rawHex: String,
    val fields: List<DecodedField>,
    val parsed: Map<String, ParsedValue>
)

class ParsedValueAdapter : JsonSerializer<ParsedValue>, JsonDeserializer<ParsedValue> {
    override fun serialize(src: ParsedValue, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return when (src) {
            is ParsedValue.IntVal -> JsonPrimitive(src.value)
            is ParsedValue.DoubleVal -> JsonPrimitive(src.value)
            is ParsedValue.StringVal -> JsonPrimitive(src.value)
            is ParsedValue.IntArrayVal -> {
                val array = JsonArray()
                for (item in src.value) {
                    array.add(item)
                }
                array
            }
            is ParsedValue.BoolVal -> JsonPrimitive(src.value)
            ParsedValue.NullVal -> JsonNull.INSTANCE
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ParsedValue {
        if (json.isJsonNull) {
            return ParsedValue.NullVal
        }
        if (json.isJsonPrimitive) {
            val primitive = json.asJsonPrimitive
            if (primitive.isBoolean) {
                return ParsedValue.BoolVal(primitive.asBoolean)
            }
            if (primitive.isNumber) {
                val num = primitive.asDouble
                return if (num == num.toLong().toDouble()) {
                    ParsedValue.IntVal(num.toLong())
                } else {
                    ParsedValue.DoubleVal(num)
                }
            }
            if (primitive.isString) {
                return ParsedValue.StringVal(primitive.asString)
            }
        }
        if (json.isJsonArray) {
            val array = json.asJsonArray
            val list = mutableListOf<Int>()
            for (elem in array) {
                list.add(elem.asInt)
            }
            return ParsedValue.IntArrayVal(list)
        }
        throw JsonParseException("Unsupported ParsedValue type")
    }
}
