package com.outbrain.oen.apisearch.domain

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Service
class IndexingScheduler(@Autowired private val indexer: Indexer) {

//  @Scheduled(cron = "0 0 * ? * *")
  fun runApiDocIndexing() {
    logger.info { "Started scheduled indexing" }
    indexer.run()
  }
}