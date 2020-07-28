package com.outbrain.oen.apisearch.domain

import com.outbrain.oen.apisearch.clients.TopologyBuilderClient
import com.outbrain.oen.apisearch.clients.TopologyMetrics
import com.outbrain.oen.apisearch.utils.executeRequestNoMetrics
import mu.KotlinLogging
import org.jgrapht.Graph
import org.jgrapht.alg.scoring.PageRank
import org.jgrapht.graph.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class ServiceRequestMetrics(@Autowired private val databaseHandler: DatabaseHandler) {

  val productionDCs = listOf("ny", "chi2", "sa")

  private var endpointRequestMetrics: HashMap<String, TopologyMetrics> = hashMapOf()

  private var weightedConnectionMetricsGraph: Graph<String, DefaultWeightedEdge> = SimpleWeightedGraph(DefaultWeightedEdge::class.java)

  private var pageRank: Map<String, Double> = emptyMap()

  private var weightedPageRank: Map<String, Double> = emptyMap()

  private val requestMetricsLoader = Executors.newScheduledThreadPool(1)
      .scheduleAtFixedRate({
        try {
          loadConnectionMetricsGraph()
        } catch (e: Exception) {
          logger.warn(e) { "Failed to fetch service topology: ${e.message}" }
        }
      }, 0, 24 * 60, TimeUnit.MINUTES)

  fun getEndpointRequestMetrics(): HashMap<String, TopologyMetrics> {
    if (endpointRequestMetrics.isEmpty()) {
      logger.warn { "Endpoint request metrics not yet loaded" }
    }
    return endpointRequestMetrics
  }

  fun getWeightedConnectionMetricsGraph(): Graph<String, DefaultWeightedEdge> {
    if (weightedConnectionMetricsGraph.vertexSet().isEmpty()) {
      logger.warn { "weighted connection metrics graph not yet loaded" }
    }
    return weightedConnectionMetricsGraph
  }

  fun getPageRank(): Map<String, Double> {
    if (pageRank.isEmpty()) {
      logger.warn { "Pagerank not yet loaded" }
    }
    return pageRank
  }

  fun getWeightedPageRank(): Map<String, Double> {
    if (weightedPageRank.isEmpty()) {
      logger.warn { "Weighted pagerank not yet loaded" }
    }
    return weightedPageRank
  }

  fun loadConnectionMetricsGraph() {
    logger.info { "Loading request metrics" }
    val topology = TopologyBuilderClient.getProdActiveClient().getTopology().executeRequestNoMetrics()

    val newEndpointRequestMetrics: HashMap<String, TopologyMetrics> = hashMapOf()
    topology.nodes
        ?.filter { it.name in productionDCs }
        ?.flatMap { it.nodes ?: emptyList() }
        ?.flatMap { service ->
          service.nodes
              ?.filter { service.name != null && it.name != null && it.metrics != null }
              ?.map {
                Pair("${service.name},${it.name}", it.metrics)
              } ?: emptyList()
        }
        ?.forEach { newEndpointRequestMetrics.merge(it.first, it.second!!, TopologyMetrics::plus) }
    endpointRequestMetrics = newEndpointRequestMetrics

    val newWeightedConnectionMetricsGraph: Graph<String, DefaultWeightedEdge> = SimpleWeightedGraph(DefaultWeightedEdge::class.java)
    val connectionMetricsGraph: Graph<String, DefaultEdge> = SimpleGraph(DefaultEdge::class.java)
    val serviceConnectionMetrics: HashMap<Pair<String, String>, TopologyMetrics> = hashMapOf()
    topology.nodes
        ?.filter { it.name in productionDCs }
        ?.map { it.connections ?: emptyList() }
        ?.flatten()
        ?.filter { it.source != null && it.target != null && it.source != it.target && it.metrics != null }
        ?.forEach { serviceConnectionMetrics.merge(Pair(it.source!!, it.target!!), it.metrics!!, TopologyMetrics::plus) }

    val edges = mutableListOf<Edge>()
    serviceConnectionMetrics.forEach {
      connectionMetricsGraph.addVertex(it.key.first)
      connectionMetricsGraph.addVertex(it.key.second)
      newWeightedConnectionMetricsGraph.addVertex(it.key.first)
      newWeightedConnectionMetricsGraph.addVertex(it.key.second)
      connectionMetricsGraph.addEdge(it.key.first, it.key.second)
      val weightedEdge = newWeightedConnectionMetricsGraph.addEdge(it.key.first, it.key.second)
      if (weightedEdge != null) {
        newWeightedConnectionMetricsGraph.setEdgeWeight(weightedEdge, it.value.normal ?: 0.0)
        edges.add(Edge(it.key.first, it.key.second, it.value.normal))
      }
    }

    weightedConnectionMetricsGraph = newWeightedConnectionMetricsGraph
    weightedPageRank = PageRank(weightedConnectionMetricsGraph).scores
    pageRank = PageRank(connectionMetricsGraph).scores
    databaseHandler.insertPageRank(pageRank, PageRankType.METRICS)
    databaseHandler.insertPageRank(weightedPageRank, PageRankType.WEIGHTED_METRICS)
    databaseHandler.insertEndpointMetrics(endpointRequestMetrics
        .mapKeys { Pair(it.key.split(",")[1].split("_").last(), it.key.split(",")[0]) }
        .mapValues { it.value.normal })
    databaseHandler.insertNetwork(edges, NetworkType.METRICS)
    logger.info { "Done loading request metrics" }
  }
}

operator fun TopologyMetrics.plus(topologyMetrics: TopologyMetrics) = TopologyMetrics(
    sumNullableDoubles(this.normal, topologyMetrics.normal),
    sumNullableDoubles(this.warning, topologyMetrics.warning),
    sumNullableDoubles(this.danger, topologyMetrics.danger))

fun sumNullableDoubles(d1: Double?, d2: Double?): Double {
  return (d1 ?: 0.0) + (d2 ?: 0.0)
}