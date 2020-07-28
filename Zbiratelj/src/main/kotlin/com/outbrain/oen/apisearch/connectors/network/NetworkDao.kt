package com.outbrain.oen.apisearch.connectors.network

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Networks: IntIdTable() {
  val start = varchar("start", 50)
  val end = varchar("end", 50)
  val bidirectional = bool("bidirectional")
  val weight = double("weight").nullable()
  val type = varchar("type", 50)
  val timestamp = long("timestamp")
}

class Network(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<Network>(Networks)

  var start by Networks.start
  var end by Networks.end
  var bidirectional by Networks.bidirectional
  var weight by Networks.weight
  var type by Networks.type
  var timestamp by Networks.timestamp
}