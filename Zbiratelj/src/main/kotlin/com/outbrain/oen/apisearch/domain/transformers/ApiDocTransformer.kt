package com.outbrain.oen.apisearch.domain.transformers

import v2.io.swagger.models.properties.ArrayProperty
import v2.io.swagger.models.properties.RefProperty
import com.outbrain.oen.apisearch.connectors.apiendpoint.ApiEndpoint
import com.outbrain.oen.apisearch.connectors.parameter.*
import com.outbrain.oen.apisearch.connectors.response.EndpointResponse
import io.swagger.models.HttpMethod
import mu.KotlinLogging
import org.springframework.stereotype.Service
import v2.io.swagger.models.*
import v2.io.swagger.models.parameters.*
import v2.io.swagger.models.properties.Property
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class ApiDocTransformer {

  fun transformApiDoc(serviceName: String, apiDoc: Swagger, tables: Set<String>): TransformedApiDoc {
    val endpoints = mutableListOf<ApiEndpoint>()
    val parameters = mutableListOf<EndpointParameter>()
    val responses = mutableListOf<EndpointResponse>()

    val paths = apiDoc.paths ?: emptyMap()
    for (path in paths) {
      val pathMethodTypes = listOf(
          Pair(HttpMethod.GET, path.value.get),
          Pair(HttpMethod.PUT, path.value.put),
          Pair(HttpMethod.POST, path.value.post),
          Pair(HttpMethod.HEAD, path.value.head),
          Pair(HttpMethod.DELETE, path.value.delete),
          Pair(HttpMethod.PATCH, path.value.patch),
          Pair(HttpMethod.OPTIONS, path.value.options))

      for (pathMethodType in pathMethodTypes) {
        val endpointId = buildApiEndpointId(serviceName, path.key, pathMethodType.first)
        pathMethodType.second?.let { endpoints.add(buildApiEndpoint(endpointId, serviceName, path.key, pathMethodType.first, it, tables)) }
        parameters.addAll(pathMethodType.second?.parameters?.map { parameter ->
          buildEndpointParameter(buildParameterId(
              serviceName, path.key, pathMethodType.first, parameter.name ?: "/"), endpointId, parameter, apiDoc)
        } ?: emptyList())
        pathMethodType.second?.responses?.let { responses.add(buildEndpointResponse(buildResponseId(serviceName, path.key, pathMethodType.first), endpointId, it, apiDoc)) }
      }
    }
    return TransformedApiDoc(endpoints, parameters, responses)
  }

  private fun buildApiEndpoint(id: String, serviceName: String, path: String, httpMethod: HttpMethod, operation: Operation, tables: Set<String>) =
      ApiEndpoint(
          id = id,
          service = serviceName,
          path = path,
          httpMethod = httpMethod,
          summary = operation.summary,
          controller = operation.tags.firstOrNull(),
          consumes = operation.consumes?.firstOrNull(),
          produces = operation.produces?.firstOrNull(),
          deprecated = operation.isDeprecated ?: false,
          tables = tables)

  private fun buildEndpointParameter(id: String, endpointId: String, parameter: Parameter, apiDoc: Swagger): EndpointParameter {
    when (parameter) {
      is BodyParameter -> {
        var modelName: String? = null
        var fields: List<GenericParameter>? = null
        if (parameter.schema != null) {
          val refParameter: ModelImpl?
          when (parameter.schema) {
            is RefModel -> {
              modelName = (parameter.schema as RefModel).simpleRef
              refParameter = getRefParameter(modelName, apiDoc)
            }
            is ArrayModel -> {
              val schema = parameter.schema as ArrayModel
              return buildArrayParameter(id, endpointId, parameter, schema.items, apiDoc)
            }
            else -> refParameter = parameter.schema as ModelImpl
          }
          fields = refParameter?.properties?.map { (name, property) ->
            buildSubParameter(name, property, name in (refParameter.required ?: emptyList()), apiDoc, emptySet())
          }
        }
        return EndpointParameter(
            id = id,
            endpoint = endpointId,
            name = parameter.name,
            location = ParameterLocation.fromString(parameter.`in`),
            required = parameter.required,
            modelName = modelName,
            fields = fields)
      }
      else -> {
        assert(parameter is SerializableParameter)
        parameter as SerializableParameter
        if (parameter.type == "array") {
          return buildArrayParameter(id, endpointId, parameter, parameter.items, apiDoc)
        }
        return EndpointParameter(
            id = id,
            endpoint = endpointId,
            name = parameter.name,
            location = ParameterLocation.fromString(parameter.`in`),
            required = parameter.required,
            type = parameter.type?.let { ParameterType.fromString(it) },
            enum = parameter.enum,
            format = parameter.format?.let { ParameterFormat.fromString(it) })
      }
    }
  }

  private fun buildEndpointResponse(id: String, endpointId: String, responses: Map<String, Response>, apiDoc: Swagger): EndpointResponse =
      EndpointResponse(
          id = id,
          endpoint = endpointId,
          statusCodes = responses.keys.map { it.toInt() },
          field = responses.values.firstOrNull { it.schema != null }?.schema?.let { buildSubParameter("", it, false, apiDoc, emptySet()) }
      )

  private fun buildArrayParameter(id: String?, endpointId: String, parameter: Parameter, items: Property, apiDoc: Swagger): EndpointParameter {
    val subParameter = buildSubParameter(parameter.name, items, parameter.required, apiDoc, emptySet())
    return EndpointParameter(
        id = id,
        endpoint = endpointId,
        name = parameter.name,
        location = ParameterLocation.fromString(parameter.`in`),
        required = parameter.required,
        type = subParameter.type,
        array = true,
        enum = subParameter.enum,
        format = subParameter.format,
        fields = subParameter.fields)
  }

  private fun buildSubParameter(name: String, property: Property, required: Boolean, apiDoc: Swagger, visitedParameters: Set<String>, array: Boolean = false): SubParameter =
      when (property) {
        is ArrayProperty -> buildSubParameter(name, property.items, required, apiDoc, visitedParameters, true)
        is RefProperty -> {
          val modelName = property.simpleRef
          val refParameter = if (modelName !in visitedParameters) getRefParameter(modelName, apiDoc) else null
          SubParameter(
              name = name,
              required = required,
              type = ParameterType.fromString(property.type),
              array = array,
              format = property.format?.let { ParameterFormat.fromString(it) },
              modelName = modelName,
              fields = refParameter?.properties?.map { (key, value) ->
                buildSubParameter(key, value, key in (refParameter.required
                    ?: emptyList()), apiDoc, visitedParameters + modelName)
              })
        }
        else -> SubParameter(
            name = name,
            required = required,
            type = ParameterType.fromString(property.type),
            array = array,
            format = property.format?.let { ParameterFormat.fromString(it) })
      }

  private fun getRefParameter(reference: String, apiDoc: Swagger): ModelImpl? {
    if (apiDoc.definitions == null || reference !in apiDoc.definitions) return null
    val parameter = apiDoc.definitions[reference]
    return parameter as ModelImpl
  }

  private fun buildParameterId(serviceName: String, path: String, httpMethod: HttpMethod, parameterName: String): String {
    return Base64.getUrlEncoder().encodeToString((serviceName + path + httpMethod.name + parameterName).toByteArray())
  }

  private fun buildResponseId(serviceName: String, path: String, httpMethod: HttpMethod): String {
    return Base64.getUrlEncoder().encodeToString((serviceName + path + httpMethod.name).toByteArray())
  }

  private fun buildApiEndpointId(serviceName: String, path: String, httpMethod: HttpMethod): String {
    return Base64.getUrlEncoder().encodeToString((serviceName + path + httpMethod.name).toByteArray())
  }
}

data class TransformedApiDoc(
    val apiEndpoints: List<ApiEndpoint>,
    val parameters: List<EndpointParameter>,
    val responses: List<EndpointResponse>
)