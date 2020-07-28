package com.outbrain.oen.apisearch.clients

import com.outbrain.oen.apisearch.utils.RetrofitUtils
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

object DyplomaClient {
  fun getProdActiveClient(): IDyplomaService {
    return RetrofitUtils.createServiceNoMetrics(
        baseUrl = "http://dyploma.outbrain.com:8080/DyPloMa/api/v1/",
        bigDecimalAsString = true,
        clazz = IDyplomaService::class.java,
        headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "--secret--"
        )
    )
  }
}

interface IDyplomaService {
  @GET("serviceBuildDefinitions/serviceName/{serviceName}")
  fun getServiceBuildDefinitions(@Path("serviceName") serviceName: String): Call<List<ServiceBuildDefinition>>

  @GET("services/name/{serviceName}")
  fun getServiceByName(@Path("serviceName") serviceName: String): Call<Service>
}

data class ServiceBuildDefinition(
    val id: Long,
    val serviceId: Long,
    val vcsRepo: String,
    val buildId: String,
    val buildSystemId: Long,
    val kind: String,
    val extraValues: Map<String, String>,
    val creationTimestamp: String
) {
  fun getProjectSubdir(): String? {
    return extraValues["system.project_subdir"]?.replace("./", "")?.replace(".", "")
  }
}

data class Service(
    val owner: String
)
