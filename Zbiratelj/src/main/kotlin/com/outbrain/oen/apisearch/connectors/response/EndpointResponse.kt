package com.outbrain.oen.apisearch.connectors.response

import com.outbrain.oen.apisearch.connectors.parameter.GenericParameter
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "apisearchresponse", type = "endpointresponse")
data class EndpointResponse(
    @Id
    val id: String,
    val endpoint: String,
    val statusCodes: List<Int>,
    val field: GenericParameter?
)