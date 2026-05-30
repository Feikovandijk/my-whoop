package com.openwhoop.sync

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@JvmSuppressWildcards
interface WhoopApiService {

    @POST
    suspend fun ingestDecoded(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, Any>
    ): Response<ResponseBody>

    @POST
    suspend fun ingestRaw(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, Any>
    ): Response<ResponseBody>

    @GET
    suspend fun getStream(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("device") deviceId: String,
        @Query("from") from: Long,
        @Query("to") to: Long,
        @Query("limit") limit: Int
    ): Response<List<Map<String, Any>>>

    @GET
    suspend fun getDaily(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("device") deviceId: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<List<Map<String, Any>>>

    @GET
    suspend fun getSleep(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("device") deviceId: String,
        @Query("date") date: String
    ): Response<ResponseBody>

    @GET
    suspend fun getDailyExplanation(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("device") deviceId: String,
        @Query("date") date: String
    ): Response<MetricExplanation>

    @GET
    suspend fun getWorkouts(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("device") deviceId: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<List<Map<String, Any>>>

    @GET
    suspend fun getHRSeries(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("device") deviceId: String,
        @Query("from") from: Long,
        @Query("to") to: Long,
        @Query("max_points") maxPoints: Int
    ): Response<List<Map<String, Any>>>

    @POST
    suspend fun backfillWorkouts(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, Any>
    ): Response<ResponseBody>
}
