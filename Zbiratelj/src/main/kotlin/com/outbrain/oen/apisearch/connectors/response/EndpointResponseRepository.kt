package com.outbrain.oen.apisearch.connectors.response

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface EndpointResponseRepository: ElasticsearchRepository<EndpointResponse, String> {

}