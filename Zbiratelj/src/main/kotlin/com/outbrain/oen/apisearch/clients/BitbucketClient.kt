package com.outbrain.oen.apisearch.clients

import com.outbrain.oen.apisearch.utils.RetrofitUtils
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class Files(
    val values: List<String>
)

data class Commits(
    val values: List<Commit>
)

data class Commit(
    val author: Author,
    val authorTimestamp: Long
)

data class Author(
    val emailAddress: String
)

object BitbucketClient {
  val authorizationToken = "--secret--"

  fun getClient(url: String, project: String, repo: String): IBitbucketService {
    return RetrofitUtils.createServiceNoMetrics(
        baseUrl = "$url/rest/api/1.0/projects/$project/repos/$repo/",
        clazz = IBitbucketService::class.java,
        headers = mapOf("Authorization" to authorizationToken)
    )
  }
}

interface IBitbucketService {
  @GET("files/{servicePath}")
  fun getFiles(@Path("servicePath", encoded = true) servicePath: String, @Query("limit") limit: Int = 10000): Call<Files>

  @GET("commits")
  fun getCommits(@Query("path", encoded = true) path: String, @Query("limit") limit: Int = 100): Call<Commits>
}
