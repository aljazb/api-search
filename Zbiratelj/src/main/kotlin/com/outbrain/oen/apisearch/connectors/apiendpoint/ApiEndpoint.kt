package com.outbrain.oen.apisearch.connectors.apiendpoint

import io.swagger.models.HttpMethod
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "apisearchendpoint", type = "apiendpoint")
data class ApiEndpoint(
    @Id
    val id: String,
    val service: String,
    val path: String,
    val httpMethod: HttpMethod,
    val controller: String?,
    val summary: String?,
    val consumes: String?,
    val produces: String?,
    val deprecated: Boolean,
    val tables: Set<String>?
)