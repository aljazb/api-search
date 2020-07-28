package com.outbrain.oen.apisearch.clients

import com.outbrain.oen.apisearch.utils.RetrofitUtils
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

object ScraperClient {
  fun getProdActiveClient(): IScraperService {
    return RetrofitUtils.createServiceNoMetrics("http://prod.scraper.service.nydc1.consul:8080/Scraper/api/", IScraperService::class.java)
  }
}

interface IScraperService {
  @GET("getDependencies")
  fun getDependencies(@Query("service") service: String): Call<List<ServiceDef>>
}

data class ServiceDef(
  val name: String?,
  val owner: String?,
  val path: String?,
  val depth: Int,
  val checked: Boolean
)