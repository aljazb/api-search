package com.outbrain.oen.apisearch.domain

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.Exception
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class DatabaseScrapper(@Autowired private val databaseHandler: DatabaseHandler) {

  val databaseConnections = listOf(
      DatabaseConnection("--secret--", "--secret--", "--secret--"),
      DatabaseConnection("--secret--", "--secret--", "--secret--"),
      DatabaseConnection("--secret--", "--secret--", "--secret--")
  )
  val tableMap = hashMapOf<String, TableInfo>()

  fun getTableMap(): Map<String, TableInfo> {
    if (tableMap.isEmpty()) {
      logger.warn { "Table info not yet loaded" }
    }
    return tableMap
  }

  fun loadDatabaseInfoAndConnections() {
    logger.info { "Started loading database info" }
    val threadPoolExecutor = Executors.newFixedThreadPool(3) as ThreadPoolExecutor
    databaseConnections.forEach { databaseConnection ->
      threadPoolExecutor.submit {
        try {
          DriverManager.getConnection(databaseConnection.url, databaseConnection.user, databaseConnection.password).use { connection ->
            val metaData: DatabaseMetaData = connection.metaData
            getTableNames(metaData).forEach { tableName ->
              val tableInfo = TableInfo(
                  name = tableName,
                  columns = getColumnNames(metaData, tableName),
                  foreignKeyTables = getForeignKeyTables(metaData, tableName)
              )
              synchronized(tableMap) {
                tableMap[tableName] = tableInfo
              }
            }

          }
        } catch (e: SQLException) {
          logger.warn(e) { e.message }
        }
      }
    }
    threadPoolExecutor.shutdown()
    threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)
    databaseHandler.insertDBInfo(tableMap)
    logger.info { "Finished loading database info" }
  }

  private fun getTableNames(metaData: DatabaseMetaData): Set<String> {
    val tables: ResultSet = metaData.getTables(null, null, null, arrayOf("TABLE"))
    val tableNames = mutableSetOf<String>()
    while (tables.next()) {
      val tableName = tables.getString("TABLE_NAME")
      if ("_" in tableName) {
        tableNames.add(tableName)
      }
    }
    return tableNames
  }

  private fun getColumnNames(metaData: DatabaseMetaData, tableName: String): Set<String> {
    logger.info { "Getting column names for table: $tableName" }
    val columnNames = mutableSetOf<String>()
    try {
      val columns: ResultSet = metaData.getColumns(null, null, tableName, null)
      while (columns.next()) {
        columnNames.add(columns.getString("COLUMN_NAME"))
      }
    } catch (e: Exception) {
      logger.warn(e) { "Could not get column names for table $tableName" }
    }
    return columnNames
  }

  private fun getForeignKeyTables(metaData: DatabaseMetaData, tableName: String): Set<String> {
    logger.info { "Getting foreign key tables for table $tableName" }
    val connectedTables = mutableSetOf<String>()
    try {
      val foreignKeys: ResultSet = metaData.getImportedKeys(null, null, tableName)
      while (foreignKeys.next()) {
        connectedTables.add(foreignKeys.getString("PKTABLE_NAME"))
      }
    } catch (e: Exception) {
      logger.warn(e) { "Could not get foreign key tables for table $tableName" }
    }
    return connectedTables
  }
}

data class TableInfo(
    val name: String,
    val columns: Set<String>,
    val foreignKeyTables: Set<String>
)

data class DatabaseConnection(
    val url: String,
    val user: String,
    val password: String,
    val schema: String? = null
)