package com.openwhoop.ble.protocol

sealed class HistoricalMeta {
    object Start : HistoricalMeta()
    data class End(val unix: Long, val trim: Long) : HistoricalMeta()
    object Complete : HistoricalMeta()
    object Other : HistoricalMeta()
}

object HistoricalMetaClassifier {
    fun classify(p: ParsedFrame): HistoricalMeta {
        if (p.typeName != "METADATA") return HistoricalMeta.Other
        val metaName = p.parsed["meta_type"]?.stringValue ?: return HistoricalMeta.Other
        return when {
            metaName.startsWith("HISTORY_START") -> HistoricalMeta.Start
            metaName.startsWith("HISTORY_COMPLETE") -> HistoricalMeta.Complete
            metaName.startsWith("HISTORY_END") -> {
                val unix = p.parsed["unix"]?.intValue?.toLong() ?: return HistoricalMeta.Other
                val trim = p.parsed["trim_cursor"]?.intValue?.toLong() ?: return HistoricalMeta.Other
                HistoricalMeta.End(unix, trim)
            }
            else -> HistoricalMeta.Other
        }
    }
}
