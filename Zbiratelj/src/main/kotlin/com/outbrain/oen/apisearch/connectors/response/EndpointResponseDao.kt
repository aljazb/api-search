package com.outbrain.oen.apisearch.connectors.response

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EndpointResponseDao(@Autowired private val endpointResponseRepository: EndpointResponseRepository) {
  fun saveEndpointResponses(endpointResponses: List<EndpointResponse>) {
    endpointResponseRepository.saveAll(endpointResponses)
  }
}