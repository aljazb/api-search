package com.outbrain.oen.apisearch.connectors.parameter

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EndpointParameterDao(@Autowired private val endpointParameterRepository: EndpointParameterRepository) {
  fun saveEndpointParameters(endpointParameters: List<EndpointParameter>) {
    endpointParameterRepository.saveAll(endpointParameters)
  }
}