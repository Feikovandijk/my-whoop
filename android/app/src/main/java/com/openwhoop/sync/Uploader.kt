package com.openwhoop.sync

import android.content.Context
import com.openwhoop.ble.protocol.*
import com.openwhoop.database.WhoopStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkConfig(
    val baseURL: String,
    val apiKey: String
) {
    companion object {
        fun load(context: Context): NetworkConfig {
            val prefs = context.getSharedPreferences("whoop_prefs", Context.MODE_PRIVATE)
            val rawUrl = prefs.getString("server_url", com.openwhoop.BuildConfig.DEFAULT_SERVER_URL) ?: com.openwhoop.BuildConfig.DEFAULT_SERVER_URL
            val url = if (rawUrl.endsWith("/")) rawUrl.substring(0, rawUrl.length - 1) else rawUrl
            val key = prefs.getString("api_key", com.openwhoop.BuildConfig.DEFAULT_API_KEY) ?: com.openwhoop.BuildConfig.DEFAULT_API_KEY
            return NetworkConfig(url, key)
        }
    }
}

object NetworkClient {
    private var retrofit: Retrofit? = null
    private var service: WhoopApiService? = null

    @Synchronized
    fun getService(): WhoopApiService {
        if (service != null) return service!!
        
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val r = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        retrofit = r
        service = r.create(WhoopApiService::class.java)
        return service!!
    }
}

class Uploader(
    private val context: Context,
    private val store: WhoopStore,
    private val deviceId: String
) {
    private val service = NetworkClient.getService()

    suspend fun drain() = withContext(Dispatchers.IO) {
        android.util.Log.d("Uploader", "drain: starting upload drain...")
        drainDecoded()
        android.util.Log.d("Uploader", "drain: upload drain complete.")
    }

    private suspend fun drainDecoded() {
        drainHR()
        drainRR()
        drainEvents()
        drainBattery()
        drainSpo2()
        drainSkinTemp()
        drainResp()
        drainGravity()
    }

    private suspend fun <Row> drainStream(
        read: suspend (limit: Int) -> List<Row>,
        bodyKey: String,
        encode: (Row) -> Map<String, Any>,
        mark: suspend (List<Row>) -> Unit
    ) {
        val config = NetworkConfig.load(context)
        val limit = 5000
        while (true) {
            val rows = try {
                read(limit)
            } catch (e: Exception) {
                android.util.Log.e("Uploader", "drainStream: error reading $bodyKey rows from database", e)
                emptyList()
            }
            if (rows.isEmpty()) {
                android.util.Log.d("Uploader", "drainStream: no unsynced rows found for $bodyKey")
                return
            }
            android.util.Log.d("Uploader", "drainStream: found ${rows.size} rows for $bodyKey")

            val payload = mapOf(
                "device" to mapOf("id" to deviceId),
                "streams" to mapOf(bodyKey to rows.map(encode))
            )

            val success = postDecoded(config, payload)
            if (success) {
                try {
                    mark(rows)
                    android.util.Log.d("Uploader", "drainStream: successfully marked ${rows.size} rows of $bodyKey as synced")
                } catch (e: Exception) {
                    android.util.Log.e("Uploader", "drainStream: error marking $bodyKey rows as synced in database", e)
                    return
                }
            } else {
                android.util.Log.w("Uploader", "drainStream: postDecoded failed for $bodyKey")
                return
            }

            if (rows.size < limit) return
        }
    }

    private suspend fun postDecoded(config: NetworkConfig, body: Map<String, Any>): Boolean {
        val url = "${config.baseURL}/v1/ingest-decoded"
        android.util.Log.d("Uploader", "postDecoded: posting to $url...")
        return try {
            val auth = "Bearer ${config.apiKey}"
            val response = service.ingestDecoded(url, auth, body)
            android.util.Log.d("Uploader", "postDecoded response code: ${response.code()} successful=${response.isSuccessful}")
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("Uploader", "postDecoded failed to POST to $url", e)
            false
        }
    }

    private suspend fun drainHR() {
        drainStream(
            read = { store.unsyncedHR(deviceId, it) },
            bodyKey = "hr",
            encode = { mapOf("ts" to it.ts, "bpm" to it.bpm) },
            mark = { store.markHRSynced(deviceId, it) }
        )
    }

    private suspend fun drainRR() {
        drainStream(
            read = { store.unsyncedRR(deviceId, it) },
            bodyKey = "rr",
            encode = { mapOf("ts" to it.ts, "rr_ms" to it.rrMs) },
            mark = { store.markRRSynced(deviceId, it) }
        )
    }

    private suspend fun drainEvents() {
        drainStream(
            read = { store.unsyncedEvents(deviceId, it) },
            bodyKey = "events",
            encode = { mapOf("ts" to it.ts, "kind" to it.kind, "payload" to it.payload) },
            mark = { store.markEventsSynced(deviceId, it) }
        )
    }

    private suspend fun drainBattery() {
        drainStream(
            read = { store.unsyncedBattery(deviceId, it) },
            bodyKey = "battery",
            encode = {
                val map = mutableMapOf<String, Any>("ts" to it.ts)
                it.soc?.let { s -> map["soc"] = s }
                it.mv?.let { m -> map["mv"] = m }
                it.charging?.let { c -> map["charging"] = c }
                map
            },
            mark = { store.markBatterySynced(deviceId, it) }
        )
    }

    private suspend fun drainSpo2() {
        drainStream(
            read = { store.unsyncedSpo2(deviceId, it) },
            bodyKey = "spo2",
            encode = { mapOf("ts" to it.ts, "red" to it.red, "ir" to it.ir) },
            mark = { store.markSpo2Synced(deviceId, it) }
        )
    }

    private suspend fun drainSkinTemp() {
        drainStream(
            read = { store.unsyncedSkinTemp(deviceId, it) },
            bodyKey = "skin_temp",
            encode = { mapOf("ts" to it.ts, "raw" to it.raw) },
            mark = { store.markSkinTempSynced(deviceId, it) }
        )
    }

    private suspend fun drainResp() {
        drainStream(
            read = { store.unsyncedResp(deviceId, it) },
            bodyKey = "resp",
            encode = { mapOf("ts" to it.ts, "raw" to it.raw) },
            mark = { store.markRespSynced(deviceId, it) }
        )
    }

    private suspend fun drainGravity() {
        drainStream(
            read = { store.unsyncedGravity(deviceId, it) },
            bodyKey = "gravity",
            encode = { mapOf("ts" to it.ts, "x" to it.x, "y" to it.y, "z" to it.z) },
            mark = { store.markGravitySynced(deviceId, it) }
        )
    }
}
