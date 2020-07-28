package com.outbrain.oen.apisearch.utils

import retrofit2.Call

object RetrofitUtils {

  val client = RetrofitClient()

  fun <T> createServiceNoMetrics(baseUrl: String, clazz: Class<T>, headers: Map<String, String> = mapOf(), bigDecimalAsString: Boolean = true): T =
      RetrofitClient().createService(baseUrl = baseUrl, clazz = clazz, headers = headers, bigDecimalAsString = bigDecimalAsString, service = "noService")

}

fun <T> Call<T>.executeRequestNoMetrics(): T =
    RetrofitUtils.client.executeRequest(this, "noService", "noEndpoint")
