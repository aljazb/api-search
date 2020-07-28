package com.outbrain.oen.apisearch.connectors.serviceinfo

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object ServiceInfos: IntIdTable() {
  val name = varchar("name", 50).index()
  val owner = varchar("owner", 50)
  val hasApiDoc = bool("hasApiDoc")
  val consulPageRank = double("consulPageRank").nullable()
  val metricsPageRank = double("metricsPageRank").nullable()
  val weightedPageRank = double("metricsWeightedPageRank").nullable()
  val timestamp = long("timestamp")
}

class ServiceInfo(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<ServiceInfo>(ServiceInfos)

  var name by ServiceInfos.name
  var owner by ServiceInfos.owner
  var hasApiDoc by ServiceInfos.hasApiDoc
  var consulPageRank by ServiceInfos.consulPageRank
  var metricsPageRank by ServiceInfos.metricsPageRank
  var weightedMetricsPageRank by ServiceInfos.weightedPageRank
  var timestamp by ServiceInfos.timestamp

}
