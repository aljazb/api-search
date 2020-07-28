package com.outbrain.oen.apisearch.connectors.parameter

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface EndpointParameterRepository: ElasticsearchRepository<EndpointParameter, String> {

}