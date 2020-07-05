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

import java.net.MalformedURLException
import java.net.URL

/**
 * A representation of a pull request URL, that provides validation and conversion to the
 * Github restful API equivalent URL.
 */
data class PullRequestUrl internal constructor(
  val owner: String,
  val project: String,
  val number: Int
) {

  val apiAddress: String get() = "https://api.github.com/repos/$owner/$project/pulls/$number"

  companion object {
    /**
     * Create from a URL string, throwing an [InvalidGithubUrl] exception if the supplied
     * URL is invalid or cannot be parsed as a URL.
     */
    @JvmStatic fun create(url: String): PullRequestUrl {
      return try {
        create(URL(url))
      } catch (unused: MalformedURLException) {
        throw InvalidGithubUrl("Pull request url supplied is not a valid url: $url")
      }
    }

    /**
     * Create from a URL string, throwing an [InvalidGithubUrl] exception if the supplied
     * URL is invalid.
     */
    private fun create(url: URL): PullRequestUrl {
      if (url.host != "github.com") {
        throw InvalidGithubUrl("Pull request url is not a github.com url: '%s'", url)
      }
      val (owner, project, _, number) = url.path.substring(1).split("/").also {
        if (it.size != 4 || it[2] != "pull") {
          throw InvalidGithubUrl("Invalid pull request URL: '%s'", url)
        }
      }

      return try {
        PullRequestUrl(owner, project, number.toInt())
      } catch (nfe: NumberFormatException) {
        throw InvalidGithubUrl("Invalid pull request number '$number': '$url'")
      }
    }
  }
}
