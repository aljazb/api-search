package com.outbrain.oen.apisearch.connectors.serviceinfo

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Endpoints: IntIdTable() {
  val name = varchar("name", 100).index()
  val service = varchar("service", 50)
  val requests = double("requests").nullable()
}

class Endpoint(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<Endpoint>(Endpoints)

  var name by Endpoints.name
  var service by Endpoints.service
  var requests by Endpoints.requests
}
