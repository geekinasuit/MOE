package com.google.devtools.moe.client.config

import com.squareup.moshi.Json

/** Configuration for the scrubber usernames.  */
data class UsernamesConfig(
  @Json(name = "scrubbable_usernames")
  val scrubbableUsernames: List<String> = listOf(),
  @Json(name = "publishable_usernames")
  val publishableUsernames: List<String> = listOf()
)
