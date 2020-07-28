package com.outbrain.oen.apisearch.domain

import com.outbrain.oen.apisearch.clients.DyplomaClient
import com.outbrain.oen.apisearch.utils.executeRequestNoMetrics
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class ServiceRepository(@Autowired private val discoveryClient: DiscoveryClient) {

  private var serviceRepositories: HashMap<String, ServiceRepositoryInfo> = hashMapOf()

  private val gitRepositoryLoader = Executors.newScheduledThreadPool(1)
      .scheduleAtFixedRate({
        try {
          loadGitRepositories()
        } catch (e: Exception) {
          logger.warn(e) { "Failed to fetch git repositories: ${e.message}" }
        }
      }, 10, 24 * 60, TimeUnit.MINUTES)

  fun getGitRepositories(): Map<String, ServiceRepositoryInfo> {
    if (serviceRepositories.isEmpty()) {
      logger.warn { "Service repositories not yet loaded" }
    }
    return serviceRepositories
  }

  fun loadGitRepositories() {
    logger.info { "Loading git repositories" }
    val threadPoolExecutor = Executors.newFixedThreadPool(10)
    val newServiceRepositories: HashMap<String, ServiceRepositoryInfo> = hashMapOf()
    val client = DyplomaClient.getProdActiveClient()
    discoveryClient.services.forEach { service ->
      threadPoolExecutor.submit {
        logger.info { service }
        try {
          val serviceRepository = client
              .getServiceBuildDefinitions(service)
              .executeRequestNoMetrics()
              .firstOrNull()
              ?.let { ServiceRepositoryInfo(it.vcsRepo, it.getProjectSubdir() ?: "") }
          if (serviceRepository != null) {
            synchronized(newServiceRepositories) {
              newServiceRepositories.put(service, serviceRepository)
            }
          }
        } catch (e: Exception) {
          logger.info { "Dyploma client could not find service $service" }
        }
        logger.info { "done $service" }
      }
    }
    threadPoolExecutor.shutdown()
    threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)

    serviceRepositories = newServiceRepositories
    logger.info { "Done loading git repositories" }
  }
}

data class ServiceRepositoryInfo(
    val fullUrl: String,
    val subDir: String
) {
  fun getProject(): String {
    val splitUrl = fullUrl.split("/")
    return splitUrl[splitUrl.size - 2]
  }

  fun getRepo(): String {
    val splitUrl = fullUrl.split("/")
    return splitUrl.last().split(".")[0]
  }

  fun getBaseUrl(): String {
    val splitUrl = fullUrl.split("/")
    return "${splitUrl[0]}//${splitUrl[2]}"
  }
}