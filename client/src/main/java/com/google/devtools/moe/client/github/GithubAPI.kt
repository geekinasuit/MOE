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

import com.squareup.moshi.Json

/** Gson-ready value types representing the needed subset of Github's restful API. */
class GithubAPI private constructor() {
  /** Represents the metadata from a pull-request on github.com.  */
  data class PullRequest(
    val number: Long,
    val url: String?,

    @Json(name = "html_url")
    val htmlUrl: String?,
    val title: String?,
    val repo: Repo?,
    val head: Commit?,
    val base: Commit?,
    val state: IssueState?,
    val merged: Boolean,

    @Json(name = "mergeable_state")
    val mergeableState: MergeableState?
  )

  /** Represents the metadata from a commit on github.com.  */
  data class Commit(
    val label: String?,
    val user: User?,
    val repo: Repo?,

    /**
     * In a pull request, this is the owner/branch information that identifies the branch for
     * which this commit is a HEAD pointer.
     */
    val ref: String?,
    val sha: String?
  )

  /** Represents the metadata for a repository as hosted on github.com.  */
  data class Repo(
    val id: Long,
    val name: String?,
    val owner: User?,

    @Json(name = "clone_url")
    val cloneUrl: String?
  )

  /** Represents the metadata for a user on github.com.  */
  data class User(
    val id: Long,
    val login: String?
  )

  /** The current status of the pull request issue (open, closed) */
  @Suppress("EnumEntryName")
  enum class IssueState {
    open,
    closed
  }

  /**
   * The current state of the pull-request with respect to the safety of merging cleanly against
   * the base commit.
   */
  @Suppress("EnumEntryName")
  enum class MergeableState {
    /** This pull request could be cleanly merged against its base  */
    clean,

    /** Clean for merging but invalid (failing build, or some other readiness check  */
    unstable,

    /** Unmergable, since the merge base is 'dirty'  */
    dirty,

    /** Github hasn't computed mergeability, but this request has kicked off a job.  Retry.  */
    unknown
  }
}
