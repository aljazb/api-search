package com.outbrain.oen.apisearch.domain

import com.outbrain.oen.apisearch.connectors.dbinfo.Column
import com.outbrain.oen.apisearch.connectors.dbinfo.Table
import com.outbrain.oen.apisearch.connectors.network.Network
import com.outbrain.oen.apisearch.connectors.serviceinfo.Endpoint
import com.outbrain.oen.apisearch.connectors.serviceinfo.Endpoints
import com.outbrain.oen.apisearch.connectors.serviceinfo.Endpoints.requests
import com.outbrain.oen.apisearch.connectors.serviceinfo.ServiceInfo
import com.outbrain.oen.apisearch.connectors.serviceinfo.ServiceInfos
import mu.KotlinLogging
import org.elasticsearch.common.collect.Tuple
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class DatabaseHandler {
  init {
    Database.connect("jdbc:mysql://127.0.0.1:3306/api_search?serverTimezone=CET", user = "root", password = "--secret--")
  }

  fun insertNetwork(edges: List<Edge>, networkType: NetworkType) {
    logger.info { "Inserting network of type ${networkType.name} to the database" }
    val transactionTimestamp = System.currentTimeMillis()
    transaction {
      for (edge in edges) {
        Network.new {
          start = edge.start
          end = edge.end
          bidirectional = networkType.bidirectional
          weight = edge.weight
          type = networkType.name
          timestamp = transactionTimestamp
        }
      }
    }
  }

  fun insertDBInfo(tableMap: Map<String, TableInfo>) {
    logger.info { "Inserting table and column information to the database" }
    val transactionTimestamp = System.currentTimeMillis()
    transaction {
      for ((tableName, tableInfo) in tableMap) {
        val dbTable = Table.new {
          name = tableName
          foreignKeys = tableInfo.foreignKeyTables.joinToString(",")
          timestamp = transactionTimestamp
        }
        tableInfo.columns.forEach {
          Column.new {
            name = it
            table = dbTable
            timestamp = transactionTimestamp
          }
        }
      }
    }
  }

  fun insertPageRank(serviceRank: Map<String, Double>, type: PageRankType) {
    logger.info { "Inserting page rank information to the database" }
    transaction {
      for ((service, pageRank) in serviceRank) {
        ServiceInfos.update({ ServiceInfos.name eq service}) {
          when(type) {
            PageRankType.CONSUL -> it[consulPageRank] = pageRank
            PageRankType.METRICS -> it[metricsPageRank] = pageRank
            PageRankType.WEIGHTED_METRICS -> it[weightedPageRank] = pageRank
          }
        }
      }
    }
  }

  fun insertEndpointMetrics(endpointMetrics: Map<Pair<String, String>, Double?>) {
    logger.info { "Inserting endpoint metrics information to the database" }
    transaction {
      for ((endpointService, rps) in endpointMetrics) {
        if (Endpoints.select { (Endpoints.name eq endpointService.first) and (Endpoints.service eq endpointService.second) }.empty()) {
          Endpoint.new {
            name = endpointService.first
            service = endpointService.second
            requests = rps
          }
        } else {
          Endpoints.update({ (Endpoints.name eq endpointService.first) and (Endpoints.service eq endpointService.second) }) {
            it[requests] = rps
          }
        }
      }
    }
  }

  fun insertServiceInfo(services: List<ServiceMeta>) {
    logger.info { "Inserting service and owner information to the database" }
    val transactionTimestamp = System.currentTimeMillis()
    transaction {
      for (serviceInfo in services) {
        ServiceInfo.new {
          name = serviceInfo.name
          owner = serviceInfo.owner
          hasApiDoc = serviceInfo.hasApiDoc
          timestamp = transactionTimestamp
        }
      }
    }
  }
}

data class Edge(
    val start: String,
    val end: String,
    val weight: Double? = null
)

enum class NetworkType(val bidirectional: Boolean) {
  CONSUL(false),
  METRICS(false),
  GIT(true)
}

enum class PageRankType {
  CONSUL,
  METRICS,
  WEIGHTED_METRICS
}