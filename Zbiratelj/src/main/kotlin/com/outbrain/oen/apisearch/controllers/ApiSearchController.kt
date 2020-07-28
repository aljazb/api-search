package com.outbrain.oen.apisearch.controllers

import com.outbrain.oen.apisearch.domain.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiSearchController(@Autowired private val indexer: Indexer,
                          @Autowired private val serviceDependency: ServiceDependency,
                          @Autowired private val serviceRepository: ServiceRepository,
                          @Autowired private val serviceRequestMetrics: ServiceRequestMetrics,
                          @Autowired private val contributionGraph: ContributionNetwork,
                          @Autowired private val databaseScrapper: DatabaseScrapper) {

  @PostMapping("/runIndexing")
  fun runIndexing(): String {
    indexer.run()
    return "ok"
  }

  @PostMapping("/loadConsulDependencyGraph")
  fun loadDependencyGraph(): String {
    serviceDependency.loadDependencyGraph()
    return "ok"
  }

  @PostMapping("/loadGitRepositories")
  fun loadGitRepositories(): String {
    serviceRepository.loadGitRepositories()
    return "ok"
  }

  @PostMapping("/loadConnectionMetricsGraph")
  fun loadConnectionMetricsGraph(): String {
    serviceRequestMetrics.loadConnectionMetricsGraph()
    return "ok"
  }

  @PostMapping("/loadGitServiceConnections")
  fun loadServiceConnections(): String {
    contributionGraph.loadServiceConnections()
    return "ok"
  }

  @PostMapping("/loadDatabaseInfoAndConnections")
  fun loadDatabaseInfoAndConnections(): String {
    databaseScrapper.loadDatabaseInfoAndConnections()
    return "ok"
  }
}

