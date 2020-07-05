/*
 * Copyright (c) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.moe.client.github

import com.google.devtools.moe.client.github.GithubAPI.PullRequest
import com.google.devtools.moe.client.github.PullRequestUrl.Companion.create
import com.squareup.moshi.Moshi
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Request.Builder
import java.io.IOException
import javax.inject.Inject

/**
 * A client for accessing github APIs
 */
class GithubClient @Inject internal constructor(
  private val moshi: Moshi,
  private val httpClient: OkHttpClientWrapper
) {

  /**
   * Issues a blocking request to the GitHub API to supply all of the metadata around
   * a given pull request url, populating a [PullRequest] instance (and all of its
   * contained classes);
   */
  fun getPullRequest(pullRequestUrl: String): PullRequest? {
    val jsonString = httpClient.getResponseJson(create(pullRequestUrl))
    return try {
      moshi.adapter(PullRequest::class.java).fromJson(jsonString)
    } catch (e: IOException) {
      throw RuntimeException("Could not parse pull request $pullRequestUrl", e)
    }
  }

  /** A thin wrapper around [OkHttpClient] to make testing a bit cleaner.  */
  internal open class OkHttpClientWrapper @Inject constructor(private val client: OkHttpClient) {
    open fun getResponseJson(id: PullRequestUrl): String {
      val request: Request = Builder()
        .url(id.apiAddress)
        .addHeader("User-Agent", "OkHttpClient/1.0 (Make Open Easy repository sync software)")
        .build()
      return try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          when (response.code()) {
            404 -> throw InvalidGithubUrl("No such pull request found: $id")
            403 -> throw InvalidGithubUrl("Github rate-limit reached - please wait 60 mins: $id")
            else -> throw IOException("Unexpected code $response")
          }
        }
        response.body().string()
      } catch (ioe: IOException) {
        throw RuntimeException(ioe)
      }
    }
  }
}
