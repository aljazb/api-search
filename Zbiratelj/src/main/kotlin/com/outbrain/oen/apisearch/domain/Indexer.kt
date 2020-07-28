package com.outbrain.oen.apisearch.domain

import com.outbrain.oen.apisearch.clients.DyplomaClient
import com.outbrain.oen.apisearch.clients.IDyplomaService
import com.outbrain.oen.apisearch.connectors.apiendpoint.ApiEndpointDao
import com.outbrain.oen.apisearch.connectors.parameter.EndpointParameterDao
import com.outbrain.oen.apisearch.connectors.response.EndpointResponseDao
import com.outbrain.oen.apisearch.domain.transformers.ApiDocTransformer
import com.outbrain.oen.apisearch.utils.executeRequestNoMetrics
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.stereotype.Service
import v2.io.swagger.models.Swagger
import v2.io.swagger.models.auth.AuthorizationValue
import v2.io.swagger.parser.SwaggerParser
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

private val API_DOC_LOCATIONS = listOf(
    "api/swagger/apiDocs",
    "api/swagger/v2/apiDocs",
    "api/api/swagger/apiDocs",
    "api/internal/v2/api/swagger/apiDocs",
    "app/api/swagger/apiDocs",
    "/v2/api-docs")
private const val URL_CHECK_TIMEOUT = 3000

private val basicAuthService = listOf(
    "--secret--" to AuthorizationValue("--secret--", "--secret--", "header"),
    "--secret--" to AuthorizationValue("--secret--", "--secret--", "header")
).toMap()

@Service
class Indexer(@Autowired private val meterRegistry: MeterRegistry,
              @Autowired private val discoveryClient: DiscoveryClient,
              @Autowired private val apiDocTransformer: ApiDocTransformer,
              @Autowired private val apiEndpointDao: ApiEndpointDao,
              @Autowired private val endpointParameterDao: EndpointParameterDao,
              @Autowired private val endpointResponseDao: EndpointResponseDao,
              @Autowired private val bitbucketParser: BitbucketParser,
              @Autowired private val databaseHandler: DatabaseHandler) {

  val apiDocFoundCounter = meterRegistry.counter("apiDocFound")
  val apiDocNotFoundCounter = meterRegistry.counter("apiDocNotFound")
  val apiDocCouldNotParseCounter = meterRegistry.counter("apiDocCouldNotParse")

  private var serviceInfos = mutableListOf<ServiceMeta>()

  fun run() {
    val threadPoolExecutor = Executors.newFixedThreadPool(10) as ThreadPoolExecutor
    val services = discoveryClient.services
    logger.info { "Found ${services.size} services" }
    val dyplomaClient = DyplomaClient.getProdActiveClient()
    serviceInfos = mutableListOf()
    services.forEach { processService(it, dyplomaClient) }
    threadPoolExecutor.shutdown()
    threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)
    databaseHandler.insertServiceInfo(serviceInfos)
    logger.info { "Finished indexing" }
  }

  private fun processService(service: String, dyplomaClient: IDyplomaService) {
    logger.info { "Working on service $service" }
    val serviceInstance = discoveryClient.getInstances(service)?.firstOrNull()
    var hasApiDoc = false
    if (serviceInstance != null) {
      val serviceHost = serviceInstance.host
      val servicePort = serviceInstance.port
      val docUrls = API_DOC_LOCATIONS.map { "http://$serviceHost:$servicePort/$service/$it" }
      val serviceAuth = basicAuthService[service]
      val correctUrl = docUrls.firstOrNull { urlExists(it, serviceAuth) }
      if (correctUrl != null) {
        logger.info { "Found api docs for service $service ($correctUrl)" }
        val auth = serviceAuth?.let { listOf(it) } ?: listOf()
        val doc = SwaggerParser().read(correctUrl, auth, false)
        if (doc != null) {
          apiDocFoundCounter.increment()
          try {
            processTablesAndApiDoc(service, doc)
            hasApiDoc = true
          } catch (e: Exception) {
            logger.warn(e) { "Failed to process API doc for service $service with exception: ${e.message}" }
          }
        } else {
          apiDocCouldNotParseCounter.increment()
          logger.warn { "Failed to parse api docs for service $service" }
        }
      } else {
        apiDocNotFoundCounter.increment()
        logger.info { "No api docs found for service $service" }
      }
    } else {
      logger.warn { "Could not find service $service in consul" }
    }

    try {
      val owner = dyplomaClient.getServiceByName(service).executeRequestNoMetrics().owner
      synchronized(serviceInfos) {
        serviceInfos.add(ServiceMeta(service, owner, hasApiDoc))
      }
    } catch (e: Exception) {
      logger.info { "Dyploma client could not find service $service" }
    }
  }

  fun processTablesAndApiDoc(serviceName: String, doc: Swagger) {
    logger.info { "Started processing tables for service $serviceName" }
    val tableNames = bitbucketParser.getTableNames(serviceName)
    logger.info { "Started processing api doc for service $serviceName" }
    val transformedApiDoc = apiDocTransformer.transformApiDoc(serviceName, doc, tableNames)
    if (transformedApiDoc.apiEndpoints.isNotEmpty()) {
      logger.info { "Writing api endpoints of service $serviceName to elasticsearch" }
      apiEndpointDao.saveApiEndpoints(transformedApiDoc.apiEndpoints)
      if (transformedApiDoc.parameters.isNotEmpty()) {
        logger.info { "Writing endpoint parameters to elasticsearch" }
        endpointParameterDao.saveEndpointParameters(transformedApiDoc.parameters)
      } else {
        logger.info { "Service $serviceName doesn't have any endpoint parameters" }
      }
      if (transformedApiDoc.responses.isNotEmpty()) {
        logger.info { "Writing endpoint responses to elasticsearch" }
        endpointResponseDao.saveEndpointResponses(transformedApiDoc.responses)
      } else {
        logger.info { "Service $serviceName doesn't have any endpoint responses" }
      }
    } else {
      logger.info { "Service $serviceName doesn't have any endpoints" }
    }
  }
}

private fun urlExists(url: String, auth: AuthorizationValue?): Boolean {
  return try {
    HttpURLConnection.setFollowRedirects(false)
    val urlConnection = URL(url).openConnection() as HttpURLConnection
    urlConnection.connectTimeout = URL_CHECK_TIMEOUT
    urlConnection.readTimeout = URL_CHECK_TIMEOUT
    auth?.let { urlConnection.setRequestProperty(it.keyName, it.value) }
    urlConnection.responseCode == HttpURLConnection.HTTP_OK
  } catch (socketTimeoutException: SocketTimeoutException) {
    logger.info { "Connection to the url $url timed out after $URL_CHECK_TIMEOUT ms" }
    false
  } catch (e: Exception) {
    logger.info { "URL check failed for url $url with message: ${e.message}" }
    false
  }
}

data class ServiceMeta(
    val name: String,
    val owner: String,
    val hasApiDoc: Boolean
)