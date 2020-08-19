package com.google.devtools.moe.client.config

import com.google.devtools.moe.client.InvalidProject
import com.squareup.moshi.Json
import java.util.regex.Pattern

/** Configuration for a scrubber. */
class ScrubberConfig(
  // General options
  @Json(name = "ignore_files_pattern")
  private val ignoreFilesRe: String? = null,
  @Json(name = "do_not_scrub_files_pattern")
  private val doNotScrubFilesRe: String? = null,
  @Json(name = "extension_map")
  private val extensionMap: String? = null,
  @Json(name = "sensitive_string_file")
  private val sensitiveStringFile: String? = null,
  @Json(name = "sensitive_words")
  private val sensitiveWords: List<String>? = null,
  @Json(name = "sensitive_patterns")
  private val sensitiveRes: List<String>? = null,
  private val whitelist: List<String>? = null,
  @Json(name = "scrub_sensitive_comments")
  private val scrubSensitiveComments: Boolean = true,
  @Json(name = "rearranging_config")
  private val rearrangingConfig: String? = null,
  @Json(name = "string_replacements")
  private val stringReplacements: List<Map<String, String>>? = null,
  @Json(name = "regex_replacements")
  private val regexReplacements: List<Map<String, String>>? = null,
  @Json(name = "scrub_non_documentation_comments")
  private val scrubNonDocumentationComments: Boolean = false,
  @Json(name = "scrub_all_comments")
  private val scrubAllComments: Boolean = false,

  // User options
  @Json(name = "usernames_to_scrub")
  private val usernamesToScrub: MutableSet<String> = mutableSetOf(),
  @Json(name = "usernames_to_publish")
  private val usernamesToPublish: MutableSet<String> = mutableSetOf(),
  @Json(name = "usernames_file")
  var usernamesFile: String? = null,
  @Json(name = "scrub_unknown_users")
  private val scrubUnknownUsers: Boolean = false,
  @Json(name = "scrub_authors")
  private val scrubAuthors: Boolean = true
) {

  /** @param author Author in the format `Name <username@domain>`. */
  @Throws(InvalidProject::class)
  fun shouldScrubAuthor(author: String) = scrubAuthors &&
    if (scrubUnknownUsers) !matchesUsername(author, usernamesToPublish)
    else matchesUsername(author, usernamesToScrub)

  /**
   * Called by ProjectContextFactory to update usernamesConfig with external usernames file.
   */
  fun updateUsernames(usernamesConfig: UsernamesConfig) {
    usernamesToScrub.addAll(usernamesConfig.scrubbableUsernames)
    usernamesToPublish.addAll(usernamesConfig.publishableUsernames)
    // reset usernamesFile to null so that we don't read the file again
    usernamesFile = null
  }

  // TODO(cgruber): Parse out the actual usernames in a custom deserializer.
  private fun matchesUsername(author: String, usernames: Set<String>?) =
    usernames?.any { author.matches(".*<${Pattern.quote(it)}@.*".toRegex()) } ?: false
}
