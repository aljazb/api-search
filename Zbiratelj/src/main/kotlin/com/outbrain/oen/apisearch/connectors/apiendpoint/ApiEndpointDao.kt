package com.outbrain.oen.apisearch.connectors.apiendpoint

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ApiEndpointDao(@Autowired private val apiEndpointRepository: ApiEndpointRepository) {
  fun saveApiEndpoints(apiEndpoints: List<ApiEndpoint>) {
    apiEndpointRepository.saveAll(apiEndpoints)
  }
}