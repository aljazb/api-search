package com.outbrain.oen.apisearch.connectors.apiendpoint

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface ApiEndpointRepository: ElasticsearchRepository<ApiEndpoint, String> {

}