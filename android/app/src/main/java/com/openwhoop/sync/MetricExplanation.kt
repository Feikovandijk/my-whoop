package com.openwhoop.sync

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class MetricExplanation(
    @SerializedName("device_id") val deviceId: String,
    val date: String,
    @SerializedName("data_quality") val dataQuality: ExplanationQuality,
    val metrics: Map<String, ExplainedMetric>
)

data class ExplanationQuality(
    @SerializedName("stream_counts") val streamCounts: Map<String, Int> = emptyMap(),
    @SerializedName("sleep_sessions") val sleepSessions: Int = 0,
    @SerializedName("exercise_sessions") val exerciseSessions: Int = 0
)

data class ExplainedMetric(
    val value: Any? = null,
    val algorithm: String = "",
    val status: String = "",
    val inputs: Map<String, Any> = emptyMap(),
    val window: Map<String, Any>? = null,
    val limitation: String? = null
)

object MetricExplanationParser {
    private val gson = Gson()

    fun fromJson(json: String): MetricExplanation {
        return gson.fromJson(json, MetricExplanation::class.java)
    }
}
