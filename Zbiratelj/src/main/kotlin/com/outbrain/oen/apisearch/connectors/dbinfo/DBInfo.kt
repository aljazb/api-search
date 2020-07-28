package com.outbrain.oen.apisearch.connectors.dbinfo

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Tables: IntIdTable() {
  val name = varchar("name", 100).index()
  val foreignKeys = varchar("foreignKeys", 1000)
  val timestamp = long("timestamp")
}

object Columns: IntIdTable() {
  val name = varchar("name", 100)
  val table = reference("table", Tables)
  val timestamp = long("timestamp")
}

class Table(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<Table>(Tables)

  var name by Tables.name
  var foreignKeys by Tables.foreignKeys
  var timestamp by Tables.timestamp

}

class Column(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<Column>(Columns)

  var name by Columns.name
  var table by Table referencedOn Columns.table
  var timestamp by Columns.timestamp
}
