package com.outbrain.oen.apisearch.domain

import com.outbrain.oen.apisearch.clients.BitbucketClient
import com.outbrain.oen.apisearch.utils.executeRequestNoMetrics
import mu.KotlinLogging
import org.apache.commons.io.IOUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class BitbucketParser(@Autowired private val serviceRepository: ServiceRepository,
                      @Autowired private val databaseScrapper: DatabaseScrapper) {

  val codeFileExtensions = listOf("java", "kt", "scala")

  fun getTableNames(service: String): Set<String> {
    val repositoryInfo = serviceRepository.getGitRepositories()[service] ?: return emptySet()
    val filePaths = getCodeFilePaths(service, repositoryInfo)

    val threadPoolExecutor = Executors.newFixedThreadPool(30) as ThreadPoolExecutor
    val tableNames = mutableSetOf<String>()
    filePaths.forEach { filePath ->
      threadPoolExecutor.submit {
        synchronized(tableNames) {
          tableNames.addAll(getTableNamesFromFile(fetchBitbucketFile(service, repositoryInfo, filePath)))
        }
      }
    }
    threadPoolExecutor.shutdown()
    threadPoolExecutor.awaitTermination(10, TimeUnit.MINUTES)
    logger.info { "Found ${tableNames.size} table names from service $service: $tableNames" }
    return tableNames
  }

  fun getCodeFilePaths(service: String, repositoryInfo: ServiceRepositoryInfo): List<String> {
    val servicePath = if (repositoryInfo.subDir.isEmpty()) service else "${repositoryInfo.subDir}/$service"
    val filePaths = BitbucketClient
        .getClient(repositoryInfo.getBaseUrl(), repositoryInfo.getProject(), repositoryInfo.getRepo())
        .getFiles(servicePath)
        .executeRequestNoMetrics()
        .values
    return filePaths.filter { path ->
      val splitPath = path.split(".")
      splitPath.last() in codeFileExtensions
    }
  }

  fun getTableNamesFromFile(file: String) = databaseScrapper.tableMap.keys.filter { it in file }.toSet()

  private fun fetchBitbucketFile(service: String, repositoryInfo: ServiceRepositoryInfo, filePath: String): String {
    val servicePath = if (repositoryInfo.subDir.isEmpty()) service else "${repositoryInfo.subDir}/$service"
    val url = URL("${repositoryInfo.getBaseUrl()}/projects/${repositoryInfo.getProject()}/repos/${repositoryInfo.getRepo()}/raw/$servicePath/$filePath")
    logger.info { "Fetching file ${filePath.split("/").last()} from service $service (url: $url)" }
    val connection = url.openConnection()
    connection.setRequestProperty("Authorization", BitbucketClient.authorizationToken)
    val stream = connection.getInputStream()
    val encoding = connection.contentEncoding ?: "UTF-8"
    return IOUtils.toString(stream, encoding)
  }

  fun getContributors(service: String): Map<String, Double>? {
    logger.info { "Getting contributors for service $service" }
    val repositoryInfo = serviceRepository.getGitRepositories()[service] ?: return emptyMap()
    val commits = try {
      val client = BitbucketClient.getClient(repositoryInfo.getBaseUrl(), repositoryInfo.getProject(), repositoryInfo.getRepo())
      val servicePath = if (repositoryInfo.subDir.isEmpty()) service else "${repositoryInfo.subDir}/$service"
      val commits = client.getCommits(servicePath).executeRequestNoMetrics()
      if (commits.values.isNotEmpty()) {
        commits
      } else {
        client.getCommits(repositoryInfo.subDir).executeRequestNoMetrics()
      }
    } catch (e: Exception) {
      logger.info(e) { "Failed to fetch commits from service $service: ${e.message}" }
      null
    }

    return commits?.values?.groupingBy { it.author.emailAddress }?.eachCount()?.mapValues { it.value.toDouble() / commits.values.size }
  }
}
