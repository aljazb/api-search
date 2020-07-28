package com.outbrain.oen.apisearch.utils

import com.google.gson.GsonBuilder
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

open class RetrofitClient @JvmOverloads constructor(val gsonBuilder: GsonBuilder = GsonBuilder()) {

  private val random = Random()

  /**
   * we call this when the request fail even before executeRequest (like in case not targets in consul)
   */
  fun serviceFailed(service: String, endpoint: String, httpMethod: String) {
    val labels = arrayOf(service, endpoint, httpMethod)
  }

  @JvmOverloads
  fun <T> executeRequest(call: Call<T>, service: String, endpoint: String, retryOnAnyError: Boolean = false): T {
    return executeRequestImpl(call, service, endpoint, retryOnAnyError)!!
  }

  @JvmOverloads
  fun <T> executeRequestNoBody(call: Call<T>, service: String, endpoint: String, retryOnAnyError: Boolean = false) {
    executeRequestImpl(call, service, endpoint, retryOnAnyError)
  }

  private fun <T> executeRequestImpl(call: Call<T>, service: String, endpoint: String, retryOnAnyError: Boolean = false): T? {
    val labels = arrayOf(service, endpoint, call.request().method())
    try {
      val response = executeWithRetryOnRateLimit(call, labels, retryOnAnyError)
      if (response.isSuccessful) {
        return response.body()
      } else {
        val body = response.errorBody()!!.string()
        logger.info("failed " + getRequestMessage(call) + " ; response:  " + response.code() + "  - " +
            response.message() + " ; response body: " + body + " ; response headers: " + response.headers())
        throw RuntimeException("failed " + call.request().url() + " (" + response.code() + " " + response.message() + " ) " +
            "headers: " + response.headers() + "  body: - " + body + " .more details in log")
      }
    } catch (e: IOException) {
      throw UncheckedIOException("failed " + getRequestMessage(call), e)
    } catch (e: Exception) {
      throw e
    } finally {
    }
  }

  @Throws(IOException::class)
  private fun <T> executeWithRetryOnRateLimit(call: Call<T>, labels: Array<String>, retryOnAnyError: Boolean = false): Response<T> {
    var response = call.execute()
    var retries = 0
    while (!response.isSuccessful && (response.code() == 429 || retryOnAnyError) && retries < 3) {
      retries++
      val millsToSleep = random.nextInt(4000) + 1000L
      logger.info("hit rate limit so going to retry ($retries), but before will sleep $millsToSleep ms")
      Thread.sleep(millsToSleep)
      response = call.clone().execute()
    }
    return response
  }

  fun <T> getRequestMessage(call: Call<T>): String {
    val body = bodyToString(call.request().body())
    return "request url: '" + call.request().url() + "' body: '" + body + "'"
  }

  private fun bodyToString(requestBody: RequestBody?): String {
    if (requestBody == null) {
      return "null_request"
    }
    val buffer = Buffer()
    try {
      requestBody.writeTo(buffer)
    } catch (e: IOException) {
      logger.warn("failed to read body of request")
      return "body_not_fetched"
    }

    return buffer.readString(Charset.forName("UTF-8"))
  }

  @JvmOverloads
  open fun <T> createService(baseUrl: String, headers: Map<String, String>, bigDecimalAsString: Boolean, clazz: Class<T>, service: String, timeout: TimeSpan = TimeSpan(360, TimeUnit.SECONDS),
                             customTypeAdapters: Map<Class<*>, Any> = emptyMap()): T {
    val debug = false
    val baseUrlWithoutInvisibleChars = StringHelper.replaceNonPrintableChars(baseUrl)
    if (baseUrl != baseUrlWithoutInvisibleChars) {
      logger.warn("found invisible chars in url!!! " + Arrays.toString(baseUrl.toCharArray()))
    }
    val logging = HttpLoggingInterceptor()
    logging.level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
          val builder = chain.request().newBuilder()
          builder.addHeader("Accept", "Application/JSON")
          headers.entries.forEach { e -> builder.addHeader(e.key, e.value) }
          try {
            chain.proceed(builder.build())
          } catch (e: IOException) {
            throw UncheckedIOException("failed creating service " + clazz.simpleName + " on  url " + baseUrlWithoutInvisibleChars, e)
          } catch (e: Exception) {
            throw e
          }
        }.addInterceptor(logging)
        .connectTimeout(timeout.duration, timeout.unit)
        .readTimeout(timeout.duration, timeout.unit)
        .build()

    customTypeAdapters.forEach { type, adapter ->
      gsonBuilder.registerTypeAdapter(type, adapter)
    }
    val gson = gsonBuilder.create()
    val retrofit = Retrofit.Builder().baseUrl(baseUrlWithoutInvisibleChars)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
        .client(httpClient).build()
    return retrofit.create(clazz)

  }

}
