package com.google.devtools.moe.client.config

import com.google.common.collect.ImmutableList
import com.google.devtools.moe.client.InvalidProject
import com.squareup.moshi.Json

/** Configuration for a MOE Repository. */
data class RepositoryConfig(
  val type: String,

  val url: String? = null,

  @Json(name = "project_space")
  val projectSpace: String = "public",

  @Json(name = "build_target")
  val buildTarget: String? = null,

  @Json(name = "package")
  val buildTargetPackage: String? = null,

  @Json(name = "preserve_authors")
  val preserveAuthors: Boolean = false,

  val paths: List<String> = listOf(),

  /**
   * A list of regular expression patterns whose matching files will be ignored when
   * calculating the relevant file set to compare codebases.
   */
  @Json(name = "ignore_file_patterns")
  val ignoreFilePatterns: List<String> = listOf(),

  /**
   * A list of patterns whose matching files will be set as executable. Useful for repository
   * types which do not store file metadata.
   */
  @Json(name = "executable_file_patterns")
  val executableFilePatterns: List<String> = listOf(),

  /**
   * List of regexes for filepaths for which changes will be ignored. In other words, in the repo's
   * Writer, changes to filepaths matching any of these regexes will be ignored -- no additions,
   * deletions, or modifications of matching filepaths.
   */
  @Json(name = "ignore_incoming_changes_patterns")
  private val ignoreIncomingChangesPatterns: List<String> = ImmutableList.of(),

  /** If present, constrain this repository to the specified branch */
  val branch: String? = null,

  /** A list of subdirectories to checkout, otherwise the whole repository will be checked out. */
  @Json(name = "checkout_paths")
  val checkoutPaths: List<String> = ImmutableList.of(),

  /** Is the repository configured to check out only at the revision point of interest. */
  @Json(name = "shallow_checkout")
  val shallowCheckout: Boolean = false

) : ValidatingConfig {

  /** Validates the repository configuration. */
  @Throws(InvalidProject::class)
  override fun validate() {
    InvalidProject.assertFalse(type.isBlank(), "Must set repository type.")
  }

  // Temporary copy operators and factory functions, to ease java access.
  // These are unnecessary for kotlin callers which can simply consume the copy operator.
  fun copyWithBranch(branch: String?) = copy(branch = branch)
  fun copyWithUrl(url: String?) = copy(url = url)
  fun copyWithProjectSpace(projectSpace: String) = copy(projectSpace = projectSpace)

  companion object {
    @JvmStatic fun fakeRepositoryConfig() = RepositoryConfig(type = "dummy")
  }
}
