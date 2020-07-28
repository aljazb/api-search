package com.outbrain.oen.apisearch.connectors.parameter

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

interface GenericParameter {
  val name: String
  val required: Boolean
  val type: ParameterType?
  val array: Boolean
  val enum: List<String>?
  val format: ParameterFormat?
  val modelName: String?
  val fields: List<GenericParameter>?
}

@Document(indexName = "apisearchparameter", type = "endpointparameter")
data class EndpointParameter(
    @Id
    val id: String?,
    val endpoint: String,
    override val name: String,
    val location: ParameterLocation,
    override val required: Boolean,
    override val type: ParameterType? = null,
    override val array: Boolean = false,
    override val enum: List<String>? = null,
    override val format: ParameterFormat? = null,
    override val modelName: String? = null,
    override val fields: List<GenericParameter>? = null
): GenericParameter

data class SubParameter(
    override val name: String,
    override val type: ParameterType? = null,
    override val array: Boolean = false,
    override val required: Boolean,
    override val enum: List<String>? = null,
    override val format: ParameterFormat? = null,
    override val modelName: String? = null,
    override val fields: List<GenericParameter>? = null
): GenericParameter

enum class ParameterLocation(val string: String) {
  PATH("path"),
  QUERY("query"),
  HEADER("header"),
  COOKIE("cookie"),
  BODY("body"),
  FORM_DATA("formData");

  companion object {
    fun fromString(value: String): ParameterLocation = values().first { it.string == value }
  }
}

enum class ParameterType(val string: String) {
  INTEGER("integer"),
  NUMBER("number"),
  STRING("string"),
  BOOLEAN("boolean"),
  FILE("file"),
  REF("ref"),
  OBJECT("object");

  companion object {
    fun fromString(value: String): ParameterType = values().first { it.string == value }
  }
}

enum class ParameterFormat(val string: String) {
  INT32("int32"),
  INT64("int64"),
  FLOAT("float"),
  DOUBLE("double"),
  BYTE("byte"),
  BINARY("binary"),
  DATE("date"),
  DATE_TIME("date-time"),
  PASSWORD("password"),
  MULTI("multi");

  companion object {
    fun fromString(value: String): ParameterFormat = values().first { it.string == value }
  }
}