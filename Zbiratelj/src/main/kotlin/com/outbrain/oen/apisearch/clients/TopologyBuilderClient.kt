package com.outbrain.oen.apisearch.clients

import com.outbrain.oen.apisearch.utils.RetrofitUtils
import retrofit2.Call
import retrofit2.http.GET

object TopologyBuilderClient {
  fun getProdActiveClient(): ITopologyBuilderService {
    return RetrofitUtils.createServiceNoMetrics("http://prod.topologybuilder.service.nydc1.consul:8090/TopologyBuilder/api/", ITopologyBuilderService::class.java)
  }
}

interface ITopologyBuilderService {
  @GET("getTopology")
  fun getTopology(): Call<TopologyNode>
}

data class TopologyNode(
    val maxVolume: Double?,
    val serverUpdateTime: Long?,
    val updated: Long?,
    val layout: String?,
    val renderer: String?,
    val name: String?,
    val displayName: String?,
    val nodes: List<TopologyNode>?,
    val class1: String?,
    val notices: List<TopologyNotice>?,
    val connections: List<TopologyConnection>?,
    val metadata: TopologyMetadata?,
    val metrics: TopologyMetrics?,
    val description: String?
)

data class TopologyNotice(
    val title: String?,
    val link: String?,
    val severity: Int?
)

data class TopologyConnection(
    val source: String?,
    val target: String?,
    val metrics: TopologyMetrics?,
    val notices: List<TopologyNotice>?,
    val metadata: TopologyMetadata?
)

class TopologyMetadata(
    val streaming: Int
)

class TopologyMetrics(
    val normal: Double?,
    val warning: Double?,
    val danger: Double?
)
