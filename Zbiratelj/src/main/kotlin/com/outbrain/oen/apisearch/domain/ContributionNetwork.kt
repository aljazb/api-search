package com.outbrain.oen.apisearch.domain

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class ContributionNetwork(@Autowired private val bitbucketParser: BitbucketParser,
                          @Autowired private val serviceRepository: ServiceRepository,
                          @Autowired private val databaseHandler: DatabaseHandler) {

  private var weightMatrix: Array<DoubleArray> = emptyArray()
  private var services = emptyList<String>()

  fun loadServiceConnections() {
    logger.info { "Getting service connections from bitbucket contributions" }

    val threadPoolExecutor = Executors.newFixedThreadPool(10) as ThreadPoolExecutor
    val contributorsPerService = hashMapOf<String, Map<String, Double>>()
    serviceRepository.getGitRepositories().keys.toList().forEach { service ->
      threadPoolExecutor.submit {
        bitbucketParser.getContributors(service)?.let {
          synchronized(contributorsPerService) {
            contributorsPerService[service] = it
          }
        }
      }
    }
    threadPoolExecutor.shutdown()
    threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)

    services = contributorsPerService.keys.toList()
    weightMatrix = Array(services.size) { DoubleArray(services.size) }
    val edges = mutableListOf<Edge>()
    for ((indexX, serviceX) in services.withIndex()) {
      for (indexY in 0 until indexX) {
        val weight = calculateEdgeWeight(contributorsPerService.getValue(serviceX), contributorsPerService.getValue(services[indexY]))
        weightMatrix[indexX][indexY] = weight
        if (weight > 0) {
          edges.add(Edge(serviceX, services[indexY], weight))
        }
      }
    }
    databaseHandler.insertNetwork(edges, NetworkType.GIT)
    logger.info { "Finished loading service connections" }
  }

  private fun calculateEdgeWeight(contributors1: Map<String, Double>, contributors2: Map<String, Double>): Double {
    val contributorIntersection = contributors1.keys.toSet() intersect contributors2.keys.toSet()
    return contributorIntersection.map { minOf(contributors1.getValue(it), contributors2.getValue(it)) }.sum()
  }
}