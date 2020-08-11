/*
 * Copyright (c) 2011 Google, Inc.
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
package com.google.devtools.moe.client.config

import com.squareup.moshi.Json

/** Configuration for a MOE metadata scrubber. */
data class MetadataScrubberConfig(
  @Json(name = "usernames_to_scrub")
  val usernamesToScrub: List<String> = listOf(),

  /**
   * Access the boolean field, which can be used as a trigger for
   * the proper MetadataScrubber to scrub any confidential words.
   * @return boolean whether confidential words should be scrubbed
   */
  @Json(name = "scrub_confidential_words")
  val scrubConfidentialWords: Boolean = true,

  @Json(name = "restore_original_author")
  val restoreOriginalAuthor: Boolean = true,

  /** A list of regular expressions for sensitive text to be scrubbed from revision metadata.  */
  @Json(name = "sensitive_text_patterns")
  val sensitiveRes: List<String> = listOf(),

  /**
   * Formatting for changelog adapted from fromRepository for commits in toRepository. See
   * [com.google.devtools.moe.client.repositories.DescriptionMetadataScrubber].
   */
  @Json(name = "log_format")
  val logFormat: String = "{description}\n\n\tChange on {date} by {author}\n"
) {

  // Fake creators (for Java based tests that need to construct partial configs.
  companion object {
    @JvmStatic fun createFakeWithUsernames(vararg usernamesToScrub: String) =
      MetadataScrubberConfig(usernamesToScrub = usernamesToScrub.toList())

    @JvmStatic fun createFakeWithLogFormat(logFormat: String) =
      MetadataScrubberConfig(logFormat = logFormat)

    @JvmStatic fun createFakeWithRestoreOriginalAuthor(restoreOriginalAuthor: Boolean) =
      MetadataScrubberConfig(restoreOriginalAuthor = restoreOriginalAuthor)
  }
}
