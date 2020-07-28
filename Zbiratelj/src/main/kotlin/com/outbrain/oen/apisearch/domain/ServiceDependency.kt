package com.outbrain.oen.apisearch.domain

import com.outbrain.oen.apisearch.clients.ScraperClient
import com.outbrain.oen.apisearch.utils.executeRequestNoMetrics
import mu.KotlinLogging
import org.jgrapht.Graph
import org.jgrapht.alg.scoring.PageRank
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class ServiceDependency(@Autowired private val discoveryClient: DiscoveryClient,
                        @Autowired private val databaseHandler: DatabaseHandler) {

  private var dependencyGraph: Graph<String, DefaultEdge> = SimpleDirectedGraph(DefaultEdge::class.java)

  private var pageRank: Map<String, Double> = emptyMap()

  private val serviceDependencyLoader = Executors.newScheduledThreadPool(1)
      .scheduleAtFixedRate({
        try {
          loadDependencyGraph()
        } catch (e: Exception) {
          logger.warn(e) { "Failed to fetch service topology: ${e.message}" }
        }
      }, 60, 24 * 60, TimeUnit.MINUTES)

  fun getDependencyGraph(): Graph<String, DefaultEdge> {
    if (dependencyGraph.vertexSet().isEmpty()) {
      logger.warn { "Dependency graph not yet loaded" }
    }
    return dependencyGraph
  }

  fun getPageRank(): Map<String, Double> {
    if (pageRank.isEmpty()) {
      logger.warn { "Pagerank not yet loaded" }
    }
    return pageRank
  }

  fun loadDependencyGraph() {
    logger.info { "Loading service dependencies" }
    val threadPoolExecutor = Executors.newFixedThreadPool(10) as ThreadPoolExecutor
    val newDependencyGraph: Graph<String, DefaultEdge> = SimpleDirectedGraph(DefaultEdge::class.java)
    val edges = mutableListOf<Edge>()
    val client = ScraperClient.getProdActiveClient()
    discoveryClient.services.forEach { service ->
      threadPoolExecutor.submit {
        val dependencies = client.getDependencies(service).executeRequestNoMetrics()
            .filter { it.depth == 1 && it.path != null && it.path.contains("->") }
            .map { it.path!!.split("->")[1] }
        if (dependencies.isNotEmpty()) {
          synchronized(newDependencyGraph) {
            synchronized(edges) {
              newDependencyGraph.addVertex(service)
              dependencies.forEach {
                newDependencyGraph.addVertex(it)
                newDependencyGraph.addEdge(service, it)
                edges.add(Edge(service, it))
              }
            }
          }
        }
      }
    }
    threadPoolExecutor.shutdown()
    threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)

    dependencyGraph = newDependencyGraph
    pageRank = PageRank(dependencyGraph).scores
    databaseHandler.insertPageRank(pageRank, PageRankType.CONSUL)
    databaseHandler.insertNetwork(edges, NetworkType.CONSUL)
    logger.info { "Done loading service dependencies" }
  }
}
